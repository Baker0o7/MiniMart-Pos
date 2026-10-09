package com.minimart.pos.data.repository

import androidx.room.withTransaction
import com.minimart.pos.data.dao.ProductDao
import com.minimart.pos.data.dao.PurchasingDao
import com.minimart.pos.data.db.AppDatabase
import com.minimart.pos.data.entity.PurchaseOrder
import com.minimart.pos.data.entity.PurchaseOrderItem
import com.minimart.pos.data.entity.PurchaseOrderStatus
import com.minimart.pos.data.entity.Supplier
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class PurchasingRepository @Inject constructor(
    private val dao: PurchasingDao,
    private val productDao: ProductDao,
    private val db: AppDatabase
) {
    fun getSuppliers(): Flow<List<Supplier>> = dao.getSuppliers()
    fun getAllSuppliers(): Flow<List<Supplier>> = dao.getAllSuppliersIncludingInactive()
    fun getOrders(): Flow<List<PurchaseOrder>> = dao.getOrders()
    fun getAllItems(): Flow<List<PurchaseOrderItem>> = dao.getAllItems()
    suspend fun getSupplier(id: Long): Supplier? = dao.getSupplier(id)
    suspend fun getOrder(id: Long): PurchaseOrder? = dao.getOrder(id)
    suspend fun getItems(poId: Long): List<PurchaseOrderItem> = dao.getItems(poId)

    suspend fun saveSupplier(supplier: Supplier): Long =
        if (supplier.id == 0L) dao.insertSupplier(supplier) else { dao.updateSupplier(supplier); supplier.id }

    suspend fun deactivateSupplier(id: Long) {
        dao.getSupplier(id)?.let { dao.updateSupplier(it.copy(isActive = false)) }
    }

    /** Creates or updates a DRAFT and replaces its lines. Orders already sent are not editable. */
    suspend fun saveDraft(order: PurchaseOrder, items: List<PurchaseOrderItem>): Long = db.withTransaction {
        val id = if (order.id == 0L) {
            dao.insertOrder(order.copy(status = PurchaseOrderStatus.DRAFT))
        } else {
            val current = dao.getOrder(order.id) ?: throw IllegalStateException("Order not found")
            if (current.status != PurchaseOrderStatus.DRAFT) throw IllegalStateException("Only a draft can be edited")
            dao.updateOrder(current.copy(supplierId = order.supplierId, notes = order.notes))
            order.id
        }
        dao.deleteItems(id)
        dao.insertItems(items.map { it.copy(id = 0, poId = id, receivedQty = 0.0) })
        id
    }

    suspend fun markOrdered(poId: Long) = db.withTransaction {
        val po = dao.getOrder(poId) ?: throw IllegalStateException("Order not found")
        if (po.status != PurchaseOrderStatus.DRAFT) return@withTransaction
        if (dao.getItems(poId).isEmpty()) throw IllegalStateException("Add at least one item first")
        dao.updateOrder(po.copy(status = PurchaseOrderStatus.ORDERED, orderedAt = System.currentTimeMillis()))
    }

    suspend fun deleteDraft(poId: Long) = db.withTransaction {
        val po = dao.getOrder(poId) ?: return@withTransaction
        if (po.status != PurchaseOrderStatus.DRAFT) throw IllegalStateException("Only a draft can be deleted")
        dao.deleteItems(poId)
        dao.deleteOrder(poId)
    }

    /** ORDERED orders that received nothing can be cancelled; PARTIAL ones are closed instead. */
    suspend fun cancel(poId: Long) = db.withTransaction {
        val po = dao.getOrder(poId) ?: return@withTransaction
        if (po.status == PurchaseOrderStatus.ORDERED || po.status == PurchaseOrderStatus.DRAFT)
            dao.updateOrder(po.copy(status = PurchaseOrderStatus.CANCELLED))
    }

    /** Accepts what has arrived so far and marks the order finished (supplier will not send the rest). */
    suspend fun closeShort(poId: Long) = db.withTransaction {
        val po = dao.getOrder(poId) ?: return@withTransaction
        if (po.status == PurchaseOrderStatus.PARTIAL)
            dao.updateOrder(po.copy(status = PurchaseOrderStatus.RECEIVED, receivedAt = System.currentTimeMillis()))
    }

    /**
     * Books a delivery: stock goes up, the product's cost price follows the order's unit cost, and the
     * order becomes PARTIAL or RECEIVED. [received] maps a line id to the quantity that arrived now.
     * All of it is one transaction, so a crash can't add stock without recording the delivery.
     */
    suspend fun receive(poId: Long, received: Map<Long, Double>, updateCostPrices: Boolean): Unit = db.withTransaction {
        val po = dao.getOrder(poId) ?: throw IllegalStateException("Order not found")
        if (po.status != PurchaseOrderStatus.ORDERED && po.status != PurchaseOrderStatus.PARTIAL)
            throw IllegalStateException("This order is not waiting for delivery")
        val now = System.currentTimeMillis()
        val items = dao.getItems(poId)
        items.forEach { item ->
            val arrived = (received[item.id] ?: 0.0).coerceIn(0.0, item.remaining)
            if (arrived <= 0.0) return@forEach
            val product = productDao.getProductById(item.productId)
            if (product != null) {
                if (product.isWeighed) productDao.incrementStockKg(product.id, arrived)
                else {
                    val units = arrived.roundToInt()
                    if (units > 0) productDao.incrementStock(product.id, units)
                }
                if (updateCostPrices && item.unitCost > 0.0) {
                    productDao.getProductById(product.id)?.let {
                        productDao.updateProduct(it.copy(costPrice = item.unitCost, updatedAt = now))
                    }
                }
            }
            dao.updateItem(item.copy(receivedQty = item.receivedQty + arrived))
        }
        val after = dao.getItems(poId)
        val complete = after.all { it.receivedQty + 1e-9 >= it.quantity }
        dao.updateOrder(po.copy(
            status = if (complete) PurchaseOrderStatus.RECEIVED else PurchaseOrderStatus.PARTIAL,
            receivedAt = if (complete) now else 0L
        ))
    }

    /** Records money paid to the supplier. Never more than the order is worth. */
    suspend fun recordPayment(poId: Long, amount: Double) = db.withTransaction {
        if (!amount.isFinite() || amount <= 0.0) throw IllegalArgumentException("Enter an amount")
        val po = dao.getOrder(poId) ?: throw IllegalStateException("Order not found")
        if (po.status == PurchaseOrderStatus.DRAFT || po.status == PurchaseOrderStatus.CANCELLED)
            throw IllegalStateException("Send the order before recording a payment")
        val total = dao.getItems(poId).sumOf { it.lineTotal }
        dao.updateOrder(po.copy(amountPaid = (po.amountPaid + amount).coerceAtMost(total)))
    }
}
