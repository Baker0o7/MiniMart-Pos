package com.minimart.pos.data

import androidx.room.withTransaction
import com.minimart.pos.data.entity.Product
import com.minimart.pos.data.entity.StockMovement
import com.minimart.pos.data.repository.tagStock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class StockMovementTest : DbTestBase() {
    private fun product(stock: Int = 10, weighed: Boolean = false, kg: Double = 0.0) = runBlocking {
        db.productDao().insertProduct(Product(barcode = "p${System.nanoTime()}", name = "Item", price = 10.0, stock = stock, isWeighed = weighed, stockKg = kg))
    }
    private fun moves(id: Long) = runBlocking { db.stockMovementDao().allFor(id) }

    @Test fun aNewProductWithStockStartsItsHistory() {
        val id = product(stock = 12)
        val m = moves(id).single()
        assertEquals(12.0, m.delta, 0.0); assertEquals(12.0, m.stockAfter, 0.0); assertEquals(StockMovement.Reasons.NEW_PRODUCT, m.reason)
    }

    @Test fun anUntaggedChangeIsRecordedAsOther() = runBlocking {
        val id = product(10)
        db.productDao().decrementStock(id, 3)
        val m = moves(id).last()
        assertEquals(-3.0, m.delta, 0.0); assertEquals(7.0, m.stockAfter, 0.0); assertEquals(StockMovement.Reasons.OTHER, m.reason)
    }

    @Test fun aTaggedChangeCarriesItsReasonAndReference() = runBlocking {
        val id = product(10)
        db.tagStock(StockMovement.Reasons.SALE, 55L) { db.productDao().decrementStock(id, 2) }
        val m = moves(id).last()
        assertEquals(StockMovement.Reasons.SALE, m.reason); assertEquals(55L, m.ref); assertEquals(-2.0, m.delta, 0.0)
        // the label is cleared afterwards: the next change is unexplained again
        db.productDao().incrementStock(id, 1)
        assertEquals(StockMovement.Reasons.OTHER, moves(id).last().reason)
    }

    @Test fun aRolledBackChangeLeavesNoHistory() = runBlocking {
        val id = product(10)
        runCatching {
            db.withTransaction {
                db.tagStock(StockMovement.Reasons.SALE, 1L) { db.productDao().decrementStock(id, 4) }
                throw IllegalStateException("boom")
            }
        }
        assertEquals(1, moves(id).size)   // only the starting line
        assertEquals(10, db.productDao().getProductById(id)!!.stock)
    }

    @Test fun weighedProductsAreTrackedInKilograms() = runBlocking {
        val id = product(stock = 0, weighed = true, kg = 5.5)
        db.productDao().decrementStockKg(id, 1.25)
        val m = moves(id).last()
        assertEquals(-1.25, m.delta, 1e-9); assertEquals(4.25, m.stockAfter, 1e-9)
    }

    @Test fun anUnchangedStockWritesNothing() = runBlocking {
        val id = product(10)
        db.productDao().incrementStock(id, 0)
        assertEquals(1, moves(id).size)
    }

    @Test fun everyMovementChainsOntoThePreviousOne() = runBlocking {
        val id = product(10)
        db.tagStock(StockMovement.Reasons.SALE, 1) { db.productDao().decrementStock(id, 4) }
        db.tagStock(StockMovement.Reasons.RECEIVE, 2) { db.productDao().incrementStock(id, 20) }
        val list = moves(id)
        list.zipWithNext().forEach { (a, b) -> assertEquals(a.stockAfter, b.stockAfter - b.delta, 1e-9) }
        assertEquals(26.0, list.last().stockAfter, 0.0)
    }
}

class Migration23To24Test : DbTestBase() {
    @Test fun addsTheTablesWritesOpeningLinesAndRunsTwice() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val helper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(ctx).name(null)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(23) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE products (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, stock INTEGER NOT NULL, stockKg REAL NOT NULL, isWeighed INTEGER NOT NULL)")
                    }
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, o: Int, n: Int) {}
                }).build())
        val sdb = helper.writableDatabase
        sdb.execSQL("INSERT INTO products (stock, stockKg, isWeighed) VALUES (7, 0, 0), (0, 0, 0), (0, 12.5, 1)")
        com.minimart.pos.data.db.AppMigrations.MIGRATION_23_24.migrate(sdb)
        com.minimart.pos.data.db.AppMigrations.MIGRATION_23_24.migrate(sdb)   // again: nothing doubles
        sdb.query("SELECT productId, delta, reason, stockAfter FROM stock_movements ORDER BY productId").use { c ->
            assertEquals(2, c.count)
            c.moveToFirst(); assertEquals(7.0, c.getDouble(1), 0.0); assertEquals("OPENING", c.getString(2))
            c.moveToNext(); assertEquals(12.5, c.getDouble(3), 0.0)
        }
        helper.close()
    }
}
