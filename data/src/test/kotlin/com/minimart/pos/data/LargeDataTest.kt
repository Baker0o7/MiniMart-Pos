package com.minimart.pos.data

import com.minimart.pos.data.entity.PaymentMethod
import com.minimart.pos.data.entity.Product
import com.minimart.pos.data.entity.Sale
import com.minimart.pos.data.entity.SaleItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * How the busy screens behave on a shop that has traded for years: thousands of products and tens of
 * thousands of sales. The time limits are deliberately generous, so a failure means something got
 * orders of magnitude slower (for example an index no longer being used), not that a CI machine had a
 * slow minute. Every timing is written to build/perf-report.txt, which CI shows on the run summary so a
 * slow creep is visible long before it breaks a limit.
 */
class LargeDataTest : DbTestBase() {
    private val day = 86_400_000L
    private val report = StringBuilder("| Query | Rows seen | Time |\n|---|---|---|\n")

    private fun <T> measure(label: String, limitMs: Long, block: () -> T): Pair<T, Long> {
        val start = System.nanoTime()
        val result = block()
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("$label took $ms ms (limit $limitMs ms)", ms <= limitMs)
        return result to ms
    }

    /** Times a query that returns a list; the row count goes in the report. */
    private fun <T> timedList(label: String, limitMs: Long, block: () -> List<T>): List<T> {
        val (rows, ms) = measure(label, limitMs, block)
        report.append("| $label | ${rows.size} | $ms ms |\n")
        return rows
    }

    /** Times a query that returns one value. */
    private fun <T> timedOne(label: String, limitMs: Long, block: () -> T): T {
        val (value, ms) = measure(label, limitMs, block)
        report.append("| $label | 1 | $ms ms |\n")
        return value
    }

    private fun <T> Flow<List<T>>.list() = runBlocking { first() }

    private fun seed(products: Int, sales: Int, now: Long) = runBlocking {
        db.productDao().insertProducts((1..products).map {
            Product(barcode = "6000%07d".format(Locale.US, it), name = "Item $it ${listOf("flour", "sugar", "milk", "rice", "oil")[it % 5]}",
                price = 20.0 + it % 200, stock = it % 40, category = "Cat${it % 12}")
        })
        val ids = db.productDao().getAllIncludingInactive().map { it.id }
        for (i in 1..sales) {
            val at = now - (i.toLong() * 365 * day / sales)          // spread evenly over the last year
            val total = 100.0 + i % 50
            val id = db.saleDao().insertSale(Sale(
                receiptNumber = "RCP-$i", subtotal = total, taxAmount = 0.0, totalAmount = total, amountPaid = total,
                changeGiven = 0.0, cashPortion = total, paymentMethod = PaymentMethod.CASH, cashierId = 1, createdAt = at))
            db.saleDao().insertSaleItems(listOf(
                SaleItem(saleId = id, productId = ids[i % ids.size], productBarcode = "x", productName = "Item", unitPrice = 50.0, quantity = 1, lineTotal = 50.0),
                SaleItem(saleId = id, productId = ids[(i * 7) % ids.size], productBarcode = "x", productName = "Item", unitPrice = total - 50.0, quantity = 1, lineTotal = total - 50.0)))
        }
    }

    @Test fun busyScreensStayFastWithAYearOfTrading() {
        val now = System.currentTimeMillis()
        seed(products = 3_000, sales = 12_000, now = now)

        assertEquals(12_000, timedOne("Count all sales", 1_000) { runBlocking { db.saleDao().saleCount() } })
        timedOne("One product by barcode (the scan)", 250) { runBlocking { db.productDao().getProductByBarcode("60000001500") } }
        val today = timedList("Sales for one day", 1_000) { db.saleDao().getSalesByDateRange(now - day, now).list() }
        assertTrue("one day should hold about 33 sales, found ${today.size}", today.size in 20..60)
        timedList("Completed sales, last 30 days", 2_000) { db.saleDao().getCompletedSalesByDateRange(now - 30 * day, now).list() }
        timedList("Top 10 products, whole year", 4_000) { db.saleDao().getTopSellingProducts(now - 365 * day, now, 10).list() }
        timedList("Per-product stats, last 90 days", 4_000) { db.saleDao().getProductSalesStats(now - 90 * day, now).list() }
        timedList("Product search 'milk'", 1_500) { db.productDao().searchProducts("milk").list() }
        timedList("Low stock list", 1_000) { db.productDao().getLowStockProducts().list() }
        timedOne("Profit, last 30 days", 2_000) { runBlocking { db.saleDao().profitBetween(now - 30 * day, now) } }

        File("build").mkdirs()
        File("build/perf-report.txt").writeText(report.toString())
    }

    @Test fun busyQueriesUseIndexesInsteadOfReadingEveryRow() {
        val sqlite = db.openHelper.readableDatabase
        fun plan(sql: String): String = sqlite.query("EXPLAIN QUERY PLAN $sql").use { c ->
            buildString { while (c.moveToNext()) appendLine(c.getString(c.getColumnCount() - 1)) }
        }
        val fullScan = Regex("""SCAN (TABLE )?(sales|sale_items|products)(?! USING)""")
        mapOf(
            "sales in a date range" to "SELECT * FROM sales WHERE createdAt >= 1 AND createdAt <= 2 AND status = 'COMPLETED' ORDER BY createdAt DESC",
            "items of one sale" to "SELECT * FROM sale_items WHERE saleId = 5",
            "product by barcode" to "SELECT * FROM products WHERE barcode = 'x' AND isActive = 1 LIMIT 1",
        ).forEach { (name, sql) ->
            val p = plan(sql)
            assertTrue("$name should use an index:\n$p", p.contains("USING"))
            assertFalse("$name reads every row:\n$p", fullScan.containsMatchIn(p))
        }
    }
}
