package com.minimart.pos.data

import com.minimart.pos.data.entity.Product
import com.minimart.pos.data.repository.CatalogApplier
import com.minimart.pos.util.CatalogItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogApplierTest : DbTestBase() {
    private fun applier() = CatalogApplier(db, db.productDao())

    @Test fun newProductsAreAddedWithZeroStock() = runBlocking {
        val r = applier().apply(listOf(CatalogItem("6001", "Milk", 120.0, category = "Dairy")))
        assertEquals(1, r.added)
        val p = db.productDao().getAnyByBarcode("6001")!!
        assertEquals("Milk", p.name); assertEquals(0, p.stock); assertEquals("Dairy", p.category)
    }

    @Test fun updateChangesSharedFieldsButNeverStockOrExpiry() = runBlocking {
        db.productDao().insertProduct(Product(barcode = "6001", name = "Milk", price = 100.0, stock = 42, lowStockThreshold = 9,
            batchNumber = "B7", expiryDate = 1234L))
        val r = applier().apply(listOf(CatalogItem("6001", "Fresh Milk", 130.0)))
        assertEquals(1, r.updated)
        val p = db.productDao().getAnyByBarcode("6001")!!
        assertEquals("Fresh Milk", p.name); assertEquals(130.0, p.price, 0.0)
        assertEquals(42, p.stock); assertEquals(9, p.lowStockThreshold); assertEquals("B7", p.batchNumber); assertEquals(1234L, p.expiryDate)
    }

    @Test fun unchangedItemsWriteNothing() = runBlocking {
        applier().apply(listOf(CatalogItem("6001", "Milk", 120.0)), now = 1L)
        val r = applier().apply(listOf(CatalogItem("6001", "Milk", 120.0)), now = 2L)
        assertEquals(0, r.added + r.updated + r.deactivated)
        assertEquals(1L, db.productDao().getAnyByBarcode("6001")!!.updatedAt)
    }

    @Test fun inactiveItemIsNotAddedButDeactivatesAnExistingOne() = runBlocking {
        applier().apply(listOf(CatalogItem("9", "Old", 5.0, isActive = false)))
        assertEquals(null, db.productDao().getAnyByBarcode("9"))
        db.productDao().insertProduct(Product(barcode = "6001", name = "Milk", price = 100.0, stock = 5))
        val r = applier().apply(listOf(CatalogItem("6001", "Milk", 100.0, isActive = false)))
        assertEquals(1, r.deactivated); assertFalse(db.productDao().getAnyByBarcode("6001")!!.isActive)
    }

    @Test fun exportSkipsDeletedProductsAndRoundTrips() = runBlocking {
        db.productDao().insertProduct(Product(barcode = "1", name = "A", price = 1.0, stock = 3))
        db.productDao().insertProduct(Product(barcode = "2~del123", name = "Gone", price = 1.0, stock = 0, isActive = false))
        val items = applier().export()
        assertEquals(listOf("1"), items.map { it.barcode })
        assertTrue(items[0].isActive); assertNotNull(items[0].name)
    }
}
