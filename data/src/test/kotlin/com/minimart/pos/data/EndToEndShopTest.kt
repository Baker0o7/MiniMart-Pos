package com.minimart.pos.data

import com.minimart.pos.data.entity.Customer
import com.minimart.pos.data.entity.MpesaPayment
import com.minimart.pos.data.entity.PaymentMethod
import com.minimart.pos.data.entity.Product
import com.minimart.pos.data.entity.PurchaseOrder
import com.minimart.pos.data.entity.PurchaseOrderItem
import com.minimart.pos.data.entity.Sale
import com.minimart.pos.data.entity.SaleItem
import com.minimart.pos.data.entity.SaleStatus
import com.minimart.pos.data.entity.Supplier
import com.minimart.pos.data.repository.BatchRepository
import com.minimart.pos.data.repository.CustomerRepository
import com.minimart.pos.data.repository.IntegrityChecker
import com.minimart.pos.data.repository.ProductRepository
import com.minimart.pos.data.repository.PurchasingRepository
import com.minimart.pos.data.repository.SaleRepository
import com.minimart.pos.util.MpesaReconciler
import com.minimart.pos.util.MpesaSaleRecord
import com.minimart.pos.util.PaymentRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.abs

/**
 * Whole-shop scenarios on a real (in-memory) database with the real repositories wired together:
 * stock comes in, sales go out, costs follow FIFO, voids put everything back, and the integrity
 * check agrees at the end. These catch what unit tests of one class cannot: the pieces disagreeing.
 */
class EndToEndShopTest : DbTestBase() {
    private val products get() = ProductRepository(db.productDao())
    private val batches get() = BatchRepository(db.batchDao(), db.productDao())
    private val customers get() = CustomerRepository(db.customerDao(), db)
    private val sales get() = SaleRepository(db.saleDao(), products, customers, batches, db)
    private val purchasing get() = PurchasingRepository(db.purchasingDao(), db.productDao(), batches, db)

    private suspend fun newProduct(price: Double = 50.0, stock: Int = 0) =
        db.productDao().insertProduct(Product(barcode = "bc${System.nanoTime()}", name = "Maize flour", price = price, stock = stock))

    /** Orders [qty] at [cost] and books the delivery, the way a manager would. */
    private suspend fun stockIn(productId: Long, qty: Int, cost: Double) {
        val supplier = purchasing.saveSupplier(Supplier(name = "Supplier"))
        val po = purchasing.saveDraft(PurchaseOrder(supplierId = supplier),
            listOf(PurchaseOrderItem(poId = 0, productId = productId, productName = "Maize flour", quantity = qty.toDouble(), unitCost = cost)))
        purchasing.markOrdered(po)
        val line = purchasing.getItems(po).single()
        purchasing.receive(po, mapOf(line.id to qty.toDouble()), updateCostPrices = true)
    }

    private var receiptNo = 0
    private fun sale(total: Double, method: PaymentMethod = PaymentMethod.CASH, ref: String? = null, at: Long = System.currentTimeMillis()) = Sale(
        receiptNumber = "RCP-${++receiptNo}", subtotal = total, taxAmount = 0.0, totalAmount = total, amountPaid = total, changeGiven = 0.0,
        cashPortion = if (method == PaymentMethod.CASH) total else 0.0, paymentMethod = method, mpesaRef = ref, cashierId = 1, createdAt = at
    )
    private fun line(productId: Long, qty: Int, price: Double = 50.0) = SaleItem(
        saleId = 0, productId = productId, productBarcode = "x", productName = "Maize flour",
        unitPrice = price, quantity = qty, lineTotal = price * qty
    )

    @Test fun aDayOfTradingKeepsStockCostAndRecordsConsistent() = runBlocking {
        val p = newProduct()
        stockIn(p, 10, cost = 20.0)   // first batch
        stockIn(p, 10, cost = 30.0)   // second batch
        assertEquals(20, db.productDao().getProductById(p)?.stock)

        val first = sales.completeSale(sale(200.0), listOf(line(p, 4)))
        val second = sales.completeSale(sale(400.0), listOf(line(p, 8)))
        assertEquals(8, db.productDao().getProductById(p)?.stock)

        // FIFO: the 4 first units cost 20 each; the next 8 are 6 at 20 and 2 at 30.
        assertEquals(80.0, db.saleDao().getSaleWithItems(first)!!.items.single().costTotal, 0.001)
        assertEquals(180.0, db.saleDao().getSaleWithItems(second)!!.items.single().costTotal, 0.001)
        assertEquals(600.0 - 260.0, sales.profitBetween(0L, Long.MAX_VALUE), 0.001)
        assertEquals(2, sales.saleCount())

        // Voiding the second sale puts the goods and the cost layers back.
        sales.voidSale(second, "customer changed their mind")
        assertEquals(16, db.productDao().getProductById(p)?.stock)
        assertEquals(SaleStatus.VOIDED, db.saleDao().getSaleWithItems(second)!!.sale.status)
        assertEquals(16.0, batches.recentBatches(p).first().sumOf { it.remaining }, 0.001)

        val report = IntegrityChecker(db).check()
        assertTrue("integrity problems: ${report.issues}", report.ok)
    }

