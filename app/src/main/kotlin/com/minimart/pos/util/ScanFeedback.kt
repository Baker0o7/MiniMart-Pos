package com.minimart.pos.util

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View

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

    private const val KEY_HAPTIC = "haptic"
    fun isHapticOn(context: Context): Boolean = try {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_HAPTIC, true)
    } catch (_: Exception) { true }

    fun setHapticOn(context: Context, on: Boolean) {
        runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_HAPTIC, on).apply() }
    }

    /** A light tick under the finger for a tap. Respects the phone's own "touch feedback" setting. */
    fun tap(view: View) {
        val c = app ?: return
        if (!isHapticOn(c)) return
        runCatching { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
    }

    fun ok() { play(ToneGenerator.TONE_PROP_BEEP, 90); buzz(longArrayOf(0, 25)) }
    fun problem() { play(ToneGenerator.TONE_PROP_NACK, 220); buzz(longArrayOf(0, 40, 60, 40)) }

    /** A short buzz that confirms (one pulse) or warns (two pulses) even when the shop is too loud to hear a beep. */
    private fun buzz(pattern: LongArray) {
        val c = app ?: return
        if (!isHapticOn(c)) return
        try {
            @Suppress("DEPRECATION")
            val v = c.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (!v.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(pattern, -1))
            else @Suppress("DEPRECATION") v.vibrate(pattern, -1)
        } catch (_: Throwable) { /* no vibrator: nothing to do */ }
    }

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
