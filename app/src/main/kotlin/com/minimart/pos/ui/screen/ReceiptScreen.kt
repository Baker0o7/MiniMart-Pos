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
    canManageSales: Boolean = false,
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

    fun receiptData(): ReceiptData? {
        val sl = sale ?: return null
        return ReceiptData(
            sale = sl, items = items,
            productNames = items.associate { it.productId to it.productName },
            storeName = storeName, currency = currency,
            cashierName = state.cashierName ?: cashierName, footerMessage = footerMessage
        )
    }
    val money = { v: Double -> "$currency ${String.format(Locale.US, "%,.2f", v)}" }

    Column(modifier = Modifier.fillMaxSize().background(DT.Bg).statusBarsPadding()) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(top = 14.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state.isLoading) {
                Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DT.Teal, modifier = Modifier.size(40.dp))
                }
            } else {
                val sc = statusColor(sale?.status)
                // ── Compact status header ─────────────────────────────────────
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(52.dp).scale(scale).clip(CircleShape)
                            .background(sc.copy(0.15f)).border(2.dp, sc, CircleShape)
                            .semantics { contentDescription = "Sale status" },
                        contentAlignment = Alignment.Center
                    ) { Icon(statusIcon(sale?.status), null, tint = sc, modifier = Modifier.size(30.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(statusLabel(sale?.status), color = sc, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        Text("#${sale?.receiptNumber ?: saleId}", color = DT.SubText, fontSize = 12.sp,
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    if (isVoided || isRefunded) {
                        Text(if (isVoided) "VOIDED" else "REFUNDED", color = DT.Red, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp,
                            modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(DT.Red.copy(0.15f))
                                .border(1.dp, DT.Red.copy(0.4f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                }
                if ((isVoided || isRefunded) && !sale?.notes.isNullOrBlank())
                    Text("Reason: ${sale?.notes}", color = DT.SubText, fontSize = 11.sp)

                // ── Receipt card ─────────────────────────────────────────────
                if (items.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(DT.Surface, DT.Surface2)))
                            .border(1.dp, DT.Border, RoundedCornerShape(18.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(storeName, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp,
                                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Text(
                                    "${(state.cashierName ?: cashierName).ifBlank { "Admin" }} · " +
                                        (sale?.createdAt?.let { SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(Date(it)) } ?: ""),
                                    color = DT.SubText, fontSize = 11.sp, maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                            sale?.paymentMethod?.let { pm ->
                                Text(pm.name, color = DT.Teal, fontWeight = FontWeight.Bold, fontSize = 11.sp,
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(DT.Teal.copy(0.15f))
                                        .border(1.dp, DT.Teal.copy(0.3f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = DT.Border)
                        Spacer(Modifier.height(4.dp))
                        items.forEach { item ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.productName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    // For weighed items unitPrice holds the calculated line total (see
                                    // CartViewModel.addWeighedItem), so price/kg is derived by dividing back.
                                    Text(
                                        (if (item.weightKg > 0) "${String.format("%.3f", item.weightKg)} kg @ ${String.format("%.2f", item.unitPrice / item.weightKg)}/kg"
                                         else "${item.quantity} × ${String.format("%.2f", item.unitPrice)}"),
                                        color = DT.SubText, fontSize = 11.sp)
                                }
                                Text(money(item.lineTotal), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        HorizontalDivider(color = DT.Teal.copy(0.3f), thickness = 1.dp)
                        Spacer(Modifier.height(8.dp))
                        sale?.discountAmount?.takeIf { it > 0 }?.let { d ->
                            Row(Modifier.fillMaxWidth()) {
                                Text("Discount", color = DT.Amber, modifier = Modifier.weight(1f), fontSize = 12.sp)
                                Text("- ${money(d)}", color = DT.Amber, fontSize = 12.sp)
                            }
                            Spacer(Modifier.height(3.dp))
                        }
                        sale?.taxAmount?.takeIf { it > 0 }?.let { t ->
                            Row(Modifier.fillMaxWidth()) {
                                Text("VAT (incl.)", color = DT.SubText, modifier = Modifier.weight(1f), fontSize = 11.sp)
                                Text(money(t), color = DT.SubText, fontSize = 11.sp)
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("TOTAL", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp,
                                modifier = Modifier.weight(1f), letterSpacing = 1.sp)
                            Text(money(sale?.totalAmount ?: 0.0), color = DT.Teal, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                        }
                        sale?.changeGiven?.takeIf { it > 0 }?.let { c ->
                            Spacer(Modifier.height(3.dp))
                            Row(Modifier.fillMaxWidth()) {
                                Text("Change", color = DT.Green, modifier = Modifier.weight(1f), fontSize = 12.sp)
                                Text(money(c), color = DT.Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (footerMessage.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(footerMessage, color = DT.SubText, fontSize = 11.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }

                // ── Share row ─────────────────────────────────────────────────
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReceiptActionBtn(Modifier.weight(1f), Icons.Default.PictureAsPdf, "PDF", DT.Red, isGeneratingPdf) {
                        scope.launch {
                            isGeneratingPdf = true
                            try {
                                val data = receiptData() ?: return@launch
                                val file = withContext(Dispatchers.IO) { PdfReceiptGenerator.generate(context, data) }
                                statusMsg = "✓ PDF: ${file.name}"
                            } catch (e: Exception) { statusMsg = "PDF error: ${e.message}" }
                            finally { isGeneratingPdf = false }
                        }
                    }
                    ReceiptActionBtn(Modifier.weight(1f), Icons.Default.Share, "WhatsApp", Color(0xFF25D366)) {
                        scope.launch {
                            try {
                                val data = receiptData() ?: return@launch
                                val file = withContext(Dispatchers.IO) { PdfReceiptGenerator.generate(context, data) }
                                val uri = PdfReceiptGenerator.getShareUri(context, file)
                                PdfReceiptGenerator.shareViaWhatsApp(context, uri, storeName, money(sale?.totalAmount ?: 0.0))
                            } catch (e: Exception) { statusMsg = "Share error: ${e.message}" }
                        }
                    }
                    ReceiptActionBtn(Modifier.weight(1f), Icons.Default.IosShare, "Share", DT.Teal) {
                        scope.launch {
                            try {
                                val data = receiptData() ?: return@launch
                                val file = withContext(Dispatchers.IO) { PdfReceiptGenerator.generate(context, data) }
                                PdfReceiptGenerator.shareGeneric(context, PdfReceiptGenerator.getShareUri(context, file))
                            } catch (e: Exception) { statusMsg = "Error: ${e.message}" }
                        }
                    }
                }

                // ── Refund / Void (only for completed sales) ──────────────────
                if (canManageSales && sale?.status == SaleStatus.COMPLETED) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showRefundDialog = true },
                            modifier = Modifier.weight(1f).height(42.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DT.Amber.copy(0.7f)),
                            enabled = !state.isProcessing
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, null, tint = DT.Amber, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Refund", color = DT.Amber, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = { showVoidDialog = true },
                            modifier = Modifier.weight(1f).height(42.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DT.Red.copy(0.7f)),
                            enabled = !state.isProcessing
                        ) {
                            if (state.isProcessing) CircularProgressIndicator(modifier = Modifier.size(14.dp), color = DT.Red, strokeWidth = 2.dp)
                            else Icon(Icons.Default.Cancel, null, tint = DT.Red, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Void", color = DT.Red, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }

                FeedbackBanner(statusMsg, isError = statusMsg?.let { it.contains("error", true) || it.startsWith("Error") } == true)
            }
        }

        // ── Pinned actions: always reachable without scrolling ────────────────
        Row(
            modifier = Modifier.fillMaxWidth().background(DT.Surface)
                .padding(horizontal = 16.dp, vertical = 10.dp).navigationBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(onClick = onDashboard,
                modifier = Modifier.weight(1f).height(50.dp).semantics { contentDescription = "Dashboard" },
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DT.Border)) {
                Icon(Icons.Default.Home, null, tint = DT.SubText, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Home", color = DT.SubText, fontSize = 14.sp)
            }
            Button(onClick = onNewSale,
                modifier = Modifier.weight(1.6f).height(50.dp).semantics { contentDescription = "New sale" },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DT.Teal)) {
                Icon(Icons.Default.QrCode, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("New Sale", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
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
        dismissButton = { OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, DT.Border)) { Text("Cancel", color = DT.SubText) } })
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
    Box(modifier = modifier.height(44.dp).clip(RoundedCornerShape(12.dp))
        .background(color.copy(0.12f)).border(1.dp, color.copy(0.3f), RoundedCornerShape(12.dp))
        .clickable(enabled = !isLoading, indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = color, strokeWidth = 2.dp)
            else Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}
