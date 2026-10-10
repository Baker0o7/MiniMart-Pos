package com.minimart.pos.sync

import com.minimart.pos.data.entity.PaymentMethod
import com.minimart.pos.data.entity.Sale
import com.minimart.pos.data.entity.SaleItem
import com.minimart.pos.data.entity.SaleStatus
import org.json.JSONArray
import org.json.JSONObject

/** A sale as it travels between devices; ids are local to each device so lines carry barcodes. */
data class RemoteSale(
    val sale: Sale,
    val items: List<SaleItem>,
    val customerName: String?,
    val customerPhone: String?,
    val cashierUsername: String?,
    val creditAmount: Double,
    val purchaseAmount: Double
)

data class RemoteReversal(val receiptNumber: String, val createdAt: Long, val status: SaleStatus, val reason: String)

object SyncPayloads {
    const val VERSION = 1

    fun saleCreated(
        sale: Sale, items: List<SaleItem>, customerName: String?, customerPhone: String?,
        cashierUsername: String?, creditAmount: Double, purchaseAmount: Double
    ): JSONObject = JSONObject().apply {
        put("v", VERSION)
        put("receiptNumber", sale.receiptNumber)
        put("subtotal", sale.subtotal); put("taxAmount", sale.taxAmount)
        put("discountAmount", sale.discountAmount); put("totalAmount", sale.totalAmount)
        put("amountPaid", sale.amountPaid); put("changeGiven", sale.changeGiven)
        put("cashPortion", sale.cashPortion)
        put("paymentMethod", sale.paymentMethod.name)
        sale.mpesaRef?.let { put("mpesaRef", it) }
        put("notes", sale.notes)
        put("createdAt", sale.createdAt)
        cashierUsername?.let { put("cashierUsername", it) }
        customerName?.let { put("customerName", it) }
        customerPhone?.let { put("customerPhone", it) }
        put("creditAmount", creditAmount); put("purchaseAmount", purchaseAmount)
        put("items", JSONArray().also { arr ->
            items.forEach { i ->
                arr.put(JSONObject().apply {
                    put("barcode", i.productBarcode); put("name", i.productName)
                    put("unitPrice", i.unitPrice); put("quantity", i.quantity)
                    put("discountAmount", i.discountAmount); put("taxAmount", i.taxAmount)
                    put("lineTotal", i.lineTotal); put("weightKg", i.weightKg)
                })
            }
        })
    }

    fun parseSale(o: JSONObject): RemoteSale {
        require(o.optInt("v", 1) == VERSION) { "Unsupported sync version" }
        val sale = Sale(
            receiptNumber = o.getString("receiptNumber"),
            subtotal = o.getDouble("subtotal"), taxAmount = o.getDouble("taxAmount"),
            discountAmount = o.getDouble("discountAmount"), totalAmount = o.getDouble("totalAmount"),
            amountPaid = o.getDouble("amountPaid"), changeGiven = o.getDouble("changeGiven"),
            cashPortion = o.optDouble("cashPortion", 0.0),
            paymentMethod = PaymentMethod.valueOf(o.getString("paymentMethod")),
            mpesaRef = o.optString("mpesaRef", "").takeIf { it.isNotEmpty() },
            status = SaleStatus.COMPLETED,
            cashierId = 0L,
            notes = o.optString("notes", ""),
            createdAt = o.getLong("createdAt")
        )
        val arr = o.getJSONArray("items")
        val items = (0 until arr.length()).map { idx ->
            val i = arr.getJSONObject(idx)
            SaleItem(
                saleId = 0L, productId = 0L,
                productBarcode = i.getString("barcode"), productName = i.getString("name"),
                unitPrice = i.getDouble("unitPrice"), quantity = i.getInt("quantity"),
                discountAmount = i.optDouble("discountAmount", 0.0), taxAmount = i.optDouble("taxAmount", 0.0),
                lineTotal = i.getDouble("lineTotal"), weightKg = i.optDouble("weightKg", 0.0)
            )
        }
        return RemoteSale(
            sale, items,
            customerName = o.optString("customerName", "").takeIf { it.isNotEmpty() },
            customerPhone = o.optString("customerPhone", "").takeIf { it.isNotEmpty() },
            cashierUsername = o.optString("cashierUsername", "").takeIf { it.isNotEmpty() },
            creditAmount = o.optDouble("creditAmount", 0.0), purchaseAmount = o.optDouble("purchaseAmount", 0.0)
        )
    }

    fun saleReversed(sale: Sale, status: SaleStatus, reason: String): JSONObject = JSONObject().apply {
        put("v", VERSION); put("kind", "reverse")
        put("receiptNumber", sale.receiptNumber); put("createdAt", sale.createdAt)
        put("status", status.name); put("reason", reason)
    }

    fun parseReversal(o: JSONObject): RemoteReversal {
        require(o.optString("kind") == "reverse") { "Unknown sale update" }
        return RemoteReversal(
            o.getString("receiptNumber"), o.getLong("createdAt"),
            SaleStatus.valueOf(o.getString("status")), o.optString("reason", "")
        )
    }
}
