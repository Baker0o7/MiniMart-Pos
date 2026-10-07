package com.minimart.pos.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.minimart.pos.data.entity.Expense
import com.minimart.pos.data.entity.ExpenseCategory
import com.minimart.pos.ui.theme.DT
import com.minimart.pos.ui.viewmodel.ExpenseViewModel
import com.minimart.pos.ui.viewmodel.ReportPeriod
import com.minimart.pos.util.toEmoji
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** "KES 12,450.50" — thousands separators make bigger totals readable at a glance. */
private fun expenseAmount(currency: String, value: Double): String =
    "$currency ${String.format(Locale.US, "%,.2f", value)}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseScreen(onBack: () -> Unit, vm: ExpenseViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsState()
    val period by vm.period.collectAsState()
    val customRange by vm.customRange.collectAsState()
    var showRangePicker by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }

    if (showRangePicker) {
        DateRangeDialog(onDismiss = { showRangePicker = false },
            onConfirm = { s, e -> vm.setCustomRange(s, e); showRangePicker = false })
    }

    LaunchedEffect(state.successMessage, state.error) {
        if (state.successMessage != null || state.error != null) {
            kotlinx.coroutines.delay(3000)
            vm.clearMessages()
        }
    }

    val customLabel = customRange?.let { formatRangeLabel(it.first, it.second) }
    val periodLabel = if (period == ReportPeriod.CUSTOM) customLabel ?: period.label else period.label

    Box(modifier = Modifier.fillMaxSize().background(DT.Bg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            GradientHeader(
                title = "Expenses & P&L",
                subtitle = periodLabel,
                onBack = onBack,
                actions = { HeaderPillButton("Add", Icons.Default.Add) { showAddDialog = true } }
            )

            FeedbackBanner(
                message = state.error ?: state.successMessage,
                isError = state.error != null,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp)
            )

            // ── Period chips ──────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReportPeriod.entries.forEach { p ->
                    ExpenseChip(
                        label = if (p == ReportPeriod.CUSTOM) customLabel ?: p.label else p.label,
                        selected = period == p
                    ) { if (p == ReportPeriod.CUSTOM) showRangePicker = true else vm.setPeriod(p) }
                }
            }

            // ── Screen tabs ───────────────────────────────────────────────────
            TabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent, contentColor = DT.Teal) {
                listOf("P&L Report", "Expenses (${state.expenses.size})").forEachIndexed { index, label ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }) {
                        Text(label,
                            color = if (selectedTab == index) DT.Teal else DT.SubText,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(vertical = 12.dp))
                    }
                }
            }

            when (selectedTab) {
                0 -> PLTab(state.totalRevenue, state.totalExpenses, state.netProfit, state.profitMargin,
                    state.expensesByCategory, state.currency)
                else -> ExpenseListTab(state.expenses, state.currency,
                    onAdd = { showAddDialog = true }, onDelete = { vm.deleteExpense(it) })
            }
        }
    }

    if (showAddDialog) {
        AddExpenseDialog(
            currency = state.currency,
            onDismiss = { showAddDialog = false },
            onSave = { vm.addExpense(it); showAddDialog = false; selectedTab = 1 }
        )
    }
}

/** Pill used for the period selector and inside the add dialog. */
@Composable
private fun ExpenseChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier.height(38.dp).clip(shape)
            .background(if (selected) DT.Teal else DT.Surface)
            .border(1.dp, if (selected) DT.Teal else DT.Border, shape)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label,
            color = if (selected) Color.White else DT.SubText,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp, maxLines = 1)
    }
}

