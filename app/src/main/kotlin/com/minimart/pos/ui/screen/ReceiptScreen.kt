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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.minimart.pos.data.entity.PaymentMethod
import com.minimart.pos.data.entity.SaleItem
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
    val scope = rememberCoroutineScope()
    val state by vm.state.collectAsState()
    var statusMsg by remember { mutableStateOf<String?>(null) }
    var isGeneratingPdf by remember { mutableStateOf(false) }
    val df = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    LaunchedEffect(saleId) { vm.loadSale(saleId) }

    // Spring bounce for checkmark
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
        label = "check"
    )

    val swi = state.saleWithItems
    val sale = swi?.sale
    val items = swi?.items ?: emptyList()

    Box(modifier = Modifier.fillMaxSize().background(DT.Bg)) {
        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = DT.Teal)
        } else {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp).padding(top = 48.dp, bottom = 24.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ── Success checkmark ────────────────────────────────────────
                Box(
                    modifier = Modifier.size(88.dp).scale(scale).clip(CircleShape)
                        .background(DT.Green.copy(0.15f)).border(2.dp, DT.Green, CircleShape)
                        .semantics { contentDescription = "Sale complete" },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = DT.Green, modifier = Modifier.size(52.dp))
                }

                Text("Sale Complete!", color = DT.Green, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                if (sale != null) {
                    Text("${sale.receiptNumber}  •  ${df.format(Date(sale.createdAt))}",
                        color = DT.SubText, style = MaterialTheme.typography.bodySmall)
                }

                // ── Receipt preview card ──────────────────────────────────────
                if (sale != null) {
                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(DT.Surface).border(1.dp, DT.Border, RoundedCornerShape(16.dp)).padding(16.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Header
                            Text(storeName.uppercase(), color = DT.OnSurface, fontWeight = FontWeight.Bold,
                                fontSize = 15.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                            Text("RECEIPT", color = DT.Teal, fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
                            HorizontalDivider(color = DT.Border)

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(df.format(Date(sale.createdAt)), color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                                Text("Cashier: $cashierName", color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                            }
                            HorizontalDivider(color = DT.Border)

                            // Items
                            if (items.isEmpty()) {
                                Text("No items recorded", color = DT.SubText, style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                            } else {
                                items.forEach { item ->
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(item.productName, color = DT.OnSurface,
                                                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                            Text("$currency ${String.format("%.2f", item.unitPrice)} × ${item.quantity}",
                                                color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                                        }
                                        Text("$currency ${String.format("%.2f", item.lineTotal)}",
                                            color = DT.TealLight, fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            HorizontalDivider(color = DT.Border)

                            // Totals
                            if (sale.discountAmount > 0) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Discount", color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                                    Text("-$currency ${String.format("%.2f", sale.discountAmount)}", color = DT.Green, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            if (sale.taxAmount > 0) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Tax", color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                                    Text("$currency ${String.format("%.2f", sale.taxAmount)}", color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("TOTAL", color = DT.OnSurface, fontWeight = FontWeight.Bold)
                                Text("$currency ${String.format("%.2f", sale.totalAmount)}",
                                    color = DT.Teal, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                            }
                            val payLabel = when (sale.paymentMethod) {
                                PaymentMethod.CASH -> "Cash  •  Change: $currency ${String.format("%.2f", sale.changeGiven)}"
                                PaymentMethod.MPESA -> "M-Pesa${sale.mpesaRef?.let { "  •  Ref: $it" } ?: ""}"
                                else -> sale.paymentMethod.name
                            }
                            Text("Payment: $payLabel", color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                            HorizontalDivider(color = DT.Border)
                            Text(footerMessage, color = DT.Teal, style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        }
                    }
                }

                // ── Share actions ─────────────────────────────────────────────
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                statusMsg = "✓ ${file.name}"
                            } catch (e: Exception) { statusMsg = "Error: ${e.message}" }
                            isGeneratingPdf = false
                        }
                    }
                    ReceiptActionBtn(Modifier.weight(1f), Icons.Default.Share, "WhatsApp", Color(0xFF25D366)) {
                        scope.launch {
                            try {
                                val data = ReceiptData(sale = sale ?: return@launch, items = items,
                                    productNames = items.associate { it.productId to it.productName },
                                    storeName = storeName, currency = currency,
                                    cashierName = cashierName, footerMessage = footerMessage)
                                val file = withContext(Dispatchers.IO) { PdfReceiptGenerator.generate(context, data) }
                                val uri = PdfReceiptGenerator.getShareUri(context, file)
                                PdfReceiptGenerator.shareViaWhatsApp(context, uri, storeName,
                                    "$currency ${String.format("%.2f", sale?.totalAmount ?: 0.0)}")
                            } catch (e: Exception) { statusMsg = "Error: ${e.message}" }
                        }
                    }
                    ReceiptActionBtn(Modifier.weight(1f), Icons.Default.IosShare, "Share", DT.Teal) {
                        scope.launch {
                            try {
                                val data = ReceiptData(sale = sale ?: return@launch, items = items,
                                    productNames = items.associate { it.productId to it.productName },
                                    storeName = storeName, currency = currency,
                                    cashierName = cashierName, footerMessage = footerMessage)
                                val file = withContext(Dispatchers.IO) { PdfReceiptGenerator.generate(context, data) }
                                PdfReceiptGenerator.shareGeneric(context, PdfReceiptGenerator.getShareUri(context, file))
                            } catch (e: Exception) { statusMsg = "Error: ${e.message}" }
                        }
                    }
                }

                AnimatedVisibility(visible = statusMsg != null) {
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(DT.Surface).border(1.dp, DT.Border, RoundedCornerShape(10.dp)).padding(10.dp)) {
                        Icon(Icons.Default.Info, null, tint = DT.Teal, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(statusMsg ?: "", color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                    }
                }

                HorizontalDivider(color = DT.Border)

                Button(onClick = onNewSale, modifier = Modifier.fillMaxWidth().height(52.dp)
                    .semantics { contentDescription = "New sale" },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DT.Teal)) {
                    Icon(Icons.Default.QrCode, null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("New Sale", fontWeight = FontWeight.Bold, color = Color.White)
                }
                OutlinedButton(onClick = onDashboard, modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DT.Border)) {
                    Icon(Icons.Default.Home, null, tint = DT.SubText)
                    Spacer(Modifier.width(8.dp))
                    Text("Dashboard", color = DT.SubText)
                }
            }
        }
    }
}

@Composable
private fun ReceiptActionBtn(modifier: Modifier, icon: ImageVector, label: String,
    color: Color, isLoading: Boolean = false, onClick: () -> Unit) {
    Box(modifier = modifier.clip(RoundedCornerShape(12.dp))
        .background(color.copy(0.12f)).border(1.dp, color.copy(0.3f), RoundedCornerShape(12.dp))
        .clickable(enabled = !isLoading, indication = null,
            interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
        .padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = color, strokeWidth = 2.dp)
            else Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Text(label, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}
