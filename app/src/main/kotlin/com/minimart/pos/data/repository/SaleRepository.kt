package com.minimart.pos.data.repository

import androidx.room.withTransaction
import com.minimart.pos.data.dao.SaleDao
import com.minimart.pos.data.dao.TopSellerResult
import com.minimart.pos.data.db.AppDatabase
import com.minimart.pos.data.entity.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SaleRepository @Inject constructor(
    private val saleDao: SaleDao,
    private val productRepository: ProductRepository,
    private val db: AppDatabase
) {
    fun getAllSalesWithItems(): Flow<List<SaleWithItems>> = saleDao.getAllSalesWithItems()
    fun getSalesToday(startMs: Long): Flow<List<Sale>> = saleDao.getSalesToday(startMs)
    fun getTotalRevenueToday(startMs: Long): Flow<Double?> = saleDao.getTotalRevenueToday(startMs)
    fun getSaleCountToday(startMs: Long): Flow<Int> = saleDao.getSaleCountToday(startMs)
    fun getTopSellers(startMs: Long): Flow<List<TopSellerResult>> = saleDao.getTopSellingProducts(startMs)
    fun getSalesByDateRange(start: Long, end: Long): Flow<List<Sale>> = saleDao.getSalesByDateRange(start, end)
    fun getCompletedSalesByDateRange(start: Long, end: Long): Flow<List<Sale>> = saleDao.getCompletedSalesByDateRange(start, end)

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
    suspend fun completeSale(sale: Sale, items: List<SaleItem>): Long = db.withTransaction {
        val saleId = saleDao.insertSaleWithItems(sale, items)
        items.forEach { item ->
            productRepository.decrementStock(item.productId, item.quantity)
        }
        saleId
    }

    fun searchSales(query: String) = saleDao.searchSales(query)
    fun getCompletedSales() = saleDao.getCompletedSales()

    /** Bug fix: same atomicity gap as completeSale — restoring stock and marking the sale
     * refunded were two separate steps; a crash between them could restore stock for a
     * sale that never actually got marked refunded (or vice versa). */
    suspend fun refundSale(saleId: Long, reason: String) = db.withTransaction {
        val saleWithItems = saleDao.getSaleWithItems(saleId) ?: return@withTransaction
        saleWithItems.items.forEach { item ->
            productRepository.incrementStock(item.productId, item.quantity)
        }
        saleDao.refundSale(saleId, reason)
    }

    /** Bug fix: same as refundSale — stock restoration and the void status update are now
     * one atomic unit. */
    suspend fun voidSale(saleId: Long, reason: String) = db.withTransaction {
        val saleWithItems = saleDao.getSaleWithItems(saleId) ?: return@withTransaction
        saleWithItems.items.forEach { item ->
            productRepository.incrementStock(item.productId, item.quantity)
        }
        saleDao.voidSale(saleId, reason)
    }
}
