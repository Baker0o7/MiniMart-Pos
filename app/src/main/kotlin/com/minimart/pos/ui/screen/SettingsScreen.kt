package com.minimart.pos.ui.screen

import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    syncVm: SyncViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
    cloudVm: com.minimart.pos.ui.viewmodel.CloudViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
    integrityVm: com.minimart.pos.ui.viewmodel.IntegrityViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
    ownerGate: com.minimart.pos.ui.viewmodel.OwnerGateViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
    onBack: () -> Unit,
    onShifts: () -> Unit,
    onUsers: () -> Unit,
    onLogout: () -> Unit,
    settingsRepo: SettingsRepository,
    printer: ThermalPrinter,
    currentRole: UserRole? = null,   // passed from NavGraph
    currentUserName: String? = null
) {
    val scope   = rememberCoroutineScope()
    val context = LocalContext.current
    val rm      = RoleManager

    // Flows
    val storeName     by settingsRepo.storeName.collectAsStateWithLifecycle("")
    val currency      by settingsRepo.currency.collectAsStateWithLifecycle("KES")
    val receiptFooter by settingsRepo.receiptFooter.collectAsStateWithLifecycle("")
    val darkMode      by settingsRepo.darkMode.collectAsStateWithLifecycle(false)
    val expiryAlertMonths by settingsRepo.expiryAlertMonths.collectAsStateWithLifecycle(1)
    val printerName   by settingsRepo.printerName.collectAsStateWithLifecycle(null)

    // Local edit state
    var storeNameInput by remember { mutableStateOf("") }
    var currencyInput  by remember { mutableStateOf("KES") }
    var footerInput    by remember { mutableStateOf("") }

    var showPrinterDialog by remember { mutableStateOf(false) }
    var pairedDevices     by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var printerStatus     by remember { mutableStateOf<String?>(null) }
    // Android 12+ needs BLUETOOTH_CONNECT granted at runtime before paired devices can be listed.
    val btPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) { pairedDevices = printer.getPairedPrinters(context); showPrinterDialog = true }
        else printerStatus = "Allow Bluetooth access to find your printer"
    }

    // Re-sync the editable store fields whenever the saved values change.
    LaunchedEffect(storeName, currency, receiptFooter) {
        storeNameInput = storeName; currencyInput = currency; footerInput = receiptFooter
    }

    // Role restriction banner
    val isAdmin = rm.canAccessSettings(currentRole)
    val isOwner = rm.canManageSystem(currentRole)

    Box(modifier = Modifier.fillMaxSize().background(DT.Bg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            GradientHeader(title = tr(com.minimart.pos.R.string.settings), subtitle = storeName.takeIf { it.isNotBlank() }, onBack = onBack)

            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                // Who is signed in
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(DT.Surface)
                        .border(1.dp, DT.Border, RoundedCornerShape(18.dp)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(DT.TealDim), contentAlignment = Alignment.Center) {
                        Text(currentUserName?.trim()?.firstOrNull()?.uppercase() ?: "?",
                            color = DT.TealLight, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(currentUserName ?: "Signed in", color = DT.OnSurface, fontWeight = FontWeight.Bold,
                            fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(storeName.ifBlank { "MiniMart POS" }, color = DT.SubText, fontSize = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    currentRole?.let { role ->
                        // roleBadgeColor is a Long literal with the alpha bit set; unsigned conversion
                        // keeps Color() from receiving a negative packed value.
                        val badge = if (role == UserRole.OWNER) DT.Teal else Color(RoleManager.roleBadgeColor(role).toUInt().toLong())
                        Box(Modifier.clip(RoundedCornerShape(20.dp)).background(badge.copy(0.22f))
                            .border(1.dp, badge.copy(0.6f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 5.dp)) {
                            Text(RoleManager.roleLabel(role), color = DT.Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Cashier restriction notice
                if (!isAdmin) {
                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(DT.Amber.copy(0.12f)).border(1.dp, DT.Amber.copy(0.3f), RoundedCornerShape(14.dp)).padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, null, tint = DT.Amber, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(tr(com.minimart.pos.R.string.cashier_only), color = DT.Amber, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(tr(com.minimart.pos.R.string.contact_manager), color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                // ── Store Information ─────────────────────────────────────────
                if (isAdmin) {
                    DSection(tr(com.minimart.pos.R.string.set_store_info), Icons.Default.Store) {
                        DField(storeNameInput, { storeNameInput = it }, "Store Name", isText = true)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(tr(com.minimart.pos.R.string.currency), color = DT.OnSurface, modifier = Modifier.weight(1f))
                            OutlinedTextField(currencyInput, { currencyInput = it }, singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters, autoCorrectEnabled = false, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                                modifier = Modifier.width(90.dp), shape = RoundedCornerShape(10.dp), colors = dColors())
                        }
                        Spacer(Modifier.height(8.dp))
                        DField(footerInput, { footerInput = it }, "Receipt Footer Message", isText = true)
                        Spacer(Modifier.height(12.dp))
                        val cleanCurrency = currencyInput.trim().uppercase().take(5)
                        val storeDirty = storeNameInput.trim() != storeName || cleanCurrency != currency || footerInput.trim() != receiptFooter
                        val storeValid = storeNameInput.isNotBlank() && cleanCurrency.isNotEmpty()
                        if (!storeValid) Text(tr(com.minimart.pos.R.string.store_empty), color = DT.Red,
                            style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 6.dp))
                        SaveButton("Save Store Settings", enabled = storeDirty && storeValid, color = DT.Teal) {
                            scope.launch {
                                settingsRepo.setStoreName(storeNameInput.trim())
                                settingsRepo.setCurrency(cleanCurrency); currencyInput = cleanCurrency
                                settingsRepo.setReceiptFooter(footerInput.trim())
                            }
                        }
                    }
                }

                // ── M-Pesa STK Push (Daraja API) ─────────────────────────────
                if (isOwner) DarajaSettingsSection(settingsRepo, ownerGate)

                // ── Thermal Printer ───────────────────────────────────────────
                if (isAdmin) {
                    DSection(tr(com.minimart.pos.R.string.set_printer), Icons.Default.Print,
                        summary = if (printer.isConnected) (printerName ?: "Connected") else (printerName ?: "Not paired"),
                        initiallyExpanded = false) {
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
                                onClick = {
                                    val needsPermission = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
                                        androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) !=
                                        android.content.pm.PackageManager.PERMISSION_GRANTED
                                    if (needsPermission) btPermission.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
                                    else { pairedDevices = printer.getPairedPrinters(context); showPrinterDialog = true }
                                },
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DT.Teal),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                            ) { Text(if (printerName.isNullOrBlank()) "Pair" else "Change", color = DT.Ink, fontWeight = FontWeight.Bold) }
                        }
                        printerStatus?.let { Text(it, color = if (it.startsWith("✓")) DT.Green else DT.Red, style = MaterialTheme.typography.labelSmall) }
                        val paperMm by settingsRepo.paperWidthMm.collectAsStateWithLifecycle(58)
                        Spacer(Modifier.height(10.dp))
                        Text(tr(com.minimart.pos.R.string.paper_width), color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(58, 80).forEach { mm ->
                                FilterChip(
                                    selected = paperMm == mm,
                                    onClick = { scope.launch { settingsRepo.setPaperWidthMm(mm) } },
                                    label = { Text("$mm mm", fontSize = 12.sp) }
                                )
                            }
                        }
                    }
                }

                // ── Cash Drawer ───────────────────────────────────────────────
                if (isAdmin) {
                    val cashDrawerAddress by settingsRepo.cashDrawerAddress.collectAsStateWithLifecycle("")
                    val cashDrawerOnSale  by settingsRepo.cashDrawerOnSale.collectAsStateWithLifecycle(true)
                    var testStatus by remember { mutableStateOf<String?>(null) }
                    // Local text, saved when the field loses focus: writing DataStore on every keystroke
                    // and echoing it back made typing laggy and could drop characters.
                    var drawerInput by remember { mutableStateOf("") }
                    var drawerTouched by remember { mutableStateOf(false) }
                    LaunchedEffect(cashDrawerAddress) { drawerInput = cashDrawerAddress }

                    DSection(tr(com.minimart.pos.R.string.set_drawer), Icons.Default.LocalAtm,
                        summary = if (cashDrawerOnSale) "Opens on cash sale" else "Manual only",
                        initiallyExpanded = false) {
                        Text(tr(com.minimart.pos.R.string.drawer_autodetect),
                            color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(10.dp))
                        // Auto open on sale toggle
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(tr(com.minimart.pos.R.string.open_on_cash), color = DT.Ink, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                Text(tr(com.minimart.pos.R.string.auto_open_cash), color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                            }
                            Switch(checked = cashDrawerOnSale, onCheckedChange = { scope.launch { settingsRepo.setCashDrawerOnSale(it) } },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DT.Teal, uncheckedTrackColor = DT.Border, uncheckedThumbColor = DT.SubText, uncheckedBorderColor = DT.SubText.copy(0.6f)))
                        }
                        Spacer(Modifier.height(10.dp))
                        // Direct BT drawer address (optional)
                        OutlinedTextField(value = drawerInput, onValueChange = { drawerInput = it },
                            label = { Text(tr(com.minimart.pos.R.string.drawer_bt_addr), color = DT.SubText) },
                            placeholder = { Text("00:11:22:33:44:55", color = DT.SubText.copy(0.5f)) },
                            singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters, autoCorrectEnabled = false, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                            modifier = Modifier.fillMaxWidth().onFocusChanged { f ->
                                if (f.isFocused) drawerTouched = true
                                else if (drawerTouched && drawerInput.trim() != cashDrawerAddress) {
                                    scope.launch { settingsRepo.setCashDrawerAddress(drawerInput.trim()) }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border,
                                focusedTextColor = DT.Ink, unfocusedTextColor = DT.Ink,
                                focusedContainerColor = DT.Bg, unfocusedContainerColor = DT.Bg))
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = {
                                scope.launch {
                                    testStatus = "Opening drawer..."
                                    // Just send kick via printer if connected
                                    val r = printer.sendRaw(com.minimart.pos.printer.CashDrawerManager.kickCommand(settingsRepo.cashDrawerPin.first()))
                                    testStatus = if (r is com.minimart.pos.printer.PrintResult.Success) "✓ Drawer opened!" else "✗ Not connected to printer"
                                }
                            }, border = androidx.compose.foundation.BorderStroke(1.dp, DT.Teal), shape = RoundedCornerShape(12.dp)) {
                                Icon(Icons.Default.LocalAtm, null, tint = DT.Teal, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(tr(com.minimart.pos.R.string.test_open), color = DT.Teal, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        testStatus?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(it, color = if (it.startsWith("✓")) DT.Green else DT.Red, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // ── Shift close & customer display ─────────────────────────────
                if (isAdmin) {
                    val blindClose by settingsRepo.blindClose.collectAsStateWithLifecycle(true)
                    val secondScreen by settingsRepo.customerDisplayEnabled.collectAsStateWithLifecycle(false)
                    val kickPin by settingsRepo.cashDrawerPin.collectAsStateWithLifecycle(0)
                    DSection(tr(com.minimart.pos.R.string.set_shift_display), Icons.Default.Tv,
                        summary = if (blindClose) "Blind close on" else "Blind close off",
                        initiallyExpanded = false) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(tr(com.minimart.pos.R.string.blind_close_lbl), color = DT.Ink, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                Text(tr(com.minimart.pos.R.string.blind_help), color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                            }
                            Switch(checked = blindClose, onCheckedChange = { scope.launch { settingsRepo.setBlindClose(it) } },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DT.Teal, uncheckedTrackColor = DT.Border, uncheckedThumbColor = DT.SubText, uncheckedBorderColor = DT.SubText.copy(0.6f)))
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(tr(com.minimart.pos.R.string.cust_display), color = DT.Ink, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                Text(tr(com.minimart.pos.R.string.cust_display_help), color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                            }
                            Switch(checked = secondScreen, onCheckedChange = { scope.launch { settingsRepo.setCustomerDisplayEnabled(it) } },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DT.Teal, uncheckedTrackColor = DT.Border, uncheckedThumbColor = DT.SubText, uncheckedBorderColor = DT.SubText.copy(0.6f)))
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(tr(com.minimart.pos.R.string.drawer_pin), color = DT.Ink, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Text(tr(com.minimart.pos.R.string.drawer_pin_help), color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                            FilterChip(selected = kickPin == 0, onClick = { scope.launch { settingsRepo.setCashDrawerPin(0) } }, label = { Text("Pin 2") })
                            FilterChip(selected = kickPin == 1, onClick = { scope.launch { settingsRepo.setCashDrawerPin(1) } }, label = { Text("Pin 5") })
                        }
                    }
                }

                // ── Bluetooth Scanner ─────────────────────────────────────────
                DSection(tr(com.minimart.pos.R.string.set_bt_scanner), Icons.Default.QrCodeScanner, initiallyExpanded = false) {
                    val beepContext = androidx.compose.ui.platform.LocalContext.current
                    var beepOn by remember { mutableStateOf(com.minimart.pos.util.ScanFeedback.isOn(beepContext)) }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(tr(com.minimart.pos.R.string.beep_scan), color = DT.Ink, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            Text(tr(com.minimart.pos.R.string.beep_help), color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                        }
                        Switch(checked = beepOn, onCheckedChange = { beepOn = it; com.minimart.pos.util.ScanFeedback.setOn(beepContext, it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DT.Teal, uncheckedTrackColor = DT.Border, uncheckedThumbColor = DT.SubText, uncheckedBorderColor = DT.SubText.copy(0.6f)))
                    }
                    Spacer(Modifier.height(10.dp))
                    var hapticOn by remember { mutableStateOf(com.minimart.pos.util.ScanFeedback.isHapticOn(beepContext)) }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(tr(com.minimart.pos.R.string.haptic_title), color = DT.Ink, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            Text(tr(com.minimart.pos.R.string.haptic_help), color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                        }
                        Switch(checked = hapticOn, onCheckedChange = { hapticOn = it; com.minimart.pos.util.ScanFeedback.setHapticOn(beepContext, it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DT.Teal, uncheckedTrackColor = DT.Border, uncheckedThumbColor = DT.SubText, uncheckedBorderColor = DT.SubText.copy(0.6f)))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(tr(com.minimart.pos.R.string.hid_help),
                        color = DT.SubText, style = MaterialTheme.typography.labelSmall, lineHeight = 18.sp)
                    Spacer(Modifier.height(10.dp))
                    val btScanners = remember {
                        try {
                            @Suppress("DEPRECATION")
                            val btAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                            btAdapter
                                ?.bondedDevices?.filter { d ->
                                    val name = (d.name ?: "").lowercase()
                                    listOf("scanner","barcode","honeywell","zebra","datalogic","newland","sunmi")
                                        .any { name.contains(it) } || d.bluetoothClass?.majorDeviceClass == 0x0500
                                }?.map { it.name ?: it.address } ?: emptyList()
                        } catch (_: Exception) { emptyList() }
                    }
                    if (btScanners.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.BluetoothDisabled, null, tint = DT.SubText, modifier = Modifier.size(18.dp))
                            Text(tr(com.minimart.pos.R.string.no_bt_scanners), color = DT.SubText, style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        btScanners.forEach { name ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 4.dp)) {
                                Icon(Icons.Default.Bluetooth, null, tint = DT.Teal, modifier = Modifier.size(18.dp))
                                Text(name, color = DT.Ink, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                Box(Modifier.clip(RoundedCornerShape(6.dp)).background(DT.Green.copy(0.15f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                    Text(tr(com.minimart.pos.R.string.paired), color = DT.Green, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(tr(com.minimart.pos.R.string.scan_tip), color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                }

                // ── Expiry Alerts ─────────────────────────────────────────────
                DSection(tr(com.minimart.pos.R.string.set_expiry), Icons.Default.CalendarToday,
                    summary = "$expiryAlertMonths ${if (expiryAlertMonths == 1) "month" else "months"} ahead") {
                    Text(tr(com.minimart.pos.R.string.alert_expire_within), color = DT.SubText,
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(1 to "1 month", 2 to "2 months", 3 to "3 months").forEach { (months, label) ->
                            val selected = expiryAlertMonths == months
                            Box(
                                modifier = Modifier.weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) DT.Teal else DT.Bg)
                                    .border(1.dp, if (selected) DT.Teal else DT.Border, RoundedCornerShape(12.dp))
                                    // Shop-wide setting: the cashier notice says settings are the manager's to change.
                                    .clickable(role = androidx.compose.ui.semantics.Role.Button, enabled = isAdmin) { scope.launch { settingsRepo.setExpiryAlertMonths(months) } }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label,
                                    color = if (selected) Color.White else DT.SubText,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(tr(com.minimart.pos.R.string.expiry_check_help),
                        color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                }

                // ── Appearance ────────────────────────────────────────────────
                AppearanceSettingsSection(settingsRepo)
                LanguageSettingsSection()

                // ── Multi-Device Sync (LAN) ───────────────────────────────────
                if (isOwner) {
                    val syncState by syncVm.state.collectAsStateWithLifecycle()
                    DSection(tr(com.minimart.pos.R.string.set_sync), Icons.Default.Sync,
                        summary = if (syncState.serverRunning) "Server running" else "Off",
                        initiallyExpanded = false) {
                        Text("Device ID: ${syncState.deviceId.take(8)}…", color = DT.SubText, fontSize = 11.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("One device is the main device (it acts as server). Other tills enter its address and code: they send their sales to it, then receive its products, stock and customer balances. Manage products on the main device. Sales made before a till was paired are not sent.",
                            color = DT.SubText, fontSize = 11.sp, lineHeight = 15.sp)
                        Spacer(Modifier.height(10.dp))

                        // This device as server
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(tr(com.minimart.pos.R.string.act_sync_server), color = DT.Ink, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                if (syncState.serverRunning)
                                    Text("Server IP: ${syncState.serverIp}:9876", color = DT.Teal, fontSize = 11.sp)
                                else
                                    Text(tr(com.minimart.pos.R.string.others_connect), color = DT.SubText, fontSize = 11.sp)
                            }
                            Switch(
                                checked = syncState.serverRunning,
                                onCheckedChange = { if (it) syncVm.startServer() else syncVm.stopServer() },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DT.Teal, uncheckedTrackColor = DT.Border, uncheckedThumbColor = DT.SubText, uncheckedBorderColor = DT.SubText.copy(0.6f))
                            )
                        }

                        // Bug fix: SyncServer previously had NO authentication — any device on
                        // the same WiFi could read or inject sync data. Show this device's
                        // pairing code so it can be typed into the device that wants to connect.
                        if (syncState.serverRunning) {
                            Spacer(Modifier.height(10.dp))
                            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(DT.Teal.copy(0.1f)).border(1.dp, DT.Teal.copy(0.3f), RoundedCornerShape(12.dp))
                                .padding(12.dp)) {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Lock, null, tint = DT.Teal, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(tr(com.minimart.pos.R.string.pairing_enter_other), color = DT.Teal, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(com.minimart.pos.util.PairingCode.grouped(syncState.mySecret), color = DT.Ink, fontSize = 20.sp,
                                        fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                                    Text(tr(com.minimart.pos.R.string.sync_encrypted), color = DT.SubText, fontSize = 11.sp)
                                    var confirmReset by remember { mutableStateOf(false) }
                                    TextButton(onClick = { confirmReset = true }, contentPadding = PaddingValues(0.dp)) {
                                        Text(if (syncState.mySecret.length < com.minimart.pos.util.PairingCode.LENGTH) "Upgrade to a stronger code" else "Make a new code",
                                            color = DT.Teal, fontSize = 12.sp)
                                    }
                                    if (confirmReset) AlertDialog(
                                        onDismissRequest = { confirmReset = false },
                                        containerColor = DT.Surface,
                                        title = { Text(tr(com.minimart.pos.R.string.make_new_code_q), color = DT.Ink) },
                                        text = { Text(tr(com.minimart.pos.R.string.new_code_help), color = DT.SubText) },
                                        confirmButton = { TextButton(onClick = { syncVm.resetPairingCode(); confirmReset = false }) { Text(tr(com.minimart.pos.R.string.make_new_code), color = DT.Teal) } },
                                        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(tr(com.minimart.pos.R.string.cancel), color = DT.SubText) } }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(color = DT.Border)
                        Spacer(Modifier.height(14.dp))

                        // Connect to another server
                        Text(tr(com.minimart.pos.R.string.connect_server), color = DT.Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = syncState.peerIp,
                            onValueChange = { syncVm.setPeerIp(it) },
                            label = { Text(tr(com.minimart.pos.R.string.server_ip), color = DT.SubText) },
                            placeholder = { Text("e.g. 192.168.1.100", color = DT.SubText.copy(0.5f)) },
                            leadingIcon = { Icon(Icons.Default.Wifi, null, tint = DT.SubText) },
                            singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri, autoCorrectEnabled = false, imeAction = androidx.compose.ui.text.input.ImeAction.Next), modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border,
                                focusedTextColor = DT.Ink, unfocusedTextColor = DT.Ink,
                                focusedContainerColor = DT.Bg, unfocusedContainerColor = DT.Bg))
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = syncState.peerKey,
                            onValueChange = { syncVm.setPeerKey(it.filter(Char::isDigit).take(16)) },
                            label = { Text(tr(com.minimart.pos.R.string.pairing_code), color = DT.SubText) },
                            placeholder = { Text("Pairing code shown on the server", color = DT.SubText.copy(0.5f)) },
                            leadingIcon = { Icon(Icons.Default.Lock, null, tint = DT.SubText) },
                            singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border,
                                focusedTextColor = DT.Ink, unfocusedTextColor = DT.Ink,
                                focusedContainerColor = DT.Bg, unfocusedContainerColor = DT.Bg))
                        Spacer(Modifier.height(10.dp))

                        // Pending badge + Sync button
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (syncState.pendingCount > 0)
                                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(DT.Amber.copy(0.15f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                                    Text("${syncState.pendingCount} pending", color = DT.Amber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            Button(
                                onClick = { syncVm.syncNow() },
                                enabled = !syncState.isSyncing && syncState.peerIp.isNotBlank() && syncState.peerKey.isNotBlank(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DT.Teal, disabledContainerColor = DT.Surface2)
                            ) {
                                if (syncState.isSyncing) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Sync, null, tint = DT.Ink, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(tr(com.minimart.pos.R.string.sync_now), color = DT.Ink, fontSize = 13.sp)
                                }
                            }
                        }
                        syncState.lastResult?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(it, color = if (it.startsWith("✓")) DT.Green else DT.Red, fontSize = 12.sp)
                        }
                        if (syncState.lastSyncAt > 0L) {
                            Spacer(Modifier.height(4.dp))
                            Text("Last synced " + java.text.SimpleDateFormat("d MMM, HH:mm", androidx.compose.ui.text.intl.Locale.current.platformLocale).format(java.util.Date(syncState.lastSyncAt)),
                                color = DT.SubText, fontSize = 11.sp)
                        }
                        if (syncState.conflictCount > 0) {
                            Spacer(Modifier.height(8.dp))
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DT.Red.copy(0.12f)).padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("${syncState.conflictCount} sale${if (syncState.conflictCount == 1) "" else "s"} refused by the main device",
                                    color = DT.Red, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("These sales are only on this phone, so the main device's totals and stock are missing them. Retry after fixing the cause (for example a product or M-Pesa code problem); the reason is shown after a retry.",
                                    color = DT.SubText, fontSize = 11.sp)
                                OutlinedButton(onClick = { syncVm.retryRefused() },
                                    enabled = !syncState.isSyncing && syncState.peerIp.isNotBlank() && syncState.peerKey.isNotBlank(),
                                    shape = RoundedCornerShape(10.dp)) { Text(tr(com.minimart.pos.R.string.retry_refused), color = DT.Red, fontSize = 12.sp) }
                            }
                        }
                    }
                }

                // ── Cloud dashboard (multi-store) ─────────────────────────────
                if (isOwner) {
                    val cs by cloudVm.state.collectAsStateWithLifecycle()
                    val fieldColors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border,
                        focusedTextColor = DT.Ink, unfocusedTextColor = DT.Ink,
                        focusedContainerColor = DT.Bg, unfocusedContainerColor = DT.Bg)
                    DSection(tr(com.minimart.pos.R.string.set_cloud), Icons.Default.Sync,
                        summary = if (cs.connected) "Connected" else "Off",
                        initiallyExpanded = false) {
                        Text(if (cs.hosted)
                            "Sends a daily summary (sales, stock alerts, expenses, shifts) of this store to your online dashboard so you can see all your shops in one place. Customer names and receipts are not sent. On the dashboard website, sign in, press \"Add a shop\", and type the code it shows below."
                        else "Sends a daily summary (sales, stock alerts, expenses, shifts) of this store to your own Supabase project so you can see all stores in one web dashboard. Customer names and receipts are not sent. See the README for the 5-minute setup.",
                            color = DT.SubText, fontSize = 11.sp, lineHeight = 15.sp)
                        Spacer(Modifier.height(10.dp))
                        if (!cs.connected) {
                            if (!cs.hosted) {
                                OutlinedTextField(cs.url, cloudVm::setUrl, label = { Text("Project URL (https://…supabase.co)") },
                                    singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri, autoCorrectEnabled = false, imeAction = androidx.compose.ui.text.input.ImeAction.Next), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = fieldColors)
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(cs.anonKey, cloudVm::setAnonKey, label = { Text("Anon (public) key") },
                                    singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = fieldColors)
                                Spacer(Modifier.height(8.dp))
                            }
                            OutlinedTextField(cs.setupCode, cloudVm::setSetupCode, label = { Text(if (cs.hosted) "Code from your dashboard (e.g. K7QM-4TX9)" else "Setup code") },
                                singleLine = true,
                                keyboardOptions = if (cs.hosted)
                                    androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters, autoCorrectEnabled = false, imeAction = androidx.compose.ui.text.input.ImeAction.Done)
                                else androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password, autoCorrectEnabled = false),
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = fieldColors)
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = { cloudVm.connect() }, enabled = !cs.busy, shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DT.Teal, disabledContainerColor = DT.Surface2)) {
                                if (cs.busy) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                else Text(tr(com.minimart.pos.R.string.connect_upload), color = DT.Ink, fontSize = 13.sp)
                            }
                        } else {
                            Text(if (cs.lastPushAt > 0) "Last upload: " + java.text.SimpleDateFormat("d MMM, HH:mm", androidx.compose.ui.text.intl.Locale.current.platformLocale).format(java.util.Date(cs.lastPushAt)) else "Not uploaded yet",
                                color = DT.Ink, fontSize = 12.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(tr(com.minimart.pos.R.string.upload_auto), color = DT.SubText, fontSize = 11.sp)
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = { cloudVm.uploadNow() }, enabled = !cs.busy, shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = DT.Teal, disabledContainerColor = DT.Surface2)) {
                                    if (cs.busy) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                    else Text(tr(com.minimart.pos.R.string.upload_now), color = DT.Ink, fontSize = 13.sp)
                                }
                                OutlinedButton(onClick = { cloudVm.disconnect() }, enabled = !cs.busy, shape = RoundedCornerShape(12.dp)) {
                                    Text(tr(com.minimart.pos.R.string.disconnect), color = DT.Red, fontSize = 13.sp)
                                }
                            }
                        }
                        cs.message?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(it, color = if (cs.isError) DT.Red else DT.Green, fontSize = 12.sp)
                        }
                    }
                }

                if (isOwner) PrivacySettingsSection(settingsRepo)

                if (isOwner) DailyReportSettingsSection(settingsRepo)

                if (isOwner) MpesaWebhookSettingsSection()

                if (isOwner) ArchiveSettingsSection(ownerGate)

                // ── Data & Backup ─────────────────────────────────────────────
                if (isOwner) {
                    DSection(tr(com.minimart.pos.R.string.set_backup), Icons.Default.Storage, summary = "Backup · Restore · Share", initiallyExpanded = false) {
                        var backupStatus by remember { mutableStateOf<String?>(null) }
                        var isBackingUp  by remember { mutableStateOf(false) }
                        var backupFiles  by remember { mutableStateOf<List<java.io.File>>(emptyList()) }
                        var showRestore  by remember { mutableStateOf(false) }
                        var lastBackupFile by remember { mutableStateOf<java.io.File?>(null) }
                        val backupHealth = remember(backupStatus, isBackingUp) { com.minimart.pos.util.BackupManager.statusText(context) }
                        val snap = remember(backupStatus, isBackingUp) { com.minimart.pos.util.BackupManager.healthSnapshot(context) }
                        val nowMs = System.currentTimeMillis()
                        val health = com.minimart.pos.util.BackupAdvice.health(snap.first, snap.second, snap.third, nowMs)
                        val (healthColor, headline) = when (health) {
                            com.minimart.pos.util.BackupHealth.GOOD -> DT.Green to "Backed up ${com.minimart.pos.util.BackupAdvice.ago(snap.first, nowMs)}"
                            com.minimart.pos.util.BackupHealth.STALE -> DT.Amber to "Last backup was ${com.minimart.pos.util.BackupAdvice.ago(snap.first, nowMs)}"
                            com.minimart.pos.util.BackupHealth.FAILED -> DT.Red to "The last backup failed"
                            com.minimart.pos.util.BackupHealth.NONE -> DT.Amber to "No backup yet"
                        }
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(healthColor.copy(0.12f))
                            .border(1.dp, healthColor.copy(0.4f), RoundedCornerShape(12.dp)).padding(12.dp)) {
                            Text(headline, color = healthColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(backupHealth, color = DT.SubText, fontSize = 11.sp)
                            Text("A copy is made automatically about once a day (the latest 7 are kept). Copies on this phone are lost if the phone is lost or broken, so save one somewhere else too.",
                                color = DT.SubText, fontSize = 11.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        // Bug fix: previously tapping a backup filename immediately restored
                        // it with NO confirmation step — a single misplaced tap could wipe
                        // today's sales with zero warning. Every other destructive action in
                        // the app (delete product, void sale, remove user) requires an
                        // explicit second confirmation; restore now matches that pattern.
                        var pendingRestoreFile by remember { mutableStateOf<java.io.File?>(null) }
                        var showBackupDialog by remember { mutableStateOf(false) }

                        // Optional passphrase: an encrypted (.mmbak) backup is unreadable without it,
                        // so it is safe to share or store in the cloud, and restores on any phone.
                        if (showBackupDialog) {
                            var pass by remember { mutableStateOf("") }
                            var pass2 by remember { mutableStateOf("") }
                            val passError = when {
                                pass.isNotEmpty() && pass.length < 6 -> "Use at least 6 characters"
                                pass != pass2 -> "Passphrases do not match"
                                else -> null
                            }
                            AlertDialog(
                                onDismissRequest = { showBackupDialog = false },
                                containerColor = DT.Surface,
                                title = { Text(tr(com.minimart.pos.R.string.create_backup), color = DT.OnSurface, fontWeight = FontWeight.Bold) },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Add a passphrase to encrypt the backup (recommended if you share or upload it). " +
                                            "Leave blank for an unencrypted backup. An encrypted backup cannot be opened without its passphrase.",
                                            color = DT.SubText, style = MaterialTheme.typography.bodySmall)
                                        OutlinedTextField(pass, { pass = it }, label = { Text(tr(com.minimart.pos.R.string.pass_optional)) }, singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password, autoCorrectEnabled = false),
                                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                            colors = dColors(), modifier = Modifier.fillMaxWidth())
                                        if (pass.isNotEmpty()) OutlinedTextField(pass2, { pass2 = it }, label = { Text(tr(com.minimart.pos.R.string.repeat_pass)) }, singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password, autoCorrectEnabled = false),
                                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                            colors = dColors(), modifier = Modifier.fillMaxWidth())
                                        if (pass.isNotEmpty()) passError?.let { Text(it, color = DT.Red, style = MaterialTheme.typography.labelSmall) }
                                        if (pass.isNotEmpty()) {
                                            val level = com.minimart.pos.util.BackupAdvice.passphraseStrength(pass)
                                            Text(com.minimart.pos.util.BackupAdvice.strengthLabel(level),
                                                color = when (level) { 3 -> DT.Green; 2 -> DT.Amber; else -> DT.Red }, style = MaterialTheme.typography.labelSmall)
                                            Text(tr(com.minimart.pos.R.string.pass_warning),
                                                color = DT.Amber, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        enabled = pass.isEmpty() || passError == null,
                                        onClick = {
                                            val chosen = pass.ifEmpty { null }
                                            showBackupDialog = false
                                            isBackingUp = true
                                            scope.launch {
                                                if (!ownerGate.allow("create a backup")) { backupStatus = "Only the owner can do this"; isBackingUp = false; return@launch }
                                                val r = com.minimart.pos.util.BackupManager.backup(context, chosen)
                                                backupStatus = when (r) {
                                                    is com.minimart.pos.util.BackupResult.Success -> { backupFiles = com.minimart.pos.util.BackupManager.listBackups(context); lastBackupFile = r.file; r.message }
                                                    is com.minimart.pos.util.BackupResult.Error -> r.message
                                                }
                                                integrityVm.recordBackup(
                                                    if (r is com.minimart.pos.util.BackupResult.Success) com.minimart.pos.util.AuditEvent.BACKUP_CREATED else com.minimart.pos.util.AuditEvent.BACKUP_FAILED,
                                                    if (r is com.minimart.pos.util.BackupResult.Success) "Manual backup${if (chosen != null) " (encrypted)" else ""}" else "Manual backup failed")
                                                isBackingUp = false
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = DT.Teal)
                                    ) { Text(tr(com.minimart.pos.R.string.back_up), color = DT.Ink) }
                                },
                                dismissButton = { TextButton(onClick = { showBackupDialog = false }) { Text(tr(com.minimart.pos.R.string.cancel), color = DT.SubText) } }
                            )
                        }
                        LaunchedEffect(Unit) { backupFiles = com.minimart.pos.util.BackupManager.listBackups(context) }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { showBackupDialog = true }, modifier = Modifier.weight(1f), enabled = !isBackingUp,
                                shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = DT.Teal)) {
                                if (isBackingUp) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                else Icon(Icons.Default.Backup, null, tint = DT.Ink, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp)); Text(tr(com.minimart.pos.R.string.backup), color = DT.Ink)
                            }
                            OutlinedButton(onClick = { backupFiles = com.minimart.pos.util.BackupManager.listBackups(context); showRestore = true },
                                modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, DT.Border)) {
                                Icon(Icons.Default.Restore, null, tint = DT.OnSurface, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp)); Text(tr(com.minimart.pos.R.string.restore), color = DT.OnSurface)
                            }
                        }
                        backupStatus?.let { Text(it, color = if ("saved" in it) DT.Green else DT.Red, style = MaterialTheme.typography.labelSmall) }
                        // The moment a backup succeeds is when a copy should leave the phone.
                        lastBackupFile?.let { f ->
                            Button(onClick = { com.minimart.pos.util.BackupManager.shareBackup(context, f) },
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DT.Green.copy(0.85f))) {
                                Icon(Icons.Default.Share, null, tint = DT.Ink, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp)); Text(tr(com.minimart.pos.R.string.save_copy_off), color = DT.Ink, fontWeight = FontWeight.Bold)
                            }
                        }
                        val integrityRunning by integrityVm.running.collectAsStateWithLifecycle()
                        val integrityReport by integrityVm.report.collectAsStateWithLifecycle()
                        val integrityFailed by integrityVm.failed.collectAsStateWithLifecycle()
                        TextButton(onClick = { integrityVm.run() }, enabled = !integrityRunning, contentPadding = PaddingValues(0.dp)) {
                            if (integrityRunning) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DT.Teal, strokeWidth = 2.dp)
                            else Icon(Icons.Default.FactCheck, null, tint = DT.Teal, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(tr(com.minimart.pos.R.string.check_data), color = DT.Teal, style = MaterialTheme.typography.labelSmall)
                        }
                        if (integrityReport != null || integrityFailed != null) {
                            AlertDialog(onDismissRequest = { integrityVm.dismiss() }, containerColor = DT.Surface,
                                title = { Text(if (integrityFailed != null) "Check could not run" else if (integrityReport?.ok == true) "No problems found" else "Problems found",
                                    color = DT.OnSurface, fontWeight = FontWeight.Bold) },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        val r = integrityReport
                                        if (integrityFailed != null) Text(integrityFailed ?: "", color = DT.Red, style = MaterialTheme.typography.bodySmall)
                                        else if (r != null) {
                                            Text("${r.salesChecked} completed sales checked.", color = DT.SubText, style = MaterialTheme.typography.bodySmall)
                                            r.issues.take(15).forEach { Text("• $it", color = DT.OnSurface, style = MaterialTheme.typography.bodySmall) }
                                            if (r.issues.size > 15) Text("…and ${r.issues.size - 15} more", color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                },
                                confirmButton = { TextButton(onClick = { integrityVm.dismiss() }) { Text(tr(com.minimart.pos.R.string.ok), color = DT.Teal) } })
                        }
                        if (backupFiles.isNotEmpty()) {
                            TextButton(onClick = { com.minimart.pos.util.BackupManager.shareBackup(context, backupFiles.first()) },
                                contentPadding = PaddingValues(0.dp)) {
                                Icon(Icons.Default.Share, null, tint = DT.Teal, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(tr(com.minimart.pos.R.string.share_backup), color = DT.Teal, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (showRestore) {
                            AlertDialog(onDismissRequest = { showRestore = false }, containerColor = DT.Surface,
                                title = { Text(tr(com.minimart.pos.R.string.restore_db), color = DT.OnSurface, fontWeight = FontWeight.Bold) },
                                text = {
                                    Column {
                                        Text(tr(com.minimart.pos.R.string.select_backup), color = DT.SubText, style = MaterialTheme.typography.bodySmall)
                                        Spacer(Modifier.height(8.dp))
                                        if (backupFiles.isEmpty()) Text(tr(com.minimart.pos.R.string.no_backups), color = DT.Red, style = MaterialTheme.typography.labelSmall)
                                        else backupFiles.take(5).forEach { f ->
                                            TextButton(onClick = {
                                                // Stage the file and show a real confirmation step
                                                // instead of restoring immediately on tap.
                                                showRestore = false
                                                pendingRestoreFile = f
                                            }, modifier = Modifier.fillMaxWidth()) {
                                                Column(Modifier.fillMaxWidth()) {
                                                    Text(f.name, color = DT.TealLight, style = MaterialTheme.typography.bodySmall,
                                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Text("${com.minimart.pos.util.BackupAdvice.ago(f.lastModified(), System.currentTimeMillis())} · " +
                                                        "${com.minimart.pos.util.BackupAdvice.humanSize(f.length())}" +
                                                        (if (f.name.endsWith(".mmbak")) " · Encrypted" else ""),
                                                        color = DT.SubText, fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    OutlinedButton(onClick = { showRestore = false }, shape = RoundedCornerShape(12.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, DT.Border)) { Text(tr(com.minimart.pos.R.string.cancel), color = DT.SubText) }
                                })
                        }

                        // Explicit "are you sure" step before any restore actually runs —
                        // restoring overwrites the ENTIRE current database irreversibly.
                        pendingRestoreFile?.let { f ->
                            val encrypted = f.name.endsWith(".mmbak")
                            var restorePass by remember(f) { mutableStateOf("") }
                            AlertDialog(
                                onDismissRequest = { pendingRestoreFile = null },
                                containerColor = DT.Surface,
                                title = { Text(tr(com.minimart.pos.R.string.restore_q), color = DT.Ink, fontWeight = FontWeight.Bold) },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("\"${f.name}\" will replace ALL current data — every sale, product, and customer added since this backup was made will be lost. This cannot be undone. The app will restart.", color = DT.SubText)
                                        if (encrypted) OutlinedTextField(restorePass, { restorePass = it }, label = { Text(tr(com.minimart.pos.R.string.backup_pass)) }, singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password, autoCorrectEnabled = false),
                                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                            colors = dColors(), modifier = Modifier.fillMaxWidth())
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        enabled = !encrypted || restorePass.isNotEmpty(),
                                        onClick = {
                                            val pw = restorePass.ifEmpty { null }
                                            pendingRestoreFile = null
                                            scope.launch {
                                                if (!ownerGate.allow("restore a backup")) { backupStatus = "Only the owner can do this"; return@launch }
                                                val r = com.minimart.pos.util.BackupManager.restore(context, f, pw)
                                                backupStatus = when (r) {
                                                    is com.minimart.pos.util.BackupResult.Success -> r.message
                                                    is com.minimart.pos.util.BackupResult.Error -> r.message
                                                }
                                                // Bug fix: previously this just showed a "please restart
                                                // manually" message — the live Room connection stayed
                                                // pointed at the now-replaced database file, risking a
                                                // crash or stale/corrupted reads if the user kept using
                                                // the app instead of remembering to close it themselves.
                                                integrityVm.recordBackup(
                                                    if (r is com.minimart.pos.util.BackupResult.Success) com.minimart.pos.util.AuditEvent.BACKUP_RESTORED else com.minimart.pos.util.AuditEvent.BACKUP_FAILED,
                                                    (if (r is com.minimart.pos.util.BackupResult.Success) "Restored " else "Restore failed: ") + f.name)
                                                if (r is com.minimart.pos.util.BackupResult.Success) {
                                                    kotlinx.coroutines.delay(1200)
                                                    com.minimart.pos.util.BackupManager.restartApp(context)
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = DT.Red, contentColor = Color.White)
                                    ) {
                                        Icon(Icons.Default.Restore, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(tr(com.minimart.pos.R.string.restore_replace), fontWeight = FontWeight.Bold)
                                    }
                                },
                                dismissButton = {
                                    OutlinedButton(onClick = { pendingRestoreFile = null }, shape = RoundedCornerShape(12.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, DT.Border)) { Text(tr(com.minimart.pos.R.string.cancel), color = DT.SubText) }
                                }
                            )
                        }
                    }
                }

                run {
                    val cloudState by cloudVm.state.collectAsStateWithLifecycle()
                    val syncOn by settingsRepo.syncServerEnabled.collectAsStateWithLifecycle(false)
                    AboutSettingsSection(syncOn = syncOn, cloudOn = cloudState.connected)
                }

                // ── Account ───────────────────────────────────────────────────
                DSection(tr(com.minimart.pos.R.string.set_account), Icons.Default.ManageAccounts) {
                    if (rm.canManageUsers(currentRole)) {
                        DMenuRow("User Management", Icons.Default.Group, DT.OnSurface) { onUsers() }
                        HDivider()
                    }
                    if (rm.canManageShifts(currentRole)) {
                        DMenuRow("Shift Management", Icons.Default.Schedule, DT.OnSurface) { onShifts() }
                        HDivider()
                    }
                    // Bug fix: this toggle is the ONLY place biometricUserId is ever set.
                    // Reaching this screen already requires a successful PIN login (Settings
                    // is session-gated behind LoginScreen and AccessGuard), so binding
                    // biometric to "whoever is logged in right now" here is safe — it can
                    // never be set without the account's PIN having been proven first.
                    val loggedInUserId by settingsRepo.loggedInUserId.collectAsStateWithLifecycle(initialValue = null)
                    val biometricUserId by settingsRepo.biometricUserId.collectAsStateWithLifecycle(initialValue = null)
                    val biometricOnForThisUser = loggedInUserId != null && loggedInUserId == biometricUserId
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(tr(com.minimart.pos.R.string.bio_login), color = DT.OnSurface, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            Text(tr(com.minimart.pos.R.string.this_account_only), color = DT.SubText, fontSize = 11.sp)
                        }
                        Switch(
                            checked = biometricOnForThisUser,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    val uid = loggedInUserId
                                    if (enabled && uid != null) settingsRepo.setBiometricUser(uid)
                                    else settingsRepo.clearBiometricUser()
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DT.Teal, uncheckedTrackColor = DT.Border, uncheckedThumbColor = DT.SubText, uncheckedBorderColor = DT.SubText.copy(0.6f))
                        )
                    }
                    HDivider()
                    DMenuRow("Logout", Icons.AutoMirrored.Filled.Logout, DT.Red) { onLogout() }
                }

                val versionName = remember {
                    try {
                        @Suppress("DEPRECATION")
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName
                    } catch (_: Exception) { null }
                }
                Text("MiniMart POS" + (versionName?.let { " · v$it" } ?: ""), color = DT.SubText.copy(0.7f),
                    fontSize = 11.sp, modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 12.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }

    if (showPrinterDialog) {
        AlertDialog(onDismissRequest = { showPrinterDialog = false }, containerColor = DT.Surface,
            title = { Text(tr(com.minimart.pos.R.string.select_printer), color = DT.OnSurface) },
            text = {
                Column {
                    if (pairedDevices.isEmpty()) Text(tr(com.minimart.pos.R.string.no_bt_printers), color = DT.SubText, style = MaterialTheme.typography.bodySmall)
                    else pairedDevices.forEach { device ->
                        TextButton(onClick = {
                            scope.launch {
                                showPrinterDialog = false
                                val result = printer.connect(device.address)
                                if (result is PrintResult.Success) {
                                    settingsRepo.setPrinterAddress(device.address, device.name ?: "Printer")
                                    printerStatus = "✓ Connected to ${device.name}"
                                } else printerStatus = (result as? PrintResult.Error)?.message ?: "Error"
                            }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Print, null, tint = DT.Teal, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(device.name ?: device.address, color = DT.OnSurface)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPrinterDialog = false }) { Text(tr(com.minimart.pos.R.string.close), color = DT.SubText) } })
    }
}
