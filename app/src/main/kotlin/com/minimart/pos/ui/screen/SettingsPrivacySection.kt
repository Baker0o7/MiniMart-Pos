package com.minimart.pos.ui.screen

import com.minimart.pos.ui.components.Text
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.minimart.pos.data.repository.SettingsRepository
import com.minimart.pos.ui.theme.DT
import kotlinx.coroutines.launch

/** Owner-only privacy switches. */
@Composable
internal fun PrivacySettingsSection(settingsRepo: SettingsRepository) {
    val on by settingsRepo.blockScreenshots.collectAsState(initial = false)
    val reports by settingsRepo.telemetryOptIn.collectAsState(initial = false)
    val kiosk by settingsRepo.kioskMode.collectAsState(initial = false)
    val scope = rememberCoroutineScope()
    DSection(tr(com.minimart.pos.R.string.set_privacy), Icons.Default.VisibilityOff, summary = if (on) "Screenshots blocked" else "Screenshots allowed", initiallyExpanded = false) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = on, role = Role.Switch) { v -> scope.launch { settingsRepo.setBlockScreenshots(v) } },
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr(com.minimart.pos.R.string.block_screens), color = DT.OnSurface, fontWeight = FontWeight.SemiBold)
                Text(tr(com.minimart.pos.R.string.priv_block),
                    color = DT.SubText, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = on, onCheckedChange = null)
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = reports, role = Role.Switch) { v ->
                scope.launch { settingsRepo.setTelemetryOptIn(v) }
                com.minimart.pos.util.Telemetry.apply(v)
            },
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr(com.minimart.pos.R.string.send_reports), color = DT.OnSurface, fontWeight = FontWeight.SemiBold)
                Text(tr(com.minimart.pos.R.string.priv_reports),
                    color = DT.SubText, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = reports, onCheckedChange = null)
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = kiosk, role = Role.Switch) { v -> scope.launch { settingsRepo.setKioskMode(v) } },
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr(com.minimart.pos.R.string.kiosk), color = DT.OnSurface, fontWeight = FontWeight.SemiBold)
                Text(tr(com.minimart.pos.R.string.priv_kiosk),
                    color = DT.SubText, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = kiosk, onCheckedChange = null)
        }
        val context = androidx.compose.ui.platform.LocalContext.current
        Text("Shop data on this phone: " + com.minimart.pos.util.DbEncryption.status(context),
            color = DT.SubText, style = MaterialTheme.typography.bodySmall)
    }
}
