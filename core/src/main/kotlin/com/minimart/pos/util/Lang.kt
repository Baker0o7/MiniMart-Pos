package com.minimart.pos.util

/**
 * Which language the plain-language messages built inside `:core` should use. `:core` has no Android
 * resources, so the app sets [arabic] at start-up from the language the owner picked; English stays the
 * default so every existing test and log line reads the same as before.
 */
object Lang {
    @Volatile var arabic: Boolean = false

    /** The English text, or the Arabic text when the app is running in Arabic. */
    fun t(en: String, ar: String): String = if (arabic) ar else en
}

/**
 * Shows Western digits as Arabic-Indic digits (0 1 2 → ٠ ١ ٢) with Arabic separators, for text on screen only.
 * Why not everywhere: codes, barcodes and typed numbers must stay as they are so they can be scanned, matched and
 * parsed, so a run of digits that touches a Latin letter (an M-Pesa code, "5kg") is left alone, and input fields are
 * never touched. Does nothing unless [Lang.arabic] is on.
 */
object ArabicDigits {
    private val RUN = Regex("(?<![A-Za-z])[0-9]+(?:[,.][0-9]+)*(?![A-Za-z0-9])")

    fun show(text: String): String {
        if (!Lang.arabic || text.none { it in '0'..'9' }) return text
        return RUN.replace(text) { m ->
            buildString(m.value.length) {
                m.value.forEach { c ->
                    append(when (c) { in '0'..'9' -> '٠' + (c - '0'); ',' -> '٬'; '.' -> '٫'; else -> c })
                }
            }
        }
    }
}
