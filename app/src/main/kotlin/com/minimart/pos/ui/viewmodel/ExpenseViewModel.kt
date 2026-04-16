package com.minimart.pos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimart.pos.data.entity.Expense
import com.minimart.pos.data.entity.ExpenseCategory
import com.minimart.pos.data.repository.ExpenseRepository
import com.minimart.pos.data.repository.SaleRepository
import com.minimart.pos.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class ProfitLossState(
    val period: ReportPeriod = ReportPeriod.TODAY,
    val totalRevenue: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val netProfit: Double = 0.0,
    val profitMargin: Double = 0.0,
    val expenses: List<Expense> = emptyList(),
    val expensesByCategory: Map<ExpenseCategory, Double> = emptyMap(),
    val currency: String = "KES",
    val isLoading: Boolean = false,
    val successMessage: String? = null,
    val error: String? = null
)

@HiltViewModel
class ExpenseViewModel @Inject constructor(
    private val expenseRepo: ExpenseRepository,
    private val saleRepo: SaleRepository,
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    private val _period = MutableStateFlow(ReportPeriod.MONTH)
    val period: StateFlow<ReportPeriod> = _period.asStateFlow()

    private val _uiState = MutableStateFlow(ProfitLossState())
    val uiState: StateFlow<ProfitLossState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepo.currency.collect { cur ->
                _uiState.update { it.copy(currency = cur) }
            }
        }
        viewModelScope.launch {
            _period.collect { period ->
                val (start, end) = periodRange(period)
                combine(
                    expenseRepo.getExpensesByDateRange(start, end),
                    saleRepo.getSalesByDateRange(start, end)
                ) { expenses, sales ->
                    val totalExpenses = expenses.sumOf { it.amount }
                    val totalRevenue = sales
                        .filter { it.status == com.minimart.pos.data.entity.SaleStatus.COMPLETED }
                        .sumOf { it.totalAmount }
                    val netProfit = totalRevenue - totalExpenses
                    val margin = if (totalRevenue > 0) (netProfit / totalRevenue) * 100 else 0.0
                    val byCategory = expenses.groupBy { it.category }
                        .mapValues { (_, list) -> list.sumOf { it.amount } }
                    _uiState.update { state ->
                        state.copy(
                            period = period,
                            totalRevenue = totalRevenue,
                            totalExpenses = totalExpenses,
                            netProfit = netProfit,
                            profitMargin = margin,
                            expenses = expenses,
                            expensesByCategory = byCategory
                        )
                    }
                }.catch { }.collect()
            }
        }
    }

    fun setPeriod(p: ReportPeriod) { _period.value = p }

    fun addExpense(expense: Expense) {
        viewModelScope.launch {
            try {
                expenseRepo.insert(expense)
                _uiState.update { it.copy(successMessage = "Expense recorded") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed: ${e.message}") }
            }
        }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            try {
                expenseRepo.delete(expense)
                _uiState.update { it.copy(successMessage = "Expense deleted") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed: ${e.message}") }
            }
        }
    }

    fun clearMessages() { _uiState.update { it.copy(successMessage = null, error = null) } }

    private fun periodRange(period: ReportPeriod): Pair<Long, Long> {
        val end = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        val start = when (period) {
            ReportPeriod.TODAY -> {
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            ReportPeriod.WEEK  -> end - 7L * 24 * 60 * 60 * 1000
            ReportPeriod.MONTH -> end - 30L * 24 * 60 * 60 * 1000
            ReportPeriod.CUSTOM -> end - 30L * 24 * 60 * 60 * 1000
        }
        return Pair(start, end)
    }
}
