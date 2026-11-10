package com.minimart.pos.util

import java.time.Instant
import java.time.ZoneId

/** One completed sale, as the books need it. Amounts are cent-exact [Money]. */
data class AcctSale(
    val at: Long, val receipt: String, val method: String,
    val total: Money, val tax: Money,
    /** Cash actually taken; only matters for MIXED sales (the rest is on credit). */
    val cashPortion: Money = Money.ZERO,
    /** What the goods cost; zero when it was not recorded. */
    val cost: Money = Money.ZERO,
    val mpesaRef: String = ""
)

data class AcctExpense(val at: Long, val category: String, val title: String, val amount: Money, val ref: String = "", val supplier: String = "")

/** One line of the double-entry journal: every sale and expense becomes lines whose debits equal their credits. */
data class JournalLine(val date: String, val ref: String, val account: String, val debit: Money, val credit: Money, val memo: String)

/** Profit and loss for a period. [costKnown] is false when no sale carried a cost, so profit would be overstated. */
data class ProfitAndLoss(
    val salesCount: Int, val revenueNet: Money, val vatCollected: Money, val takings: Money,
    val costOfGoods: Money, val grossProfit: Money,
    val expensesByCategory: Map<String, Money>, val totalExpenses: Money, val netProfit: Money,
    val takingsByMethod: Map<String, Money>, val costKnown: Boolean
)

/**
 * What an accountant asks a shop for: a journal that balances, a profit and loss, and a VAT figure, as plain
 * CSV that opens in Excel or Google Sheets. Built on [Money] so totals are exact to the cent. Refunded and
 * voided sales are left out by the caller (they are not income), and text cells that could be read as a
 * spreadsheet formula are defused ([cell]) because names and notes come from people and customers.
 */
object AccountingExport {
    const val CASH = "Cash on hand"
    const val MPESA = "M-Pesa received"
    const val CARD = "Card payments received"
    const val RECEIVABLE = "Customer credit (owed to shop)"
    const val SALES = "Sales revenue"
    const val VAT = "VAT payable"
    const val COGS = "Cost of goods sold"
    const val INVENTORY = "Inventory"
    const val PAID_OUT = "Cash paid out for expenses"

    fun journal(sales: List<AcctSale>, expenses: List<AcctExpense>, zone: ZoneId): List<JournalLine> {
        val out = mutableListOf<Pair<Long, List<JournalLine>>>()
        for (s in sales) {
            val d = day(s.at, zone); val lines = mutableListOf<JournalLine>()
            val total = s.total.coerceAtLeast(Money.ZERO)
            val tax = s.tax.coerceAtLeast(Money.ZERO).coerceAtMost(total)
            fun dr(acc: String, m: Money, memo: String = "Sale") { if (m.cents != 0L) lines += JournalLine(d, s.receipt, acc, m, Money.ZERO, memo) }
            fun cr(acc: String, m: Money, memo: String = "Sale") { if (m.cents != 0L) lines += JournalLine(d, s.receipt, acc, Money.ZERO, m, memo) }
            when (s.method.uppercase()) {
                "CASH" -> dr(CASH, total)
                "MPESA" -> dr(MPESA, total, if (s.mpesaRef.isBlank()) "Sale" else "Sale, M-Pesa ${s.mpesaRef}")
                "CARD" -> dr(CARD, total)
                "CREDIT" -> dr(RECEIVABLE, total)
                else -> { val cash = s.cashPortion.coerceAtLeast(Money.ZERO).coerceAtMost(total); dr(CASH, cash); dr(RECEIVABLE, total - cash) }
            }
            cr(SALES, total - tax); cr(VAT, tax, "VAT in sale")
            if (s.cost.cents > 0) { dr(COGS, s.cost, "Cost of items sold"); cr(INVENTORY, s.cost, "Cost of items sold") }
            out += s.at to lines
        }
        for (e in expenses) {
            val d = day(e.at, zone); val amt = e.amount.coerceAtLeast(Money.ZERO)
            if (amt.cents == 0L) continue
            val ref = e.ref.ifBlank { "EXP" }
            val memo = listOf(e.title, e.supplier).filter { it.isNotBlank() }.joinToString(" - ")
            out += e.at to listOf(
                JournalLine(d, ref, "Expense: ${pretty(e.category)}", amt, Money.ZERO, memo),
                JournalLine(d, ref, PAID_OUT, Money.ZERO, amt, memo))
        }
        return out.sortedBy { it.first }.flatMap { it.second }
    }

    fun journalCsv(lines: List<JournalLine>, currency: String): String = buildString {
        append("Date,Reference,Account,Debit ($currency),Credit ($currency),Memo\r\n")
        for (l in lines) append(listOf(l.date, cell(l.ref), cell(l.account), amount(l.debit), amount(l.credit), cell(l.memo)).joinToString(",")).append("\r\n")
        val d = lines.fold(Money.ZERO) { a, l -> a + l.debit }; val c = lines.fold(Money.ZERO) { a, l -> a + l.credit }
        append(",,Totals,${amount(d)},${amount(c)},${if (d == c) "Balanced" else "DOES NOT BALANCE"}\r\n")
    }

