package com.minimart.pos.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.minimart.pos.ui.theme.DT
import com.minimart.pos.ui.viewmodel.ReportPeriod
import com.minimart.pos.ui.viewmodel.ReportsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(onBack: () -> Unit, vm: ReportsViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsState()
    val period by vm.period.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(DT.Bg)) {
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {

            // ── Header ──
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = DT.OnSurface) }
                    Text("Reports", color = DT.Teal, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
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
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                ) { vm.setPeriod(p) }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(p.name.lowercase().replaceFirstChar { it.uppercase() },
                                color = if (sel) Color.White else DT.SubText, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // ── Revenue card with bar chart ──
            item {
                Spacer(Modifier.height(12.dp))
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(20.dp)).background(DT.Surface)
                    .border(1.dp, DT.Border, RoundedCornerShape(20.dp)).padding(20.dp)) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(DT.TealDim),
                                contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Receipt, null, tint = DT.Teal, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Revenue", color = DT.OnSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text("${state.currency} ${String.format("%.2f", state.totalRevenue)}",
                                    color = DT.Teal, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            }
                            // Mini bar chart
                            MiniGlowChart(modifier = Modifier.width(100.dp).height(48.dp))
                        }
                    }
                }
            }

            // ── Transactions card ──
            item {
                Spacer(Modifier.height(8.dp))
                DarkStatCard(
                    label = "Transactions",
                    icon = Icons.Default.ShoppingCart,
                    leftLabel = state.currency,
                    leftValue = String.format("%.2f", state.totalRevenue),
                    rightLabel = "Count",
                    rightValue = state.totalTransactions.toString()
                )
            }

            // ── Avg basket card ──
            item {
                Spacer(Modifier.height(8.dp))
                DarkStatCard(
                    label = "Avg Basket",
                    icon = Icons.Default.ShoppingCart,
                    leftLabel = state.currency,
                    leftValue = String.format("%.2f", state.averageBasket),
                    rightLabel = "",
                    rightValue = state.totalTransactions.toString()
                )
            }

            // ── Top sellers ──
            if (state.topSellers.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Text("Top Selling Products", color = DT.OnSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                        modifier = Modifier.padding(horizontal = 20.dp))
                    Spacer(Modifier.height(8.dp))
                }
                items(state.topSellers.take(8), key = { it.productId }) { seller ->
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)
                        .clip(RoundedCornerShape(12.dp)).background(DT.Surface).border(1.dp, DT.Border, RoundedCornerShape(12.dp)).padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(seller.productName, color = DT.OnSurface, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text("${seller.totalQty} pcs", color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.width(12.dp))
                            Text("${state.currency} ${String.format("%.0f", seller.totalRevenue)}", color = DT.Teal, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DarkStatCard(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    leftLabel: String, leftValue: String, rightLabel: String, rightValue: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        .clip(RoundedCornerShape(20.dp)).background(DT.Surface).border(1.dp, DT.Border, RoundedCornerShape(20.dp)).padding(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = DT.Teal, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(label, color = DT.OnSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
                Text(rightValue, color = DT.OnSurface, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("$leftLabel $leftValue", color = DT.SubText, style = MaterialTheme.typography.bodySmall)
                if (rightLabel.isNotEmpty()) Text("$rightLabel $rightValue", color = DT.SubText, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun MiniGlowChart(modifier: Modifier = Modifier) {
    val points = listOf(0.3f, 0.5f, 0.4f, 0.8f, 0.6f, 0.9f, 1.0f)
    Canvas(modifier = modifier) {
        val barW = size.width / (points.size * 1.8f)
        points.forEachIndexed { i, v ->
            val h = v * size.height * 0.8f
            val x = i * (size.width / points.size)
            val y = size.height - h
            drawLine(color = Color(0xFF00897B).copy(alpha = 0.4f), start = Offset(x + barW/2, size.height), end = Offset(x + barW/2, y), strokeWidth = barW)
            drawCircle(color = Color(0xFF4DB6AC), radius = 4.dp.toPx(), center = Offset(x + barW/2, y))
        }
    }
}
