package com.minimart.pos.ui.screen

import com.minimart.pos.util.Lang
import com.minimart.pos.ui.components.Text
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.minimart.pos.data.entity.PaymentMethod
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
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.minimart.pos.data.dao.SaleStats
import kotlinx.coroutines.flow.Flow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

// ─── ViewModel ────────────────────────────────────────────────────────────────

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SalesHistoryViewModel @Inject constructor(
    private val saleRepo: SaleRepository,
    private val voidSaleUseCase: com.minimart.pos.domain.usecase.VoidSaleUseCase
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val debouncedQuery = _query.debounce(250)

    /** Sales load a page at a time instead of the whole table. */
    val sales: Flow<PagingData<SaleWithItems>> = debouncedQuery
        .flatMapLatest { q -> saleRepo.pagedCompletedSales(q) }
        .cachedIn(viewModelScope)

    /** Record count and revenue for the current filter, computed in SQL. */
    val stats: StateFlow<SaleStats> = debouncedQuery
        .flatMapLatest { q -> saleRepo.completedSalesStats(q) }
        .catch { emit(SaleStats(0, 0.0)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(3000), SaleStats(0, 0.0))

    fun setQuery(q: String) { _query.value = q }

    /** Void a sale directly from the history list — for Manager/Owner without
     * needing to navigate to the full receipt screen. */
    fun voidSale(saleId: Long, reason: String) {
        viewModelScope.launch {
            try { voidSaleUseCase(saleId, reason) }
            catch (_: Exception) {}
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesHistoryScreen(
    onBack: () -> Unit,
    onSaleClick: (Long) -> Unit,
    currency: String,
    currentRole: com.minimart.pos.data.entity.UserRole? = null,
    vm: SalesHistoryViewModel = hiltViewModel()
) {
    val canVoid = com.minimart.pos.util.RoleManager.canApplyDiscounts(currentRole) // Manager+ only
    val query   by vm.query.collectAsStateWithLifecycle()
    val sales   = vm.sales.collectAsLazyPagingItems()
    val stats   by vm.stats.collectAsStateWithLifecycle()
    val df      = remember { SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()) }
    var pendingVoidId by remember { mutableStateOf<Long?>(null) }
    pendingVoidId?.let { id ->
        ReasonDialog(Lang.t("Void Sale", "إلغاء عملية البيع"), Lang.t("This cannot be undone. Stock will be restored.", "لا يمكن التراجع عن هذا. سيُعاد المخزون."), DT.Red,
            onDismiss = { pendingVoidId = null },
            onConfirm = { reason -> vm.voidSale(id, reason); pendingVoidId = null })
    }

    Box(modifier = Modifier.fillMaxSize().background(DT.Bg)) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top bar ───────────────────────────────────────────────────────
            GradientHeader(
                title = tr(com.minimart.pos.R.string.sales_history),
                subtitle = Lang.t("${stats.count} records", "${stats.count} سجلًا") + "  •  $currency ${String.format(java.util.Locale.US, "%,.2f", stats.total)}",
                onBack = onBack
            )
            Spacer(Modifier.height(12.dp))

            // ── Search bar ────────────────────────────────────────────────────
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                placeholder = { Text(tr(com.minimart.pos.R.string.search_receipt), color = DT.SubText, maxLines = 1) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = DT.SubText, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) {
                        Icon(Icons.Default.Close, Lang.t("Clear search", "مسح البحث"), tint = DT.SubText, modifier = Modifier.size(18.dp))
                    }
                },
                singleLine = true,
                keyboardOptions = searchKeyboardOptions(),
                keyboardActions = searchKeyboardActions(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border,
                    focusedTextColor = DT.OnSurface, unfocusedTextColor = DT.OnSurface,
                    cursorColor = DT.Teal, focusedContainerColor = DT.Surface, unfocusedContainerColor = DT.Surface
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(8.dp))

            if (sales.itemCount == 0 && sales.loadState.refresh !is LoadState.Loading) {
                EmptyState(
                    Icons.Default.Receipt,
                    if (query.isNotBlank()) Lang.t("No results for \"$query\"", "لا نتائج لـ \"$query\"") else Lang.t("No sales yet", "لا توجد مبيعات بعد"),
                    Modifier.fillMaxSize(),
                    hint = if (query.isNotBlank()) tr(com.minimart.pos.R.string.try_receipt) else null
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(count = sales.itemCount, key = sales.itemKey { it.sale.id }) { index ->
                        val saleWithItems = sales[index] ?: return@items
                        SaleHistoryRow(
                            saleWithItems = saleWithItems,
                            currency = currency,
                            df = df,
                            canVoid = canVoid,
                            onVoid = { pendingVoidId = saleWithItems.sale.id },
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
    canVoid: Boolean = false,
    onVoid: () -> Unit = {},
    onClick: () -> Unit
) {
    val sale = saleWithItems.sale
    var showVoidConfirm by remember { mutableStateOf(false) }
    val statusColor = when (sale.status) {
        SaleStatus.COMPLETED -> DT.Green
        SaleStatus.VOIDED    -> DT.Red
        SaleStatus.REFUNDED  -> DT.Amber
        else                 -> DT.SubText
    }

    if (showVoidConfirm) {
        AlertDialog(
            onDismissRequest = { showVoidConfirm = false },
            containerColor = DT.Surface,
            title = { Text(tr(com.minimart.pos.R.string.void_sale_q), color = DT.Ink, fontWeight = FontWeight.Bold) },
            text = { Text(tr(com.minimart.pos.R.string.void_confirm, sale.receiptNumber, currency, String.format(java.util.Locale.US, "%,.2f", sale.totalAmount)), color = DT.SubText) },
            confirmButton = {
                Button(onClick = { onVoid(); showVoidConfirm = false },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DT.Red)) {
                    Text(tr(com.minimart.pos.R.string.void_sale), color = DT.Ink, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showVoidConfirm = false }, shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DT.Border)) {
                    Text(tr(com.minimart.pos.R.string.cancel), color = DT.SubText)
                }
            }
        )
    }

    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DT.Surface)
            .border(1.dp, DT.Border, RoundedCornerShape(14.dp))
            .clickable(role = androidx.compose.ui.semantics.Role.Button, indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
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
                    Text("$currency ${String.format(java.util.Locale.US, "%,.2f", sale.totalAmount)}",
                        color = DT.Teal, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Box(modifier = Modifier.clip(RoundedCornerShape(6.dp))
                        .background(statusColor.copy(0.15f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text(sale.status.name, color = statusColor,
                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
            // Items summary + payment method chip
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ShoppingCart, null, tint = DT.SubText, modifier = Modifier.size(14.dp))
                Text(com.minimart.pos.util.ArabicCount.t(saleWithItems.items.size, "item", "items", "لا أصناف", "صنف واحد", "صنفان", "أصناف", "صنفًا"),
                    color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                val (pmLabel, pmColor) = when (sale.paymentMethod) {
                    PaymentMethod.CASH   -> "💵 Cash"   to com.minimart.pos.ui.theme.paymentColor(sale.paymentMethod)
                    PaymentMethod.MPESA  -> "📱 M-Pesa" to com.minimart.pos.ui.theme.paymentColor(sale.paymentMethod)
                    PaymentMethod.CARD   -> "💳 Card"   to com.minimart.pos.ui.theme.paymentColor(sale.paymentMethod)
                    PaymentMethod.CREDIT -> "🤝 Credit" to com.minimart.pos.ui.theme.paymentColor(sale.paymentMethod)
                    PaymentMethod.MIXED  -> "🔀 Split"  to com.minimart.pos.ui.theme.paymentColor(sale.paymentMethod)
                }
                Box(modifier = Modifier.clip(RoundedCornerShape(6.dp))
                    .background(pmColor.copy(0.15f))
                    .padding(horizontal = 6.dp, vertical = 1.dp)) {
                    Text(pmLabel, color = pmColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                if (!sale.mpesaRef.isNullOrBlank()) {
                    Text("•  Ref: ${sale.mpesaRef}", color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                }
            }
            // Quick void button for Manager/Owner on COMPLETED sales only
            if (canVoid && sale.status == SaleStatus.COMPLETED) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showVoidConfirm = true },
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DT.Red.copy(0.5f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(Icons.Default.Cancel, null, tint = DT.Red, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(tr(com.minimart.pos.R.string.void_sale), color = DT.Red, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
