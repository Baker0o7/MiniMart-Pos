package com.minimart.pos.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.minimart.pos.data.repository.SettingsRepository
import com.minimart.pos.printer.ThermalPrinter
import com.minimart.pos.ui.screen.*
import com.minimart.pos.ui.viewmodel.AuthViewModel
import com.minimart.pos.ui.viewmodel.CartViewModel
import com.minimart.pos.util.RoleManager

object Routes {
    const val LOGIN     = "login"
    const val DASHBOARD = "dashboard"
    const val SCANNER   = "scanner"
    const val CHECKOUT  = "checkout"
    const val RECEIPT   = "receipt/{saleId}"
    const val PRODUCTS  = "products"
    const val INVENTORY = "inventory"
    const val REPORTS   = "reports"
    const val EXPENSES  = "expenses"
    const val USERS     = "users"
    const val SHIFTS    = "shifts"
    const val SETTINGS  = "settings"
    fun receipt(saleId: Long) = "receipt/$saleId"
}

private val NavBg    = Color(0xFF0F1E1D)
private val NavSel   = Color(0xFF00897B)
private val NavUnsel = Color(0xFF4A6B68)
private val NavSurface = Color(0xFF1A2E2C)

// Routes that should show the bottom nav
private val mainRoutes = setOf(
    Routes.DASHBOARD, Routes.PRODUCTS, Routes.INVENTORY, Routes.EXPENSES
)

@Composable
fun MiniMartNavGraph(
    settingsRepo: SettingsRepository,
    printer: ThermalPrinter,
    darkMode: Boolean
) {
    val navController = rememberNavController()
    val authVm: AuthViewModel = hiltViewModel()
    val authState by authVm.uiState.collectAsState()
    val cartVm: CartViewModel = hiltViewModel()

    val storeName by settingsRepo.storeName.collectAsState("My MiniMart")
    val currency   by settingsRepo.currency.collectAsState("KES")
    val footer     by settingsRepo.receiptFooter.collectAsState("Thank you!")

    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route
    val showBottomNav = currentRoute in mainRoutes

    Scaffold(
        containerColor = NavBg,
        bottomBar = {
            if (showBottomNav) {
                BottomNavBar(
                    currentRoute = currentRoute,
                    onHome       = { navController.navigate(Routes.DASHBOARD) { launchSingleTop = true; restoreState = true } },
                    onProducts   = { navController.navigate(Routes.PRODUCTS)  { launchSingleTop = true } },
                    onScan       = { navController.navigate(Routes.SCANNER) },
                    onExpenses   = { navController.navigate(Routes.EXPENSES)  { launchSingleTop = true } },
                    onInventory  = { navController.navigate(Routes.INVENTORY) { launchSingleTop = true } }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            NavHost(
        navController = navController,
        startDestination = Routes.LOGIN,
        enterTransition = { slideInHorizontally(tween(280)) { it / 3 } + fadeIn(tween(280)) },
        exitTransition = { slideOutHorizontally(tween(280)) { -it / 3 } + fadeOut(tween(280)) },
        popEnterTransition = { slideInHorizontally(tween(280)) { -it / 3 } + fadeIn(tween(280)) },
        popExitTransition = { slideOutHorizontally(tween(280)) { it / 3 } + fadeOut(tween(280)) }
    ) {

                composable(Routes.LOGIN) {
                    LaunchedEffect(authState.isLoggedIn) {
                        if (authState.isLoggedIn) navController.navigate(Routes.DASHBOARD) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    }
                    LoginScreen(onLoginSuccess = {
                        navController.navigate(Routes.DASHBOARD) { popUpTo(Routes.LOGIN) { inclusive = true } }
                    })
                }

                composable(Routes.DASHBOARD) {
                    DashboardScreen(
                        onNavigateToScanner   = { navController.navigate(Routes.SCANNER) },
                        onNavigateToProducts  = { navController.navigate(Routes.PRODUCTS) },
                        onNavigateToInventory = { navController.navigate(Routes.INVENTORY) },
                        onNavigateToReports   = { navController.navigate(Routes.REPORTS) },
                        onNavigateToExpenses  = { navController.navigate(Routes.EXPENSES) },
                        onNavigateToSettings  = { navController.navigate(Routes.SETTINGS) }
                    )
                }

                composable(Routes.SCANNER) {
                    ScannerCartScreen(
                        onNavigateToCheckout = { navController.navigate(Routes.CHECKOUT) },
                        onBack = { navController.popBackStack() },
                        vm = cartVm
                    )
                }

                composable(Routes.CHECKOUT) {
                    CheckoutScreen(
                        onSaleComplete = { saleId ->
                            navController.navigate(Routes.receipt(saleId)) { popUpTo(Routes.SCANNER) { inclusive = true } }
                        },
                        onBack = { navController.popBackStack() },
                        vm = cartVm
                    )
                }

                composable(Routes.RECEIPT, arguments = listOf(navArgument("saleId") { type = NavType.LongType })) { back ->
                    val saleId = back.arguments?.getLong("saleId") ?: 0L
                    ReceiptScreen(
                        saleId        = saleId,
                        onNewSale     = { cartVm.clearCart(); navController.navigate(Routes.SCANNER) { popUpTo(Routes.DASHBOARD) } },
                        onDashboard   = { cartVm.clearCart(); navController.navigate(Routes.DASHBOARD) { popUpTo(Routes.DASHBOARD) { inclusive = true } } },
                        printer       = printer,
                        storeName     = storeName,
                        currency      = currency,
                        footerMessage = footer,
                        cashierName   = authState.currentUser?.displayName ?: "Cashier"
                    )
                }

                composable(Routes.PRODUCTS)  { ProductListScreen(onBack = { navController.popBackStack() }, canEditPrices = RoleManager.canEditPrices(authState.currentUser?.role)) }
                composable(Routes.INVENTORY) { InventoryScreen(onBack = { navController.popBackStack() }, canEditPrices = RoleManager.canEditPrices(authState.currentUser?.role)) }
                composable(Routes.REPORTS)   { ReportsScreen(onBack = { navController.popBackStack() }) }
                composable(Routes.EXPENSES)  { ExpenseScreen(onBack = { navController.popBackStack() }) }
                composable(Routes.USERS)     { UserManagementScreen(onBack = { navController.popBackStack() }) }
                composable(Routes.SHIFTS)    { ShiftScreen(onBack = { navController.popBackStack() }) }

                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onBack       = { navController.popBackStack() },
                        onShifts     = { navController.navigate(Routes.SHIFTS) },
                        onUsers      = { navController.navigate(Routes.USERS) },
                        onLogout     = {
                            authVm.logout()
                            navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                        },
                        settingsRepo  = settingsRepo,
                        printer       = printer,
                        currentRole   = authState.currentUser?.role
                    )
                }
            }
        }
    }
}

