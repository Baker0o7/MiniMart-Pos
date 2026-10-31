package com.minimart.pos.data

import androidx.sqlite.db.SupportSQLiteDatabase
import com.minimart.pos.data.db.AppMigrations
import com.minimart.pos.data.entity.Customer
import com.minimart.pos.data.entity.Expense
import com.minimart.pos.data.entity.ExpenseCategory
import com.minimart.pos.data.repository.CustomerRepository
import com.minimart.pos.data.repository.ExpenseRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** Money is exact to the cent when stored and when added up in the database. */
class MoneyCentsTest : DbTestBase() {
    @Test fun migrationRoundsEveryStoredAmountToTheCent() {
        runBlocking { db.customerDao().insertCustomer(Customer(name = "A", phone = "0700000001")) }
        val sdb: SupportSQLiteDatabase = db.openHelper.writableDatabase
        sdb.execSQL("UPDATE customers SET creditBalance = 12.3400000001, totalPurchases = 0.1 + 0.2")
        AppMigrations.MIGRATION_16_17.migrate(sdb)
        sdb.query("SELECT creditBalance, totalPurchases FROM customers").use { c ->
            c.moveToFirst()
            assertEquals(12.34, c.getDouble(0), 0.0)
            assertEquals(0.3, c.getDouble(1), 0.0)
        }
    }

    @Test fun everyListedMoneyColumnReallyExists() {
        val sdb = db.openHelper.writableDatabase
        AppMigrations.MONEY_COLUMNS.forEach { (table, cols) ->
            val existing = sdb.query("PRAGMA table_info(`$table`)").use { c ->
                generateSequence { if (c.moveToNext()) c.getString(1) else null }.toSet()
            }
            cols.forEach { assertEquals("$table.$it missing", true, it in existing) }
        }
    }

    @Test fun thousandsOfTenCentSalesAddUpExactly() = runBlocking {
        val repo = ExpenseRepository(db.expenseDao())
        repeat(1000) { repo.insert(Expense(title = "x", amount = 0.1, category = ExpenseCategory.OTHER, createdAt = 1_000L)) }
        assertEquals(100.0, db.expenseDao().getTotalExpensesToday(0L).first() ?: -1.0, 0.0)
    }

    @Test fun creditBalanceDoesNotDrift() = runBlocking {
        val id = db.customerDao().insertCustomer(Customer(name = "B", phone = "0700000002"))
        val repo = CustomerRepository(db.customerDao(), db)
        repeat(30) { repo.addCredit(id, 0.1) }
        assertEquals(3.0, db.customerDao().getCustomerById(id)?.creditBalance ?: -1.0, 0.0)
    }
}
