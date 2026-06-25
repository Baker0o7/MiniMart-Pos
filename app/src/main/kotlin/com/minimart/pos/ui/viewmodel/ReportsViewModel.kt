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
                // Bug fix: was getSalesByDateRange (all statuses) then .filter{COMPLETED}
                // in Kotlin — loaded voided/refunded sales into memory unnecessarily.
                // getCompletedSalesByDateRange filters in SQL, only useful rows fetched.
                saleRepo.getCompletedSalesByDateRange(start, end),
                saleRepo.getTopSellers(start)
            ) { completed, topSellers ->
                val totalRevenue = completed.sumOf { it.totalAmount }
                ReportsUiState(
                    period = period,
                    sales = completed,
                    totalRevenue = totalRevenue,
                    totalTransactions = completed.size,
                    // Bug fix: was sumOf { totalAmount } / size computed twice — use the
                    // already-computed totalRevenue value instead.
                    averageBasket = if (completed.isEmpty()) 0.0 else totalRevenue / completed.size,
                    topSellers = topSellers,
                    currency = currency
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReportsUiState())

    fun setPeriod(period: ReportPeriod) { _period.value = period }

    private fun periodRange(period: ReportPeriod): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        val start = when (period) {
            ReportPeriod.TODAY  -> todayStartMs()
            ReportPeriod.WEEK   -> {
                // Bug fix: was `now - 7*24h` rolling window. Anchored to Mon 00:00.
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            ReportPeriod.MONTH  -> {
                // Bug fix: was `now - 30*24h` rolling window. Anchored to 1st 00:00.
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            ReportPeriod.CUSTOM -> now - 90L * 24 * 60 * 60 * 1000
        }
        return Pair(start, now)
    }
}
