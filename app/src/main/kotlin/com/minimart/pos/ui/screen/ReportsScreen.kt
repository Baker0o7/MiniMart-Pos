package com.minimart.pos.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.minimart.pos.data.entity.PaymentMethod
import com.minimart.pos.ui.theme.DT
import com.minimart.pos.ui.viewmodel.ReportPeriod
import com.minimart.pos.ui.viewmodel.ReportsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(onBack: () -> Unit, vm: ReportsViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsState()
    val period by vm.period.collectAsState()

    // Compute payment method split from real sale data
    val paymentTotals = remember(state.sales) {
        state.sales.groupBy { it.paymentMethod }
            .mapValues { (_, sales) -> sales.sumOf { it.totalAmount } }
    }

    // Build daily/hourly bars from sale timestamps for the revenue chart
    val chartPoints = remember(state.sales, state.period) {
        if (state.sales.isEmpty()) return@remember listOf(0f)
        when (state.period) {
            ReportPeriod.TODAY -> {
                // 6 blocks of 4 hours each (0-3, 4-7, 8-11, 12-15, 16-19, 20-23)
                val buckets = FloatArray(6)
                state.sales.forEach { sale ->
                    val cal = java.util.Calendar.getInstance().also { it.timeInMillis = sale.createdAt }
                    val bucket = cal.get(java.util.Calendar.HOUR_OF_DAY) / 4
                    buckets[bucket.coerceIn(0, 5)] += sale.totalAmount.toFloat()
                }
                buckets.toList()
            }
            ReportPeriod.WEEK -> {
                // 7 day buckets Mon–Sun
                val buckets = FloatArray(7)
                val cal = java.util.Calendar.getInstance()
                state.sales.forEach { sale ->
                    cal.timeInMillis = sale.createdAt
                    val dow = (cal.get(java.util.Calendar.DAY_OF_WEEK) - java.util.Calendar.MONDAY + 7) % 7
                    buckets[dow.coerceIn(0, 6)] += sale.totalAmount.toFloat()
                }
                buckets.toList()
            }
            else -> {
                // ~8 weekly buckets for month/custom
                val earliest = state.sales.minOf { it.createdAt }
                val latest = state.sales.maxOf { it.createdAt }
                val range = (latest - earliest).coerceAtLeast(1L)
                val buckets = FloatArray(8)
                state.sales.forEach { sale ->
                    val bucket = ((sale.createdAt - earliest).toFloat() / range * 7).toInt().coerceIn(0, 7)
                    buckets[bucket] += sale.totalAmount.toFloat()
                }
                buckets.toList()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(DT.Bg)) {
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            // ── Header ──
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = DT.OnSurface) }
                    Spacer(Modifier.width(4.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Reports", color = DT.Teal, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                        Text("Analytics overview", color = DT.SubText, fontSize = 12.sp)
                    }
                    Icon(Icons.Default.BarChart, null, tint = DT.SubText, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                }
            }

            // ── Period chips ──
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReportPeriod.entries.forEach { p ->
                        val sel = period == p
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(20.dp))
                                .background(if (sel) DT.Teal else DT.Surface)
                                .border(1.dp, if (sel) DT.Teal else DT.Border, RoundedCornerShape(20.dp))
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) { vm.setPeriod(p) }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(p.name.lowercase().replaceFirstChar { it.uppercase() },
                                color = if (sel) Color.White else DT.SubText,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            if (state.totalTransactions == 0) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.BarChart, null, modifier = Modifier.size(56.dp), tint = DT.SubText.copy(0.3f))
                            Text("No sales in this period", color = DT.SubText, fontWeight = FontWeight.SemiBold)
                            Text("Reports will appear here once a sale is completed",
                                color = DT.SubText.copy(0.6f), fontSize = 12.sp)
                        }
                    }
                }
            } else {

            // ── Revenue card with real bar chart ──
            item {
                Spacer(Modifier.height(12.dp))
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(20.dp)).background(DT.Surface)
                    .border(1.dp, DT.Border, RoundedCornerShape(20.dp)).padding(20.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(DT.TealDim),
                                contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Receipt, null, tint = DT.Teal, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Revenue", color = DT.OnSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("${state.currency} ${String.format("%,.2f", state.totalRevenue)}",
                                    color = DT.Teal, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${state.totalTransactions}", color = DT.OnSurface,
                                    fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                                Text("sales", color = DT.SubText, fontSize = 11.sp)
                            }
                        }
                        // Real bar chart from sale timestamps
                        RevenueBarChart(
                            points = chartPoints,
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        )
                    }
                }
            }

            // ── Stats row: avg basket + transactions ──
            item {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Avg basket
                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                        .background(DT.Surface).border(1.dp, DT.Border, RoundedCornerShape(18.dp))
                        .padding(16.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ShoppingCart, null, tint = DT.TealLight, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Avg Basket", color = DT.SubText, fontSize = 11.sp)
                            }
                            Text("${state.currency} ${String.format("%,.2f", state.averageBasket)}",
                                color = DT.OnSurface, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                            Text("per transaction", color = DT.SubText, fontSize = 10.sp)
                        }
                    }
                    // Unique days with sales
                    val activeDays = remember(state.sales) {
                        state.sales.map {
                            val c = java.util.Calendar.getInstance().also { c -> c.timeInMillis = it.createdAt }
                            "${c.get(java.util.Calendar.YEAR)}-${c.get(java.util.Calendar.DAY_OF_YEAR)}"
                        }.toSet().size
                    }
                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                        .background(DT.Surface).border(1.dp, DT.Border, RoundedCornerShape(18.dp))
                        .padding(16.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CalendarToday, null, tint = Color(0xFF64B5F6), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Active Days", color = DT.SubText, fontSize = 11.sp)
                            }
                            Text("$activeDays", color = DT.OnSurface, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
                            Text("with sales", color = DT.SubText, fontSize = 10.sp)
                        }
                    }
                }
            }

            // ── Payment method breakdown ──
            if (paymentTotals.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Payments, null, tint = DT.SubText, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Payment Split", color = DT.OnSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(18.dp)).background(DT.Surface)
                        .border(1.dp, DT.Border, RoundedCornerShape(18.dp)).padding(16.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            val methodColors = mapOf(
                                PaymentMethod.CASH   to Color(0xFF4CAF50),
                                PaymentMethod.MPESA  to Color(0xFF00C853),
                                PaymentMethod.CARD   to Color(0xFF64B5F6),
                                PaymentMethod.MIXED  to Color(0xFFFFB74D),
                                PaymentMethod.CREDIT to Color(0xFFEF5350)
                            )
                            val total = paymentTotals.values.sum().coerceAtLeast(0.01)
                            paymentTotals.entries.sortedByDescending { it.value }.forEach { (method, amount) ->
                                val color = methodColors[method] ?: DT.TealLight
                                val pct = (amount / total * 100).toFloat()
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                                        Spacer(Modifier.width(8.dp))
                                        Text(method.name, color = DT.OnSurface, fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                        Text("${state.currency} ${String.format("%,.0f", amount)}",
                                            color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Spacer(Modifier.width(8.dp))
                                        Text("${String.format("%.0f", pct)}%",
                                            color = DT.SubText, fontSize = 11.sp,
                                            modifier = Modifier.width(36.dp))
                                    }
                                    // Progress bar
                                    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                                        .background(DT.Border)) {
                                        Box(Modifier.fillMaxWidth(pct / 100f).height(4.dp)
                                            .clip(RoundedCornerShape(2.dp)).background(color))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Top sellers ──
            if (state.topSellers.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Whatshot, null, tint = Color(0xFFFFB74D), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Top Products", color = DT.OnSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                items(state.topSellers.take(8), key = { it.productId }) { seller ->
                    val rank = state.topSellers.indexOf(seller) + 1
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)
                        .clip(RoundedCornerShape(14.dp)).background(DT.Surface)
                        .border(1.dp, DT.Border, RoundedCornerShape(14.dp)).padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Rank badge
                            Box(modifier = Modifier.size(28.dp).clip(CircleShape)
                                .background(if (rank == 1) Color(0xFFFFB74D).copy(0.2f) else DT.TealDim),
                                contentAlignment = Alignment.Center) {
                                Text("$rank",
                                    color = if (rank == 1) Color(0xFFFFB74D) else DT.TealLight,
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(seller.productName, color = DT.OnSurface, modifier = Modifier.weight(1f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.width(8.dp))
                            Text("×${seller.totalQty}", color = DT.SubText,
                                style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.width(12.dp))
                            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(DT.TealDim)
                                .padding(horizontal = 8.dp, vertical = 3.dp)) {
                                Text("${state.currency} ${String.format("%,.0f", seller.totalRevenue)}",
                                    color = DT.TealLight, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            } // end else (has sales)
        }
    }
}

/** Bar chart built from real sale-timestamp distributions — no hardcoded data. */
@Composable
private fun RevenueBarChart(points: List<Float>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (points.isEmpty()) return@Canvas
        val max = points.maxOrNull()?.coerceAtLeast(0.01f) ?: return@Canvas
        val count = points.size
        val totalW = size.width
        val barW = (totalW / (count * 1.6f)).coerceAtMost(totalW / count * 0.7f)
        val gap   = (totalW - barW * count) / (count + 1).coerceAtLeast(1)
        points.forEachIndexed { i, v ->
            val norm = (v / max).coerceIn(0f, 1f)
            val barH = (norm * size.height * 0.85f).coerceAtLeast(if (v > 0f) 4.dp.toPx() else 0f)
            val x = gap + i * (barW + gap)
            val y = size.height - barH
            // Shadow / glow
            drawLine(
                color = Color(0xFF00897B).copy(alpha = 0.18f),
                start = Offset(x + barW / 2, size.height),
                end = Offset(x + barW / 2, y),
                strokeWidth = barW + 6.dp.toPx(),
                cap = StrokeCap.Round
            )
            // Bar
            drawLine(
                color = if (v > 0f) Color(0xFF00897B) else DT.Border,
                start = Offset(x + barW / 2, size.height),
                end = Offset(x + barW / 2, y),
                strokeWidth = barW,
                cap = StrokeCap.Round
            )
            // Top dot on non-zero bars
            if (v > 0f) {
                drawCircle(Color(0xFF4DB6AC), radius = 3.dp.toPx(), center = Offset(x + barW / 2, y))
            }
        }
    }
}
