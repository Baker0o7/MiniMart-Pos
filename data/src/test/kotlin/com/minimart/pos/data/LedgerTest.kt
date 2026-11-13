package com.minimart.pos.data

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.minimart.pos.data.db.AppMigrations
import com.minimart.pos.data.entity.LedgerEntry
import com.minimart.pos.data.entity.PaymentMethod
import com.minimart.pos.data.entity.Sale
import com.minimart.pos.data.entity.SaleStatus
import com.minimart.pos.data.repository.IntegrityChecker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shadow ledger: a second, append-only record of every sale and reversal, checked against the sales table. */
class LedgerTest : DbTestBase() {
    private fun sale(n: Int, total: Double, method: PaymentMethod = PaymentMethod.CASH, cash: Double = total, status: SaleStatus = SaleStatus.COMPLETED) = Sale(
        receiptNumber = "L-$n", subtotal = total, taxAmount = 0.0, totalAmount = total, amountPaid = total, changeGiven = 0.0,
        cashPortion = cash, paymentMethod = method, cashierId = 1L, status = status, createdAt = 10_000L + n)

    private fun ledgerIssues() = IntegrityChecker(db).check().issues.filter { it.contains("ledger") }

    @Test fun savingASaleWritesOneSaleLineInCents() = runBlocking {
        val id = db.saleDao().insertSaleWithItems(sale(1, 120.55, PaymentMethod.MIXED, cash = 20.05), emptyList())
        val lines = db.saleDao().ledgerFor(id)
        assertEquals(1, lines.size)
        assertEquals(LedgerEntry.SALE, lines[0].kind)
        assertEquals(12055L, lines[0].cents); assertEquals(2005L, lines[0].cashCents); assertEquals("MIXED", lines[0].method)
        assertTrue(ledgerIssues().isEmpty())
    }

    @Test fun aRefundAndAVoidEachAddOneReversalAndOnlyOnce() = runBlocking {
        val a = db.saleDao().insertSaleWithItems(sale(1, 50.0), emptyList())
        val b = db.saleDao().insertSaleWithItems(sale(2, 75.25), emptyList())
        assertEquals(1, db.saleDao().refundSale(a, "wrong item"))
        assertEquals(0, db.saleDao().refundSale(a, "again"))
        assertEquals(0, db.saleDao().voidSale(a, "too late"))
        assertEquals(1, db.saleDao().voidSale(b, "mistake"))
        assertEquals(listOf(5000L, -5000L), db.saleDao().ledgerFor(a).map { it.cents })
        assertEquals(listOf(7525L, -7525L), db.saleDao().ledgerFor(b).map { it.cents })
        assertTrue(ledgerIssues().isEmpty())
    }

    @Test fun aSaleThatArrivesAlreadyReversedBalancesToZero() = runBlocking {
        val id = db.saleDao().insertSaleWithItems(sale(1, 30.0, status = SaleStatus.VOIDED), emptyList())
        assertEquals(0L, db.saleDao().ledgerFor(id).sumOf { it.cents })
        assertTrue(ledgerIssues().isEmpty())
    }

    @Test fun theCheckNoticesASaleThatWasChangedBehindTheLedgersBack() = runBlocking {
        val id = db.saleDao().insertSaleWithItems(sale(1, 100.0), emptyList())
        db.openHelper.writableDatabase.execSQL("UPDATE sales SET totalAmount = 90.0 WHERE id = $id")
        assertTrue(ledgerIssues().single().contains("ledger says 100.00 but the sale says 90.00"))
    }

    @Test fun theCheckNoticesAReversalThatNeverWasRecorded() = runBlocking {
        val id = db.saleDao().insertSaleWithItems(sale(1, 100.0), emptyList())
        db.openHelper.writableDatabase.execSQL("UPDATE sales SET status = 'VOIDED' WHERE id = $id")
        assertTrue(ledgerIssues().single().contains("ledger says 100.00 but the sale says 0.00"))
    }

    @Test fun theCheckNoticesASaleThatBypassedTheLedger() = runBlocking {
        db.saleDao().insertSale(sale(1, 10.0))
        assertTrue(ledgerIssues().single().contains("missing from the shadow ledger"))
    }

