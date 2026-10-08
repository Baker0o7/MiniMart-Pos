package com.minimart.pos.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.minimart.pos.data.dao.ProductSalesStat
import com.minimart.pos.data.entity.PaymentMethod
import com.minimart.pos.ui.theme.DT
import com.minimart.pos.ui.viewmodel.AnalyticsRange
import com.minimart.pos.ui.viewmodel.AnalyticsState
import com.minimart.pos.ui.viewmodel.AnalyticsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Blue = Color(0xFF64B5F6)
private val Purple = Color(0xFFB39DDB)

private fun money(v: Double) = String.format(Locale.US, "%,.0f", v)

@Composable
fun AnalyticsScreen(onBack: () -> Unit, vm: AnalyticsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsState()
    val range by vm.range.collectAsState()

    Column(Modifier.fillMaxSize().background(DT.Bg)) {
        GradientHeader(title = "Analytics", subtitle = "Sales, profit and trends", onBack = onBack)

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnalyticsRange.values().forEach { r ->
                val picked = r == range
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(if (picked) DT.Teal else DT.Surface)
                        .border(1.dp, if (picked) DT.Teal else DT.Border, RoundedCornerShape(12.dp))
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { vm.setRange(r) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(r.label, color = if (picked) Color.White else DT.SubText, fontSize = 13.sp,
                        fontWeight = if (picked) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }

        when {
            !s.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DT.Teal)
            }
            s.transactions == 0 -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Insights, null, tint = DT.SubText, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("No sales in the last ${s.range.label}", color = DT.OnSurface, fontWeight = FontWeight.SemiBold)
                    Text("Complete a sale and your trends will show up here.", color = DT.SubText,
                        fontSize = 13.sp, textAlign = TextAlign.Center)
                }
            }
            else -> AnalyticsBody(s)
        }
    }
}

