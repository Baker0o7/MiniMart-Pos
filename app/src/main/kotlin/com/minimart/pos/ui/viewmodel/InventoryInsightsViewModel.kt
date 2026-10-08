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
import kotlin.math.ceil

/** One product with the numbers the stock-insight lists are built from. */
data class InsightRow(
    val product: Product,
    val onHand: Double,          // units, or kg for weighed products
    val sold30: Double,          // sold in the last 30 days (same unit)
    val avgDaily: Double,
    val daysCover: Double?,      // null when it hasn't been selling
    val suggestedQty: Int,       // how many to order to reach ~2 weeks of cover
    val lastSoldAt: Long?,
    val costValue: Double,
    val retailValue: Double
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
        private const val COVER_TARGET_DAYS = 14
        private const val LOW_COVER_DAYS = 7.0
    }

    val insights: StateFlow<InventoryInsights> = combine(
        productRepo.getAllProducts(),
        saleRepo.getProductSalesStats(System.currentTimeMillis() - WINDOW_DAYS * DAY),
        saleRepo.getLastSoldTimes(),
        settingsRepo.currency,
        settingsRepo.storeName
    ) { products, stats, lastSold, currency, storeName ->
        build(products, stats.associateBy { it.productId }, lastSold.associate { it.productId to it.lastSoldAt }, currency, storeName)
    }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .catch { emit(InventoryInsights(loaded = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InventoryInsights())

    private fun build(
        products: List<Product>,
        stats: Map<Long, com.minimart.pos.data.dao.ProductSalesStat>,
        lastSold: Map<Long, Long>,
        currency: String,
        storeName: String
    ): InventoryInsights {
        val now = System.currentTimeMillis()
        val rows = products.map { p ->
            val onHand = (if (p.isWeighed) p.stockKg else p.stock.toDouble()).coerceAtLeast(0.0)
            val sold30 = stats[p.id]?.qty ?: 0.0
            // A product added 5 days ago has only had 5 days to sell — don't dilute its rate over 30.
            val ageDays = ((now - p.createdAt) / DAY).toInt().coerceIn(1, WINDOW_DAYS)
            val avgDaily = sold30 / ageDays
            val cover = if (avgDaily > 0) onHand / avgDaily else null
            val need = ceil(avgDaily * COVER_TARGET_DAYS - onHand).toInt()
            val suggested = maxOf(p.reorderQuantity, need, 1)
            val unitPrice = if (p.isWeighed) p.pricePerKg else p.price
            InsightRow(
                product = p, onHand = onHand, sold30 = sold30, avgDaily = avgDaily, daysCover = cover,
                suggestedQty = suggested, lastSoldAt = lastSold[p.id],
                costValue = onHand * p.costPrice, retailValue = onHand * unitPrice
            )
        }

        val reorder = rows.filter { r ->
            r.onHand <= r.product.lowStockThreshold || (r.daysCover != null && r.daysCover < LOW_COVER_DAYS)
        }.sortedWith(compareBy<InsightRow> { it.daysCover ?: Double.MAX_VALUE }.thenBy { it.onHand })

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
