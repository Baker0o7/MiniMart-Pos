package com.minimart.pos.ui.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.GridView
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

private fun roleLabel(role: UserRole?): String? = when (role) {
    UserRole.OWNER   -> "Owner"
    UserRole.MANAGER -> "Manager"
    UserRole.CASHIER -> "Cashier"
    null             -> null
}

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
    onNavigateToCustomers:    () -> Unit = {},
    onNavigateToCreditOverview: () -> Unit = {},
    onNavigateToMpesa: () -> Unit = {},
    onNavigateToAnalytics: () -> Unit = {},
    onNavigateToStockInsights: () -> Unit = {},
    currentRole: UserRole? = null,
    currentUserName: String? = null,
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
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {

            // ── Greeting header ───────────────────────────────────────────────
            item {
                Box(modifier = Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xFF0D2420), Bg)))
                    .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Avatar: glowing rounded tile with the flag and an "online" dot
                        Box(modifier = Modifier.size(62.dp)) {
                            Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp))
                                .background(Brush.linearGradient(listOf(Color(0xFF0E3A33), Color(0xFF06201C))))
                                .border(2.dp, TealGlow.copy(0.55f), RoundedCornerShape(20.dp)),
                                contentAlignment = Alignment.Center) {
                                Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF0A1A18)),
                                    contentAlignment = Alignment.Center) {
                                    Text("🇰🇪", fontSize = 26.sp)
                                }
                            }
                            Box(Modifier.align(Alignment.BottomEnd).size(16.dp).clip(CircleShape)
                                .background(Bg).padding(2.dp).clip(CircleShape).background(GreenGlow))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(roleLabel(currentRole) ?: "Welcome", color = White, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            // A long shop name must not wrap and unbalance the header.
                            Text(state.storeName, color = Sub, fontSize = 14.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val today = remember { java.text.SimpleDateFormat("EEE, d MMM", java.util.Locale.getDefault()).format(java.util.Date()) }
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CalendarToday, null, tint = Sub, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(today, color = Sub, fontSize = 13.sp, maxLines = 1)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        // Status pill
                        Row(modifier = Modifier.clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF0A1F1C))
                            .border(1.dp, TealGlow.copy(0.45f), RoundedCornerShape(24.dp))
                            .padding(horizontal = 11.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(TealGlow))
                            Text(" Offline", color = TealGlow, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.size(8.dp).clip(CircleShape).background(GreenGlow))
                            Text(" Ready", color = GreenGlow, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.width(8.dp))
                        // Settings button
                        Box(modifier = Modifier.size(48.dp).clip(CircleShape)
                            .background(Color(0xFF0A1F1C))
                            .border(1.5.dp, TealGlow.copy(0.45f), CircleShape)
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onNavigateToSettings() },
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Settings, "Settings", tint = TealGlow, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }

            // ── Stats row ─────────────────────────────────────────────────────
            item {
                val hasYesterday = state.yesterdayRevenue > 0
                val pct = if (hasYesterday) (state.todayRevenue - state.yesterdayRevenue) / state.yesterdayRevenue * 100 else 0.0
                val up = pct >= 0
                val avgBasket = if (state.todaySaleCount > 0) state.todayRevenue / state.todaySaleCount else 0.0
                val spark = if (state.hourlySpark.size >= 2) state.hourlySpark else List(12) { 0f }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(148.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Revenue card
                    Box(modifier = Modifier.weight(1.3f).fillMaxHeight().clip(RoundedCornerShape(22.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFF0E2E28), Color(0xFF071815))))
                        .border(1.dp, TealGlow.copy(0.3f), RoundedCornerShape(22.dp))) {
                        // Faint hourly curve in the corner
                        MiniLineChart(spark, TealGlow.copy(0.55f),
                            Modifier.align(Alignment.BottomEnd).fillMaxWidth(0.6f).height(64.dp).padding(bottom = 30.dp))
                        Column(Modifier.fillMaxSize().padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                                    .background(Brush.linearGradient(listOf(Color(0xFF00A58B), Color(0xFF00695C)))),
                                    contentAlignment = Alignment.Center) {
                                    Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = White, modifier = Modifier.size(22.dp))
                                }
                                Spacer(Modifier.width(10.dp))
                                Text("Today's Sales", color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("${state.currency} ${String.format(java.util.Locale.US, "%,.0f", state.todayRevenue)}",
                                color = White, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.weight(1f))
                            HorizontalDivider(color = TealGlow.copy(0.22f), thickness = 1.dp)
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CalendarMonth, null, tint = Sub, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                if (hasYesterday) {
                                    // Only a real comparison: with no sales yesterday there is nothing to compare to.
                                    val tint = if (up) GreenGlow else RedGlow
                                    Text("${if (up) "▲ +" else "▼ "}${String.format(java.util.Locale.US, "%.0f", pct)}% vs yesterday",
                                        color = tint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                } else {
                                    Text("No sales yesterday to compare", color = Sub, fontSize = 11.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                    // Right column: sales count + average basket (tap through to detail)
                    Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(Modifier.weight(1f), Icons.Default.ShoppingBag, PurpleGlow, Color(0xFF14102A),
                            "Sales", state.todaySaleCount.toString(), onNavigateToSalesHistory)
                        StatTile(Modifier.weight(1f), Icons.Default.ShoppingCart, GreenGlow, Color(0xFF0C1F10),
                            "Avg Basket", "${state.currency} ${String.format(java.util.Locale.US, "%,.0f", avgBasket)}",
                            if (rm.canViewReports(currentRole)) onNavigateToAnalytics else onNavigateToSalesHistory)
                    }
                }
            }

            // ── Needs attention (first, so it is never below the fold) ────────
            if (state.lowStockProducts.isNotEmpty()) {
                item { Spacer(Modifier.height(16.dp)) }
                item {
                    val n = state.lowStockProducts.size
                    DashAlert(Icons.Default.Warning, RedGlow, Color(0xFF1E0808),
                        "$n ${if (n == 1) "item is" else "items are"} low on stock", "Needs restocking", onNavigateToLowStock)
                }
            }
            if (state.expiredProducts.isNotEmpty() || state.expiringProducts.isNotEmpty()) {
                item { Spacer(Modifier.height(if (state.lowStockProducts.isEmpty()) 16.dp else 6.dp)) }
                item {
                    val parts = listOfNotNull(
                        state.expiredProducts.size.takeIf { it > 0 }?.let { "$it expired" },
                        state.expiringProducts.size.takeIf { it > 0 }?.let { "$it expiring soon" }
                    )
                    DashAlert(
                        Icons.Default.CalendarToday,
                        if (state.expiredProducts.isNotEmpty()) RedGlow else AmberGlow,
                        if (state.expiredProducts.isNotEmpty()) Color(0xFF1E0808) else Color(0xFF1E1005),
                        parts.joinToString(" · "), "Review in Inventory", onNavigateToInventory)
                }
            }

            item { Spacer(Modifier.height(24.dp)) }

            // ── Quick Actions ─────────────────────────────────────────────────
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.GridView, null, tint = TealGlow, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Quick Actions", color = White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                        Text("Everything else is in the bottom bar", color = Sub, fontSize = 12.sp)
                    }
                    Box(modifier = Modifier.clip(RoundedCornerShape(16.dp))
                        .background(if (showManageActions) DT.Teal else Color(0xFF0A1F1C))
                        .border(1.dp, if (showManageActions) DT.Teal else TealGlow.copy(0.5f), RoundedCornerShape(16.dp))
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { showManageActions = !showManageActions }
                        .padding(horizontal = 16.dp, vertical = 11.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (showManageActions) Icons.Default.Check else Icons.Default.Tune,
                                null, tint = if (showManageActions) Color.White else TealGlow, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(7.dp))
                            Text(if (showManageActions) "Done" else "Edit",
                                color = if (showManageActions) Color.White else TealGlow,
                                fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
                    if (rm.canViewReports(currentRole) && "reports" !in hidden)
                        add(DashCard_("reports","Reports","Sales & PDF",Icons.Default.BarChart,Color(0xFF160B2C),PurpleGlow,onNavigateToReports))
                    if (rm.canViewReports(currentRole) && "analytics" !in hidden)
                        add(DashCard_("analytics","Analytics","Trends & profit",Icons.Default.Insights,Color(0xFF0B1F2C),BlueGlow,onNavigateToAnalytics))
                    if (rm.canEditPrices(currentRole) && "stockinsights" !in hidden)
                        add(DashCard_("stockinsights","Stock Insights","Reorder & value",Icons.Default.Inventory2,Color(0xFF1E1708),AmberGlow,onNavigateToStockInsights))
                    if ("history"  !in hidden) add(DashCard_("history",  "Sales History","Past sales",        Icons.Default.History,   Color(0xFF081525), BlueGlow,   onNavigateToSalesHistory))
                    if ("lowstock" !in hidden) add(DashCard_("lowstock", "Low Stock",    "Items running low", Icons.Default.Warning,   Color(0xFF1E0808), RedGlow,    onNavigateToLowStock))
                    if ("mpesa" !in hidden) add(DashCard_("mpesa", "M-Pesa", "Payments & verify", Icons.Default.PhoneAndroid, Color(0xFF0B2210), GreenGlow, onNavigateToMpesa))
                    if ("customers" !in hidden) add(DashCard_("customers", "Customers", "Credit & loyalty",  Icons.Default.People,    Color(0xFF0B1525), BlueGlow,   onNavigateToCustomers))
                    if ("creditoverview" !in hidden) add(DashCard_("creditoverview", "Credit Ledger", "Outstanding balances", Icons.Default.AccountBalanceWallet, Color(0xFF1A0808), RedGlow, onNavigateToCreditOverview))
                }

                Column(Modifier.padding(horizontal = 16.dp).animateContentSize(),
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
    // A press-scale animation gives the tap visible feedback during the screen transition.
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.95f else 1f, label = "cardPress")

    Box(modifier = Modifier.fillMaxWidth()
        .aspectRatio(1.08f)
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(RoundedCornerShape(20.dp))
        .background(Brush.verticalGradient(listOf(bg, Bg)))
        .border(1.5.dp, glow.copy(0.5f), RoundedCornerShape(20.dp))
        .clickable(indication = null, interactionSource = interactionSource, onClick = onClick)
        .padding(12.dp)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                    .background(glow.copy(0.22f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = glow, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.ChevronRight, null, tint = White.copy(0.75f), modifier = Modifier.size(20.dp).padding(top = 2.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(title, color = White, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(sub, color = Sub, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Compact stat card (icon tile, label, value, chevron) used in the Home stats row. */
@Composable
private fun StatTile(modifier: Modifier, icon: ImageVector, glow: Color, bg: Color,
    label: String, value: String, onClick: () -> Unit) {
    Row(modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
        .background(Brush.verticalGradient(listOf(bg, Bg)))
        .border(1.dp, glow.copy(0.4f), RoundedCornerShape(18.dp))
        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
        .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(glow.copy(0.28f)),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = glow, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(value, color = White, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Default.ChevronRight, null, tint = White.copy(0.75f), modifier = Modifier.size(18.dp))
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
    // 32dp touch target around a 20dp visual badge.
    Box(Modifier.align(Alignment.TopEnd).size(32.dp).clickable(onClick = onRemove), contentAlignment = Alignment.Center) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(RedGlow), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Close, "Hide card", tint = White, modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
private fun MiniLineChart(data: List<Float>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (data.size < 2 || data.isEmpty()) return@Canvas
        val w = size.width; val h = size.height; val max = (data.maxOrNull() ?: 1f).coerceAtLeast(0.0001f)
        val pts = data.mapIndexed { i, v -> Offset(i * w / (data.size - 1), h - (v / max) * h * 0.85f) }
        val fill = Path().apply { moveTo(pts.first().x, h); pts.forEach { lineTo(it.x, it.y) }; lineTo(pts.last().x, h); close() }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(0.4f), Color.Transparent)))
        val line = Path().apply { moveTo(pts.first().x, pts.first().y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
        drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(color, 4.dp.toPx(), pts.last())
        drawCircle(Color.White, 2.dp.toPx(), pts.last())
    }
}
