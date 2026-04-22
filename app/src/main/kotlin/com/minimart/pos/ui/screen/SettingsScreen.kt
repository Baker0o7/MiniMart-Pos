package com.minimart.pos.ui.screen

import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.minimart.pos.data.entity.UserRole
import com.minimart.pos.data.repository.SettingsRepository
import com.minimart.pos.printer.PrintResult
import com.minimart.pos.printer.ThermalPrinter
import com.minimart.pos.ui.theme.DT
import com.minimart.pos.util.RoleManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onShifts: () -> Unit,
    onUsers: () -> Unit,
    onLogout: () -> Unit,
    settingsRepo: SettingsRepository,
    printer: ThermalPrinter,
    currentRole: UserRole? = null   // passed from NavGraph
) {
    val scope   = rememberCoroutineScope()
    val context = LocalContext.current
    val rm      = RoleManager

    // Flows
    val storeName     by settingsRepo.storeName.collectAsState("")
    val currency      by settingsRepo.currency.collectAsState("KES")
    val receiptFooter by settingsRepo.receiptFooter.collectAsState("")
    val darkMode      by settingsRepo.darkMode.collectAsState(false)
    val mpesaPaybill  by settingsRepo.mpesaPaybill.collectAsState("")
    val mpesaTill     by settingsRepo.mpesaTill.collectAsState("")
    val mpesaWithdraw by settingsRepo.mpesaWithdraw.collectAsState("")
    val mpesaName     by settingsRepo.mpesaAccountName.collectAsState("")
    val printerName   by settingsRepo.printerName.collectAsState(null)

    // Local edit state
    var storeNameInput by remember { mutableStateOf("") }
    var currencyInput  by remember { mutableStateOf("KES") }
    var footerInput    by remember { mutableStateOf("") }
    var paybillInput   by remember { mutableStateOf("") }
    var tillInput      by remember { mutableStateOf("") }
    var withdrawInput  by remember { mutableStateOf("") }
    var nameInput      by remember { mutableStateOf("") }

    var showPrinterDialog by remember { mutableStateOf(false) }
    var pairedDevices     by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var printerStatus     by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(storeName, currency, receiptFooter, mpesaPaybill, mpesaTill, mpesaWithdraw, mpesaName) {
        storeNameInput = storeName; currencyInput = currency; footerInput = receiptFooter
        paybillInput = mpesaPaybill; tillInput = mpesaTill; withdrawInput = mpesaWithdraw; nameInput = mpesaName
    }

    // Role restriction banner
    val isAdmin = rm.canAccessSettings(currentRole)

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
                    // Role chip
                    currentRole?.let { role ->
                        val color = Color(RoleManager.roleBadgeColor(role).toLong() or 0xFF000000L)
                        Box(modifier = Modifier.clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(0.15f)).border(1.dp, Color.White.copy(0.3f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 5.dp)) {
                            Text(RoleManager.roleLabel(role), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                // Cashier restriction notice
                if (!isAdmin) {
                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(DT.Amber.copy(0.12f)).border(1.dp, DT.Amber.copy(0.3f), RoundedCornerShape(14.dp)).padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, null, tint = DT.Amber, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Cashier Access Only", color = DT.Amber, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("Contact your manager to change settings.", color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                // ── Store Information ─────────────────────────────────────────
                if (isAdmin) {
                    DSection("Store Information", Icons.Default.Store) {
                        DField(storeNameInput, { storeNameInput = it }, "Store Name")
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("Currency", color = DT.OnSurface, modifier = Modifier.weight(1f))
                            OutlinedTextField(currencyInput, { currencyInput = it }, singleLine = true,
                                modifier = Modifier.width(90.dp), shape = RoundedCornerShape(10.dp), colors = dColors())
                        }
                        Spacer(Modifier.height(8.dp))
                        DField(footerInput, { footerInput = it }, "Receipt Footer Message")
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { scope.launch {
                                settingsRepo.setStoreName(storeNameInput)
                                settingsRepo.setCurrency(currencyInput)
                                settingsRepo.setReceiptFooter(footerInput)
                            }},
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DT.Teal)
                        ) { Text("Save Store Settings", color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                }

                // ── M-Pesa Configuration ──────────────────────────────────────
                DSection("M-Pesa Configuration", Icons.Default.PhoneAndroid) {
                    if (!isAdmin) {
                        // Read-only for cashiers — just show the till number
                        if (tillInput.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PhoneAndroid, null, tint = DT.Teal, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Till Number: $tillInput", color = DT.OnSurface, fontWeight = FontWeight.SemiBold)
                            }
                        } else {
                            Text("M-Pesa not configured. Contact your manager.", color = DT.SubText, style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        // Account name
                        DField(nameInput, { nameInput = it }, "Account / Business Name")
                        Spacer(Modifier.height(8.dp))

                        // Paybill
                        SSubTitle("Paybill (for business payments)")
                        DField(paybillInput, { paybillInput = it }, "Paybill Number", Icons.Default.Business)
                        Spacer(Modifier.height(8.dp))

                        // Till
                        SSubTitle("Buy Goods / Till Number")
                        DField(tillInput, { tillInput = it }, "Till Number", Icons.Default.PointOfSale)
                        Spacer(Modifier.height(8.dp))

                        // Withdraw
                        SSubTitle("Withdrawal / Agent Number")
                        DField(withdrawInput, { withdrawInput = it }, "Agent / Withdraw Number", Icons.Default.Money)
                        Spacer(Modifier.height(4.dp))
                        Text("Used for end-of-day withdrawal reminders.", color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(12.dp))

                        Button(
                            onClick = { scope.launch {
                                settingsRepo.setMpesaPaybill(paybillInput)
                                settingsRepo.setMpesaTill(tillInput)
                                settingsRepo.setMpesaWithdraw(withdrawInput)
                                settingsRepo.setMpesaAccountName(nameInput)
                            }},
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20))
                        ) {
                            Icon(Icons.Default.PhoneAndroid, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Save M-Pesa Settings", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // ── Thermal Printer ───────────────────────────────────────────
                if (isAdmin) {
                    DSection("Thermal Printer", Icons.Default.Print) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(printerName ?: "No printer paired",
                                    color = if (printer.isConnected) DT.Teal else DT.SubText,
                                    fontWeight = FontWeight.SemiBold)
                                Text(if (printer.isConnected) "Connected" else "Disconnected",
                                    color = if (printer.isConnected) DT.Green else DT.Red,
                                    style = MaterialTheme.typography.labelSmall)
                            }
                            Button(
                                onClick = { pairedDevices = printer.getPairedPrinters(context); showPrinterDialog = true },
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DT.Teal),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                            ) { Text("Pair", color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                        printerStatus?.let { Text(it, color = if (it.startsWith("✓")) DT.Green else DT.Red, style = MaterialTheme.typography.labelSmall) }
                    }
                }

                // ── Appearance ────────────────────────────────────────────────
                DSection("Appearance", Icons.Default.Palette) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DarkMode, null, tint = DT.SubText, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Dark Mode", color = DT.OnSurface, modifier = Modifier.weight(1f))
                        Switch(checked = darkMode, onCheckedChange = { scope.launch { settingsRepo.setDarkMode(it) } },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DT.Teal))
                    }
                }

                // ── Data & Backup ─────────────────────────────────────────────
                if (isAdmin) {
                    DSection("Data & Backup", Icons.Default.Storage) {
                        var backupStatus by remember { mutableStateOf<String?>(null) }
                        var isBackingUp  by remember { mutableStateOf(false) }
                        var backupFiles  by remember { mutableStateOf<List<java.io.File>>(emptyList()) }
                        var showRestore  by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { backupFiles = com.minimart.pos.util.BackupManager.listBackups() }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = {
                                isBackingUp = true
                                scope.launch {
                                    val r = com.minimart.pos.util.BackupManager.backup(context)
                                    backupStatus = when (r) {
                                        is com.minimart.pos.util.BackupResult.Success -> { backupFiles = com.minimart.pos.util.BackupManager.listBackups(); r.message }
                                        is com.minimart.pos.util.BackupResult.Error -> r.message
                                    }
                                    isBackingUp = false
                                }
                            }, modifier = Modifier.weight(1f), enabled = !isBackingUp,
                                shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = DT.Teal)) {
                                if (isBackingUp) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                else Icon(Icons.Default.Backup, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp)); Text("Backup", color = Color.White)
                            }
                            OutlinedButton(onClick = { backupFiles = com.minimart.pos.util.BackupManager.listBackups(); showRestore = true },
                                modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, DT.Border)) {
                                Icon(Icons.Default.Restore, null, tint = DT.OnSurface, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp)); Text("Restore", color = DT.OnSurface)
                            }
                        }
                        backupStatus?.let { Text(it, color = if ("saved" in it) DT.Green else DT.Red, style = MaterialTheme.typography.labelSmall) }
                        if (backupFiles.isNotEmpty()) {
                            TextButton(onClick = { com.minimart.pos.util.BackupManager.shareBackup(context, backupFiles.first()) },
                                contentPadding = PaddingValues(0.dp)) {
                                Icon(Icons.Default.Share, null, tint = DT.Teal, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Share Latest Backup (USB/OTG/Cloud)", color = DT.Teal, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (showRestore) {
                            AlertDialog(onDismissRequest = { showRestore = false }, containerColor = DT.Surface,
                                title = { Text("Restore Database", color = DT.OnSurface, fontWeight = FontWeight.Bold) },
                                text = {
                                    Column {
                                        Text("Select a backup to restore.", color = DT.SubText, style = MaterialTheme.typography.bodySmall)
                                        Spacer(Modifier.height(8.dp))
                                        if (backupFiles.isEmpty()) Text("No backups found.", color = DT.Red, style = MaterialTheme.typography.labelSmall)
                                        else backupFiles.take(5).forEach { f ->
                                            TextButton(onClick = {
                                                showRestore = false
                                                scope.launch {
                                                    val r = com.minimart.pos.util.BackupManager.restore(context, f)
                                                    backupStatus = when (r) {
                                                        is com.minimart.pos.util.BackupResult.Success -> r.message
                                                        is com.minimart.pos.util.BackupResult.Error -> r.message
                                                    }
                                                }
                                            }, modifier = Modifier.fillMaxWidth()) { Text(f.name, color = DT.TealLight, style = MaterialTheme.typography.bodySmall) }
                                        }
                                    }
                                },
                                confirmButton = { TextButton(onClick = { showRestore = false }) { Text("Cancel", color = DT.SubText) } })
                        }
                    }
                }

                // ── Account ───────────────────────────────────────────────────
                DSection("Account", Icons.Default.ManageAccounts) {
                    if (rm.canManageUsers(currentRole)) {
                        DMenuRow("User Management", Icons.Default.Group, DT.OnSurface) { onUsers() }
                        HDivider()
                    }
                    if (rm.canManageShifts(currentRole)) {
                        DMenuRow("Shift Management", Icons.Default.Schedule, DT.OnSurface) { onShifts() }
                        HDivider()
                    }
                    DMenuRow("Logout", Icons.AutoMirrored.Filled.Logout, DT.Red) { onLogout() }
                }
            }
        }
    }

    if (showPrinterDialog) {
        AlertDialog(onDismissRequest = { showPrinterDialog = false }, containerColor = DT.Surface,
            title = { Text("Select Printer", color = DT.OnSurface) },
            text = {
                Column {
                    if (pairedDevices.isEmpty()) Text("No paired Bluetooth printers found. Pair in phone Settings first.", color = DT.SubText, style = MaterialTheme.typography.bodySmall)
                    else pairedDevices.forEach { device ->
                        TextButton(onClick = {
                            scope.launch {
                                showPrinterDialog = false
                                val result = printer.connect(device.address)
                                if (result is PrintResult.Success) {
                                    settingsRepo.setPrinterAddress(device.address, device.name ?: "Printer")
                                    printerStatus = "✓ Connected to ${device.name}"
                                } else printerStatus = (result as PrintResult.Error).message
                            }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Print, null, tint = DT.Teal, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(device.name ?: device.address, color = DT.OnSurface)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPrinterDialog = false }) { Text("Close", color = DT.SubText) } })
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

@Composable
private fun DSection(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(DT.Surface)
        .border(1.dp, DT.Border, RoundedCornerShape(18.dp))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = DT.Teal, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, color = DT.OnSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun DField(value: String, onValueChange: (String) -> Unit, label: String, icon: ImageVector? = null) {
    OutlinedTextField(value = value, onValueChange = onValueChange,
        label = { Text(label, color = DT.SubText, style = MaterialTheme.typography.labelSmall) },
        leadingIcon = icon?.let { { Icon(it, null, tint = DT.SubText, modifier = Modifier.size(18.dp)) } },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp), colors = dColors())
}

@Composable private fun SSubTitle(text: String) {
    Text(text, color = DT.SubText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 4.dp))
}

@Composable private fun HDivider() = HorizontalDivider(color = DT.Border, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

@Composable private fun DMenuRow(label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, color = color, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, null, tint = DT.SubText, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable private fun dColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border,
    focusedTextColor = DT.OnSurface, unfocusedTextColor = DT.OnSurface,
    cursorColor = DT.Teal, focusedContainerColor = DT.Bg, unfocusedContainerColor = DT.Bg
)