/** Horizontal bar showing this figure relative to the larger of revenue/expenses (real data, not a placeholder chart). */
@Composable
private fun ShareBar(fraction: Float, color: Color) {
    // fillMaxWidth() throws on NaN, so callers pass an already-clamped fraction.
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(DT.Border)) {
        Box(Modifier.fillMaxWidth(fraction).height(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
    }
}

@Composable
private fun PLTab(revenue: Double, expenses: Double, netProfit: Double,
    margin: Double, byCategory: Map<ExpenseCategory, Double>, currency: String) {
    val sortedCategories = remember(byCategory) { byCategory.entries.sortedByDescending { it.value } }
    val scale = maxOf(revenue, expenses, 0.01)
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Revenue + Expenses side by side
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF0B2210), Color(0xFF060E08))))
                    .border(1.dp, DT.Green.copy(0.25f), RoundedCornerShape(18.dp)).padding(14.dp)) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = DT.Green, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Revenue", color = DT.SubText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(expenseAmount(currency, revenue), color = DT.Green,
                            fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(10.dp))
                        ShareBar((revenue / scale).toFloat().coerceIn(0f, 1f), DT.Green)
                    }
                }
                Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF220B0B), Color(0xFF0E0606))))
                    .border(1.dp, DT.Red.copy(0.25f), RoundedCornerShape(18.dp)).padding(14.dp)) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.TrendingDown, null, tint = DT.Red, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Expenses", color = DT.SubText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(expenseAmount(currency, expenses), color = DT.Red,
                            fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(10.dp))
                        ShareBar((expenses / scale).toFloat().coerceIn(0f, 1f), DT.Red)
                    }
                }
            }
        }
        // Net profit
        item {
            val profitColor = if (netProfit >= 0) DT.Green else DT.Red
            val profitBg = if (netProfit >= 0) Color(0xFF0B2210) else Color(0xFF220B0B)
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(Brush.horizontalGradient(listOf(profitBg, DT.Surface)))
                .border(1.dp, profitColor.copy(0.25f), RoundedCornerShape(18.dp)).padding(18.dp)) {
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Net Profit", color = DT.SubText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text("${if (netProfit >= 0) "+" else ""}${expenseAmount(currency, netProfit)}",
                            color = profitColor, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Margin", color = DT.SubText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text("${String.format(Locale.US, "%.1f", margin)}%",
                            color = profitColor, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                    }
                }
            }
        }
        if (revenue <= 0.0 && expenses <= 0.0) {
            item {
                Text("No sales or expenses in this period yet.",
                    color = DT.SubText, fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        if (sortedCategories.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(Icons.Default.BarChart, null, tint = DT.Red, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("By Category", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
            items(sortedCategories, key = { it.key.name }) { (cat, amount) ->
                // Share of total spend (0/0 guarded: NaN would crash fillMaxWidth).
                val share = if (expenses > 0) (amount / expenses).toFloat().coerceIn(0f, 1f) else 0f
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(DT.Surface).border(1.dp, DT.Border, RoundedCornerShape(14.dp)).padding(14.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("${cat.toEmoji()} ${prettyEnumName(cat.name)}", color = Color.White,
                                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(expenseAmount(currency, amount), color = DT.Red, fontWeight = FontWeight.Bold)
                                Text("${String.format(Locale.US, "%.0f", share * 100)}% of spend",
                                    color = DT.SubText, fontSize = 11.sp)
                            }
                        }
                        Box(modifier = Modifier.fillMaxWidth().height(4.dp)
                            .clip(RoundedCornerShape(2.dp)).background(DT.Border)) {
                            Box(modifier = Modifier.fillMaxWidth(share)
                                .height(4.dp).clip(RoundedCornerShape(2.dp)).background(DT.Red.copy(0.7f)))
                        }
                    }
                }
            }
        }
    }
}

// ─── Expense list ────────────────────────────────────────────────────────────────────────

private data class DayGroup(val dayStart: Long, val label: String, val total: Double, val items: List<Expense>)

private fun dayStartOf(ms: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ms
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** Newest day first, each with its own total — "Today", "Yesterday", then "Mon, 5 Oct". */
private fun groupByDay(expenses: List<Expense>): List<DayGroup> {
    val today = dayStartOf(System.currentTimeMillis())
    val yesterday = dayStartOf(Calendar.getInstance().apply {
        timeInMillis = today; add(Calendar.DAY_OF_YEAR, -1)
    }.timeInMillis)
    val dateFormat = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
    return expenses.sortedByDescending { it.createdAt }
        .groupBy { dayStartOf(it.createdAt) }
        .map { (dayStart, list) ->
            DayGroup(
                dayStart = dayStart,
                label = when (dayStart) {
                    today -> "Today"
                    yesterday -> "Yesterday"
                    else -> dateFormat.format(Date(dayStart))
                },
                total = list.sumOf { it.amount },
                items = list
            )
        }
}

@Composable
private fun ExpenseListTab(
    expenses: List<Expense>,
    currency: String,
    onAdd: () -> Unit,
    onDelete: (Expense) -> Unit
) {
    var pendingDelete by remember { mutableStateOf<Expense?>(null) }

    if (expenses.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(bottom = 48.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Receipt, null, modifier = Modifier.size(56.dp), tint = DT.SubText.copy(0.3f))
                Text("No expenses in this period", color = DT.SubText, fontWeight = FontWeight.SemiBold)
                Text("Log rent, stock purchases, power and more.", color = DT.SubText.copy(0.7f), fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Button(onClick = onAdd, shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DT.Teal, contentColor = Color.White)) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add expense", fontWeight = FontWeight.Bold)
                }
            }
        }
    } else {
        val groups = remember(expenses) { groupByDay(expenses) }
        val total = remember(expenses) { expenses.sumOf { it.amount } }
        val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "summary") {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(DT.Red.copy(0.10f))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${expenses.size} ${if (expenses.size == 1) "expense" else "expenses"}",
                        color = DT.SubText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(expenseAmount(currency, total), color = DT.Red,
                        fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }
            }
            groups.forEach { group ->
                item(key = "day-${group.dayStart}") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(group.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(expenseAmount(currency, group.total), color = DT.SubText, fontSize = 12.sp)
                    }
                }
                items(group.items, key = { "expense-${it.id}" }) { expense ->
                    ExpenseRow(expense, currency, timeFormat.format(Date(expense.createdAt))) { pendingDelete = expense }
                }
            }
        }
    }

    pendingDelete?.let { expense ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = DT.Surface,
            title = { Text("Delete expense?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text("\"${expense.title}\" (${expenseAmount(currency, expense.amount)}) will be permanently removed.",
                    color = DT.SubText)
            },
            confirmButton = {
                Button(onClick = { onDelete(expense); pendingDelete = null },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DT.Red, contentColor = Color.White)) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingDelete = null }, shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, DT.Border)) { Text("Cancel", color = DT.SubText) }
            }
        )
    }
}

