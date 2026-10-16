package com.minimart.pos.printer

import java.text.Normalizer

/**
 * Turns any text into what a basic ESC/POS thermal printer can print: plain ASCII.
 *
 * These printers default to a single-byte code page, so UTF-8 text (an accented customer name, "×",
 * "•", curly quotes) comes out as garbage. And because the printer reads control bytes as commands,
 * a product or customer name containing one (typed, imported or scanned from a QR code) must never be
 * sent through untouched. Newlines are kept; every other control character is dropped.
 */
object PrinterText {
    private val replacements = mapOf(
        '×' to "x", '•' to "-", '·' to "-", '–' to "-", '—' to "-", '‘' to "'", '’' to "'",
        '“' to "\"", '”' to "\"", '…' to "...", '€' to "EUR", '£' to "GBP", '°' to "o",
        '✓' to "v", '→' to "->", '←' to "<-", ' ' to " ", 'ß' to "ss", 'æ' to "ae", 'Æ' to "AE",
        'ø' to "o", 'Ø' to "O", 'œ' to "oe", 'Œ' to "OE", 'đ' to "d", 'Đ' to "D", 'ł' to "l", 'Ł' to "L"
    )

    fun ascii(s: String): String {
        val sb = StringBuilder(s.length)
        // Split accents off letters (é -> e + ´), then drop the accent marks.
        for (ch in Normalizer.normalize(s, Normalizer.Form.NFD)) {
            when {
                ch == '\n' -> sb.append(ch)
                ch.isISOControl() -> {}
                ch.code in 0x20..0x7E -> sb.append(ch)
                Character.getType(ch) == Character.NON_SPACING_MARK.toInt() -> {}
                else -> sb.append(replacements[ch] ?: "?")
            }
        }
        return sb.toString()
    }
}
