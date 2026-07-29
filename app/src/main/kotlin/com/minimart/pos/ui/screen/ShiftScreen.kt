package com.minimart.pos.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.*
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
import com.minimart.pos.data.entity.Shift
import com.minimart.pos.data.entity.ShiftStatus
import com.minimart.pos.ui.theme.Brand500
import com.minimart.pos.ui.theme.DT
import com.minimart.pos.ui.theme.ErrorRed
import com.minimart.pos.ui.theme.SuccessGreen
import com.minimart.pos.ui.viewmodel.ShiftViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftScreen(
    onBack: () -> Unit,
    vm: ShiftViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsState()
    var showClockInDialog by remember { mutableStateOf(false) }
    var showClockOutDialog by remember { mutableStateOf(false) }
    var showSummaryDialog by remember { mutableStateOf<Shift?>(null) }

    LaunchedEffect(state.successMessage, state.error) {
        if (state.successMessage != null || state.error != null) {
            kotlinx.coroutines.delay(3000); vm.clearMessages()
        }
    }
    LaunchedEffect(state.lastClosedShift) {
        if (state.lastClosedShift != null) showSummaryDialog = state.lastClosedShift
    }

    Scaffold(
        containerColor = com.minimart.pos.ui.theme.DT.Bg,
        topBar = {
            TopAppBar(
                title = { Text("Shift Management", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = com.minimart.pos.ui.theme.DT.Teal)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(com.minimart.pos.ui.theme.DT.Bg).padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Box(Modifier.fillMaxWidth()
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(DT.Teal, androidx.compose.ui.graphics.Color(0xFF004D40))))
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Shift Management", color = androidx.compose.ui.graphics.Color.White,
                                fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                            Text("Clock in & track cash", color = androidx.compose.ui.graphics.Color.White.copy(0.7f), fontSize = 12.sp)
                        }
                        Icon(Icons.Default.Schedule, null,
                            tint = androidx.compose.ui.graphics.Color.White.copy(0.7f),
                            modifier = Modifier.size(28.dp))
                    }
                }
            }

            // ── Active shift card ──
            item {
                state.activeShift?.let { shift ->
                    ActiveShiftCard(
                        shift = shift,
                        currency = state.currency,
                        onClockOut = { showClockOutDialog = true }
                    )
                } ?: run {
                    // No active shift
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = com.minimart.pos.ui.theme.DT.Surface2)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.AccessTime, null, tint = Brand500, modifier = Modifier.size(48.dp))
                            Text("No Active Shift", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Clock in to start tracking your shift", color = com.minimart.pos.ui.theme.DT.SubText, style = MaterialTheme.typography.bodySmall)
                            Button(
                                onClick = { showClockInDialog = true },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Brand500)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Login, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Clock In", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Feedback
            state.successMessage?.let { msg ->
                item {
                    Row(modifier = Modifier.fillMaxWidth().background(SuccessGreen, RoundedCornerShape(10.dp)).padding(12.dp)) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(msg, color = Color.White, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            state.error?.let { err ->
                item {
                    Row(modifier = Modifier.fillMaxWidth().background(ErrorRed, RoundedCornerShape(10.dp)).padding(12.dp)) {
                        Icon(Icons.Default.Error, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(err, color = Color.White, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // ── Shift history ──
            if (state.allShifts.isNotEmpty()) {
                item { Text("Shift History", fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
                items(state.allShifts.take(20), key = { it.id }) { shift ->
                    ShiftHistoryRow(shift = shift, currency = state.currency, onClick = { showSummaryDialog = shift })
                }
            }
        }
    }

    if (showClockInDialog) {
        ClockInDialog(
            onDismiss = { showClockInDialog = false },
            onClockIn = { float -> vm.clockIn(float); showClockInDialog = false }
        )
    }

    if (showClockOutDialog) {
        ClockOutDialog(
            onDismiss = { showClockOutDialog = false },
            onClockOut = { float, notes -> vm.clockOut(float, notes); showClockOutDialog = false }
        )
    }

    showSummaryDialog?.let { shift ->
        ShiftSummaryDialog(shift = shift, currency = state.currency, onDismiss = { showSummaryDialog = null })
    }
}

// ─── Active Shift Card ────────────────────────────────────────────────────────

@Composable
private fun ActiveShiftCard(shift: Shift, currency: String, onClockOut: () -> Unit) {
    val df = SimpleDateFormat("HH:mm", Locale.getDefault())
    val dfFull = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    val durationMs = System.currentTimeMillis() - shift.clockIn
    val hours = durationMs / 3600000
    val mins = (durationMs % 3600000) / 60000

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = com.minimart.pos.ui.theme.DT.Surface2)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).background(SuccessGreen, androidx.compose.foundation.shape.CircleShape))
                Spacer(Modifier.width(8.dp))
                Text("Active Shift", color = SuccessGreen, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("${hours}h ${mins}m", color = SuccessGreen, style = MaterialTheme.typography.labelMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Person, null, tint = Brand500, modifier = Modifier.size(18.dp))
                Text(shift.cashierName, fontWeight = FontWeight.SemiBold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Schedule, null, tint = com.minimart.pos.ui.theme.DT.SubText, modifier = Modifier.size(18.dp))
                Text("Started: ${dfFull.format(Date(shift.clockIn))}", style = MaterialTheme.typography.bodySmall, color = com.minimart.pos.ui.theme.DT.SubText)
            }
            if (shift.openingFloat > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Money, null, tint = com.minimart.pos.ui.theme.DT.SubText, modifier = Modifier.size(18.dp))
                    Text("Opening float: $currency ${String.format("%.2f", shift.openingFloat)}", style = MaterialTheme.typography.bodySmall, color = com.minimart.pos.ui.theme.DT.SubText)
                }
            }
            Button(
                onClick = onClockOut,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, null)
                Spacer(Modifier.width(8.dp))
                Text("Clock Out & End Shift", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─── Shift History Row ────────────────────────────────────────────────────────

@Composable
private fun ShiftHistoryRow(shift: Shift, currency: String, onClick: () -> Unit) {
    val DT = com.minimart.pos.ui.theme.DT
    val df = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = DT.Surface)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).background(
                    if (shift.status == ShiftStatus.OPEN) SuccessGreen.copy(0.15f) else DT.Surface2,
                    RoundedCornerShape(12.dp)
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (shift.status == ShiftStatus.OPEN) Icons.Default.PlayArrow else Icons.Default.CheckCircle,
                    null,
                    tint = if (shift.status == ShiftStatus.OPEN) SuccessGreen else DT.Teal,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(shift.cashierName, fontWeight = FontWeight.SemiBold, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                Text(df.format(Date(shift.clockIn)), style = MaterialTheme.typography.labelSmall, color = com.minimart.pos.ui.theme.DT.SubText)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("$currency ${String.format("%.0f", shift.totalSales)}", fontWeight = FontWeight.Bold, color = com.minimart.pos.ui.theme.DT.Teal)
                Text("${shift.totalTransactions} sales", style = MaterialTheme.typography.labelSmall, color = com.minimart.pos.ui.theme.DT.SubText)
            }
        }
    }
}

// ─── Clock In Dialog ──────────────────────────────────────────────────────────

@Composable
private fun ClockInDialog(onDismiss: () -> Unit, onClockIn: (Double) -> Unit) {
    var openingFloat by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = com.minimart.pos.ui.theme.DT.Bg,
        title = { Text("Clock In", fontWeight = FontWeight.Bold, color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Enter the opening cash float in the drawer:",
                    style = MaterialTheme.typography.bodyMedium, color = Color.White)
                OutlinedTextField(
                    value = openingFloat,
                    onValueChange = { openingFloat = it },
                    label = { Text("Opening Float (KES)", color = com.minimart.pos.ui.theme.DT.SubText) },
                    leadingIcon = { Icon(Icons.Default.Money, null, tint = com.minimart.pos.ui.theme.DT.SubText) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = com.minimart.pos.ui.theme.DT.Teal,
                        unfocusedBorderColor = com.minimart.pos.ui.theme.DT.Border,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = com.minimart.pos.ui.theme.DT.Teal,
                        focusedContainerColor = com.minimart.pos.ui.theme.DT.Bg,
                        unfocusedContainerColor = com.minimart.pos.ui.theme.DT.Bg
                    )
                )
                Text("Leave 0 if no cash drawer is used.",
                    style = MaterialTheme.typography.labelSmall,
                    color = com.minimart.pos.ui.theme.DT.SubText)
            }
        },
        confirmButton = {
            Button(onClick = { onClockIn((openingFloat.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)) },
                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Login, null, tint = Color.White)
                Spacer(Modifier.width(6.dp))
                Text("Clock In", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = com.minimart.pos.ui.theme.DT.SubText)
            }
        }
    )
}

