package com.minimart.pos.util

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.minimart.pos.data.entity.PaymentMethod
import com.minimart.pos.data.entity.Sale
import com.minimart.pos.data.entity.SaleItem
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ReceiptData(
    val sale: Sale,
    val items: List<SaleItem>,
    val productNames: Map<Long, String>,  // productId -> name
    val storeName: String,
    val currency: String,
    val cashierName: String,
    val footerMessage: String
)

object PdfReceiptGenerator {

    private const val PAGE_WIDTH  = 400
    private const val MARGIN      = 24f
    private val df = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    fun generate(context: Context, data: ReceiptData): File {
        val dir = File(context.filesDir, "receipts").apply { mkdirs() }
        val file = File(dir, "receipt_${data.sale.id}_${System.currentTimeMillis()}.pdf")

        val doc  = PdfDocument()
        val lineH = 22f
        var y     = MARGIN + 10f

        // Measure total height needed
        val lineCount = 8 + data.items.size * 2 + 6
        val pageHeight = (lineCount * lineH + 160).toInt().coerceAtLeast(600)

        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, pageHeight, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        // ── Paints ──────────────────────────────────────────────────────────
        val boldPaint = Paint().apply { isAntiAlias = true; color = Color.BLACK; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        val normPaint = Paint().apply { isAntiAlias = true; color = Color.BLACK; textSize = 13f; typeface = Typeface.DEFAULT }
        val smallPaint = Paint().apply { isAntiAlias = true; color = Color.DKGRAY; textSize = 11f }
        val rightPaint = Paint().apply { isAntiAlias = true; color = Color.BLACK; textSize = 13f; textAlign = Paint.Align.RIGHT }
        val linePaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }
        val tealPaint = Paint().apply { isAntiAlias = true; color = Color.parseColor("#00897B"); textSize = 15f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        val totalPaint = Paint().apply { isAntiAlias = true; color = Color.parseColor("#00897B"); textSize = 18f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT }

        val cx = PAGE_WIDTH / 2f
        val rw = PAGE_WIDTH - MARGIN

        // ── Store name ──────────────────────────────────────────────────────
        canvas.drawText(data.storeName.uppercase(), cx, y + 18f, boldPaint.apply { textSize = 18f })
        y += 28f
        canvas.drawText("RECEIPT", cx, y, tealPaint)
        y += 20f
        canvas.drawLine(MARGIN, y, rw, y, linePaint)
        y += 14f

        // ── Meta ─────────────────────────────────────────────────────────────
        canvas.drawText("Date:    ${df.format(Date(data.sale.createdAt))}", MARGIN, y, smallPaint)
        y += lineH * 0.9f
        canvas.drawText("Receipt: #${data.sale.id}", MARGIN, y, smallPaint)
        canvas.drawText("Cashier: ${data.cashierName}", cx, y, smallPaint)
        y += lineH * 0.9f
        canvas.drawLine(MARGIN, y, rw, y, linePaint)
        y += 14f

        // ── Items ────────────────────────────────────────────────────────────
        canvas.drawText("Item", MARGIN, y, normPaint.apply { typeface = Typeface.DEFAULT_BOLD })
        canvas.drawText("Qty", cx - 20f, y, normPaint)
        canvas.drawText("Total", rw, y, rightPaint.apply { typeface = Typeface.DEFAULT_BOLD })
        y += 6f
        canvas.drawLine(MARGIN, y, rw, y, linePaint)
        y += 14f
        normPaint.typeface = Typeface.DEFAULT
        rightPaint.typeface = Typeface.DEFAULT

        data.items.forEach { item ->
            val name = data.productNames[item.productId] ?: "Item #${item.productId}"
            val lineTotal = item.unitPrice * item.quantity
            canvas.drawText(name.take(28), MARGIN, y, normPaint)
            canvas.drawText("×${item.quantity}", cx - 20f, y, normPaint)
            canvas.drawText("${data.currency} ${String.format("%.2f", lineTotal)}", rw, y, rightPaint)
            y += lineH * 0.85f
            canvas.drawText("@ ${data.currency} ${String.format("%.2f", item.unitPrice)} each", MARGIN + 8f, y, smallPaint)
            y += lineH * 0.75f
        }

        y += 4f
        canvas.drawLine(MARGIN, y, rw, y, linePaint)
        y += 14f

        // ── Totals ────────────────────────────────────────────────────────────
        if (data.sale.discountAmount > 0) {
            canvas.drawText("Discount:", MARGIN, y, normPaint)
            canvas.drawText("-${data.currency} ${String.format("%.2f", data.sale.discountAmount)}", rw, y, rightPaint)
            y += lineH
        }
        if (data.sale.taxAmount > 0) {
            canvas.drawText("Tax (16% VAT):", MARGIN, y, normPaint)
            canvas.drawText("${data.currency} ${String.format("%.2f", data.sale.taxAmount)}", rw, y, rightPaint)
            y += lineH
        }
        canvas.drawLine(MARGIN, y, rw, y, linePaint)
        y += 14f
        canvas.drawText("TOTAL", MARGIN, y + 2f, boldPaint.apply { textAlign = Paint.Align.LEFT; textSize = 16f })
        canvas.drawText("${data.currency} ${String.format("%.2f", data.sale.totalAmount)}", rw, y + 2f, totalPaint)
        y += lineH + 4f

        // ── Payment ──────────────────────────────────────────────────────────
        val payLabel = when (data.sale.paymentMethod) {
            PaymentMethod.CASH  -> "Cash"
            PaymentMethod.MPESA -> "M-Pesa"
            PaymentMethod.CARD  -> "Card"
            else -> "Cash"
        }
        canvas.drawText("Payment: $payLabel", MARGIN, y, smallPaint)
        if (data.sale.paymentMethod == PaymentMethod.CASH && data.sale.amountPaid > 0) {
            val change = data.sale.amountPaid - data.sale.totalAmount
            canvas.drawText("Paid: ${data.currency} ${String.format("%.2f", data.sale.amountPaid)}   Change: ${data.currency} ${String.format("%.2f", change.coerceAtLeast(0.0))}", MARGIN, y + lineH * 0.85f, smallPaint)
            y += lineH
        }
        if (data.sale.mpesaRef?.isNotBlank() == true) {
            canvas.drawText("M-Pesa Ref: ${data.sale.mpesaRef}", MARGIN, y + lineH * 0.85f, smallPaint)
            y += lineH
        }

        y += 12f
        canvas.drawLine(MARGIN, y, rw, y, linePaint)
        y += 16f

        // ── Footer ────────────────────────────────────────────────────────────
        canvas.drawText(data.footerMessage, cx, y, tealPaint.apply { textSize = 12f })
        y += lineH
        canvas.drawText("Thank you for your business!", cx, y, tealPaint)

        doc.finishPage(page)
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()

        return file
    }

    fun getShareUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun shareViaWhatsApp(context: Context, uri: Uri, storeName: String, total: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Receipt from $storeName — Total: $total")
            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // If WhatsApp not installed fall back to generic share
        val chooser = try {
            context.startActivity(intent); null
        } catch (e: Exception) {
            Intent.createChooser(
                intent.apply { setPackage(null) }, "Share Receipt"
            )
        }
        chooser?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun shareGeneric(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share Receipt").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
