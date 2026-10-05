package com.minimart.pos.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.minimart.pos.ui.theme.DT
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Label such as "01 Oct – 05 Oct" for a custom range chip. */
fun formatRangeLabel(startMs: Long, endMs: Long): String {
    val f = SimpleDateFormat("dd MMM", Locale.getDefault())
    return "${f.format(Date(startMs))} – ${f.format(Date(endMs))}"
}

/**
 * Pick a start and end day. The picker returns UTC midnights, so they are converted to the
 * device's local day boundaries: start = 00:00:00.000, end = 23:59:59.999 of the end day.
 *
 * Uses a full-width Dialog (not AlertDialog): AlertDialog's narrow default width squeezed the
 * range picker so the header wrapped letter-by-letter and the Saturday column was clipped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeDialog(onDismiss: () -> Unit, onConfirm: (startMs: Long, endMs: Long) -> Unit) {
    val state = rememberDateRangePickerState()
    val ready = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.9f),
            shape = RoundedCornerShape(24.dp),
            color = DT.Surface
        ) {
            Column(Modifier.fillMaxSize()) {
                DateRangePicker(
                    state = state,
                    modifier = Modifier.weight(1f),
                    colors = DatePickerDefaults.colors(
                        containerColor = DT.Surface,
                        titleContentColor = DT.SubText,
                        headlineContentColor = DT.OnSurface,
                        weekdayContentColor = DT.SubText,
                        subheadContentColor = DT.SubText,
                        navigationContentColor = DT.OnSurface,
                        dayContentColor = DT.OnSurface,
                        selectedDayContainerColor = DT.Teal,
                        selectedDayContentColor = Color.White,
                        todayContentColor = DT.TealLight,
                        todayDateBorderColor = DT.TealLight,
                        dayInSelectionRangeContainerColor = DT.Teal.copy(alpha = 0.25f),
                        dayInSelectionRangeContentColor = DT.OnSurface
                    )
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = DT.SubText) }
                    TextButton(
                        enabled = ready,
                        onClick = {
                            val s = localDayStart(state.selectedStartDateMillis!!)
                            val e = localDayStart(state.selectedEndDateMillis!!) + 24L * 60 * 60 * 1000 - 1
                            onConfirm(s, maxOf(s, e))
                        }
                    ) { Text("Apply", color = if (ready) DT.Teal else DT.SubText) }
                }
            }
        }
    }
}

/** Converts a UTC-midnight date from the picker into local midnight of the same calendar day. */
private fun localDayStart(utcMidnightMs: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMidnightMs }
    return Calendar.getInstance().apply {
        clear()
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
    }.timeInMillis
}
