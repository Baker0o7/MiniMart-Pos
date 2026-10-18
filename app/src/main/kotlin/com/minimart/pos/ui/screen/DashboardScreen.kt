package com.minimart.pos.ui.screen

import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.draw.drawBehind
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
private val Bg: Color get() = DT.DashBg
private val TealGlow: Color get() = DT.Glow
private val GreenGlow   = Color(0xFF4CAF50)
private val PurpleGlow  = Color(0xFFB39DDB)
private val BlueGlow    = Color(0xFF64B5F6)
private val AmberGlow   = Color(0xFFFFB74D)
private val RedGlow     = Color(0xFFEF5350)
private val White       = Color.White
private val Sub: Color get() = DT.SubText

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
    onNavigateToPurchasing: () -> Unit = {},
    onNavigateToLabels: () -> Unit = {},
    onNavigateToCustomerDisplay: () -> Unit = {},
    onNavigateToActivityLog: () -> Unit = {},
    currentRole: UserRole? = null,
    currentUserName: String? = null,
    settingsRepo: SettingsRepository? = null,
    vm: DashboardViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    CrashReportPrompt()
    val rm    = com.minimart.pos.util.RoleManager
    val hiddenActions by (settingsRepo?.hiddenActions
        ?: kotlinx.coroutines.flow.flowOf("")).collectAsStateWithLifecycle("")
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
                    .background(Brush.verticalGradient(listOf(DT.DashTop, Bg)))
                    .drawBehind {
                        // soft teal glows in the corners
                        drawCircle(Brush.radialGradient(listOf(DT.Glow.copy(alpha = 0.20f), Color.Transparent), center = Offset(0f, 0f), radius = size.width * 0.55f),
                            radius = size.width * 0.55f, center = Offset(0f, 0f))
                        drawCircle(Brush.radialGradient(listOf(DT.Glow.copy(alpha = 0.15f), Color.Transparent), center = Offset(size.width, size.height), radius = size.width * 0.5f),
                            radius = size.width * 0.5f, center = Offset(size.width, size.height))
                    }
                    .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Avatar: glowing rounded tile with the flag and an "online" dot
                        Box(modifier = Modifier.size(56.dp)) {
                            Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp))
                                .background(Brush.linearGradient(listOf(DT.CardTop, DT.CardBottom)))
                                .border(2.dp, TealGlow.copy(0.55f), RoundedCornerShape(18.dp)),
                                contentAlignment = Alignment.Center) {
                                Box(Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(DT.Bg),
                                    contentAlignment = Alignment.Center) {
                                    Text("🇰🇪", fontSize = 22.sp)
                                }
                            }
                            Box(Modifier.align(Alignment.BottomEnd).size(16.dp).clip(CircleShape)
                                .background(Bg).padding(2.dp).clip(CircleShape).background(GreenGlow))
                        }
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.width(1.5.dp).height(46.dp).background(TealGlow.copy(0.6f)))
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(roleLabel(currentRole) ?: "Welcome", color = White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            // A long shop name must not wrap and unbalance the header.
                            Text(state.storeName, color = DT.StoreText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val today = remember { java.text.SimpleDateFormat("EEE, d MMM", java.util.Locale.getDefault()).format(java.util.Date()) }
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CalendarToday, null, tint = TealGlow, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(today, color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        // Status pill: stacked so the name, shop and date keep enough width on narrow phones.
                        Column(modifier = Modifier.clip(RoundedCornerShape(16.dp))
                            .background(DT.Chip)
                            .border(1.dp, TealGlow.copy(0.45f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                            horizontalAlignment = Alignment.Start) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF8CA3B5)))
                                Text(" Offline", color = White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                            }
                            Box(Modifier.padding(vertical = 4.dp).width(60.dp).height(1.dp).background(TealGlow.copy(0.45f)))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(GreenGlow))
                                Text(" Ready", color = White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        // Settings button
                        Box(modifier = Modifier.size(42.dp).clip(CircleShape)
                            .background(DT.Chip)
                            .border(1.5.dp, TealGlow.copy(0.45f), CircleShape)
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onNavigateToSettings() },
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Settings, "Settings", tint = DT.Glow, modifier = Modifier.size(23.dp))
                        }
                    }
                }
            }

            // ── Backup warning (owner / manager only) ─────────────────────────
            item {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val backupWarn = remember(state.todayRevenue, state.todaySaleCount) { com.minimart.pos.util.BackupManager.warning(ctx) }
                val refused by vm.refusedSales.collectAsStateWithLifecycle()
                val warn = listOfNotNull(
                    backupWarn,
                    if (refused > 0) "$refused sale${if (refused == 1) "" else "s"} refused by the main device — open Settings → Sync to retry" else null
                ).joinToString("\n").ifBlank { null }
                if (warn != null && rm.canAccessSettings(currentRole)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(14.dp)).background(AmberGlow.copy(0.14f))
                        .border(1.dp, AmberGlow.copy(0.6f), RoundedCornerShape(14.dp))
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onNavigateToSettings() }
                        .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = AmberGlow, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(warn, color = White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
                val money = { v: Double -> "${state.currency} ${String.format(java.util.Locale.US, "%,.0f", v)}" }
                val interaction = remember { MutableInteractionSource() }
                // One combined summary card: revenue on the left, count + average basket on the right.
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF0B3A33), Color(0xFF0A2A3A), Color(0xFF14204A))))
                    .border(1.dp, TealGlow.copy(0.5f), RoundedCornerShape(20.dp))) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        // Left: revenue
                        Column(Modifier.weight(1.25f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp))
                                    .background(Brush.linearGradient(listOf(DT.Glow, DT.HeaderMid))),
                                    contentAlignment = Alignment.Center) {
                                    Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = White, modifier = Modifier.size(20.dp))
                                }
                                Spacer(Modifier.width(8.dp))
                                Text("Today's Sales Revenue", color = White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(money(state.todayRevenue), color = White, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider(color = TealGlow.copy(0.25f), thickness = 1.dp)
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CalendarMonth, null, tint = Sub, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(5.dp))
                                if (hasYesterday) {
                                    // Only a real comparison: with no sales yesterday there is nothing to compare to.
                                    Text("${if (up) "▲ +" else "▼ "}${String.format(java.util.Locale.US, "%.0f", pct)}% vs yesterday",
                                        color = if (up) GreenGlow else RedGlow, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                } else {
                                    Text("No sales yesterday", color = Sub, fontSize = 11.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        // Right: count + average basket (tap through to detail)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SummaryStat(Icons.Default.ShoppingBag, PurpleGlow, "Sales (Count)", state.todaySaleCount.toString(),
                                onNavigateToSalesHistory, interaction)
                            SummaryStat(Icons.Default.ShoppingCart, GreenGlow, "Average Basket Value", money(avgBasket),
                                if (rm.canViewReports(currentRole)) onNavigateToAnalytics else onNavigateToSalesHistory, interaction)
                        }
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
                        .background(if (showManageActions) DT.Teal else DT.Chip)
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
                        add(DashCard_("stockinsights","Insights","Reorder & value",Icons.Default.Inventory2,Color(0xFF1E1708),AmberGlow,onNavigateToStockInsights))
                    if (rm.canEditPrices(currentRole) && "purchasing" !in hidden)
                        add(DashCard_("purchasing","Purchasing","Suppliers & POs",Icons.Default.LocalShipping,Color(0xFF0B2220),GreenGlow,onNavigateToPurchasing))
                    if (rm.canEditPrices(currentRole) && "labels" !in hidden)
                        add(DashCard_("labels","Labels","Print barcodes",Icons.Default.QrCode2,Color(0xFF1A1030),PurpleGlow,onNavigateToLabels))
                    if (rm.canManageUsers(currentRole) && "activity" !in hidden)
                        add(DashCard_("activity","Activity","Who did what",Icons.Default.Fingerprint,Color(0xFF1A0F1F),PurpleGlow,onNavigateToActivityLog))
                    if ("customerview" !in hidden)
                        add(DashCard_("customerview","Customer","Display screen",Icons.Default.Tv,Color(0xFF0B1A2C),BlueGlow,onNavigateToCustomerDisplay))
                    if ("history"  !in hidden) add(DashCard_("history",  "History","Past sales",        Icons.Default.History,   Color(0xFF081525), BlueGlow,   onNavigateToSalesHistory))
                    if ("lowstock" !in hidden) add(DashCard_("lowstock", "Low Stock",    "Running low", Icons.Default.Warning,   Color(0xFF1E0808), RedGlow,    onNavigateToLowStock))
                    if ("mpesa" !in hidden) add(DashCard_("mpesa", "M-Pesa", "Payments", Icons.Default.PhoneAndroid, Color(0xFF0B2210), GreenGlow, onNavigateToMpesa))
                    if ("customers" !in hidden) add(DashCard_("customers", "Customers", "Credit & loyalty",  Icons.Default.People,    Color(0xFF0B1525), BlueGlow,   onNavigateToCustomers))
                    if ("creditoverview" !in hidden) add(DashCard_("creditoverview", "Ledger", "Balances owed", Icons.Default.AccountBalanceWallet, Color(0xFF1A0808), RedGlow, onNavigateToCreditOverview))
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

    // Height follows the content (with a floor) instead of a fixed aspect ratio: on phones with a
    // large font setting the fixed ratio clipped the subtitle at the bottom of the card.
    Box(modifier = Modifier.fillMaxWidth()
        .defaultMinSize(minHeight = 104.dp)
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(RoundedCornerShape(20.dp))
        .background(Brush.verticalGradient(listOf(bg, Bg)))
        .border(1.5.dp, glow.copy(0.5f), RoundedCornerShape(20.dp))
        .clickable(indication = null, interactionSource = interactionSource, onClick = onClick)
        .padding(start = 11.dp, end = 8.dp, top = 11.dp, bottom = 11.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Box(modifier = Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
                    .background(glow.copy(0.22f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = glow, modifier = Modifier.size(23.dp))
                }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.ChevronRight, null, tint = White.copy(0.75f), modifier = Modifier.size(20.dp).padding(top = 2.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(title, color = White, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, lineHeight = 16.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = Sub, fontSize = 10.sp, lineHeight = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Icon tile + label + value, used on the right side of the Home summary card. */
@Composable
private fun SummaryStat(icon: ImageVector, glow: Color, label: String, value: String,
    onClick: () -> Unit, interaction: MutableInteractionSource) {
    Row(modifier = Modifier.fillMaxWidth()
        .clickable(indication = null, interactionSource = interaction, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(glow.copy(0.3f)),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = glow, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = White.copy(0.85f), fontSize = 11.sp, fontWeight = FontWeight.Medium,
                maxLines = 2, lineHeight = 13.sp)
            Text(value, color = White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    // 32dp touch target around a 20dp visual badge.
    Box(Modifier.align(Alignment.TopEnd).size(32.dp).clickable(onClick = onRemove), contentAlignment = Alignment.Center) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(RedGlow), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Close, "Hide card", tint = White, modifier = Modifier.size(12.dp))
        }
    }
}

