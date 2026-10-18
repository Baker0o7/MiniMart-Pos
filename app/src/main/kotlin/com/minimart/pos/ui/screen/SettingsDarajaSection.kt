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

/** Daraja (M-Pesa STK Push) credentials box; owner/manager only (the caller checks the role). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DarajaSettingsSection(settingsRepo: SettingsRepository, ownerGate: com.minimart.pos.ui.viewmodel.OwnerGateViewModel) {
    val scope = rememberCoroutineScope()
    val initialDaraja = remember { settingsRepo.getDarajaConfig() }
    var darajaSandbox       by remember { mutableStateOf(initialDaraja.sandbox) }
    var darajaShortcode     by remember { mutableStateOf(initialDaraja.shortcode) }
    var darajaConsumerKey   by remember { mutableStateOf(initialDaraja.consumerKey) }
    // Saved secrets are never loaded into the screen (they could be shown with the eye icon or seen over a
    // shoulder). The boxes start empty; leaving one empty keeps the saved value.
    var secretSaved by remember { mutableStateOf(settingsRepo.darajaSecretsSaved()) }
    var darajaConsumerSecret by remember { mutableStateOf("") }
    var darajaPasskey       by remember { mutableStateOf("") }
    var saveError           by remember { mutableStateOf<String?>(null) }
    val secureAvailable = remember { settingsRepo.secureStorageAvailable }
    var showSecret          by remember { mutableStateOf(false) }
    var showPasskey         by remember { mutableStateOf(false) }
    var darajaSaved         by remember { mutableStateOf(false) }

    DSection("M-Pesa STK Push (Daraja API)", Icons.Default.Send,
        summary = if (initialDaraja.consumerKey.isBlank()) "Not set up" else if (darajaSandbox) "Sandbox" else "Production",
        initiallyExpanded = false) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("Environment", color = DT.OnSurface, fontSize = 14.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (darajaSandbox) "Sandbox" else "Production",
                    color = if (darajaSandbox) DT.Amber else DT.Green,
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                Switch(checked = !darajaSandbox, onCheckedChange = { darajaSandbox = !it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor   = DT.Green,
                        checkedTrackColor   = DT.Green.copy(0.3f),
                        uncheckedThumbColor = DT.Amber,
                        uncheckedTrackColor = DT.Amber.copy(0.3f)
                    ))
            }
        }
        Text(if (darajaSandbox) "Sandbox uses test credentials — no real money moves."
             else "Production — customers are charged for real.",
            color = DT.SubText, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(8.dp))
        DField(darajaShortcode, { darajaShortcode = it }, "Business Shortcode", Icons.Default.Storefront)
        Spacer(Modifier.height(4.dp))
        DField(darajaConsumerKey, { darajaConsumerKey = it }, "Consumer Key", Icons.Default.VpnKey)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = darajaConsumerSecret, onValueChange = { darajaConsumerSecret = it },
            label = { Text("Consumer Secret", color = DT.SubText, style = MaterialTheme.typography.labelSmall) },
            placeholder = { if (secretSaved.first) Text("Saved. Type to replace", color = DT.SubText.copy(0.6f)) },
            leadingIcon = { Icon(Icons.Default.Lock, null, tint = DT.SubText, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                IconButton(onClick = { showSecret = !showSecret }) {
                    Icon(if (showSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = DT.SubText)
                }
            },
            visualTransformation = if (showSecret) androidx.compose.ui.text.input.VisualTransformation.None
                                   else androidx.compose.ui.text.input.PasswordVisualTransformation(),
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp), colors = dColors()
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = darajaPasskey, onValueChange = { darajaPasskey = it },
            label = { Text("Lipa na M-Pesa Passkey", color = DT.SubText, style = MaterialTheme.typography.labelSmall) },
            placeholder = { if (secretSaved.second) Text("Saved. Type to replace", color = DT.SubText.copy(0.6f)) },
            leadingIcon = { Icon(Icons.Default.Key, null, tint = DT.SubText, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                IconButton(onClick = { showPasskey = !showPasskey }) {
                    Icon(if (showPasskey) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = DT.SubText)
                }
            },
            visualTransformation = if (showPasskey) androidx.compose.ui.text.input.VisualTransformation.None
                                   else androidx.compose.ui.text.input.PasswordVisualTransformation(),
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp), colors = dColors()
        )
        if (!secureAvailable) Text("Secure storage is unavailable on this phone, so M-Pesa keys cannot be saved safely.",
            color = DT.Red, style = MaterialTheme.typography.labelSmall)
        saveError?.let { Text(it, color = DT.Red, style = MaterialTheme.typography.labelSmall) }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                scope.launch {
                    if (!ownerGate.allow("change payment credentials")) return@launch
                    val result = settingsRepo.saveDarajaConfig(
                        com.minimart.pos.data.repository.SettingsRepository.DarajaConfig(
                            consumerKey    = darajaConsumerKey.trim(),
                            consumerSecret = darajaConsumerSecret.trim(),
                            passkey        = darajaPasskey.trim(),
                            shortcode      = darajaShortcode.trim(),
                            sandbox        = darajaSandbox
                        )
                    )
                    saveError = when (result) {
                        com.minimart.pos.data.repository.SettingsRepository.DarajaSave.SAVED -> null
                        com.minimart.pos.data.repository.SettingsRepository.DarajaSave.NO_SECURE_STORAGE ->
                            "This phone's secure storage is not available, so the M-Pesa keys were NOT saved. Restart the phone and try again."
                        com.minimart.pos.data.repository.SettingsRepository.DarajaSave.FAILED -> "Could not save the M-Pesa keys. Try again."
                    }
                    if (result == com.minimart.pos.data.repository.SettingsRepository.DarajaSave.SAVED) {
                        darajaConsumerSecret = ""; darajaPasskey = ""; showSecret = false; showPasskey = false
                        secretSaved = settingsRepo.darajaSecretsSaved()
                        darajaSaved = true
                        scope.launch { kotlinx.coroutines.delay(3000); darajaSaved = false }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DT.Teal)
        ) {
            Icon(Icons.Default.Send, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (darajaSaved) "✓ Saved — encrypted on this device" else "Save Daraja Settings",
                color = Color.White, fontWeight = FontWeight.Bold)
        }
    }

}
