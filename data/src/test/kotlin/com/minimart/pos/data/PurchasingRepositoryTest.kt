package com.minimart.pos.data

import com.minimart.pos.data.entity.Product
import com.minimart.pos.data.entity.PurchaseOrder
import com.minimart.pos.data.entity.PurchaseOrderItem
import com.minimart.pos.data.entity.PurchaseOrderStatus
import com.minimart.pos.data.entity.Supplier
import com.minimart.pos.data.repository.BatchRepository
import com.minimart.pos.data.repository.PurchasingRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PurchasingRepositoryTest : DbTestBase() {
    private val repo get() = PurchasingRepository(
        db.purchasingDao(), db.productDao(), BatchRepository(db.batchDao(), db.productDao()), db
    )

    private suspend fun orderedPo(productId: Long, qty: Double, cost: Double = 10.0): Long {
        val supplier = repo.saveSupplier(Supplier(name = "Supplier"))
        val po = repo.saveDraft(
            PurchaseOrder(supplierId = supplier),
            listOf(PurchaseOrderItem(poId = 0, productId = productId, productName = "Item", quantity = qty, unitCost = cost))
        )
        repo.markOrdered(po)
        return po
    }

    private suspend fun newProduct(stock: Int = 0) =
        db.productDao().insertProduct(Product(barcode = "b${System.nanoTime()}", name = "Item", price = 50.0, stock = stock))

    @Test fun invalidDraftLinesAreDropped() = runBlocking {
        val p = newProduct()
        val supplier = repo.saveSupplier(Supplier(name = "S"))
        val po = repo.saveDraft(PurchaseOrder(supplierId = supplier), listOf(
            PurchaseOrderItem(poId = 0, productId = p, productName = "ok", quantity = 2.0, unitCost = 5.0),
            PurchaseOrderItem(poId = 0, productId = p, productName = "zero", quantity = 0.0),
            PurchaseOrderItem(poId = 0, productId = p, productName = "nan", quantity = Double.NaN),
            PurchaseOrderItem(poId = 0, productId = p, productName = "cost", quantity = 1.0, unitCost = Double.POSITIVE_INFINITY)
        ))
        assertEquals(listOf("ok"), repo.getItems(po).map { it.productName })
    }

    @Test fun nonFiniteArrivalAddsNothing() = runBlocking {
        val p = newProduct(stock = 5)
        val po = orderedPo(p, 10.0)
        val line = repo.getItems(po).single()
        repo.receive(po, mapOf(line.id to Double.NaN), updateCostPrices = false)
        assertEquals(5, db.productDao().getProductById(p)?.stock)
        assertEquals(0.0, repo.getItems(po).single().receivedQty, 0.0)
    }

    @Test fun arrivalNeverExceedsWhatIsOutstanding() = runBlocking {
        val p = newProduct()
        val po = orderedPo(p, 4.0)
        val line = repo.getItems(po).single()
        repo.receive(po, mapOf(line.id to 100.0), updateCostPrices = false)
        assertEquals(4, db.productDao().getProductById(p)?.stock)
        assertEquals(PurchaseOrderStatus.RECEIVED, repo.getOrder(po)?.status)
    }

    @Test fun countedProductsAreReceivedInWholeUnits() = runBlocking {
        val p = newProduct()
        val po = orderedPo(p, 5.0)
        val line = repo.getItems(po).single()
        repo.receive(po, mapOf(line.id to 2.4), updateCostPrices = false)
        assertEquals(2, db.productDao().getProductById(p)?.stock)
        assertEquals(2.0, repo.getItems(po).single().receivedQty, 0.0)
        assertEquals(PurchaseOrderStatus.PARTIAL, repo.getOrder(po)?.status)
        // a fraction that rounds to nothing must not count as delivered
        repo.receive(po, mapOf(line.id to 0.4), updateCostPrices = false)
        assertEquals(2.0, repo.getItems(po).single().receivedQty, 0.0)
    }

    @Test fun orderCannotBeReceivedTwice() = runBlocking {
        val p = newProduct()
        val po = orderedPo(p, 1.0)
        val line = repo.getItems(po).single()
        repo.receive(po, mapOf(line.id to 1.0), updateCostPrices = false)
        try {
            repo.receive(po, mapOf(line.id to 1.0), updateCostPrices = false)
            fail("a finished order must not accept another delivery")
        } catch (_: IllegalStateException) { }
        assertEquals(1, db.productDao().getProductById(p)?.stock)
    }

    @Test fun paymentIsCappedAtOrderTotal() = runBlocking {
        val p = newProduct()
        val po = orderedPo(p, 3.0, cost = 10.0)
        repo.recordPayment(po, 100.0)
        assertEquals(30.0, repo.getOrder(po)?.amountPaid ?: -1.0, 0.001)
        try { repo.recordPayment(po, Double.NaN); fail() } catch (_: IllegalArgumentException) { }
        assertTrue((repo.getOrder(po)?.amountPaid ?: 0.0) <= 30.0)
    }
}
