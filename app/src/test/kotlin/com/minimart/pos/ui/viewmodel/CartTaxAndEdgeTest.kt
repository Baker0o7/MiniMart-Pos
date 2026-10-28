package com.minimart.pos.ui.viewmodel

import com.minimart.pos.data.entity.CartItem
import com.minimart.pos.data.entity.Product
import org.junit.Assert.*
import org.junit.Test

/** More cart arithmetic: VAT is inside the price (never added on top), and edge cases a cashier can hit. */
class CartTaxAndEdgeTest {
    private fun product(price: Double, tax: Double = 0.0) = Product(barcode = "B", name = "P", price = price, stock = 50, taxRate = tax)

    @Test fun `VAT is extracted from the price, not added to it`() {
        val state = CartUiState(items = listOf(CartItem(product(116.0, tax = 0.16), quantity = 1)))
        assertEquals(116.0, state.total, 0.0)
        assertEquals(16.0, state.totalTax, 0.001)
    }

    @Test fun `untaxed lines contribute no VAT`() {
        val state = CartUiState(items = listOf(CartItem(product(50.0), quantity = 3)))
        assertEquals(0.0, state.totalTax, 0.0)
        assertEquals(150.0, state.total, 0.0)
    }

    @Test fun `quantity multiplies the price on a taxed line`() {
        val state = CartUiState(items = listOf(CartItem(product(116.0, tax = 0.16), quantity = 2)))
        assertEquals(232.0, state.subtotal, 0.0)
        assertEquals(32.0, state.totalTax, 0.001)
    }

    @Test fun `ten cents added ten times is exactly one shilling`() {
        val items = List(10) { CartItem(product(0.1), quantity = 1) }
        assertEquals(1.0, CartUiState(items = items).total, 0.0)
    }

    @Test fun `line discount reduces total once and shows in totalDiscount`() {
        val state = CartUiState(items = listOf(CartItem(product(100.0), quantity = 1, discount = 30.0)))
        assertEquals(30.0, state.totalDiscount, 0.0)
        assertEquals(70.0, state.total, 0.0)
    }

    @Test fun `a fully discounted cart totals zero but is not empty`() {
        val state = CartUiState(items = listOf(CartItem(product(40.0))), discount = 40.0)
        assertEquals(0.0, state.total, 0.0)
        assertFalse(state.isEmpty)
    }
}
