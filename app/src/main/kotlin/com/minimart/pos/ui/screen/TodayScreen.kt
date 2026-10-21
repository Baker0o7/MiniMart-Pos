package com.minimart.pos.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minimart.pos.ui.components.BarChart
import com.minimart.pos.ui.theme.DT
import com.minimart.pos.ui.viewmodel.TodayViewModel
import com.minimart.pos.util.TodaySummary
import java.util.Locale

private fun money(currency: String, v: Double) = "$currency ${String.format(Locale.US, "%,.0f", v)}"

private fun hourLabel(h: Int): String = when {
    h == 0 -> "12 AM"
    h < 12 -> "$h AM"
    h == 12 -> "12 PM"
    else -> "${h - 12} PM"
}

/** The owner's view of today: takings, profit, how it was paid, and what needs attention. */
@Composable
fun TodayScreen(onBack: () -> Unit, onOpenHealth: () -> Unit, onOpenShifts: () -> Unit, vm: TodayViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    // Keep the numbers fresh while the screen is open.
    LaunchedEffect(Unit) {
        while (true) { vm.refresh(); kotlinx.coroutines.delay(20_000) }
    }
    Column(Modifier.fillMaxSize().background(DT.Bg)) {
        GradientHeader("Today", s.storeName.ifBlank { "How the shop is doing" }, onBack) {
            HeaderPillButton("Refresh", Icons.Default.Refresh) { vm.refresh() }
        }
        val sum = s.summary
        if (s.loading || sum == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = DT.Teal) }
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Hero(sum, s.currency) }
            if (s.alerts.isNotEmpty()) item { Alerts(s.alerts, onOpenHealth, onOpenShifts) }
            item { Splits(sum, s.currency) }
            item { Hours(sum) }
            item {
                Text("Profit counts only items whose cost is known. Expenses are everything recorded today.",
                    color = DT.SubText, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(DT.Surface)
            .border(1.dp, DT.Border, RoundedCornerShape(18.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), content = content
    )
}

@Composable
private fun Hero(sum: TodaySummary, currency: String) {
    Card {
        Text("Sales so far today", color = DT.SubText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(money(currency, sum.revenue), color = DT.OnSurface, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp)
        val vs = sum.vsLastWeekPercent
        Text(
            when {
                vs == null -> "${sum.count} sale(s) · no sales at this time last week to compare"
                vs >= 0 -> "${sum.count} sale(s) · up $vs% on this time last week"
                else -> "${sum.count} sale(s) · down ${-vs}% on this time last week"
            },
            color = when { vs == null -> DT.SubText; vs >= 0 -> DT.Green; else -> DT.Amber }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("Profit", money(currency, sum.profit), DT.Green, Modifier.weight(1f))
            Stat("Expenses", money(currency, sum.expenses), DT.Amber, Modifier.weight(1f))
            Stat("Net", money(currency, sum.netProfit), if (sum.netProfit >= 0) DT.Green else DT.Red, Modifier.weight(1f))
        }
        if (sum.count > 0) Text("Average basket ${money(currency, sum.averageBasket)}", color = DT.SubText, fontSize = 12.sp)
    }
}

@Composable
private fun Stat(label: String, value: String, tone: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(DT.Bg).padding(10.dp)) {
        Text(label, color = DT.SubText, fontSize = 11.sp)
        Text(value, color = tone, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
private fun Alerts(alerts: List<String>, onOpenHealth: () -> Unit, onOpenShifts: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(DT.Amber.copy(0.12f))
            .border(1.dp, DT.Amber.copy(0.5f), RoundedCornerShape(18.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.WarningAmber, null, tint = DT.Amber, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Needs your attention", color = DT.OnSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        alerts.forEach { Text("• $it", color = DT.OnSurface, fontSize = 13.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onOpenShifts) { Text("Shifts", color = DT.Teal) }
            TextButton(onClick = onOpenHealth) { Text("Store health", color = DT.Teal) }
        }
    }
}

@Composable
private fun Splits(sum: TodaySummary, currency: String) {
    Card {
        Text("How customers paid", color = DT.OnSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        val rows = listOf("Cash" to sum.cash, "M-Pesa" to sum.mpesa, "Card" to sum.card, "On credit" to sum.credit)
        val total = rows.sumOf { it.second }.coerceAtLeast(0.01)
        rows.forEach { (name, v) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = DT.SubText, fontSize = 13.sp, modifier = Modifier.width(80.dp))
                Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(DT.Bg)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth((v / total).toFloat().coerceIn(0f, 1f)).background(DT.Teal))
                }
                Spacer(Modifier.width(10.dp))
                Text(money(currency, v), color = DT.OnSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun Hours(sum: TodaySummary) {
    Card {
        Text("Sales by hour", color = DT.OnSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        val peak = sum.busiestHour
        Text(if (peak == null) "No sales yet today." else "Busiest hour: ${hourLabel(peak)}", color = DT.SubText, fontSize = 12.sp)
        BarChart(
            values = sum.hourly.map { it.toFloat() },
            highlight = peak ?: -1,
            labels = List(24) { hourLabel(it) },
            modifier = Modifier.fillMaxWidth().height(110.dp),
            description = if (peak == null) "No sales yet today" else "Sales by hour. Busiest hour ${hourLabel(peak)}"
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("12 AM", color = DT.SubText, fontSize = 10.sp)
            Text("6 AM", color = DT.SubText, fontSize = 10.sp)
            Text("12 PM", color = DT.SubText, fontSize = 10.sp)
            Text("6 PM", color = DT.SubText, fontSize = 10.sp)
            Text("11 PM", color = DT.SubText, fontSize = 10.sp)
        }
    }
}
