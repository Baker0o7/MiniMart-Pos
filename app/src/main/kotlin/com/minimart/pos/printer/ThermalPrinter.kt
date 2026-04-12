package com.minimart.pos.printer

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import com.minimart.pos.data.entity.CartItem
import com.minimart.pos.data.entity.PaymentMethod
import com.minimart.pos.data.entity.Sale
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

// ─── ESC/POS command constants ────────────────────────────────────────────────
object EscPos {
    val INIT            = byteArrayOf(0x1B, 0x40)
    val NEWLINE         = byteArrayOf(0x0A)
    val CUT             = byteArrayOf(0x1D, 0x56, 0x41, 0x05)
    val BOLD_ON         = byteArrayOf(0x1B, 0x45, 0x01)
    val BOLD_OFF        = byteArrayOf(0x1B, 0x45, 0x00)
    val ALIGN_LEFT      = byteArrayOf(0x1B, 0x61, 0x00)
    val ALIGN_CENTER    = byteArrayOf(0x1B, 0x61, 0x01)
    val ALIGN_RIGHT     = byteArrayOf(0x1B, 0x61, 0x02)
    val FONT_NORMAL     = byteArrayOf(0x1B, 0x21, 0x00)
    val FONT_LARGE      = byteArrayOf(0x1B, 0x21, 0x30)   // double width + height
    val FONT_MEDIUM     = byteArrayOf(0x1B, 0x21, 0x10)   // double height
    val UNDERLINE_ON    = byteArrayOf(0x1B, 0x2D, 0x01)
    val UNDERLINE_OFF   = byteArrayOf(0x1B, 0x2D, 0x00)
    val DRAWER_KICK     = byteArrayOf(0x1B, 0x70, 0x00, 0x19, 0xFA.toByte()) // cash drawer
    const val LINE_CHARS = 32                               // chars per line on 80mm paper

    fun text(s: String) = s.toByteArray(Charsets.UTF_8)
    fun divider(char: Char = '-') = text(char.toString().repeat(LINE_CHARS)) + NEWLINE
}

sealed class PrintResult {
    object Success : PrintResult()
    data class Error(val message: String) : PrintResult()
}

