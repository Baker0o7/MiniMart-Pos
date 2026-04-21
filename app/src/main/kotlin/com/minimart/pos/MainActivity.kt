package com.minimart.pos

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.minimart.pos.data.repository.SettingsRepository
import com.minimart.pos.printer.ThermalPrinter
import com.minimart.pos.scanner.KeyboardScanner
import com.minimart.pos.ui.MiniMartNavGraph
import com.minimart.pos.ui.theme.MiniMartTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var keyboardScanner: KeyboardScanner
    @Inject lateinit var printer: ThermalPrinter
    @Inject lateinit var settingsRepo: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val darkMode by settingsRepo.darkMode.collectAsState(false)
            MiniMartTheme(darkTheme = darkMode) {
                MiniMartNavGraph(
                    settingsRepo = settingsRepo,
                    printer = printer,
                    darkMode = darkMode
                )
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event != null && keyboardScanner.onKeyDown(keyCode, event)) return true
        return super.onKeyDown(keyCode, event)
    }
}