    /**
     * One row per sale (date, receipt, payment method, net, VAT, total, M-Pesa code): the list an accountant matches
     * against the bank and M-Pesa statements, and what most bookkeeping packages take as a sales import. Newest last.
     */
    fun salesCsv(sales: List<AcctSale>, zone: ZoneId, currency: String): String = buildString {
        append("Date,Receipt,Payment method,Net ($currency),VAT ($currency),Total ($currency),M-Pesa code\r\n")
        for (s in sales.sortedBy { it.at }) {
            append(listOf(day(s.at, zone), cell(s.receipt), cell(pretty(s.method)), amount(s.total - s.tax), amount(s.tax), amount(s.total), cell(s.mpesaRef))
                .joinToString(",")).append("\r\n")
        }
        val total = sales.fold(Money.ZERO) { a, s -> a + s.total }; val tax = sales.fold(Money.ZERO) { a, s -> a + s.tax }
        append(",,Totals,${amount(total - tax)},${amount(tax)},${amount(total)},\r\n")
    }

    fun profitAndLoss(sales: List<AcctSale>, expenses: List<AcctExpense>): ProfitAndLoss {
        var takings = Money.ZERO; var vat = Money.ZERO; var cost = Money.ZERO
        val byMethod = linkedMapOf<String, Money>()
        for (s in sales) {
            val total = s.total.coerceAtLeast(Money.ZERO); val tax = s.tax.coerceAtLeast(Money.ZERO).coerceAtMost(total)
            takings += total; vat += tax; cost += s.cost.coerceAtLeast(Money.ZERO)
            val key = s.method.uppercase(); byMethod[key] = (byMethod[key] ?: Money.ZERO) + total
        }
        val byCat = linkedMapOf<String, Money>()
        for (e in expenses) { val k = pretty(e.category); byCat[k] = (byCat[k] ?: Money.ZERO) + e.amount.coerceAtLeast(Money.ZERO) }
        val totalExp = byCat.values.fold(Money.ZERO) { a, m -> a + m }
        val revenue = takings - vat; val gross = revenue - cost
        return ProfitAndLoss(sales.size, revenue, vat, takings, cost, gross,
            byCat.toList().sortedByDescending { it.second.cents }.toMap(LinkedHashMap()), totalExp, gross - totalExp,
            byMethod, costKnown = sales.any { it.cost.cents > 0 })
    }

    fun profitAndLossCsv(p: ProfitAndLoss, storeName: String, period: String, currency: String): String = buildString {
        fun row(a: String, b: String = "", c: String = "") = append(listOf(cell(a), cell(b), c).joinToString(",")).append("\r\n")
        row(storeName); row("Profit and loss", period); row("All amounts in $currency"); row("")
        row("Section", "Item", "Amount")
        row("Income", "Sales (${p.salesCount})", amount(p.takings))
        row("Income", "less VAT collected", amount(-p.vatCollected))
        row("Income", "Revenue excluding VAT", amount(p.revenueNet))
        row("Cost of sales", "Cost of goods sold", amount(-p.costOfGoods))
        row("Result", "Gross profit", amount(p.grossProfit))
        p.expensesByCategory.forEach { (k, v) -> row("Expenses", k, amount(-v)) }
        row("Expenses", "Total expenses", amount(-p.totalExpenses))
        row("Result", "Net profit", amount(p.netProfit))
        row("")
        row("Takings by payment method")
        p.takingsByMethod.forEach { (k, v) -> row("", pretty(k), amount(v)) }
        row("")
        row("VAT collected, to be paid over", "", amount(p.vatCollected))
        if (!p.costKnown && p.salesCount > 0) row("Note", "No item costs were recorded in this period, so gross and net profit are overstated.")
    }

    /** Text that a spreadsheet could run as a formula gets a leading apostrophe; quotes and commas are escaped. */
    fun cell(text: String): String {
        var t = text.replace("\r", " ").replace("\n", " ")
        if (t.isNotEmpty() && t[0] in "=+-@\t") t = "'$t"
        return if (t.any { it == ',' || it == '"' }) "\"" + t.replace("\"", "\"\"") + "\"" else t
    }

    /** Exact decimal text: 12345 cents -> "123.45", negative with a leading minus. */
    fun amount(m: Money): String {
        val c = m.cents; val a = kotlin.math.abs(c)
        return (if (c < 0) "-" else "") + (a / 100) + "." + (a % 100).toString().padStart(2, '0')
    }

    private fun day(at: Long, zone: ZoneId) = Instant.ofEpochMilli(at).atZone(zone).toLocalDate().toString()
    private fun pretty(s: String) = s.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}
