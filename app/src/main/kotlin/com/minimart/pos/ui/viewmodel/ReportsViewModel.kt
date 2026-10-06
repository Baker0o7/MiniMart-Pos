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
import com.minimart.pos.util.weekStartMs
import com.minimart.pos.util.monthStartMs
import kotlinx.coroutines.flow.*
import java.util.Calendar
import javax.inject.Inject

enum class ReportPeriod(val label: String) {
    TODAY("Today"), WEEK("Week"), MONTH("Month"),
    /** User-picked date range (falls back to the last 90 days until one is chosen). */
    CUSTOM("Custom")
}

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
    private val expenseRepo: com.minimart.pos.data.repository.ExpenseRepository,
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    private val _period = MutableStateFlow(ReportPeriod.TODAY)
    val period: StateFlow<ReportPeriod> = _period.asStateFlow()

    private val _customRange = MutableStateFlow<Pair<Long, Long>?>(null)
    val customRange: StateFlow<Pair<Long, Long>?> = _customRange.asStateFlow()

    val uiState: StateFlow<ReportsUiState> = combine(
        _period,
        settingsRepo.currency,
        _customRange
    ) { period, currency, custom -> Triple(period, currency, custom) }
        .flatMapLatest { (period, currency, custom) ->
            val (start, end) = periodRange(period, custom)
            combine(
                // Bug fix: was getSalesByDateRange (all statuses) then .filter{COMPLETED}
                // in Kotlin — loaded voided/refunded sales into memory unnecessarily.
                // getCompletedSalesByDateRange filters in SQL, only useful rows fetched.
                saleRepo.getCompletedSalesByDateRange(start, end),
                saleRepo.getTopSellers(start, end)
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

    /** Gathers everything for the PDF business report for the period currently selected. */
    suspend fun buildReport(): com.minimart.pos.util.BusinessReportData {
        val period = _period.value
        val (start, rawEnd) = periodRange(period, _customRange.value)
        val now = System.currentTimeMillis()
        val end = minOf(rawEnd, now)
        val day = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
        val label = when (period) {
            ReportPeriod.TODAY -> "Today, ${day.format(java.util.Date(now))}"
            ReportPeriod.WEEK -> "This week: ${day.format(java.util.Date(start))} – ${day.format(java.util.Date(end))}"
            ReportPeriod.MONTH -> java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(start))
            ReportPeriod.CUSTOM -> "${day.format(java.util.Date(start))} – ${day.format(java.util.Date(end))}"
        }
        return com.minimart.pos.util.BusinessReportBuilder.build(
            storeName = settingsRepo.storeName.first(),
            currency = settingsRepo.currency.first(),
            periodLabel = label,
            generatedAt = now,
            allSales = saleRepo.getSalesByDateRange(start, rawEnd).first(),
            topSellers = saleRepo.getTopSellers(start, rawEnd).first(),
            expenses = expenseRepo.getExpensesByDateRange(start, rawEnd).first()
        )
    }

    fun setCustomRange(startMs: Long, endMs: Long) {
        _customRange.value = Pair(startMs, endMs)
        _period.value = ReportPeriod.CUSTOM
    }

    private fun periodRange(period: ReportPeriod, custom: Pair<Long, Long>?): Pair<Long, Long> {
        if (period == ReportPeriod.CUSTOM && custom != null) return custom
        val now = System.currentTimeMillis()
        val start = when (period) {
            ReportPeriod.TODAY  -> todayStartMs()
            ReportPeriod.WEEK   -> weekStartMs()    // Monday 00:00 (was wrong on Sundays)
            ReportPeriod.MONTH  -> monthStartMs()   // 1st of month 00:00
            ReportPeriod.CUSTOM -> now - 90L * 24 * 60 * 60 * 1000
        }
        // Open-ended: the end is not frozen at "now", so sales made while this stays
        // subscribed are included instead of being cut off at selection time.
        return Pair(start, Long.MAX_VALUE)
    }
}
