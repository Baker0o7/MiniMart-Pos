package com.minimart.pos.ui.screen

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minimart.pos.ui.components.Text
import com.minimart.pos.ui.theme.DT
import com.minimart.pos.ui.viewmodel.ProfitLossViewModel
import com.minimart.pos.ui.viewmodel.ReportPeriod
import com.minimart.pos.util.CategoryMargin
import com.minimart.pos.util.Delta
import com.minimart.pos.util.ErrorAdvice
import com.minimart.pos.util.Lang
import com.minimart.pos.util.Money
import com.minimart.pos.util.ProductMargin
import com.minimart.pos.util.ProfitAndLoss
import com.minimart.pos.util.ProfitReport
import kotlinx.coroutines.launch
import java.util.Locale

private enum class PnlTab(private val en: String, private val ar: String) {
    STATEMENT("Statement", "القائمة"), COMPARE("Compared", "المقارنة"), PRODUCTS("Products", "المنتجات"),
    CATEGORIES("Categories", "الفئات"), ATTENTION("Needs attention", "يحتاج انتباهًا");
    val label: String get() = Lang.t(en, ar)
}

private val PERIODS = listOf(ReportPeriod.TODAY, ReportPeriod.WEEK, ReportPeriod.MONTH)

private fun fmt(m: Money) = String.format(Locale.US, "%,.2f", m.toDouble())

/** Advanced Profit & Loss: the statement, the period before it, and which products and categories earn (or lose) money. */
@Composable
fun ProfitLossScreen(onBack: () -> Unit, vm: ProfitLossViewModel = hiltViewModel()) {
    val ui by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(PnlTab.STATEMENT) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sharing by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(DT.Bg)) {
        GradientHeader(
            title = Lang.t("Profit & Loss", "الأرباح والخسائر"),
            subtitle = ui.label.ifBlank { null },
            onBack = onBack,
            actions = {
                HeaderPillButton(Lang.t("Export", "تصدير"), Icons.Default.Share) {
                    if (sharing || ui.pnl == null) return@HeaderPillButton
                    sharing = true
                    scope.launch {
                        try {
                            shareFiles(context, vm.buildFiles(context))
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, Lang.t("Could not create the files. ", "تعذّر إنشاء الملفات. ") + ErrorAdvice.reason(e), android.widget.Toast.LENGTH_LONG).show()
                        } finally { sharing = false }
                    }
                }
            }
        )

        LazyRow(Modifier.padding(top = 10.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(PERIODS) { p -> Chip(p.label, p == ui.period) { vm.setPeriod(p) } }
        }

        val pnl = ui.pnl
        if (ui.loading || pnl == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DT.Teal) }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()
        ) {
            item { Headline(pnl, ui.currency) }
            if (!pnl.costKnown && pnl.salesCount > 0) item {
                Note(Lang.t("No item costs were recorded in this period, so profit is overstated. Add cost prices or receive stock through purchase orders.",
                    "لم تُسجَّل تكاليف الأصناف في هذه الفترة، لذلك الربح مبالغ فيه. أضف أسعار التكلفة أو استلم المخزون عبر أوامر الشراء."))
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PnlTab.values().toList()) { t -> Chip(t.label, t == tab) { tab = t } }
                }
            }
            when (tab) {
                PnlTab.STATEMENT -> item { Statement(pnl, ui.currency) }
                PnlTab.COMPARE -> {
                    if (ui.deltas.isEmpty()) item { Empty(Lang.t("Nothing earlier to compare with yet", "لا توجد فترة سابقة للمقارنة بعد")) }
                    else {
                        item { Hint(Lang.t("Against the period of the same length just before this one.", "مقارنةً بالفترة السابقة بنفس الطول.")) }
                        items(ui.deltas, key = { it.label }) { DeltaRow(it, ui.currency) }
                    }
                }
                PnlTab.PRODUCTS -> {
                    val ranked = ProfitReport.ranked(ui.products)
                    if (ranked.isEmpty()) item { Empty(Lang.t("No sales in this period", "لا مبيعات في هذه الفترة")) }
                    items(ranked, key = { it.name + "|" + it.category }) { ProductRow(it, ui.currency) }
                }
                PnlTab.CATEGORIES -> {
                    val cats = ProfitReport.byCategory(ui.products)
                    if (cats.isEmpty()) item { Empty(Lang.t("No sales in this period", "لا مبيعات في هذه الفترة")) }
                    items(cats, key = { it.category }) { CategoryRow(it, ui.currency) }
                }
                PnlTab.ATTENTION -> {
                    val below = ProfitReport.belowCost(ui.products)
                    val missing = ProfitReport.missingCost(ui.products)
                    if (below.isEmpty() && missing.isEmpty()) item { Empty(Lang.t("Nothing needs attention", "لا شيء يحتاج انتباهًا")) }
                    if (below.isNotEmpty()) {
                        item { Hint(Lang.t("Sold for less than they cost:", "بيعت بأقل من تكلفتها:")) }
                        items(below, key = { "b" + it.name + it.category }) { ProductRow(it, ui.currency) }
                    }
                    if (missing.isNotEmpty()) {
                        item { Hint(Lang.t("Sold, but no cost price is recorded (profit unknown):", "بيعت لكن لا يوجد سعر تكلفة (الربح غير معروف):")) }
                        items(missing, key = { "m" + it.name + it.category }) { ProductRow(it, ui.currency) }
                    }
                }
            }
        }
    }
}

