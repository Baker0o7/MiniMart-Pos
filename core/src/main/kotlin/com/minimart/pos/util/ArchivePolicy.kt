package com.minimart.pos.util

import java.time.Instant
import java.time.ZoneId

/** What archiving would do, shown to the owner before anything is deleted. */
data class ArchivePlan(
    val cutoffMs: Long,
    val sales: Int,
    val items: Int,
    val oldestMs: Long?,
    val newestMs: Long?
) {
    val isEmpty: Boolean get() = sales == 0
}

/**
 * Rules for moving old sales out of the live database. Sales are the shop's financial record, so the rules are
 * deliberately conservative: nothing under a year old is ever archived, and old sales are first written to a file
 * the owner keeps. Pure, so it is unit-tested.
 */
object ArchivePolicy {
    const val MIN_MONTHS = 12
    const val DEFAULT_MONTHS = 24
    const val MAX_MONTHS = 120

    /** Synced change records are only needed until the other phone has them. */
    const val SYNC_LOG_KEEP_DAYS = 30

    fun clampMonths(months: Int): Int = months.coerceIn(MIN_MONTHS, MAX_MONTHS)

    /** Start of the day [months] months before [nowMs] in [zone]; sales created before this are old enough to archive. */
    fun cutoff(nowMs: Long, months: Int, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
            .minusMonths(clampMonths(months).toLong()).atStartOfDay(zone).toInstant().toEpochMilli()

    fun syncLogCutoff(nowMs: Long): Long = nowMs - SYNC_LOG_KEEP_DAYS * 24L * 60 * 60 * 1000

    val CSV_HEADER = listOf(
        "receipt", "date", "status", "payment", "mpesa_ref", "cashier_id", "subtotal", "tax", "discount",
        "total", "amount_paid", "cash_portion", "notes",
        "item_barcode", "item_name", "quantity", "weight_kg", "unit_price", "item_discount", "item_tax", "line_total", "item_cost"
    ).joinToString(",")

    /** One CSV field: quoted when it holds a comma, quote or line break; a leading = + - @ is defused so a spreadsheet never runs it as a formula. */
    fun csvField(value: String): String {
        var v = value
        if (v.isNotEmpty() && v[0] in "=+-@\t\r" && v.toDoubleOrNull() == null) v = "'$v"
        return if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
    }

    fun csvRow(fields: List<String>): String = fields.joinToString(",") { csvField(it) }

    fun fileName(nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val d = Instant.ofEpochMilli(nowMs).atZone(zone)
        return String.format(java.util.Locale.ROOT, "minimart-sales-archive-%04d%02d%02d-%02d%02d.csv.gz", d.year, d.monthValue, d.dayOfMonth, d.hour, d.minute)
    }

    fun describe(plan: ArchivePlan, zone: ZoneId = ZoneId.systemDefault()): String {
        if (plan.isEmpty) return "Nothing to archive yet. Sales stay on the phone until they are over a year old."
        fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toString()
        return "${plan.sales} sale(s) with ${plan.items} item line(s), from ${day(plan.oldestMs!!)} to ${day(plan.newestMs!!)}, can be archived."
    }
}
