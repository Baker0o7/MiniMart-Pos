package com.minimart.pos.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
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
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeDialog(onDismiss: () -> Unit, onConfirm: (startMs: Long, endMs: Long) -> Unit) {
    val state = rememberDateRangePickerState()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null,
                onClick = {
                    val s = localDayStart(state.selectedStartDateMillis!!)
                    val e = localDayStart(state.selectedEndDateMillis!!) + 24L * 60 * 60 * 1000 - 1
                    onConfirm(s, maxOf(s, e))
                }
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            Box(Modifier.height(480.dp)) { DateRangePicker(state = state) }
        }
    )
}

/** Converts a UTC-midnight date from the picker into local midnight of the same calendar day. */
private fun localDayStart(utcMidnightMs: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMidnightMs }
    return Calendar.getInstance().apply {
        clear()
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
    }.timeInMillis
}
