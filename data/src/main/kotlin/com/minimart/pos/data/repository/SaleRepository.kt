package com.minimart.pos.data.repository

import androidx.room.withTransaction
import com.minimart.pos.data.dao.SaleDao
import com.minimart.pos.data.dao.TopSellerResult
import com.minimart.pos.data.db.AppDatabase
import com.minimart.pos.data.entity.*
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.minimart.pos.data.dao.SaleStats
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SaleRepository @Inject constructor(
    private val saleDao: SaleDao,
    private val productRepository: ProductRepository,
    private val customerRepository: CustomerRepository,
    private val db: AppDatabase
) {
    fun getAllSalesWithItems(): Flow<List<SaleWithItems>> = saleDao.getAllSalesWithItems()
    fun getSalesToday(startMs: Long): Flow<List<Sale>> = saleDao.getSalesToday(startMs)
    fun getTotalRevenueToday(startMs: Long): Flow<Double?> = saleDao.getTotalRevenueToday(startMs)
    fun getTotalRevenueBetween(start: Long, end: Long): Flow<Double?> = saleDao.getTotalRevenueBetween(start, end)
    fun getSaleCountToday(startMs: Long): Flow<Int> = saleDao.getSaleCountToday(startMs)
    fun getTopSellers(startMs: Long, endMs: Long = Long.MAX_VALUE): Flow<List<TopSellerResult>> =
        saleDao.getTopSellingProducts(startMs, endMs)
    fun getProductSalesStats(startMs: Long, endMs: Long = Long.MAX_VALUE) = saleDao.getProductSalesStats(startMs, endMs)
    fun getLastSoldTimes() = saleDao.getLastSoldTimes()
    fun getSalesByDateRange(start: Long, end: Long): Flow<List<Sale>> = saleDao.getSalesByDateRange(start, end)
    fun getCompletedSalesByDateRange(start: Long, end: Long): Flow<List<Sale>> = saleDao.getCompletedSalesByDateRange(start, end)

    /** Sales History, loaded a page at a time. */
    fun pagedCompletedSales(query: String): Flow<PagingData<SaleWithItems>> =
        Pager(PagingConfig(pageSize = 30, enablePlaceholders = false)) {
            saleDao.pagedCompletedSales(query.trim())
        }.flow

    fun completedSalesStats(query: String): Flow<SaleStats> = saleDao.completedSalesStats(query.trim())

    suspend fun getSaleWithItems(saleId: Long): SaleWithItems? = saleDao.getSaleWithItems(saleId)

    /** Complete a sale: persist the sale, items, and decrement stock — all atomically.
     *
     * Bug fix: this used to be two separate, uncoordinated steps — insertSaleWithItems()
     * (its own transaction), then a forEach loop calling decrementStock() per item (each
     * its own tiny transaction). If the app crashed or was killed by the OS partway
     * through that loop, the sale + items were already committed but only SOME products
     * had their stock decremented — silently corrupting inventory counts and risking
     * overselling on the next sale (the stock check would pass against a stale, too-high
     * number). Wrapping the whole thing in db.withTransaction{} makes it all-or-nothing.
     */
    suspend fun completeSale(
        sale: Sale,
        items: List<SaleItem>,
        customerId: Long? = null,
        creditAmount: Double = 0.0,
        purchaseAmount: Double = 0.0
    ): Long = db.withTransaction {
        checkPayment(sale, items, customerId != null, creditAmount)
        val saleId = saleDao.insertSaleWithItems(sale, items)
        items.forEach { item ->
            if (item.weightKg > 0.0) {
                // Weighed item: deduct the actual kilograms sold (floored at 0 — weighed stock is
                // never allowed to block a sale, since the scale ticket already exists).
                productRepository.decrementStockKg(item.productId, item.weightKg)
            } else {
                val updated = productRepository.decrementStock(item.productId, item.quantity)
                // Throwing rolls the whole sale back.
                if (updated == 0) {
                    throw IllegalStateException("Not enough stock for ${item.productName}")
                }
            }
        }
        // Customer credit / purchase stats are part of the same transaction so a failure here
        // can no longer leave a committed CREDIT sale with no recorded debt.
        if (customerId != null) {
            if (creditAmount > 0.0) {
                if (!customerRepository.useCredit(customerId, creditAmount, saleId))
                    throw IllegalStateException("Customer account not found")
            } else if (purchaseAmount > 0.0) {
                customerRepository.recordPurchase(customerId, purchaseAmount, saleId)
            }
        }
        saleId
    }

    /**
     * The checks no screen can be trusted to do alone: lines and totals agree, the payment covers the
     * bill, credit has a customer, and an M-Pesa reference is well formed, not already used by a live
     * sale, and (when the SMS tracker has seen that payment) was for at least this amount.
     */
    private suspend fun checkPayment(sale: Sale, items: List<SaleItem>, hasCustomer: Boolean, creditAmount: Double) {
        PaymentValidator.validate(sale, items, creditAmount, hasCustomer)?.let { throw IllegalArgumentException(it) }
        val ref = sale.mpesaRef?.trim()?.takeIf { it.isNotEmpty() } ?: return
        if (saleDao.countCompletedWithMpesaRef(ref) > 0)
            throw IllegalArgumentException("M-Pesa reference $ref was already used for another sale")
        db.mpesaPaymentDao().findByCode(ref)?.let { paid ->
            val due = if (sale.paymentMethod == PaymentMethod.MPESA) sale.totalAmount else sale.amountPaid - sale.cashPortion
            if (paid.amount + 0.011 < due)
                throw IllegalArgumentException("M-Pesa payment $ref was only ${"%.2f".format(paid.amount)}, less than the ${"%.2f".format(due)} due")
        }
    }

    /**
     * Records a sale that was rung up on another till and synced in. It is validated like any other
     * sale, but stock is taken off without ever blocking (the goods already left the shop), products and
     * customers are matched by barcode / phone, and a barcode this device has never seen gets a minimal
     * product so the sale history stays intact. Returns the new sale id.
     */
    suspend fun applyRemoteSale(
        sale: Sale,
        items: List<SaleItem>,
        customerName: String?,
        customerPhone: String?,
        creditAmount: Double,
        purchaseAmount: Double
    ): Long = db.withTransaction {
        val hasCustomer = !customerName.isNullOrBlank() || !customerPhone.isNullOrBlank()
        checkPayment(sale, items, hasCustomer, creditAmount)

        val mapped = items.map { item ->
            val product = productRepository.getAnyByBarcode(item.productBarcode)
                ?: Product(
                    barcode = item.productBarcode, name = item.productName,
                    price = item.unitPrice,
                    stock = 0
                ).let { stub -> stub.copy(id = productRepository.insert(stub)) }
            item.copy(productId = product.id)
        }
        val saleId = saleDao.insertSaleWithItems(sale, mapped)
        mapped.forEach { item ->
            if (item.weightKg > 0.0) productRepository.decrementStockKg(item.productId, item.weightKg)
            else productRepository.decrementStockClamped(item.productId, item.quantity)
        }

        if (hasCustomer) {
            val phone = customerPhone?.trim().orEmpty()
            val name = customerName?.trim().orEmpty()
            var customer = (if (phone.isNotEmpty()) customerRepository.getByPhone(phone)
                            else customerRepository.getByNameWithoutPhone(name))
            if (customer == null) {
                val id = customerRepository.saveCustomer(Customer(name = name.ifEmpty { phone }, phone = phone))
                customer = customerRepository.getById(id)
            }
            if (customer != null) {
                if (creditAmount > 0.0) customerRepository.useCredit(customer.id, creditAmount, saleId)
                else if (purchaseAmount > 0.0) customerRepository.recordPurchase(customer.id, purchaseAmount, saleId)
            }
        }
        saleId
    }

    /** Looks a synced sale up on this device (receipt number + creation time identify it across tills). */
    suspend fun findSale(receiptNumber: String, createdAt: Long): Sale? =
        saleDao.findByReceiptAndTime(receiptNumber, createdAt)

    fun searchSales(query: String) = saleDao.searchSales(query)
    fun getCompletedSales() = saleDao.getCompletedSales()

    /** Refund a COMPLETED sale: marks it refunded, restores stock and gives back any customer
     * credit it used — all in one transaction. Does nothing if the sale was already refunded
     * or voided (previously a second refund/void restored the stock again). */
    suspend fun refundSale(saleId: Long, reason: String): Unit = reverseSale(saleId, reason, refund = true)

    /** Void a COMPLETED sale: same guarantees as [refundSale]. */
    suspend fun voidSale(saleId: Long, reason: String): Unit = reverseSale(saleId, reason, refund = false)

    private suspend fun reverseSale(saleId: Long, reason: String, refund: Boolean): Unit = db.withTransaction<Unit> {
        val saleWithItems = saleDao.getSaleWithItems(saleId) ?: return@withTransaction
        if (saleWithItems.sale.status != SaleStatus.COMPLETED) return@withTransaction
        val changed = if (refund) saleDao.refundSale(saleId, reason) else saleDao.voidSale(saleId, reason)
        if (changed == 0) return@withTransaction
        saleWithItems.items.forEach { item ->
            if (item.weightKg > 0.0) productRepository.incrementStockKg(item.productId, item.weightKg)
            else productRepository.incrementStock(item.productId, item.quantity)
        }
        customerRepository.reverseCreditForSale(saleId, reason)
    }
}
