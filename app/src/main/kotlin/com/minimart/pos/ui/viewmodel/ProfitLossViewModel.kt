package com.minimart.pos.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimart.pos.data.dao.SaleDao
import com.minimart.pos.data.repository.ExpenseRepository
import com.minimart.pos.data.repository.SaleRepository
import com.minimart.pos.data.repository.SettingsRepository
import com.minimart.pos.util.AccountingExport
import com.minimart.pos.util.AccountingFiles
import com.minimart.pos.util.Delta
import com.minimart.pos.util.Money
import com.minimart.pos.util.ProductMargin
import com.minimart.pos.util.ProfitAndLoss
import com.minimart.pos.util.ProfitReport
import com.minimart.pos.util.monthStartMs
import com.minimart.pos.util.todayStartMs
import com.minimart.pos.util.weekStartMs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** What the Profit & Loss screen shows for one period. */
data class ProfitUiState(
    val period: ReportPeriod = ReportPeriod.MONTH,
    val loading: Boolean = true,
    val currency: String = "KES",
    val label: String = "",
    val pnl: ProfitAndLoss? = null,
    val before: ProfitAndLoss? = null,
    val deltas: List<Delta> = emptyList(),
    val products: List<ProductMargin> = emptyList()
)

/**
 * Profit & Loss for today, this week or this month, set against the stretch of equal length just before it.
 * It reads the same cost figures the accountant's export uses (FIFO cost on each sale line), so the screen and the
 * files always agree.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProfitLossViewModel @Inject constructor(
    private val saleRepo: SaleRepository,
    private val expenseRepo: ExpenseRepository,
    private val settingsRepo: SettingsRepository,
    private val saleDao: SaleDao
) : ViewModel() {

    private val _period = MutableStateFlow(ReportPeriod.MONTH)
    val period: StateFlow<ReportPeriod> = _period.asStateFlow()
    private val _refresh = MutableStateFlow(0)

    val state: StateFlow<ProfitUiState> = kotlinx.coroutines.flow.combine(_period, _refresh) { p, _ -> p }
        .flatMapLatest { p ->
            flow {
                emit(ProfitUiState(period = p, loading = true))
                emit(load(p))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfitUiState())

    fun setPeriod(p: ReportPeriod) { if (p != ReportPeriod.CUSTOM) _period.value = p }
    fun refresh() { _refresh.value += 1 }

    private fun range(p: ReportPeriod, now: Long): Pair<Long, Long> = when (p) {
        ReportPeriod.TODAY -> todayStartMs() to now
        ReportPeriod.WEEK -> weekStartMs() to now
        else -> monthStartMs() to now
    }

    private suspend fun pnl(start: Long, end: Long): ProfitAndLoss {
        val sales = saleRepo.getCompletedSalesByDateRange(start, end).first()
        val costs = saleDao.costsBetween(start, end).associate { it.saleId to it.cost }
        val expenses = expenseRepo.getExpensesByDateRange(start, end).first()
        return AccountingExport.profitAndLoss(AccountingFiles.acctSales(sales, costs), AccountingFiles.acctExpenses(expenses))
    }

    private suspend fun load(p: ReportPeriod): ProfitUiState {
        val now = System.currentTimeMillis()
        val (start, end) = range(p, now)
        val currency = settingsRepo.currency.first()
        val day = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val label = "${day.format(Date(start))} - ${day.format(Date(end))}"
        val current = pnl(start, end)
        val prev = ProfitReport.previousRange(start, end)
        val before = prev?.let { pnl(it.first, it.second - 1) }
        val products = saleRepo.getProductSalesStats(start, end).first().map {
            ProductMargin(it.productName, it.category, it.qty, Money.fromDouble(it.revenue), Money.fromDouble(it.cost), Money.fromDouble(it.costedRevenue))
        }
        return ProfitUiState(
            period = p, loading = false, currency = currency, label = label, pnl = current, before = before,
            deltas = if (before != null && before.salesCount + before.totalExpenses.cents > 0) ProfitReport.compare(current, before) else emptyList(),
            products = products
        )
    }

    /** The profit-and-loss statement and the product margin table as CSV files, ready to share. */
    suspend fun buildFiles(context: Context): List<File> {
        val s = state.value
        val pnl = s.pnl ?: return emptyList()
        val store = settingsRepo.storeName.first()
        return withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "archives/exports").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
            val a = File(dir, "profit_and_loss_$stamp.csv").also {
                it.writeBytes(bom + AccountingExport.profitAndLossCsv(pnl, store, s.label, s.currency).toByteArray(Charsets.UTF_8))
            }
            val b = File(dir, "profit_by_product_$stamp.csv").also {
                it.writeBytes(bom + ProfitReport.productMarginCsv(s.products, store, s.label, s.currency).toByteArray(Charsets.UTF_8))
            }
            listOf(a, b)
        }
    }
}
