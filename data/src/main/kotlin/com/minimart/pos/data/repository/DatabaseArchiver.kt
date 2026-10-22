package com.minimart.pos.data.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import com.minimart.pos.data.db.AppDatabase
import com.minimart.pos.util.ArchivePlan
import com.minimart.pos.util.ArchivePolicy
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.time.Instant
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class ArchiveResult(val file: File, val sales: Int, val items: Int, val bytes: Long)

class ArchiveException(message: String) : Exception(message)

/**
 * Keeps the live database small and fast. Two jobs:
 *  - [routineMaintenance]: safe housekeeping that needs no decision (old, already-synced change records).
 *  - [archive]: moves sales older than a chosen age (at least a year) into a compressed CSV file the owner keeps, then
 *    removes them from the phone. The file is written and checked first; if anything looks wrong nothing is deleted.
 */
@Singleton
class DatabaseArchiver @Inject constructor(private val db: AppDatabase) {

    fun plan(months: Int, nowMs: Long = System.currentTimeMillis()): ArchivePlan {
        val cutoff = ArchivePolicy.cutoff(nowMs, months)
        var sales = 0; var oldest: Long? = null; var newest: Long? = null
        db.query(SimpleSQLiteQuery("SELECT COUNT(*), MIN(createdAt), MAX(createdAt) FROM sales WHERE createdAt < ?", arrayOf(cutoff))).use { c ->
            if (c.moveToFirst()) {
                sales = c.getInt(0)
                if (sales > 0) { oldest = c.getLong(1); newest = c.getLong(2) }
            }
        }
        var items = 0
        if (sales > 0) db.query(SimpleSQLiteQuery(
            "SELECT COUNT(*) FROM sale_items WHERE saleId IN (SELECT id FROM sales WHERE createdAt < ?)", arrayOf(cutoff))).use { c ->
            if (c.moveToFirst()) items = c.getInt(0)
        }
        return ArchivePlan(cutoff, sales, items, oldest, newest)
    }

    /** Writes the archive file, checks it, then deletes the archived sales. Throws [ArchiveException] without deleting on any doubt. */
    fun archive(months: Int, nowMs: Long, file: File): ArchiveResult {
        val plan = plan(months, nowMs)
        if (plan.isEmpty) throw ArchiveException("There are no sales old enough to archive.")
        val cutoff = plan.cutoffMs

        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        var salesWritten = 0; var itemsWritten = 0
        try {
            FileOutputStream(tmp).use { fos ->
                val gz = GZIPOutputStream(fos)
                OutputStreamWriter(gz, Charsets.UTF_8).use { w ->
                    w.write(ArchivePolicy.CSV_HEADER); w.write("\n")
                    var lastSale = -1L
                    db.query(SimpleSQLiteQuery("""
                        SELECT s.id, s.receiptNumber, s.createdAt, s.status, s.paymentMethod, s.mpesaRef, s.cashierId,
                               s.subtotal, s.taxAmount, s.discountAmount, s.totalAmount, s.amountPaid, s.cashPortion, s.notes,
                               si.id, si.productBarcode, si.productName, si.quantity, si.weightKg, si.unitPrice,
                               si.discountAmount, si.taxAmount, si.lineTotal, si.costTotal
                        FROM sales s LEFT JOIN sale_items si ON si.saleId = s.id
                        WHERE s.createdAt < ? ORDER BY s.id, si.id
                    """.trimIndent(), arrayOf(cutoff))).use { c ->
                        while (c.moveToNext()) {
                            val id = c.getLong(0)
                            if (id != lastSale) { salesWritten++; lastSale = id }
                            val hasItem = !c.isNull(14)
                            if (hasItem) itemsWritten++
                            fun s(i: Int) = if (c.isNull(i)) "" else c.getString(i)
                            val row = listOf(
                                s(1), Instant.ofEpochMilli(c.getLong(2)).toString(), s(3), s(4), s(5), s(6),
                                s(7), s(8), s(9), s(10), s(11), s(12), s(13),
                                if (hasItem) s(15) else "", if (hasItem) s(16) else "", if (hasItem) s(17) else "",
                                if (hasItem) s(18) else "", if (hasItem) s(19) else "", if (hasItem) s(20) else "",
                                if (hasItem) s(21) else "", if (hasItem) s(22) else "", if (hasItem) s(23) else ""
                            )
                            w.write(ArchivePolicy.csvRow(row)); w.write("\n")
                        }
                    }
                    // Finish the compressed stream and force it to disk BEFORE the streams close (closing releases the file).
                    w.flush(); gz.finish(); fos.flush(); fos.fd.sync()
                }
            }
            if (salesWritten != plan.sales || itemsWritten != plan.items)
                throw ArchiveException("The archive did not match the database (sales $salesWritten of ${plan.sales}). Nothing was deleted.")
            if (!tmp.renameTo(file)) throw ArchiveException("Could not save the archive file. Nothing was deleted.")
        } catch (e: Exception) {
            tmp.delete()
            if (e is ArchiveException) throw e
            throw ArchiveException("Could not write the archive file (${e.localizedMessage ?: "unknown error"}). Nothing was deleted.")
        }

        // Only now that the file is safely on disk are the rows removed, all or nothing.
        db.runInTransaction(Runnable {
            val sdb = db.openHelper.writableDatabase
            val oldSales = "SELECT id FROM sales WHERE createdAt < ?"
            sdb.execSQL("DELETE FROM sale_item_costs WHERE saleItemId IN (SELECT id FROM sale_items WHERE saleId IN ($oldSales))", arrayOf(cutoff))
            // The customer's credit ledger stays; it just no longer points at a receipt that has left the phone.
            sdb.execSQL("UPDATE credit_transactions SET saleId = NULL WHERE saleId IN ($oldSales)", arrayOf(cutoff))
            sdb.execSQL("DELETE FROM sale_items WHERE saleId IN ($oldSales)", arrayOf(cutoff))
            sdb.execSQL("DELETE FROM sales WHERE createdAt < ?", arrayOf(cutoff))
        })
        compact()
        return ArchiveResult(file, salesWritten, itemsWritten, file.length())
    }

    /** Housekeeping that never needs a decision. Returns how many change records were removed. */
    fun routineMaintenance(nowMs: Long = System.currentTimeMillis()): Int {
        val sdb = db.openHelper.writableDatabase
        val before = count("SELECT COUNT(*) FROM sync_log WHERE status = 'SYNCED' AND createdAt < ${ArchivePolicy.syncLogCutoff(nowMs)}")
        if (before > 0) db.runInTransaction(Runnable {
            sdb.execSQL("DELETE FROM sync_log WHERE status = 'SYNCED' AND createdAt < ?", arrayOf(ArchivePolicy.syncLogCutoff(nowMs)))
        })
        try { sdb.execSQL("PRAGMA optimize") } catch (_: Exception) {}
        return before
    }

    /** Hands the freed pages back and refreshes the query planner's statistics. Failure only means the file stays larger. */
    private fun compact() {
        val sdb = db.openHelper.writableDatabase
        try { sdb.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() } } catch (_: Exception) {}
        try { sdb.execSQL("VACUUM") } catch (_: Exception) {}
        try { sdb.execSQL("ANALYZE") } catch (_: Exception) {}
    }

    private fun count(sql: String): Int = db.query(SimpleSQLiteQuery(sql)).use { if (it.moveToFirst()) it.getInt(0) else 0 }
}
