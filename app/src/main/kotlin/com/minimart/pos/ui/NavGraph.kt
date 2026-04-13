package com.minimart.pos.ui

import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.minimart.pos.data.repository.SettingsRepository
import com.minimart.pos.printer.ThermalPrinter
import com.minimart.pos.ui.screen.*
import com.minimart.pos.ui.viewmodel.AuthViewModel
import com.minimart.pos.ui.viewmodel.CartViewModel

object Routes {
    const val LOGIN     = "login"
    const val DASHBOARD = "dashboard"
    const val SCANNER   = "scanner"
    const val CHECKOUT  = "checkout"
    const val RECEIPT   = "receipt/{saleId}"
    const val PRODUCTS  = "products"
    const val REPORTS   = "reports"
    const val SETTINGS  = "settings"
    fun receipt(saleId: Long) = "receipt/$saleId"
}

@Composable
fun MiniMartNavGraph(
    settingsRepo: SettingsRepository,
    printer: ThermalPrinter,
    darkMode: Boolean
) {
    val navController = rememberNavController()
    val authVm: AuthViewModel = hiltViewModel()
    val authState by authVm.uiState.collectAsState()

    // ── Shared CartViewModel scoped to the NavGraph ───────────────────────────
    // Must be created here so Scanner and Checkout share the SAME instance
    val cartVm: CartViewModel = hiltViewModel()

    val storeName by settingsRepo.storeName.collectAsState("My MiniMart")
    val currency   by settingsRepo.currency.collectAsState("KES")
    val footer     by settingsRepo.receiptFooter.collectAsState("Thank you!")

    NavHost(navController = navController, startDestination = Routes.LOGIN) {

        composable(Routes.LOGIN) {
            LaunchedEffect(authState.isLoggedIn) {
                if (authState.isLoggedIn) navController.navigate(Routes.DASHBOARD) {
                    popUpTo(Routes.LOGIN) { inclusive = true }
                }
            }
            LoginScreen(onLoginSuccess = {
                navController.navigate(Routes.DASHBOARD) {
                    popUpTo(Routes.LOGIN) { inclusive = true }
                }
            })
        }

        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onNavigateToScanner  = { navController.navigate(Routes.SCANNER) },
                onNavigateToProducts = { navController.navigate(Routes.PRODUCTS) },
                onNavigateToReports  = { navController.navigate(Routes.REPORTS) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(Routes.SCANNER) {
            ScannerCartScreen(
                onNavigateToCheckout = { navController.navigate(Routes.CHECKOUT) },
                onBack = { navController.popBackStack() },
                vm = cartVm   // shared instance
            )
        }

        composable(Routes.CHECKOUT) {
            CheckoutScreen(
                onSaleComplete = { saleId ->
                    navController.navigate(Routes.receipt(saleId)) {
                        popUpTo(Routes.SCANNER) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
                vm = cartVm   // same shared instance — cart data intact
            )
        }

        composable(
            Routes.RECEIPT,
            arguments = listOf(navArgument("saleId") { type = NavType.LongType })
        ) { backStack ->
            val saleId = backStack.arguments?.getLong("saleId") ?: 0L
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

        composable(Routes.PRODUCTS) {
            ProductListScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.REPORTS) {
            ReportsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack   = { navController.popBackStack() },
                onLogout = {
                    authVm.logout()
                    navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                },
                settingsRepo = settingsRepo,
                printer      = printer
            )
        }
    }
}
