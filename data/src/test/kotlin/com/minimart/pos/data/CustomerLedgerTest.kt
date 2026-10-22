package com.minimart.pos.data

import com.minimart.pos.data.entity.Customer
import com.minimart.pos.data.entity.CreditTxType
import com.minimart.pos.data.repository.CustomerRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** The credit ledger must always agree with the customer's balance, whatever order things happen in. */
class CustomerLedgerTest : DbTestBase() {
    private val repo get() = CustomerRepository(db.customerDao(), db)
    private suspend fun customer() = repo.saveCustomer(Customer(name = "Wanjiru", phone = "0711000111"))
    private suspend fun balance(id: Long) = repo.getById(id)!!.creditBalance
    private suspend fun lastLedger(id: Long) = repo.getTransactions(id).first().maxByOrNull { it.id }!!

    @Test fun depositsAndPurchasesOnAccountKeepBalanceAndLedgerTogether() = runBlocking {
        val id = customer()
        assertTrue(repo.addCredit(id, 1000.0, "deposit"))
        assertTrue(repo.useCredit(id, 350.0, saleId = 7))
        assertEquals(650.0, balance(id), 1e-9)
        assertEquals(650.0, lastLedger(id).balanceAfter, 1e-9)
        assertEquals(CreditTxType.CREDIT_USED, lastLedger(id).type)
    }

    @Test fun buyingOnAccountMayTakeTheBalanceNegative() = runBlocking {
        val id = customer()
        assertTrue(repo.useCredit(id, 200.0, saleId = 1))
        assertEquals(-200.0, balance(id), 1e-9)
    }

    @Test fun nonsenseAmountsAreRefusedAndChangeNothing() = runBlocking {
        val id = customer()
        listOf(0.0, -5.0, Double.NaN, Double.POSITIVE_INFINITY).forEach {
            assertFalse(repo.addCredit(id, it)); assertFalse(repo.useCredit(id, it, 1))
        }
        assertEquals(0.0, balance(id), 1e-9)
        assertTrue(repo.getTransactions(id).first().isEmpty())
    }

    @Test fun unknownCustomerIsRefused() = runBlocking {
        assertFalse(repo.addCredit(999, 10.0)); assertFalse(repo.useCredit(999, 10.0, 1))
    }

    @Test fun voidingASaleGivesTheCreditBackOnce() = runBlocking {
        val id = customer()
        repo.addCredit(id, 500.0)
        repo.useCredit(id, 300.0, saleId = 42)
        repo.reverseCreditForSale(42, "voided")
        assertEquals(500.0, balance(id), 1e-9)
        assertEquals(CreditTxType.REFUND, lastLedger(id).type)
        assertEquals(500.0, lastLedger(id).balanceAfter, 1e-9)
    }

    @Test fun reversingASaleThatUsedNoCreditDoesNothing() = runBlocking {
        val id = customer()
        repo.addCredit(id, 100.0)
        repo.reverseCreditForSale(55, "voided")
        assertEquals(100.0, balance(id), 1e-9)
        assertEquals(1, repo.getTransactions(id).first().size)
    }
}
