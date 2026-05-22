package com.minimart.pos.ui.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import com.minimart.pos.data.entity.PaymentMethod
import com.minimart.pos.ui.theme.DT
import com.minimart.pos.ui.viewmodel.CartViewModel
import com.minimart.pos.ui.viewmodel.CheckoutResult

@Composable
fun CheckoutScreen(
    onSaleComplete: (Long) -> Unit,
    onBack: () -> Unit,
    vm: CartViewModel,
    canApplyDiscounts: Boolean = true
) {
    val state    by vm.uiState.collectAsState()
    val currency by vm.currency.collectAsState()
    var selectedMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var cashInput      by remember { mutableStateOf("") }
    var mpesaRef       by remember { mutableStateOf("") }
    var globalDiscount by remember { mutableStateOf("") }

    var selectedCustomerId by remember { mutableStateOf<Long?>(null) }
    var selectedCustomerName by remember { mutableStateOf<String?>(null) }
    var selectedCustomerCredit by remember { mutableStateOf(0.0) }
    var showCustomerSearch by remember { mutableStateOf(false) }
    val cashAmount = cashInput.toDoubleOrNull() ?: 0.0
    val change     = (cashAmount - state.total).coerceAtLeast(0.0)
    val canComplete = when (selectedMethod) {
        PaymentMethod.CASH   -> cashAmount >= state.total && state.total > 0
        PaymentMethod.MPESA  -> state.total > 0
        PaymentMethod.CREDIT -> selectedCustomerId != null && selectedCustomerCredit >= state.total && state.total > 0
        else -> false
    }

    LaunchedEffect(Unit) {
        vm.checkoutResult.collect { result ->
            if (result is CheckoutResult.Success) onSaleComplete(result.saleId)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(DT.Bg)) {
        Column(modifier = Modifier.fillMaxSize().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState())) {

            // ── Top bar ───────────────────────────────────────────────────────
            Box(modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .background(Brush.verticalGradient(listOf(DT.Teal, Color(0xFF006B5E), Color(0xFF004D40))))
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(40.dp).clip(CircleShape)
                            .background(Color.White.copy(0.15f))
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onBack),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Checkout", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                            Text("${state.itemCount} item${if (state.itemCount != 1) "s" else ""}  •  $currency ${String.format("%.2f", state.total)}",
                                color = Color.White.copy(0.75f), fontSize = 13.sp)
                        }
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

                    // Discount field
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

                    // VAT (inclusive)
                    if (state.totalTax > 0) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("VAT (incl.)", color = DT.SubText, fontSize = 13.sp)
                            Text("$currency ${String.format("%.2f", state.totalTax)}", color = DT.SubText, fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                    // Discount shown
                    if (state.totalDiscount > 0) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Discount", color = DT.Amber, fontSize = 13.sp)
                            Text("- $currency ${String.format("%.2f", state.totalDiscount)}", color = DT.Amber, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                    // Change pill
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

                    // Total
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("TOTAL", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, letterSpacing = 1.sp)
                        Text("$currency ${String.format("%.2f", state.total)}", color = DT.Teal,
                            fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
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
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PaymentCard(Modifier.weight(1f), "Cash", Icons.Default.Money, PaymentMethod.CASH, selectedMethod) {
                    selectedMethod = it; if (it == PaymentMethod.MPESA) cashInput = ""
                }
                PaymentCard(Modifier.weight(1f), "M-Pesa", Icons.Default.PhoneAndroid, PaymentMethod.MPESA, selectedMethod) {
                    selectedMethod = it; if (it == PaymentMethod.CASH) mpesaRef = ""
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PaymentCard(Modifier.weight(1f), "Credit", Icons.Default.AccountBalanceWallet, PaymentMethod.CREDIT, selectedMethod) {
                    selectedMethod = it
                }
                Spacer(Modifier.weight(1f))
            }

            Spacer(Modifier.height(16.dp))

            // ── Payment Inputs ────────────────────────────────────────────────
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
                            // Quick cash grid
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
                                            style = MaterialTheme.typography.labelSmall)
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
                            // M-Pesa amount box
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
                            // Customer selector
                            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .background(DT.Surface).border(1.dp, DT.Border, RoundedCornerShape(14.dp))
                                .clickable { showCustomerSearch = true }.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Person, null, tint = DT.Teal, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(selectedCustomerName ?: "Select Customer", color = if (selectedCustomerName != null) Color.White else DT.SubText, fontWeight = FontWeight.SemiBold)
                                        if (selectedCustomerId != null)
                                            Text("Credit: KES ${String.format("%.2f", selectedCustomerCredit)}", color = DT.Teal, fontSize = 12.sp)
                                    }
                                    Icon(Icons.Default.ChevronRight, null, tint = DT.SubText, modifier = Modifier.size(18.dp))
                                }
                            }
                            if (selectedCustomerId != null && selectedCustomerCredit < state.total) {
                                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                    .background(DT.Red.copy(0.1f)).border(1.dp, DT.Red.copy(0.3f), RoundedCornerShape(12.dp))
                                    .padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Warning, null, tint = DT.Red, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Insufficient credit. Balance: KES ${String.format("%.2f", selectedCustomerCredit)}", color = DT.Red, fontSize = 12.sp)
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
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Button(
                    onClick = {
                        vm.checkout(
                            paymentMethod = selectedMethod,
                            amountPaid = if (selectedMethod == PaymentMethod.CASH) cashAmount else state.total,
                            mpesaRef = mpesaRef.takeIf { it.isNotBlank() },
                            customerId = selectedCustomerId
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    enabled = canComplete && !state.isLoading,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DT.Green,
                        disabledContainerColor = DT.Surface2
                    ),
                    elevation = ButtonDefaults.buttonElevation(6.dp)
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(
                            if (canComplete) Icons.Default.CheckCircle else Icons.Default.Lock,
                            null, tint = if (canComplete) Color.White else DT.SubText,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            when {
                                !canComplete && selectedMethod == PaymentMethod.CASH && state.total > 0 -> "ENTER CASH AMOUNT"
                                !canComplete -> "SELECT PAYMENT METHOD"
                                else -> "COMPLETE CHECKOUT"
                            },
                            fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, letterSpacing = 0.5.sp,
                            color = if (canComplete) Color.White else DT.SubText
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─── Payment Method Card ───────────────────────────────────────────────────────

@Composable
private fun PaymentCard(modifier: Modifier, label: String, icon: ImageVector,
    method: PaymentMethod, selected: PaymentMethod, onSelect: (PaymentMethod) -> Unit) {
    val isSelected = method == selected
    val bgColor = if (isSelected) DT.Teal else DT.Surface
    val borderColor = if (isSelected) DT.Teal else DT.Border
    Box(modifier = modifier.height(80.dp).clip(RoundedCornerShape(18.dp))
        .background(bgColor).border(2.dp, borderColor, RoundedCornerShape(18.dp))
        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onSelect(method) },
        contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = if (isSelected) Color.White else DT.SubText, modifier = Modifier.size(26.dp))
            Text(label, color = if (isSelected) Color.White else DT.SubText,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal, fontSize = 14.sp)
        }
    }
}
