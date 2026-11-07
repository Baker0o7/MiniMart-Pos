package com.minimart.pos.ui.screen

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.minimart.pos.data.repository.SettingsRepository
import com.minimart.pos.ui.theme.DT
import com.minimart.pos.worker.DailyReportWorker
import kotlinx.coroutines.launch

private val HOURS = listOf(18, 19, 20, 21, 22)
private fun hourLabel(h: Int) = if (h < 12) "$h am" else "${if (h == 12) 12 else h - 12} pm"

/** Owner-only: the end-of-day summary notification (what it contains is in DailySummary). */
@Composable
internal fun DailyReportSettingsSection(settingsRepo: SettingsRepository) {
    val context: Context = LocalContext.current
    val scope = rememberCoroutineScope()
    val on by settingsRepo.dailyReport.collectAsState(initial = false)
    val hour by settingsRepo.dailyReportHour.collectAsState(initial = 20)
    val saved by settingsRepo.ownerReportPhone.collectAsState(initial = "")
    var phone by rememberSaveable(saved) { mutableStateOf(saved) }
    DSection(tr(com.minimart.pos.R.string.set_daily), Icons.Default.Summarize,
        summary = if (on) "Every day at ${hourLabel(hour)}" else "Off", initiallyExpanded = false) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = on, role = Role.Switch) { v ->
                scope.launch {
                    settingsRepo.setDailyReport(v)
                    if (v) DailyReportWorker.schedule(context, hour, replace = true) else DailyReportWorker.cancel(context)
                }
            }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr(com.minimart.pos.R.string.daily_summary), color = DT.OnSurface, fontWeight = FontWeight.SemiBold)
                Text("Every day at the time below, this phone shows a notification with the day's sales, cash and M-Pesa, refunds, expenses, profit and anything that needs attention. Tap Share or SMS owner on it to send the text on. Allow notifications for MiniMart in Android settings if you do not see it.",
                    color = DT.SubText, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = on, onCheckedChange = null)
        }
        Text(tr(com.minimart.pos.R.string.time), color = DT.OnSurface, fontWeight = FontWeight.SemiBold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HOURS.forEach { h ->
                FilterChip(selected = h == hour, onClick = {
                    scope.launch {
                        settingsRepo.setDailyReportHour(h)
                        if (on) DailyReportWorker.schedule(context, h, replace = true)
                    }
                }, label = { Text(hourLabel(h)) })
            }
        }
        OutlinedTextField(value = phone, onValueChange = { phone = it.filterIndexed { i, c -> c.isDigit() || (i == 0 && c == '+') }.take(16) },
            label = { Text(tr(com.minimart.pos.R.string.owner_phone)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { scope.launch { settingsRepo.setOwnerReportPhone(phone) } },
                modifier = Modifier.heightIn(min = 48.dp), enabled = phone != saved) { Text(tr(com.minimart.pos.R.string.save_number)) }
            OutlinedButton(onClick = { DailyReportWorker.runNow(context) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(tr(com.minimart.pos.R.string.send_test))
            }
        }
    }
}
