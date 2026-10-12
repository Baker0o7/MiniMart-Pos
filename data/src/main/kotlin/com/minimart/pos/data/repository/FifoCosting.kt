package com.minimart.pos.data.repository

import kotlin.math.min

/** A batch as the costing sees it. */
data class BatchSlice(val id: Long, val remaining: Double, val unitCost: Double)

/** [qty] of a sale line costed at [unitCost], taken from [batchId] (0 = no batch record). */
data class CostPart(val batchId: Long, val qty: Double, val unitCost: Double) {
    val cost: Double get() = qty * unitCost
}

/** First-in-first-out costing, kept free of the database so it can be tested on its own. */
object FifoCosting {
    private const val EPS = 1e-9

    /**
     * Takes [needed] from [batches] in the order given (oldest first). Anything the batches cannot
     * cover (stock that predates batch tracking, or a count that drifted) is costed at [fallbackCost]
     * under batch 0, so a sale is never blocked and its cost is never left out.
     */
    fun allocate(batches: List<BatchSlice>, needed: Double, fallbackCost: Double): List<CostPart> {
        if (needed <= EPS) return emptyList()
        val parts = mutableListOf<CostPart>()
        var left = needed
        for (b in batches) {
            if (left <= EPS) break
            if (b.remaining <= EPS) continue
            val take = min(left, b.remaining)
            parts += CostPart(b.id, take, b.unitCost)
            left -= take
        }
        if (left > EPS) parts += CostPart(0L, left, fallbackCost.coerceAtLeast(0.0))
        return parts
    }

    /** Total cost of [parts], rounded to cents. */
    fun total(parts: List<CostPart>): Double = Math.round(parts.sumOf { it.cost } * 100.0) / 100.0
}
