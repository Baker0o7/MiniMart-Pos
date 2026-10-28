package com.minimart.pos.util

import com.minimart.pos.util.CheckoutRules.Kind
import com.minimart.pos.util.CheckoutRules.canComplete
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CheckoutRulesTest {
    @Test fun `empty or zero total can never be completed`() {
        assertFalse(canComplete(0.0, Kind.OTHER, 0.0))
        assertFalse(canComplete(0.0, Kind.CASH, 100.0))
    }
    @Test fun `cash must cover the total`() {
        assertFalse(canComplete(100.0, Kind.CASH, 99.0))
        assertTrue(canComplete(100.0, Kind.CASH, 100.0))
        assertTrue(canComplete(100.0, Kind.CASH, 500.0))
    }
    @Test fun `exact cash for a fractional total is accepted`() {
        assertTrue(canComplete(99.99, Kind.CASH, 99.99))
        assertFalse(canComplete(99.99, Kind.CASH, 99.98))
    }
    @Test fun `M-Pesa follows the checks, not the cash box`() {
        assertFalse(canComplete(50.0, Kind.MPESA, 1000.0, mpesaAccepted = false))
        assertTrue(canComplete(50.0, Kind.MPESA, 0.0, mpesaAccepted = true))
    }
    @Test fun `credit needs a customer`() {
        assertFalse(canComplete(50.0, Kind.CREDIT, 0.0))
        assertTrue(canComplete(50.0, Kind.CREDIT, 0.0, hasCustomer = true))
    }
    @Test fun `split needs a customer, some credit and cash for the rest`() {
        assertFalse(canComplete(100.0, Kind.CREDIT, 60.0, hasCustomer = false, split = 40.0))
        assertFalse(canComplete(100.0, Kind.CREDIT, 60.0, hasCustomer = true, split = 0.0))
        assertFalse(canComplete(100.0, Kind.CREDIT, 59.0, hasCustomer = true, split = 40.0))
        assertTrue(canComplete(100.0, Kind.CREDIT, 60.0, hasCustomer = true, split = 40.0))
    }
    @Test fun `split covering the whole total needs no cash`() {
        assertTrue(canComplete(100.0, Kind.CREDIT, 0.0, hasCustomer = true, split = 100.0))
    }
    @Test fun `change is never negative`() {
        assertEquals(0.0, CheckoutRules.change(100.0, 40.0), 0.0)
        assertEquals(25.5, CheckoutRules.change(74.5, 100.0), 0.0)
    }
}
