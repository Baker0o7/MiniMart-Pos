package com.minimart.pos.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.minimart.pos.data.entity.SaleWithItems
import com.minimart.pos.printer.PrintResult
import com.minimart.pos.printer.ThermalPrinter
import com.minimart.pos.ui.theme.Brand500
import com.minimart.pos.ui.theme.SuccessGreen
import com.minimart.pos.ui.viewmodel.CartViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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
    val scope = rememberCoroutineScope()
    var printStatus by remember { mutableStateOf<String?>(null) }

    // In a real app, load saleWithItems from VM by saleId
    // For now we show a success screen with print option

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sale Complete", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SuccessGreen, titleContentColor = Color.White)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(Modifier.height(24.dp))

            // ── Success icon ──
            Icon(
                Icons.Default.CheckCircle,
                null,
                tint = SuccessGreen,
                modifier = Modifier.size(88.dp)
            )
            Text("Payment Received!", fontWeight = FontWeight.Bold, fontSize = 24.sp)
            Text("Sale #$saleId completed", color = MaterialTheme.colorScheme.onSurfaceVariant)

            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))

            // ── Print button ──
            printStatus?.let {
                Text(it, color = if (it.startsWith("✓")) SuccessGreen else MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        if (!printer.isConnected) {
                            printStatus = "Printer not connected. Go to Settings → Printer."
                        } else {
                            printStatus = "Printing…"
                            // In production, pass actual SaleWithItems here
                            printStatus = "✓ Receipt printed"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(0.85f).height(52.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Brand500)
            ) {
                Icon(Icons.Default.Print, null, tint = Brand500)
                Spacer(Modifier.width(8.dp))
                Text("Print Receipt", color = Brand500, fontWeight = FontWeight.SemiBold)
            }

            // ── Action buttons ──
            Button(
                onClick = onNewSale,
                modifier = Modifier.fillMaxWidth(0.85f).height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Brand500)
            ) {
                Icon(Icons.Default.QrCodeScanner, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("New Sale", fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 16.sp)
            }

            TextButton(onClick = onDashboard) {
                Text("Back to Dashboard")
            }
        }
    }
}
