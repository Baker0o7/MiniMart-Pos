package com.minimart.pos.util

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance

/**
 * Opt-in crash and speed reports to Firebase. The manifest switches collection off; this turns it on only after
 * the owner agrees (Settings > Privacy). Every call is guarded because Firebase is not set up at all unless
 * app/google-services.json was added at build time, and reporting must never break a sale.
 */
object Telemetry {
    /** Applies the owner's choice. Safe to call repeatedly, and a no-op when Firebase is not configured. */
    fun apply(enabled: Boolean) {
        runCatching { FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabled) }
        runCatching { FirebasePerformance.getInstance().isPerformanceCollectionEnabled = enabled }
    }

    /** Times [block] as a named trace (only recorded when the owner opted in); always returns the block's result. */
    inline fun <T> timed(name: String, block: () -> T): T {
        val trace = runCatching { FirebasePerformance.getInstance().newTrace(name).also { it.start() } }.getOrNull()
        try { return block() } finally { runCatching { trace?.stop() } }
    }

    /** Leaves a breadcrumb in the next crash report (no personal data: names of steps only). */
    fun breadcrumb(message: String) { runCatching { FirebaseCrashlytics.getInstance().log(message) } }
}
