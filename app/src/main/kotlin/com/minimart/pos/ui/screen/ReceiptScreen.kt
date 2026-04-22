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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimart.pos.data.entity.PaymentMethod
import com.minimart.pos.data.entity.Sale
import com.minimart.pos.data.entity.SaleItem
import com.minimart.pos.data.entity.SaleStatus
import com.minimart.pos.data.entity.SaleWithItems
import com.minimart.pos.data.repository.SaleRepository
import com.minimart.pos.printer.ThermalPrinter
import com.minimart.pos.ui.theme.DT
import com.minimart.pos.util.PdfReceiptGenerator
import com.minimart.pos.util.ReceiptData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@Composable
fun ReceiptScreen(
    saleId: Long,
    onNewSale: () -> Unit,
    onDashboard: () -> Unit,
    printer: ThermalPrinter,
    storeName: String,
    currency: String,
    footerMessage: String,
    cashierName: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var statusMsg by remember { mutableStateOf<String?>(null) }
    var isGeneratingPdf by remember { mutableStateOf(false) }
    // Dummy sale for display — in production load from DB via ViewModel
    val sale = remember {
        Sale(
            id = saleId,
            receiptNumber = "RCP-${saleId.toString().padStart(4, '0')}",
            subtotal = 0.0, taxAmount = 0.0, discountAmount = 0.0,
            totalAmount = 0.0, amountPaid = 0.0, changeGiven = 0.0,
            paymentMethod = PaymentMethod.CASH,
            status = SaleStatus.COMPLETED, cashierId = 1L
        )
    }
    val df = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    // Spring bounce animation for checkmark
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
        label = "checkScale"
    )

    Box(modifier = Modifier.fillMaxSize().background(DT.Bg)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(top = 48.dp, bottom = 24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Animated checkmark ────────────────────────────────────────────
            Box(
                modifier = Modifier.size(96.dp).scale(scale).clip(CircleShape)
                    .background(DT.Green.copy(0.15f)).border(3.dp, DT.Green, CircleShape)
                    .semantics { contentDescription = "Sale complete" },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CheckCircle, null, tint = DT.Green, modifier = Modifier.size(56.dp))
            }

            Text("Sale Complete!", color = DT.Green, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
            Text("Receipt ${sale.receiptNumber}  •  ${df.format(Date())}",
                color = DT.SubText, style = MaterialTheme.typography.bodySmall)

            HorizontalDivider(color = DT.Border)

            // ── Share / Print row ─────────────────────────────────────────────
            Text("Share Receipt", color = DT.OnSurface, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth())

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {

                ReceiptActionBtn(Modifier.weight(1f), Icons.Default.PictureAsPdf, "Save PDF",
                    DT.Red, isGeneratingPdf) {
                    scope.launch {
                        isGeneratingPdf = true
                        try {
                            val data = buildReceiptData(sale, emptyList(), storeName, currency, cashierName, footerMessage)
                            val file = withContext(Dispatchers.IO) { PdfReceiptGenerator.generate(context, data) }
                            statusMsg = "✓ PDF saved: ${file.name}"
                        } catch (e: Exception) {
                            statusMsg = "PDF error: ${e.message}"
                        }
                        isGeneratingPdf = false
                    }
                }

                ReceiptActionBtn(Modifier.weight(1f), Icons.Default.Share, "WhatsApp",
                    Color(0xFF25D366)) {
                    scope.launch {
                        try {
                            val data = buildReceiptData(sale, emptyList(), storeName, currency, cashierName, footerMessage)
                            val file = withContext(Dispatchers.IO) { PdfReceiptGenerator.generate(context, data) }
                            val uri = PdfReceiptGenerator.getShareUri(context, file)
                            PdfReceiptGenerator.shareViaWhatsApp(context, uri, storeName, "$currency ${String.format("%.2f", sale.totalAmount)}")
                        } catch (e: Exception) {
                            statusMsg = "Share error: ${e.message}"
                        }
                    }
                }

                ReceiptActionBtn(Modifier.weight(1f), Icons.Default.IosShare, "Share",
                    DT.Teal) {
                    scope.launch {
                        try {
                            val data = buildReceiptData(sale, emptyList(), storeName, currency, cashierName, footerMessage)
                            val file = withContext(Dispatchers.IO) { PdfReceiptGenerator.generate(context, data) }
                            val uri = PdfReceiptGenerator.getShareUri(context, file)
                            PdfReceiptGenerator.shareGeneric(context, uri)
                        } catch (e: Exception) {
                            statusMsg = "Error: ${e.message}"
                        }
                    }
                }
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

            // ── Navigation ────────────────────────────────────────────────────
            Button(
                onClick = onNewSale,
                modifier = Modifier.fillMaxWidth().height(52.dp)
                    .semantics { contentDescription = "Start a new sale" },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DT.Teal)
            ) {
                Icon(Icons.Default.QrCode, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("New Sale", fontWeight = FontWeight.Bold, color = Color.White)
            }

            OutlinedButton(
                onClick = onDashboard,
                modifier = Modifier.fillMaxWidth().height(52.dp)
                    .semantics { contentDescription = "Return to dashboard" },
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DT.Border)
            ) {
                Icon(Icons.Default.Home, null, tint = DT.SubText)
                Spacer(Modifier.width(8.dp))
                Text("Dashboard", color = DT.SubText)
            }
        }
    }
}

private fun buildReceiptData(
    sale: Sale, items: List<SaleItem>,
    storeName: String, currency: String,
    cashierName: String, footerMessage: String
) = ReceiptData(
    sale = sale,
    items = items,
    productNames = items.associate { it.productId to it.productName },
    storeName = storeName,
    currency = currency,
    cashierName = cashierName,
    footerMessage = footerMessage
)

@Composable
private fun ReceiptActionBtn(
    modifier: Modifier, icon: ImageVector, label: String,
    color: Color, isLoading: Boolean = false, onClick: () -> Unit
) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(14.dp))
            .background(color.copy(0.12f)).border(1.dp, color.copy(0.3f), RoundedCornerShape(14.dp))
            .clickable(enabled = !isLoading, indication = null,
                interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = color, strokeWidth = 2.dp)
            else Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            Text(label, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}
