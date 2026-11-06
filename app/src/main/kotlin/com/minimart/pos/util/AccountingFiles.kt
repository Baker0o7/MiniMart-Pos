package com.minimart.pos.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.minimart.pos.data.entity.Expense
import com.minimart.pos.data.entity.Sale
import java.io.File
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/**
 * Writes the accountant's CSV files (journal, profit and loss) into the app's own folder and shares them.
 * The files go in archives/exports, which the share provider already covers, so no new folder is exposed.
 * A byte-order mark is written first so Excel shows names and symbols correctly.
 */
object AccountingFiles {
    private const val KEEP = 8

    fun write(
        context: Context, sales: List<Sale>, costs: Map<Long, Double>, expenses: List<Expense>,
        storeName: String, currency: String, periodLabel: String, now: Long = System.currentTimeMillis()
    ): List<File> {
        val acctSales = sales.map {
            AcctSale(it.createdAt, it.receiptNumber, it.paymentMethod.name, Money.fromDouble(it.totalAmount), Money.fromDouble(it.taxAmount),
                Money.fromDouble(it.cashPortion), Money.fromDouble(costs[it.id] ?: 0.0), it.mpesaRef.orEmpty())
        }
        val acctExpenses = expenses.map {
            AcctExpense(it.createdAt, it.category.name, it.title, Money.fromDouble(it.amount), it.receiptRef, it.supplierName)
        }
        val journal = AccountingExport.journalCsv(AccountingExport.journal(acctSales, acctExpenses, ZoneId.systemDefault()), currency)
        val pnl = AccountingExport.profitAndLossCsv(AccountingExport.profitAndLoss(acctSales, acctExpenses), storeName, periodLabel, currency)

        val dir = File(context.filesDir, "archives/exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(now))
        val files = listOf(
            File(dir, "journal_$stamp.csv").also { it.writeBytes(BOM + journal.toByteArray(Charsets.UTF_8)) },
            File(dir, "profit_and_loss_$stamp.csv").also { it.writeBytes(BOM + pnl.toByteArray(Charsets.UTF_8)) }
        )
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(KEEP)?.forEach { it.delete() }
        return files
    }

    fun share(context: Context, files: List<File>, subject: String) {
        val uris = ArrayList(files.map { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) })
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/csv"; putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putExtra(Intent.EXTRA_SUBJECT, subject); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Send to your accountant").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private val BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
}
