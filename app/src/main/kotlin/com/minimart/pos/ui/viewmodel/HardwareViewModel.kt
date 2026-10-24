package com.minimart.pos.ui.viewmodel

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.StatFs
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimart.pos.data.repository.SettingsRepository
import com.minimart.pos.printer.CashDrawerManager
import com.minimart.pos.printer.DrawerResult
import com.minimart.pos.printer.PrintResult
import com.minimart.pos.printer.ThermalPrinter
import com.minimart.pos.util.HardwareDiagnostics
import com.minimart.pos.util.HardwareInputs
import com.minimart.pos.util.HealthCheck
import com.minimart.pos.util.HealthLevel
import com.minimart.pos.util.TestOutcome
import com.minimart.pos.util.hasBluetoothConnectPermission
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class HardwareState(
    val loading: Boolean = true,
    val checks: List<HealthCheck> = emptyList(),
    val overall: HealthLevel = HealthLevel.GOOD,
    val headline: String = "",
    val bluetoothOff: Boolean = false,
    /** Which test is running right now ("printer" or "drawer"), so its button can show progress. */
    val testing: String? = null
)

/** Reads the phone's hardware state and runs the printer and drawer tests; [HardwareDiagnostics] judges the facts. */
@HiltViewModel
class HardwareViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val printer: ThermalPrinter,
    private val drawer: CashDrawerManager
) : ViewModel() {
    private val _state = MutableStateFlow(HardwareState())
    val state: StateFlow<HardwareState> = _state.asStateFlow()
    private var printerTest: TestOutcome? = null
    private var drawerTest: TestOutcome? = null

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.checks.isEmpty()) }
            val inputs = withContext(Dispatchers.IO) { gather() }
            val checks = HardwareDiagnostics.evaluate(inputs)
            _state.update {
                it.copy(loading = false, checks = checks, overall = HardwareDiagnostics.overall(checks),
                    headline = HardwareDiagnostics.headline(checks), bluetoothOff = inputs.bluetoothEnabled == false)
            }
        }
    }

    fun testPrinter() = runTest("printer") {
        val address = settings.printerAddress.first()
        printerTest = when {
            address.isNullOrBlank() -> TestOutcome.Failed("No printer chosen in Settings.")
            else -> {
                val connected = if (printer.isConnected) PrintResult.Success else printer.connect(address)
                val result = if (connected is PrintResult.Success) printer.printPlainText(testPage()) else connected
                when (result) {
                    is PrintResult.Success -> TestOutcome.Passed
                    is PrintResult.Error -> TestOutcome.Failed(result.message)
                }
            }
        }
    }

    fun testDrawer() = runTest("drawer") {
        // The kick signal goes through the printer, so make sure it is connected first.
        val address = settings.printerAddress.first()
        if (!printer.isConnected && !address.isNullOrBlank()) printer.connect(address)
        drawerTest = when (val r = drawer.openDrawer()) {
            is DrawerResult.Success -> TestOutcome.Passed
            is DrawerResult.Error -> TestOutcome.Failed(r.msg)
        }
    }

    private fun runTest(name: String, body: suspend () -> Unit) {
        if (_state.value.testing != null) return
        viewModelScope.launch {
            _state.update { it.copy(testing = name) }
            try { body() } catch (e: Exception) {
                val failed = TestOutcome.Failed(com.minimart.pos.util.ErrorAdvice.explain(e, "The test"))
                if (name == "printer") printerTest = failed else drawerTest = failed
            }
            _state.update { it.copy(testing = null) }
            refresh()
        }
    }

    private fun testPage(): String {
        val stamp = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date())
        return "MiniMart POS\nPrinter test\n$stamp\n--------------------------------\n" +
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ\nabcdefghijklmnopqrstuvwxyz\n0123456789 !@#%&*()\n" +
            "--------------------------------\nIf you can read this,\nprinting works."
    }

    private suspend fun gather(): HardwareInputs {
        val pm = context.packageManager
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bm?.adapter
        val btPermission = context.hasBluetoothConnectPermission()
        val address = runCatching { settings.printerAddress.first() }.getOrNull()
        val paired: Boolean? = if (address.isNullOrBlank() || adapter == null || !btPermission) null else try {
            adapter.bondedDevices.any { it.address.equals(address, ignoreCase = true) }
        } catch (_: SecurityException) { null }

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val caps = cm?.let { c -> c.activeNetwork?.let { c.getNetworkCapabilities(it) } }
        val connected: Boolean? = if (cm == null) null else caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val wifi: Boolean? = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)

        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else null
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val cfg = context.resources.configuration
        val notifications = NotificationManagerCompat.from(context).areNotificationsEnabled()

        return HardwareInputs(
            bluetoothSupported = adapter != null,
            bluetoothEnabled = adapter?.isEnabled,
            bluetoothPermission = btPermission,
            printerChosen = !address.isNullOrBlank(),
            printerName = runCatching { settings.printerName.first() }.getOrNull(),
            printerPaired = paired,
            printerTest = printerTest,
            drawerConfigured = runCatching { settings.cashDrawerAddress.first().isNotBlank() }.getOrDefault(false),
            drawerTest = drawerTest,
            cameraPresent = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY),
            cameraPermission = context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
            flashPresent = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH),
            smsTrackingOn = runCatching { settings.mpesaSmsTracking.first() }.getOrDefault(false),
            smsPermission = context.checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED,
            notificationsAllowed = notifications,
            networkConnected = connected,
            onWifi = wifi,
            freeStorageMb = runCatching { StatFs(context.filesDir.path).availableBytes / (1024 * 1024) }.getOrNull(),
            batteryPercent = pct,
            charging = charging,
            fontScale = cfg.fontScale,
            screenWidthDp = cfg.screenWidthDp
        )
    }
}
