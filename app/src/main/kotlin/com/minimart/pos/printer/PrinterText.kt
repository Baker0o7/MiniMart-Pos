package com.minimart.pos.printer

import java.text.Normalizer

/**
 * Turns any text into what a basic ESC/POS thermal printer can print: plain ASCII.
 *
 * These printers default to a single-byte code page, so UTF-8 text (an accented customer name, "×",
 * "•", curly quotes) comes out as garbage. And because the printer reads control bytes as commands,
 * a product or customer name containing one (typed, imported or scanned from a QR code) must never be
 * sent through untouched. Newlines are kept; every other control character is dropped.
 *
 * Arabic letters cannot be printed by these printers either, so an Arabic product or customer name is written in
 * Latin letters (a rough sound-alike, "حليب" -> "hlyb") and Arabic-Indic digits become 0-9, instead of a row of "?".
 */
object PrinterText {
    private val replacements = mapOf(
        '×' to "x", '•' to "-", '·' to "-", '–' to "-", '—' to "-", '‘' to "'", '’' to "'",
        '“' to "\"", '”' to "\"", '…' to "...", '€' to "EUR", '£' to "GBP", '°' to "o",
        '✓' to "v", '→' to "->", '←' to "<-", ' ' to " ", 'ß' to "ss", 'æ' to "ae", 'Æ' to "AE",
        'ø' to "o", 'Ø' to "O", 'œ' to "oe", 'Œ' to "OE", 'đ' to "d", 'Đ' to "D", 'ł' to "l", 'Ł' to "L"
    )

    private val arabic = mapOf(
        'ا' to "a", 'أ' to "a", 'إ' to "i", 'آ' to "aa", 'ء' to "'", 'ؤ' to "w", 'ئ' to "y", 'ب' to "b", 'ت' to "t", 'ث' to "th",
        'ج' to "j", 'ح' to "h", 'خ' to "kh", 'د' to "d", 'ذ' to "dh", 'ر' to "r", 'ز' to "z", 'س' to "s", 'ش' to "sh",
        'ص' to "s", 'ض' to "d", 'ط' to "t", 'ظ' to "z", 'ع' to "a", 'غ' to "gh", 'ف' to "f", 'ق' to "q", 'ك' to "k",
        'ل' to "l", 'م' to "m", 'ن' to "n", 'ه' to "h", 'ة' to "a", 'و' to "w", 'ى' to "a", 'ي' to "y", 'پ' to "p", 'گ' to "g",
        'ـ' to "", '،' to ",", '؟' to "?", '؛' to ";"
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
                ch in '\u0660'..'\u0669' -> sb.append('0' + (ch - '\u0660'))
                ch in '\u06F0'..'\u06F9' -> sb.append('0' + (ch - '\u06F0'))
                else -> sb.append(replacements[ch] ?: arabic[ch] ?: "?")
            }
        }
        return sb.toString()
    }
}
