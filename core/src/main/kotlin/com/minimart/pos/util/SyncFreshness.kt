package com.minimart.pos.util

/** How current the data on a till phone is compared with the main phone. */
enum class Freshness { FRESH, AGING, STALE, NEVER }

data class FreshnessReport(
    val freshness: Freshness, val level: HealthLevel, val headline: String, val detail: String,
    /** True when prices, stock or customer balances on this phone may differ enough from the main phone to matter. */
    val warnAtCheckout: Boolean
)

/**
 * "Stale data" rules for a till that gets its prices, stock and customer balances from the main phone.
 * A till can keep selling offline, but the longer it has been since the last successful sync the more likely
 * a price changed, stock ran out or a customer paid their credit elsewhere. Pure so the thresholds are tested.
 */
object SyncFreshness {
    const val AGING_AFTER_HOURS = 12L
    const val STALE_AFTER_HOURS = 48L

    /** Null when this phone does not sync with another one (nothing can be stale). */
    fun assess(configured: Boolean, lastSyncAt: Long, now: Long, pending: Int = 0, openConflicts: Int = 0): FreshnessReport? {
        if (!configured) return null
        val conflictNote = if (openConflicts > 0) Lang.t(" $openConflicts disagreement(s) between phones were settled automatically; see the activity log.", " تمت تسوية $openConflicts اختلاف بين الهواتف تلقائيًا؛ راجع سجل النشاط.") else ""
        if (lastSyncAt <= 0L) return FreshnessReport(Freshness.NEVER, HealthLevel.WARN, Lang.t("Never synced", "لم تتم المزامنة قط"),
            Lang.t("This phone has not synced with the main phone yet, so its prices and stock may be out of date.", "لم يتزامن هذا الهاتف مع الهاتف الرئيسي بعد، لذا قد تكون أسعاره ومخزونه قديمة.") + conflictNote, true)
        val ageMs = (now - lastSyncAt).coerceAtLeast(0L)
        val hours = ageMs / HOUR
        val age = ageText(ageMs)
        return when {
            hours >= STALE_AFTER_HOURS -> FreshnessReport(Freshness.STALE, HealthLevel.BAD, Lang.t("Out of date: last synced $age ago", "قديمة: آخر مزامنة منذ $age"),
                Lang.t("Prices, stock and customer balances here may be wrong. Connect to the main phone's Wi-Fi and sync before relying on them.", "قد تكون الأسعار والمخزون وأرصدة العملاء هنا خاطئة. اتصل بواي فاي الهاتف الرئيسي وزامن قبل الاعتماد عليها.") + conflictNote, true)
            hours >= AGING_AFTER_HOURS -> FreshnessReport(Freshness.AGING, HealthLevel.WARN, Lang.t("Last synced $age ago", "آخر مزامنة منذ $age"),
                Lang.t("Still fine for selling. Sync when you are next near the main phone to pick up price and stock changes.", "ما زال مناسبًا للبيع. زامن عندما تكون قرب الهاتف الرئيسي لتلتقط تغييرات الأسعار والمخزون.") + conflictNote, false)
            else -> FreshnessReport(Freshness.FRESH, HealthLevel.GOOD, if (hours == 0L && ageMs < 5 * MIN) Lang.t("Just synced", "تمت المزامنة للتو") else Lang.t("Synced $age ago", "تمت المزامنة منذ $age"),
                (if (pending > 0) Lang.t("$pending sale(s) will go across on the next sync.", "سيتم إرسال $pending عملية بيع في المزامنة التالية.") else Lang.t("Up to date.", "محدّث.")) + conflictNote, false)
        }
    }

    fun ageText(ms: Long): String {
        val mins = ms / MIN; val hours = ms / HOUR; val days = ms / (24 * HOUR)
        if (Lang.arabic) return when {
            mins < 1 -> "أقل من دقيقة"
            mins < 60 -> arCount(mins, "دقيقة", "دقيقتين", "دقائق")
            hours < 48 -> arCount(hours, "ساعة", "ساعتين", "ساعات")
            else -> arCount(days, "يوم", "يومين", "أيام", "يومًا")
        }
        return when {
            mins < 1 -> "less than a minute"
            mins < 60 -> "$mins minute${if (mins == 1L) "" else "s"}"
            hours < 48 -> "$hours hour${if (hours == 1L) "" else "s"}"
            else -> "$days days"
        }
    }

    /** Arabic count phrase: one and two use their own forms, 3-10 take the plural, 11 and up the singular. */
    private fun arCount(n: Long, one: String, two: String, few: String, many: String = one): String = when {
        n == 1L -> one
        n == 2L -> two
        n in 3..10 -> "$n $few"
        else -> "$n $many"
    }

    private const val MIN = 60_000L
    private const val HOUR = 60 * MIN
}
