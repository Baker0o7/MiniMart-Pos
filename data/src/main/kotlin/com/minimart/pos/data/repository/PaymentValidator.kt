package com.minimart.pos.data.repository

import com.minimart.pos.data.entity.PaymentMethod
import com.minimart.pos.data.entity.Sale
import com.minimart.pos.data.entity.SaleItem
import com.minimart.pos.data.entity.SaleStatus
import kotlin.math.abs

/**
 * Re-checks a sale's money in the data layer, whatever the UI (or another device syncing in) says.
 * The checkout screen already blocks most of these, but nothing below it did: a sale with a total
 * that doesn't match its lines, cash that doesn't cover the bill, or a credit sale with no customer
 * was saved as-is. Returns an error message, or null when the sale is consistent.
 */
object PaymentValidator {
    /** Amounts are in shillings with cents, so one cent of rounding slack. */
    private const val CENT = 0.011
    /** Header totals add up many lines, so allow a little more. */
    private const val TOTAL_SLACK = 0.02

    private val MPESA_REF = Regex("^[A-Z0-9]{6,20}$")

    fun isWellFormedMpesaRef(ref: String) = MPESA_REF.matches(ref)

    private fun bad(v: Double) = !v.isFinite() || v < 0.0
    private fun near(a: Double, b: Double, slack: Double = CENT) = abs(a - b) <= slack

    fun validate(sale: Sale, items: List<SaleItem>, creditAmount: Double, hasCustomer: Boolean): String? {
        if (items.isEmpty()) return "A sale needs at least one item"
        if (sale.status != SaleStatus.COMPLETED) return "A new sale must be completed"

        listOf(sale.subtotal, sale.taxAmount, sale.discountAmount, sale.totalAmount, sale.amountPaid,
            sale.changeGiven, sale.cashPortion, creditAmount).forEach {
            if (bad(it)) return "Sale amounts must be valid, non-negative numbers"
        }
        if (sale.totalAmount <= 0.0) return "Sale total must be greater than zero"

        var lineSum = 0.0
        var lineDiscounts = 0.0
        for (i in items) {
            if (i.quantity < 1) return "${i.productName}: quantity must be at least 1"
            if (bad(i.unitPrice) || bad(i.discountAmount) || bad(i.lineTotal) || bad(i.weightKg))
                return "${i.productName}: invalid amount"
            // Weighed lines carry the scale ticket's computed price, so only the plain lines are re-derived.
            if (i.weightKg <= 0.0) {
                val gross = i.unitPrice * i.quantity
                if (i.discountAmount > gross + CENT) return "${i.productName}: discount is larger than the line"
                if (!near(i.lineTotal, gross - i.discountAmount))
                    return "${i.productName}: line total does not match price × quantity"
            }
            lineSum += i.lineTotal
            lineDiscounts += i.discountAmount
        }

        // Whatever discount the header has beyond the per-line ones is the cart-wide discount.
        val globalDiscount = sale.discountAmount - lineDiscounts
        if (globalDiscount < -TOTAL_SLACK) return "Discount does not match the items"
        if (globalDiscount > lineSum + TOTAL_SLACK) return "Discount is larger than the sale"
        val expectedTotal = (lineSum - globalDiscount).coerceAtLeast(0.0)
        if (!near(sale.totalAmount, expectedTotal, TOTAL_SLACK))
            return "Sale total does not match the items (expected ${"%.2f".format(expectedTotal)})"

        val total = sale.totalAmount
        val change = (sale.amountPaid - total).coerceAtLeast(0.0)
        if (!near(sale.changeGiven, change)) return "Change given does not match the amount paid"

        when (sale.paymentMethod) {
            PaymentMethod.CASH -> {
                if (creditAmount > 0.0) return "A cash sale cannot use customer credit"
                if (sale.amountPaid + CENT < total) return "Cash received does not cover the total"
                if (!near(sale.cashPortion, total)) return "Cash portion does not match the total"
            }
            PaymentMethod.MPESA, PaymentMethod.CARD -> {
                if (creditAmount > 0.0) return "A ${sale.paymentMethod.name.lowercase()} sale cannot use customer credit"
                if (sale.amountPaid + CENT < total) return "Amount paid does not cover the total"
                if (sale.cashPortion > CENT) return "No cash should be recorded for this payment method"
            }
            PaymentMethod.CREDIT -> {
                if (!hasCustomer) return "A credit sale needs a customer"
                if (!near(creditAmount, total, TOTAL_SLACK)) return "Credit amount must equal the sale total"
                if (sale.cashPortion > CENT) return "No cash should be recorded for a credit sale"
            }
            PaymentMethod.MIXED -> {
                if (!hasCustomer) return "A split payment needs a customer"
                if (creditAmount <= 0.0) return "A split payment needs a credit amount"
                if (creditAmount > total + CENT) return "Credit amount is larger than the total"
                if (sale.cashPortion + creditAmount + CENT < total) return "Credit plus cash does not cover the total"
                if (sale.cashPortion > total + CENT) return "Cash portion is larger than the total"
            }
        }

        val ref = sale.mpesaRef
        if (ref != null && ref.isNotBlank() && !isWellFormedMpesaRef(ref.trim()))
            return "M-Pesa reference looks wrong (letters and digits only, 6–20 characters)"
        return null
    }
}
