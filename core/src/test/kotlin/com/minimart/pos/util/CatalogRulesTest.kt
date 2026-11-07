package com.minimart.pos.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class CatalogRulesTest {
    private fun item(barcode: String = "6001", name: String = "Milk", price: Double = 120.0) = CatalogItem(barcode, name, price)

    @Test fun goodItemPassesUnchanged() {
        val r = CatalogRules.sanitize(listOf(item()))
        assertEquals(listOf(item()), r.items); assertEquals(0, r.rejected)
    }

    @Test fun badPricesAreRejected() {
        val bad = listOf(item(price = -1.0), item("2", price = Double.NaN), item("3", price = Double.POSITIVE_INFINITY), item("4", price = 1e12),
            item("5").copy(costPrice = -5.0), item("6").copy(pricePerKg = -1.0), item("7").copy(taxRate = 150.0), item("8").copy(taxRate = -1.0))
        val r = CatalogRules.sanitize(bad)
        assertEquals(0, r.items.size); assertEquals(8, r.rejected)
    }

    @Test fun badBarcodesAndNamesAreRejected() {
        val r = CatalogRules.sanitize(listOf(item(barcode = ""), item(barcode = "a b"), item(barcode = "x".repeat(41)), item(barcode = "1;DROP"), item(name = "   ")))
        assertEquals(0, r.items.size); assertEquals(5, r.rejected)
    }

    @Test fun textIsCleanedAndCut() {
        val r = CatalogRules.sanitize(listOf(item(name = "  Mi\nlk\u0007  ").copy(category = "", unit = "", supplierName = "s".repeat(500))))
        val i = r.items.single()
        assertEquals("Milk", i.name); assertEquals("General", i.category); assertEquals("pcs", i.unit); assertEquals(80, i.supplierName.length)
    }

    @Test fun oneBadItemDoesNotStopTheRest() {
        val r = CatalogRules.sanitize(listOf(item("1"), item("2", price = -1.0), item("3")))
        assertEquals(listOf("1", "3"), r.items.map { it.barcode }); assertEquals(1, r.rejected)
    }

    @Test fun repeatedBarcodeKeepsTheLast() {
        val r = CatalogRules.sanitize(listOf(item("1", price = 10.0), item("1", price = 20.0)))
        assertEquals(1, r.items.size); assertEquals(20.0, r.items[0].price, 0.0)
    }

    @Test fun listIsCappedAtTheLimit() {
        val many = (1..CatalogRules.MAX_ITEMS + 10).map { item(it.toString()) }
        val r = CatalogRules.sanitize(many)
        assertEquals(CatalogRules.MAX_ITEMS, r.items.size); assertEquals(10, r.rejected)
    }

    @Test fun fingerprintIgnoresOrderButNoticesChanges() {
        val a = listOf(item("1"), item("2"))
        assertEquals(CatalogRules.fingerprint(a), CatalogRules.fingerprint(a.reversed()))
        assertNotEquals(CatalogRules.fingerprint(a), CatalogRules.fingerprint(listOf(item("1"), item("2", price = 121.0))))
    }
}
