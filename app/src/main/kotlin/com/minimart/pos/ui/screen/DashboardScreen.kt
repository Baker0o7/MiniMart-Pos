package com.minimart.pos.ui.screen

import androidx.compose.animation.animateContentSize
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

private val Bg         = Color(0xFF080E0D)
private val TealGlow   = Color(0xFF00C9A7)
private val GreenGlow  = Color(0xFF4CAF50)
private val PurpleGlow = Color(0xFFB39DDB)
private val BlueGlow   = Color(0xFF64B5F6)
private val AmberGlow  = Color(0xFFFFB74D)
private val RedGlow    = Color(0xFFEF5350)
private val White      = Color.White
private val Sub        = Color(0xFF7A9E9B)

private data class DashCard_(val id: String, val title: String, val sub: String, val icon: ImageVector, val bg: Color, val glow: Color, val action: () -> Unit)

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
        LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {

            // ── Header ────────────────────────────────────────────────────────
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("🇰🇪", fontSize = 26.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Habari! ", color = White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                            Text("👋", fontSize = 20.sp)
                        }
                        Text(state.storeName, color = Sub, fontSize = 12.sp)
                    }
                    // Status pill
                    Box(modifier = Modifier.clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF0F1F1C)).border(1.dp, Color(0xFF1A3530), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(TealGlow))
                            Text(" Offline  ", color = TealGlow, fontSize = 11.sp)
                            Box(Modifier.size(6.dp).clip(CircleShape).background(GreenGlow))
                            Text(" Ready", color = GreenGlow, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(modifier = Modifier.size(40.dp).clip(CircleShape)
                        .background(Color(0xFF0F1F1C)).border(1.dp, Color(0xFF1A3530), CircleShape)
                        .clickable { onNavigateToSettings() }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Person, null, tint = Sub, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // ── Stats ─────────────────────────────────────────────────────────
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Sales card
                    Box(modifier = Modifier.weight(1.4f).clip(RoundedCornerShape(20.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFF0E2C26), Color(0xFF071512))))
                        .border(1.dp, TealGlow.copy(0.2f), RoundedCornerShape(20.dp)).padding(16.dp)) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Today's Sales", color = Sub, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                Box(Modifier.clip(RoundedCornerShape(10.dp)).background(GreenGlow.copy(0.2f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                                    Text("+${if (state.todayRevenue > 0) "8" else "0"}%", color = GreenGlow, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("KES ${String.format("%,.0f", state.todayRevenue)}", color = White, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
                            Text("vs yesterday", color = Sub, fontSize = 10.sp)
                            Text("+${if (state.todayRevenue > 0) "8" else "0"}%", color = TealGlow, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(10.dp))
                            MiniLineChart(listOf(0.2f, 0.4f, 0.3f, 0.6f, 0.5f, 0.8f, 0.7f, 1f), TealGlow, Modifier.fillMaxWidth().height(40.dp))
                        }
                    }
                    // Transactions card
                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(20.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFF12121F), Color(0xFF0A0A16))))
                        .border(1.dp, PurpleGlow.copy(0.2f), RoundedCornerShape(20.dp)).padding(16.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Spacer(Modifier.height(8.dp))
                            Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF1E1E35)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Star, null, tint = TealGlow, modifier = Modifier.size(22.dp))
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(state.todaySaleCount.toString(), color = White, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("Transactions\nToday", color = Sub, fontSize = 11.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }

            // ── Quick Actions ─────────────────────────────────────────────────
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Quick Actions", color = White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { showManageActions = !showManageActions }) {
                        Text(if (showManageActions) "Done" else "Edit", color = TealGlow, fontWeight = FontWeight.SemiBold)
                    }
                    Icon(Icons.Default.Tune, null, tint = TealGlow, modifier = Modifier.size(18.dp))
                }
            }

            item {
                val hidden = hiddenActions.split(",").filter { it.isNotBlank() }.toSet()
                val scope  = rememberCoroutineScope()
                fun hide(id: String) { scope.launch { settingsRepo?.setHiddenActions((hidden + id).joinToString(",")) } }

                val cards = buildList {
                    if ("sale" !in hidden)     add(DashCard_("sale",     "New Sale",      "Scan items or\nadd products",    Icons.Default.QrCode,   Color(0xFF0E2825), TealGlow,   onNavigateToScanner))
                    if ("products" !in hidden) add(DashCard_("products", "Products",      "Manage your\ninventory",         Icons.Default.Inventory2,Color(0xFF0D1F0E), GreenGlow,  onNavigateToProducts))
                    if (rm.canViewReports(currentRole) && "reports" !in hidden)
                        add(DashCard_("reports",  "Reports",       "View detailed\ninsights",        Icons.Default.BarChart, Color(0xFF180E2E), PurpleGlow, onNavigateToReports))
                    if (rm.canViewExpenses(currentRole) && "expenses" !in hidden)
                        add(DashCard_("expenses", "Expenses",      "Track your\nexpenses",           Icons.Default.Receipt,  Color(0xFF1F1205), AmberGlow,  onNavigateToExpenses))
                    if ("history" !in hidden)  add(DashCard_("history",  "Sales History", "View past\ntransactions",       Icons.Default.History,  Color(0xFF0A1628), BlueGlow,   onNavigateToSalesHistory))
                    if ("lowstock" !in hidden) add(DashCard_("lowstock", "Low Stock",     "Items running\nlow",            Icons.Default.Warning,  Color(0xFF1F0A0A), RedGlow,    onNavigateToLowStock))
                }

                Column(Modifier.padding(horizontal = 12.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    cards.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { c ->
                                Box(Modifier.weight(1f)) {
                                    DashCard(c.title, c.sub, c.icon, c.bg, c.glow, c.action)
                                    if (showManageActions) DashRemoveBadge { hide(c.id) }
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }

            // Restore hidden
            item {
                val hidden = hiddenActions.split(",").filter { it.isNotBlank() }.toSet()
                val scope  = rememberCoroutineScope()
                if (hidden.isNotEmpty()) {
                    TextButton(onClick = { scope.launch { settingsRepo?.setHiddenActions("") } }, Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Restore, null, modifier = Modifier.size(16.dp), tint = TealGlow); Spacer(Modifier.width(6.dp))
                        Text("Restore hidden cards", color = TealGlow, fontSize = 12.sp)
                    }
                }
            }

            // ── Alert banners ─────────────────────────────────────────────────
            if (state.lowStockProducts.isNotEmpty()) {
                item { DashAlert(Icons.Default.Warning, RedGlow, Color(0xFF1F0A0A), "${state.lowStockProducts.size} items low on stock", "Tap to restock", onNavigateToLowStock) }
            }
            if (state.expiredProducts.isNotEmpty() || state.expiringProducts.isNotEmpty()) {
                item { DashAlert(Icons.Default.CalendarToday,
                    if (state.expiredProducts.isNotEmpty()) RedGlow else AmberGlow,
                    if (state.expiredProducts.isNotEmpty()) Color(0xFF1F0A0A) else Color(0xFF1F1205),
                    "${state.expiredProducts.size + state.expiringProducts.size} expiry alert(s)", "Tap to view", onNavigateToInventory) }
            }

            // ── Top sellers ───────────────────────────────────────────────────
            if (state.topSellers.isNotEmpty()) {
                item { Text("Top Items Today", color = White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp)) }
                items(state.topSellers.take(5), key = { it.productId }) { s ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF0E2825)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Inventory2, null, modifier = Modifier.size(18.dp), tint = TealGlow)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(s.productName, color = White, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("×${s.totalQty}", color = TealGlow, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable private fun DashCard(title: String, subtitle: String, icon: ImageVector, bg: Color, glow: Color, onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.82f).clip(RoundedCornerShape(18.dp))
        .background(Brush.verticalGradient(listOf(bg, Bg))).border(1.dp, glow.copy(0.22f), RoundedCornerShape(18.dp))
        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick).padding(12.dp)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(glow.copy(0.18f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, modifier = Modifier.size(22.dp), tint = glow)
            }
            Spacer(Modifier.weight(1f))
            Text(title, color = White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = Sub, fontSize = 10.sp, lineHeight = 13.sp)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.size(24.dp).clip(CircleShape).background(glow.copy(0.18f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(16.dp), tint = glow)
            }
        }
    }
}