// ─── Clock Out Dialog ─────────────────────────────────────────────────────────

@Composable
private fun ClockOutDialog(onDismiss: () -> Unit, onClockOut: (Double, String) -> Unit) {
    val DT = com.minimart.pos.ui.theme.DT
    var closingFloat by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DT.Surface,
        title = { Text("End Shift", fontWeight = FontWeight.Bold, color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = closingFloat, onValueChange = { closingFloat = it },
                    label = { Text("Closing Cash Float (KES)", color = DT.SubText) },
                    leadingIcon = { Icon(Icons.Default.Money, null, tint = DT.SubText) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border,
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = DT.Teal,
                        focusedContainerColor = DT.Bg, unfocusedContainerColor = DT.Bg))
                OutlinedTextField(value = notes, onValueChange = { notes = it },
                    label = { Text("Notes / Discrepancy reason", color = DT.SubText) },
                    maxLines = 3, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border,
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = DT.Teal,
                        focusedContainerColor = DT.Bg, unfocusedContainerColor = DT.Bg))
            }
        },
        confirmButton = {
            Button(onClick = { onClockOut((closingFloat.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0), notes) },
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
            ) { Icon(Icons.AutoMirrored.Filled.Logout, null, tint = Color.White, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("End Shift", color = Color.White, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, DT.Border)) { Text("Cancel", color = DT.SubText) } }
    )
}

