package com.minimart.pos.scanner

/**
 * One place that decides whether text coming from a scanner (camera or keyboard/Bluetooth) is a
 * usable barcode. A QR code can hold thousands of characters and a hostile or damaged one can
 * hold control characters; none of that should reach database lookups, error banners or receipts.
 */
object BarcodeNormalizer {
    const val MAX_LENGTH = 64

    /** The cleaned barcode, or null when [raw] is blank, too long or contains control characters. */
    fun normalize(raw: String?): String? {
        val s = raw?.trim() ?: return null
        if (s.isEmpty() || s.length > MAX_LENGTH) return null
        if (s.any { it.isISOControl() }) return null
        return s
    }
}
