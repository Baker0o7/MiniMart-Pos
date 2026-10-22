package com.minimart.pos.ui.screen

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
    val scope = rememberCoroutineScope()
    DSection("Privacy", Icons.Default.VisibilityOff, summary = if (on) "Screenshots blocked" else "Screenshots allowed", initiallyExpanded = false) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = on, role = Role.Switch) { v -> scope.launch { settingsRepo.setBlockScreenshots(v) } },
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Block screenshots", color = DT.OnSurface, fontWeight = FontWeight.SemiBold)
                Text("Stops screenshots and screen recording of the app, and hides it in the recent-apps list. Turn on for a till that customers or staff can pick up. You will not be able to screenshot the app for support either.",
                    color = DT.SubText, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = on, onCheckedChange = null)
        }
    }
}
