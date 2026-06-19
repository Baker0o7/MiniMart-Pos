package com.minimart.pos.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.minimart.pos.data.entity.Customer
import com.minimart.pos.data.entity.PaymentMethod
import com.minimart.pos.ui.theme.DT
import com.minimart.pos.ui.viewmodel.CartViewModel
import com.minimart.pos.ui.viewmodel.CheckoutResult
import com.minimart.pos.ui.viewmodel.CustomerViewModel
import com.minimart.pos.util.vibrateShort

@Composable
fun CheckoutScreen(
    onSaleComplete: (Long) -> Unit,
    onBack: () -> Unit,
    vm: CartViewModel,
    canApplyDiscounts: Boolean = true,
    customerVm: CustomerViewModel = hiltViewModel()
) {
    val state       by vm.uiState.collectAsState()
    val currency    by vm.currency.collectAsState()
    val custState   by customerVm.uiState.collectAsState()

    var selectedMethod          by remember { mutableStateOf(PaymentMethod.CASH) }
    var cashInput               by remember { mutableStateOf("") }
    var mpesaRef                by remember { mutableStateOf("") }
    var globalDiscount          by remember { mutableStateOf("") }
    var selectedCustomer        by remember { mutableStateOf<Customer?>(null) }
    var showCustomerSearch      by remember { mutableStateOf(false) }
    var customerQuery           by remember { mutableStateOf("") }
    var showConfirmCredit       by remember { mutableStateOf(false) }
    // Split payment: use partial credit + cash for remainder
    var splitCreditInput        by remember { mutableStateOf("") }
    var useSplitPayment         by remember { mutableStateOf(false) }

    val cashAmount      = cashInput.toDoubleOrNull() ?: 0.0
    val change          = (cashAmount - state.total).coerceAtLeast(0.0)
    val creditBalance   = selectedCustomer?.creditBalance ?: 0.0
    val splitCredit     = splitCreditInput.toDoubleOrNull()?.coerceIn(0.0, creditBalance) ?: 0.0
    val splitCashNeeded = (state.total - splitCredit).coerceAtLeast(0.0)

    val canComplete = when {
        state.total <= 0 -> false
        useSplitPayment  -> selectedCustomer != null && splitCredit > 0 && cashAmount >= splitCashNeeded
        else -> when (selectedMethod) {
            PaymentMethod.CASH   -> cashAmount >= state.total
            PaymentMethod.MPESA  -> true
            PaymentMethod.CREDIT -> selectedCustomer != null && state.total > 0
            else                 -> true
        }
    }

    val checkoutContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        vm.checkoutResult.collect { result ->
            if (result is CheckoutResult.Success) {
                checkoutContext.vibrateShort()
                onSaleComplete(result.saleId)
            }
        }
    }

    // Customer search side effect
    LaunchedEffect(customerQuery) { customerVm.setQuery(customerQuery) }

    Box(modifier = Modifier.fillMaxSize().background(DT.Bg)) {
        Column(modifier = Modifier.fillMaxSize().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState())) {

            // ── Top bar ───────────────────────────────────────────────────────
            Box(modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .background(Brush.verticalGradient(listOf(DT.Teal, Color(0xFF006B5E), Color(0xFF004D40))))
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(42.dp).clip(CircleShape)
                        .background(Color.White.copy(0.18f))
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onBack),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Checkout", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                        Text("${state.itemCount} item${if (state.itemCount != 1) "s" else ""}  •  $currency ${String.format("%.2f", state.total)}",
                            color = Color.White.copy(0.75f), fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Order Summary ─────────────────────────────────────────────────
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.verticalGradient(listOf(DT.Surface, DT.Surface2)))
                .border(1.dp, DT.Border, RoundedCornerShape(20.dp)).padding(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Receipt, null, tint = DT.Teal, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Order Summary", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = DT.Border)
                    Spacer(Modifier.height(10.dp))
                    // Items
                    state.items.forEach { item ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                                .background(Brush.linearGradient(listOf(DT.Teal.copy(0.2f), DT.TealDim))),
                                contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Inventory2, null, tint = DT.Teal, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.product.name, color = Color.White, fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("$currency ${String.format("%.2f", item.product.price)} × ${item.quantity}",
                                    color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                            }
                            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(DT.Teal.copy(0.12f))
                                .border(1.dp, DT.Teal.copy(0.3f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp)) {
                                Text("$currency ${String.format("%.2f", item.lineTotal)}",
                                    color = DT.TealLight, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = DT.Border)
                    Spacer(Modifier.height(10.dp))
                    // Discount
                    if (canApplyDiscounts) {
                        OutlinedTextField(value = globalDiscount,
                            onValueChange = { globalDiscount = it; vm.setGlobalDiscount(it.toDoubleOrNull() ?: 0.0) },
                            label = { Text("Discount ($currency)", color = DT.SubText) },
                            leadingIcon = { Icon(Icons.Default.Discount, null, tint = DT.SubText) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border,
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                cursorColor = DT.Teal, focusedContainerColor = DT.Bg, unfocusedContainerColor = DT.Bg))
                        Spacer(Modifier.height(8.dp))
                    }
                    if (state.totalTax > 0) {
                        SummaryLine("VAT (incl.)", "$currency ${String.format("%.2f", state.totalTax)}", DT.SubText)
                    }
                    if (state.totalDiscount > 0) {
                        SummaryLine("Discount", "- $currency ${String.format("%.2f", state.totalDiscount)}", DT.Amber, bold = true)
                    }
                    // Change pill (cash only)
                    AnimatedVisibility(visible = selectedMethod == PaymentMethod.CASH && cashAmount >= state.total && state.total > 0) {
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(DT.Green.copy(0.1f)).border(1.dp, DT.Green.copy(0.3f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Money, null, tint = DT.Green, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Change", color = DT.Green, fontWeight = FontWeight.Bold)
                                }
                                AnimatedContent(targetState = change, transitionSpec = {
                                    slideInVertically { -it } togetherWith slideOutVertically { it }
                                }, label = "change") { ch ->
                                    Text("$currency ${String.format("%.2f", ch)}", color = DT.Green,
                                        fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = DT.Border)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("TOTAL", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, letterSpacing = 1.sp)
                        Text("$currency ${String.format("%.2f", state.total)}", color = DT.Teal,
                            fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Customer (optional) ───────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, null, tint = DT.Teal, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Customer", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text("Optional", color = DT.SubText, fontSize = 11.sp)
            }
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(DT.Surface).border(1.dp, if (selectedCustomer != null) DT.Teal.copy(0.4f) else DT.Border, RoundedCornerShape(16.dp))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { showCustomerSearch = true }
                .padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(38.dp).clip(CircleShape)
                        .background(if (selectedCustomer != null) DT.Teal else DT.Surface2),
                        contentAlignment = Alignment.Center) {
                        if (selectedCustomer != null) {
                            Text(selectedCustomer?.name?.firstOrNull()?.uppercase() ?: "?",
                                color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        } else {
                            Icon(Icons.Default.PersonSearch, null, tint = DT.SubText, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(selectedCustomer?.name ?: "Select customer (optional)",
                            color = if (selectedCustomer != null) Color.White else DT.SubText,
                            fontWeight = if (selectedCustomer != null) FontWeight.SemiBold else FontWeight.Normal)
                        if (selectedCustomer != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (selectedCustomer?.phone?.isNotBlank() == true)
                                    Text(selectedCustomer?.phone ?: "", color = DT.SubText, fontSize = 12.sp)
                                Box(Modifier.clip(RoundedCornerShape(6.dp)).background(DT.Teal.copy(0.15f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                    Text("Credit: $currency ${String.format("%.2f", creditBalance)}",
                                        color = DT.Teal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    if (selectedCustomer != null) {
                        IconButton(onClick = { selectedCustomer = null; if (selectedMethod == PaymentMethod.CREDIT) selectedMethod = PaymentMethod.CASH }) {
                            Icon(Icons.Default.Close, null, tint = DT.SubText, modifier = Modifier.size(18.dp))
                        }
                    } else {
                        Icon(Icons.Default.ChevronRight, null, tint = DT.SubText, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Payment Method ────────────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Payment, null, tint = DT.Teal, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Payment Method", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PaymentCard(Modifier.weight(1f), "Cash", Icons.Default.Money, PaymentMethod.CASH, selectedMethod) {
                    selectedMethod = it; if (it != PaymentMethod.CASH) cashInput = ""
                }
                PaymentCard(Modifier.weight(1f), "M-Pesa", Icons.Default.PhoneAndroid, PaymentMethod.MPESA, selectedMethod) { selectedMethod = it }
                // Credit only shown when customer selected
                AnimatedVisibility(visible = selectedCustomer != null, modifier = Modifier.weight(1f)) {
                    PaymentCard(Modifier.fillMaxWidth(), "Credit", Icons.Default.AccountBalanceWallet,
                        PaymentMethod.CREDIT, selectedMethod,
                        enabled = true) { selectedMethod = it }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Payment inputs ────────────────────────────────────────────────
            AnimatedContent(targetState = selectedMethod,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                label = "payInput") { method ->
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (method) {
                        PaymentMethod.CASH -> {
                            OutlinedTextField(value = cashInput, onValueChange = { cashInput = it },
                                label = { Text("Cash Received ($currency)", color = DT.SubText) },
                                leadingIcon = { Icon(Icons.Default.Money, null, tint = DT.SubText) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true, modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border,
                                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                    cursorColor = DT.Teal, focusedContainerColor = DT.Surface, unfocusedContainerColor = DT.Surface))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                listOf(50, 100, 200, 500, 1000).forEach { amt ->
                                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                        .background(if (cashInput == amt.toString()) DT.Teal else DT.Surface)
                                        .border(1.dp, if (cashInput == amt.toString()) DT.Teal else DT.Border, RoundedCornerShape(12.dp))
                                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { cashInput = amt.toString() }
                                        .padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                                        Text(amt.toString(),
                                            color = if (cashInput == amt.toString()) Color.White else DT.SubText,
                                            fontWeight = if (cashInput == amt.toString()) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                        PaymentMethod.MPESA -> {
                            OutlinedTextField(value = mpesaRef, onValueChange = { mpesaRef = it.uppercase() },
                                label = { Text("M-Pesa Ref (optional)", color = DT.SubText) },
                                leadingIcon = { Icon(Icons.Default.ConfirmationNumber, null, tint = DT.SubText) },
                                singleLine = true, modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border,
                                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                    cursorColor = DT.Teal, focusedContainerColor = DT.Surface, unfocusedContainerColor = DT.Surface))
                            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .background(DT.Teal.copy(0.1f)).border(1.dp, DT.Teal.copy(0.25f), RoundedCornerShape(14.dp))
                                .padding(horizontal = 16.dp, vertical = 14.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.PhoneAndroid, null, tint = DT.Teal, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Amount due", color = DT.SubText, fontSize = 14.sp)
                                    }
                                    Text("$currency ${String.format("%.2f", state.total)}",
                                        color = DT.Teal, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                                }
                            }
                        }
                        PaymentMethod.CREDIT -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                // Credit summary card
                                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                    .background(Brush.linearGradient(listOf(Color(0xFF0B2822), Color(0xFF081510))))
                                    .border(1.dp, DT.Teal.copy(0.3f), RoundedCornerShape(16.dp))
                                    .padding(16.dp)) {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(Modifier.size(42.dp).clip(CircleShape).background(DT.Teal), contentAlignment = Alignment.Center) {
                                                Text(selectedCustomer?.name?.firstOrNull()?.uppercase() ?: "?",
                                                    color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                            }
                                            Spacer(Modifier.width(12.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(selectedCustomer?.name ?: "", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                                if (selectedCustomer?.phone?.isNotBlank() == true)
                                                    Text(selectedCustomer?.phone ?: "", color = DT.SubText, fontSize = 12.sp)
                                            }
                                            // Visit count badge
                                            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(DT.Surface2).padding(horizontal = 8.dp, vertical = 4.dp)) {
                                                Text("${selectedCustomer?.visitCount ?: 0} visits", color = DT.SubText, fontSize = 10.sp)
                                            }
                                        }
                                        HorizontalDivider(color = DT.Border.copy(0.5f))
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            CreditStat("Available", "$currency ${String.format("%.2f", creditBalance)}", DT.Teal)
                                            CreditStat("Order", "$currency ${String.format("%.2f", state.total)}", Color.White)
                                            val remaining = creditBalance - state.total
                                            CreditStat("After", "$currency ${String.format("%.2f", remaining)}", if (remaining >= 0) DT.Green else DT.Red)
                                        }
                                        // Insufficient warning
                                        if (creditBalance < state.total && !useSplitPayment) {
                                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                                .background(DT.Amber.copy(0.1f)).border(1.dp, DT.Amber.copy(0.3f), RoundedCornerShape(10.dp))
                                                .padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Warning, null, tint = DT.Amber, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Text("${selectedCustomer?.name ?: "Customer"} will owe $currency ${String.format("%.2f", state.total - creditBalance)}", color = DT.Amber, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                    Text("Purchase on credit — balance goes to -$currency ${String.format("%.2f", state.total - creditBalance)}", color = DT.SubText, fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                }

                                // Split payment toggle
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                    .background(DT.Surface).border(1.dp, DT.Border, RoundedCornerShape(14.dp))
                                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { useSplitPayment = !useSplitPayment; splitCreditInput = "" }
                                    .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if (useSplitPayment) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                        null, tint = if (useSplitPayment) DT.Teal else DT.SubText, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("Split payment (Credit + Cash)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text("Pay part with credit, rest with cash", color = DT.SubText, fontSize = 11.sp)
                                    }
                                }

                                // Split inputs
                                AnimatedVisibility(visible = useSplitPayment) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(value = splitCreditInput, onValueChange = { splitCreditInput = it },
                                            label = { Text("Credit Amount (max $currency ${String.format("%.2f", creditBalance)})", color = DT.SubText) },
                                            leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, null, tint = DT.Teal) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(14.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border,
                                                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                                cursorColor = DT.Teal, focusedContainerColor = DT.Surface, unfocusedContainerColor = DT.Surface))
                                        if (splitCredit > 0) {
                                            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                                .background(DT.Teal.copy(0.08f)).border(1.dp, DT.Teal.copy(0.25f), RoundedCornerShape(12.dp))
                                                .padding(12.dp)) {
                                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    CreditStat("Credit used", "$currency ${String.format("%.2f", splitCredit)}", DT.Teal)
                                                    CreditStat("Cash needed", "$currency ${String.format("%.2f", splitCashNeeded)}", Color.White)
                                                    val balAfter = creditBalance - splitCredit
                                                    CreditStat("Credit left", "$currency ${String.format("%.2f", balAfter)}", DT.Green)
                                                }
                                            }
                                        }
                                        // Cash input for split
                                        OutlinedTextField(value = cashInput, onValueChange = { cashInput = it },
                                            label = { Text("Cash for remaining $currency ${String.format("%.2f", splitCashNeeded)}", color = DT.SubText) },
                                            leadingIcon = { Icon(Icons.Default.Money, null, tint = DT.SubText) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(14.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border,
                                                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                                cursorColor = DT.Teal, focusedContainerColor = DT.Surface, unfocusedContainerColor = DT.Surface))
                                    }
                                }
                            }
                        }
                        else -> {}
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Complete button ───────────────────────────────────────────────
            Button(
                onClick = {
                    if (useSplitPayment && selectedCustomer != null) {
                        // Split: deduct credit portion first, then record cash portion
                        vm.checkoutSplit(
                            creditAmount  = splitCredit,
                            cashAmount    = cashAmount,
                            customerId    = selectedCustomer?.id ?: 0L,
                            mpesaRef      = null
                        )
                    } else {
                        vm.checkout(
                            paymentMethod = selectedMethod,
                            amountPaid    = if (selectedMethod == PaymentMethod.CASH) cashAmount else state.total,
                            mpesaRef      = mpesaRef.takeIf { it.isNotBlank() },
                            customerId    = selectedCustomer?.id
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 16.dp),
                enabled = canComplete && !state.isLoading,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedMethod == PaymentMethod.CREDIT) DT.Teal else DT.Green,
                    disabledContainerColor = DT.Surface2),
                elevation = ButtonDefaults.buttonElevation(6.dp)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(if (canComplete) Icons.Default.CheckCircle else Icons.Default.Lock, null,
                        tint = if (canComplete) Color.White else DT.SubText, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(when {
                        !canComplete && useSplitPayment && splitCredit <= 0 -> "ENTER CREDIT AMOUNT"
                        !canComplete && useSplitPayment -> "ENTER CASH AMOUNT"
                        !canComplete && selectedMethod == PaymentMethod.CASH && state.total > 0 -> "ENTER CASH AMOUNT"
                        !canComplete && selectedMethod == PaymentMethod.CREDIT -> "SELECT CUSTOMER"
                        !canComplete -> "SELECT PAYMENT METHOD"
                        useSplitPayment -> "COMPLETE SPLIT PAYMENT"
                        selectedMethod == PaymentMethod.CREDIT && creditBalance < state.total -> "PURCHASE ON CREDIT"
                        else -> "COMPLETE CHECKOUT"
                    }, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, letterSpacing = 0.5.sp,
                        color = if (canComplete) Color.White else DT.SubText)
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // ── Customer Search Sheet ─────────────────────────────────────────────
        if (showCustomerSearch) {
            CustomerSearchSheet(
                query = customerQuery,
                customers = custState.customers,
                onQueryChange = { customerQuery = it },
                onSelect = { cust ->
                    selectedCustomer = cust
                    showCustomerSearch = false
                    customerQuery = ""
                    if (cust.creditBalance >= state.total && state.total > 0)
                        selectedMethod = PaymentMethod.CREDIT
                },
                onNewCustomer = { name, phone ->
                    val newCust = Customer(name = name, phone = phone)
                    customerVm.saveCustomer(newCust)
                    // Refresh list and auto-select the new customer
                    customerVm.setQuery("")
                    showCustomerSearch = false
                    customerQuery = ""
                },
                onDismiss = { showCustomerSearch = false; customerQuery = "" }
            )
        }
    }
}

// ─── Customer Search Sheet ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, com.google.accompanist.permissions.ExperimentalPermissionsApi::class)
@Composable
private fun CustomerSearchSheet(
    query: String,
    customers: List<Customer>,
    onQueryChange: (String) -> Unit,
    onSelect: (Customer) -> Unit,
    onNewCustomer: (String, String) -> Unit,  // name, phone
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val contactsPermission = com.google.accompanist.permissions.rememberPermissionState(android.Manifest.permission.READ_CONTACTS)
    var showAddForm by remember { mutableStateOf(false) }
    var newName  by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }
    var pendingPickAfterPermission by remember { mutableStateOf(false) }

    // Contact picker — queries the specific picked contact's phone number
    val contactPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickContact()
    ) { uri ->
        uri?.let {
            try {
                // Get contact ID from the returned URI
                val contactId = uri.lastPathSegment
                // Query phone number for THIS specific contact
                val cursor = context.contentResolver.query(
                    android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
                    ),
                    "${android.provider.ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                    arrayOf(contactId),
                    "${android.provider.ContactsContract.CommonDataKinds.Phone.IS_PRIMARY} DESC"
                )
                cursor?.use { c ->
                    if (c.moveToFirst()) {
                        newName  = c.getString(0) ?: ""
                        // Clean number: keep only digits and +
                        newPhone = c.getString(1)
                            ?.replace(" ", "")
                            ?.replace("-", "")
                            ?.replace("(", "")
                            ?.replace(")", "")
                            ?: ""
                        showAddForm = true
                    }
                }
            } catch (_: Exception) {
                // Permission denied — open form for manual entry
                showAddForm = true
            }
        }
    }

    // Auto-launch picker once permission is granted
    LaunchedEffect(contactsPermission.status) {
        if (pendingPickAfterPermission &&
            contactsPermission.status is com.google.accompanist.permissions.PermissionStatus.Granted) {
            pendingPickAfterPermission = false
            contactPicker.launch(null)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = DT.Surface) {
        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Select Customer", color = Color.White, fontWeight = FontWeight.Bold,
                    fontSize = 18.sp, modifier = Modifier.weight(1f))
                // Import from contacts
                IconButton(onClick = {
                    if (contactsPermission.status is com.google.accompanist.permissions.PermissionStatus.Granted) {
                        contactPicker.launch(null)
                    } else {
                        pendingPickAfterPermission = true
                        contactsPermission.launchPermissionRequest()
                    }
                }) {
                    Icon(Icons.Default.ImportContacts, null, tint = DT.Teal, modifier = Modifier.size(22.dp))
                }
                // Quick add new
                Box(modifier = Modifier.size(36.dp).clip(CircleShape)
                    .background(DT.Teal)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                        showAddForm = !showAddForm; newName = ""; newPhone = ""
                    },
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PersonAdd, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            // Quick-add form (expands when + tapped or contact selected)
            AnimatedVisibility(visible = showAddForm) {
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(DT.Surface2).border(1.dp, DT.Teal.copy(0.3f), RoundedCornerShape(16.dp))
                    .padding(14.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("New Customer", color = DT.Teal, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        OutlinedTextField(value = newName, onValueChange = { newName = it },
                            label = { Text("Full Name *", color = DT.SubText) },
                            leadingIcon = { Icon(Icons.Default.Person, null, tint = DT.SubText) },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border,
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                cursorColor = DT.Teal, focusedContainerColor = DT.Bg, unfocusedContainerColor = DT.Bg))
                        OutlinedTextField(value = newPhone, onValueChange = { newPhone = it },
                            label = { Text("Phone Number", color = DT.SubText) },
                            leadingIcon = { Icon(Icons.Default.Phone, null, tint = DT.SubText) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border,
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                cursorColor = DT.Teal, focusedContainerColor = DT.Bg, unfocusedContainerColor = DT.Bg))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { showAddForm = false },
                                modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, DT.Border)) {
                                Text("Cancel", color = DT.SubText)
                            }
                            Button(onClick = {
                                if (newName.isNotBlank()) {
                                    onNewCustomer(newName.trim(), newPhone.trim())
                                    showAddForm = false
                                }
                            }, modifier = Modifier.weight(1f),
                                enabled = newName.isNotBlank(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DT.Green)) {
                                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Save & Select", color = Color.White, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // Search field
            OutlinedTextField(value = query, onValueChange = onQueryChange,
                placeholder = { Text("Search by name or phone", color = DT.SubText) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = DT.SubText, modifier = Modifier.size(20.dp)) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, null, tint = DT.SubText, modifier = Modifier.size(16.dp)) }
                },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border,
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    cursorColor = DT.Teal, focusedContainerColor = DT.Bg, unfocusedContainerColor = DT.Bg))

            // Customer list
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 320.dp)) {
                if (customers.isEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.PeopleOutline, null, tint = DT.SubText.copy(0.4f), modifier = Modifier.size(40.dp))
                            Text("No customers found", color = DT.SubText)
                            Text("Tap + to add one now", color = DT.SubText.copy(0.6f), fontSize = 12.sp)
                        }
                    }
                }
                items(customers, key = { it.id }) { cust ->
                    Box(modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(DT.Surface2).border(1.dp, DT.Border, RoundedCornerShape(14.dp))
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onSelect(cust) }
                        .padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(42.dp).clip(CircleShape)
                                .background(Brush.linearGradient(listOf(DT.Teal, Color(0xFF004D40)))),
                                contentAlignment = Alignment.Center) {
                                Text(cust.name.firstOrNull()?.uppercase() ?: "?",
                                    color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(cust.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                if (cust.phone.isNotBlank()) Text(cust.phone, color = DT.SubText, fontSize = 12.sp)
                                Text("${cust.visitCount} visits  •  KES ${String.format("%.0f", cust.totalPurchases)} total",
                                    color = DT.SubText, fontSize = 10.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Box(Modifier.clip(RoundedCornerShape(8.dp))
                                    .background(if (cust.creditBalance > 0) DT.Teal.copy(0.15f) else DT.Bg)
                                    .border(1.dp, if (cust.creditBalance > 0) DT.Teal.copy(0.4f) else DT.Border, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)) {
                                    Text("KES ${String.format("%.2f", cust.creditBalance)}",
                                        color = if (cust.creditBalance > 0) DT.Teal else DT.SubText,
                                        fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

@Composable
private fun SummaryLine(label: String, value: String, color: Color, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = DT.SubText, fontSize = 13.sp)
        Text(value, color = color, fontSize = 13.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun CreditStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = DT.SubText, fontSize = 9.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(3.dp))
        Text(value, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
    }
}

@Composable
private fun PaymentCard(modifier: Modifier, label: String, icon: ImageVector,
    method: PaymentMethod, selected: PaymentMethod, enabled: Boolean = true,
    onSelect: (PaymentMethod) -> Unit) {
    val isSelected = method == selected
    Box(modifier = modifier.height(80.dp).clip(RoundedCornerShape(18.dp))
        .background(if (isSelected) DT.Teal else DT.Surface)
        .border(2.dp, if (isSelected) DT.Teal else if (!enabled) DT.Border.copy(0.4f) else DT.Border, RoundedCornerShape(18.dp))
        .clickable(enabled = enabled, indication = null, interactionSource = remember { MutableInteractionSource() }) { onSelect(method) },
        contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = if (isSelected) Color.White else if (!enabled) DT.SubText.copy(0.4f) else DT.SubText, modifier = Modifier.size(26.dp))
            Text(label, color = if (isSelected) Color.White else if (!enabled) DT.SubText.copy(0.4f) else DT.SubText,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal, fontSize = 13.sp)
        }
    }
}
