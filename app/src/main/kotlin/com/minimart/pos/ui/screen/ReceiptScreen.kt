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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.minimart.pos.data.entity.PaymentMethod
import com.minimart.pos.data.entity.SaleStatus
import com.minimart.pos.printer.ThermalPrinter
import com.minimart.pos.ui.theme.DT
import com.minimart.pos.ui.viewmodel.ReceiptViewModel
import com.minimart.pos.util.PdfReceiptGenerator
import com.minimart.pos.util.ReceiptData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ReceiptScreen(
    saleId: Long,
    onNewSale: () -> Unit,
    onDashboard: () -> Unit,
    printer: ThermalPrinter,
    storeName: String,
    currency: String,
    footerMessage: String,
    cashierName: String,
    vm: ReceiptViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val state   by vm.state.collectAsState()
    var statusMsg       by remember { mutableStateOf<String?>(null) }
    var isGeneratingPdf by remember { mutableStateOf(false) }
    var showRefundDialog by remember { mutableStateOf(false) }
    var showVoidDialog   by remember { mutableStateOf(false) }

    LaunchedEffect(saleId) { vm.loadSale(saleId) }

    LaunchedEffect(state.successMessage) {
        state.successMessage?.let { statusMsg = it; kotlinx.coroutines.delay(3000); vm.clearMessages() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { statusMsg = it; kotlinx.coroutines.delay(3000); vm.clearMessages() }
    }

    val sale  = state.saleWithItems?.sale
    val items = state.saleWithItems?.items ?: emptyList()
    val df    = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    // Bounce animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
        label = "checkScale"
    )

    val isVoided   = sale?.status == SaleStatus.VOIDED
    val isRefunded = sale?.status == SaleStatus.REFUNDED

    Box(modifier = Modifier.fillMaxSize().background(DT.Bg)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(top = 48.dp, bottom = 24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(color = DT.Teal, modifier = Modifier.size(48.dp))
            } else {
                // ── Status icon ───────────────────────────────────────────────
                Box(
                    modifier = Modifier.size(96.dp).scale(scale).clip(CircleShape)
                        .background(statusColor(sale?.status).copy(0.15f))
                        .border(3.dp, statusColor(sale?.status), CircleShape)
                        .semantics { contentDescription = "Sale status" },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(statusIcon(sale?.status), null,
                        tint = statusColor(sale?.status), modifier = Modifier.size(56.dp))
                }

                Text(statusLabel(sale?.status), color = statusColor(sale?.status),
                    fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)

                Text("Receipt ${sale?.receiptNumber ?: "#$saleId"}  •  ${sale?.createdAt?.let { df.format(Date(it)) } ?: ""}",
                    color = DT.SubText, style = MaterialTheme.typography.bodySmall)

                // Status badge for voided/refunded
                if (isVoided || isRefunded) {
                    Box(modifier = Modifier.clip(RoundedCornerShape(20.dp))
                        .background(DT.Red.copy(0.15f)).border(1.dp, DT.Red.copy(0.4f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Text(if (isVoided) "VOIDED" else "REFUNDED",
                            color = DT.Red, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                    }
                    sale?.notes?.let { if (it.isNotBlank()) Text("Reason: $it", color = DT.SubText, style = MaterialTheme.typography.labelSmall) }
                }

                // ── Items summary ─────────────────────────────────────────────
                if (items.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(DT.Surface).border(1.dp, DT.Border, RoundedCornerShape(16.dp)).padding(14.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Order Summary", color = DT.OnSurface, fontWeight = FontWeight.Bold)
                            HorizontalDivider(color = DT.Border)
                            items.forEach { item ->
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Text("${item.productName} ×${item.quantity}",
                                        color = DT.OnSurface, modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodySmall)
                                    Text("$currency ${String.format("%.2f", item.unitPrice * item.quantity)}",
                                        color = DT.SubText, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            HorizontalDivider(color = DT.Border)
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text("TOTAL", color = DT.OnSurface, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text("$currency ${String.format("%.2f", sale?.totalAmount ?: 0.0)}",
                                    color = DT.Teal, fontWeight = FontWeight.Bold)
                            }
                            sale?.paymentMethod?.let { pm ->
                                Text("Paid via ${pm.name}", color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                HorizontalDivider(color = DT.Border)

                // ── Share / PDF row ───────────────────────────────────────────
                Text("Share Receipt", color = DT.OnSurface, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth())

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ReceiptActionBtn(Modifier.weight(1f), Icons.Default.PictureAsPdf, "PDF", DT.Red, isGeneratingPdf) {
                        scope.launch {
                            isGeneratingPdf = true
                            try {
                                val data = ReceiptData(
                                    sale = sale ?: return@launch,
                                    items = items,
                                    productNames = items.associate { it.productId to it.productName },
                                    storeName = storeName, currency = currency,
                                    cashierName = cashierName, footerMessage = footerMessage
                                )
                                val file = withContext(Dispatchers.IO) { PdfReceiptGenerator.generate(context, data) }
                                statusMsg = "✓ PDF: ${file.name}"
                            } catch (e: Exception) { statusMsg = "PDF error: ${e.message}" }
                            isGeneratingPdf = false
                        }
                    }

                    ReceiptActionBtn(Modifier.weight(1f), Icons.Default.Share, "WhatsApp", Color(0xFF25D366)) {
                        scope.launch {
                            try {
                                val data = ReceiptData(
                                    sale = sale ?: return@launch,
                                    items = items,
                                    productNames = items.associate { it.productId to it.productName },
                                    storeName = storeName, currency = currency,
                                    cashierName = cashierName, footerMessage = footerMessage
                                )
                                val file = withContext(Dispatchers.IO) { PdfReceiptGenerator.generate(context, data) }
                                val uri = PdfReceiptGenerator.getShareUri(context, file)
                                PdfReceiptGenerator.shareViaWhatsApp(context, uri, storeName,
                                    "$currency ${String.format("%.2f", sale?.totalAmount ?: 0.0)}")
                            } catch (e: Exception) { statusMsg = "Share error: ${e.message}" }
                        }
                    }

                    ReceiptActionBtn(Modifier.weight(1f), Icons.Default.IosShare, "Share", DT.Teal) {
                        scope.launch {
                            try {
                                val data = ReceiptData(
                                    sale = sale ?: return@launch,
                                    items = items,
                                    productNames = items.associate { it.productId to it.productName },
                                    storeName = storeName, currency = currency,
                                    cashierName = cashierName, footerMessage = footerMessage
                                )
                                val file = withContext(Dispatchers.IO) { PdfReceiptGenerator.generate(context, data) }
                                val uri = PdfReceiptGenerator.getShareUri(context, file)
                                PdfReceiptGenerator.shareGeneric(context, uri)
                            } catch (e: Exception) { statusMsg = "Error: ${e.message}" }
                        }
                    }
                }

                // ── Refund / Void (only for completed sales) ──────────────────
                if (sale?.status == SaleStatus.COMPLETED) {
                    HorizontalDivider(color = DT.Border)
                    Text("Manage Sale", color = DT.OnSurface, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth())
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { showRefundDialog = true },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DT.Amber),
                            enabled = !state.isProcessing
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, null, tint = DT.Amber, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Refund", color = DT.Amber, fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = { showVoidDialog = true },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DT.Red),
                            enabled = !state.isProcessing
                        ) {
                            if (state.isProcessing) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DT.Red, strokeWidth = 2.dp)
                            else Icon(Icons.Default.Cancel, null, tint = DT.Red, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Void", color = DT.Red, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Text("Refund or void restores stock automatically.",
                        color = DT.SubText, style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.fillMaxWidth())
                }

                // Status feedback
                AnimatedVisibility(visible = statusMsg != null) {
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(DT.Surface).border(1.dp, DT.Border, RoundedCornerShape(10.dp)).padding(12.dp)) {
                        Icon(Icons.Default.Info, null, tint = DT.Teal, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(statusMsg ?: "", color = DT.SubText, style = MaterialTheme.typography.bodySmall)
                    }
                }

                HorizontalDivider(color = DT.Border)

                // ── Nav buttons ───────────────────────────────────────────────
                Button(onClick = onNewSale,
                    modifier = Modifier.fillMaxWidth().height(52.dp).semantics { contentDescription = "New sale" },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DT.Teal)) {
                    Icon(Icons.Default.QrCode, null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("New Sale", fontWeight = FontWeight.Bold, color = Color.White)
                }
                OutlinedButton(onClick = onDashboard,
                    modifier = Modifier.fillMaxWidth().height(52.dp).semantics { contentDescription = "Dashboard" },
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DT.Border)) {
                    Icon(Icons.Default.Home, null, tint = DT.SubText)
                    Spacer(Modifier.width(8.dp))
                    Text("Dashboard", color = DT.SubText)
                }
            }
        }
    }

    // Refund dialog
    if (showRefundDialog) {
        ReasonDialog("Refund Sale", "Stock will be restored.", DT.Amber,
            onDismiss = { showRefundDialog = false },
            onConfirm = { reason -> vm.refundSale(reason); showRefundDialog = false })
    }

    // Void dialog
    if (showVoidDialog) {
        ReasonDialog("Void Sale", "This cannot be undone. Stock will be restored.", DT.Red,
            onDismiss = { showVoidDialog = false },
            onConfirm = { reason -> vm.voidSale(reason); showVoidDialog = false })
    }
}

@Composable
private fun ReasonDialog(title: String, subtitle: String, color: Color,
    onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, containerColor = DT.Surface,
        title = { Text(title, color = color, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(subtitle, color = DT.SubText, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = reason, onValueChange = { reason = it },
                    label = { Text("Reason (optional)", color = DT.SubText) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = color, unfocusedBorderColor = DT.Border,
                        focusedTextColor = DT.OnSurface, unfocusedTextColor = DT.OnSurface,
                        cursorColor = color, focusedContainerColor = DT.Bg, unfocusedContainerColor = DT.Bg))
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(reason) },
                colors = ButtonDefaults.buttonColors(containerColor = color)) {
                Text("Confirm", color = if (color == DT.Red) Color.White else Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = DT.SubText) } })
}

private fun statusColor(status: SaleStatus?) = when (status) {
    SaleStatus.COMPLETED -> com.minimart.pos.ui.theme.DT.Green
    SaleStatus.VOIDED, SaleStatus.REFUNDED -> com.minimart.pos.ui.theme.DT.Red
    else -> com.minimart.pos.ui.theme.DT.Teal
}
private fun statusIcon(status: SaleStatus?) = when (status) {
    SaleStatus.COMPLETED -> Icons.Default.CheckCircle
    SaleStatus.VOIDED -> Icons.Default.Cancel
    SaleStatus.REFUNDED -> Icons.AutoMirrored.Filled.Undo
    else -> Icons.Default.CheckCircle
}
private fun statusLabel(status: SaleStatus?) = when (status) {
    SaleStatus.COMPLETED -> "Sale Complete!"
    SaleStatus.VOIDED -> "Sale Voided"
    SaleStatus.REFUNDED -> "Sale Refunded"
    else -> "Processing..."
}

@Composable
private fun ReceiptActionBtn(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String, color: Color, isLoading: Boolean = false, onClick: () -> Unit) {
    Box(modifier = modifier.clip(RoundedCornerShape(14.dp))
        .background(color.copy(0.12f)).border(1.dp, color.copy(0.3f), RoundedCornerShape(14.dp))
        .clickable(enabled = !isLoading, indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
        .padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = color, strokeWidth = 2.dp)
            else Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            Text(label, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}
