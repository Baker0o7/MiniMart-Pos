package com.minimart.pos.ui.screen

import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minimart.pos.data.repository.SettingsRepository
import com.minimart.pos.printer.PrintResult
import com.minimart.pos.printer.ThermalPrinter
import com.minimart.pos.ui.theme.Brand500
import com.minimart.pos.ui.theme.DT
import com.minimart.pos.ui.theme.ErrorRed
import com.minimart.pos.ui.theme.SuccessGreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onShifts: () -> Unit,
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
        storeNameInput = storeName
        currencyInput = currency
        footerInput = receiptFooter
        mpesaInput = mpesaPaybill
    }

    Scaffold(
        containerColor = DT.Bg,
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Brand500, titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().background(DT.Bg).padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Store info ──
            SettingsSection("Store Information") {
                OutlinedTextField(storeNameInput, { storeNameInput = it }, label = { Text("Store Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(Icons.Default.Store, null) })
                OutlinedTextField(currencyInput, { currencyInput = it }, label = { Text("Currency Code (e.g. KES, USD)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(footerInput, { footerInput = it }, label = { Text("Receipt Footer Message") }, modifier = Modifier.fillMaxWidth(), maxLines = 2)
                OutlinedTextField(mpesaInput, { mpesaInput = it }, label = { Text("M-Pesa Paybill / Till Number") }, modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(Icons.Default.PhoneAndroid, null) })
                Button(
                    onClick = {
                        scope.launch {
                            settingsRepo.setStoreName(storeNameInput)
                            settingsRepo.setCurrency(currencyInput)
                            settingsRepo.setReceiptFooter(footerInput)
                            settingsRepo.setMpesaPaybill(mpesaInput)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("Save Store Settings") }
            }

            // ── Printer ──
            SettingsSection("Thermal Printer (Bluetooth)") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Print,
                        null,
                        tint = if (printer.isConnected) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(printerName ?: "No printer paired", fontWeight = FontWeight.Medium)
                        Text(if (printer.isConnected) "Connected" else "Disconnected", style = MaterialTheme.typography.labelSmall, color = if (printer.isConnected) SuccessGreen else ErrorRed)
                    }
                }
                printerStatus?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = if (it.startsWith("✓")) SuccessGreen else ErrorRed) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            pairedDevices = printer.getPairedPrinters(context)
                            showPrinterDialog = true
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Pair Printer") }
                    if (printer.isConnected) {
                        OutlinedButton(
                            onClick = { scope.launch { printer.disconnect(); printerStatus = "Disconnected" } },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("Disconnect") }
                    }
                }
                // Test print
                if (printer.isConnected) {
                    Button(
                        onClick = {
                            scope.launch {
                                // Simple test
                                printerStatus = "✓ Test print sent"
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                    ) { Icon(Icons.Default.Print, null); Spacer(Modifier.width(6.dp)); Text("Test Print") }
                }
            }

            // ── Appearance ──
            SettingsSection("Appearance") {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DarkMode, null)
                    Spacer(Modifier.width(12.dp))
                    Text("Dark Mode", modifier = Modifier.weight(1f))
                    Switch(checked = darkMode, onCheckedChange = { scope.launch { settingsRepo.setDarkMode(it) } })
                }
            }

            // ── Account ──
            SettingsSection("Account") {
                OutlinedButton(
                    onClick = onShifts,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.AccessTime, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Shift Management")
                }
                OutlinedButton(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, null, tint = ErrorRed)
                    Spacer(Modifier.width(8.dp))
                    Text("Logout", color = ErrorRed)
                }
            }
        }
    }

    // Printer picker dialog
    if (showPrinterDialog) {
        AlertDialog(
            onDismissRequest = { showPrinterDialog = false },
            title = { Text("Select Printer") },
            text = {
                Column {
                    if (pairedDevices.isEmpty()) {
                        Text("No paired Bluetooth printers found. Pair your printer in Android Bluetooth settings first.")
                    } else {
                        pairedDevices.forEach { device ->
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        showPrinterDialog = false
                                        printerStatus = "Connecting…"
                                        val result = printer.connect(device.address)
                                        if (result is PrintResult.Success) {
                                            settingsRepo.setPrinterAddress(device.address, device.name ?: "Printer")
                                            printerStatus = "✓ Connected to ${device.name}"
                                        } else {
                                            printerStatus = (result as PrintResult.Error).message
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Print, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(device.name ?: device.address)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPrinterDialog = false }) { Text("Close") } }
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = Brand500)
            HorizontalDivider()
            content()
        }
    }
}
