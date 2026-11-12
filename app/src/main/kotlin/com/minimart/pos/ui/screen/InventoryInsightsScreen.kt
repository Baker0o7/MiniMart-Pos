package com.minimart.pos.ui.screen

import com.minimart.pos.ui.components.Text
import com.minimart.pos.util.ArabicCount
import com.minimart.pos.util.Lang
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.minimart.pos.ui.theme.DT
import com.minimart.pos.ui.viewmodel.InsightRow
import com.minimart.pos.ui.viewmodel.InventoryInsights
import com.minimart.pos.ui.viewmodel.InventoryInsightsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class InsightTab(private val en: String, private val ar: String) {
    REORDER("Reorder", "إعادة الطلب"), DEAD("Dead stock", "مخزون راكد"), FAST("Fast movers", "الأكثر مبيعًا"), CATEGORIES("By category", "حسب الفئة");
    val label: String get() = Lang.t(en, ar)
}

private fun money(v: Double) = String.format(Locale.US, "%,.0f", v)

@Composable
fun InventoryInsightsScreen(onBack: () -> Unit, vm: InventoryInsightsViewModel = hiltViewModel()) {
    val ins by vm.insights.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(InsightTab.REORDER) }
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().background(DT.Bg)) {
        GradientHeader(
            title = tr(com.minimart.pos.R.string.stock_insights),
            subtitle = Lang.t("${ins.skuCount} products • ${ins.reorder.size} to reorder",
                ArabicCount.phrase(ins.skuCount, "لا منتجات", "منتج واحد", "منتجان", "منتجات", "منتجًا") + " • " +
                    (if (ins.reorder.isEmpty()) "لا شيء لإعادة الطلب" else "${ins.reorder.size} لإعادة الطلب")),
            onBack = onBack,
            actions = {
                if (ins.reorder.isNotEmpty()) HeaderPillButton(Lang.t("Share", "مشاركة"), Icons.Default.Share) {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "${ins.storeName} – reorder list")
                        putExtra(Intent.EXTRA_TEXT, reorderText(ins))
                    }
                    try { context.startActivity(Intent.createChooser(send, Lang.t("Share reorder list", "مشاركة قائمة إعادة الطلب")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    catch (_: Exception) { }
                }
            }
        )

        if (!ins.loaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DT.Teal) }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Tile(Lang.t("Stock at cost", "المخزون بسعر التكلفة"), "${ins.currency} ${money(ins.costValue)}", DT.OnSurface, Modifier.weight(1f))
                        Tile(Lang.t("Stock at retail", "المخزون بسعر البيع"), "${ins.currency} ${money(ins.retailValue)}", DT.TealLight, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Tile(Lang.t("Potential profit", "الربح المتوقع"), "${ins.currency} ${money(ins.potentialProfit)}",
                            if (ins.potentialProfit >= 0) DT.Green else DT.Red, Modifier.weight(1f))
                        Tile(Lang.t("Out of stock", "نفد من المخزون"), ins.outOfStock.toString(),
                            if (ins.outOfStock == 0) DT.Green else DT.Red, Modifier.weight(1f))
                    }
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(InsightTab.values().toList()) { t ->
                        val count = when (t) {
                            InsightTab.REORDER -> " (${ins.reorder.size})"
                            InsightTab.DEAD -> " (${ins.dead.size})"
                            else -> ""
                        }
                        val picked = t == tab
                        Box(
                            Modifier.clip(RoundedCornerShape(20.dp))
                                .background(if (picked) DT.Teal else DT.Surface)
                                .border(1.dp, if (picked) DT.Teal else DT.Border, RoundedCornerShape(20.dp))
                                .clickable(role = androidx.compose.ui.semantics.Role.Button, indication = null, interactionSource = remember { MutableInteractionSource() }) { tab = t }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(t.label + count, color = if (picked) Color.White else DT.SubText, fontSize = 13.sp,
                                fontWeight = if (picked) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }

            when (tab) {
                InsightTab.REORDER -> {
                    item { Hint(Lang.t("Low on stock, or likely to run out within a week. Quantities aim for about two weeks of sales.", "مخزونها منخفض أو قد ينفد خلال أسبوع. الكميات المقترحة تكفي لنحو أسبوعين من المبيعات.")) }
                    if (ins.reorder.isEmpty()) item { Empty(Lang.t("Nothing needs reordering right now", "لا شيء يحتاج إلى إعادة طلب الآن")) }
                    items(ins.reorder, key = { it.product.id }) { ReorderRow(it) }
                }
                InsightTab.DEAD -> {
                    item {
                        Hint(Lang.t("In stock but not sold for 30+ days. ${ins.currency} ${money(ins.deadCostValue)} of stock (at cost) is tied up here.", "متوفرة لكنها لم تُبع منذ أكثر من 30 يومًا. مخزون بقيمة ${ins.currency} ${money(ins.deadCostValue)} (بسعر التكلفة) مجمّد هنا."))
                    }
                    if (ins.dead.isEmpty()) item { Empty(Lang.t("No dead stock — everything is moving", "لا مخزون راكد — كل شيء يُباع")) }
                    items(ins.dead, key = { it.product.id }) { DeadRow(it, ins.currency) }
                }
                InsightTab.FAST -> {
                    item { Hint(Lang.t("Best sellers over the last 30 days, by quantity sold.", "الأكثر مبيعًا خلال آخر 30 يومًا حسب الكمية المباعة.")) }
                    if (ins.fast.isEmpty()) item { Empty(Lang.t("No sales in the last 30 days", "لا مبيعات خلال آخر 30 يومًا")) }
                    items(ins.fast, key = { it.product.id }) { FastRow(it) }
                }
                InsightTab.CATEGORIES -> {
                    item { Hint(Lang.t("Value of the stock you hold in each category.", "قيمة المخزون الذي تملكه في كل فئة.")) }
                    items(ins.categories, key = { it.category }) { c ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(DT.Surface)
                                .border(1.dp, DT.Border, RoundedCornerShape(14.dp)).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(c.category, color = DT.OnSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(ArabicCount.t(c.items, "product", "products", "لا منتجات", "منتج واحد", "منتجان", "منتجات", "منتجًا"), color = DT.SubText, fontSize = 11.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${ins.currency} ${money(c.retailValue)}", color = DT.TealLight, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(tr(com.minimart.pos.R.string.cost_fmt, money(c.costValue)), color = DT.SubText, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun reorderText(ins: InventoryInsights): String = buildString {
    appendLine("${ins.storeName} – reorder list")
    appendLine(SimpleDateFormat("EEE d MMM yyyy", Locale.getDefault()).format(Date()))
    ins.reorder.groupBy { it.product.supplierName.ifBlank { Lang.t("No supplier set", "لم يُحدَّد مورّد") } }.forEach { (supplier, rows) ->
        appendLine()
        val phone = rows.firstOrNull { it.product.supplierPhone.isNotBlank() }?.product?.supplierPhone
        val est = rows.sumOf { it.orderCost }
        appendLine((if (phone != null) "$supplier ($phone)" else supplier) + if (est > 0) " — about ${ins.currency} ${money(est)}" else "")
        rows.forEach { r ->
            val unit = if (r.product.isWeighed) "kg" else r.product.unit
            appendLine("• ${r.product.name} — ${r.suggestedQty} $unit (have ${plainNumber(r.onHand)})")
        }
    }
}

@Composable
private fun Tile(label: String, value: String, color: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(DT.Surface)
            .border(1.dp, DT.Border, RoundedCornerShape(16.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(label, color = DT.SubText, fontSize = 11.sp)
        Text(value, color = color, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Hint(text: String) = Text(text, color = DT.SubText, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 2.dp))

@Composable
private fun Empty(text: String) = Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
    Text(text, color = DT.SubText, fontSize = 13.sp, textAlign = TextAlign.Center)
}

@Composable
private fun RowCard(content: @Composable RowScope.() -> Unit) = Row(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(DT.Surface)
        .border(1.dp, DT.Border, RoundedCornerShape(14.dp)).padding(12.dp),
    verticalAlignment = Alignment.CenterVertically, content = content
)

private fun unitOf(r: InsightRow) = if (r.product.isWeighed) "kg" else r.product.unit

@Composable
private fun ReorderRow(r: InsightRow) = RowCard {
    Column(Modifier.weight(1f)) {
        Text(r.product.name, color = DT.OnSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        val cover = r.daysCover?.let { if (it < 1) Lang.t("under a day left", "يكفي أقل من يوم") else Lang.t("~${it.toInt()} days left", "يكفي نحو ${it.toInt()} أيام") } ?: Lang.t("not selling lately", "لا مبيعات مؤخرًا")
        Text("${Lang.t("Have", "المتوفر")} ${plainNumber(r.onHand)} ${unitOf(r)} • $cover", color = if (r.onHand <= 0) DT.Red else DT.Amber, fontSize = 11.sp)
        r.emptyInDays?.takeIf { r.onHand > 0 }?.let { d ->
            val day = java.text.SimpleDateFormat("EEE d MMM", androidx.compose.ui.text.intl.Locale.current.platformLocale).format(Date(System.currentTimeMillis() + d * 86_400_000L))
            Text(Lang.t("Forecast: runs out around $day", "المتوقع: ينفد تقريبًا في $day") + if (r.forecastConfidence < 0.5) Lang.t(" (rough, little history)", " (تقدير تقريبي، سجل قليل)") else "",
                color = DT.SubText, fontSize = 11.sp)
            r.accuracy?.let { Text(it.label(), color = DT.SubText, fontSize = 10.sp) }
        }
        if (r.product.supplierName.isNotBlank())
            Text(r.product.supplierName, color = DT.SubText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Column(horizontalAlignment = Alignment.End) {
        Text(tr(com.minimart.pos.R.string.order_lbl), color = DT.SubText, fontSize = 11.sp)
        Text("${r.suggestedQty}", color = DT.TealLight, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        if (r.orderCost > 0) Text("≈ ${money(r.orderCost)}", color = DT.SubText, fontSize = 11.sp)
    }
}

@Composable
private fun DeadRow(r: InsightRow, currency: String) = RowCard {
    Column(Modifier.weight(1f)) {
        Text(r.product.name, color = DT.OnSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        val last = r.lastSoldAt?.let { Lang.t("last sold ", "آخر بيع ") + SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(it)) } ?: Lang.t("never sold", "لم يُبع قط")
        Text("${plainNumber(r.onHand)} ${unitOf(r)} • $last", color = DT.SubText, fontSize = 11.sp)
    }
    Column(horizontalAlignment = Alignment.End) {
        val v = if (r.costValue > 0) r.costValue else r.retailValue
        Text("$currency ${money(v)}", color = DT.Amber, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(if (r.costValue > 0) Lang.t("at cost", "بسعر التكلفة") else Lang.t("at retail", "بسعر البيع"), color = DT.SubText, fontSize = 11.sp)
    }
}

@Composable
private fun FastRow(r: InsightRow) = RowCard {
    Column(Modifier.weight(1f)) {
        Text(r.product.name, color = DT.OnSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${plainNumber(r.avgDaily, 1)} ${unitOf(r)}${Lang.t("/day", "/يوم")} • ${plainNumber(r.onHand)} ${Lang.t("in stock", "في المخزون")}", color = DT.SubText, fontSize = 11.sp)
    }
    Column(horizontalAlignment = Alignment.End) {
        Text(plainNumber(r.sold30), color = DT.Green, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        Text(tr(com.minimart.pos.R.string.sold_30d), color = DT.SubText, fontSize = 11.sp)
    }
}
