package com.minimart.pos

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.minimart.pos.data.repository.SettingsRepository
import com.minimart.pos.printer.ThermalPrinter
import com.minimart.pos.scanner.KeyboardScanner
import com.minimart.pos.ui.MiniMartNavGraph
import com.minimart.pos.ui.theme.MiniMartTheme
import dagger.hilt.android.AndroidEntryPoint
import net.sqlcipher.database.SQLiteDatabase
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {   // AppCompatActivity extends FragmentActivity → biometric works

    @Inject lateinit var keyboardScanner: KeyboardScanner
    @Inject lateinit var printer: ThermalPrinter
    @Inject lateinit var settingsRepo: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // SQLCipher native libs must be loaded on the main thread before Room opens the DB
        SQLiteDatabase.loadLibs(this)

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
