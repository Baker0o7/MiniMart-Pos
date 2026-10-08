package com.minimart.pos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimart.pos.data.dao.ProductSalesStat
import com.minimart.pos.data.entity.PaymentMethod
import com.minimart.pos.data.entity.Sale
import com.minimart.pos.data.repository.ExpenseRepository
import com.minimart.pos.data.repository.SaleRepository
import com.minimart.pos.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.util.Calendar
import javax.inject.Inject

enum class AnalyticsRange(val label: String, val days: Int) {
    D7("7 days", 7), D30("30 days", 30), D90("90 days", 90)
}

/** One bar of the revenue trend (a day, or a week for the 90-day view). */
data class TrendPoint(val startMs: Long, val revenue: Double)

data class AnalyticsState(
    val range: AnalyticsRange = AnalyticsRange.D30,
    val currency: String = "KES",
    val loaded: Boolean = false,
    val revenue: Double = 0.0,
    val prevRevenue: Double = 0.0,
    val transactions: Int = 0,
    val avgBasket: Double = 0.0,
    val grossProfit: Double = 0.0,
    /** Share (0..1) of revenue whose products have a cost price — profit is only as good as this. */
    val costCoverage: Double = 0.0,
    val expenses: Double = 0.0,
    val trend: List<TrendPoint> = emptyList(),
    val trendBucketDays: Int = 1,
    val paymentMix: List<Pair<PaymentMethod, Double>> = emptyList(),
    val hourly: List<Int> = List(24) { 0 },
    /** Revenue per weekday, Monday first. */
    val weekday: List<Double> = List(7) { 0.0 },
    val topProducts: List<ProductSalesStat> = emptyList(),
    val categories: List<Pair<String, Double>> = emptyList()
) {
    val netProfit: Double get() = grossProfit - expenses
    /** % change vs the previous period of the same length; null when there is nothing to compare. */
    val revenueChangePct: Double? get() = if (prevRevenue > 0) (revenue - prevRevenue) / prevRevenue * 100 else null
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val saleRepo: SaleRepository,
    private val expenseRepo: ExpenseRepository,
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    private val _range = MutableStateFlow(AnalyticsRange.D30)
    val range: StateFlow<AnalyticsRange> = _range.asStateFlow()
    fun setRange(r: AnalyticsRange) { _range.value = r }

    val state: StateFlow<AnalyticsState> = _range.flatMapLatest { r ->
        val start = dayStart(-(r.days - 1))
        val prevStart = dayStart(-(2 * r.days - 1))
        combine(
            saleRepo.getCompletedSalesByDateRange(start, Long.MAX_VALUE),
            saleRepo.getTotalRevenueBetween(prevStart, start),
            saleRepo.getProductSalesStats(start, Long.MAX_VALUE),
            expenseRepo.getExpensesByDateRange(start, Long.MAX_VALUE),
            settingsRepo.currency
        ) { sales, prev, stats, expenses, currency ->
            compute(r, start, currency, sales, prev ?: 0.0, stats, expenses.sumOf { it.amount })
        }
    }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .catch { emit(AnalyticsState(range = _range.value, loaded = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AnalyticsState())

    /** Midnight [offsetDays] days from today (negative = past). */
    private fun dayStart(offsetDays: Int): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, offsetDays)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun compute(
        r: AnalyticsRange, start: Long, currency: String, sales: List<Sale>, prev: Double,
        stats: List<ProductSalesStat>, expenses: Double
    ): AnalyticsState {
        val revenue = sales.sumOf { it.totalAmount }
        val tax = sales.sumOf { it.taxAmount }
        val cogs = stats.sumOf { it.cost }
        val statRevenue = stats.sumOf { it.revenue }
        val coverage = if (statRevenue > 0) (stats.sumOf { it.costedRevenue } / statRevenue).coerceIn(0.0, 1.0) else 0.0

        val bucketDays = if (r.days > 31) 7 else 1
        val bucketMs = bucketDays * 24L * 60 * 60 * 1000
        val buckets = (r.days + bucketDays - 1) / bucketDays
        val trend = DoubleArray(buckets)
        val hourly = IntArray(24)
        val weekday = DoubleArray(7)
        val cal = Calendar.getInstance()
        val mix = HashMap<PaymentMethod, Double>()
        sales.forEach { s ->
            val idx = ((s.createdAt - start) / bucketMs).toInt().coerceIn(0, buckets - 1)
            trend[idx] += s.totalAmount
            cal.timeInMillis = s.createdAt
            hourly[cal.get(Calendar.HOUR_OF_DAY)]++
            // Calendar: Sunday = 1 … Saturday = 7  →  Monday-first index 0..6
            weekday[(cal.get(Calendar.DAY_OF_WEEK) + 5) % 7] += s.totalAmount
            mix[s.paymentMethod] = (mix[s.paymentMethod] ?: 0.0) + s.totalAmount
        }

        return AnalyticsState(
            range = r, currency = currency, loaded = true,
            revenue = revenue, prevRevenue = prev,
            transactions = sales.size,
            avgBasket = if (sales.isEmpty()) 0.0 else revenue / sales.size,
            grossProfit = revenue - tax - cogs,
            costCoverage = coverage,
            expenses = expenses,
            trend = trend.mapIndexed { i, v -> TrendPoint(start + i * bucketMs, v) },
            trendBucketDays = bucketDays,
            paymentMix = mix.entries.map { it.key to it.value }.filter { it.second > 0 }.sortedByDescending { it.second },
            hourly = hourly.toList(),
            weekday = weekday.toList(),
            topProducts = stats.sortedByDescending { it.revenue }.take(5),
            categories = stats.groupBy { it.category.ifBlank { "General" } }
                .map { (c, l) -> c to l.sumOf { it.revenue } }
                .sortedByDescending { it.second }.take(6)
        )
    }
}
