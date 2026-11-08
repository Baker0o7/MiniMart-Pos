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

/** Phase 1 of whole-cent storage: sales carry exact integer copies of their total and cash share. */
class SaleCentsTest : DbTestBase() {
    private fun sale(total: Double, cash: Double) = com.minimart.pos.data.entity.Sale(
        receiptNumber = "R-$total", subtotal = total, taxAmount = 0.0, totalAmount = total, amountPaid = total, changeGiven = 0.0,
        cashPortion = cash, paymentMethod = com.minimart.pos.data.entity.PaymentMethod.CASH, cashierId = 1L)

    @Test fun savingASaleStoresExactCents() = runBlocking {
        val id = db.saleDao().insertSaleWithItems(sale(12.34, 12.34), emptyList())
        val s = db.saleDao().getSaleWithItems(id)!!.sale
        assertEquals(1234L, s.totalCents)
        assertEquals(1234L, s.cashPortionCents)
    }

    @Test fun cumulativeFloatDriftStillGivesTheRightCents() = runBlocking {
        val id = db.saleDao().insertSaleWithItems(sale(0.1 + 0.2, 0.0), emptyList())
        assertEquals(30L, db.saleDao().getSaleWithItems(id)!!.sale.totalCents)
    }

    @Test fun migrationFillsCentsForExistingSales() = runBlocking {
        val id = db.saleDao().insertSaleWithItems(sale(99.99, 50.0), emptyList())
        val sdb = db.openHelper.writableDatabase
        sdb.execSQL("UPDATE sales SET totalCents = 0, cashPortionCents = 0")
        sdb.execSQL(AppMigrations.BACKFILL_SALE_CENTS)
        val s = db.saleDao().getSaleWithItems(id)!!.sale
        assertEquals(9999L, s.totalCents)
        assertEquals(5000L, s.cashPortionCents)
    }
}

/** The real 18→19 step, run against a table shaped like a v18 `sales` table that holds old data. */
class Migration18To19Test : DbTestBase() {
    @Test fun addsAndFillsTheCentsColumnsWithoutTouchingTheOldOnes() {
        val factory = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory()
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val helper = factory.create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(ctx).name(null)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(18) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE sales (id INTEGER PRIMARY KEY, totalAmount REAL NOT NULL, cashPortion REAL NOT NULL)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, o: Int, n: Int) {}
                }).build())
        val sdb = helper.writableDatabase
        sdb.execSQL("INSERT INTO sales VALUES (1, 1234.56, 200.0), (2, 0.29, 0.0), (3, 19.99, 19.99)")
        AppMigrations.MIGRATION_18_19.migrate(sdb)
        sdb.query("SELECT totalAmount, totalCents, cashPortionCents FROM sales ORDER BY id").use { c ->
            val rows = generateSequence { if (c.moveToNext()) Triple(c.getDouble(0), c.getLong(1), c.getLong(2)) else null }.toList()
            assertEquals(listOf(Triple(1234.56, 123456L, 20000L), Triple(0.29, 29L, 0L), Triple(19.99, 1999L, 1999L)), rows)
        }
        helper.close()
    }
}
