package com.minimart.pos.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.minimart.pos.data.entity.CreditTransaction
import com.minimart.pos.data.entity.CreditTxType
import com.minimart.pos.data.entity.Customer
import com.minimart.pos.ui.theme.DT
import com.minimart.pos.ui.viewmodel.CustomerViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CustomerScreen(
    onBack: () -> Unit,
    vm: CustomerViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsState()
    var showAddDialog    by remember { mutableStateOf(false) }
    var showCreditDialog by remember { mutableStateOf<Customer?>(null) }
    var selectedCustomer by remember { mutableStateOf<Customer?>(null) }

    // Show snackbar on message
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { snack.showSnackbar(it); vm.clearMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        containerColor = DT.Bg,
        topBar = {
            Box(modifier = Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(DT.Teal, Color(0xFF004D40))))
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(40.dp).clip(CircleShape)
                        .background(Color.White.copy(0.15f))
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onBack),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Customers", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        Text("${state.customers.size} registered", color = Color.White.copy(0.7f), fontSize = 12.sp)
                    }
                    Box(modifier = Modifier.size(40.dp).clip(CircleShape)
                        .background(Color.White.copy(0.15f))
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { showAddDialog = true },
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.PersonAdd, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Search
            OutlinedTextField(
                value = state.query,
                onValueChange = { vm.setQuery(it) },
                placeholder = { Text("Search by name or phone", color = DT.SubText) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = DT.SubText, modifier = Modifier.size(20.dp)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border,
                    focusedTextColor = DT.OnSurface, unfocusedTextColor = DT.OnSurface,
                    cursorColor = DT.Teal, focusedContainerColor = DT.Surface, unfocusedContainerColor = DT.Surface),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
            )

            if (state.customers.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.People, null, modifier = Modifier.size(64.dp), tint = DT.SubText.copy(0.25f))
                        Text("No customers yet", color = DT.SubText, fontWeight = FontWeight.SemiBold)
                        Text("Tap + to add your first customer", color = DT.SubText.copy(0.6f), fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.customers, key = { it.id }) { customer ->
                        CustomerCard(
                            customer = customer,
                            onSelect = { selectedCustomer = customer; vm.selectCustomer(customer) },
                            onAddCredit = { showCreditDialog = customer }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    // Customer detail bottom sheet
    selectedCustomer?.let { cust ->
        CustomerDetailSheet(
            customer = cust,
            transactions = state.transactions,
            onDismiss = { selectedCustomer = null },
            onAddCredit = { showCreditDialog = cust },
            onEdit = { showAddDialog = true },
            onDelete = { vm.deleteCustomer(cust); selectedCustomer = null }
        )
    }

    // Add/Edit customer dialog
    if (showAddDialog) {
        AddCustomerDialog(
            customer = selectedCustomer,
            onDismiss = { showAddDialog = false },
            onSave = { vm.saveCustomer(it); showAddDialog = false }
        )
    }

    // Add credit dialog
    showCreditDialog?.let { cust ->
        AddCreditDialog(
            customer = cust,
            onDismiss = { showCreditDialog = null },
            onAdd = { amount, notes ->
                vm.addCredit(cust.id, amount, notes)
                showCreditDialog = null
            }
        )
    }
}

// ─── Customer Card ─────────────────────────────────────────────────────────────

@Composable
private fun CustomerCard(customer: Customer, onSelect: () -> Unit, onAddCredit: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(18.dp))
        .background(Brush.horizontalGradient(listOf(DT.Surface, DT.Surface2)))
        .border(1.dp, DT.Border, RoundedCornerShape(18.dp))
        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onSelect)
        .padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Avatar circle
            Box(modifier = Modifier.size(46.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(DT.Teal, Color(0xFF004D40)))),
                contentAlignment = Alignment.Center) {
                Text(customer.name.firstOrNull()?.uppercase() ?: "?",
                    color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(customer.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (customer.phone.isNotBlank())
                    Text(customer.phone, color = DT.SubText, fontSize = 12.sp)
                Text("${customer.visitCount} visits  •  KES ${String.format("%.0f", customer.totalPurchases)} total",
                    color = DT.SubText, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(modifier = Modifier.clip(RoundedCornerShape(10.dp))
                    .background(if (customer.creditBalance > 0) DT.Teal.copy(0.15f) else DT.Surface2)
                    .border(1.dp, if (customer.creditBalance > 0) DT.Teal.copy(0.4f) else DT.Border, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Text("KES ${String.format("%.2f", customer.creditBalance)}",
                        color = if (customer.creditBalance > 0) DT.Teal else DT.SubText,
                        fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Box(modifier = Modifier.size(30.dp).clip(CircleShape)
                    .background(DT.Teal.copy(0.15f))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onAddCredit),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, null, tint = DT.Teal, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ─── Customer Detail Sheet ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomerDetailSheet(
    customer: Customer, transactions: List<CreditTransaction>,
    onDismiss: () -> Unit, onAddCredit: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit
) {
    val df = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = DT.Surface,
        dragHandle = { Box(Modifier.padding(vertical = 10.dp).size(36.dp, 4.dp)
            .clip(RoundedCornerShape(2.dp)).background(DT.Border)) }) {
        LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(DT.Teal, Color(0xFF004D40)))),
                        contentAlignment = Alignment.Center) {
                        Text(customer.name.firstOrNull()?.uppercase() ?: "?",
                            color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(customer.name, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        if (customer.phone.isNotBlank()) Text(customer.phone, color = DT.SubText)
                        if (customer.email.isNotBlank()) Text(customer.email, color = DT.SubText, fontSize = 12.sp)
                    }
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, null, tint = DT.Teal) }
                    IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = DT.Red) }
                }
            }
            // Stats row
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatChip(Modifier.weight(1f), "Credit Balance",
                        "KES ${String.format("%.2f", customer.creditBalance)}", DT.Teal)
                    StatChip(Modifier.weight(1f), "Total Spent",
                        "KES ${String.format("%.0f", customer.totalPurchases)}", DT.Green)
                    StatChip(Modifier.weight(1f), "Visits", customer.visitCount.toString(), DT.SubText)
                }
            }
            // Add credit button
            item {
                Button(onClick = onAddCredit, modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DT.Teal)) {
                    Icon(Icons.Default.AddCard, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add Credit", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
            // Transaction history
            if (transactions.isNotEmpty()) {
                item { Text("Credit History", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                items(transactions.take(20)) { tx ->
                    Row(modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)).background(DT.Surface2)
                        .border(1.dp, DT.Border, RoundedCornerShape(12.dp))
                        .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).clip(CircleShape)
                            .background(if (tx.amount > 0) DT.Green.copy(0.15f) else DT.Red.copy(0.15f)),
                            contentAlignment = Alignment.Center) {
                            Icon(if (tx.amount > 0) Icons.Default.Add else Icons.Default.Remove, null,
                                tint = if (tx.amount > 0) DT.Green else DT.Red, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tx.type.name.replace("_", " "), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            if (tx.notes.isNotBlank()) Text(tx.notes, color = DT.SubText, fontSize = 11.sp)
                            Text(df.format(Date(tx.createdAt)), color = DT.SubText, fontSize = 11.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${if (tx.amount > 0) "+" else ""}KES ${String.format("%.2f", tx.amount)}",
                                color = if (tx.amount > 0) DT.Green else DT.Red, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Bal: KES ${String.format("%.2f", tx.balanceAfter)}",
                                color = DT.SubText, fontSize = 10.sp)
                        }
                    }
                }
            } else {
                item { Text("No credit history yet", color = DT.SubText, fontSize = 13.sp) }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun StatChip(modifier: Modifier, label: String, value: String, color: Color) {
    Box(modifier = modifier.clip(RoundedCornerShape(14.dp))
        .background(color.copy(0.08f)).border(1.dp, color.copy(0.25f), RoundedCornerShape(14.dp))
        .padding(horizontal = 10.dp, vertical = 10.dp)) {
        Column {
            Text(label, color = DT.SubText, fontSize = 10.sp)
            Spacer(Modifier.height(3.dp))
            Text(value, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        }
    }
}

// ─── Dialogs ───────────────────────────────────────────────────────────────────

@Composable
private fun AddCustomerDialog(customer: Customer?, onDismiss: () -> Unit, onSave: (Customer) -> Unit) {
    var name  by remember { mutableStateOf(customer?.name ?: "") }
    var phone by remember { mutableStateOf(customer?.phone ?: "") }
    var email by remember { mutableStateOf(customer?.email ?: "") }
    var notes by remember { mutableStateOf(customer?.notes ?: "") }

    AlertDialog(onDismissRequest = onDismiss, containerColor = DT.Surface,
        title = { Text(if (customer == null) "New Customer" else "Edit Customer",
            color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                listOf(
                    Triple(name, { v: String -> name = v }, "Full Name *"),
                    Triple(phone, { v: String -> phone = v }, "Phone Number"),
                    Triple(email, { v: String -> email = v }, "Email (optional)"),
                    Triple(notes, { v: String -> notes = v }, "Notes (optional)")
                ).forEach { (value, onChange, label) ->
                    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label, color = DT.SubText) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border,
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            cursorColor = DT.Teal, focusedContainerColor = DT.Bg, unfocusedContainerColor = DT.Bg))
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(Customer(id = customer?.id ?: 0L, name = name.trim(),
                phone = phone.trim(), email = email.trim(), notes = notes.trim(),
                creditBalance = customer?.creditBalance ?: 0.0,
                totalPurchases = customer?.totalPurchases ?: 0.0,
                visitCount = customer?.visitCount ?: 0)) },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = DT.Green),
                shape = RoundedCornerShape(12.dp)) { Text("Save", color = Color.White, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = DT.SubText) } }
    )
}