@Singleton
class ThermalPrinter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ThermalPrinter"
        private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var connectedAddress: String? = null

    // ── Connection ────────────────────────────────────────────────────────────

    suspend fun connect(address: String): PrintResult = withContext(Dispatchers.IO) {
        try {
            disconnect()
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = bluetoothManager.adapter
                ?: return@withContext PrintResult.Error("Bluetooth not available")

            val device: BluetoothDevice = adapter.getRemoteDevice(address)
            val s = device.createRfcommSocketToServiceRecord(SPP_UUID)
            adapter.cancelDiscovery()
            s.connect()
            socket = s
            outputStream = s.outputStream
            connectedAddress = address
            Log.d(TAG, "Connected to $address")
            PrintResult.Success
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed", e)
            PrintResult.Error("Could not connect: ${e.message}")
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            outputStream?.close()
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        outputStream = null
        connectedAddress = null
    }

    val isConnected: Boolean get() = socket?.isConnected == true

    // ── Low-level write ───────────────────────────────────────────────────────

    private suspend fun write(vararg chunks: ByteArray) = withContext(Dispatchers.IO) {
        val os = outputStream ?: throw IllegalStateException("Printer not connected")
        chunks.forEach { os.write(it) }
        os.flush()
    }

    // ── High-level receipt printing ────────────────────────────────────────────

    suspend fun printReceipt(
        storeName: String,
        sale: Sale,
        items: List<CartItem>,
        currency: String,
        footerMessage: String,
        cashierName: String,
        mpesaPaybill: String = ""
    ): PrintResult {
        if (!isConnected) return PrintResult.Error("Printer not connected")
        return try {
            val df = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val dateStr = df.format(Date(sale.createdAt))

            write(EscPos.INIT)

            // ── Header ──
            write(EscPos.ALIGN_CENTER, EscPos.BOLD_ON, EscPos.FONT_LARGE)
            write(EscPos.text(storeName.uppercase()), EscPos.NEWLINE)
            write(EscPos.FONT_NORMAL, EscPos.BOLD_OFF)
            write(EscPos.text("SALES RECEIPT"), EscPos.NEWLINE)
            write(EscPos.text(dateStr), EscPos.NEWLINE)
            write(EscPos.text("Receipt: ${sale.receiptNumber}"), EscPos.NEWLINE)
            write(EscPos.text("Cashier: $cashierName"), EscPos.NEWLINE)
            write(EscPos.divider())

            // ── Items ──
            write(EscPos.ALIGN_LEFT)
            items.forEach { item ->
                val nameCol  = item.product.name.take(18).padEnd(18)
                val qtyCol   = "x${item.quantity}".padStart(4)
                val totalCol = formatMoney(item.lineTotal, currency).padStart(10)
                write(EscPos.text("$nameCol$qtyCol$totalCol"), EscPos.NEWLINE)
                val unitPrice = "  @ ${formatMoney(item.product.price, currency)}/unit"
                write(EscPos.text(unitPrice), EscPos.NEWLINE)
            }
            write(EscPos.divider())

            // ── Totals ──
            write(EscPos.ALIGN_RIGHT)
            if (sale.discountAmount > 0) {
                write(EscPos.text(padLR("Subtotal:", formatMoney(sale.subtotal, currency))), EscPos.NEWLINE)
                write(EscPos.text(padLR("Discount:", "-${formatMoney(sale.discountAmount, currency)}")), EscPos.NEWLINE)
            }
            if (sale.taxAmount > 0) {
                write(EscPos.text(padLR("Tax (16% VAT):", formatMoney(sale.taxAmount, currency))), EscPos.NEWLINE)
            }
            write(EscPos.BOLD_ON, EscPos.FONT_MEDIUM)
            write(EscPos.text(padLR("TOTAL:", formatMoney(sale.totalAmount, currency))), EscPos.NEWLINE)
            write(EscPos.FONT_NORMAL, EscPos.BOLD_OFF)
            write(EscPos.divider())

            // ── Payment ──
            write(EscPos.ALIGN_LEFT)
            write(EscPos.text(padLR("Payment:", sale.paymentMethod.name)), EscPos.NEWLINE)
            if (sale.paymentMethod == PaymentMethod.MPESA && sale.mpesaRef != null) {
                write(EscPos.text(padLR("M-Pesa Ref:", sale.mpesaRef)), EscPos.NEWLINE)
            }
            if (sale.paymentMethod == PaymentMethod.CASH) {
                write(EscPos.text(padLR("Cash Paid:", formatMoney(sale.amountPaid, currency))), EscPos.NEWLINE)
                write(EscPos.text(padLR("Change:", formatMoney(sale.changeGiven, currency))), EscPos.NEWLINE)
            }
            if (mpesaPaybill.isNotBlank() && sale.paymentMethod != PaymentMethod.MPESA) {
                write(EscPos.NEWLINE)
                write(EscPos.ALIGN_CENTER)
                write(EscPos.text("M-Pesa Paybill: $mpesaPaybill"), EscPos.NEWLINE)
            }

            // ── Footer ──
            write(EscPos.divider('='))
            write(EscPos.ALIGN_CENTER)
            write(EscPos.text(footerMessage), EscPos.NEWLINE)
            write(EscPos.NEWLINE, EscPos.NEWLINE, EscPos.NEWLINE)

            // ── Cut ──
            write(EscPos.CUT)

            PrintResult.Success
        } catch (e: Exception) {
            Log.e(TAG, "Print failed", e)
            PrintResult.Error("Print failed: ${e.message}")
        }
    }

    /** Print a simple low-stock alert list */
    suspend fun printLowStockReport(
        storeName: String,
        items: List<Pair<String, Int>>,  // name to stock
        currency: String
    ): PrintResult {
        if (!isConnected) return PrintResult.Error("Printer not connected")
        return try {
            write(EscPos.INIT, EscPos.ALIGN_CENTER, EscPos.BOLD_ON)
            write(EscPos.text(storeName), EscPos.NEWLINE)
            write(EscPos.text("LOW STOCK REPORT"), EscPos.NEWLINE)
            write(EscPos.text(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())), EscPos.NEWLINE)
            write(EscPos.BOLD_OFF, EscPos.divider(), EscPos.ALIGN_LEFT)
            items.forEach { (name, stock) ->
                write(EscPos.text(name.take(22).padEnd(22) + "Qty: $stock".padStart(10)), EscPos.NEWLINE)
            }
            write(EscPos.divider(), EscPos.NEWLINE, EscPos.NEWLINE, EscPos.CUT)
            PrintResult.Success
        } catch (e: Exception) {
            PrintResult.Error("Print failed: ${e.message}")
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun formatMoney(amount: Double, currency: String) =
        "$currency ${String.format("%.2f", amount)}"

    private fun padLR(left: String, right: String): String {
        val total = EscPos.LINE_CHARS
        val padding = total - left.length - right.length
        return if (padding > 0) left + " ".repeat(padding) + right
        else left.take(total - right.length - 1) + " " + right
    }

    fun getPairedPrinters(context: Context): List<BluetoothDevice> {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return bm?.adapter?.bondedDevices?.filter { device ->
            device.name?.contains("printer", ignoreCase = true) == true ||
            device.bluetoothClass?.majorDeviceClass == android.bluetooth.BluetoothClass.Device.Major.IMAGING
        } ?: emptyList()
    }
}
