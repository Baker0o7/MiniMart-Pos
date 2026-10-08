package com.minimart.pos.ui.screen

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

private enum class InsightTab(val label: String) {
    REORDER("Reorder"), DEAD("Dead stock"), FAST("Fast movers"), CATEGORIES("By category")
}

private fun money(v: Double) = String.format(Locale.US, "%,.0f", v)

@Composable
fun InventoryInsightsScreen(onBack: () -> Unit, vm: InventoryInsightsViewModel = hiltViewModel()) {
    val ins by vm.insights.collectAsState()
    var tab by rememberSaveable { mutableStateOf(InsightTab.REORDER) }
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().background(DT.Bg)) {
        GradientHeader(
            title = "Stock Insights",
            subtitle = "${ins.skuCount} products • ${ins.reorder.size} to reorder",
            onBack = onBack,
            actions = {
                if (ins.reorder.isNotEmpty()) HeaderPillButton("Share", Icons.Default.Share) {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "${ins.storeName} – reorder list")
                        putExtra(Intent.EXTRA_TEXT, reorderText(ins))
                    }
                    try { context.startActivity(Intent.createChooser(send, "Share reorder list").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
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
                        Tile("Stock at cost", "${ins.currency} ${money(ins.costValue)}", DT.OnSurface, Modifier.weight(1f))
                        Tile("Stock at retail", "${ins.currency} ${money(ins.retailValue)}", DT.TealLight, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Tile("Potential profit", "${ins.currency} ${money(ins.potentialProfit)}",
                            if (ins.potentialProfit >= 0) DT.Green else DT.Red, Modifier.weight(1f))
                        Tile("Out of stock", ins.outOfStock.toString(),
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
                                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { tab = t }
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
                    item { Hint("Low on stock, or likely to run out within a week. Quantities aim for about two weeks of sales.") }
                    if (ins.reorder.isEmpty()) item { Empty("Nothing needs reordering right now") }
                    items(ins.reorder, key = { it.product.id }) { ReorderRow(it) }
                }
                InsightTab.DEAD -> {
                    item {
                        Hint("In stock but not sold for 30+ days. ${ins.currency} ${money(ins.deadCostValue)} of stock (at cost) is tied up here.")
                    }
                    if (ins.dead.isEmpty()) item { Empty("No dead stock — everything is moving") }
                    items(ins.dead, key = { it.product.id }) { DeadRow(it, ins.currency) }
                }
                InsightTab.FAST -> {
                    item { Hint("Best sellers over the last 30 days, by quantity sold.") }
                    if (ins.fast.isEmpty()) item { Empty("No sales in the last 30 days") }
                    items(ins.fast, key = { it.product.id }) { FastRow(it) }
                }
                InsightTab.CATEGORIES -> {
                    item { Hint("Value of the stock you hold in each category.") }
                    items(ins.categories, key = { it.category }) { c ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(DT.Surface)
                                .border(1.dp, DT.Border, RoundedCornerShape(14.dp)).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(c.category, color = DT.OnSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${c.items} product${if (c.items != 1) "s" else ""}", color = DT.SubText, fontSize = 11.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${ins.currency} ${money(c.retailValue)}", color = DT.TealLight, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("cost ${money(c.costValue)}", color = DT.SubText, fontSize = 11.sp)
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
    ins.reorder.groupBy { it.product.supplierName.ifBlank { "No supplier set" } }.forEach { (supplier, rows) ->
        appendLine()
        val phone = rows.firstOrNull { it.product.supplierPhone.isNotBlank() }?.product?.supplierPhone
        appendLine(if (phone != null) "$supplier ($phone)" else supplier)
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
        val cover = r.daysCover?.let { if (it < 1) "under a day left" else "~${it.toInt()} days left" } ?: "not selling lately"
        Text("Have ${plainNumber(r.onHand)} ${unitOf(r)} • $cover", color = if (r.onHand <= 0) DT.Red else DT.Amber, fontSize = 11.sp)
        if (r.product.supplierName.isNotBlank())
            Text(r.product.supplierName, color = DT.SubText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Column(horizontalAlignment = Alignment.End) {
        Text("Order", color = DT.SubText, fontSize = 10.sp)
        Text("${r.suggestedQty}", color = DT.TealLight, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
    }
}

@Composable
private fun DeadRow(r: InsightRow, currency: String) = RowCard {
    Column(Modifier.weight(1f)) {
        Text(r.product.name, color = DT.OnSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        val last = r.lastSoldAt?.let { "last sold ${SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(it))}" } ?: "never sold"
        Text("${plainNumber(r.onHand)} ${unitOf(r)} • $last", color = DT.SubText, fontSize = 11.sp)
    }
    Column(horizontalAlignment = Alignment.End) {
        val v = if (r.costValue > 0) r.costValue else r.retailValue
        Text("$currency ${money(v)}", color = DT.Amber, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(if (r.costValue > 0) "at cost" else "at retail", color = DT.SubText, fontSize = 10.sp)
    }
}

@Composable
private fun FastRow(r: InsightRow) = RowCard {
    Column(Modifier.weight(1f)) {
        Text(r.product.name, color = DT.OnSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${plainNumber(r.avgDaily, 1)} ${unitOf(r)}/day • ${plainNumber(r.onHand)} in stock", color = DT.SubText, fontSize = 11.sp)
    }
    Column(horizontalAlignment = Alignment.End) {
        Text(plainNumber(r.sold30), color = DT.Green, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        Text("sold (30d)", color = DT.SubText, fontSize = 10.sp)
    }
}
