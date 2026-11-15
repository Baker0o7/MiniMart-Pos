package com.minimart.pos.util

/**
 * The shop owner's chosen order of the Home quick-action tiles, kept free of the UI so it can be tested.
 * The saved order is a list of tile ids; tiles added by a later app version (not in the list) are shown
 * after the saved ones, and saved ids that no longer exist are ignored.
 */
object ActionOrder {
    fun parse(saved: String): List<String> = saved.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    fun encode(order: List<String>): String = order.distinct().joinToString(",")

    /** [available] in default order, rearranged to follow [saved]; every available id appears exactly once. */
    fun apply(available: List<String>, saved: List<String>): List<String> {
        val have = available.toSet()
        val first = saved.filter { it in have }.distinct()
        return first + available.filter { it !in first }
    }

    /** Takes [id] out and puts it where [targetId] is, so the tiles in between shift by one. */
    fun moveTo(order: List<String>, id: String, targetId: String): List<String> {
        val from = order.indexOf(id)
        val to = order.indexOf(targetId)
        if (from < 0 || to < 0 || from == to) return order
        return order.toMutableList().also { it.removeAt(from); it.add(to, id) }
    }

    /** Moves [id] [delta] places (negative = earlier), stopping at either end. Used by screen-reader "move" actions. */
    fun moveBy(order: List<String>, id: String, delta: Int): List<String> {
        val from = order.indexOf(id)
        if (from < 0) return order
        val to = (from + delta).coerceIn(0, order.lastIndex)
        if (to == from) return order
        return order.toMutableList().also { it.removeAt(from); it.add(to, id) }
    }

    /** Text for the count bubble on a tile: nothing for zero, "99+" when large. */
    fun badgeText(count: Int): String = when { count <= 0 -> ""; count > 99 -> "99+"; else -> count.toString() }

    /** Keeps ids that are hidden right now (in [previous] but not in [visible]) so they return to a sensible place. */
    fun merge(visible: List<String>, previous: List<String>): List<String> = visible + previous.filter { it !in visible }
}