    @Test fun sellingMoreThanIsInStockRollsTheWholeSaleBack() = runBlocking {
        val p = newProduct(stock = 2)
        try {
            sales.completeSale(sale(150.0), listOf(line(p, 3)))
            fail("selling 3 of 2 must be refused")
        } catch (_: IllegalStateException) { }
        assertEquals(2, db.productDao().getProductById(p)?.stock)
        assertEquals(0, sales.saleCount())
    }

    @Test fun creditSaleRecordsTheDebtAndVoidingClearsIt() = runBlocking {
        val p = newProduct(stock = 5)
        val c = db.customerDao().insertCustomer(Customer(name = "Amina", phone = "0700000001"))
        val id = sales.completeSale(sale(100.0, PaymentMethod.CREDIT).copy(amountPaid = 0.0), listOf(line(p, 2)), customerId = c, creditAmount = 100.0)
        assertEquals(-100.0, db.customerDao().getCustomerById(c)!!.creditBalance, 0.001)
        assertTrue(IntegrityChecker(db).check().ok)

        sales.voidSale(id, "returned")
        assertEquals(0.0, db.customerDao().getCustomerById(c)!!.creditBalance, 0.001)
        assertEquals(5, db.productDao().getProductById(p)?.stock)
        assertTrue(IntegrityChecker(db).check().ok)
    }

    @Test fun mpesaRefCannotBeUsedTwiceAndReconciliationFindsTheGaps() = runBlocking {
        val p = newProduct(stock = 10)
        val now = System.currentTimeMillis()
        db.mpesaPaymentDao().insert(MpesaPayment(code = "QWE12ABC34", amount = 100.0, senderName = "Juma", senderPhone = "0711", receivedAt = now, rawMessage = ""))
        db.mpesaPaymentDao().insert(MpesaPayment(code = "ZXC98LMN76", amount = 250.0, senderName = "Neema", senderPhone = "0722", receivedAt = now, rawMessage = ""))

        sales.completeSale(sale(100.0, PaymentMethod.MPESA, "QWE12ABC34"), listOf(line(p, 2)))
        try {
            sales.completeSale(sale(100.0, PaymentMethod.MPESA, "QWE12ABC34"), listOf(line(p, 2)))
            fail("the same M-Pesa code must not pay for two sales")
        } catch (_: IllegalArgumentException) { }
        // A sale whose code never arrived as a message here.
        sales.completeSale(sale(50.0, PaymentMethod.MPESA, "NOMSG00001"), listOf(line(p, 1)))

        val received = db.mpesaPaymentDao().between(0L, Long.MAX_VALUE).map { PaymentRecord(it.code, it.amount, it.receivedAt) }
        val sold = sales.mpesaSalesBetween(0L, Long.MAX_VALUE).map {
            MpesaSaleRecord(it.id, it.receiptNumber, it.mpesaRef.orEmpty(), it.totalAmount, it.paymentMethod == PaymentMethod.MPESA, it.createdAt)
        }
        val r = MpesaReconciler.reconcile(received, sold)
        assertEquals(1, r.matched.size)
        assertEquals(listOf("ZXC98LMN76"), r.unclaimedPayments.map { it.code })
        assertEquals(listOf("NOMSG00001"), r.unverifiedSales.map { it.code })
        assertTrue(r.mismatches.isEmpty())
    }

    @Test fun deliveryThenSaleThenIntegrityAgreeOnStockValue() = runBlocking {
        val p = newProduct()
        stockIn(p, 6, cost = 15.0)
        sales.completeSale(sale(100.0), listOf(line(p, 2)))
        val remaining = batches.recentBatches(p).first().sumOf { it.remaining }
        assertEquals(db.productDao().getProductById(p)!!.stock.toDouble(), remaining, 0.001)
        assertTrue(abs(remaining - 4.0) < 0.001)
    }
}
