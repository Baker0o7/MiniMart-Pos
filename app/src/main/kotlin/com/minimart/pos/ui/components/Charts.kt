package com.minimart.pos.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimart.pos.ui.theme.DT
import java.util.Locale

/**
 * The app's one bar chart (it replaces three hand-rolled copies and the unused MPAndroidChart library).
 * Bars grow in when the data changes, scale to the tallest value, and can be tapped or dragged across to
 * read an exact figure. [highlight] is drawn in the accent colour; pass -1 for none.
 */
@Composable
fun BarChart(
    values: List<Float>,
    highlight: Int,
    modifier: Modifier = Modifier,
    bar: Color = DT.Teal.copy(alpha = 0.55f),
    accent: Color = DT.TealLight,
    labels: List<String>? = null,
    valueLabel: (Float) -> String = { String.format(Locale.US, "%,.0f", it) },
    description: String? = null
) {
    val progress = remember(values) { Animatable(0f) }
    LaunchedEffect(values) { progress.animateTo(1f, tween(450)) }
    var selected by remember(values) { mutableIntStateOf(-1) }

    Box(modifier.semantics {
        contentDescription = description ?: buildString {
            append("Bar chart, ${values.size} bars")
            if (highlight in values.indices) append(". Highest: ${labels?.getOrNull(highlight).orEmpty()} ${valueLabel(values[highlight])}")
        }
    }) {
        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(values) {
                    val indexAt = { x: Float -> if (values.isEmpty()) -1 else (x / size.width * values.size).toInt().coerceIn(0, values.size - 1) }
                    detectTapGestures(onTap = { p -> val i = indexAt(p.x); selected = if (i == selected) -1 else i })
                }
                .pointerInput(values) {
                    val indexAt = { x: Float -> if (values.isEmpty()) -1 else (x / size.width * values.size).toInt().coerceIn(0, values.size - 1) }
                    detectHorizontalDragGestures(
                        onDragStart = { p -> selected = indexAt(p.x) },
                        onHorizontalDrag = { change, _ -> selected = indexAt(change.position.x) }
                    )
                }
        ) {
            if (values.isEmpty()) return@Canvas
            val max = (values.maxOrNull() ?: 0f).coerceAtLeast(0.0001f)
            val gap = if (values.size > 40) 1.dp.toPx() else 3.dp.toPx()
            val w = ((size.width - gap * (values.size - 1)) / values.size).coerceAtLeast(1f)
            val p = progress.value
            values.forEachIndexed { i, v ->
                val full = if (v > 0f) (v / max * size.height).coerceAtLeast(3.dp.toPx()) else 2.dp.toPx()
                val h = full * p
                drawRoundRect(
                    color = if (i == selected || (selected < 0 && i == highlight && v > 0f)) accent else bar,
                    topLeft = Offset(i * (w + gap), size.height - h),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(minOf(w / 2, 4.dp.toPx()))
                )
            }
        }
        if (selected in values.indices) {
            val label = labels?.getOrNull(selected)
            Text(
                text = if (label.isNullOrBlank()) valueLabel(values[selected]) else "$label · ${valueLabel(values[selected])}",
                color = DT.OnSurface, fontSize = 11.sp,
                modifier = Modifier.align(Alignment.TopCenter)
                    .background(DT.Surface.copy(alpha = 0.92f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}
