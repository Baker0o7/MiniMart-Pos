package com.minimart.pos.ui.viewmodel

import com.minimart.pos.data.entity.CartItem
import com.minimart.pos.data.entity.Product
import org.junit.Assert.assertEquals
import org.junit.Test

/** Regression tests for discount capping — a stale or oversized discount must never give goods away. */
class CartDiscountTest {

    private fun product(price: Double) = Product(barcode = "T", name = "Test", price = price, stock = 100)

    @Test
    fun `global discount larger than subtotal is capped at the subtotal`() {
        val state = CartUiState(items = listOf(CartItem(product(100.0), quantity = 1)), discount = 500.0)
        assertEquals(100.0, state.totalDiscount, 0.0)
        assertEquals(0.0, state.total, 0.0)
    }

    @Test
    fun `global discount is capped to what is left after line discounts`() {
        val item = CartItem(product(100.0), quantity = 1, discount = 60.0)
        val state = CartUiState(items = listOf(item), discount = 80.0)
        // 60 line discount leaves room for at most 40 more
        assertEquals(100.0, state.totalDiscount, 0.0)
        assertEquals(0.0, state.total, 0.0)
    }

    @Test
    fun `line discount cannot exceed the line subtotal`() {
        val item = CartItem(product(50.0), quantity = 2, discount = 999.0)
        assertEquals(100.0, item.lineDiscount, 0.0)
        assertEquals(0.0, item.lineTotal, 0.0)
    }

    @Test
    fun `discount shrinks automatically when the cart shrinks`() {
        val big = CartItem(product(100.0), quantity = 1)
        val small = CartItem(product(10.0), quantity = 1)
        val before = CartUiState(items = listOf(big, small), discount = 50.0)
        assertEquals(50.0, before.totalDiscount, 0.0)
        // remove the big line: the same stored discount is now capped to the 10.0 left
        val after = before.copy(items = listOf(small))
        assertEquals(10.0, after.totalDiscount, 0.0)
        assertEquals(0.0, after.total, 0.0)
    }

    @Test
    fun `negative discount is ignored`() {
        val state = CartUiState(items = listOf(CartItem(product(100.0), quantity = 1)), discount = -20.0)
        assertEquals(0.0, state.totalDiscount, 0.0)
        assertEquals(100.0, state.total, 0.0)
    }
}
