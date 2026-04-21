package com.minimart.pos.ui.screen

import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minimart.pos.data.repository.SettingsRepository
import com.minimart.pos.printer.PrintResult
import com.minimart.pos.printer.ThermalPrinter
import com.minimart.pos.ui.theme.DT
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onShifts: () -> Unit,
    onUsers: () -> Unit,
    onLogout: () -> Unit,
    settingsRepo: SettingsRepository,
    printer: ThermalPrinter
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val storeName by settingsRepo.storeName.collectAsState("")
    val currency by settingsRepo.currency.collectAsState("KES")
    val receiptFooter by settingsRepo.receiptFooter.collectAsState("")
    val darkMode by settingsRepo.darkMode.collectAsState(false)
    val mpesaPaybill by settingsRepo.mpesaPaybill.collectAsState("")
    val printerName by settingsRepo.printerName.collectAsState(null)

    var storeNameInput by remember { mutableStateOf("") }
    var currencyInput by remember { mutableStateOf("KES") }
    var footerInput by remember { mutableStateOf("") }
    var mpesaInput by remember { mutableStateOf("") }
    var printerStatus by remember { mutableStateOf<String?>(null) }
    var pairedDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var showPrinterDialog by remember { mutableStateOf(false) }

    LaunchedEffect(storeName, currency, receiptFooter, mpesaPaybill) {
        storeNameInput = storeName; currencyInput = currency; footerInput = receiptFooter; mpesaInput = mpesaPaybill
    }

    Box(modifier = Modifier.fillMaxSize().background(DT.Bg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Teal top bar ──────────────────────────────────────────────────
            Box(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                    .background(Brush.horizontalGradient(listOf(DT.Teal, Color(0xFF00695C))))
                    .padding(horizontal = 8.dp, vertical = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
                    Text("Settings", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
                }
            }

            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // ── Store Information ─────────────────────────────────────────
                DarkSection("Store Information") {
                    DarkSettingRow("Store Name", storeNameInput, { storeNameInput = it })
                    DarkDivider()
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Currency", color = DT.OnSurface, modifier = Modifier.weight(1f))
                        OutlinedTextField(currencyInput, { currencyInput = it }, singleLine = true,
                            modifier = Modifier.width(100.dp),
                            colors = darkFieldColors(), shape = RoundedCornerShape(10.dp))
                    }
                    DarkDivider()
                    DarkSettingRow("Receipt Footer", footerInput, { footerInput = it })
                    DarkDivider()
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("M-Pesa Till", color = DT.OnSurface, modifier = Modifier.weight(1f))
                        Switch(checked = mpesaInput.isNotBlank(), onCheckedChange = {}, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DT.Teal))
                    }
                    OutlinedTextField(mpesaInput, { mpesaInput = it }, placeholder = { Text("M-Pesa Till Number", color = DT.SubText) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(), colors = darkFieldColors(), shape = RoundedCornerShape(10.dp))
                    Button(onClick = {
                        scope.launch {
                            settingsRepo.setStoreName(storeNameInput); settingsRepo.setCurrency(currencyInput)
                            settingsRepo.setReceiptFooter(footerInput); settingsRepo.setMpesaPaybill(mpesaInput)
                        }
                    }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DT.Teal)) {
                        Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                // ── Thermal Printer ───────────────────────────────────────────
                DarkSection("Thermal Printer") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(printerName ?: "No printer paired", color = if (printer.isConnected) DT.Teal else DT.SubText, modifier = Modifier.weight(1f))
                        Button(onClick = { pairedDevices = printer.getPairedPrinters(context); showPrinterDialog = true },
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DT.Teal),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                            Text("Pair", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    printerStatus?.let { Text(it, color = if (it.startsWith("✓")) DT.Green else DT.Red, style = MaterialTheme.typography.labelSmall) }
                }

                // ── Appearance ────────────────────────────────────────────────
                DarkSection("Appearance") {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Dark Mode", color = DT.OnSurface, modifier = Modifier.weight(1f))
                        Switch(checked = darkMode, onCheckedChange = { scope.launch { settingsRepo.setDarkMode(it) } },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DT.Teal))
                    }
                }

                // ── Data & Backup ──────────────────────────────────────────────
                DarkSection("Data & Backup") {
                    var backupStatus by remember { mutableStateOf<String?>(null) }
                    var isBackingUp by remember { mutableStateOf(false) }
                    var isRestoring by remember { mutableStateOf(false) }
                    var backupFiles by remember { mutableStateOf<List<java.io.File>>(emptyList()) }
                    var showRestoreDialog by remember { mutableStateOf(false) }

                    LaunchedEffect(Unit) { backupFiles = com.minimart.pos.util.BackupManager.listBackups() }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Backup now
                        Button(
                            onClick = {
                                isBackingUp = true
                                scope.launch {
                                    val result = com.minimart.pos.util.BackupManager.backup(context)
                                    backupStatus = when (result) {
                                        is com.minimart.pos.util.BackupResult.Success -> { backupFiles = com.minimart.pos.util.BackupManager.listBackups(); result.message }
                                        is com.minimart.pos.util.BackupResult.Error -> result.message
                                    }
                                    isBackingUp = false
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isBackingUp,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DT.Teal)
                        ) {
                            if (isBackingUp) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            else Icon(Icons.Default.Backup, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Backup Now", style = MaterialTheme.typography.labelMedium, color = Color.White)
                        }
                        // Restore
                        OutlinedButton(
                            onClick = { backupFiles = com.minimart.pos.util.BackupManager.listBackups(); showRestoreDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DT.Border)
                        ) {
                            Icon(Icons.Default.Restore, null, tint = DT.OnSurface, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Restore", style = MaterialTheme.typography.labelMedium, color = DT.OnSurface)
                        }
                    }

                    backupStatus?.let { msg ->
                        Text(msg, color = if (msg.startsWith("Backup saved")) DT.Green else DT.Red,
                            style = MaterialTheme.typography.labelSmall)
                    }

                    if (backupFiles.isNotEmpty()) {
                        Text("${backupFiles.size} backup${if (backupFiles.size == 1) "" else "s"} in Downloads/MiniMartPOS/backups",
                            color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                        // Share latest backup
                        TextButton(onClick = { com.minimart.pos.util.BackupManager.shareBackup(context, backupFiles.first()) },
                            contentPadding = PaddingValues(0.dp)) {
                            Icon(Icons.Default.Share, null, tint = DT.Teal, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Share Latest Backup (USB/OTG/Cloud)", color = DT.Teal, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    if (showRestoreDialog) {
                        AlertDialog(
                            onDismissRequest = { showRestoreDialog = false },
                            containerColor = DT.Surface,
                            title = { Text("Restore Database", color = DT.OnSurface, fontWeight = FontWeight.Bold) },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Choose a backup to restore. The app will need to restart after restore.",
                                        color = DT.SubText, style = MaterialTheme.typography.bodySmall)
                                    if (backupFiles.isEmpty()) {
                                        Text("No backups found in Downloads/MiniMartPOS/backups", color = DT.Red, style = MaterialTheme.typography.labelSmall)
                                    } else {
                                        backupFiles.take(5).forEach { file ->
                                            TextButton(onClick = {
                                                showRestoreDialog = false
                                                isRestoring = true
                                                scope.launch {
                                                    val result = com.minimart.pos.util.BackupManager.restore(context, file)
                                                    backupStatus = when (result) {
                                                        is com.minimart.pos.util.BackupResult.Success -> result.message
                                                        is com.minimart.pos.util.BackupResult.Error -> result.message
                                                    }
                                                    isRestoring = false
                                                }
                                            }, modifier = Modifier.fillMaxWidth()) {
                                                Text(file.name, color = DT.TealLight, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = { TextButton(onClick = { showRestoreDialog = false }) { Text("Cancel", color = DT.SubText) } }
                        )
                    }
                }

                // ── Account ───────────────────────────────────────────────────
                DarkSection("Account") {
                    DarkMenuRow("User Management", Icons.Default.Group, Icons.Default.ChevronRight, DT.OnSurface, onUsers)
                    DarkDivider()
                    DarkMenuRow("Shift Management", Icons.Default.Schedule, Icons.Default.ChevronRight, DT.OnSurface, onShifts)
                    DarkDivider()
                    DarkMenuRow("Logout", Icons.AutoMirrored.Filled.Logout, null, DT.Red, onLogout)
                }
            }
        }
    }

    if (showPrinterDialog) {
        AlertDialog(onDismissRequest = { showPrinterDialog = false }, containerColor = DT.Surface,
            title = { Text("Select Printer", color = DT.OnSurface) },
            text = {
                Column {
                    if (pairedDevices.isEmpty()) Text("No paired printers found.", color = DT.SubText)
                    else pairedDevices.forEach { device ->
                        TextButton(onClick = {
                            scope.launch {
                                showPrinterDialog = false; printerStatus = "Connecting…"
                                val result = printer.connect(device.address)
                                if (result is PrintResult.Success) {
                                    settingsRepo.setPrinterAddress(device.address, device.name ?: "Printer")
                                    printerStatus = "✓ Connected to ${device.name}"
                                } else printerStatus = (result as PrintResult.Error).message
                            }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(device.name ?: device.address, color = DT.OnSurface)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPrinterDialog = false }) { Text("Close", color = DT.SubText) } }
        )
    }
}

@Composable
private fun DarkSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(DT.Surface)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, color = DT.OnSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            content()
        }
    }
}

@Composable
private fun DarkSettingRow(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label, color = DT.SubText, style = MaterialTheme.typography.labelSmall) },
        singleLine = true, modifier = Modifier.fillMaxWidth(), colors = darkFieldColors(), shape = RoundedCornerShape(10.dp))
}

@Composable
private fun DarkMenuRow(label: String, icon: ImageVector, trailingIcon: ImageVector?, color: Color, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, color = color, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            trailingIcon?.let { Icon(it, null, tint = DT.SubText, modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun DarkDivider() = HorizontalDivider(color = DT.Border, thickness = 0.5.dp)

@Composable
private fun darkFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border,
    focusedTextColor = DT.OnSurface, unfocusedTextColor = DT.OnSurface,
    cursorColor = DT.Teal, focusedContainerColor = DT.Bg, unfocusedContainerColor = DT.Bg
)
