package com.minimart.pos.ui.screen

import com.minimart.pos.ui.components.Text
import com.minimart.pos.util.HourLabel
import com.minimart.pos.util.ArabicCount
import com.minimart.pos.util.Lang
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.minimart.pos.ui.components.BarChart
import com.minimart.pos.ui.theme.DT
import com.minimart.pos.ui.viewmodel.AnalyticsRange
import com.minimart.pos.ui.viewmodel.AnalyticsState
import com.minimart.pos.ui.viewmodel.AnalyticsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Blue: Color get() = DT.SoftBlue
private val Purple: Color get() = DT.SoftPurple

private fun money(v: Double) = String.format(Locale.US, "%,.0f", v)

@Composable
fun AnalyticsScreen(onBack: () -> Unit, vm: AnalyticsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val range by vm.range.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(DT.Bg)) {
        GradientHeader(title = tr(com.minimart.pos.R.string.analytics), subtitle = tr(com.minimart.pos.R.string.analytics_sub), onBack = onBack)

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
                        .clickable(role = androidx.compose.ui.semantics.Role.Button, indication = null, interactionSource = remember { MutableInteractionSource() }) { vm.setRange(r) }
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
                    Text(Lang.t("No sales in the last ${s.range.label}", "لا مبيعات خلال ${s.range.label}"), color = DT.OnSurface, fontWeight = FontWeight.SemiBold)
                    Text(tr(com.minimart.pos.R.string.trends_empty), color = DT.SubText,
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
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiTile("Revenue", "$cur ${money(s.revenue)}", DT.TealLight, Modifier.weight(1f).fillMaxHeight()) {
                        s.revenueChangePct?.let { pct ->
                            val up = pct >= 0
                            Text("${if (up) "▲" else "▼"} ${String.format(Locale.US, "%.0f", kotlin.math.abs(pct))}% vs previous ${s.range.days} days",
                                color = if (up) DT.Green else DT.Red, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    KpiTile("Sales", s.transactions.toString(), DT.OnSurface, Modifier.weight(1f).fillMaxHeight()) {
                        Text(tr(com.minimart.pos.R.string.an_avg_basket, cur, money(s.avgBasket)), color = DT.SubText, fontSize = 11.sp)
                    }
                }
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiTile(Lang.t("Gross profit", "إجمالي الربح"), "$cur ${money(s.grossProfit)}",
                        if (s.grossProfit >= 0) DT.Green else DT.Red, Modifier.weight(1f).fillMaxHeight()) {
                        val margin = if (s.revenue > 0) s.grossProfit / s.revenue * 100 else 0.0
                        Text("${String.format(Locale.US, "%.0f", margin)}% margin", color = DT.SubText, fontSize = 11.sp)
                    }
                    KpiTile(Lang.t("Net profit", "صافي الربح"), "$cur ${money(s.netProfit)}",
                        if (s.netProfit >= 0) DT.Green else DT.Red, Modifier.weight(1f).fillMaxHeight()) {
                        Text(tr(com.minimart.pos.R.string.an_after_exp, cur, money(s.expenses)), color = DT.SubText, fontSize = 11.sp)
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
                        Text(tr(com.minimart.pos.R.string.an_cov_profit, String.format(Locale.US, "%.0f", s.costCoverage * 100)),
                            color = DT.Amber, fontSize = 11.sp)
                    }
                }
            }
        }

        // ── Revenue trend ─────────────────────────────────────────────────────
        item {
            AnalyticsCard(if (s.trendBucketDays == 1) Lang.t("Daily revenue", "الإيراد اليومي") else Lang.t("Weekly revenue", "الإيراد الأسبوعي")) {
                val values = s.trend.map { it.revenue.toFloat() }
                val best = values.indices.maxByOrNull { values[it] } ?: 0
                val fmt = SimpleDateFormat(if (s.range.days <= 7) "EEE" else "d MMM", androidx.compose.ui.text.intl.Locale.current.platformLocale)
                BarChart(values, highlight = best, modifier = Modifier.fillMaxWidth().height(120.dp),
                    labels = s.trend.map { fmt.format(Date(it.startMs)) },
                    valueLabel = { v -> String.format(Locale.US, "%,.0f", v) })
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(fmt.format(Date(s.trend.first().startMs)), color = DT.SubText, fontSize = 11.sp)
                    Text(fmt.format(Date(s.trend.last().startMs)), color = DT.SubText, fontSize = 11.sp)
                }
                Spacer(Modifier.height(6.dp))
                val d = SimpleDateFormat("EEE d MMM", androidx.compose.ui.text.intl.Locale.current.platformLocale).format(Date(s.trend[best].startMs))
                Text((if (s.trendBucketDays == 1) Lang.t("Best day: ", "أفضل يوم: ") else Lang.t("Best week from: ", "أفضل أسبوع يبدأ: ")) + "$d • $cur ${money(values[best].toDouble())}",
                    color = DT.OnSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // ── Payment mix ───────────────────────────────────────────────────────
        item {
            AnalyticsCard(Lang.t("Payment methods", "طرق الدفع")) {
                val total = s.paymentMix.sumOf { it.second }.coerceAtLeast(0.01)
                Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))) {
                    s.paymentMix.forEach { (m, v) ->
                        Box(Modifier.weight((v / total).toFloat().coerceAtLeast(0.001f)).fillMaxHeight().background(com.minimart.pos.ui.theme.paymentColor(m)))
                    }
                }
                Spacer(Modifier.height(10.dp))
                s.paymentMix.forEach { (m, v) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(com.minimart.pos.ui.theme.paymentColor(m)))
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
            AnalyticsCard(Lang.t("Busiest hours", "أكثر الساعات ازدحامًا")) {
                val values = s.hourly.map { it.toFloat() }
                val peak = values.indices.maxByOrNull { values[it] } ?: 0
                BarChart(values, highlight = peak, modifier = Modifier.fillMaxWidth().height(80.dp),
                    labels = values.indices.map { hourLabel(it) },
                    valueLabel = { v -> ArabicCount.t(v.toInt(), "sale", "sales", "لا مبيعات", "عملية بيع واحدة", "عمليتا بيع", "عمليات بيع", "عملية بيع") })
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf(0, 6, 12, 18, 23).map { hourLabel(it) }.forEach {
                        Text(it, color = DT.SubText, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(Lang.t("Peak: ", "الذروة: ") + "${hourLabel(peak)}–${hourLabel((peak + 1) % 24)} • " + ArabicCount.t(s.hourly[peak], "sale", "sales", "لا مبيعات", "عملية بيع واحدة", "عمليتا بيع", "عمليات بيع", "عملية بيع"),
                    color = DT.OnSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // ── Weekdays ──────────────────────────────────────────────────────────
        item {
            AnalyticsCard(Lang.t("Best days of the week", "أفضل أيام الأسبوع")) {
                val values = s.weekday.map { it.toFloat() }
                val best = values.indices.maxByOrNull { values[it] } ?: 0
                BarChart(values, highlight = best, modifier = Modifier.fillMaxWidth().height(80.dp),
                    labels = weekdayNames(),
                    valueLabel = { v -> "$cur ${money(v.toDouble())}" })
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth()) {
                    (if (Lang.arabic) listOf("ن", "ث", "ر", "خ", "ج", "س", "ح") else listOf("M", "T", "W", "T", "F", "S", "S")).forEach {
                        Text(it, color = DT.SubText, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(6.dp))
                val names = weekdayNames()
                Text(Lang.t("Strongest: ", "الأقوى: ") + "${names[best]} • $cur ${money(values[best].toDouble())}",
                    color = DT.OnSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // ── Top products ──────────────────────────────────────────────────────
        if (s.topProducts.isNotEmpty()) item {
            AnalyticsCard(Lang.t("Top products by revenue", "أفضل المنتجات إيرادًا")) {
                s.topProducts.forEachIndexed { i, p -> TopProductRow(i + 1, p, cur) }
            }
        }

        // ── Categories ────────────────────────────────────────────────────────
        if (s.categories.isNotEmpty()) item {
            AnalyticsCard(Lang.t("Sales by category", "المبيعات حسب الفئة")) {
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

        // ── Business intelligence ─────────────────────────────────────────────
        s.abc?.let { abc -> item {
            AnalyticsCard(Lang.t("Where your sales come from", "من أين تأتي مبيعاتك")) {
                Text("${abc.aCount} of your ${abc.total} selling products make ${String.format(Locale.US, "%.0f", abc.aShare * 100)}% of sales.",
                    color = DT.OnSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                listOf(
                    Triple("A  Core sellers", abc.aShare, DT.Green),
                    Triple("B  Steady sellers", abc.bShare, DT.Teal),
                    Triple("C  Slow tail (${abc.cCount} products)", abc.cShare, DT.SubText)
                ).forEach { (label, share, color) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, color = DT.OnSurface, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text("${String.format(Locale.US, "%.0f", share * 100)}%", color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(DT.Surface2)) {
                        Box(Modifier.fillMaxWidth(share.toFloat().coerceIn(0.02f, 1f)).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(color))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(Lang.t("Never run out of: ", "لا تدعها تنفد: ") + "${abc.aProducts.take(5).joinToString()}${if (abc.aProducts.size > 5) " …" else ""}",
                    color = DT.SubText, fontSize = 11.sp)
            }
        } }

        s.margins?.takeIf { it.best.isNotEmpty() || it.lossMakers.isNotEmpty() || it.thin.isNotEmpty() }?.let { m -> item {
            AnalyticsCard(Lang.t("Profit by product", "الربح حسب المنتج")) {
                if (m.lossMakers.isNotEmpty()) {
                    Text(tr(com.minimart.pos.R.string.below_cost), color = DT.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    m.lossMakers.take(5).forEach { p ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(p.productLabel(), color = DT.OnSurface, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text("$cur ${money(p.profit)}", color = DT.Red, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (m.thin.isNotEmpty()) {
                    Text(tr(com.minimart.pos.R.string.thin_margin), color = DT.Amber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    m.thin.take(5).forEach { p ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(p.productLabel(), color = DT.OnSurface, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text("${String.format(Locale.US, "%.1f", (p.margin ?: 0.0) * 100)}%", color = DT.Amber, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (m.best.isNotEmpty()) {
                    Text(tr(com.minimart.pos.R.string.profit_earners), color = DT.Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    m.best.forEach { p ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(p.productLabel(), color = DT.OnSurface, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text("+$cur ${money(p.profit)}  (${String.format(Locale.US, "%.0f", (p.margin ?: 0.0) * 100)}%)", color = DT.Green, fontSize = 12.sp)
                        }
                    }
                }
                if (s.costCoverage < 0.9) {
                    Spacer(Modifier.height(8.dp))
                    Text(tr(com.minimart.pos.R.string.an_cov_list, String.format(Locale.US, "%.0f", s.costCoverage * 100)),
                        color = DT.SubText, fontSize = 11.sp)
                }
            }
        } }


        // ── Basket, staff and leakage ─────────────────────────────────────────
        if (s.basket != null || s.pairs.isNotEmpty()) item {
            AnalyticsCard(Lang.t("What goes in a basket", "ما يوضع في السلة")) {
                s.basket?.let { b ->
                    Text("${String.format(Locale.US, "%.1f", b.avgItemsPerBasket)} items per sale", color = DT.OnSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("${String.format(Locale.US, "%.0f", b.multiItemShare * 100)}% of ${b.baskets} sales had two or more different products.", color = DT.SubText, fontSize = 11.sp)
                }
                if (s.pairs.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(tr(com.minimart.pos.R.string.bought_together), color = DT.SubText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    s.pairs.forEach { p ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${p.first} + ${p.second}", color = DT.OnSurface, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 2)
                            Text("${p.times}×", color = DT.SubText, fontSize = 12.sp)
                        }
                    }
                    Text(tr(com.minimart.pos.R.string.side_by_side), color = DT.SubText, fontSize = 11.sp)
                }
            }
        }
        if (s.staff.size > 1) item {
            AnalyticsCard(Lang.t("Staff and reversals", "الموظفون والمرتجعات")) {
                s.staff.forEach { st ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(st.row.name, color = DT.OnSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("${st.row.sales} sales • $cur ${money(st.row.revenue)}", color = DT.SubText, fontSize = 11.sp)
                        }
                        Text("${st.row.voids + st.row.refunds} taken back (${String.format(Locale.US, "%.0f", st.reversalRate * 100)}%)",
                            color = if (st.flagged) DT.Amber else DT.SubText, fontSize = 12.sp,
                            fontWeight = if (st.flagged) FontWeight.Bold else FontWeight.Normal)
                    }
                }
                if (s.staff.any { it.flagged }) {
                    Spacer(Modifier.height(4.dp))
                    Text(tr(com.minimart.pos.R.string.an_amber),
                        color = DT.SubText, fontSize = 11.sp)
                }
            }
        }
        s.leakage?.let { l -> item {
            AnalyticsCard(Lang.t("Where money leaks", "أين تضيع الأموال")) {
                if (l.discounts > 0.0) Text(tr(com.minimart.pos.R.string.an_discounts, cur, money(l.discounts), String.format(Locale.US, "%.1f", l.discountShare * 100)),
                    color = DT.OnSurface, fontSize = 13.sp)
                if (l.reversedCount > 0) Text(tr(com.minimart.pos.R.string.an_takenback, l.reversedCount, cur, money(l.reversedValue), String.format(Locale.US, "%.1f", l.reversalRate * 100)),
                    color = DT.OnSurface, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text(tr(com.minimart.pos.R.string.discounts_normal), color = DT.SubText, fontSize = 11.sp)
            }
        } }

        s.typicalWeek?.let { week -> item {
            AnalyticsCard(Lang.t("Your typical week", "أسبوعك المعتاد")) {
                val total = week.sum()
                Text(tr(com.minimart.pos.R.string.an_week, cur, money(total)), color = DT.OnSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(tr(com.minimart.pos.R.string.an_based, s.range.days), color = DT.SubText, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                BarChart(week.map { it.toFloat() }, highlight = week.indices.maxByOrNull { week[it] } ?: -1,
                    modifier = Modifier.fillMaxWidth().height(70.dp),
                    labels = weekdayNames(),
                    valueLabel = { v -> "$cur ${money(v.toDouble())}" })
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth()) {
                    (if (Lang.arabic) listOf("ن", "ث", "ر", "خ", "ج", "س", "ح") else listOf("M", "T", "W", "T", "F", "S", "S")).forEach {
                        Text(it, color = DT.SubText, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    }
                }
            }
        } }
    }
}

private fun hourLabel(h: Int): String = HourLabel.of(h)

private fun weekdayNames(): List<String> = if (Lang.arabic) listOf("الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد") else listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")


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
        Text(title, color = DT.Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                Text("+${money(p.revenue - p.cost)} profit", color = DT.Green, fontSize = 11.sp)
        }
    }
}

private fun com.minimart.pos.util.ProductPerformance.productLabel() = name
