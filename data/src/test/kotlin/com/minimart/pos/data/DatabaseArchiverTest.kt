package com.minimart.pos.data

import com.minimart.pos.data.entity.CreditTransaction
import com.minimart.pos.data.entity.CreditTxType
import com.minimart.pos.data.entity.Customer
import com.minimart.pos.data.entity.PaymentMethod
import com.minimart.pos.data.entity.Product
import com.minimart.pos.data.entity.Sale
import com.minimart.pos.data.entity.SaleItem
import com.minimart.pos.data.entity.SyncEntityType
import com.minimart.pos.data.entity.SyncLog
import com.minimart.pos.data.entity.SyncOperation
import com.minimart.pos.data.entity.SyncStatus
import com.minimart.pos.data.repository.ArchiveException
import com.minimart.pos.data.repository.DatabaseArchiver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.GZIPInputStream

class DatabaseArchiverTest : DbTestBase() {
    @get:Rule val tmp = TemporaryFolder()
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_800_000_000_000L   // fixed "today"
    private val archiver get() = DatabaseArchiver(db)

    private suspend fun makeSale(receipt: String, ageDays: Int, name: String = "Sugar, 1kg", total: Double = 100.0): Long {
        val pid = db.productDao().insertProduct(Product(barcode = "b-$receipt", name = name, price = total, stock = 10))
        val sale = Sale(receiptNumber = receipt, subtotal = total, taxAmount = 0.0, totalAmount = total, amountPaid = total,
            changeGiven = 0.0, cashPortion = total, paymentMethod = PaymentMethod.CASH, cashierId = 1, notes = "=cmd|' /C calc'!A0",
            createdAt = now - ageDays * day)
        val item = SaleItem(saleId = 0, productId = pid, productBarcode = "b-$receipt", productName = name,
            unitPrice = total, quantity = 2, lineTotal = total, costTotal = 60.0)
        return db.saleDao().insertSaleWithItems(sale, listOf(item))
    }

    private fun read(f: File) = GZIPInputStream(f.inputStream()).bufferedReader().readText()
    private fun count(table: String) = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    @Test fun nothingIsArchivedWhenEverythingIsRecent() = runBlocking {
        makeSale("NEW-1", 10); makeSale("NEW-2", 300)
        assertTrue(archiver.plan(12, now).isEmpty)
        try { archiver.archive(12, now, File(tmp.root, "a.csv.gz")); fail("expected ArchiveException") } catch (_: ArchiveException) {}
        assertEquals(2, count("sales"))
    }

    @Test fun oldSalesAreWrittenToFileThenRemoved() = runBlocking {
        makeSale("OLD-1", 800); makeSale("OLD-2", 900, name = "Milk \"fresh\"")
        makeSale("KEEP-1", 100)
        val plan = archiver.plan(12, now)
        assertEquals(2, plan.sales); assertEquals(2, plan.items)

        val file = File(tmp.root, "archive.csv.gz")
        val result = archiver.archive(12, now, file)
        assertEquals(2, result.sales); assertEquals(2, result.items); assertTrue(file.exists()); assertTrue(result.bytes > 0)
        assertFalse(File(tmp.root, "archive.csv.gz.tmp").exists())

        val text = read(file)
        assertTrue(text.startsWith("receipt,date,status"))
        assertTrue(text.contains("OLD-1")); assertTrue(text.contains("OLD-2")); assertFalse(text.contains("KEEP-1"))
        assertTrue("quotes doubled", text.contains("\"Milk \"\"fresh\"\"\""))
        assertTrue("comma field quoted", text.contains("\"Sugar, 1kg\""))
        assertTrue("formulas defused", text.contains("'=cmd"))
        assertEquals(3, text.trim().lines().size)   // header + 2 item rows

        assertEquals(1, count("sales")); assertEquals(1, count("sale_items"))
        assertEquals(0, archiver.plan(12, now).sales)
    }

    @Test fun creditLedgerSurvivesButNoLongerPointsAtTheArchivedSale() = runBlocking {
        val saleId = makeSale("OLD-3", 700)
        val cid = db.customerDao().insertCustomer(Customer(name = "Wanjiru", phone = "0700000000", creditBalance = 500.0))
        db.customerDao().insertCreditTx(CreditTransaction(customerId = cid, amount = 500.0, type = CreditTxType.CREDIT_ADDED, saleId = saleId, balanceAfter = 500.0))
        archiver.archive(12, now, File(tmp.root, "b.csv.gz"))
        assertEquals(1, count("credit_transactions"))
        db.openHelper.readableDatabase.query("SELECT saleId FROM credit_transactions").use { c -> c.moveToFirst(); assertTrue(c.isNull(0)) }
    }

    @Test fun routineMaintenanceRemovesOnlyOldSyncedRecords() = runBlocking {
        fun log(status: SyncStatus, ageDays: Int, id: Long) = SyncLog(entityType = SyncEntityType.SALE, entityId = id, operation = SyncOperation.CREATE,
            deviceId = "d", payload = "{}", status = status, createdAt = now - ageDays * day)
        db.syncDao().insertLog(log(SyncStatus.SYNCED, 60, 1))     // old and done: removed
        db.syncDao().insertLog(log(SyncStatus.SYNCED, 5, 2))      // recent: kept
        db.syncDao().insertLog(log(SyncStatus.PENDING, 90, 3))    // old but not yet delivered: kept
        db.syncDao().insertLog(log(SyncStatus.CONFLICT, 90, 4))   // refused: kept
        assertEquals(1, archiver.routineMaintenance(now))
        assertEquals(3, count("sync_log"))
    }
}
