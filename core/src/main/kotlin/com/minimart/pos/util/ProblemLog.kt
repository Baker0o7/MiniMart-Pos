package com.minimart.pos.util

/** One kind of problem the app handled without crashing, with how often and when it last happened. */
data class ProblemEntry(val lastAt: Long, val doing: String, val kind: String, val detail: String, val count: Int)

/**
 * The on-phone problem log, kept free of Android so it can be tested. Why: the shop owner sees a friendly
 * message when something fails, and this keeps the technical side (what, how often, when) so a support
 * conversation does not start from "it didn't work". It holds no sale, customer or secret data: long digit
 * runs (phone numbers, M-Pesa amounts, codes) and e-mail addresses are masked before anything is stored.
 */
object ProblemLog {
    const val MAX_ENTRIES = 60
    private const val MAX_DETAIL = 140
    private val digitRun = Regex("\\d{6,}")
    private val email = Regex("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+")
    private val code = Regex("\\b(?=[A-Z0-9]*\\d)(?=[A-Z0-9]*[A-Z])[A-Z0-9]{8,20}\\b")

    /** Problems the person caused or the app already explains in words (a rule was broken, a task was cancelled). */
    fun isExpected(error: Throwable): Boolean =
        error is java.util.concurrent.CancellationException ||
            ((error is IllegalStateException || error is IllegalArgumentException) && !error.message.isNullOrBlank())

    fun scrub(text: String): String =
        text.replace(email, "<email>").replace(code, "<code>").replace(digitRun, "<digits>")
            .filter { it.code in 32..126 }.replace('\t', ' ').trim().take(MAX_DETAIL)

    fun entryFor(error: Throwable, doing: String, now: Long): ProblemEntry = ProblemEntry(
        lastAt = now,
        doing = scrub(doing).ifBlank { "unknown" }.take(60),
        kind = error::class.java.simpleName.ifBlank { "Error" },
        detail = scrub(error.message.orEmpty()),
        count = 1
    )

    /** Adds [e]; the same problem again only bumps its count and time. The list stays newest-first and capped. */
    fun add(list: List<ProblemEntry>, e: ProblemEntry): List<ProblemEntry> {
        val same = list.firstOrNull { it.doing == e.doing && it.kind == e.kind && it.detail == e.detail }
        val merged = if (same == null) e else same.copy(lastAt = e.lastAt, count = same.count + 1)
        return (listOf(merged) + list.filter { it !== same }).take(MAX_ENTRIES)
    }

    /** Number of distinct problems seen in the last [days] days. */
    fun recent(list: List<ProblemEntry>, now: Long, days: Int = 7): Int =
        list.count { it.lastAt >= now - days * 24L * 60 * 60 * 1000 }

    fun encode(list: List<ProblemEntry>): String =
        list.joinToString("\n") { "${it.lastAt}\t${it.count}\t${it.doing}\t${it.kind}\t${it.detail}" }

    /** Reads what [encode] wrote. Lines that are damaged are skipped, never fatal. */
    fun decode(text: String): List<ProblemEntry> = text.lineSequence().mapNotNull { line ->
        val p = line.split('\t', limit = 5)
        if (p.size < 5) return@mapNotNull null
        val at = p[0].toLongOrNull() ?: return@mapNotNull null
        val n = p[1].toIntOrNull()?.coerceAtLeast(1) ?: return@mapNotNull null
        ProblemEntry(at, p[2], p[3], p[4], n)
    }.take(MAX_ENTRIES).toList()

    /** Text an owner can paste or share with whoever is helping. */
    fun report(list: List<ProblemEntry>, header: String, fmt: (Long) -> String): String = buildString {
        appendLine(header)
        appendLine("Problems the app handled (newest first). No sales, customer or payment details are kept here.")
        appendLine()
        if (list.isEmpty()) append("None recorded.")
        list.forEach { appendLine("${fmt(it.lastAt)}  ${it.doing}: ${it.kind}${if (it.detail.isNotBlank()) " - ${it.detail}" else ""}${if (it.count > 1) "  (x${it.count})" else ""}") }
    }.trimEnd()
}
