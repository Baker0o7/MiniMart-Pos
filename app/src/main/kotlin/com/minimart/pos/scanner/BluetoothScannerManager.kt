package com.minimart.pos.scanner

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class BtScannerState { DISCONNECTED, CONNECTING, CONNECTED }

data class BtScannerInfo(
    val name: String,
    val address: String,
    val state: BtScannerState = BtScannerState.DISCONNECTED
)

/**
 * Manages Bluetooth HID barcode scanner connection.
 *
 * Most BT scanners pair as HID keyboards (SPP profile) and inject
 * key events directly into the focused view — handled by KeyboardScanner.
 *
 * This class tracks paired scanners, their connection state, and
 * exposes live state to the UI for status display.
 */
@Singleton
class BluetoothScannerManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "BTScannerManager"
        // Common BT scanner name prefixes
        private val SCANNER_PREFIXES = listOf(
            "scanner", "barcode", "honeywell", "zebra", "datalogic",
            "symbol", "intermec", "unitec", "newland", "sunmi", "urovo"
        )
    }

    private val adapter = @Suppress("DEPRECATION") BluetoothAdapter.getDefaultAdapter()

    private val _scannerInfo = MutableStateFlow<BtScannerInfo?>(null)
    val scannerInfo: StateFlow<BtScannerInfo?> = _scannerInfo

    private val _pairedScanners = MutableStateFlow<List<BtScannerInfo>>(emptyList())
    val pairedScanners: StateFlow<List<BtScannerInfo>> = _pairedScanners

    private val connectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                ?: return
            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    Log.d(TAG, "BT Connected: ${device.name}")
                    if (isScanner(device)) {
                        _scannerInfo.value = BtScannerInfo(
                            device.name ?: "Scanner", device.address, BtScannerState.CONNECTED
                        )
                        refreshPaired()
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    Log.d(TAG, "BT Disconnected: ${device.name}")
                    if (_scannerInfo.value?.address == device.address) {
                        _scannerInfo.value = _scannerInfo.value?.copy(state = BtScannerState.DISCONNECTED)
                    }
                    refreshPaired()
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> refreshPaired()
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        try {
            context.registerReceiver(connectionReceiver, filter)
            refreshPaired()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register BT receiver", e)
        }
    }

    fun refreshPaired() {
        try {
            val bonded = adapter?.bondedDevices ?: return
            val scanners = bonded
                .filter { isScanner(it) }
                .map { BtScannerInfo(it.name ?: it.address, it.address, BtScannerState.DISCONNECTED) }
            _pairedScanners.value = scanners
            // Keep current connected state if still paired
            val current = _scannerInfo.value
            if (current != null && scanners.none { it.address == current.address }) {
                _scannerInfo.value = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing paired devices", e)
        }
    }

    fun getPairedDevices(): List<BluetoothDevice> {
        return try {
            adapter?.bondedDevices?.toList() ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    private fun isScanner(device: BluetoothDevice): Boolean {
        val name = (device.name ?: "").lowercase()
        // HID device class = 0x0500 (Peripheral), major = 5
        val isHid = device.bluetoothClass?.majorDeviceClass == 0x0500
        val nameMatch = SCANNER_PREFIXES.any { name.contains(it) }
        return isHid || nameMatch
    }
}
