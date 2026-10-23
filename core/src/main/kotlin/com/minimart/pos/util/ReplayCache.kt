package com.minimart.pos.util

import java.io.File

/**
 * Remembers signed requests already accepted so a captured request cannot be sent again.
 * Entries live only as long as the clock-skew window, and the set can be saved to disk so a
 * restart does not open a fresh replay window. Kept free of Android so it can be tested.
 */
class ReplayCache(private val maxEntries: Int = 5_000, private val file: File? = null) {
    private val seen = LinkedHashMap<String, Long>()
    private var lastSaved = 0L

    init { file?.let { load(it, System.currentTimeMillis()) } }

    /** True when [key] is new (and is now remembered until [expiresAt]); false when it is a replay. */
    @Synchronized
    fun accept(key: String, expiresAt: Long, now: Long): Boolean {
        prune(now)
        if (seen.containsKey(key)) return false
        if (seen.size >= maxEntries) return false // fail closed rather than forget old entries
        seen[key] = expiresAt
        persistIfDue(now)
        return true
    }

    @Synchronized fun size(): Int = seen.size

    @Synchronized
    private fun prune(now: Long) {
        val it = seen.entries.iterator()
        while (it.hasNext()) if (it.next().value <= now) it.remove()
    }

    @Synchronized
    private fun load(f: File, now: Long) {
        if (!f.exists()) return
        runCatching {
            f.readLines().forEach { line ->
                val parts = line.split('\t')
                val exp = parts.getOrNull(1)?.toLongOrNull()
                if (parts.size == 2 && exp != null && exp > now && seen.size < maxEntries) seen[parts[0]] = exp
            }
        }
    }

    /** Writes at most once every few seconds; call [flush] on shutdown for the rest. */
    @Synchronized
    private fun persistIfDue(now: Long) { if (now - lastSaved >= SAVE_EVERY_MS) flush(now) }

    @Synchronized
    fun flush(now: Long = System.currentTimeMillis()) {
        val f = file ?: return
        runCatching {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(seen.entries.joinToString("\n") { "${it.key}\t${it.value}" })
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
            lastSaved = now
        }
    }

    companion object { const val SAVE_EVERY_MS = 3_000L }
}
