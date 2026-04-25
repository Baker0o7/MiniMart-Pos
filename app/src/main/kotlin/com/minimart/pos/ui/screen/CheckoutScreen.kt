package com.minimart.pos.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimart.pos.data.entity.PaymentMethod
import com.minimart.pos.ui.theme.DT
import com.minimart.pos.ui.viewmodel.CartViewModel
import com.minimart.pos.ui.viewmodel.CheckoutResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreen(
    onSaleComplete: (Long) -> Unit,
    onBack: () -> Unit,
    vm: CartViewModel
) {
    val state by vm.uiState.collectAsState()
    val currency by vm.currency.collectAsState()
    var selectedMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var cashInput by remember { mutableStateOf("") }
    var mpesaRef by remember { mutableStateOf("") }
    var globalDiscount by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val cashAmount = cashInput.toDoubleOrNull() ?: 0.0
    val change = (cashAmount - state.total).coerceAtLeast(0.0)

    LaunchedEffect(Unit) {
        vm.checkoutResult.collect { result ->
            if (result is CheckoutResult.Success) onSaleComplete(result.saleId)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(DT.Bg)) {
        Column(
            modifier = Modifier.fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = DT.OnSurface)
                }
                Spacer(Modifier.width(4.dp))
                Column {
                    Text("Checkout", color = DT.Teal, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
                    Text("${state.itemCount} item${if (state.itemCount != 1) "s" else ""}",
                        color = DT.SubText, style = MaterialTheme.typography.bodyMedium)
                }
            }

            // ── Order Summary card ────────────────────────────────────────────
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(DT.Surface)
                    .border(1.dp, DT.Border, RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Order Summary", color = DT.OnSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    HorizontalDivider(color = DT.Border)

                    // Items
                    state.items.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Product icon placeholder (matching screenshot style)
                            Box(
                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(DT.TealDim),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Inventory2, null, tint = DT.Teal, modifier = Modifier.size(24.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.product.name, color = DT.OnSurface, fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium)
                                Text("$currency ${String.format("%.2f", item.product.price)} × ${item.quantity}",
                                    color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                            }
                            // Green price badge
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                    .background(DT.Green.copy(alpha = 0.15f))
                                    .border(1.dp, DT.Green.copy(0.3f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("$currency ${String.format("%.2f", item.lineTotal)}",
                                    color = DT.Green, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }

                    HorizontalDivider(color = DT.Border)

                    // Discount field
                    OutlinedTextField(
                        value = globalDiscount,
                        onValueChange = { globalDiscount = it; vm.setGlobalDiscount(it.toDoubleOrNull() ?: 0.0) },
                        label = { Text("Discount ($currency)", color = DT.SubText) },
                        leadingIcon = { Icon(Icons.Default.Discount, null, tint = DT.SubText) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border,
                            focusedTextColor = DT.OnSurface, unfocusedTextColor = DT.OnSurface,
                            cursorColor = DT.Teal, focusedContainerColor = DT.Bg, unfocusedContainerColor = DT.Bg
                        )
                    )

                    // Tax row
                    if (state.totalTax > 0) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Tax:", color = DT.SubText)
                            Text("$currency ${String.format("%.2f", state.totalTax)}", color = DT.SubText)
                        }
                    }

                    // Change row — shows inside summary as soon as cash >= total
                    if (selectedMethod == PaymentMethod.CASH && cashAmount >= state.total && state.total > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(DT.Green.copy(0.12f)).padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Money, null, tint = DT.Green, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Change", color = DT.Green, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Text("$currency ${String.format("%.2f", change)}", color = DT.Green, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        }
                    }

                    // Total
                    HorizontalDivider(color = DT.Border)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("TOTAL", color = DT.OnSurface, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        Text("$currency ${String.format("%.2f", state.total)}",
                            color = DT.Teal, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Payment Method ────────────────────────────────────────────────
            Text("Payment Method", color = DT.OnSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(10.dp))

            // Cash + M-Pesa only (no Card)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PaymentCard(Modifier.weight(1f), "Cash", Icons.Default.Money, PaymentMethod.CASH, selectedMethod) { selectedMethod = it; if (it == PaymentMethod.MPESA) cashInput = "" }
                PaymentCard(Modifier.weight(1f), "M-Pesa", Icons.Default.PhoneAndroid, PaymentMethod.MPESA, selectedMethod) { selectedMethod = it; if (it == PaymentMethod.CASH) mpesaRef = "" }
            }

            Spacer(Modifier.height(16.dp))

            // ── Payment inputs ────────────────────────────────────────────────
            Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (selectedMethod) {
                    PaymentMethod.CASH -> {
                        // Cash received field
                        OutlinedTextField(
                            value = cashInput,
                            onValueChange = { cashInput = it },
                            label = { Text("Cash Received ($currency)", color = DT.SubText) },
                            leadingIcon = { Icon(Icons.Default.Money, null, tint = DT.SubText) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border,
                                focusedTextColor = DT.OnSurface, unfocusedTextColor = DT.OnSurface,
                                cursorColor = DT.Teal, focusedContainerColor = DT.Bg, unfocusedContainerColor = DT.Bg
                            )
                        )
                        // Quick cash buttons
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            listOf(50, 100, 200, 500, 1000).forEach { amt ->
                                Box(
                                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                        .background(DT.Surface).border(1.dp, DT.Border, RoundedCornerShape(10.dp))
                                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { cashInput = amt.toString() }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(amt.toString(), color = DT.OnSurface, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        // (Change is shown in Order Summary above)
                    }
                    PaymentMethod.MPESA -> {
                        OutlinedTextField(
                            value = mpesaRef,
                            onValueChange = { mpesaRef = it.uppercase() },
                            label = { Text("M-Pesa Ref (optional)", color = DT.SubText) },
                            leadingIcon = { Icon(Icons.Default.ConfirmationNumber, null, tint = DT.SubText) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border,
                                focusedTextColor = DT.OnSurface, unfocusedTextColor = DT.OnSurface,
                                cursorColor = DT.Teal, focusedContainerColor = DT.Bg, unfocusedContainerColor = DT.Bg
                            )
                        )
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(DT.TealDim).padding(16.dp)
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Amount due", color = DT.SubText)
                                Text("$currency ${String.format("%.2f", state.total)}", color = DT.Teal, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            }
                        }
                    }
                    else -> {}
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Complete Checkout button ───────────────────────────────────────
            val canComplete = when (selectedMethod) {
                PaymentMethod.CASH -> cashAmount >= state.total && state.total > 0
                PaymentMethod.MPESA -> state.total > 0
                else -> false
            }

            Button(
                onClick = {
                    vm.checkout(
                        paymentMethod = selectedMethod,
                        amountPaid = if (selectedMethod == PaymentMethod.CASH) cashAmount else state.total,
                        mpesaRef = mpesaRef.takeIf { it.isNotBlank() }
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(58.dp),
                enabled = canComplete && !state.isLoading,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DT.Green,
                    disabledContainerColor = DT.Surface2
                )
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Text(
                        when {
                            !canComplete && selectedMethod == PaymentMethod.CASH && state.total > 0 -> "ENTER CASH AMOUNT"
                            !canComplete -> "SELECT PAYMENT"
                            else -> "COMPLETE CHECKOUT"
                        },
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = if (canComplete) Color.Black else DT.SubText,
                        letterSpacing = 1.sp
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PaymentCard(
    modifier: Modifier,
    label: String,
    icon: ImageVector,
    method: PaymentMethod,
    selected: PaymentMethod,
    onSelect: (PaymentMethod) -> Unit
) {
    val isSelected = method == selected
    Box(
        modifier = modifier.height(80.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) DT.Teal else DT.Surface)
            .border(2.dp, if (isSelected) DT.Teal else DT.Border, RoundedCornerShape(16.dp))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onSelect(method) },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null,
                tint = if (isSelected) Color.Black else DT.SubText,
                modifier = Modifier.size(24.dp))
            Text(label,
                color = if (isSelected) Color.Black else DT.SubText,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}
