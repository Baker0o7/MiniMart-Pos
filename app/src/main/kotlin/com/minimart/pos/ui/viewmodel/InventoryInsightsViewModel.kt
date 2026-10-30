package com.minimart.pos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimart.pos.data.entity.Product
import com.minimart.pos.data.repository.ProductRepository
import com.minimart.pos.data.repository.SaleRepository
import com.minimart.pos.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

/** One product with the numbers the stock-insight lists are built from. */
data class InsightRow(
    val product: Product,
    val onHand: Double,          // units, or kg for weighed products
    val sold30: Double,          // sold in the last 30 days (same unit)
    val avgDaily: Double,
    val daysCover: Double?,      // null when it hasn't been selling
    val suggestedQty: Int,       // how many to order to cover lead time plus ~2 weeks (0 when not due)
    val needsReorder: Boolean = false,
    val orderCost: Double = 0.0, // suggestedQty at the product's cost price
    val lastSoldAt: Long?,
    val costValue: Double,
    val retailValue: Double,
    /** Forecast: whole days until it runs out (null = not selling or outlasts 4 weeks), and how much history backs it (0..1). */
    val emptyInDays: Int? = null,
    val forecastConfidence: Double = 0.0
)

data class CategoryValue(val category: String, val items: Int, val costValue: Double, val retailValue: Double)

data class InventoryInsights(
    val currency: String = "KES",
    val loaded: Boolean = false,
    val skuCount: Int = 0,
    val outOfStock: Int = 0,
    val costValue: Double = 0.0,
    val retailValue: Double = 0.0,
    val reorder: List<InsightRow> = emptyList(),
    val dead: List<InsightRow> = emptyList(),
    val deadCostValue: Double = 0.0,
    val fast: List<InsightRow> = emptyList(),
    val categories: List<CategoryValue> = emptyList(),
    val storeName: String = "MiniMart"
) {
    val potentialProfit: Double get() = retailValue - costValue
}

@HiltViewModel
class InventoryInsightsViewModel @Inject constructor(
    productRepo: ProductRepository,
    saleRepo: SaleRepository,
    settingsRepo: SettingsRepository
) : ViewModel() {

    companion object {
        private const val DAY = 24L * 60 * 60 * 1000
        private const val WINDOW_DAYS = 30
        private const val FORECAST_DAYS = 28L
        private const val COVER_TARGET_DAYS = 14
        private const val LOW_COVER_DAYS = 7.0
    }

    val insights: StateFlow<InventoryInsights> = combine(
        productRepo.getAllProducts(),
        saleRepo.getProductSalesStats(System.currentTimeMillis() - WINDOW_DAYS * DAY),
        saleRepo.getLastSoldTimes().combine(
            saleRepo.getDailyProductUnits(System.currentTimeMillis() - FORECAST_DAYS * DAY, java.util.TimeZone.getDefault().rawOffset.toLong())
        ) { last, daily -> last to daily },
        settingsRepo.currency,
        settingsRepo.storeName
    ) { products, stats, lastAndDaily, currency, storeName ->
        val (lastSold, daily) = lastAndDaily
        build(products, stats.associateBy { it.productId }, lastSold.associate { it.productId to it.lastSoldAt },
            daily.groupBy { it.productId }, currency, storeName)
    }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .catch { emit(InventoryInsights(loaded = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InventoryInsights())

    private fun build(
        products: List<Product>,
        stats: Map<Long, com.minimart.pos.data.dao.ProductSalesStat>,
        lastSold: Map<Long, Long>,
        daily: Map<Long, List<com.minimart.pos.data.dao.DailyProductUnits>>,
        currency: String,
        storeName: String
    ): InventoryInsights {
        val now = System.currentTimeMillis()
        val rows = products.map { p ->
            val onHand = (if (p.isWeighed) p.stockKg else p.stock.toDouble()).coerceAtLeast(0.0)
            val sold30 = stats[p.id]?.qty ?: 0.0
            // A product added 5 days ago has only had 5 days to sell — don't dilute its rate over 30.
            val ageDays = ((now - p.createdAt) / DAY).toInt().coerceIn(1, WINDOW_DAYS)
            val plan = com.minimart.pos.util.RestockPlanner.plan(
                onHand = onHand, sold = sold30, sellingDays = ageDays,
                lowStockThreshold = p.lowStockThreshold.toDouble(), minOrderQty = p.reorderQuantity)
            val avgDaily = plan.avgDaily
            val cover = plan.daysCover
            // Forecast from the last 4 weeks of daily sales (weekday pattern, recent days count more).
            val offset = java.util.TimeZone.getDefault().rawOffset.toLong()
            val today = (now + offset) / DAY
            val series = com.minimart.pos.util.DemandForecast.dense(
                daily[p.id].orEmpty().associate { it.day to it.qty }, today - (FORECAST_DAYS - 1) - 0, today - 1)
            val ageDaysFull = ((now - p.createdAt) / DAY).toInt()
            // A product younger than the window only has its own days as history.
            val usable = if (ageDaysFull in 1 until series.size) series.takeLast(ageDaysFull) else series
            val firstWeekday = ((today - usable.size) + 3).mod(7L).toInt()   // Monday = 0; epoch day 0 was a Thursday
            val fc = com.minimart.pos.util.DemandForecast.forecast(usable, firstWeekday, onHand)
            val confident = fc.confidence >= 0.5 && fc.avgPerDay > 0
            val suggested = if (plan.needsReorder && confident)
                maxOf(p.reorderQuantity, com.minimart.pos.util.DemandForecast.orderQuantity(fc, onHand,
                    com.minimart.pos.util.RestockPlanner.LEAD_TIME_DAYS.toInt(), COVER_TARGET_DAYS), 1)
            else plan.suggestedQty
            val unitPrice = if (p.isWeighed) p.pricePerKg else p.price
            InsightRow(
                product = p, onHand = onHand, sold30 = sold30, avgDaily = avgDaily, daysCover = cover,
                suggestedQty = suggested, needsReorder = plan.needsReorder, orderCost = suggested * p.costPrice,
                lastSoldAt = lastSold[p.id],
                costValue = onHand * p.costPrice, retailValue = onHand * unitPrice,
                emptyInDays = fc.daysUntilEmpty, forecastConfidence = fc.confidence
            )
        }

        val reorder = rows.filter { it.needsReorder }.sortedWith(compareBy<InsightRow> { it.daysCover ?: Double.MAX_VALUE }.thenBy { it.onHand })

        val deadCutoff = now - WINDOW_DAYS * DAY
        val dead = rows.filter { r ->
            r.onHand > 0 && (r.lastSoldAt ?: r.product.createdAt) < deadCutoff
        }.sortedByDescending { if (it.costValue > 0) it.costValue else it.retailValue }

        val fast = rows.filter { it.sold30 > 0 }.sortedByDescending { it.sold30 }.take(10)

        val categories = rows.groupBy { it.product.category.ifBlank { "General" } }
            .map { (cat, list) -> CategoryValue(cat, list.size, list.sumOf { it.costValue }, list.sumOf { it.retailValue }) }
            .sortedByDescending { it.retailValue }

        return InventoryInsights(
            currency = currency, loaded = true, storeName = storeName,
            skuCount = rows.size, outOfStock = rows.count { it.onHand <= 0 },
            costValue = rows.sumOf { it.costValue }, retailValue = rows.sumOf { it.retailValue },
            reorder = reorder, dead = dead, deadCostValue = dead.sumOf { it.costValue },
            fast = fast, categories = categories
        )
    }
}