@Composable
internal fun AddCreditDialog(customer: Customer, onDismiss: () -> Unit, onAdd: (Double, String) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var notes  by remember { mutableStateOf("") }

    AlertDialog(onDismissRequest = onDismiss, containerColor = DT.Surface,
        title = { Text("Add Credit — ${customer.name}", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Current balance: KES ${String.format("%.2f", customer.creditBalance)}",
                    color = DT.Teal, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(value = amount, onValueChange = { amount = it },
                    label = { Text("Amount (KES)", color = DT.SubText) },
                    leadingIcon = { Icon(Icons.Default.Money, null, tint = DT.SubText) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border,
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        cursorColor = DT.Teal, focusedContainerColor = DT.Bg, unfocusedContainerColor = DT.Bg))
                // Quick amounts
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(100, 200, 500, 1000).forEach { amt ->
                        OutlinedButton(onClick = { amount = amt.toString() },
                            shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (amount == amt.toString()) DT.Teal else DT.Border),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (amount == amt.toString()) DT.Teal.copy(0.15f) else Color.Transparent)) {
                            Text("$amt", color = if (amount == amt.toString()) DT.Teal else DT.SubText, fontSize = 12.sp)
                        }
                    }
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it },
                    label = { Text("Notes (optional)", color = DT.SubText) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border,
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        cursorColor = DT.Teal, focusedContainerColor = DT.Bg, unfocusedContainerColor = DT.Bg))
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(amount.toDoubleOrNull() ?: 0.0, notes.trim()) },
                enabled = (amount.toDoubleOrNull() ?: 0.0) > 0,
                colors = ButtonDefaults.buttonColors(containerColor = DT.Green),
                shape = RoundedCornerShape(12.dp)) { Text("Add Credit", color = Color.White) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = DT.SubText) } }
    )
}
