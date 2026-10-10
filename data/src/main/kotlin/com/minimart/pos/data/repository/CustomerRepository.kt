package com.minimart.pos.data.repository

import androidx.room.withTransaction
import com.minimart.pos.data.dao.CustomerDao
import com.minimart.pos.data.db.AppDatabase
import com.minimart.pos.data.entity.CreditTransaction
import com.minimart.pos.data.entity.CreditTxType
import com.minimart.pos.data.entity.Customer
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomerRepository @Inject constructor(
    private val dao: CustomerDao,
    private val db: AppDatabase
) {

    fun getAllCustomers(): Flow<List<Customer>>  = dao.getAllCustomers()
    fun searchCustomers(q: String): Flow<List<Customer>> = dao.searchCustomers(q)
    fun getTransactions(customerId: Long): Flow<List<CreditTransaction>> = dao.getTransactions(customerId)
    fun getCustomerCount(): Flow<Int> = dao.getCustomerCount()
    fun getCustomersWithCredit(): Flow<List<Customer>> = dao.getCustomersWithCredit()
    fun getTotalCreditOutstanding(): Flow<Double?> = dao.getTotalCreditOutstanding()

    suspend fun getById(id: Long): Customer? = dao.getCustomerById(id)
    suspend fun getByPhone(phone: String): Customer? = dao.getCustomerByPhone(phone)
    suspend fun getByNameWithoutPhone(name: String): Customer? = dao.getCustomerByNameNoPhone(name)
    suspend fun getAllList(): List<Customer> = dao.getAllCustomersList()

    suspend fun saveCustomer(customer: Customer): Long = db.withTransaction {
        if (customer.id == 0L) dao.insertCustomer(customer)
        else {
            // The edit form holds a snapshot: writing it back whole used to roll the credit balance and
            // purchase stats back to what they were when the form opened (wiping any sale since).
            val current = dao.getCustomerById(customer.id)
            dao.updateCustomer(
                if (current == null) customer
                else customer.copy(creditBalance = current.creditBalance,
                    totalPurchases = current.totalPurchases, visitCount = current.visitCount)
            )
            customer.id
        }
    }

    suspend fun deleteCustomer(customer: Customer) = dao.deleteCustomer(customer)

    /** Add credit manually (e.g. deposit or refund).
     * Bug fix: balance update and the audit-log insert are now one atomic unit — a crash
     * between them used to risk a balance change with no matching ledger entry. */
    suspend fun addCredit(customerId: Long, amount: Double, notes: String = ""): Boolean = db.withTransaction {
        if (!amount.isFinite() || amount <= 0.0) return@withTransaction false
        val customer = dao.getCustomerById(customerId) ?: return@withTransaction false
        dao.updateBalance(customerId, amount)
        dao.insertCreditTx(CreditTransaction(
            customerId = customerId, amount = amount,
            type = CreditTxType.CREDIT_ADDED, notes = notes,
            balanceAfter = customer.creditBalance + amount
        ))
        true
    }

    /** Use credit for a sale — allows negative balance (buy on account).
     * Bug fix: same atomicity gap as addCredit. */
    suspend fun useCredit(customerId: Long, amount: Double, saleId: Long): Boolean = db.withTransaction {
        val customer = dao.getCustomerById(customerId) ?: return@withTransaction false
        dao.updateBalance(customerId, -amount, purchase = amount, visit = 1)
        dao.insertCreditTx(CreditTransaction(
            customerId = customerId, amount = -amount,
            type = CreditTxType.CREDIT_USED, saleId = saleId,
            balanceAfter = customer.creditBalance - amount,
            notes = "Sale #$saleId"
        ))
        true
    }

    /** Give back any customer credit that was used by [saleId] (called when a sale is voided
     * or refunded) and write a REFUND ledger entry so the balance and history stay in sync. */
    suspend fun reverseCreditForSale(saleId: Long, reason: String): Unit = db.withTransaction<Unit> {
        dao.getCreditUsedForSale(saleId).forEach { tx ->
            val used = -tx.amount   // CREDIT_USED rows are stored negative
            if (used <= 0.0) return@forEach
            val customer = dao.getCustomerById(tx.customerId) ?: return@forEach
            dao.updateBalance(tx.customerId, used, purchase = -used, visit = 0)
            dao.insertCreditTx(CreditTransaction(
                customerId = tx.customerId, amount = used,
                type = CreditTxType.REFUND, saleId = saleId,
                balanceAfter = customer.creditBalance + used,
                notes = "Sale #$saleId $reason"
            ))
        }
    }

    /** Record a purchase (non-credit payment) — updates stats */
    suspend fun recordPurchase(customerId: Long, amount: Double, saleId: Long) {
        dao.updateBalance(customerId, 0.0, purchase = amount, visit = 1)
    }
}
