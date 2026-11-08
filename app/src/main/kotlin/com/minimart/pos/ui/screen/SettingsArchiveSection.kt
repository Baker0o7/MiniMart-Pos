package com.minimart.pos.ui.screen

import com.minimart.pos.ui.components.Text
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minimart.pos.ui.theme.DT
import com.minimart.pos.ui.viewmodel.ArchiveViewModel
import com.minimart.pos.ui.viewmodel.OwnerGateViewModel
import com.minimart.pos.util.ArchivePolicy
import kotlinx.coroutines.launch

/** Owner-only: move old sales to a file so the phone stays fast. Nothing is deleted until the file is written and checked. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ArchiveSettingsSection(ownerGate: OwnerGateViewModel, vm: ArchiveViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf<String?>(null) }

    DSection(tr(com.minimart.pos.R.string.arc_title), Icons.Default.Archive, summary = tr(com.minimart.pos.R.string.arc_summary), initiallyExpanded = false) {
        Text(tr(com.minimart.pos.R.string.arc_help),
            color = DT.SubText, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(10.dp))
        Text(tr(com.minimart.pos.R.string.arc_older), color = DT.OnSurface, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(12, 24, 36).forEach { m ->
                FilterChip(selected = s.months == m, onClick = { vm.setMonths(m) }, label = { Text(if (m == 12) "1 year" else "${m / 12} years") })
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(s.plan?.let { ArchivePolicy.describe(it) } ?: "Checking…", color = DT.SubText, style = MaterialTheme.typography.bodySmall)
        s.message?.let { FeedbackBanner(it, isError = false, modifier = Modifier.padding(top = 8.dp)) }
        (s.error ?: denied)?.let { FeedbackBanner(it, isError = true, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { denied = null; confirm = true },
            enabled = !s.working && s.plan?.isEmpty == false,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) { Text(if (s.working) "Archiving…" else "Archive now") }
        s.lastFile?.let { file ->
            OutlinedButton(
                onClick = {
                    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    val send = Intent(Intent.ACTION_SEND).setType("application/gzip").putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    runCatching { context.startActivity(Intent.createChooser(send, "Save archive").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text(tr(com.minimart.pos.R.string.arc_share)) }
        }
    }

    if (confirm) {
        val plan = s.plan
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(tr(com.minimart.pos.R.string.arc_q, plan?.sales ?: 0)) },
            text = { Text(tr(com.minimart.pos.R.string.arc_warn)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    scope.launch { if (ownerGate.allow("archive old sales")) vm.archive() else denied = "Only the owner can do this" }
                }) { Text(tr(com.minimart.pos.R.string.arc_btn)) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(tr(com.minimart.pos.R.string.cancel)) } }
        )
    }
}
