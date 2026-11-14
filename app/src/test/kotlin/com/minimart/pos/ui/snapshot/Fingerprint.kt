package com.minimart.pos.ui.snapshot

import android.graphics.Bitmap
import java.io.File

/**
 * A screenshot reduced to a small grid of average colours, kept as text in `src/test/resources/snapshots/<name>.txt`.
 * Text is easy to review in a pull request, needs no image library, and a tolerance hides font-smoothing noise while
 * a moved, cut-off, recoloured or missing element still fails. To accept an intended change, replace the golden file with
 * the one the failing test wrote to `build/snapshots-actual/` (CI prints it).
 */
object Fingerprint {
    const val COLS = 24
    const val ROWS = 16
    /** Average per-channel difference allowed (0..255), and the largest single cell difference. */
    const val MEAN_TOLERANCE = 3.0
    const val CELL_TOLERANCE = 40

    fun of(bitmap: Bitmap): List<String> {
        val w = bitmap.width; val h = bitmap.height
        return (0 until ROWS).map { r ->
            buildString {
                for (c in 0 until COLS) {
                    var sr = 0L; var sg = 0L; var sb = 0L; var n = 0
                    val x0 = c * w / COLS; val x1 = maxOf(x0 + 1, (c + 1) * w / COLS)
                    val y0 = r * h / ROWS; val y1 = maxOf(y0 + 1, (r + 1) * h / ROWS)
                    for (y in y0 until minOf(y1, h)) for (x in x0 until minOf(x1, w)) {
                        val p = bitmap.getPixel(x, y); sr += (p shr 16) and 0xff; sg += (p shr 8) and 0xff; sb += p and 0xff; n++
                    }
                    n = maxOf(n, 1)
                    append("%02x%02x%02x".format((sr / n).toInt(), (sg / n).toInt(), (sb / n).toInt()))
                }
            }
        }
    }

    /** Null when [actual] matches [golden] within tolerance, otherwise a short reason. */
    fun difference(golden: List<String>, actual: List<String>): String? {
        if (golden.size != actual.size || golden.any { it.length != COLS * 6 }) return "the saved snapshot has the wrong shape"
        var total = 0L; var count = 0; var worst = 0; var worstAt = ""
        for (r in golden.indices) for (c in 0 until COLS) for (k in 0 until 3) {
            val g = golden[r].substring(c * 6 + k * 2, c * 6 + k * 2 + 2).toInt(16)
            val a = actual[r].substring(c * 6 + k * 2, c * 6 + k * 2 + 2).toInt(16)
            val d = kotlin.math.abs(g - a); total += d; count++
            if (d > worst) { worst = d; worstAt = "row $r, column $c" }
        }
        val mean = total.toDouble() / count
        return if (mean > MEAN_TOLERANCE || worst > CELL_TOLERANCE)
            "mean difference %.1f (max %.1f), largest %d at %s".format(java.util.Locale.US, mean, MEAN_TOLERANCE, worst, worstAt) else null
    }

    fun golden(name: String): List<String>? =
        Fingerprint::class.java.getResourceAsStream("/snapshots/$name.txt")?.bufferedReader()?.readLines()?.filter { it.isNotBlank() }

    fun saveActual(name: String, lines: List<String>) {
        runCatching { File("build/snapshots-actual").apply { mkdirs() }.resolve("$name.txt").writeText(lines.joinToString("\n") + "\n") }
    }
}
