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
    // An IP address with an optional port, a version number (1.32.6) or a plain number with separators.
    private val RUN = Regex("(?<![A-Za-z])(?:[0-9]+(?:\\.[0-9]+){3}(?::[0-9]+)?|[0-9]+(?:\\.[0-9]+){2,}|[0-9]+(?:[,.][0-9]+)*)(?![A-Za-z0-9])")

    /**
     * Left as written: addresses and versions (someone has to type them into another phone), and zero on its own,
     * because the Arabic-Indic zero is a small dot that reads as a stray bullet next to a word ("٠ payments", "KES ٠٫٠٠").
     */
    private fun keepAsWritten(run: String): Boolean =
        run.count { it == '.' } >= 2 || ':' in run || run.all { it == '0' || it == ',' || it == '.' }

    fun show(text: String): String {
        if (!Lang.arabic || text.none { it in '0'..'9' }) return text
        return RUN.replace(text) { m ->
            if (keepAsWritten(m.value)) return@replace m.value
            buildString(m.value.length) {
                m.value.forEach { c ->
                    append(when (c) { in '0'..'9' -> '٠' + (c - '0'); ',' -> '٬'; '.' -> '٫'; else -> c })
                }
            }
        }
    }
}

/**
 * "no payments / one payment / two payments / 3 payments / 11 payment": Arabic changes the noun with the number,
 * so a count shown next to a word has to pick the right form instead of gluing a digit to a plural.
 */
object ArabicCount {
    fun phrase(n: Int, none: String, one: String, two: String, few: String, many: String): String = when {
        n <= 0 -> none
        n == 1 -> one
        n == 2 -> two
        n in 3..10 -> "$n $few"
        else -> "$n $many"
    }

    /** English "1 sale" / "3 sales", or the Arabic form above. */
    fun t(n: Int, enOne: String, enMany: String, none: String, one: String, two: String, few: String, many: String): String =
        if (Lang.arabic) phrase(n, none, one, two, few, many) else "$n ${if (n == 1) enOne else enMany}"
}

/** "12 AM", "6 PM" (Arabic: "12 ص", "6 م") for an hour 0-23, so every chart and summary names hours the same way. */
object HourLabel {
    fun of(hour: Int): String {
        val h = ((hour % 24) + 24) % 24
        val h12 = if (h % 12 == 0) 12 else h % 12
        return "$h12 " + if (h < 12) Lang.t("AM", "ص") else Lang.t("PM", "م")
    }
}