private fun shareFiles(context: Context, files: List<java.io.File>) {
    if (files.isEmpty()) return
    com.minimart.pos.util.AccountingFiles.share(context, files, Lang.t("Profit and loss", "الأرباح والخسائر"))
}

@Composable
private fun Chip(label: String, picked: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(20.dp))
            .background(if (picked) DT.Teal else DT.Surface)
            .border(1.dp, if (picked) DT.Teal else DT.Border, RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) { Text(label, color = if (picked) Color.White else DT.SubText, fontSize = 13.sp, fontWeight = if (picked) FontWeight.Bold else FontWeight.Normal) }
}

@Composable
private fun Headline(p: ProfitAndLoss, currency: String) {
    val margin = ProfitReport.grossMarginPercent(p)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(Lang.t("Revenue (no VAT)", "الإيرادات (بدون ضريبة)"), "$currency ${fmt(p.revenueNet)}", DT.OnSurface, Modifier.weight(1f))
            Tile(Lang.t("Gross profit", "إجمالي الربح"), "$currency ${fmt(p.grossProfit)}", if (p.grossProfit.isNegative()) DT.Red else DT.Green, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(Lang.t("Gross margin", "هامش الربح الإجمالي"), ProfitReport.percentText(margin), DT.TealLight, Modifier.weight(1f))
            Tile(Lang.t("Net profit", "صافي الربح"), "$currency ${fmt(p.netProfit)}", if (p.netProfit.isNegative()) DT.Red else DT.Green, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Statement(p: ProfitAndLoss, currency: String) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(DT.Surface).border(1.dp, DT.Border, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Line(Lang.t("Sales (${p.salesCount})", "المبيعات (${p.salesCount})"), p.takings, currency)
        Line(Lang.t("less VAT collected", "ناقصًا الضريبة المحصّلة"), -p.vatCollected, currency)
        Line(Lang.t("Revenue", "الإيرادات"), p.revenueNet, currency, bold = true)
        Line(Lang.t("Cost of goods sold", "تكلفة البضاعة المباعة"), -p.costOfGoods, currency)
        Line(Lang.t("Gross profit", "إجمالي الربح"), p.grossProfit, currency, bold = true)
        p.expensesByCategory.forEach { (k, v) -> Line(k, -v, currency) }
        Line(Lang.t("Total expenses", "إجمالي المصروفات"), -p.totalExpenses, currency)
        Line(Lang.t("Net profit", "صافي الربح"), p.netProfit, currency, bold = true)
    }
}

@Composable
private fun Line(label: String, value: Money, currency: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = if (bold) DT.OnSurface else DT.SubText, fontSize = 13.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
        Text("$currency ${fmt(value)}", color = if (value.isNegative() && bold) DT.Red else DT.OnSurface, fontSize = 13.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

private fun deltaLabel(label: String) = when (label) {
    "Revenue" -> Lang.t("Revenue", "الإيرادات")
    "Cost of goods" -> Lang.t("Cost of goods", "تكلفة البضاعة")
    "Gross profit" -> Lang.t("Gross profit", "إجمالي الربح")
    "Expenses" -> Lang.t("Expenses", "المصروفات")
    else -> Lang.t("Net profit", "صافي الربح")
}

@Composable
private fun DeltaRow(d: Delta, currency: String) {
    // For costs, going up is bad; for income and profit, going up is good.
    val upIsGood = d.label != "Cost of goods" && d.label != "Expenses"
    val c = when {
        d.change.isZero() -> DT.SubText
        (d.change.isPositive()) == upIsGood -> DT.Green
        else -> DT.Red
    }
    RowCard {
        Column(Modifier.weight(1f)) {
            Text(deltaLabel(d.label), color = DT.OnSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(Lang.t("Before: $currency ${fmt(d.before)}", "قبل: $currency ${fmt(d.before)}"), color = DT.SubText, fontSize = 11.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("$currency ${fmt(d.now)}", color = DT.OnSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(ProfitReport.percentText(d.changePercent, signed = true), color = c, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ProductRow(p: ProductMargin, currency: String) = RowCard {
    Column(Modifier.weight(1f)) {
        Text(p.name, color = DT.OnSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(Lang.t("${p.category} • sold $currency ${fmt(p.revenue)}", "${p.category} • مبيعات $currency ${fmt(p.revenue)}"), color = DT.SubText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Column(horizontalAlignment = Alignment.End) {
        if (p.costKnown) {
            Text("$currency ${fmt(p.profit)}", color = if (p.belowCost) DT.Red else DT.Green, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(ProfitReport.percentText(p.marginPercent), color = DT.SubText, fontSize = 11.sp)
        } else Text(Lang.t("No cost", "بلا تكلفة"), color = DT.SubText, fontSize = 12.sp)
    }
}

@Composable
private fun CategoryRow(c: CategoryMargin, currency: String) = RowCard {
    Column(Modifier.weight(1f)) {
        Text(c.category, color = DT.OnSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(Lang.t("sold $currency ${fmt(c.revenue)} • ${c.products} products", "مبيعات $currency ${fmt(c.revenue)} • ${c.products} منتج"), color = DT.SubText, fontSize = 11.sp)
    }
    Column(horizontalAlignment = Alignment.End) {
        Text("$currency ${fmt(c.profit)}", color = if (c.profit.isNegative()) DT.Red else DT.Green, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(ProfitReport.percentText(c.marginPercent), color = DT.SubText, fontSize = 11.sp)
    }
}

@Composable
private fun Tile(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(DT.Surface).border(1.dp, DT.Border, RoundedCornerShape(16.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = DT.SubText, fontSize = 11.sp)
        Text(value, color = color, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Note(text: String) = Text(text, color = DT.Amber, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DT.Surface).padding(12.dp))

@Composable
private fun Hint(text: String) = Text(text, color = DT.SubText, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 2.dp))

@Composable
private fun Empty(text: String) = Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
    Text(text, color = DT.SubText, fontSize = 13.sp, textAlign = TextAlign.Center)
}

@Composable
private fun RowCard(content: @Composable RowScope.() -> Unit) = Row(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(DT.Surface).border(1.dp, DT.Border, RoundedCornerShape(14.dp)).padding(12.dp),
    verticalAlignment = Alignment.CenterVertically, content = content
)