// ─── Bottom Nav Bar ───────────────────────────────────────────────────────────

@Composable
private fun BottomNavBar(
    currentRoute: String?,
    onHome: () -> Unit,
    onProducts: () -> Unit,
    onScan: () -> Unit,
    onExpenses: () -> Unit,
    onInventory: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        // Nav bar surface
        Surface(
            modifier = Modifier.fillMaxWidth().height(68.dp).align(Alignment.BottomCenter),
            color = NavSurface,
            shadowElevation = 12.dp,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavItem(Icons.Default.Home,          "Home",      currentRoute == Routes.DASHBOARD, onHome)
                NavItem(Icons.Default.Inventory2,    "Products",  currentRoute == Routes.PRODUCTS,  onProducts)
                Spacer(Modifier.width(64.dp)) // space for center button
                NavItem(Icons.Default.Receipt,   "Expenses",  currentRoute == Routes.EXPENSES,  onExpenses)
                NavItem(Icons.Default.Store,         "Inventory", currentRoute == Routes.INVENTORY, onInventory)
            }
        }

        // Raised center scan button
        Box(
            modifier = Modifier
                .size(64.dp)
                .align(Alignment.TopCenter)
                .offset(y = (-12).dp)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(NavSel)
                .clickable(onClick = onScan),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.QrCode, null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun RowScope.NavItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.weight(1f).fillMaxHeight().clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = if (selected) NavSel else NavUnsel, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, color = if (selected) NavSel else NavUnsel, fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
    }
}