// ─── Shift Summary Dialog ─────────────────────────────────────────────────────

@Composable
private fun ShiftSummaryDialog(shift: Shift, currency: String, onDismiss: () -> Unit) {
    val DT = com.minimart.pos.ui.theme.DT
    val df = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault())
    val durationMs = (shift.clockOut ?: System.currentTimeMillis()) - shift.clockIn
    val hours = durationMs / 3600000; val mins = (durationMs % 3600000) / 60000

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DT.Surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BarChart, null, tint = DT.Teal, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Shift Summary", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryRow("Cashier", shift.cashierName)
                SummaryRow("Clock In", df.format(Date(shift.clockIn)))
                shift.clockOut?.let { SummaryRow("Clock Out", df.format(Date(it))) }
                SummaryRow("Duration", "${hours}h ${mins}m")
                HorizontalDivider(color = DT.Border)
                SummaryRow("Cash Sales", "$currency ${String.format("%.2f", shift.totalCashSales)}")
                SummaryRow("M-Pesa Sales", "$currency ${String.format("%.2f", shift.totalMpesaSales)}")
                if (shift.totalCardSales > 0) SummaryRow("Card Sales", "$currency ${String.format("%.2f", shift.totalCardSales)}")
                HorizontalDivider(color = DT.Border)
                SummaryRow("Total Sales", "$currency ${String.format("%.2f", shift.totalSales)}", bold = true, color = DT.Teal)
                SummaryRow("Transactions", shift.totalTransactions.toString())
                if (shift.totalDiscounts > 0) SummaryRow("Discounts Given", "$currency ${String.format("%.2f", shift.totalDiscounts)}", color = ErrorRed)
                HorizontalDivider(color = DT.Border)
                SummaryRow("Opening Float", "$currency ${String.format("%.2f", shift.openingFloat)}")
                shift.closingFloat?.let { cf ->
                    SummaryRow("Closing Float", "$currency ${String.format("%.2f", cf)}")
                    SummaryRow("Expected Cash", "$currency ${String.format("%.2f", shift.expectedCash)}")
                    val disc = shift.cashDiscrepancy
                    SummaryRow(
                        "Discrepancy",
                        "${if (disc >= 0) "+" else ""}$currency ${String.format("%.2f", disc)}",
                        bold = true,
                        color = when { disc > 10 -> SuccessGreen; disc < -10 -> ErrorRed; else -> Color.White }
                    )
                }
                if (shift.notes.isNotBlank()) {
                    HorizontalDivider(color = DT.Border)
                    Text("Notes: ${shift.notes}", style = MaterialTheme.typography.bodySmall, color = DT.SubText)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = DT.SubText) } }
    )
}

@Composable
private fun SummaryRow(label: String, value: String, bold: Boolean = false, color: Color = Color.White) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = com.minimart.pos.ui.theme.DT.SubText)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, color = color)
    }
}