@Composable
private fun ExpenseRow(expense: Expense, currency: String, time: String, onDelete: () -> Unit) {
    val detail = listOfNotNull(
        prettyEnumName(expense.category.name),
        expense.supplierName.takeIf { it.isNotBlank() },
        time
    ).joinToString(" • ")
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(DT.Surface).border(1.dp, DT.Border, RoundedCornerShape(14.dp))
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(DT.Surface2),
            contentAlignment = Alignment.Center) {
            Text(expense.category.toEmoji(), fontSize = 20.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(expense.title, color = DT.OnSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, color = DT.SubText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (expense.notes.isNotBlank()) {
                Text(expense.notes, color = DT.SubText.copy(0.75f), fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(expenseAmount(currency, expense.amount), color = DT.Red, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.Delete, "Delete ${expense.title}", tint = DT.Red.copy(0.8f), modifier = Modifier.size(18.dp))
        }
    }
}

// ─── Add expense ─────────────────────────────────────────────────────────────────────────

@Composable
private fun AddExpenseDialog(currency: String, onDismiss: () -> Unit, onSave: (Expense) -> Unit) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(ExpenseCategory.SUPPLIER) }
    var supplierName by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var yesterday by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val titleFocus = remember { FocusRequester() }

    val amountValue = amount.toDoubleOrNull() ?: 0.0
    val canSave = title.isNotBlank() && amountValue > 0

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border,
        focusedTextColor = DT.OnSurface, unfocusedTextColor = DT.OnSurface, cursorColor = DT.Teal,
        focusedContainerColor = DT.Bg, unfocusedContainerColor = DT.Bg
    )
    val fieldShape = RoundedCornerShape(12.dp)

    fun save() {
        if (!canSave) return
        val createdAt = if (yesterday) {
            Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
        } else System.currentTimeMillis()
        onSave(Expense(
            title = title.trim(),
            amount = amountValue,
            category = category,
            supplierName = if (category == ExpenseCategory.SUPPLIER) supplierName.trim() else "",
            notes = notes.trim(),
            createdAt = createdAt
        ))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DT.Surface,
        title = { Text("Add expense", color = DT.OnSurface, fontWeight = FontWeight.Bold) },
        text = {
            // Requested from inside the dialog's own content so the field is attached first;
            // asking from the parent can run before the dialog window has composed it.
            LaunchedEffect(Unit) { runCatching { titleFocus.requestFocus() } }
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("Description *", color = DT.SubText) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().focusRequester(titleFocus),
                    colors = fieldColors, shape = fieldShape
                )
                OutlinedTextField(
                    value = amount, onValueChange = { amount = sanitizeMoneyInput(it) },
                    label = { Text("Amount ($currency) *", color = DT.SubText) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors, shape = fieldShape
                )

                Text("Category", color = DT.SubText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExpenseCategory.entries.forEach { cat ->
                        ExpenseChip("${cat.toEmoji()} ${prettyEnumName(cat.name)}", cat == category) { category = cat }
                    }
                }

                if (category == ExpenseCategory.SUPPLIER) {
                    OutlinedTextField(
                        value = supplierName, onValueChange = { supplierName = it },
                        label = { Text("Supplier", color = DT.SubText) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors, shape = fieldShape
                    )
                }

                Text("Date", color = DT.SubText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpenseChip("Today", !yesterday) { yesterday = false }
                    ExpenseChip("Yesterday", yesterday) { yesterday = true }
                }

                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text("Notes (optional)", color = DT.SubText) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors, shape = fieldShape
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { save() },
                enabled = canSave,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DT.Green, contentColor = Color.White,
                    disabledContainerColor = DT.Green.copy(0.45f), disabledContentColor = Color.White.copy(0.7f)
                )
            ) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Save", fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, DT.Border)) { Text("Cancel", color = DT.SubText) }
        }
    )
}
