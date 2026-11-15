package com.minimart.pos.util

import kotlin.math.ceil

/** Result of planning one product's restock. */
data class RestockPlan(
    val avgDaily: Double,
    val daysCover: Double?,
    /** Stock level at which an order should already have been placed. */
    val reorderPoint: Double,
    val needsReorder: Boolean,
    val suggestedQty: Int
)

/**
 * Sales-rate based restocking. An order is due when stock would run out before a delivery could
 * arrive (lead time) plus a small safety buffer, and it is sized to cover lead time + [coverDays].
 */
object RestockPlanner {
    const val LEAD_TIME_DAYS = 3.0
    const val SAFETY_DAYS = 2.0
    const val COVER_DAYS = 14.0

    fun plan(
        onHand: Double,
        sold: Double,
        sellingDays: Int,
        lowStockThreshold: Double,
        minOrderQty: Int,
        leadDays: Double = LEAD_TIME_DAYS,
        safetyDays: Double = SAFETY_DAYS,
        coverDays: Double = COVER_DAYS
    ): RestockPlan {
        val stock = onHand.coerceAtLeast(0.0)
        val avg = if (sellingDays > 0) (sold / sellingDays).coerceAtLeast(0.0) else 0.0
        val cover = if (avg > 0) stock / avg else null
        val point = maxOf(avg * (leadDays + safetyDays), lowStockThreshold)
        val due = stock <= point && (avg > 0 || stock <= lowStockThreshold)
        val need = ceil(avg * (leadDays + coverDays) - stock).toInt()
        val qty = if (due) maxOf(minOrderQty, need, 1) else 0
        return RestockPlan(avg, cover, point, due, qty)
    }
}
