package com.minimart.pos

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.minimart.pos.data.repository.SettingsRepository
import com.minimart.pos.printer.ThermalPrinter
import com.minimart.pos.scanner.KeyboardScanner
import com.minimart.pos.ui.MiniMartNavGraph
import com.minimart.pos.ui.theme.MiniMartTheme
import com.minimart.pos.util.SessionManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        /** Intent extra set by the low-stock / expiry notifications. */
        const val EXTRA_NAVIGATE_TO = "navigate_to"
    }

    @Inject lateinit var keyboardScanner: KeyboardScanner
    @Inject lateinit var printer: ThermalPrinter
    @Inject lateinit var settingsRepo: SettingsRepository
    @Inject lateinit var sessionManager: SessionManager

    // Where a tapped notification wants to go. Consumed by the nav graph once the cashier is
    // signed in, so a tap never bypasses the PIN screen.
    private var pendingRoute by mutableStateOf<String?>(null)

    // Low-stock and expiry alerts are silently dropped on Android 13+ until this is granted.
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* nothing to do */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // The UI is always dark, so ask for light system-bar icons explicitly instead of letting
        // the default follow the phone's light/dark setting (dark icons on dark teal).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        // Only read the extra on a fresh launch — after rotation the same intent is replayed.
        if (savedInstanceState == null) readNavigationExtra(intent)
        requestNotificationPermissionIfNeeded()
        setContent {
            val darkMode by settingsRepo.darkMode.collectAsState(false)
            MiniMartTheme(darkTheme = darkMode) {
                MiniMartNavGraph(
                    settingsRepo = settingsRepo,
                    printer = printer,
                    darkMode = darkMode,
                    pendingRoute = pendingRoute,
                    onPendingRouteConsumed = { pendingRoute = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readNavigationExtra(intent)
    }

    private fun readNavigationExtra(intent: Intent?) {
        val target = intent?.getStringExtra(EXTRA_NAVIGATE_TO) ?: return
        intent?.removeExtra(EXTRA_NAVIGATE_TO)
        pendingRoute = target
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /**
     * Any touch or key (including a barcode scanner typing) counts as activity. Nothing used to
     * call SessionManager.recordActivity(), so the 15-minute idle timer ran from app launch and
     * signed cashiers out mid-sale even while they were busy.
     */
    override fun onUserInteraction() {
        super.onUserInteraction()
        sessionManager.recordActivity()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event != null && keyboardScanner.onKeyDown(keyCode, event)) return true
        return super.onKeyDown(keyCode, event)
    }
}
