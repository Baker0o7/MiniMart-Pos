package com.minimart.pos.scanner

import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Most cheap USB/Bluetooth barcode scanners act as a HID keyboard:
 * they send character keypresses followed by ENTER (keyCode 66).
 *
 * Usage:
 *   1. In MainActivity.onKeyDown, forward the event to [onKeyDown].
 *   2. Collect [barcodeFlow] in your ViewModel/Composable.
 */
@Singleton
class KeyboardScanner @Inject constructor() {

    private val scan = ScanBuffer()

    // Emits a complete barcode string whenever ENTER is received
    private val _barcodeFlow = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val barcodeFlow: SharedFlow<String> = _barcodeFlow.asSharedFlow()

    /**
     * Forward Activity.onKeyDown events here.
     * Returns true if the event was consumed (scanner input), false otherwise.
     */
    fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val now = System.currentTimeMillis()
        val step = when {
            keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER -> scan.enter(now)
            event.isPrintingKey -> scan.printable(event.unicodeChar.toChar(), now)
            else -> scan.other(now)
        }
        step.barcode?.let { _barcodeFlow.tryEmit(it) }
        return step.consumed
    }

    fun clearBuffer() = scan.clear()
}
