package com.minimart.pos.scanner

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Merges ML Kit camera scan events AND keyboard (HID) scanner events
 * into a single flow that ViewModels can collect.
 */
@Singleton
class ScannerManager @Inject constructor(
    val keyboardScanner: KeyboardScanner
) {
    // ML Kit scans are emitted from the UI composable via onBarcodeDetected callback.
    // ViewModels call processBarcode() directly for camera scans.
    // For keyboard scans, collect keyboardScanner.barcodeFlow.
}
