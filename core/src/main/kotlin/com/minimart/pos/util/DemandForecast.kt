package com.minimart.pos.util

import kotlin.math.ceil
import kotlin.math.max

/** Forecast for one product. [daily] is the expected units per day for the next [daily].size days, starting tomorrow. */
data class Forecast(
    val daily: List<Double>,
    val avgPerDay: Double,
    /** Whole days until stock reaches zero at the forecast rate, or null when it is not selling or will outlast the horizon. */
    val daysUntilEmpty: Int?,
    /** 0..1: how much history the forecast rests on. Low means "treat as a rough guess". */
    val confidence: Double
)

/**
 * Predicts demand from daily sales history. Simple on purpose, so a shop owner can trust and check it:
 * recent days count more than old ones (half-weight every [HALF_LIFE_DAYS]), and each weekday keeps its own
 * pattern (Saturdays are busier) once there are at least [MIN_WEEKS_FOR_PATTERN] weeks of history.
 * A short history gives a flat average and a low [Forecast.confidence].
 */
object DemandForecast {
    const val HALF_LIFE_DAYS = 10.0
    const val MIN_WEEKS_FOR_PATTERN = 3
    const val HORIZON_DAYS = 28

    /**
     * @param history units sold per day, oldest first, ending with yesterday (zeros for days without sales).
     * @param firstWeekday weekday index (0..6) of the first day of [history]; the same numbering is used for tomorrow's weekday.
     * @param onHand units in stock now.
     */
    fun forecast(history: List<Double>, firstWeekday: Int, onHand: Double, horizon: Int = HORIZON_DAYS): Forecast {
        val days = history.map { it.coerceAtLeast(0.0) }
        if (days.isEmpty() || days.all { it == 0.0 }) return Forecast(List(horizon) { 0.0 }, 0.0, null, if (days.isEmpty()) 0.0 else confidence(days.size))

        val n = days.size
        val weight = DoubleArray(n) { i -> Math.pow(0.5, (n - 1 - i) / HALF_LIFE_DAYS) }
        val overall = weighted(days, weight) { true }

        val usePattern = n >= MIN_WEEKS_FOR_PATTERN * 7
        val factor = DoubleArray(7) { 1.0 }
        if (usePattern && overall > 0) {
            for (wd in 0 until 7) {
                val wdAvg = weighted(days, weight) { i -> (firstWeekday + i) % 7 == wd }
                // Pulled towards 1 so one odd Saturday does not swing the plan.
                factor[wd] = (0.5 + 0.5 * (wdAvg / overall)).coerceIn(0.4, 2.0)
            }
            val mean = factor.average()
            for (wd in 0 until 7) factor[wd] /= mean   // keeps the weekly total equal to the plain average
        }

        val tomorrow = (firstWeekday + n) % 7
        val out = List(horizon) { d -> overall * factor[(tomorrow + d) % 7] }

        var left = onHand.coerceAtLeast(0.0)
        var empty: Int? = null
        for ((d, units) in out.withIndex()) {
            left -= units
            if (left <= 0.0) { empty = d + 1; break }
        }
        return Forecast(out, out.average(), empty, confidence(n))
    }

    /** Units to order now so stock lasts through the lead time plus [coverDays], given the forecast. */
    fun orderQuantity(f: Forecast, onHand: Double, leadDays: Int, coverDays: Int): Int {
        val span = (leadDays + coverDays).coerceIn(1, f.daily.size)
        val need = f.daily.take(span).sum() - onHand.coerceAtLeast(0.0)
        return if (need <= 0) 0 else ceil(need).toInt()
    }

    private fun confidence(n: Int) = (n / 28.0).coerceIn(0.0, 1.0)

    private inline fun weighted(v: List<Double>, w: DoubleArray, take: (Int) -> Boolean): Double {
        var s = 0.0; var ws = 0.0
        for (i in v.indices) if (take(i)) { s += v[i] * w[i]; ws += w[i] }
        return if (ws == 0.0) 0.0 else s / ws
    }

    /** Fills a day-indexed sparse map into a dense list, oldest day first. */
    fun dense(unitsByDay: Map<Long, Double>, firstDay: Long, lastDay: Long): List<Double> =
        (firstDay..max(firstDay, lastDay)).map { unitsByDay[it] ?: 0.0 }
}
