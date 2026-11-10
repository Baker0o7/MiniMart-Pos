package com.minimart.pos.util

import java.time.ZoneId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AccountingExportTest {
    private val z = ZoneId.of("Africa/Nairobi")
    private fun m(d: Double) = Money.fromDouble(d)
    private val t0 = 1_762_300_800_000L   // 2025-11-05 (Nairobi)

    private fun sale(method: String, total: Double, tax: Double = 0.0, cash: Double = 0.0, cost: Double = 0.0, ref: String = "R1", at: Long = t0) =
        AcctSale(at, ref, method, m(total), m(tax), m(cash), m(cost))

    private fun balanced(l: List<JournalLine>) = l.fold(Money.ZERO) { a, x -> a + x.debit } == l.fold(Money.ZERO) { a, x -> a + x.credit }

    @Test fun everySaleAndExpenseBalances() {
        val sales = listOf(sale("CASH", 116.0, 16.0, cost = 60.0), sale("MPESA", 350.5, 48.34, ref = "R2"),
            sale("CARD", 99.99, 0.0, ref = "R3"), sale("CREDIT", 40.0, 5.52, ref = "R4"), sale("MIXED", 200.0, 27.59, cash = 120.0, ref = "R5"))
        val exp = listOf(AcctExpense(t0, "ELECTRICITY", "KPLC", m(1234.56)), AcctExpense(t0, "SUPPLIER", "Stock", m(0.1), supplier = "Brookside"))
        val lines = AccountingExport.journal(sales, exp, z)
        assertTrue(balanced(lines))
        sales.forEach { s -> assertTrue(balanced(lines.filter { it.ref == s.receipt })) }
    }

    @Test fun vatIsSplitFromRevenue() {
        val l = AccountingExport.journal(listOf(sale("CASH", 116.0, 16.0)), emptyList(), z)
        assertEquals(m(100.0), l.first { it.account == AccountingExport.SALES }.credit)
        assertEquals(m(16.0), l.first { it.account == AccountingExport.VAT }.credit)
        assertEquals(m(116.0), l.first { it.account == AccountingExport.CASH }.debit)
    }

    @Test fun aMixedSaleSplitsCashAndCredit() {
        val l = AccountingExport.journal(listOf(sale("MIXED", 200.0, cash = 120.0)), emptyList(), z)
        assertEquals(m(120.0), l.first { it.account == AccountingExport.CASH }.debit)
        assertEquals(m(80.0), l.first { it.account == AccountingExport.RECEIVABLE }.debit)
    }

    @Test fun costOfGoodsMovesStockToCost() {
        val l = AccountingExport.journal(listOf(sale("CASH", 100.0, cost = 60.0)), emptyList(), z)
        assertEquals(m(60.0), l.first { it.account == AccountingExport.COGS }.debit)
        assertEquals(m(60.0), l.first { it.account == AccountingExport.INVENTORY }.credit)
    }

    @Test fun badInputNeverUnbalancesTheBooks() {
        val l = AccountingExport.journal(listOf(sale("MIXED", 100.0, 500.0, cash = 900.0), sale("CASH", -5.0)), emptyList(), z)
        assertTrue(balanced(l))
    }

    @Test fun centsAreExactWhereDoublesDrift() {
        val sales = List(10) { sale("CASH", 0.1, ref = "R$it") } + sale("CASH", 0.2, ref = "X")
        val p = AccountingExport.profitAndLoss(sales, emptyList())
        assertEquals(m(1.2), p.takings)
        assertEquals("1.20", AccountingExport.amount(p.takings))
    }

    @Test fun profitAndLossAddsUp() {
        val sales = listOf(sale("CASH", 116.0, 16.0, cost = 40.0), sale("MPESA", 232.0, 32.0, cost = 90.0, ref = "R2"))
        val exp = listOf(AcctExpense(t0, "RENT", "Rent", m(50.0)), AcctExpense(t0, "RENT", "Rent 2", m(25.0)), AcctExpense(t0, "WATER", "Water", m(10.0)))
        val p = AccountingExport.profitAndLoss(sales, exp)
        assertEquals(m(348.0), p.takings); assertEquals(m(48.0), p.vatCollected); assertEquals(m(300.0), p.revenueNet)
        assertEquals(m(130.0), p.costOfGoods); assertEquals(m(170.0), p.grossProfit)
        assertEquals(m(85.0), p.totalExpenses); assertEquals(m(85.0), p.netProfit)
        assertEquals(m(75.0), p.expensesByCategory["Rent"]); assertEquals("Rent", p.expensesByCategory.keys.first())
        assertTrue(p.costKnown)
    }

    @Test fun missingCostsAreFlaggedInTheReport() {
        val p = AccountingExport.profitAndLoss(listOf(sale("CASH", 100.0)), emptyList())
        assertFalse(p.costKnown)
        assertTrue(AccountingExport.profitAndLossCsv(p, "Shop", "Nov", "KES").contains("overstated"))
    }

    @Test fun spreadsheetFormulasAreDefused() {
        assertEquals("'=HYPERLINK(\"x\")".let { "\"" + it.replace("\"", "\"\"") + "\"" }, AccountingExport.cell("=HYPERLINK(\"x\")"))
        assertEquals("'+254700000000", AccountingExport.cell("+254700000000"))
        assertEquals("'@SUM(A1)", AccountingExport.cell("@SUM(A1)"))
        assertEquals("'-2+3", AccountingExport.cell("-2+3"))
        assertEquals("\"Tea, sugar\"", AccountingExport.cell("Tea, sugar"))
        assertEquals("Maji", AccountingExport.cell("Maji"))
        assertEquals("a b", AccountingExport.cell("a\nb"))
    }

    @Test fun csvHasAHeaderAndABalancedTotalsRow() {
        val csv = AccountingExport.journalCsv(AccountingExport.journal(listOf(sale("CASH", 116.0, 16.0)), emptyList(), z), "KES")
        val rows = csv.trim().split("\r\n")
        assertEquals("Date,Reference,Account,Debit (KES),Credit (KES),Memo", rows.first())
        assertTrue(rows.last().endsWith("Balanced"))
        assertTrue(rows[1].startsWith("2025-11-05,R1,"))
    }

    @Test fun negativeAmountsKeepTheirSign() {
        assertEquals("-12.05", AccountingExport.amount(Money.fromCents(-1205))); assertEquals("0.07", AccountingExport.amount(Money.fromCents(7)))
    }

    @Test fun linesAreInDateOrder() {
        val l = AccountingExport.journal(listOf(sale("CASH", 10.0, ref = "LATE", at = t0 + 86_400_000L), sale("CASH", 10.0, ref = "EARLY")), emptyList(), z)
        assertEquals("EARLY", l.first().ref)
    }

    @Test fun salesListHasOneRowPerSaleAndTotalsThatAddUp() {
        val csv = AccountingExport.salesCsv(
            listOf(sale("MPESA", 116.0, 16.0, ref = "R2", at = t0 + 1000), sale("CASH", 50.55, 0.0, ref = "R1")), z, "KSh")
        val rows = csv.trim().split("\r\n")
        assertEquals("Date,Receipt,Payment method,Net (KSh),VAT (KSh),Total (KSh),M-Pesa code", rows[0])
        assertEquals("2025-11-05,R1,Cash,50.55,0.00,50.55,", rows[1])          // oldest first
        assertEquals("2025-11-05,R2,Mpesa,100.00,16.00,116.00,", rows[2])
        assertEquals(",,Totals,150.55,16.00,166.55,", rows[3])
    }

    @Test fun salesListDefusesFormulaLookingReceiptsAndCodes() {
        val csv = AccountingExport.salesCsv(listOf(AcctSale(t0, "=1+1", "CASH", m(10.0), m(0.0), mpesaRef = "@x")), z, "KSh")
        assertTrue(csv.contains("'=1+1") && csv.contains("'@x"), csv)
    }
}
