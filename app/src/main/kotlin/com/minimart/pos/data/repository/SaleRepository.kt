package com.minimart.pos.data.repository

import com.minimart.pos.data.dao.SaleDao
import com.minimart.pos.data.dao.TopSellerResult
import com.minimart.pos.data.dao.UserDao
import com.minimart.pos.data.entity.*
import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SaleRepository @Inject constructor(
    private val saleDao: SaleDao,
    private val productRepository: ProductRepository
) {
    fun getAllSalesWithItems(): Flow<List<SaleWithItems>> = saleDao.getAllSalesWithItems()
    fun getSalesToday(startMs: Long): Flow<List<Sale>> = saleDao.getSalesToday(startMs)
    fun getTotalRevenueToday(startMs: Long): Flow<Double?> = saleDao.getTotalRevenueToday(startMs)
    fun getSaleCountToday(startMs: Long): Flow<Int> = saleDao.getSaleCountToday(startMs)
    fun getTopSellers(startMs: Long): Flow<List<TopSellerResult>> = saleDao.getTopSellingProducts(startMs)
    fun getSalesByDateRange(start: Long, end: Long): Flow<List<Sale>> = saleDao.getSalesByDateRange(start, end)

    suspend fun getSaleWithItems(saleId: Long): SaleWithItems? = saleDao.getSaleWithItems(saleId)
    suspend fun voidSale(saleId: Long) = saleDao.voidSale(saleId)

    /** Complete a sale: persist the sale, items, and decrement stock. */
    suspend fun completeSale(sale: Sale, items: List<SaleItem>): Long {
        val saleId = saleDao.insertSaleWithItems(sale, items)
        // Decrement stock for each sold product
        items.forEach { item ->
            productRepository.decrementStock(item.productId, item.quantity)
        }
        return saleId
    }
}

@Singleton
class UserRepository @Inject constructor(private val userDao: UserDao) {

    fun getAllUsers(): Flow<List<User>> = userDao.getAllUsers()
    suspend fun getUserById(id: Long): User? = userDao.getUserById(id)
    suspend fun getUserCount(): Int = userDao.getUserCount()
    suspend fun insertUser(user: User): Long = userDao.insertUser(user)
    suspend fun updateUser(user: User) = userDao.updateUser(user)

    /** Verify PIN login. Returns the User or null if credentials invalid. */
    suspend fun login(username: String, pin: String): User? {
        val user = userDao.getUserByUsername(username) ?: return null
        val hash = sha256(pin)
        return if (hash == user.pinHash) user else null
    }

    fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
