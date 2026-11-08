package com.minimart.pos.ui.screen

import com.minimart.pos.ui.components.Text
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.StatFs
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.minimart.pos.BuildConfig
import com.minimart.pos.ui.theme.DT
import com.minimart.pos.util.BackupManager
import com.minimart.pos.util.CrashRecorder
import com.minimart.pos.util.SupportFacts
import com.minimart.pos.util.SupportInfo

/** Version, licence, project links, and a one-tap "copy support info" that holds no shop data or secrets. */
@Composable
internal fun AboutSettingsSection(syncOn: Boolean, cloudOn: Boolean, webhook: MpesaWebhookViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
                                  updates: com.minimart.pos.ui.viewmodel.UpdateViewModel = androidx.hilt.navigation.compose.hiltViewModel()) {
    val context = LocalContext.current
    val instantMpesaOn = webhook.state.collectAsStateWithLifecycle().value.enabled
    var copied by remember { mutableStateOf(false) }
    DSection(tr(com.minimart.pos.R.string.set_about), Icons.Default.Info, summary = tr(com.minimart.pos.R.string.version_fmt, BuildConfig.VERSION_NAME), initiallyExpanded = false) {
        Text("MiniMart POS ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})", color = DT.OnSurface, style = MaterialTheme.typography.bodyMedium)
        Text(tr(com.minimart.pos.R.string.about_free),
            color = DT.SubText, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(10.dp))
        UpdateBlock(updates)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val text = SupportInfo.format(facts(context, syncOn, cloudOn, instantMpesaOn))
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("MiniMart support info", text))
                copied = true
            }, modifier = Modifier.heightIn(min = 48.dp)) { Text(tr(com.minimart.pos.R.string.copy_support)) }
            OutlinedButton(onClick = { open(context, SupportInfo.ISSUES_URL) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(tr(com.minimart.pos.R.string.report_problem)) }
        }
        OutlinedButton(onClick = { open(context, SupportInfo.REPO_URL) }, modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp)) { Text(tr(com.minimart.pos.R.string.project_page)) }
        if (copied) Text(tr(com.minimart.pos.R.string.copied_support), color = DT.Green, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp))
    }
}

private fun open(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun facts(context: Context, sync: Boolean, cloud: Boolean, instant: Boolean): SupportFacts {
    val now = System.currentTimeMillis()
    val lastOk = runCatching { BackupManager.healthSnapshot(context).first }.getOrDefault(0L)
    val newest = runCatching { BackupManager.listBackups(context).maxOfOrNull { it.lastModified() } ?: 0L }.getOrDefault(0L)
    val last = maxOf(lastOk, newest)
    return SupportFacts(
        appVersion = BuildConfig.VERSION_NAME, versionCode = BuildConfig.VERSION_CODE,
        androidVersion = Build.VERSION.RELEASE.orEmpty(), sdk = Build.VERSION.SDK_INT,
        device = "${Build.MANUFACTURER} ${Build.MODEL}", locale = java.util.Locale.getDefault().toString(),
        freeStorageMb = runCatching { StatFs(context.filesDir.path).availableBytes / (1024 * 1024) }.getOrNull(),
        crashReportsThisWeek = runCatching { CrashRecorder.recentCount(context) }.getOrDefault(0),
        lastBackupDays = if (last > 0) ((now - last) / (24L * 60 * 60 * 1000)).toInt() else null,
        syncOn = sync, cloudOn = cloud, instantMpesaOn = instant
    )
}

/** "Check for updates" and the download-and-install flow; Android still asks the owner to confirm the install. */
@Composable
private fun UpdateBlock(vm: com.minimart.pos.ui.viewmodel.UpdateViewModel) {
    val context = LocalContext.current
    val state = vm.state.collectAsStateWithLifecycle().value
    var auto by remember { mutableStateOf(vm.autoCheck) }
    Text(tr(com.minimart.pos.R.string.upd_title), color = DT.OnSurface, style = MaterialTheme.typography.titleSmall)
    when (state) {
        is com.minimart.pos.update.UpdateState.Idle -> Text(tr(com.minimart.pos.R.string.upd_not_checked), color = DT.SubText, style = MaterialTheme.typography.bodySmall)
        is com.minimart.pos.update.UpdateState.Checking -> Text(tr(com.minimart.pos.R.string.upd_checking), color = DT.SubText, style = MaterialTheme.typography.bodySmall)
        is com.minimart.pos.update.UpdateState.UpToDate -> Text(tr(com.minimart.pos.R.string.upd_latest), color = DT.Green, style = MaterialTheme.typography.bodySmall)
        is com.minimart.pos.update.UpdateState.Available -> {
            Text(tr(com.minimart.pos.R.string.upd_available, state.release.tag.removePrefix("v"), state.asset.size / 1_000_000), color = DT.OnSurface, style = MaterialTheme.typography.bodyMedium)
            if (state.release.notes.isNotBlank()) Text(state.release.notes, color = DT.SubText, style = MaterialTheme.typography.bodySmall)
            Text(tr(com.minimart.pos.R.string.upd_wifi), color = DT.SubText, style = MaterialTheme.typography.bodySmall)
        }
        is com.minimart.pos.update.UpdateState.Downloading -> {
            Text(tr(com.minimart.pos.R.string.upd_downloading, state.percent), color = DT.OnSurface, style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(progress = { state.percent / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        }
        is com.minimart.pos.update.UpdateState.Installing -> Text(tr(com.minimart.pos.R.string.upd_confirm), color = DT.OnSurface, style = MaterialTheme.typography.bodyMedium)
        is com.minimart.pos.update.UpdateState.NeedsPermission -> Text(tr(com.minimart.pos.R.string.upd_perm), color = DT.Amber, style = MaterialTheme.typography.bodySmall)
        is com.minimart.pos.update.UpdateState.Failed -> Text(state.message, color = DT.Red, style = MaterialTheme.typography.bodySmall)
    }
    val canInstall = state is com.minimart.pos.update.UpdateState.Available || state is com.minimart.pos.update.UpdateState.NeedsPermission ||
        (state is com.minimart.pos.update.UpdateState.Failed && state.asset != null)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
        if (canInstall) {
            Button(onClick = {
                if (!vm.canInstall()) runCatching { context.startActivity(vm.permissionIntent()) }
                vm.install()
            }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (state is com.minimart.pos.update.UpdateState.Failed) "Try again" else "Download and install") }
        }
        val busy = state is com.minimart.pos.update.UpdateState.Checking || state is com.minimart.pos.update.UpdateState.Downloading
        OutlinedButton(onClick = { vm.check() }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(tr(com.minimart.pos.R.string.upd_check)) }
    }
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Text(tr(com.minimart.pos.R.string.upd_daily), color = DT.OnSurface, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Switch(checked = auto, onCheckedChange = { auto = it; vm.autoCheck = it })
    }
}
