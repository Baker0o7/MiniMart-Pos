package com.minimart.pos.data

import com.minimart.pos.data.entity.PaymentMethod
import com.minimart.pos.data.entity.Sale
import com.minimart.pos.data.entity.SaleItem
import com.minimart.pos.data.repository.PaymentValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentValidatorTest {

    private fun item(price: Double = 100.0, qty: Int = 2, discount: Double = 0.0, total: Double = price * qty - discount) =
        SaleItem(saleId = 0, productId = 1, productBarcode = "123", productName = "Milk",
            unitPrice = price, quantity = qty, discountAmount = discount, lineTotal = total)

    private fun sale(
        total: Double = 200.0, paid: Double = 200.0, change: Double = 0.0, cash: Double = 200.0,
        method: PaymentMethod = PaymentMethod.CASH, discount: Double = 0.0, ref: String? = null
    ) = Sale(receiptNumber = "R1", subtotal = total + discount, taxAmount = 0.0, discountAmount = discount,
        totalAmount = total, amountPaid = paid, changeGiven = change, cashPortion = cash,
        paymentMethod = method, mpesaRef = ref, cashierId = 1)

    @Test fun `a consistent cash sale passes`() {
        assertNull(PaymentValidator.validate(sale(), listOf(item()), 0.0, false))
    }

    @Test fun `cash with change passes and wrong change fails`() {
        val ok = sale(paid = 500.0, change = 300.0)
        assertNull(PaymentValidator.validate(ok, listOf(item()), 0.0, false))
        val bad = sale(paid = 500.0, change = 100.0)
        assertNotNull(PaymentValidator.validate(bad, listOf(item()), 0.0, false))
    }

    @Test fun `cash that does not cover the total is rejected`() {
        val s = sale(paid = 150.0, change = 0.0, cash = 150.0)
        assertEquals("Cash received does not cover the total", PaymentValidator.validate(s, listOf(item()), 0.0, false))
    }

    @Test fun `total must match the lines`() {
        val s = sale(total = 50.0, paid = 50.0, cash = 50.0)
        assertTrue(PaymentValidator.validate(s, listOf(item()), 0.0, false)!!.startsWith("Sale total does not match"))
    }

    @Test fun `line total must match price times quantity`() {
        val tampered = item(total = 10.0)
        assertNotNull(PaymentValidator.validate(sale(total = 10.0, paid = 10.0, cash = 10.0), listOf(tampered), 0.0, false))
    }

    @Test fun `line and cart-wide discounts are accepted when they add up`() {
        // 2 x 100 with a 20 line discount and a 30 cart discount -> 150
        val s = sale(total = 150.0, paid = 150.0, cash = 150.0, discount = 50.0)
        assertNull(PaymentValidator.validate(s, listOf(item(discount = 20.0)), 0.0, false))
    }

    @Test fun `discount cannot exceed the sale`() {
        val s = sale(total = 1.0, paid = 1.0, cash = 1.0, discount = 500.0)
        assertNotNull(PaymentValidator.validate(s, listOf(item()), 0.0, false))
    }

    @Test fun `credit sale needs a customer and a full credit amount`() {
        val s = sale(method = PaymentMethod.CREDIT, cash = 0.0)
        assertEquals("A credit sale needs a customer", PaymentValidator.validate(s, listOf(item()), 200.0, false))
        assertNotNull(PaymentValidator.validate(s, listOf(item()), 50.0, true))
        assertNull(PaymentValidator.validate(s, listOf(item()), 200.0, true))
    }

    @Test fun `split payment needs credit plus cash to cover the total`() {
        val ok = sale(method = PaymentMethod.MIXED, paid = 200.0, cash = 120.0)
        assertNull(PaymentValidator.validate(ok, listOf(item()), 80.0, true))
        val short = sale(method = PaymentMethod.MIXED, paid = 150.0, cash = 70.0)
        assertNotNull(PaymentValidator.validate(short, listOf(item()), 80.0, true))
    }

    @Test fun `credit cannot be used on a cash sale`() {
        assertNotNull(PaymentValidator.validate(sale(), listOf(item()), 50.0, true))
    }

    @Test fun `mpesa sale must be fully paid and record no cash`() {
        assertNull(PaymentValidator.validate(sale(method = PaymentMethod.MPESA, cash = 0.0, ref = "SGL1A2B3C4"), listOf(item()), 0.0, false))
        assertNotNull(PaymentValidator.validate(sale(method = PaymentMethod.MPESA, cash = 200.0), listOf(item()), 0.0, false))
        assertNotNull(PaymentValidator.validate(sale(method = PaymentMethod.MPESA, paid = 100.0, cash = 0.0), listOf(item()), 0.0, false))
    }

    @Test fun `malformed mpesa references are rejected`() {
        assertFalse(PaymentValidator.isWellFormedMpesaRef("abc"))
        assertFalse(PaymentValidator.isWellFormedMpesaRef("SGL1A2B3C4 "))
        assertTrue(PaymentValidator.isWellFormedMpesaRef("SGL1A2B3C4"))
        val s = sale(method = PaymentMethod.MPESA, cash = 0.0, ref = "!!")
        assertNotNull(PaymentValidator.validate(s, listOf(item()), 0.0, false))
    }

    @Test fun `empty sales and non-finite or negative amounts are rejected`() {
        assertNotNull(PaymentValidator.validate(sale(), emptyList(), 0.0, false))
        assertNotNull(PaymentValidator.validate(sale(total = Double.NaN), listOf(item()), 0.0, false))
        assertNotNull(PaymentValidator.validate(sale(paid = -5.0), listOf(item()), 0.0, false))
        assertNotNull(PaymentValidator.validate(sale(total = 0.0, paid = 0.0, cash = 0.0), listOf(item(total = 0.0)), 0.0, false))
    }

    @Test fun `weighed lines keep the scale price`() {
        val weighed = SaleItem(saleId = 0, productId = 1, productBarcode = "9", productName = "Rice",
            unitPrice = 180.0, quantity = 1, lineTotal = 90.0, weightKg = 0.5)
        val s = sale(total = 90.0, paid = 90.0, cash = 90.0)
        assertNull(PaymentValidator.validate(s, listOf(weighed), 0.0, false))
    }

    @Test fun `overpaying in cash needs the right change`() {
        assertNull(PaymentValidator.validate(sale(paid = 500.0, change = 300.0), listOf(item()), 0.0, false))
        assertNotNull(PaymentValidator.validate(sale(paid = 500.0, change = 250.0), listOf(item()), 0.0, false))
    }

    @Test fun `a zero quantity or invalid line is rejected`() {
        assertNotNull(PaymentValidator.validate(sale(), listOf(item(qty = 0, total = 0.0), item()), 0.0, false))
        assertNotNull(PaymentValidator.validate(sale(), listOf(item(price = Double.NaN)), 0.0, false))
        assertNotNull(PaymentValidator.validate(sale(), listOf(item(price = 100.0, qty = 1, discount = 150.0, total = -50.0), item()), 0.0, false))
    }

    @Test fun `one cent of rounding is tolerated but fifty cents is not`() {
        val lines = listOf(item(price = 33.33, qty = 3, total = 99.99))
        assertNull(PaymentValidator.validate(sale(total = 99.99, paid = 99.99, cash = 99.99), lines, 0.0, false))
        assertNotNull(PaymentValidator.validate(sale(total = 99.49, paid = 99.49, cash = 99.49), lines, 0.0, false))
    }

    @Test fun `card sales are fully paid with no cash recorded`() {
        assertNull(PaymentValidator.validate(sale(method = PaymentMethod.CARD, cash = 0.0), listOf(item()), 0.0, false))
        assertNotNull(PaymentValidator.validate(sale(method = PaymentMethod.CARD, cash = 50.0), listOf(item()), 0.0, false))
        assertNotNull(PaymentValidator.validate(sale(method = PaymentMethod.CARD, paid = 100.0, cash = 0.0), listOf(item()), 0.0, false))
    }

    @Test fun `a valid split payment passes and bad splits fail`() {
        val mixed = sale(method = PaymentMethod.MIXED, cash = 100.0)
        assertNull(PaymentValidator.validate(mixed, listOf(item()), 100.0, true))
        assertNotNull(PaymentValidator.validate(mixed, listOf(item()), 100.0, false))
        assertNotNull(PaymentValidator.validate(mixed, listOf(item()), 250.0, true))
        assertNotNull(PaymentValidator.validate(mixed, listOf(item()), 0.0, true))
        assertNotNull(PaymentValidator.validate(sale(method = PaymentMethod.MIXED, cash = 20.0), listOf(item()), 100.0, true))
    }

    @Test fun `credit amount must be a real number`() {
        val credit = sale(method = PaymentMethod.CREDIT, paid = 0.0, cash = 0.0)
        assertNull(PaymentValidator.validate(credit, listOf(item()), 200.0, true))
        assertNotNull(PaymentValidator.validate(credit, listOf(item()), Double.POSITIVE_INFINITY, true))
        assertNotNull(PaymentValidator.validate(credit, listOf(item()), -1.0, true))
        assertNotNull(PaymentValidator.validate(credit, listOf(item()), 150.0, true))
    }

    @Test fun `mpesa reference rules`() {
        val m = sale(method = PaymentMethod.MPESA, cash = 0.0)
        assertNull(PaymentValidator.validate(m.copy(mpesaRef = "QGH7XYZ123"), listOf(item()), 0.0, false))
        assertNull(PaymentValidator.validate(m.copy(mpesaRef = "   "), listOf(item()), 0.0, false))
        assertNotNull(PaymentValidator.validate(m.copy(mpesaRef = "abc123"), listOf(item()), 0.0, false))
        assertNotNull(PaymentValidator.validate(m.copy(mpesaRef = "AB12"), listOf(item()), 0.0, false))
        assertNotNull(PaymentValidator.validate(m.copy(mpesaRef = "AB12 CD34; DROP"), listOf(item()), 0.0, false))
        assertTrue(PaymentValidator.isWellFormedMpesaRef("ABCDEF"))
        assertFalse(PaymentValidator.isWellFormedMpesaRef("ABCDE"))
        assertFalse(PaymentValidator.isWellFormedMpesaRef("A".repeat(21)))
    }

    @Test fun `the error names the expected total`() {
        val msg = PaymentValidator.validate(sale(total = 150.0, paid = 150.0, cash = 150.0), listOf(item()), 0.0, false)
        assertTrue(msg!!.contains("200.00"))
    }
}
