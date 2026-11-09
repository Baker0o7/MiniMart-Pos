package com.minimart.pos.util

/**
 * Decides when to keep a safety copy of the shop database: once, just before the first launch of a newer app
 * version (that is when a database upgrade runs). Pure so the rule is tested; the file copying lives in `:app`.
 */
object UpgradeSafety {
    /** Copy only for a real upgrade of an existing database, and only when there is room for it twice over. */
    fun shouldCopy(lastRunVersion: Int, currentVersion: Int, dbBytes: Long, freeBytes: Long): Boolean =
        lastRunVersion in 1 until currentVersion && dbBytes > 0L && freeBytes > dbBytes * 2

    /** Plain-language line for the health screen. */
    fun describe(copiedFromVersion: Int?, bytes: Long): String = when {
        copiedFromVersion == null -> Lang.t("No upgrade copy yet.", "لا توجد نسخة ترقية بعد.")
        else -> Lang.t(
            "A copy of your data from before the update to this version is kept on this phone (${BackupAdvice.humanSize(bytes)}).",
            "نسخة من بياناتك قبل التحديث إلى هذا الإصدار محفوظة على هذا الهاتف (${BackupAdvice.humanSize(bytes)})."
        )
    }
}
