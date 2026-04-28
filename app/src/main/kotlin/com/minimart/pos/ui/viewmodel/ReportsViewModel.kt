package com.minimart.pos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimart.pos.data.dao.TopSellerResult
import com.minimart.pos.data.entity.Sale
import com.minimart.pos.data.repository.SaleRepository
import com.minimart.pos.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.minimart.pos.util.todayStartMs
import kotlinx.coroutines.flow.*
import java.util.Calendar
import javax.inject.Inject

enum class ReportPeriod { TODAY, WEEK, MONTH, CUSTOM }

data class ReportsUiState(
    val period: ReportPeriod = ReportPeriod.TODAY,
    val sales: List<Sale> = emptyList(),
    val totalRevenue: Double = 0.0,
    val totalTransactions: Int = 0,
    val averageBasket: Double = 0.0,
    val topSellers: List<TopSellerResult> = emptyList(),
    val currency: String = "KES",
    val isLoading: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val saleRepo: SaleRepository,
    settingsRepo: SettingsRepository
) : ViewModel() {

    private val _period = MutableStateFlow(ReportPeriod.TODAY)
    val period: StateFlow<ReportPeriod> = _period.asStateFlow()

    val uiState: StateFlow<ReportsUiState> = combine(
        _period,
        settingsRepo.currency
    ) { period, currency -> Pair(period, currency) }
        .flatMapLatest { (period, currency) ->
            val (start, end) = periodRange(period)
            combine(
                saleRepo.getSalesByDateRange(start, end),
                saleRepo.getTopSellers(start)
            ) { sales, topSellers ->
                val completed = sales.filter { it.status == com.minimart.pos.data.entity.SaleStatus.COMPLETED }
                ReportsUiState(
                    period = period,
                    sales = completed,
                    totalRevenue = completed.sumOf { it.totalAmount },
                    totalTransactions = completed.size,
                    averageBasket = if (completed.isEmpty()) 0.0 else completed.sumOf { it.totalAmount } / completed.size,
                    topSellers = topSellers,
                    currency = currency
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReportsUiState())

    fun setPeriod(period: ReportPeriod) { _period.value = period }

    private fun periodRange(period: ReportPeriod): Pair<Long, Long> {
        val end = System.currentTimeMillis()
        val start = when (period) {
            ReportPeriod.TODAY  -> todayStartMs()
            ReportPeriod.WEEK   -> end - 7L * 24 * 60 * 60 * 1000
            ReportPeriod.MONTH  -> end - 30L * 24 * 60 * 60 * 1000
            ReportPeriod.CUSTOM -> end - 90L * 24 * 60 * 60 * 1000
        }
        return Pair(start, end)
    }
}
