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

    private val buffer = StringBuilder()
    private var lastKeyTime = 0L

    // Emits a complete barcode string whenever ENTER is received
    private val _barcodeFlow = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val barcodeFlow: SharedFlow<String> = _barcodeFlow.asSharedFlow()

    /**
     * Forward Activity.onKeyDown events here.
     * Returns true if the event was consumed (scanner input), false otherwise.
     */
    fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val now = System.currentTimeMillis()

        // If gap > 300ms assume new barcode, clear buffer
        if (now - lastKeyTime > 300L && buffer.isNotEmpty()) buffer.clear()
        lastKeyTime = now

        return when {
            keyCode == KeyEvent.KEYCODE_ENTER && buffer.isNotEmpty() -> {
                val barcode = buffer.toString().trim()
                buffer.clear()
                if (barcode.length >= 3) _barcodeFlow.tryEmit(barcode)
                true
            }
            event.isPrintingKey -> {
                val char = event.unicodeChar.toChar()
                if (char.isLetterOrDigit() || char == '-') buffer.append(char)
                true
            }
            else -> false
        }
    }

    fun clearBuffer() = buffer.clear()
}
