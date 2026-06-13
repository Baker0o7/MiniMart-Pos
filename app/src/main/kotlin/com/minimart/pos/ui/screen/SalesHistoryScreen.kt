package com.minimart.pos.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimart.pos.data.entity.SaleStatus
import com.minimart.pos.data.entity.SaleWithItems
import com.minimart.pos.data.repository.SaleRepository
import com.minimart.pos.ui.theme.DT
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SalesHistoryViewModel @Inject constructor(
    private val saleRepo: SaleRepository
) : ViewModel() {
            item {
                Box(Modifier.fillMaxWidth()
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(DT.Teal, androidx.compose.ui.graphics.Color(0xFF004D40))))
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Sales History", color = androidx.compose.ui.graphics.Color.White,
                                fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                            Text("Past transactions", color = androidx.compose.ui.graphics.Color.White.copy(0.7f), fontSize = 12.sp)
                        }
                        Icon(Icons.Default.History, null,
                            tint = androidx.compose.ui.graphics.Color.White.copy(0.7f),
                            modifier = Modifier.size(28.dp))
                    }
                }
            }

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val sales: StateFlow<List<SaleWithItems>> = _query
        .debounce(250)
        .flatMapLatest { q ->
            if (q.isBlank()) saleRepo.getCompletedSales()
            else saleRepo.searchSales(q)
        }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(3000), emptyList())

    val totalRevenue: StateFlow<Double> = sales
        .map { list -> list.filter { it.sale.status == SaleStatus.COMPLETED }.sumOf { it.sale.totalAmount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(3000), 0.0)

    fun setQuery(q: String) { _query.value = q }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesHistoryScreen(
    onBack: () -> Unit,
    onSaleClick: (Long) -> Unit,
    currency: String,
    vm: SalesHistoryViewModel = hiltViewModel()
) {
    val query   by vm.query.collectAsState()
    val sales   by vm.sales.collectAsState()
    val total   by vm.totalRevenue.collectAsState()
    val df      = remember { SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()) }

    Box(modifier = Modifier.fillMaxSize().background(DT.Bg)) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top bar ───────────────────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = DT.Teal)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sales History", color = DT.Teal, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Text("${sales.size} records  •  $currency ${String.format("%.2f", total)}",
                        color = DT.SubText, style = MaterialTheme.typography.labelMedium)
                }
            }

            // ── Search bar ────────────────────────────────────────────────────
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                placeholder = { Text("Search by receipt, ref, notes…", color = DT.SubText, maxLines = 1) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = DT.SubText, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) {
                        Icon(Icons.Default.Close, null, tint = DT.SubText, modifier = Modifier.size(18.dp))
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border,
                    focusedTextColor = DT.OnSurface, unfocusedTextColor = DT.OnSurface,
                    cursorColor = DT.Teal, focusedContainerColor = DT.Surface, unfocusedContainerColor = DT.Surface
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(8.dp))

            if (sales.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Receipt, null, modifier = Modifier.size(64.dp), tint = DT.SubText.copy(0.3f))
                        Text(if (query.isNotBlank()) "No results for \"$query\"" else "No sales yet",
                            color = DT.SubText, fontWeight = FontWeight.Medium)
                        if (query.isNotBlank()) Text("Try receipt number, M-Pesa ref, or notes",
                            color = DT.SubText.copy(0.6f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sales, key = { it.sale.id }) { saleWithItems ->
                        SaleHistoryRow(
                            saleWithItems = saleWithItems,
                            currency = currency,
                            df = df,
                            onClick = { onSaleClick(saleWithItems.sale.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SaleHistoryRow(
    saleWithItems: SaleWithItems,
    currency: String,
    df: SimpleDateFormat,
    onClick: () -> Unit
) {
    val sale = saleWithItems.sale
    val statusColor = when (sale.status) {
        SaleStatus.COMPLETED -> DT.Green
        SaleStatus.VOIDED    -> DT.Red
        SaleStatus.REFUNDED  -> DT.Amber
        else                 -> DT.SubText
    }

    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DT.Surface)
            .border(1.dp, DT.Border, RoundedCornerShape(14.dp))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(sale.receiptNumber, color = DT.OnSurface, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium)
                    Text(df.format(Date(sale.createdAt)), color = DT.SubText,
                        style = MaterialTheme.typography.labelSmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("$currency ${String.format("%.2f", sale.totalAmount)}",
                        color = DT.Teal, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Box(modifier = Modifier.clip(RoundedCornerShape(6.dp))
                        .background(statusColor.copy(0.15f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text(sale.status.name, color = statusColor,
                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
            // Items summary
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ShoppingCart, null, tint = DT.SubText, modifier = Modifier.size(14.dp))
                Text("${saleWithItems.items.size} item${if (saleWithItems.items.size != 1) "s" else ""}  •  ${sale.paymentMethod.name}",
                    color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                if (!sale.mpesaRef.isNullOrBlank()) {
                    Text("•  Ref: ${sale.mpesaRef}", color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