@Composable private fun DashAlert(icon: ImageVector, glow: Color, bg: Color, title: String, sub: String, action: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        .clip(RoundedCornerShape(14.dp)).background(bg).border(1.dp, glow.copy(0.3f), RoundedCornerShape(14.dp)).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = glow); Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(sub, color = Sub, fontSize = 11.sp)
            }
            TextButton(onClick = action) { Text("View", color = glow, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
        }
    }
}

@Composable private fun BoxScope.DashRemoveBadge(onRemove: () -> Unit) {
    Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).clip(CircleShape).background(RedGlow).clickable(onClick = onRemove), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp), tint = Color.White)
    }
}

@Composable private fun MiniLineChart(data: List<Float>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (data.size < 2) return@Canvas
        val w = size.width; val h = size.height; val max = data.maxOrNull() ?: 1f
        val pts = data.mapIndexed { i, v -> Offset(i * w / (data.size - 1), h - (v / max) * h * 0.85f) }
        val fill = Path().apply { moveTo(pts.first().x, h); pts.forEach { lineTo(it.x, it.y) }; lineTo(pts.last().x, h); close() }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(0.35f), Color.Transparent)))
        val line = Path().apply { moveTo(pts.first().x, pts.first().y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
        drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(color, 4.dp.toPx(), pts.last())
        drawCircle(Color.White, 2.dp.toPx(), pts.last())
    }
}
