package com.minimart.pos.util

/** What the app remembers about the printer's recent behaviour: when it last worked and when it recently failed. */
data class PrinterRecord(val lastOkAt: Long = 0L, val failures: List<Long> = emptyList())

/**
 * A short memory of print results, so the health screen can say "failed 3 times today, last worked 5 hours ago"
 * instead of only reporting the one test the person happened to run. Kept small: the last week, at most [MAX] failures.
 */
object PrinterHistory {
    const val KEEP_MS = 7L * 24 * 60 * 60 * 1000
    const val MAX = 20
    private const val DAY = 24L * 60 * 60 * 1000

    fun onSuccess(r: PrinterRecord, now: Long) = r.copy(lastOkAt = now)
    fun onFailure(r: PrinterRecord, now: Long) = r.copy(failures = (r.failures + now).filter { now - it in 0..KEEP_MS }.takeLast(MAX))
    fun failuresInLastDay(r: PrinterRecord, now: Long) = r.failures.count { now - it in 0..DAY }
    fun hoursSinceOk(r: PrinterRecord, now: Long): Long? = if (r.lastOkAt <= 0L) null else ((now - r.lastOkAt).coerceAtLeast(0L)) / (60 * 60 * 1000)

    fun encode(r: PrinterRecord) = "${r.lastOkAt};${r.failures.joinToString(",")}"
    fun decode(s: String?): PrinterRecord {
        if (s.isNullOrBlank()) return PrinterRecord()
        val parts = s.split(';')
        return PrinterRecord(parts.getOrNull(0)?.toLongOrNull() ?: 0L,
            parts.getOrNull(1).orEmpty().split(',').mapNotNull { it.toLongOrNull() }.takeLast(MAX))
    }
}
