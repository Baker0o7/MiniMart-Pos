package com.minimart.pos.ui.screen

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.minimart.pos.data.entity.Shift
import com.minimart.pos.data.entity.ShiftStatus
import com.minimart.pos.ui.theme.Brand500
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
        topBar = {
            TopAppBar(
                title = { Text("Shift Management", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Brand500, titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Active shift card ──
            item {
                if (state.activeShift != null) {
                    ActiveShiftCard(
                        shift = state.activeShift!!,
                        onClockOut = { showClockOutDialog = true }
                    )
                } else {
                    // No active shift
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Brand500.copy(alpha = 0.12f))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.AccessTime, null, tint = Brand500, modifier = Modifier.size(48.dp))
                            Text("No Active Shift", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Clock in to start tracking your shift", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            Button(
                                onClick = { showClockInDialog = true },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Brand500)
                            ) {
                                Icon(Icons.Default.Login, null)
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
                items(state.allShifts.take(20)) { shift ->
                    ShiftHistoryRow(shift = shift, onClick = { showSummaryDialog = shift })
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
        ShiftSummaryDialog(shift = shift, onDismiss = { showSummaryDialog = null })
    }
}

// ─── Active Shift Card ────────────────────────────────────────────────────────

@Composable
private fun ActiveShiftCard(shift: Shift, onClockOut: () -> Unit) {
    val df = SimpleDateFormat("HH:mm", Locale.getDefault())
    val dfFull = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    val durationMs = System.currentTimeMillis() - shift.clockIn
    val hours = durationMs / 3600000
    val mins = (durationMs % 3600000) / 60000

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SuccessGreen.copy(alpha = 0.12f))
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
                Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Text("Started: ${dfFull.format(Date(shift.clockIn))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (shift.openingFloat > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Money, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Text("Opening float: KES ${String.format("%.2f", shift.openingFloat)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Button(
                onClick = onClockOut,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
            ) {
                Icon(Icons.Default.Logout, null)
                Spacer(Modifier.width(8.dp))
                Text("Clock Out & End Shift", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─── Shift History Row ────────────────────────────────────────────────────────

@Composable
private fun ShiftHistoryRow(shift: Shift, onClick: () -> Unit) {
    val df = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), onClick = onClick) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(42.dp).background(
                    if (shift.status == ShiftStatus.OPEN) SuccessGreen.copy(0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(10.dp)
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(if (shift.status == ShiftStatus.OPEN) Icons.Default.PlayArrow else Icons.Default.CheckCircle,
                    null, tint = if (shift.status == ShiftStatus.OPEN) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(shift.cashierName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text(df.format(Date(shift.clockIn)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("KES ${String.format("%.0f", shift.totalSales)}", fontWeight = FontWeight.Bold, color = Brand500)
                Text("${shift.totalTransactions} sales", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        title = { Text("Clock In", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Enter the opening cash float in the drawer:", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = openingFloat,
                    onValueChange = { openingFloat = it },
                    label = { Text("Opening Float (KES)") },
                    leadingIcon = { Icon(Icons.Default.Money, null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Leave 0 if no cash drawer is used.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(onClick = { onClockIn(openingFloat.toDoubleOrNull() ?: 0.0) },
                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
            ) { Icon(Icons.Default.Login, null, tint = Color.White); Spacer(Modifier.width(6.dp)); Text("Clock In") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ─── Clock Out Dialog ─────────────────────────────────────────────────────────

@Composable
private fun ClockOutDialog(onDismiss: () -> Unit, onClockOut: (Double, String) -> Unit) {
    var closingFloat by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("End Shift", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = closingFloat,
                    onValueChange = { closingFloat = it },
                    label = { Text("Closing Cash Float (KES)") },
                    leadingIcon = { Icon(Icons.Default.Money, null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Discrepancy reason") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onClockOut(closingFloat.toDoubleOrNull() ?: 0.0, notes) },
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
            ) { Icon(Icons.Default.Logout, null, tint = Color.White); Spacer(Modifier.width(6.dp)); Text("End Shift") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ─── Shift Summary Dialog ─────────────────────────────────────────────────────

@Composable
private fun ShiftSummaryDialog(shift: Shift, onDismiss: () -> Unit) {
    val df = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault())
    val durationMs = (shift.clockOut ?: System.currentTimeMillis()) - shift.clockIn
    val hours = durationMs / 3600000; val mins = (durationMs % 3600000) / 60000

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BarChart, null, tint = Brand500, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Shift Summary", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryRow("Cashier", shift.cashierName)
                SummaryRow("Clock In", df.format(Date(shift.clockIn)))
                shift.clockOut?.let { SummaryRow("Clock Out", df.format(Date(it))) }
                SummaryRow("Duration", "${hours}h ${mins}m")
                HorizontalDivider()
                SummaryRow("Cash Sales", "KES ${String.format("%.2f", shift.totalCashSales)}")
                SummaryRow("M-Pesa Sales", "KES ${String.format("%.2f", shift.totalMpesaSales)}")
                if (shift.totalCardSales > 0) SummaryRow("Card Sales", "KES ${String.format("%.2f", shift.totalCardSales)}")
                HorizontalDivider()
                SummaryRow("Total Sales", "KES ${String.format("%.2f", shift.totalSales)}", bold = true, color = Brand500)
                SummaryRow("Transactions", shift.totalTransactions.toString())
                if (shift.totalDiscounts > 0) SummaryRow("Discounts Given", "KES ${String.format("%.2f", shift.totalDiscounts)}", color = ErrorRed)
                HorizontalDivider()
                SummaryRow("Opening Float", "KES ${String.format("%.2f", shift.openingFloat)}")
                shift.closingFloat?.let { cf ->
                    SummaryRow("Closing Float", "KES ${String.format("%.2f", cf)}")
                    SummaryRow("Expected Cash", "KES ${String.format("%.2f", shift.expectedCash)}")
                    val disc = shift.cashDiscrepancy
                    SummaryRow(
                        "Discrepancy",
                        "${if (disc >= 0) "+" else ""}KES ${String.format("%.2f", disc)}",
                        bold = true,
                        color = when {
                            disc > 10  -> SuccessGreen
                            disc < -10 -> ErrorRed
                            else       -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
                if (shift.notes.isNotBlank()) {
                    HorizontalDivider()
                    Text("Notes: ${shift.notes}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun SummaryRow(label: String, value: String, bold: Boolean = false, color: Color = MaterialTheme.colorScheme.onSurface) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, color = color)
    }
}
