package com.minimart.pos.util

/** Facts a person can paste into a support message. Contains nothing about sales, customers or secrets. */
data class SupportFacts(
    val appVersion: String,
    val versionCode: Int,
    val androidVersion: String,
    val sdk: Int,
    val device: String,
    val locale: String,
    val freeStorageMb: Long?,
    val crashReportsThisWeek: Int,
    val lastBackupDays: Int?,
    val syncOn: Boolean,
    val cloudOn: Boolean,
    val instantMpesaOn: Boolean
)

object SupportInfo {
    const val REPO_URL = "https://github.com/Baker0o7/MiniMart-Pos"
    const val ISSUES_URL = "$REPO_URL/issues"

    /** Keeps only printable characters and a sane length, so a strange device name cannot break the text. */
    fun clean(s: String, max: Int = 60): String = s.filter { it.code in 32..126 }.trim().take(max).ifBlank { "unknown" }

    fun format(f: SupportFacts): String = buildString {
        appendLine("MiniMart POS ${clean(f.appVersion)} (build ${f.versionCode})")
        appendLine("Android ${clean(f.androidVersion)} (API ${f.sdk})")
        appendLine("Device: ${clean(f.device)}")
        appendLine("Language: ${clean(f.locale, 20)}")
        appendLine("Free storage: ${f.freeStorageMb?.let { "$it MB" } ?: "unknown"}")
        appendLine("Crash reports this week: ${f.crashReportsThisWeek}")
        appendLine("Last backup: ${f.lastBackupDays?.let { if (it == 0) "today" else "$it day(s) ago" } ?: "none found"}")
        append("Features on: ")
        append(listOfNotNull("sync".takeIf { f.syncOn }, "cloud dashboard".takeIf { f.cloudOn }, "instant M-Pesa".takeIf { f.instantMpesaOn })
            .ifEmpty { listOf("none") }.joinToString(", "))
    }
}
