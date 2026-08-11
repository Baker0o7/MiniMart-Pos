package com.minimart.pos.ui.viewmodel

import com.minimart.pos.data.entity.CartItem
import com.minimart.pos.data.entity.Product
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests the core checkout calculation logic in [CartUiState] — the numbers that
 * directly decide how much cash a cashier collects from a customer. CartItem and
 * Product are plain Kotlin data classes with Room annotations (pure metadata, no
 * Android runtime dependency), so they're fully constructible and testable here on
 * the local JVM with no emulator/Robolectric needed.
 */
class CartUiStateTest {

    private fun product(price: Double, barcode: String = "TEST", name: String = "Test Product") =
        Product(barcode = barcode, name = name, price = price, stock = 100)

    @Test
    fun `empty cart has zero totals`() {
        val state = CartUiState()
        assertEquals(0.0, state.subtotal, 0.0)
        assertEquals(0.0, state.total, 0.0)
        assertEquals(0, state.itemCount)
        assertTrue(state.isEmpty)
    }

    @Test
    fun `subtotal sums line subtotals exactly, without Double compounding error`() {
        // These specific prices are the textbook case where naive Double summation
        // drifts: 19.99*2 + 49.50*1 + 5.25*3 is mathematically exactly 105.23, but
        // summing the same values as raw Doubles (as the old implementation did via
        // items.sumOf{}) can produce 105.22999999999999 due to IEEE-754 binary
        // floating-point representation. The Money-backed implementation must give
        // the mathematically exact answer.
        val items = listOf(
            CartItem(product(19.99), quantity = 2),
            CartItem(product(49.50), quantity = 1),
            CartItem(product(5.25), quantity = 3)
        )
        val state = CartUiState(items = items)
        assertEquals(105.23, state.subtotal, 0.0)
    }

    @Test
    fun `itemCount sums quantities across all lines`() {
        val items = listOf(
            CartItem(product(10.0), quantity = 2),
            CartItem(product(20.0), quantity = 5)
        )
        val state = CartUiState(items = items)
        assertEquals(7, state.itemCount)
    }

    @Test
    fun `total subtracts discount from subtotal`() {
        val items = listOf(CartItem(product(100.0), quantity = 1))
        val state = CartUiState(items = items, discount = 15.0)
        assertEquals(100.0, state.subtotal, 0.0)
        assertEquals(15.0, state.totalDiscount, 0.0)
        assertEquals(85.0, state.total, 0.0)
    }

    @Test
    fun `total never goes negative even if discount exceeds subtotal`() {
        // Regression test: total previously had no floor, so a discount larger than
        // the subtotal made total negative. That broke checkout validation downstream
        // — `cashAmount >= total` became trivially true for ANY amount (even KES 0)
        // since any number is >= a negative number, letting a sale complete while
        // collecting nothing.
        val items = listOf(CartItem(product(50.0), quantity = 1))
        val state = CartUiState(items = items, discount = 999.0)
        assertEquals(0.0, state.total, 0.0)
        assertFalse(state.total < 0.0)
    }

    @Test
    fun `per-item discounts combine with global discount`() {
        val items = listOf(
            CartItem(product(100.0), quantity = 1, discount = 10.0),
            CartItem(product(50.0), quantity = 1, discount = 5.0)
        )
        val state = CartUiState(items = items, discount = 20.0)
        // subtotal = 100 + 50 = 150
        // totalDiscount = 10 + 5 (line) + 20 (global) = 35
        // total = 150 - 35 = 115
        assertEquals(150.0, state.subtotal, 0.0)
        assertEquals(35.0, state.totalDiscount, 0.0)
        assertEquals(115.0, state.total, 0.0)
    }

    @Test
    fun `weighed item subtotal uses weight times price per kg, not quantity`() {
        val weighedProduct = Product(
            barcode = "PLU00001", name = "Loose Rice", price = 150.0, stock = 100,
            isWeighed = true, pricePerKg = 300.0
        )
        // quantity is always 1 for weighed items — the real measure is weightKg
        val item = CartItem(weighedProduct, quantity = 1, weightKg = 0.5)
        val state = CartUiState(items = listOf(item))
        // 0.5kg * KES 300/kg = KES 150.00
        assertEquals(150.0, state.subtotal, 0.0)
    }
}