@Composable
private fun AnalyticsBody(s: AnalyticsState) {
    val cur = s.currency
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── KPIs ──────────────────────────────────────────────────────────────
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiTile("Revenue", "$cur ${money(s.revenue)}", DT.TealLight, Modifier.weight(1f)) {
                        s.revenueChangePct?.let { pct ->
                            val up = pct >= 0
                            Text("${if (up) "▲" else "▼"} ${String.format(Locale.US, "%.0f", kotlin.math.abs(pct))}% vs previous ${s.range.days} days",
                                color = if (up) DT.Green else DT.Red, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    KpiTile("Sales", s.transactions.toString(), DT.OnSurface, Modifier.weight(1f)) {
                        Text("avg basket $cur ${money(s.avgBasket)}", color = DT.SubText, fontSize = 10.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiTile("Gross profit", "$cur ${money(s.grossProfit)}",
                        if (s.grossProfit >= 0) DT.Green else DT.Red, Modifier.weight(1f)) {
                        val margin = if (s.revenue > 0) s.grossProfit / s.revenue * 100 else 0.0
                        Text("${String.format(Locale.US, "%.0f", margin)}% margin", color = DT.SubText, fontSize = 10.sp)
                    }
                    KpiTile("Net profit", "$cur ${money(s.netProfit)}",
                        if (s.netProfit >= 0) DT.Green else DT.Red, Modifier.weight(1f)) {
                        Text("after $cur ${money(s.expenses)} expenses", color = DT.SubText, fontSize = 10.sp)
                    }
                }
                if (s.costCoverage < 0.9) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DT.Amber.copy(0.1f))
                            .border(1.dp, DT.Amber.copy(0.3f), RoundedCornerShape(12.dp)).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, null, tint = DT.Amber, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Only ${String.format(Locale.US, "%.0f", s.costCoverage * 100)}% of sales have a cost price, so profit is overstated. " +
                            "Add cost prices to your products for accurate profit.",
                            color = DT.Amber, fontSize = 11.sp)
                    }
                }
            }
        }

        // ── Revenue trend ─────────────────────────────────────────────────────
        item {
            AnalyticsCard(if (s.trendBucketDays == 1) "Daily revenue" else "Weekly revenue") {
                val values = s.trend.map { it.revenue.toFloat() }
                val best = values.indices.maxByOrNull { values[it] } ?: 0
                BarChart(values, highlight = best, modifier = Modifier.fillMaxWidth().height(120.dp))
                Spacer(Modifier.height(6.dp))
                val fmt = SimpleDateFormat(if (s.range.days <= 7) "EEE" else "d MMM", Locale.getDefault())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(fmt.format(Date(s.trend.first().startMs)), color = DT.SubText, fontSize = 10.sp)
                    Text(fmt.format(Date(s.trend.last().startMs)), color = DT.SubText, fontSize = 10.sp)
                }
                Spacer(Modifier.height(6.dp))
                val d = SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(s.trend[best].startMs))
                Text("Best ${if (s.trendBucketDays == 1) "day" else "week from"}: $d • $cur ${money(values[best].toDouble())}",
                    color = DT.OnSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // ── Payment mix ───────────────────────────────────────────────────────
        item {
            AnalyticsCard("Payment methods") {
                val total = s.paymentMix.sumOf { it.second }.coerceAtLeast(0.01)
                Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))) {
                    s.paymentMix.forEach { (m, v) ->
                        Box(Modifier.weight((v / total).toFloat().coerceAtLeast(0.001f)).fillMaxHeight().background(methodColor(m)))
                    }
                }
                Spacer(Modifier.height(10.dp))
                s.paymentMix.forEach { (m, v) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(methodColor(m)))
                        Spacer(Modifier.width(8.dp))
                        Text(prettyEnumName(m.name), color = DT.OnSurface, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text("$cur ${money(v)}", color = DT.OnSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("  ${String.format(Locale.US, "%.0f", v / total * 100)}%", color = DT.SubText, fontSize = 12.sp)
                    }
                }
            }
        }

        // ── Peak hours ────────────────────────────────────────────────────────
        item {
            AnalyticsCard("Busiest hours") {
                val values = s.hourly.map { it.toFloat() }
                val peak = values.indices.maxByOrNull { values[it] } ?: 0
                BarChart(values, highlight = peak, modifier = Modifier.fillMaxWidth().height(80.dp))
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("12 AM", "6 AM", "12 PM", "6 PM", "11 PM").forEach {
                        Text(it, color = DT.SubText, fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Peak: ${hourLabel(peak)}–${hourLabel((peak + 1) % 24)} • ${s.hourly[peak]} sale${if (s.hourly[peak] != 1) "s" else ""}",
                    color = DT.OnSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // ── Weekdays ──────────────────────────────────────────────────────────
        item {
            AnalyticsCard("Best days of the week") {
                val values = s.weekday.map { it.toFloat() }
                val best = values.indices.maxByOrNull { values[it] } ?: 0
                BarChart(values, highlight = best, modifier = Modifier.fillMaxWidth().height(80.dp))
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth()) {
                    listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                        Text(it, color = DT.SubText, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(6.dp))
                val names = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
                Text("Strongest: ${names[best]} • $cur ${money(values[best].toDouble())}",
                    color = DT.OnSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // ── Top products ──────────────────────────────────────────────────────
        if (s.topProducts.isNotEmpty()) item {
            AnalyticsCard("Top products by revenue") {
                s.topProducts.forEachIndexed { i, p -> TopProductRow(i + 1, p, cur) }
            }
        }

        // ── Categories ────────────────────────────────────────────────────────
        if (s.categories.isNotEmpty()) item {
            AnalyticsCard("Sales by category") {
                val total = s.categories.sumOf { it.second }.coerceAtLeast(0.01)
                s.categories.forEach { (cat, v) ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(cat, color = DT.OnSurface, fontSize = 13.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text("$cur ${money(v)}  ${String.format(Locale.US, "%.0f", v / total * 100)}%",
                                color = DT.SubText, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(DT.Surface2)) {
                            Box(Modifier.fillMaxWidth((v / total).toFloat().coerceIn(0.02f, 1f)).fillMaxHeight()
                                .clip(RoundedCornerShape(3.dp)).background(Purple))
                        }
                    }
                }
            }
        }
    }
}

private fun hourLabel(h: Int): String {
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "$h12 ${if (h < 12) "AM" else "PM"}"
}

private fun methodColor(m: PaymentMethod): Color = when (m) {
    PaymentMethod.CASH -> DT.Green
    PaymentMethod.MPESA -> DT.TealLight
    PaymentMethod.CARD -> Blue
    PaymentMethod.MIXED -> DT.Amber
    PaymentMethod.CREDIT -> Purple
}

@Composable
private fun KpiTile(label: String, value: String, valueColor: Color, modifier: Modifier, footer: @Composable () -> Unit = {}) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(DT.Surface)
            .border(1.dp, DT.Border, RoundedCornerShape(16.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(label, color = DT.SubText, fontSize = 11.sp)
        Text(value, color = valueColor, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        footer()
    }
}

@Composable
private fun AnalyticsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(DT.Surface)
            .border(1.dp, DT.Border, RoundedCornerShape(18.dp)).padding(14.dp)
    ) {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun TopProductRow(rank: Int, p: ProductSalesStat, cur: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(DT.TealDim), contentAlignment = Alignment.Center) {
            Text(rank.toString(), color = DT.TealLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(p.productName, color = DT.OnSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${plainNumber(p.qty)} sold", color = DT.SubText, fontSize = 11.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("$cur ${money(p.revenue)}", color = DT.OnSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            if (p.costedRevenue > 0)
                Text("+${money(p.revenue - p.cost)} profit", color = DT.Green, fontSize = 10.sp)
        }
    }
}

/** Minimal bar chart: bars scale to the tallest value; [highlight] is drawn in the accent colour. */
@Composable
internal fun BarChart(values: List<Float>, highlight: Int, modifier: Modifier = Modifier,
                      bar: Color = DT.Teal.copy(alpha = 0.55f), accent: Color = DT.TealLight) {
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val max = (values.maxOrNull() ?: 0f).coerceAtLeast(0.0001f)
        val gap = if (values.size > 40) 1.dp.toPx() else 3.dp.toPx()
        val w = ((size.width - gap * (values.size - 1)) / values.size).coerceAtLeast(1f)
        values.forEachIndexed { i, v ->
            val h = (v / max * size.height).let { if (v > 0f) it.coerceAtLeast(3.dp.toPx()) else 2.dp.toPx() }
            drawRoundRect(
                color = if (i == highlight && v > 0f) accent else bar,
                topLeft = Offset(i * (w + gap), size.height - h),
                size = Size(w, h),
                cornerRadius = CornerRadius(minOf(w / 2, 4.dp.toPx()))
            )
        }
    }
}
