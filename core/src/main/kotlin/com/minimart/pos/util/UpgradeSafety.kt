package com.minimart.pos.util

/**
 * Decides when to keep a safety copy of the shop database: once, just before the first launch of a newer app
 * version (that is when a database upgrade runs). Pure so the rule is tested; the file copying lives in `:app`.
 */
object UpgradeSafety {
    /**
     * Copy only for a real upgrade of an existing database, and only when there is room for it twice over.
     * [lastRunVersion] 0 with a database present means an app from before this safety existed: also worth a copy.
     */
    fun shouldCopy(lastRunVersion: Int, currentVersion: Int, dbBytes: Long, freeBytes: Long): Boolean =
        lastRunVersion in 0 until currentVersion && dbBytes > 0L && freeBytes > dbBytes * 2
}
