package com.minimart.pos.util

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

// ─── Money formatting ─────────────────────────────────────────────────────────

fun Double.formatMoney(currency: String = "KES"): String =
    "$currency ${String.format("%.2f", this)}"

fun Double.formatMoneyCompact(currency: String = "KES"): String {
    return when {
        this >= 1_000_000 -> "$currency ${String.format("%.1f", this / 1_000_000)}M"
        this >= 1_000     -> "$currency ${String.format("%.1f", this / 1_000)}K"
        else              -> formatMoney(currency)
    }
}

// ─── Date helpers ─────────────────────────────────────────────────────────────

fun Long.toDisplayDate(): String =
    SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(this))

fun Long.toDisplayDateTime(): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(this))

fun todayStartMs(): Long {
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

// ─── Haptic feedback ──────────────────────────────────────────────────────────

fun Context.vibrateShort() {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION") v.vibrate(40)
            }
        }
    } catch (_: Exception) {}
}

// ─── Barcode validation ───────────────────────────────────────────────────────

fun String.isValidEan13(): Boolean {
    if (length != 13 || any { !it.isDigit() }) return false
    val digits = map { it.digitToInt() }
    val check = digits.dropLast(1)
        .mapIndexed { i, d -> if (i % 2 == 0) d else d * 3 }
        .sum().let { (10 - it % 10) % 10 }
    return check == digits.last()
}

// ─── Receipt number ───────────────────────────────────────────────────────────

fun buildReceiptNumber(counter: Int, prefix: String = "RCP"): String {
    val date = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
    return "$prefix-$date-${counter.toString().padStart(4, '0')}"
}

// ─── Category icons ───────────────────────────────────────────────────────────

fun categoryEmoji(category: String): String = when (category.lowercase()) {
    "drinks"        -> "🥤"
    "snacks"        -> "🍟"
    "cigarettes"    -> "🚬"
    "personal care" -> "🧴"
    "dairy"         -> "🥛"
    "bread"         -> "🍞"
    "household"     -> "🏠"
    "frozen"        -> "🧊"
    "fruits"        -> "🍎"
    "vegetables"    -> "🥦"
    else            -> "📦"
}
