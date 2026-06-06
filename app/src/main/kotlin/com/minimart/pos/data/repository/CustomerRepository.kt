package com.minimart.pos.data.repository

import com.minimart.pos.data.dao.CustomerDao
import com.minimart.pos.data.entity.CreditTransaction
import com.minimart.pos.data.entity.CreditTxType
import com.minimart.pos.data.entity.Customer
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomerRepository @Inject constructor(private val dao: CustomerDao) {

    fun getAllCustomers(): Flow<List<Customer>>  = dao.getAllCustomers()
    fun searchCustomers(q: String): Flow<List<Customer>> = dao.searchCustomers(q)
    fun getTransactions(customerId: Long): Flow<List<CreditTransaction>> = dao.getTransactions(customerId)
    fun getCustomerCount(): Flow<Int> = dao.getCustomerCount()
    fun getCustomersWithCredit(): Flow<List<Customer>> = dao.getCustomersWithCredit()
    fun getTotalCreditOutstanding(): Flow<Double?> = dao.getTotalCreditOutstanding()

    suspend fun getById(id: Long): Customer? = dao.getCustomerById(id)
    suspend fun getByPhone(phone: String): Customer? = dao.getCustomerByPhone(phone)

    suspend fun saveCustomer(customer: Customer): Long =
        if (customer.id == 0L) dao.insertCustomer(customer)
        else { dao.updateCustomer(customer); customer.id }

    suspend fun deleteCustomer(customer: Customer) = dao.deleteCustomer(customer)

    /** Add credit manually (e.g. deposit or refund) */
    suspend fun addCredit(customerId: Long, amount: Double, notes: String = ""): Boolean {
        val customer = dao.getCustomerById(customerId) ?: return false
        dao.updateBalance(customerId, amount)
        dao.insertCreditTx(CreditTransaction(
            customerId = customerId, amount = amount,
            type = CreditTxType.CREDIT_ADDED, notes = notes,
            balanceAfter = customer.creditBalance + amount
        ))
        return true
    }

    /** Use credit for a sale — returns false if insufficient balance */
    suspend fun useCredit(customerId: Long, amount: Double, saleId: Long): Boolean {
        val customer = dao.getCustomerById(customerId) ?: return false
        if (customer.creditBalance < amount) return false
        dao.updateBalance(customerId, -amount, purchase = amount, visit = 1)
        dao.insertCreditTx(CreditTransaction(
            customerId = customerId, amount = -amount,
            type = CreditTxType.CREDIT_USED, saleId = saleId,
            balanceAfter = customer.creditBalance - amount,
            notes = "Sale #$saleId"
        ))
        return true
    }

    /** Record a purchase (non-credit payment) — updates stats */
    suspend fun recordPurchase(customerId: Long, amount: Double, saleId: Long) {
        dao.updateBalance(customerId, 0.0, purchase = amount, visit = 1)
    }
}
