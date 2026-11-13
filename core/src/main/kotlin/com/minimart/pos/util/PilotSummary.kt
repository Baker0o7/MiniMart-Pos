package com.minimart.pos.util

/**
 * The short daily message a pilot shop sends to whoever is running the pilot: how the day went and what Store health
 * found. Plain English on purpose (it is read by the person supporting the shop, not by the shopkeeper), one line per
 * check, no customer names and no amounts other than today's total.
 */
object PilotSummary {
    private fun oneLine(s: String, max: Int = 160): String =
        s.filter { it >= ' ' && it != '\u007f' }.replace(Regex("\\s+"), " ").trim().take(max)

    private fun tag(level: HealthLevel) = when (level) { HealthLevel.GOOD -> "OK"; HealthLevel.WARN -> "CHECK"; HealthLevel.BAD -> "PROBLEM" }

    fun format(
        shop: String, dateText: String, appVersion: String, headline: String, checks: List<HealthCheck>,
        salesToday: Int, revenueToday: String, crashesThisWeek: Int
    ): String = buildString {
        appendLine("MiniMart POS pilot summary")
        appendLine("Shop: ${oneLine(shop, 60).ifBlank { "(no name)" }}")
        appendLine("Date: ${oneLine(dateText, 40)}")
        appendLine("App: ${oneLine(appVersion, 30)}")
        appendLine()
        appendLine("Today: $salesToday sale(s), ${oneLine(revenueToday, 30)}")
        appendLine("Overall: ${oneLine(headline)}")
        appendLine("Crash reports this week: $crashesThisWeek")
        appendLine()
        checks.forEach { appendLine("[${tag(it.level)}] ${oneLine(it.title, 60)}: ${oneLine(it.detail)}") }
    }.trimEnd()
}
