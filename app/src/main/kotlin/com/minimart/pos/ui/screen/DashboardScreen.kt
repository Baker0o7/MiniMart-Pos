package com.minimart.pos.ui.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.minimart.pos.data.entity.UserRole
import com.minimart.pos.data.repository.SettingsRepository
import com.minimart.pos.ui.theme.DT
import com.minimart.pos.ui.viewmodel.DashboardViewModel
import kotlinx.coroutines.launch

// ─── Design tokens ─────────────────────────────────────────────────────────────
private val Bg          = Color(0xFF060C0B)
private val TealGlow    = Color(0xFF00C9A7)
private val GreenGlow   = Color(0xFF4CAF50)
private val PurpleGlow  = Color(0xFFB39DDB)
private val BlueGlow    = Color(0xFF64B5F6)
private val AmberGlow   = Color(0xFFFFB74D)
private val RedGlow     = Color(0xFFEF5350)
private val White       = Color.White
private val Sub         = Color(0xFF7A9E9B)

private data class DashCard_(
    val id: String, val title: String, val sub: String,
    val icon: ImageVector, val bg: Color, val glow: Color, val action: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToScanner:      () -> Unit,
    onNavigateToProducts:     () -> Unit,
    onNavigateToInventory:    () -> Unit,
    onNavigateToReports:      () -> Unit,
    onNavigateToExpenses:     () -> Unit,
    onNavigateToSettings:     () -> Unit,
    onNavigateToSalesHistory: () -> Unit = {},
    onNavigateToLowStock:     () -> Unit = {},
    currentRole: UserRole? = null,
    settingsRepo: SettingsRepository? = null,
    vm: DashboardViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsState()
    val rm    = com.minimart.pos.util.RoleManager
    val hiddenActions by (settingsRepo?.hiddenActions
        ?: kotlinx.coroutines.flow.flowOf("")).collectAsState("")
    var showManageActions by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) { kotlinx.coroutines.delay(1000); vm.refresh(); isRefreshing = false }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing, onRefresh = { isRefreshing = true },
        modifier = Modifier.fillMaxSize().background(Bg)
    ) {
        LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) {

            // ── Greeting header ───────────────────────────────────────────────
            item {
                Box(modifier = Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xFF0D2420), Bg)))
                    .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Avatar / greeting
                        Box(modifier = Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))
                            .background(Brush.linearGradient(listOf(DT.Teal, Color(0xFF004D40)))),
                            contentAlignment = Alignment.Center) {
                            Text("🇰🇪", fontSize = 22.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Habari! 👋", color = White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                            Text(state.storeName, color = Sub, fontSize = 12.sp)
                        }
                        // Status pill
                        Row(modifier = Modifier.clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF0D2420))
                            .border(1.dp, Color(0xFF1A4038), RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(TealGlow))
                            Text(" Offline ", color = TealGlow, fontSize = 10.sp)
                            Box(Modifier.size(6.dp).clip(CircleShape).background(GreenGlow))
                            Text(" Ready", color = GreenGlow, fontSize = 10.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        // Settings button
                        Box(modifier = Modifier.size(40.dp).clip(CircleShape)
                            .background(Color(0xFF0D2420))
                            .border(1.dp, Color(0xFF1A4038), CircleShape)
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onNavigateToSettings() },
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Settings, null, tint = Sub, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // ── Stats row ─────────────────────────────────────────────────────
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Revenue card
                    Box(modifier = Modifier.weight(1.5f).clip(RoundedCornerShape(22.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFF0E2E28), Color(0xFF071815))))
                        .border(1.dp, TealGlow.copy(0.18f), RoundedCornerShape(22.dp))
                        .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp)) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Today's Sales", color = Sub, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Box(Modifier.clip(RoundedCornerShape(8.dp))
                                    .background(GreenGlow.copy(0.18f))
                                    .padding(horizontal = 7.dp, vertical = 3.dp)) {
                                    Text("+${if (state.todayRevenue > 0) "8" else "0"}%",
                                        color = GreenGlow, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("KES ${String.format("%,.0f", state.todayRevenue)}",
                                color = White, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                            Text("vs yesterday", color = Sub, fontSize = 10.sp)
                            Spacer(Modifier.height(10.dp))
                            MiniLineChart(listOf(0.2f, 0.4f, 0.3f, 0.6f, 0.5f, 0.8f, 0.7f, 1f),
                                TealGlow, Modifier.fillMaxWidth().height(44.dp))
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.TrendingUp, null, tint = GreenGlow, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("+${if (state.todayRevenue > 0) "8" else "0"}% from yesterday",
                                    color = GreenGlow, fontSize = 10.sp)
                            }
                        }
                    }
                    // Right column: transactions + top product
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Transactions card
                        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                            .background(Brush.verticalGradient(listOf(Color(0xFF12121F), Color(0xFF0A0A18))))
                            .border(1.dp, PurpleGlow.copy(0.18f), RoundedCornerShape(18.dp))
                            .padding(14.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                Box(Modifier.size(38.dp).clip(CircleShape)
                                    .background(PurpleGlow.copy(0.12f)),
                                    contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.ShoppingBag, null, tint = PurpleGlow, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(state.todaySaleCount.toString(), color = White,
                                    fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
                                Text("Sales", color = Sub, fontSize = 10.sp)
                            }
                        }
                        // Avg basket card
                        val avgBasket = if (state.todaySaleCount > 0) state.todayRevenue / state.todaySaleCount else 0.0
                        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                            .background(Brush.verticalGradient(listOf(Color(0xFF0F1A0A), Color(0xFF080E05))))
                            .border(1.dp, GreenGlow.copy(0.18f), RoundedCornerShape(18.dp))
                            .padding(14.dp)) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ShoppingCart, null, tint = GreenGlow, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Avg Basket", color = Sub, fontSize = 10.sp)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text("KES ${String.format("%.0f", avgBasket)}",
                                    color = White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }

            // ── Quick Actions ─────────────────────────────────────────────────
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Quick Actions", color = White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        Text("Tap to navigate", color = Sub, fontSize = 11.sp)
                    }
                    Box(modifier = Modifier.clip(RoundedCornerShape(10.dp))
                        .background(if (showManageActions) DT.Teal else DT.Surface)
                        .border(1.dp, if (showManageActions) DT.Teal else DT.Border, RoundedCornerShape(10.dp))
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { showManageActions = !showManageActions }
                        .padding(horizontal = 12.dp, vertical = 7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (showManageActions) Icons.Default.Check else Icons.Default.Tune,
                                null, tint = if (showManageActions) Color.White else DT.Teal, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (showManageActions) "Done" else "Edit",
                                color = if (showManageActions) Color.White else DT.Teal,
                                fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }

            // ── Cards grid ────────────────────────────────────────────────────
            item {
                val hidden = hiddenActions.split(",").filter { it.isNotBlank() }.toSet()
                val scope  = rememberCoroutineScope()
                fun hide(id: String) { scope.launch { settingsRepo?.setHiddenActions((hidden + id).joinToString(",")) } }

                val cards = buildList {
                    if ("sale"     !in hidden) add(DashCard_("sale",    "New Sale",     "Scan & sell",        Icons.Default.QrCode,    Color(0xFF0B2822), TealGlow,   onNavigateToScanner))
                    if ("products" !in hidden) add(DashCard_("products","Products",     "Manage items",       Icons.Default.Inventory2,Color(0xFF0B1C0A), GreenGlow,  onNavigateToProducts))
                    if (rm.canViewReports(currentRole) && "reports" !in hidden)
                        add(DashCard_("reports","Reports","Analytics",Icons.Default.BarChart,Color(0xFF160B2C),PurpleGlow,onNavigateToReports))
                    if (rm.canViewExpenses(currentRole) && "expenses" !in hidden)
                        add(DashCard_("expenses","Expenses","Track costs",Icons.Default.Receipt,Color(0xFF1E1005),AmberGlow,onNavigateToExpenses))
                    if ("history"  !in hidden) add(DashCard_("history",  "Sales History","Past sales",        Icons.Default.History,   Color(0xFF081525), BlueGlow,   onNavigateToSalesHistory))
                    if ("lowstock" !in hidden) add(DashCard_("lowstock", "Low Stock",    "Items running low", Icons.Default.Warning,   Color(0xFF1E0808), RedGlow,    onNavigateToLowStock))
                }

                Column(Modifier.padding(horizontal = 12.dp).animateContentSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    cards.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { c ->
                                Box(Modifier.weight(1f)) {
                                    DashActionCard(c.title, c.sub, c.icon, c.bg, c.glow, c.action)
                                    if (showManageActions) DashBadge { hide(c.id) }
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    val hidden2 = hiddenActions.split(",").filter { it.isNotBlank() }.toSet()
                    if (hidden2.isNotEmpty()) {
                        val restoreScope = rememberCoroutineScope()
                        TextButton(onClick = { restoreScope.launch { settingsRepo?.setHiddenActions("") } },
                            modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Restore, null, tint = DT.Teal, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Restore hidden cards", color = DT.Teal, fontSize = 12.sp)
                        }
                    }
                }
            }

            // ── Alert banners ─────────────────────────────────────────────────
            if (state.lowStockProducts.isNotEmpty()) {
                item { Spacer(Modifier.height(8.dp)) }
                item { DashAlert(Icons.Default.Warning, RedGlow, Color(0xFF1E0808),
                    "${state.lowStockProducts.size} items low on stock", "Needs restocking", onNavigateToLowStock) }
            }
            if (state.expiredProducts.isNotEmpty() || state.expiringProducts.isNotEmpty()) {
                item { Spacer(Modifier.height(6.dp)) }
                item { DashAlert(
                    Icons.Default.CalendarToday,
                    if (state.expiredProducts.isNotEmpty()) RedGlow else AmberGlow,
                    if (state.expiredProducts.isNotEmpty()) Color(0xFF1E0808) else Color(0xFF1E1005),
                    "${state.expiredProducts.size + state.expiringProducts.size} expiry alert(s)",
                    if (state.expiredProducts.isNotEmpty()) "Check expired items" else "Expiring soon",
                    onNavigateToInventory) }
            }

            // ── Top sellers ───────────────────────────────────────────────────
            if (state.topSellers.isNotEmpty()) {
                item { Spacer(Modifier.height(20.dp)) }
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Whatshot, null, tint = AmberGlow, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Top Items Today", color = White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
                item {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Brush.verticalGradient(listOf(DT.Surface, DT.Surface2)))
                        .border(1.dp, DT.Border, RoundedCornerShape(18.dp))) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                            state.topSellers.take(5).forEachIndexed { i, s ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    // Rank
                                    Box(modifier = Modifier.size(26.dp).clip(CircleShape)
                                        .background(if (i == 0) AmberGlow.copy(0.2f) else DT.Bg),
                                        contentAlignment = Alignment.Center) {
                                        Text("${i + 1}", color = if (i == 0) AmberGlow else Sub,
                                            fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
                                        .background(TealGlow.copy(0.1f)), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Inventory2, null, tint = TealGlow, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(s.productName, color = White, modifier = Modifier.weight(1f),
                                        maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                                    Box(Modifier.clip(RoundedCornerShape(8.dp))
                                        .background(TealGlow.copy(0.12f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)) {
                                        Text("×${s.totalQty}", color = TealGlow,
                                            fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                                if (i < state.topSellers.take(5).size - 1)
                                    HorizontalDivider(color = DT.Border.copy(0.5f), thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashActionCard(title: String, sub: String, icon: ImageVector,
    bg: Color, glow: Color, onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()
        .aspectRatio(0.8f)
        .clip(RoundedCornerShape(20.dp))
        .background(Brush.verticalGradient(listOf(bg, Bg)))
        .border(1.dp, glow.copy(0.2f), RoundedCornerShape(20.dp))
        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
        .padding(12.dp)) {
        Column(Modifier.fillMaxSize()) {
            // Icon with glow bg
            Box(modifier = Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
                .background(glow.copy(0.15f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = glow, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(title, color = White, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(sub, color = Sub, fontSize = 10.sp, lineHeight = 13.sp)
            Spacer(Modifier.height(8.dp))
            // Arrow chip
            Box(modifier = Modifier.size(22.dp).clip(CircleShape)
                .background(glow.copy(0.15f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.ChevronRight, null, tint = glow, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun DashAlert(icon: ImageVector, glow: Color, bg: Color,
    title: String, sub: String, action: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)
        .clip(RoundedCornerShape(16.dp)).background(bg)
        .border(1.dp, glow.copy(0.25f), RoundedCornerShape(16.dp)).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(glow.copy(0.15f)),
                contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = glow, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(sub, color = Sub, fontSize = 11.sp)
            }
            Box(modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(glow.copy(0.15f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = action)
                .padding(horizontal = 12.dp, vertical = 7.dp)) {
                Text("View", color = glow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun BoxScope.DashBadge(onRemove: () -> Unit) {
    Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp).clip(CircleShape)
        .background(RedGlow).clickable(onClick = onRemove), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.Close, null, tint = White, modifier = Modifier.size(12.dp))
    }
}

@Composable
private fun MiniLineChart(data: List<Float>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (data.size < 2) return@Canvas
        val w = size.width; val h = size.height; val max = data.maxOrNull() ?: 1f
        val pts = data.mapIndexed { i, v -> Offset(i * w / (data.size - 1), h - (v / max) * h * 0.85f) }
        val fill = Path().apply { moveTo(pts.first().x, h); pts.forEach { lineTo(it.x, it.y) }; lineTo(pts.last().x, h); close() }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(0.4f), Color.Transparent)))
        val line = Path().apply { moveTo(pts.first().x, pts.first().y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
        drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(color, 4.dp.toPx(), pts.last())
        drawCircle(Color.White, 2.dp.toPx(), pts.last())
    }
}
