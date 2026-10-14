package com.minimart.pos.ui.screen

import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimart.pos.data.entity.UserRole
import com.minimart.pos.data.repository.SettingsRepository
import com.minimart.pos.printer.PrintResult
import com.minimart.pos.printer.ThermalPrinter
import com.minimart.pos.ui.theme.DT
import com.minimart.pos.ui.viewmodel.SyncViewModel
import com.minimart.pos.util.RoleManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Accent colour picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppearanceSettingsSection(settingsRepo: SettingsRepository) {
    val scope = rememberCoroutineScope()
    DSection("Appearance", Icons.Default.Palette, summary = com.minimart.pos.ui.theme.DT.accent.label) {
        Text("The whole app is dark. Pick the colour used for buttons, headers and highlights.",
            color = DT.SubText, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            com.minimart.pos.ui.theme.AppAccent.entries.forEach { a ->
                val picked = com.minimart.pos.ui.theme.DT.accent == a
                Box(
                    Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape)
                        .background(a.palette.accent)
                        .border(if (picked) 3.dp else 1.dp, if (picked) Color.White else DT.Border,
                            androidx.compose.foundation.shape.CircleShape)
                        .clickable {
                            com.minimart.pos.ui.theme.DT.accent = a            // instant
                            scope.launch { settingsRepo.setAccent(a.name) }   // remembered
                        }
                        .semantics { contentDescription = a.label + if (picked) ", selected" else "" },
                    contentAlignment = Alignment.Center
                ) { if (picked) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
            }
        }
    }
}
