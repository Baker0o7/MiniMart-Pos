package com.minimart.pos.util

import org.json.JSONArray
import org.json.JSONObject

/** One line of a parked sale. Only ids and amounts are kept; price and stock are re-read on resume. */
data class HeldLine(val productId: Long, val quantity: Int, val weightKg: Double, val discount: Double)

/**
 * A sale set aside so the next customer can be served. [total] and [itemCount] are only a snapshot for
 * the list; resuming rebuilds the cart from current product data.
 */
data class HeldCart(
    val id: Long,
    val createdAt: Long,
    val discount: Double,
    val total: Double,
    val itemCount: Int,
    val lines: List<HeldLine>
)

/** Saves parked sales as JSON (plain org.json, so app updates and code shrinking can never change the format). */
object HeldCarts {
    const val MAX = 10

    fun encode(carts: List<HeldCart>): String {
        val arr = JSONArray()
        carts.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id); put("at", c.createdAt); put("disc", c.discount)
                put("total", c.total); put("n", c.itemCount)
                put("lines", JSONArray().apply {
                    c.lines.forEach { l ->
                        put(JSONObject().apply {
                            put("p", l.productId); put("q", l.quantity); put("w", l.weightKg); put("d", l.discount)
                        })
                    }
                })
            })
        }
        return arr.toString()
    }

    /** Never throws: a damaged entry is skipped, damaged storage reads as "nothing parked". */
    fun decode(json: String?): List<HeldCart> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                try {
                    val o = arr.getJSONObject(i)
                    val lines = o.getJSONArray("lines").let { la ->
                        (0 until la.length()).map { j ->
                            val l = la.getJSONObject(j)
                            HeldLine(l.getLong("p"), l.getInt("q"), l.optDouble("w", 0.0), l.optDouble("d", 0.0))
                        }
                    }
                    if (lines.isEmpty()) null
                    else HeldCart(o.getLong("id"), o.getLong("at"), o.optDouble("disc", 0.0),
                        o.optDouble("total", 0.0), o.optInt("n", lines.sumOf { it.quantity }), lines)
                } catch (_: Exception) { null }
            }
        } catch (_: Exception) { emptyList() }
    }

    /** Adds [cart]; at the limit the oldest parked sale is dropped. */
    fun add(carts: List<HeldCart>, cart: HeldCart): List<HeldCart> = (carts + cart).takeLast(MAX)

    fun remove(carts: List<HeldCart>, id: Long): List<HeldCart> = carts.filterNot { it.id == id }
}
