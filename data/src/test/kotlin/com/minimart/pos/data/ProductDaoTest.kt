package com.minimart.pos.data

import android.database.sqlite.SQLiteConstraintException
import com.minimart.pos.data.entity.Product
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ProductDaoTest : DbTestBase() {
    private val dao get() = db.productDao()

    private fun product(barcode: String, name: String = "Item $barcode", stock: Int = 10) =
        Product(barcode = barcode, name = name, price = 50.0, stock = stock)

    @Test fun duplicateBarcodeIsRejectedInsteadOfReplacingTheOtherProduct() = runBlocking {
        dao.insertProduct(product("111", "First"))
        try {
            dao.insertProduct(product("111", "Second"))
            fail("a second product with the same barcode must not be inserted")
        } catch (_: SQLiteConstraintException) { }
        assertEquals("First", dao.getProductByBarcode("111")?.name)
        assertEquals(1, dao.getProductCount())
    }

    @Test fun decrementStockRefusesToGoNegative() = runBlocking {
        val id = dao.insertProduct(product("222", stock = 3))
        assertEquals(0, dao.decrementStock(id, 5))
        assertEquals(3, dao.getProductById(id)?.stock)
        assertEquals(1, dao.decrementStock(id, 3))
        assertEquals(0, dao.getProductById(id)?.stock)
    }

    @Test fun clampedDecrementStopsAtZero() = runBlocking {
        val id = dao.insertProduct(product("333", stock = 2))
        dao.decrementStockClamped(id, 9)
        assertEquals(0, dao.getProductById(id)?.stock)
    }

    @Test fun incrementPutsStockBack() = runBlocking {
        val id = dao.insertProduct(product("444", stock = 1))
        dao.incrementStock(id, 4)
        assertEquals(5, dao.getProductById(id)?.stock)
    }

    @Test fun weighedStockKeepsKilogramsAndWholeKgFloorInStep() = runBlocking {
        val id = dao.insertProduct(product("555", stock = 0).copy(isWeighed = true, pluCode = "00123", stockKg = 5.5))
        dao.decrementStockKg(id, 1.25)
        val p = dao.getProductById(id)!!
        assertEquals(4.25, p.stockKg, 1e-9)
        assertEquals(4, p.stock)
        dao.decrementStockKg(id, 100.0) // never below zero
        assertEquals(0.0, dao.getProductById(id)!!.stockKg, 1e-9)
    }

    @Test fun softDeletedProductsAreHiddenButTheirBarcodeCanBeReused() = runBlocking {
        val id = dao.insertProduct(product("666", "Old"))
        dao.softDeleteProduct(id)
        assertNull(dao.getProductByBarcode("666"))
        assertNotNull(dao.getAnyByBarcode("666"))
        dao.releaseDeletedBarcode("666")
        dao.insertProduct(product("666", "New")) // would violate the unique index without the release
        assertEquals("New", dao.getProductByBarcode("666")?.name)
    }

    @Test fun searchPutsAnExactBarcodeMatchFirst() = runBlocking {
        dao.insertProduct(product("700", "Cola 700ml"))
        dao.insertProduct(product("12345", "Fizzy drink"))
        dao.insertProduct(product("999", "Soda 12345 pack"))
        val hits = dao.searchProducts("12345").first()
        assertEquals("12345", hits.first().barcode)
        assertEquals(2, hits.size)
    }

    @Test fun lowStockListsOnlyProductsAtOrBelowTheirThreshold() = runBlocking {
        dao.insertProduct(product("801", stock = 2).copy(lowStockThreshold = 5))
        dao.insertProduct(product("802", stock = 50).copy(lowStockThreshold = 5))
        val low = dao.getLowStockProducts().first()
        assertTrue(low.all { it.stock <= it.lowStockThreshold })
        assertEquals(listOf("801"), low.map { it.barcode })
    }
}
