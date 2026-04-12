package com.minimart.pos.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.minimart.pos.data.entity.PaymentMethod
import com.minimart.pos.ui.theme.Accent
import com.minimart.pos.ui.theme.Brand500
import com.minimart.pos.ui.theme.SuccessGreen
import com.minimart.pos.ui.viewmodel.CartViewModel
import com.minimart.pos.ui.viewmodel.CheckoutResult
import kotlinx.coroutines.flow.collect

@Composable
fun CheckoutScreen(
    onSaleComplete: (Long) -> Unit,
    onBack: () -> Unit,
    vm: CartViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsState()
    val currency by vm.currency.collectAsState()
    var selectedMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var cashInput by remember { mutableStateOf("") }
    var mpesaRef by remember { mutableStateOf("") }
    var globalDiscount by remember { mutableStateOf("") }

    val cashAmount = cashInput.toDoubleOrNull() ?: 0.0
    val change = (cashAmount - state.total).coerceAtLeast(0.0)

    LaunchedEffect(Unit) {
        vm.checkoutResult.collect { result ->
            if (result is CheckoutResult.Success) onSaleComplete(result.saleId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Checkout", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Brand500, titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Order summary card ────────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Order Summary", fontWeight = FontWeight.SemiBold)
                    Divider()
                    state.items.forEach { item ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text("${item.product.name} ×${item.quantity}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text("$currency ${String.format("%.2f", item.lineTotal)}", fontWeight = FontWeight.Medium)
                        }
                    }
                    Divider()
                    // Global discount
                    OutlinedTextField(
                        value = globalDiscount,
                        onValueChange = {
                            globalDiscount = it
                            vm.setGlobalDiscount(it.toDoubleOrNull() ?: 0.0)
                        },
                        label = { Text("Discount ($currency)") },
                        leadingIcon = { Icon(Icons.Default.Discount, null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (state.totalTax > 0) SummaryRow("Tax (VAT)", "$currency ${String.format("%.2f", state.totalTax)}")
                    if (state.totalDiscount > 0) SummaryRow("Discount", "-$currency ${String.format("%.2f", state.totalDiscount)}", SuccessGreen)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("TOTAL", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("$currency ${String.format("%.2f", state.total)}", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Brand500)
                    }
                }
            }

            // ── Payment method grid ───────────────────────────────────────────
            Text("Payment Method", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PaymentMethodCard(Modifier.weight(1f), "Cash", Icons.Default.Money, PaymentMethod.CASH, selectedMethod) { selectedMethod = it }
                PaymentMethodCard(Modifier.weight(1f), "M-Pesa", Icons.Default.PhoneAndroid, PaymentMethod.MPESA, selectedMethod) { selectedMethod = it }
                PaymentMethodCard(Modifier.weight(1f), "Card", Icons.Default.CreditCard, PaymentMethod.CARD, selectedMethod) { selectedMethod = it }
            }

            // ── Payment inputs ────────────────────────────────────────────────
            Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (selectedMethod) {
                    PaymentMethod.CASH -> {
                        OutlinedTextField(
                            value = cashInput,
                            onValueChange = { cashInput = it },
                            label = { Text("Cash Received ($currency)") },
                            leadingIcon = { Icon(Icons.Default.Money, null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        // Quick cash buttons
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(50, 100, 200, 500, 1000).forEach { amt ->
                                OutlinedButton(
                                    onClick = { cashInput = amt.toString() },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(36.dp)
                                ) { Text("$amt", style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                        if (cashAmount >= state.total && state.total > 0) {
                            Card(colors = CardDefaults.cardColors(containerColor = SuccessGreen.copy(alpha = 0.1f)), shape = RoundedCornerShape(10.dp)) {
                                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Change", fontWeight = FontWeight.SemiBold, color = SuccessGreen)
                                    Text("$currency ${String.format("%.2f", change)}", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = SuccessGreen)
                                }
                            }
                        }
                    }
                    PaymentMethod.MPESA -> {
                        OutlinedTextField(
                            value = mpesaRef,
                            onValueChange = { mpesaRef = it.uppercase() },
                            label = { Text("M-Pesa Reference (optional)") },
                            leadingIcon = { Icon(Icons.Default.ConfirmationNumber, null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Card(colors = CardDefaults.cardColors(containerColor = Accent.copy(alpha = 0.1f)), shape = RoundedCornerShape(10.dp)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Ask customer to send payment to:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$currency ${String.format("%.2f", state.total)}", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Accent)
                            }
                        }
                    }
                    PaymentMethod.CARD -> {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(10.dp)) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Process card payment on your POS terminal, then confirm.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    PaymentMethod.MIXED -> {}
                }
            }

            // ── Complete button ───────────────────────────────────────────────
            val canComplete = when (selectedMethod) {
                PaymentMethod.CASH -> cashAmount >= state.total && state.total > 0
                PaymentMethod.MPESA, PaymentMethod.CARD -> state.total > 0
                PaymentMethod.MIXED -> false
            }
            Button(
                onClick = {
                    vm.checkout(
                        paymentMethod = selectedMethod,
                        amountPaid = if (selectedMethod == PaymentMethod.CASH) cashAmount else state.total,
                        mpesaRef = mpesaRef.takeIf { it.isNotBlank() }
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(56.dp),
                enabled = canComplete && !state.isLoading,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White)
                } else {
                    Icon(Icons.Default.CheckCircle, null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Complete Sale", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PaymentMethodCard(
    modifier: Modifier,
    label: String,
    icon: ImageVector,
    method: PaymentMethod,
    selected: PaymentMethod,
    onSelect: (PaymentMethod) -> Unit
) {
    val isSelected = method == selected
    Card(
        modifier = modifier.clickable { onSelect(method) },
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) BorderStroke(2.dp, Brand500) else null,
        colors = CardDefaults.cardColors(containerColor = if (isSelected) Brand500.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, null, tint = if (isSelected) Brand500 else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) Brand500 else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = color)
    }
}
