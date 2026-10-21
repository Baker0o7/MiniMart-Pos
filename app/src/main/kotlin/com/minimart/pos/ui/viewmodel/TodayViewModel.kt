package com.minimart.pos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimart.pos.data.repository.ExpenseRepository
import com.minimart.pos.data.repository.MpesaPaymentRepository
import com.minimart.pos.data.repository.ProductRepository
import com.minimart.pos.data.repository.SaleRepository
import com.minimart.pos.data.repository.SettingsRepository
import com.minimart.pos.data.repository.ShiftRepository
import com.minimart.pos.util.SaleLite
import com.minimart.pos.util.TodaySummary
import com.minimart.pos.util.TodaySummaryBuilder
import com.minimart.pos.util.todayStartMs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class TodayUiState(
    val loading: Boolean = true,
    val currency: String = "KES",
    val storeName: String = "",
    val summary: TodaySummary? = null,
    /** Short, plain things worth acting on today. */
    val alerts: List<String> = emptyList(),
    val updatedAt: Long = 0L
)

/** The owner's "how is today going" numbers, refreshed while the screen is open. */
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val sales: SaleRepository,
    private val expenses: ExpenseRepository,
    private val settings: SettingsRepository,
    private val products: ProductRepository,
    private val shifts: ShiftRepository,
    private val mpesa: MpesaPaymentRepository
) : ViewModel() {
    private val _state = MutableStateFlow(TodayUiState())
    val state: StateFlow<TodayUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val start = todayStartMs()
                val cal = Calendar.getInstance()
                cal.timeInMillis = start; cal.add(Calendar.DAY_OF_YEAR, -7)
                val lastWeekStart = cal.timeInMillis
                cal.timeInMillis = now; cal.add(Calendar.DAY_OF_YEAR, -7)
                val lastWeekNow = cal.timeInMillis

                val today = sales.getCompletedSalesByDateRange(start, Long.MAX_VALUE).first()
                val profit = sales.profitBetween(start, Long.MAX_VALUE)
                val spent = expenses.getTotalExpensesToday(start).first() ?: 0.0
                val lastWeek = sales.getTotalRevenueBetween(lastWeekStart, lastWeekNow).first() ?: 0.0
                val hourCal = Calendar.getInstance()
                val summary = TodaySummaryBuilder.build(
                    sales = today.map { SaleLite(it.createdAt, it.totalAmount, it.paymentMethod.name, it.cashPortion) },
                    profit = profit, expenses = spent, lastWeekSameTime = lastWeek,
                    hourOf = { ms -> hourCal.timeInMillis = ms; hourCal.get(Calendar.HOUR_OF_DAY) }
                )

                val alerts = mutableListOf<String>()
                val open = runCatching { shifts.openShifts() }.getOrDefault(emptyList())
                if (open.isNotEmpty()) alerts += "${open.size} shift(s) open: ${open.joinToString(", ") { it.cashierName }}"
                val recon = runCatching { reconcileRange(mpesa, sales, start, Long.MAX_VALUE) }.getOrNull()
                if (recon != null && recon.unclaimedPayments.isNotEmpty())
                    alerts += "${recon.unclaimedPayments.size} M-Pesa payment(s) not matched to a sale"
                if (recon != null && recon.unverifiedSales.isNotEmpty())
                    alerts += "${recon.unverifiedSales.size} M-Pesa sale(s) with no matching message"
                val low = runCatching { products.getLowStockProducts().first().size }.getOrDefault(0)
                if (low > 0) alerts += "$low product(s) running low"

                _state.value = TodayUiState(
                    loading = false,
                    currency = settings.currency.first(), storeName = settings.storeName.first(),
                    summary = summary, alerts = alerts, updatedAt = now
                )
            } catch (_: Exception) {
                _state.value = _state.value.copy(loading = false)
            }
        }
    }
}
