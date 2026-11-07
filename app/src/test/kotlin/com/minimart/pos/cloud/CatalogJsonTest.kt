package com.minimart.pos.cloud

import com.minimart.pos.util.CatalogItem
import com.minimart.pos.util.CatalogRules
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogJsonTest {
    @Test fun roundTripKeepsEveryField() {
        val item = CatalogItem("6001", "Milk", 120.5, 90.0, "SKU1", "Dairy", "ltr", 16.0, "Brookside", "0712345678", "12", true, 80.0, false)
        val back = CatalogJson.parse(CatalogJson.toJson(listOf(item)))
        assertEquals(listOf(item), back.items); assertEquals(0, back.skipped)
    }

    @Test fun nonObjectsAndIncompleteItemsAreSkipped() {
        val arr = JSONArray("""[1, "x", {"barcode":"1"}, {"name":"A"}, {"barcode":"2","name":"B","price":5}]""")
        val p = CatalogJson.parse(arr)
        assertEquals(listOf("2"), p.items.map { it.barcode }); assertEquals(4, p.skipped)
    }

    @Test fun wrongTypedNumbersAreRejectedByTheRulesNotGuessed() {
        val arr = JSONArray("""[{"barcode":"1","name":"A","price":"free"},{"barcode":"2","name":"B"},{"barcode":"3","name":"C","price":10,"taxRate":"x"}]""")
        val r = CatalogRules.sanitize(CatalogJson.parse(arr).items)
        assertTrue(r.items.isEmpty()); assertEquals(3, r.rejected)
    }

    @Test fun nullListGivesNothing() {
        val p = CatalogJson.parse(null); assertTrue(p.items.isEmpty()); assertEquals(0, p.skipped)
    }
}
