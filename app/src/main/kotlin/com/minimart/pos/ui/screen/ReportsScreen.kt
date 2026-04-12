package com.minimart.pos.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.minimart.pos.data.entity.Sale
import com.minimart.pos.data.entity.SaleStatus
import com.minimart.pos.ui.theme.Accent
import com.minimart.pos.ui.theme.Brand500
import com.minimart.pos.ui.theme.SuccessGreen
import com.minimart.pos.ui.viewmodel.ReportPeriod
import com.minimart.pos.ui.viewmodel.ReportsViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onBack: () -> Unit,
    vm: ReportsViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsState()
    val period by vm.period.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reports", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Brand500, titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Period selector ──
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReportPeriod.entries.forEach { p ->
                        FilterChip(
                            selected = period == p,
                            onClick = { vm.setPeriod(p) },
                            label = { Text(p.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }

            // ── KPI cards ──
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KpiCard(Modifier.weight(1f), "Revenue", "${state.currency} ${String.format("%,.2f", state.totalRevenue)}", SuccessGreen, Icons.Default.AttachMoney)
                    KpiCard(Modifier.weight(1f), "Transactions", state.totalTransactions.toString(), Brand500, Icons.Default.Receipt)
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KpiCard(Modifier.weight(1f), "Avg Basket", "${state.currency} ${String.format("%,.2f", state.averageBasket)}", Accent, Icons.Default.ShoppingBasket)
                    Spacer(Modifier.weight(1f))
                }
            }

            // ── Top sellers ──
            if (state.topSellers.isNotEmpty()) {
                item { Text("Top Selling Products", fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
                items(state.topSellers) { seller ->
                    Card(shape = RoundedCornerShape(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(seller.productName, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${seller.totalQty} units", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${state.currency} ${String.format("%,.2f", seller.totalRevenue)}", fontWeight = FontWeight.Bold, color = SuccessGreen)
                            }
                        }
                    }
                }
            }

            // ── Recent transactions ──
            if (state.sales.isNotEmpty()) {
                item { Text("Transactions", fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
                items(state.sales.take(20)) { sale ->
                    TransactionRow(sale, state.currency)
                }
            }
        }
    }
}

@Composable
private fun KpiCard(modifier: Modifier, title: String, value: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(modifier = modifier, shape = RoundedCornerShape(14.dp)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = color)
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TransactionRow(sale: Sale, currency: String) {
    val df = SimpleDateFormat("HH:mm dd/MM", Locale.getDefault())
    Card(shape = RoundedCornerShape(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Receipt, null, tint = Brand500, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(sale.receiptNumber, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(df.format(Date(sale.createdAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("$currency ${String.format("%.2f", sale.totalAmount)}", fontWeight = FontWeight.Bold, color = SuccessGreen)
                Text(sale.paymentMethod.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
