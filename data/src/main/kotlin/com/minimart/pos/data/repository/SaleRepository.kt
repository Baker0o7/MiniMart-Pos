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
