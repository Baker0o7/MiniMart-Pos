package com.minimart.pos.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.minimart.pos.data.entity.Expense
import com.minimart.pos.data.entity.ExpenseCategory
import com.minimart.pos.ui.theme.Brand500
import com.minimart.pos.ui.theme.ErrorRed
import com.minimart.pos.ui.theme.SuccessGreen
import com.minimart.pos.ui.viewmodel.ExpenseViewModel
import com.minimart.pos.ui.viewmodel.ReportPeriod
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseScreen(
    onBack: () -> Unit,
    vm: ExpenseViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsState()
    val period by vm.period.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(state.successMessage, state.error) {
        if (state.successMessage != null || state.error != null) {
            kotlinx.coroutines.delay(2000)
            vm.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Expenses & P&L", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Brand500, titleContentColor = Color.White, navigationIconContentColor = Color.White),
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, "Add expense", tint = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // ── Period tabs ───────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReportPeriod.entries.forEach { p ->
                    FilterChip(
                        selected = period == p,
                        onClick = { vm.setPeriod(p) },
                        label = { Text(p.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            // ── Tab bar ───────────────────────────────────────────────────────
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("P&L Report") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Expenses") })
            }

            // Feedback
            state.successMessage?.let {
                Row(modifier = Modifier.fillMaxWidth().background(SuccessGreen).padding(10.dp)) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(it, color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
            }
            state.error?.let {
                Row(modifier = Modifier.fillMaxWidth().background(ErrorRed).padding(10.dp)) {
                    Icon(Icons.Default.Error, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(it, color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
            }

            when (selectedTab) {
                0 -> ProfitLossTab(state.totalRevenue, state.totalExpenses, state.netProfit, state.profitMargin, state.expensesByCategory, state.currency)
                1 -> ExpenseListTab(state.expenses, state.currency, onDelete = { vm.deleteExpense(it) })
            }
        }
    }

    if (showAddDialog) {
        AddExpenseDialog(
            onDismiss = { showAddDialog = false },
            onSave = { vm.addExpense(it); showAddDialog = false }
        )
    }
}

@Composable
private fun ProfitLossTab(
    revenue: Double, expenses: Double, netProfit: Double,
    margin: Double, byCategory: Map<ExpenseCategory, Double>, currency: String
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Summary cards ──
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                PLCard(Modifier.weight(1f), "Revenue", revenue, currency, SuccessGreen, Icons.AutoMirrored.Filled.TrendingUp)
                PLCard(Modifier.weight(1f), "Expenses", expenses, currency, ErrorRed, Icons.AutoMirrored.Filled.TrendingDown)
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (netProfit >= 0) SuccessGreen.copy(alpha = 0.12f) else ErrorRed.copy(alpha = 0.12f)
                )
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Net Profit", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${if (netProfit < 0) "-" else "+"}$currency ${String.format("%.2f", Math.abs(netProfit))}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            color = if (netProfit >= 0) SuccessGreen else ErrorRed
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Margin", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${String.format("%.1f", margin)}%", fontWeight = FontWeight.Bold, fontSize = 18.sp,
                            color = if (margin >= 0) SuccessGreen else ErrorRed)
                    }
                }
            }
        }

        // ── By category ──
        if (byCategory.isNotEmpty()) {
            item { Text("Expenses by Category", fontWeight = FontWeight.SemiBold) }
            items(byCategory.entries.sortedByDescending { it.value }.toList()) { (cat, amount) ->
                Card(shape = RoundedCornerShape(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(categoryEmoji(cat), fontSize = 20.sp)
                            Spacer(Modifier.width(10.dp))
                            Text(cat.name.lowercase().replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Medium)
                        }
                        Text("$currency ${String.format("%.2f", amount)}", fontWeight = FontWeight.SemiBold, color = ErrorRed)
                    }
                }
            }
        }
    }
}

@Composable
private fun PLCard(modifier: Modifier, label: String, amount: Double, currency: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(modifier = modifier, shape = RoundedCornerShape(14.dp)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            Text("$currency ${String.format("%.2f", amount)}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ExpenseListTab(expenses: List<Expense>, currency: String, onDelete: (Expense) -> Unit) {
    if (expenses.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Receipt, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
                Spacer(Modifier.height(8.dp))
                Text("No expenses recorded", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Tap + to add an expense", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f))
            }
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(expenses, key = { it.id }) { expense ->
                ExpenseRow(expense, currency, onDelete)
            }
        }
    }
}

@Composable
private fun ExpenseRow(expense: Expense, currency: String, onDelete: (Expense) -> Unit) {
    var showConfirm by remember { mutableStateOf(false) }
    val df = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault())
    Card(shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(categoryEmoji(expense.category), fontSize = 24.sp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(expense.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text("${expense.category.name} • ${df.format(Date(expense.createdAt))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (expense.supplierName.isNotBlank()) Text(expense.supplierName, style = MaterialTheme.typography.labelSmall, color = Brand500)
            }
            Text("$currency ${String.format("%.2f", expense.amount)}", fontWeight = FontWeight.Bold, color = ErrorRed)
            IconButton(onClick = { showConfirm = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, null, tint = ErrorRed, modifier = Modifier.size(18.dp))
            }
        }
    }
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Delete Expense?") },
            text = { Text("Remove '${expense.title}'?") },
            confirmButton = { TextButton(onClick = { onDelete(expense); showConfirm = false }) { Text("Delete", color = ErrorRed) } },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Cancel") } }
        )
    }
}

// ─── Add Expense Dialog ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExpenseDialog(onDismiss: () -> Unit, onSave: (Expense) -> Unit) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(ExpenseCategory.SUPPLIER) }
    var supplierName by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var receiptRef by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Expense", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Description *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    amount, { amount = it },
                    label = { Text("Amount (KES) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                // Category dropdown
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = "${categoryEmoji(category)} ${category.name}",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        ExpenseCategory.entries.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text("${categoryEmoji(cat)} ${cat.name}") },
                                onClick = { category = cat; expanded = false }
                            )
                        }
                    }
                }
                if (category == ExpenseCategory.SUPPLIER) {
                    OutlinedTextField(supplierName, { supplierName = it }, label = { Text("Supplier Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                OutlinedTextField(receiptRef, { receiptRef = it }, label = { Text("Receipt/Invoice Ref") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, maxLines = 2, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(Expense(
                        title = title.trim(),
                        amount = amount.toDoubleOrNull() ?: 0.0,
                        category = category,
                        supplierName = supplierName.trim(),
                        notes = notes.trim(),
                        receiptRef = receiptRef.trim()
                    ))
                },
                enabled = title.isNotBlank() && amount.toDoubleOrNull() != null && (amount.toDoubleOrNull() ?: 0.0) > 0
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun categoryEmoji(cat: ExpenseCategory) = when (cat) {
    ExpenseCategory.SUPPLIER    -> "🏪"
    ExpenseCategory.ELECTRICITY -> "⚡"
    ExpenseCategory.WATER       -> "💧"
    ExpenseCategory.RENT        -> "🏠"
    ExpenseCategory.SALARY      -> "👤"
    ExpenseCategory.TRANSPORT   -> "🚗"
    ExpenseCategory.PACKAGING   -> "📦"
    ExpenseCategory.CLEANING    -> "🧹"
    ExpenseCategory.MAINTENANCE -> "🔧"
    ExpenseCategory.TAXES       -> "📋"
    ExpenseCategory.OTHER       -> "💰"
}
