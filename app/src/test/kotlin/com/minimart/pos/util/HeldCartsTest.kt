package com.minimart.pos.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeldCartsTest {
    private fun cart(id: Long) = HeldCart(
        id, 1_000L + id, 5.0, 515.0, 4,
        listOf(HeldLine(7, 2, 0.0, 0.0), HeldLine(9, 1, 0.75, 10.5))
    )

    @Test fun roundTrips() {
        val list = listOf(cart(1), cart(2))
        assertEquals(list, HeldCarts.decode(HeldCarts.encode(list)))
    }

    @Test fun damagedStorageReadsAsEmpty() {
        assertTrue(HeldCarts.decode("not json").isEmpty())
        assertTrue(HeldCarts.decode(null).isEmpty())
        assertTrue(HeldCarts.decode("").isEmpty())
    }

    @Test fun aDamagedEntryDoesNotLoseTheOthers() {
        val good = HeldCarts.encode(listOf(cart(1))).trimEnd(']')
        val mixed = "$good,{\"id\":2},{\"id\":3,\"lines\":[]}]"
        assertEquals(listOf(1L), HeldCarts.decode(mixed).map { it.id })
    }

    @Test fun keepsAtMostTheNewestTen() {
        var list = emptyList<HeldCart>()
        (1L..12L).forEach { list = HeldCarts.add(list, cart(it)) }
        assertEquals(HeldCarts.MAX, list.size)
        assertEquals(3L, list.first().id)
        assertEquals(12L, list.last().id)
    }

    @Test fun removesById() =
        assertEquals(listOf(2L), HeldCarts.remove(listOf(cart(1), cart(2)), 1).map { it.id })
}
