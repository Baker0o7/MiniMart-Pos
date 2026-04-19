package com.minimart.pos.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.minimart.pos.data.entity.Expense
import com.minimart.pos.data.entity.ExpenseCategory
import com.minimart.pos.ui.theme.DT
import com.minimart.pos.ui.viewmodel.ExpenseViewModel
import com.minimart.pos.ui.viewmodel.ReportPeriod
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseScreen(onBack: () -> Unit, vm: ExpenseViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsState()
    val period by vm.period.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(state.successMessage, state.error) {
        if (state.successMessage != null || state.error != null) { kotlinx.coroutines.delay(2000); vm.clearMessages() }
    }

    Box(modifier = Modifier.fillMaxSize().background(DT.Bg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Teal top bar ──────────────────────────────────────────────────
            Box(modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                .background(Brush.horizontalGradient(listOf(DT.Teal, Color(0xFF00695C))))
                .padding(horizontal = 8.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
                    Text("Expenses & P&L", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    Box(modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(0.2f)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                        TextButton(onClick = { showAddDialog = true }, contentPadding = PaddingValues(0.dp)) {
                            Text("+ Add", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ── Period tabs ───────────────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportPeriod.entries.forEach { p ->
                    val sel = period == p
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(20.dp))
                            .background(if (sel) DT.Teal else DT.Surface)
                            .border(1.dp, if (sel) DT.Teal else DT.Border, RoundedCornerShape(20.dp))
                            .clickable(
                                indication = null,
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                            ) { vm.setPeriod(p) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(p.name.lowercase().replaceFirstChar { it.uppercase() },
                            color = if (sel) Color.White else DT.SubText,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                            style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // ── Screen tabs ───────────────────────────────────────────────────
            TabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent,
                contentColor = DT.Teal, dividerColor = DT.Border) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                    Text("P&L Report", color = if (selectedTab == 0) DT.Teal else DT.SubText,
                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(vertical = 12.dp))
                }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                    Text("Expenses", color = if (selectedTab == 1) DT.Teal else DT.SubText,
                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(vertical = 12.dp))
                }
            }

            when (selectedTab) {
                0 -> PLTab(state.totalRevenue, state.totalExpenses, state.netProfit, state.profitMargin,
                    state.expensesByCategory, state.currency)
                1 -> ExpenseListTab(state.expenses, state.currency) { vm.deleteExpense(it) }
            }
        }
    }

    if (showAddDialog) {
        AddExpenseDialog(onDismiss = { showAddDialog = false }, onSave = { vm.addExpense(it); showAddDialog = false })
    }
}

@Composable
private fun PLTab(revenue: Double, expenses: Double, netProfit: Double,
    margin: Double, byCategory: Map<ExpenseCategory, Double>, currency: String) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Revenue + Expenses side by side with line charts
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                // Revenue card
                Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                    .background(DT.Surface).border(1.dp, DT.Border, RoundedCornerShape(16.dp)).padding(16.dp)) {
                    Column {
                        Text("Revenue", color = DT.OnSurface, fontWeight = FontWeight.Bold)
                        Text("$currency ${String.format("%.2f", revenue)}", color = DT.OnSurface, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        Spacer(Modifier.height(12.dp))
                        LineChart(listOf(0.3f,0.5f,0.8f,0.6f,1.0f), DT.Teal, modifier = Modifier.fillMaxWidth().height(60.dp))
                    }
                }
                // Expenses card
                Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                    .background(DT.Surface).border(1.dp, DT.Border, RoundedCornerShape(16.dp)).padding(16.dp)) {
                    Column {
                        Text("Expenses", color = DT.OnSurface, fontWeight = FontWeight.Bold)
                        Text("$currency ${String.format("%.2f", expenses)}", color = DT.OnSurface, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        Spacer(Modifier.height(12.dp))
                        LineChart(listOf(0.2f,0.6f,0.4f,0.9f,0.7f), DT.Red, modifier = Modifier.fillMaxWidth().height(60.dp))
                    }
                }
            }
        }
        // Net Profit card
        item {
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(DT.Surface).border(1.dp, DT.Border, RoundedCornerShape(16.dp)).padding(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Net Profit", color = DT.SubText, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text("${if (netProfit >= 0) "+" else ""}$currency ${String.format("%.2f", netProfit)}",
                            color = if (netProfit >= 0) DT.Green else DT.Red, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Margin", color = DT.SubText, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text("${String.format("%.1f", margin)}%", color = DT.OnSurface, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                    }
                }
            }
        }
        if (byCategory.isNotEmpty()) {
            item { Text("By Category", color = DT.OnSurface, fontWeight = FontWeight.Bold) }
            items(byCategory.entries.sortedByDescending { it.value }.toList()) { (cat, amount) ->
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DT.Surface).border(1.dp, DT.Border, RoundedCornerShape(12.dp)).padding(12.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("${categoryEmoji(cat)} ${cat.name}", color = DT.OnSurface)
                        Text("$currency ${String.format("%.2f", amount)}", color = DT.Red, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun LineChart(points: List<Float>, color: Color, modifier: Modifier = Modifier) {
    val fillColor = color.copy(alpha = 0.15f)
    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas
        val step = size.width / (points.size - 1)
        val path = Path()
        val fillPath = Path()
        points.forEachIndexed { i, v ->
            val x = i * step; val y = size.height * (1 - v * 0.9f)
            if (i == 0) { path.moveTo(x, y); fillPath.moveTo(x, size.height) }
            path.lineTo(x, y); fillPath.lineTo(x, y)
            drawCircle(color, 5.dp.toPx(), Offset(x, y))
        }
        fillPath.lineTo((points.size - 1) * step, size.height); fillPath.close()
        drawPath(fillPath, Brush.verticalGradient(listOf(fillColor, Color.Transparent)))
        drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
    }
}

@Composable
private fun ExpenseListTab(expenses: List<Expense>, currency: String, onDelete: (Expense) -> Unit) {
    if (expenses.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Receipt, null, modifier = Modifier.size(56.dp), tint = DT.SubText.copy(0.3f))
                Spacer(Modifier.height(8.dp))
                Text("No expenses recorded", color = DT.SubText)
            }
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(expenses, key = { it.id }) { expense ->
                var showConfirm by remember { mutableStateOf(false) }
                val df = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DT.Surface).border(1.dp, DT.Border, RoundedCornerShape(12.dp)).padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(categoryEmoji(expense.category), fontSize = 22.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(expense.title, color = DT.OnSurface, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            Text("${expense.category.name} • ${df.format(Date(expense.createdAt))}", color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                        }
                        Text("$currency ${String.format("%.2f", expense.amount)}", color = DT.Red, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { showConfirm = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, null, tint = DT.Red, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                if (showConfirm) AlertDialog(onDismissRequest = { showConfirm = false }, containerColor = DT.Surface,
                    title = { Text("Delete?", color = DT.OnSurface) }, text = { Text(expense.title, color = DT.SubText) },
                    confirmButton = { TextButton(onClick = { onDelete(expense); showConfirm = false }) { Text("Delete", color = DT.Red) } },
                    dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Cancel", color = DT.SubText) } })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExpenseDialog(onDismiss: () -> Unit, onSave: (Expense) -> Unit) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(ExpenseCategory.SUPPLIER) }
    var supplierName by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val fieldColors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border,
        focusedTextColor = DT.OnSurface, unfocusedTextColor = DT.OnSurface, cursorColor = DT.Teal,
        focusedContainerColor = DT.Bg, unfocusedContainerColor = DT.Bg)

    AlertDialog(onDismissRequest = onDismiss, containerColor = DT.Surface,
        title = { Text("Add Expense", color = DT.OnSurface, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Description *", color = DT.SubText) }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors, shape = RoundedCornerShape(10.dp))
                OutlinedTextField(amount, { amount = it }, label = { Text("Amount *", color = DT.SubText) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors, shape = RoundedCornerShape(10.dp))
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField("${categoryEmoji(category)} ${category.name}", {}, readOnly = true,
                        label = { Text("Category", color = DT.SubText) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(), colors = fieldColors, shape = RoundedCornerShape(10.dp))
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = DT.Surface2) {
                        ExpenseCategory.entries.forEach { cat ->
                            DropdownMenuItem(text = { Text("${categoryEmoji(cat)} ${cat.name}", color = DT.OnSurface) }, onClick = { category = cat; expanded = false })
                        }
                    }
                }
                if (category == ExpenseCategory.SUPPLIER)
                    OutlinedTextField(supplierName, { supplierName = it }, label = { Text("Supplier", color = DT.SubText) }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors, shape = RoundedCornerShape(10.dp))
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes", color = DT.SubText) }, maxLines = 2, modifier = Modifier.fillMaxWidth(), colors = fieldColors, shape = RoundedCornerShape(10.dp))
            }
        },
        confirmButton = {
            Button(onClick = { onSave(Expense(title = title.trim(), amount = amount.toDoubleOrNull() ?: 0.0, category = category, supplierName = supplierName, notes = notes)) },
                enabled = title.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0,
                colors = ButtonDefaults.buttonColors(containerColor = DT.Teal)) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = DT.SubText) } }
    )
}

private fun categoryEmoji(cat: ExpenseCategory) = when (cat) {
    ExpenseCategory.SUPPLIER -> "🏪"; ExpenseCategory.ELECTRICITY -> "⚡"; ExpenseCategory.WATER -> "💧"
    ExpenseCategory.RENT -> "🏠"; ExpenseCategory.SALARY -> "👤"; ExpenseCategory.TRANSPORT -> "🚗"
    ExpenseCategory.PACKAGING -> "📦"; ExpenseCategory.CLEANING -> "🧹"; ExpenseCategory.MAINTENANCE -> "🔧"
    ExpenseCategory.TAXES -> "📋"; ExpenseCategory.OTHER -> "💰"
}
