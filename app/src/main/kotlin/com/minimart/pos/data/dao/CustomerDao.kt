package com.minimart.pos.data.dao

import androidx.room.*
import com.minimart.pos.data.entity.CreditTransaction
import com.minimart.pos.data.entity.Customer
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {

    @Query("SELECT * FROM customers ORDER BY name ASC")
    fun getAllCustomers(): Flow<List<Customer>>

    @Query("SELECT * FROM customers WHERE name LIKE '%' || :q || '%' OR phone LIKE '%' || :q || '%' ORDER BY name ASC")
    fun searchCustomers(q: String): Flow<List<Customer>>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getCustomerById(id: Long): Customer?

    @Query("SELECT * FROM customers WHERE phone = :phone LIMIT 1")
    suspend fun getCustomerByPhone(phone: String): Customer?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: Customer): Long

    @Update
    suspend fun updateCustomer(customer: Customer)

    @Delete
    suspend fun deleteCustomer(customer: Customer)

    @Query("UPDATE customers SET creditBalance = creditBalance + :amount, totalPurchases = totalPurchases + :purchase, visitCount = visitCount + :visit WHERE id = :id")
    suspend fun updateBalance(id: Long, amount: Double, purchase: Double = 0.0, visit: Int = 0)

    // Credit transactions
    @Insert
    suspend fun insertCreditTx(tx: CreditTransaction): Long

    @Query("SELECT * FROM credit_transactions WHERE customerId = :customerId ORDER BY createdAt DESC")
    fun getTransactions(customerId: Long): Flow<List<CreditTransaction>>

    @Query("SELECT COUNT(*) FROM customers")
    fun getCustomerCount(): Flow<Int>

    @Query("SELECT * FROM customers WHERE creditBalance > 0 ORDER BY creditBalance DESC")
    fun getCustomersWithCredit(): Flow<List<Customer>>

    @Query("SELECT SUM(creditBalance) FROM customers")
    fun getTotalCreditOutstanding(): Flow<Double?>
}
