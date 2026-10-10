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
    val storeName     by settingsRepo.storeName.collectAsState("")
    val currency      by settingsRepo.currency.collectAsState("KES")
    val receiptFooter by settingsRepo.receiptFooter.collectAsState("")
    val darkMode      by settingsRepo.darkMode.collectAsState(false)
    val expiryAlertMonths by settingsRepo.expiryAlertMonths.collectAsState(1)
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
    // Android 12+ needs BLUETOOTH_CONNECT granted at runtime before paired devices can be listed.
    val btPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) { pairedDevices = printer.getPairedPrinters(context); showPrinterDialog = true }
        else printerStatus = "Allow Bluetooth access to find your printer"
    }

    // Separate effects: saving the store block used to reset half-typed M-Pesa fields (and vice versa).
    LaunchedEffect(storeName, currency, receiptFooter) {
        storeNameInput = storeName; currencyInput = currency; footerInput = receiptFooter
    }
    LaunchedEffect(mpesaPaybill, mpesaTill, mpesaWithdraw, mpesaName) {
        paybillInput = mpesaPaybill; tillInput = mpesaTill; withdrawInput = mpesaWithdraw; nameInput = mpesaName
    }

    // Role restriction banner
    val isAdmin = rm.canAccessSettings(currentRole)

    Box(modifier = Modifier.fillMaxSize().background(DT.Bg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            GradientHeader(title = "Settings", subtitle = storeName.takeIf { it.isNotBlank() }, onBack = onBack)

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
                        val badge = Color(RoleManager.roleBadgeColor(role).toUInt().toLong())
                        Box(Modifier.clip(RoundedCornerShape(20.dp)).background(badge.copy(0.22f))
                            .border(1.dp, badge.copy(0.6f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 5.dp)) {
                            Text(RoleManager.roleLabel(role), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                        val cleanCurrency = currencyInput.trim().uppercase().take(5)
                        val storeDirty = storeNameInput.trim() != storeName || cleanCurrency != currency || footerInput.trim() != receiptFooter
                        val storeValid = storeNameInput.isNotBlank() && cleanCurrency.isNotEmpty()
                        if (!storeValid) Text("Store name and currency can't be empty.", color = DT.Red,
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
                        DField(paybillInput, { paybillInput = it.filter(Char::isDigit).take(10) }, "Paybill Number", Icons.Default.Business, numeric = true)
                        Spacer(Modifier.height(8.dp))

                        // Till
                        SSubTitle("Buy Goods / Till Number")
                        DField(tillInput, { tillInput = it.filter(Char::isDigit).take(10) }, "Till Number", Icons.Default.PointOfSale, numeric = true)
                        Spacer(Modifier.height(8.dp))

                        // Withdraw
                        SSubTitle("Withdrawal / Agent Number")
                        DField(withdrawInput, { withdrawInput = it.filter(Char::isDigit).take(10) }, "Agent / Withdraw Number", Icons.Default.Money, numeric = true)
                        Spacer(Modifier.height(4.dp))
                        Text("Used for end-of-day withdrawal reminders.", color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(12.dp))

                        val mpesaDirty = paybillInput != mpesaPaybill || tillInput != mpesaTill ||
                            withdrawInput != mpesaWithdraw || nameInput != mpesaName
                        SaveButton("Save M-Pesa Settings", enabled = mpesaDirty, color = Color(0xFF1B5E20), icon = Icons.Default.PhoneAndroid) {
                            scope.launch {
                                settingsRepo.setMpesaPaybill(paybillInput)
                                settingsRepo.setMpesaTill(tillInput)
                                settingsRepo.setMpesaWithdraw(withdrawInput)
                                settingsRepo.setMpesaAccountName(nameInput)
                            }
                        }
                    }
                }

                // ── M-Pesa STK Push (Daraja API) ─────────────────────────────
                if (isAdmin) {
                    val initialDaraja = remember { settingsRepo.getDarajaConfig() }
                    var darajaSandbox       by remember { mutableStateOf(initialDaraja.sandbox) }
                    var darajaShortcode     by remember { mutableStateOf(initialDaraja.shortcode) }
                    var darajaConsumerKey   by remember { mutableStateOf(initialDaraja.consumerKey) }
                    var darajaConsumerSecret by remember { mutableStateOf(initialDaraja.consumerSecret) }
                    var darajaPasskey       by remember { mutableStateOf(initialDaraja.passkey) }
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
                                    color = if (darajaSandbox) Color(0xFFFFB300) else DT.Green,
                                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.width(6.dp))
                                Switch(checked = !darajaSandbox, onCheckedChange = { darajaSandbox = !it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor   = DT.Green,
                                        checkedTrackColor   = DT.Green.copy(0.3f),
                                        uncheckedThumbColor = Color(0xFFFFB300),
                                        uncheckedTrackColor = Color(0xFFFFB300).copy(0.3f)
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
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                settingsRepo.saveDarajaConfig(
                                    com.minimart.pos.data.repository.SettingsRepository.DarajaConfig(
                                        consumerKey    = darajaConsumerKey.trim(),
                                        consumerSecret = darajaConsumerSecret.trim(),
                                        passkey        = darajaPasskey.trim(),
                                        shortcode      = darajaShortcode.trim(),
                                        sandbox        = darajaSandbox
                                    )
                                )
                                darajaSaved = true
                                scope.launch { kotlinx.coroutines.delay(3000); darajaSaved = false }
                            },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20))
                        ) {
                            Icon(Icons.Default.Send, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (darajaSaved) "✓ Saved — encrypted on this device" else "Save Daraja Settings",
                                color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // ── Thermal Printer ───────────────────────────────────────────
                if (isAdmin) {
                    DSection("Thermal Printer", Icons.Default.Print,
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
                            ) { Text(if (printerName.isNullOrBlank()) "Pair" else "Change", color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                        printerStatus?.let { Text(it, color = if (it.startsWith("✓")) DT.Green else DT.Red, style = MaterialTheme.typography.labelSmall) }
                    }
                }

                // ── Cash Drawer ───────────────────────────────────────────────
                if (isAdmin) {
                    val cashDrawerAddress by settingsRepo.cashDrawerAddress.collectAsState("")
                    val cashDrawerOnSale  by settingsRepo.cashDrawerOnSale.collectAsState(true)
                    var testStatus by remember { mutableStateOf<String?>(null) }
                    // Local text, saved when the field loses focus: writing DataStore on every keystroke
                    // and echoing it back made typing laggy and could drop characters.
                    var drawerInput by remember { mutableStateOf("") }
                    var drawerTouched by remember { mutableStateOf(false) }
                    LaunchedEffect(cashDrawerAddress) { drawerInput = cashDrawerAddress }

                    DSection("Cash Drawer", Icons.Default.LocalAtm,
                        summary = if (cashDrawerOnSale) "Opens on cash sale" else "Manual only",
                        initiallyExpanded = false) {
                        Text("Cash drawer connected to thermal printer via RJ11 is auto-detected.",
                            color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(10.dp))
                        // Auto open on sale toggle
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Open on cash sale", color = Color.White, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                Text("Auto-opens when cash payment completes", color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                            }
                            Switch(checked = cashDrawerOnSale, onCheckedChange = { scope.launch { settingsRepo.setCashDrawerOnSale(it) } },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DT.Teal, uncheckedTrackColor = DT.Border))
                        }
                        Spacer(Modifier.height(10.dp))
                        // Direct BT drawer address (optional)
                        OutlinedTextField(value = drawerInput, onValueChange = { drawerInput = it },
                            label = { Text("Direct BT Drawer Address (optional)", color = DT.SubText) },
                            placeholder = { Text("00:11:22:33:44:55", color = DT.SubText.copy(0.5f)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().onFocusChanged { f ->
                                if (f.isFocused) drawerTouched = true
                                else if (drawerTouched && drawerInput.trim() != cashDrawerAddress) {
                                    scope.launch { settingsRepo.setCashDrawerAddress(drawerInput.trim()) }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border,
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
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
                                Text("Test Open", color = DT.Teal, style = MaterialTheme.typography.labelMedium)
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
                    val blindClose by settingsRepo.blindClose.collectAsState(true)
                    val secondScreen by settingsRepo.customerDisplayEnabled.collectAsState(false)
                    val kickPin by settingsRepo.cashDrawerPin.collectAsState(0)
                    DSection("Shift close & display", Icons.Default.Tv,
                        summary = if (blindClose) "Blind close on" else "Blind close off",
                        initiallyExpanded = false) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Blind close", color = Color.White, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                Text("Cashiers count the till without seeing the expected cash. Managers and owners see the variance on the Z-report.", color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                            }
                            Switch(checked = blindClose, onCheckedChange = { scope.launch { settingsRepo.setBlindClose(it) } },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DT.Teal, uncheckedTrackColor = DT.Border))
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Customer display on second screen", color = Color.White, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                Text("Shows the live cart on an attached HDMI or dual-screen display. The Customer tile on Home also works full-screen on this device.", color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                            }
                            Switch(checked = secondScreen, onCheckedChange = { scope.launch { settingsRepo.setCustomerDisplayEnabled(it) } },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DT.Teal, uncheckedTrackColor = DT.Border))
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Drawer kick pin", color = Color.White, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Text("Try pin 5 if the drawer does not open on pin 2.", color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                            FilterChip(selected = kickPin == 0, onClick = { scope.launch { settingsRepo.setCashDrawerPin(0) } }, label = { Text("Pin 2") })
                            FilterChip(selected = kickPin == 1, onClick = { scope.launch { settingsRepo.setCashDrawerPin(1) } }, label = { Text("Pin 5") })
                        }
                    }
                }

                // ── Bluetooth Scanner ─────────────────────────────────────────
                DSection("Bluetooth Barcode Scanner", Icons.Default.QrCodeScanner, initiallyExpanded = false) {
                    Text("HID scanners pair as keyboards — connect via Android Bluetooth settings. Once paired, scan works automatically in the New Sale screen.",
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
                            Text("No paired BT scanners found", color = DT.SubText, style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        btScanners.forEach { name ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 4.dp)) {
                                Icon(Icons.Default.Bluetooth, null, tint = DT.Teal, modifier = Modifier.size(18.dp))
                                Text(name, color = Color.White, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                Box(Modifier.clip(RoundedCornerShape(6.dp)).background(DT.Green.copy(0.15f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                    Text("Paired", color = DT.Green, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Tip: Enable continuous scan mode (∞) in New Sale for rapid scanning.", color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                }

                // ── Expiry Alerts ─────────────────────────────────────────────
                DSection("Expiry Alerts", Icons.Default.CalendarToday,
                    summary = "$expiryAlertMonths ${if (expiryAlertMonths == 1) "month" else "months"} ahead") {
                    Text("Alert me when products expire within:", color = DT.SubText,
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
                                    .clickable(enabled = isAdmin) { scope.launch { settingsRepo.setExpiryAlertMonths(months) } }
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
                    Text("Checks every 12 hours. Runs in background.",
                        color = DT.SubText, style = MaterialTheme.typography.labelSmall)
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

                // ── Multi-Device Sync (LAN) ───────────────────────────────────
                if (isAdmin) {
                    val syncState by syncVm.state.collectAsState()
                    DSection("Multi-Device Sync (LAN)", Icons.Default.Sync,
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
                                Text("Act as Sync Server", color = Color.White, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                if (syncState.serverRunning)
                                    Text("Server IP: ${syncState.serverIp}:9876", color = DT.Teal, fontSize = 11.sp)
                                else
                                    Text("Other devices can connect to this one", color = DT.SubText, fontSize = 11.sp)
                            }
                            Switch(
                                checked = syncState.serverRunning,
                                onCheckedChange = { if (it) syncVm.startServer() else syncVm.stopServer() },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DT.Teal, uncheckedTrackColor = DT.Border)
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
                                        Text("Pairing code — enter this on the other device", color = DT.Teal, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(syncState.mySecret, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 4.sp)
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(color = DT.Border)
                        Spacer(Modifier.height(14.dp))

                        // Connect to another server
                        Text("Connect to Server", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = syncState.peerIp,
                            onValueChange = { syncVm.setPeerIp(it) },
                            label = { Text("Server IP address", color = DT.SubText) },
                            placeholder = { Text("e.g. 192.168.1.100", color = DT.SubText.copy(0.5f)) },
                            leadingIcon = { Icon(Icons.Default.Wifi, null, tint = DT.SubText) },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border,
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                focusedContainerColor = DT.Bg, unfocusedContainerColor = DT.Bg))
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = syncState.peerKey,
                            onValueChange = { syncVm.setPeerKey(it.filter(Char::isDigit).take(6)) },
                            label = { Text("Pairing code", color = DT.SubText) },
                            placeholder = { Text("6-digit code shown on the server", color = DT.SubText.copy(0.5f)) },
                            leadingIcon = { Icon(Icons.Default.Lock, null, tint = DT.SubText) },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border,
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
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
                                    Icon(Icons.Default.Sync, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Sync Now", color = Color.White, fontSize = 13.sp)
                                }
                            }
                        }
                        syncState.lastResult?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(it, color = if (it.startsWith("✓")) DT.Green else DT.Red, fontSize = 12.sp)
                        }
                    }
                }

                // ── Data & Backup ─────────────────────────────────────────────
                if (isAdmin) {
                    DSection("Data & Backup", Icons.Default.Storage, summary = "Backup · Restore · Share", initiallyExpanded = false) {
                        var backupStatus by remember { mutableStateOf<String?>(null) }
                        var isBackingUp  by remember { mutableStateOf(false) }
                        var backupFiles  by remember { mutableStateOf<List<java.io.File>>(emptyList()) }
                        var showRestore  by remember { mutableStateOf(false) }
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
                                title = { Text("Create Backup", color = DT.OnSurface, fontWeight = FontWeight.Bold) },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Add a passphrase to encrypt the backup (recommended if you share or upload it). " +
                                            "Leave blank for an unencrypted backup. An encrypted backup cannot be opened without its passphrase.",
                                            color = DT.SubText, style = MaterialTheme.typography.bodySmall)
                                        OutlinedTextField(pass, { pass = it }, label = { Text("Passphrase (optional)") }, singleLine = true,
                                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                            colors = dColors(), modifier = Modifier.fillMaxWidth())
                                        if (pass.isNotEmpty()) OutlinedTextField(pass2, { pass2 = it }, label = { Text("Repeat passphrase") }, singleLine = true,
                                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                            colors = dColors(), modifier = Modifier.fillMaxWidth())
                                        if (pass.isNotEmpty()) passError?.let { Text(it, color = DT.Red, style = MaterialTheme.typography.labelSmall) }
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
                                                val r = com.minimart.pos.util.BackupManager.backup(context, chosen)
                                                backupStatus = when (r) {
                                                    is com.minimart.pos.util.BackupResult.Success -> { backupFiles = com.minimart.pos.util.BackupManager.listBackups(context); r.message }
                                                    is com.minimart.pos.util.BackupResult.Error -> r.message
                                                }
                                                isBackingUp = false
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = DT.Teal)
                                    ) { Text("Back up", color = Color.White) }
                                },
                                dismissButton = { TextButton(onClick = { showBackupDialog = false }) { Text("Cancel", color = DT.SubText) } }
                            )
                        }
                        LaunchedEffect(Unit) { backupFiles = com.minimart.pos.util.BackupManager.listBackups(context) }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { showBackupDialog = true }, modifier = Modifier.weight(1f), enabled = !isBackingUp,
                                shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = DT.Teal)) {
                                if (isBackingUp) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                else Icon(Icons.Default.Backup, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp)); Text("Backup", color = Color.White)
                            }
                            OutlinedButton(onClick = { backupFiles = com.minimart.pos.util.BackupManager.listBackups(context); showRestore = true },
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
                                                // Stage the file and show a real confirmation step
                                                // instead of restoring immediately on tap.
                                                showRestore = false
                                                pendingRestoreFile = f
                                            }, modifier = Modifier.fillMaxWidth()) {
                                                Column(Modifier.fillMaxWidth()) {
                                                    Text(f.name, color = DT.TealLight, style = MaterialTheme.typography.bodySmall,
                                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Text("${java.text.SimpleDateFormat("d MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(f.lastModified()))} · ${f.length() / 1024} KB",
                                                        color = DT.SubText, fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    OutlinedButton(onClick = { showRestore = false }, shape = RoundedCornerShape(12.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, DT.Border)) { Text("Cancel", color = DT.SubText) }
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
                                title = { Text("Restore This Backup?", color = Color.White, fontWeight = FontWeight.Bold) },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("\"${f.name}\" will replace ALL current data — every sale, product, and customer added since this backup was made will be lost. This cannot be undone. The app will restart.", color = DT.SubText)
                                        if (encrypted) OutlinedTextField(restorePass, { restorePass = it }, label = { Text("Backup passphrase") }, singleLine = true,
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
                                        Text("Restore & Replace", fontWeight = FontWeight.Bold)
                                    }
                                },
                                dismissButton = {
                                    OutlinedButton(onClick = { pendingRestoreFile = null }, shape = RoundedCornerShape(12.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, DT.Border)) { Text("Cancel", color = DT.SubText) }
                                }
                            )
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
                    // Bug fix: this toggle is the ONLY place biometricUserId is ever set.
                    // Reaching this screen already requires a successful PIN login (Settings
                    // is session-gated behind LoginScreen and AccessGuard), so binding
                    // biometric to "whoever is logged in right now" here is safe — it can
                    // never be set without the account's PIN having been proven first.
                    val loggedInUserId by settingsRepo.loggedInUserId.collectAsState(initial = null)
                    val biometricUserId by settingsRepo.biometricUserId.collectAsState(initial = null)
                    val biometricOnForThisUser = loggedInUserId != null && loggedInUserId == biometricUserId
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Fingerprint / Face Login", color = DT.OnSurface, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            Text("For this account on this device only", color = DT.SubText, fontSize = 11.sp)
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
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DT.Teal, uncheckedTrackColor = DT.Border)
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
            confirmButton = { TextButton(onClick = { showPrinterDialog = false }) { Text("Close", color = DT.SubText) } })
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

@Composable
private fun DSection(
    title: String,
    icon: ImageVector,
    summary: String? = null,
    initiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    // Remembered per title so a rotation doesn't snap every section back to its default.
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(DT.Surface)
        .border(1.dp, DT.Border, RoundedCornerShape(18.dp))) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    ) { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = DT.Teal, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = DT.OnSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    if (!expanded && summary != null) {
                        Text(summary, color = DT.SubText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    if (expanded) "Collapse" else "Expand", tint = DT.SubText, modifier = Modifier.size(22.dp))
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) { content() }
            }
        }
    }
}

@Composable
private fun DField(value: String, onValueChange: (String) -> Unit, label: String, icon: ImageVector? = null, numeric: Boolean = false) {
    OutlinedTextField(value = value, onValueChange = onValueChange,
        label = { Text(label, color = DT.SubText, style = MaterialTheme.typography.labelSmall) },
        leadingIcon = icon?.let { { Icon(it, null, tint = DT.SubText, modifier = Modifier.size(18.dp)) } },
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp), colors = dColors())
}

/** Full-width save button that is only live when something changed and confirms with "✓ Saved". */
@Composable
private fun SaveButton(label: String, enabled: Boolean, color: Color, icon: ImageVector? = null, onSave: () -> Unit) {
    var saved by remember { mutableStateOf(false) }
    LaunchedEffect(saved) { if (saved) { kotlinx.coroutines.delay(2500); saved = false } }
    Button(
        onClick = { onSave(); saved = true },
        enabled = enabled || saved,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color, contentColor = Color.White,
            disabledContainerColor = color.copy(0.35f), disabledContentColor = Color.White.copy(0.7f)
        )
    ) {
        if (!saved && icon != null) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(if (saved) "✓ Saved" else label, fontWeight = FontWeight.Bold)
    }
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
