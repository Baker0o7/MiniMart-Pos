package com.minimart.pos.util

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

/**
 * A short beep when a scan lands (one tone) or does not (a lower double tone), so a cashier scanning a
 * basket does not have to watch the screen to know each item was taken. On by default; one switch in
 * Settings turns it off. Uses the notification volume and needs no permission. Never throws into a sale.
 */
object ScanFeedback {
    private const val PREFS = "scan_feedback"
    private const val KEY_ON = "on"
    @Volatile private var app: Context? = null

    fun install(application: Application) { app = application.applicationContext }

    fun isOn(context: Context): Boolean = try {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ON, true)
    } catch (_: Exception) { true }

    fun setOn(context: Context, on: Boolean) {
        runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ON, on).apply() }
    }

    fun ok() = play(ToneGenerator.TONE_PROP_BEEP, 90)
    fun problem() = play(ToneGenerator.TONE_PROP_NACK, 220)

    private fun play(tone: Int, ms: Int) {
        val c = app ?: return
        if (!isOn(c)) return
        try {
            val gen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
            gen.startTone(tone, ms)
            Handler(Looper.getMainLooper()).postDelayed({ runCatching { gen.release() } }, ms + 150L)
        } catch (_: Throwable) { /* no audio output available: the on-screen message still shows */ }
    }
}