    @Test fun archivedSalesLeaveTheirLedgerLinesAndNoAlarm() = runBlocking {
        val id = db.saleDao().insertSaleWithItems(sale(1, 10.0), emptyList())
        db.openHelper.writableDatabase.execSQL("DELETE FROM sales WHERE id = $id")
        assertEquals(1, db.saleDao().ledgerFor(id).size)
        assertTrue(ledgerIssues().isEmpty())
    }

    @Test fun manyMixedSalesAndReversalsAlwaysBalance() = runBlocking {
        val ids = (1..200).map { db.saleDao().insertSaleWithItems(sale(it, it * 3.37), emptyList()) }
        ids.filterIndexed { i, _ -> i % 4 == 0 }.forEach { db.saleDao().refundSale(it, "r") }
        ids.filterIndexed { i, _ -> i % 9 == 1 }.forEach { db.saleDao().voidSale(it, "v") }
        assertTrue(ledgerIssues().isEmpty())
    }
}

/** The 19→20 step, and that the table it makes is the one Room itself would make. */
class Migration19To20Test : DbTestBase() {
    private fun bareHelper(version: Int, create: (SupportSQLiteDatabase) -> Unit): SupportSQLiteOpenHelper {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        return androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(ctx).name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) = create(db)
                    override fun onUpgrade(db: SupportSQLiteDatabase, o: Int, n: Int) {}
                }).build())
    }

    private fun shape(db: SupportSQLiteDatabase, table: String): List<List<String>> =
        db.query("PRAGMA table_info(`$table`)").use { c -> generateSequence { if (c.moveToNext()) List(6) { i -> c.getString(i) ?: "" } else null }.toList() }

    @Test fun migrationMakesTheSameColumnsAndIndexRoomMakes() {
        val room = db.openHelper.writableDatabase
        val helper = bareHelper(19) { d ->
            // The migration backfills from `sales`, so an (empty) one must exist.
            d.execSQL("CREATE TABLE sales (id INTEGER PRIMARY KEY, receiptNumber TEXT NOT NULL, totalAmount REAL NOT NULL, cashPortion REAL NOT NULL, " +
                "paymentMethod TEXT NOT NULL, status TEXT NOT NULL, createdAt INTEGER NOT NULL, statusChangedAt INTEGER NOT NULL)")
        }
        val sdb = helper.writableDatabase
        AppMigrations.MIGRATION_19_20.migrate(sdb)
        assertEquals(shape(room, "ledger_entries"), shape(sdb, "ledger_entries"))
        fun indexes(d: SupportSQLiteDatabase) = d.query("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='ledger_entries' AND name NOT LIKE 'sqlite_%'").use { c ->
            generateSequence { if (c.moveToNext()) c.getString(0) else null }.toList() }
        assertEquals(indexes(room), indexes(sdb))
        helper.close()
    }

    @Test fun existingSalesGetTheirLedgerLines() {
        val helper = bareHelper(19) { d ->
            d.execSQL("CREATE TABLE sales (id INTEGER PRIMARY KEY, receiptNumber TEXT NOT NULL, totalAmount REAL NOT NULL, cashPortion REAL NOT NULL, " +
                "paymentMethod TEXT NOT NULL, status TEXT NOT NULL, createdAt INTEGER NOT NULL, statusChangedAt INTEGER NOT NULL)")
        }
        val sdb = helper.writableDatabase
        sdb.execSQL("INSERT INTO sales VALUES (1,'A',100.5,100.5,'CASH','COMPLETED',1000,0), (2,'B',20.0,5.0,'MIXED','REFUNDED',2000,2500), (3,'C',9.99,0.0,'CARD','VOIDED',3000,0)")
        AppMigrations.MIGRATION_19_20.migrate(sdb)
        val rows = sdb.query("SELECT saleId, kind, cents, cashCents, at FROM ledger_entries ORDER BY saleId, id").use { c ->
            generateSequence { if (c.moveToNext()) listOf(c.getLong(0), c.getString(1), c.getLong(2), c.getLong(3), c.getLong(4)) else null }.toList()
        }
        assertEquals(listOf(
            listOf(1L, "SALE", 10050L, 10050L, 1000L),
            listOf(2L, "SALE", 2000L, 500L, 2000L), listOf(2L, "REVERSAL", -2000L, -500L, 2500L),
            listOf(3L, "SALE", 999L, 0L, 3000L), listOf(3L, "REVERSAL", -999L, 0L, 3000L)), rows)
        helper.close()
    }
}
