package com.minimart.pos.scanner

/** What one key press did: whether the scanner logic took it, and the finished barcode if it completed one. */
data class ScanStep(val consumed: Boolean, val barcode: String? = null)

/**
 * The timing and filtering rules for a scanner that types like a keyboard, kept free of Android so they can be
 * tested. A scanner sends a whole code in a few milliseconds and ends it with Enter. A person typing is slow,
 * so a pause of more than [maxGapMs] throws away a half-typed code; letters, digits and '-' are kept and
 * anything else is ignored; and codes shorter than [minLength] are dropped as noise.
 */
class ScanBuffer(
    private val maxGapMs: Long = 300L,
    private val minLength: Int = 3,
    private val maxLength: Int = BarcodeNormalizer.MAX_LENGTH
) {
    private val buffer = StringBuilder()
    private var lastKeyAt = 0L

    private fun tick(now: Long) {
        if (now - lastKeyAt > maxGapMs && buffer.isNotEmpty()) buffer.clear()
        lastKeyAt = now
    }

    fun enter(now: Long): ScanStep {
        tick(now)
        if (buffer.isEmpty()) return ScanStep(consumed = false)
        val code = buffer.toString().trim()
        buffer.clear()
        return ScanStep(consumed = true, barcode = code.takeIf { it.length >= minLength })
    }

    fun printable(ch: Char, now: Long): ScanStep {
        tick(now)
        if ((ch.isLetterOrDigit() || ch == '-') && buffer.length < maxLength) buffer.append(ch)
        return ScanStep(consumed = true)
    }

    /** Any other key (arrows, shift, volume): not ours, but time still passes. */
    fun other(now: Long): ScanStep { tick(now); return ScanStep(consumed = false) }

    fun clear() = buffer.clear()
}
