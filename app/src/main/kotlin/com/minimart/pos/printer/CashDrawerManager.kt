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

        // ESC p m t1 t2 — standard RJ11 drawer kick
        // m=0: pin 2, m=1: pin 5
        // t1=50ms on, t2=250ms off (recommended for most drawers)
        fun kickCommand(pin: Int = 0): ByteArray = byteArrayOf(
            0x1B, 0x70,
            pin.toByte(),
            0x32,          // 50ms  on  (0x32 = 50 × 2ms)
            0xFA.toByte()  // 250ms off (0xFA = 250 × 1ms)
        )
    }

    private val _openCount = MutableStateFlow(0)
    val openCount: StateFlow<Int> = _openCount

    /**
     * Open the cash drawer.
     * Priority:
     *   1. Via already-connected thermal printer (RJ11 wired to printer)
     *   2. Via a separate Bluetooth drawer at [directAddress]
     */
    suspend fun openDrawer(directAddress: String? = null, pin: Int = 0): DrawerResult =
        withContext(Dispatchers.IO) {
            try {
                // Strategy 1 — via thermal printer (most common setup)
                if (printer.isConnected) {
                    val result = printer.sendRaw(kickCommand(pin))
                    if (result is PrintResult.Success) {
                        _openCount.value++
                        Log.d(TAG, "Drawer opened via printer (pin=$pin)")
                        return@withContext DrawerResult.Success
                    }
                }

                // Strategy 2 — direct Bluetooth RFCOMM
                if (!directAddress.isNullOrBlank()) {
                    val adapter = BluetoothAdapter.getDefaultAdapter()
                        ?: return@withContext DrawerResult.Error("No Bluetooth adapter")
                    @Suppress("DEPRECATION")
                    val device = adapter.getRemoteDevice(directAddress)
                    val sock: BluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                    adapter.cancelDiscovery()
                    sock.connect()
                    sock.outputStream.apply {
                        write(kickCommand(pin))
                        flush()
                    }
                    sock.close()
                    _openCount.value++
                    Log.d(TAG, "Drawer opened via BT: $directAddress (pin=$pin)")
                    return@withContext DrawerResult.Success
                }

                DrawerResult.Error("No printer connected and no BT drawer address configured")
            } catch (e: Exception) {
                Log.e(TAG, "Drawer open failed", e)
                DrawerResult.Error(e.localizedMessage ?: "Unknown error")
            }
        }
}

