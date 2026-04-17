package com.minimart.pos.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.minimart.pos.data.dao.TopSellerResult
import com.minimart.pos.ui.viewmodel.DashboardViewModel
import kotlin.math.max

// ─── Color palette ────────────────────────────────────────────────────────────
private val DarkCard    = Color(0xFF1E2D2C)
private val TealCard    = Color(0xFF2A4A47)
private val TealGlow    = Color(0xFF00897B)
private val AmberCard   = Color(0xFFB8860B).copy(alpha = 0.85f)
private val PurpleCard  = Color(0xFF6B4FA0)
private val RoseCard    = Color(0xFFC2705A)
private val OnDark      = Color(0xFFE8F5E9)
private val SubText     = Color(0xFF9DB8B5)
private val GreenBadge  = Color(0xFF4CAF50)
private val DarkBg      = Color(0xFF0F1E1D)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToScanner:   () -> Unit,
    onNavigateToProducts:  () -> Unit,
    onNavigateToInventory: () -> Unit,
    onNavigateToReports:   () -> Unit,
    onNavigateToExpenses:  () -> Unit,
    onNavigateToSettings:  () -> Unit,
    vm: DashboardViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // ── Top bar ───────────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("🇰🇪", fontSize = 28.sp)
                        Column {
                            Text("Habari!", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = OnDark)
                            Text(state.storeName, color = SubText, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Offline badge
                        Surface(shape = RoundedCornerShape(20.dp), color = DarkCard) {
                            Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Box(modifier = Modifier.size(7.dp).background(GreenBadge, CircleShape))
                                Text("Offline • Ready", color = GreenBadge, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        // Avatar
                        IconButton(onClick = onNavigateToSettings, modifier = Modifier.size(40.dp).background(DarkCard, CircleShape)) {
                            Icon(Icons.Default.Person, null, tint = SubText, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }

            // ── KPI row ───────────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Big Sales card
                    Box(
                        modifier = Modifier.weight(1.6f).height(160.dp).clip(RoundedCornerShape(20.dp))
                            .background(Brush.linearGradient(listOf(TealCard, Color(0xFF1A3530))))
                    ) {
                        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Today's Sales", color = SubText, style = MaterialTheme.typography.labelMedium)
                                Surface(shape = RoundedCornerShape(12.dp), color = GreenBadge.copy(alpha = 0.2f)) {
                                    Text("+${state.todaySaleCount * 8}%", color = GreenBadge, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                }
                            }
                            Text(
                                "${state.currency} ${String.format("%,.0f", state.todayRevenue)}",
                                color = OnDark, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp
                            )
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                                Text("+${state.todaySaleCount * 8}%", color = GreenBadge, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                MiniBarChart(
                                    data = listOf(0.3f, 0.5f, 0.4f, 0.7f, 0.6f, 0.9f, 1.0f),
                                    color = TealGlow.copy(alpha = 0.6f),
                                    modifier = Modifier.width(80.dp).height(36.dp)
                                )
                            }
                        }
                    }

                    // Transactions card
                    Box(
                        modifier = Modifier.weight(1f).height(160.dp).clip(RoundedCornerShape(20.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF1E2A28), TealCard.copy(alpha = 0.6f))))
                    ) {
                        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Diamond, null, tint = TealGlow.copy(0.7f), modifier = Modifier.size(22.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(state.todaySaleCount.toString(), color = OnDark, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("Transactions\nToday", color = SubText, style = MaterialTheme.typography.labelSmall, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }

            // ── Quick Actions ─────────────────────────────────────────────────
            item { Spacer(Modifier.height(20.dp)) }
            item {
                Text("Quick Actions", color = OnDark, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            }
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        BigActionCard(Modifier.weight(1f), "New Sale",     Icons.Default.QrCodeScanner, TealCard,   TealGlow,   onNavigateToScanner)
                        BigActionCard(Modifier.weight(1f), "My Products",  Icons.Default.ShoppingBasket, Color(0xFF3D2E08), Color(0xFFD4A017), onNavigateToInventory)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        BigActionCard(Modifier.weight(1f), "Daily Report", Icons.Default.BarChart,       PurpleCard, Color(0xFFCE93D8), onNavigateToReports)
                        BigActionCard(Modifier.weight(1f), "Add Expense",  Icons.Default.ReceiptLong,    RoseCard.copy(alpha = 0.4f), Color(0xFFEF9A9A),  onNavigateToExpenses)
                    }
                }
            }

            // ── Top Items Today ───────────────────────────────────────────────
            if (state.topSellers.isNotEmpty()) {
                item { Spacer(Modifier.height(20.dp)) }
                item {
                    Text("Top Items Today", color = OnDark, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                }
                items(state.topSellers.take(5)) { seller ->
                    TopItemRow(seller = seller, currency = state.currency)
                }
            }

            // ── Low stock alert ───────────────────────────────────────────────
            if (state.lowStockProducts.isNotEmpty()) {
                item { Spacer(Modifier.height(12.dp)) }
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF3B1A1A)
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = Color(0xFFEF5350), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Low Stock Alert", color = Color(0xFFEF5350), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                Text("${state.lowStockProducts.size} items need restocking", color = SubText, style = MaterialTheme.typography.labelSmall)
                            }
                            TextButton(onClick = onNavigateToInventory) {
                                Text("View", color = TealGlow, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Big action card ──────────────────────────────────────────────────────────

@Composable
private fun BigActionCard(modifier: Modifier, label: String, icon: ImageVector, bg: Color, iconColor: Color, onClick: () -> Unit) {
    Box(
        modifier = modifier.height(130.dp).clip(RoundedCornerShape(20.dp)).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.BottomStart
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Box(modifier = Modifier.size(44.dp).background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(24.dp))
            }
            Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
        }
    }
}

// ─── Top item row ─────────────────────────────────────────────────────────────

@Composable
private fun TopItemRow(seller: TopSellerResult, currency: String) {
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), shape = RoundedCornerShape(14.dp), color = DarkCard) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Placeholder product icon
            Box(modifier = Modifier.size(44.dp).background(TealCard, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Inventory2, null, tint = TealGlow.copy(0.7f), modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(seller.productName, color = OnDark, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$currency ${String.format("%.0f", seller.totalRevenue / seller.totalQty.toDouble().coerceAtLeast(1.0))}", color = SubText, style = MaterialTheme.typography.labelSmall)
            }
            Surface(shape = RoundedCornerShape(20.dp), color = TealGlow.copy(alpha = 0.2f)) {
                Text("×${seller.totalQty}", color = TealGlow, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
            }
        }
    }
}

// ─── Mini bar chart ───────────────────────────────────────────────────────────

@Composable
private fun MiniBarChart(data: List<Float>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val barW = size.width / (data.size * 2f - 1)
        data.forEachIndexed { i, v ->
            val h = v * size.height
            val x = i * barW * 2f
            drawRoundRect(
                color = color,
                topLeft = Offset(x, size.height - h),
                size = androidx.compose.ui.geometry.Size(barW, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
            )
        }
    }
}
