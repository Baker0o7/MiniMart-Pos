package com.minimart.pos.printer

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed class DrawerResult {
    object Success : DrawerResult()
    data class Error(val msg: String) : DrawerResult()
}

@Singleton
class CashDrawerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val printer: ThermalPrinter
) {
    companion object {
        private const val TAG = "CashDrawerManager"
        private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        // ESC p PIN 0 — standard cash drawer kick
        private val KICK_PIN0 = byteArrayOf(0x1B, 0x70, 0x00, 0x19, 0xFA.toByte())
        // ESC p PIN 1 — secondary port
        private val KICK_PIN1 = byteArrayOf(0x1B, 0x70, 0x01, 0x19, 0xFA.toByte())
    }

    private var directSocket: BluetoothSocket? = null

    private val _isDrawerOpen   = MutableStateFlow(false)
    val isDrawerOpen: StateFlow<Boolean> = _isDrawerOpen

    private val _openCount      = MutableStateFlow(0)
    val openCount: StateFlow<Int> = _openCount

    /**
     * Open the cash drawer.
     * Strategy 1: Via the already-connected thermal printer (most common — drawer wired to printer RJ11)
     * Strategy 2: Via a separate Bluetooth drawer at [directAddress]
     */
    suspend fun openDrawer(directAddress: String? = null): DrawerResult = withContext(Dispatchers.IO) {
        try {
            // ── Strategy 1: via thermal printer ──────────────────────────────
            if (printer.isConnected) {
                printer.sendRaw(KICK_PIN0)
                _isDrawerOpen.value = true
                _openCount.value++
                Log.d(TAG, "Drawer opened via printer")
                return@withContext DrawerResult.Success
            }

            // ── Strategy 2: direct Bluetooth ─────────────────────────────────
            if (!directAddress.isNullOrBlank()) {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                    ?: return@withContext DrawerResult.Error("No Bluetooth adapter")
                val device = adapter.getRemoteDevice(directAddress)
                val sock = device.createRfcommSocketToServiceRecord(SPP_UUID)
                adapter.cancelDiscovery()
                sock.connect()
                directSocket = sock
                sock.outputStream.write(KICK_PIN0)
                sock.outputStream.flush()
                sock.outputStream.close()
                sock.close()
                directSocket = null
                _isDrawerOpen.value = true
                _openCount.value++
                Log.d(TAG, "Drawer opened via direct BT: $directAddress")
                return@withContext DrawerResult.Success
            }

            DrawerResult.Error("No printer connected and no drawer BT address configured")
        } catch (e: Exception) {
            Log.e(TAG, "Drawer open failed", e)
            DrawerResult.Error(e.localizedMessage ?: "Unknown error")
        }
    }

    /** Call after confirmed cash received to reset the open flag */
    fun acknowledgeClosed() { _isDrawerOpen.value = false }
}
