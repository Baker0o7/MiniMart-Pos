package com.minimart.pos.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.minimart.pos.data.entity.Product
import com.minimart.pos.ui.theme.DT
import com.minimart.pos.ui.viewmodel.ProductViewModel
import java.util.Locale

@Composable
fun InventoryScreen(
    onBack: () -> Unit,
    canEditPrices: Boolean = true,
    currency: String = "KES",
    onInsights: (() -> Unit)? = null,
    vm: ProductViewModel = hiltViewModel()
) {
    val products by vm.products.collectAsState()
    val categories by vm.categories.collectAsState()
    val selectedCat by vm.selectedCategory.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()
    val uiState by vm.uiState.collectAsState()
    val lowStock by vm.lowStockProducts.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editProduct by remember { mutableStateOf<Product?>(null) }
    var showStockDialog by remember { mutableStateOf<Product?>(null) }
    var lowOnly by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.successMessage) {
        if (uiState.successMessage != null) { kotlinx.coroutines.delay(2500); vm.clearMessages() }
    }
    // Errors (duplicate barcode, "cannot remove more than stock", ...) stay a little longer.
    LaunchedEffect(uiState.error) {
        if (uiState.error != null) { kotlinx.coroutines.delay(4000); vm.clearMessages() }
    }

    val lowIds = remember(lowStock) { lowStock.map { it.id }.toSet() }
    val shown = remember(products, lowOnly, lowIds) {
        if (lowOnly) products.filter { it.id in lowIds } else products
    }
    // Value of the stock currently listed, at cost (weighed products are costed per kg).
    val stockValue = remember(shown) {
        shown.sumOf { (if (it.isWeighed) it.stockKg else it.stock.toDouble().coerceAtLeast(0.0)) * it.costPrice }
    }

    Box(modifier = Modifier.fillMaxSize().background(DT.Bg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            GradientHeader(
                title = "Inventory",
                subtitle = "${products.size} products • ${lowStock.size} low stock",
                onBack = onBack,
                actions = {
                    if (canEditPrices && onInsights != null) {
                        HeaderPillButton("Insights", Icons.Default.Insights, onInsights)
                        Spacer(Modifier.width(8.dp))
                    }
                    if (canEditPrices) HeaderPillButton("Add", Icons.Default.Add) { editProduct = null; showAddDialog = true }
                }
            )

            FeedbackBanner(
                message = uiState.error ?: uiState.successMessage,
                isError = uiState.error != null,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp)
            )

            // ── Summary tiles ─────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SummaryTile("Listed", shown.size.toString(), DT.TealLight, Modifier.weight(1f))
                SummaryTile("Low stock", lowStock.size.toString(),
                    if (lowStock.isEmpty()) DT.Green else DT.Amber, Modifier.weight(1f))
                if (canEditPrices) {
                    SummaryTile("Stock value", "$currency ${String.format(Locale.US, "%,.0f", stockValue)}",
                        DT.OnSurface, Modifier.weight(1.6f))
                }
            }

            // ── Search ────────────────────────────────────────────────────────
            OutlinedTextField(
                value = searchQuery,
                onValueChange = vm::setSearchQuery,
                placeholder = { Text("Search by name or barcode", color = DT.SubText) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = DT.SubText, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) IconButton(onClick = { vm.setSearchQuery("") }) {
                        Icon(Icons.Default.Close, "Clear search", tint = DT.SubText, modifier = Modifier.size(18.dp))
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border,
                    focusedTextColor = DT.OnSurface, unfocusedTextColor = DT.OnSurface,
                    cursorColor = DT.Teal,
                    focusedContainerColor = DT.Surface, unfocusedContainerColor = DT.Surface
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 52.dp)
            )

            Spacer(Modifier.height(10.dp))

            // ── Filter chips ──────────────────────────────────────────────────
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { CategoryChip("Low stock (${lowStock.size})", lowOnly, DT.Amber) { lowOnly = !lowOnly } }
                item { CategoryChip("All", selectedCat == null) { vm.setCategory(null) } }
                items(categories) { cat -> CategoryChip(cat, selectedCat == cat) { vm.setCategory(cat) } }
            }

            Spacer(Modifier.height(10.dp))

            // ── Product list ──────────────────────────────────────────────────
            val context = LocalContext.current
            LazyColumn(
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(shown, key = { it.id }) { product ->
                    InventoryRow(
                        product = product,
                        currency = currency,
                        canEdit = canEditPrices,
                        onEdit = { editProduct = product; showAddDialog = true },
                        onAdjustStock = { showStockDialog = product },
                        onDelete = { vm.deleteProduct(product.id) },
                        onCallSupplier = {
                            // Devices without a dialer (some tablets) used to crash here.
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(product.supplierPhone.trim()))))
                            }
                        }
                    )
                }
                if (shown.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Inventory2, null, modifier = Modifier.size(56.dp), tint = DT.SubText.copy(0.4f))
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    when {
                                        lowOnly && searchQuery.isBlank() -> "Nothing is running low"
                                        searchQuery.isNotBlank() -> "No results for \"$searchQuery\""
                                        else -> "No products yet"
                                    },
                                    color = DT.SubText
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddEditProductDialog(
            product = editProduct,
            onDismiss = { showAddDialog = false; editProduct = null },
            onSave = { vm.saveProduct(it); showAddDialog = false; editProduct = null },
            currency = currency,
            categories = categories
        )
    }
    showStockDialog?.let { product ->
        StockAdjustDialog(product = product, onDismiss = { showStockDialog = null },
            onAdjust = { delta ->
                if (product.isWeighed) vm.adjustWeighedStock(product.id, delta) else vm.adjustStock(product.id, delta.toInt())
                showStockDialog = null
            })
    }
}

@Composable
private fun SummaryTile(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(14.dp)).background(DT.Surface)
            .border(1.dp, DT.Border, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(label, color = DT.SubText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(value, color = valueColor, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, accent: Color = DT.Teal, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier.height(38.dp).clip(shape)
            .background(if (selected) accent else DT.Surface2)
            .border(1.dp, if (selected) accent else DT.Border, shape)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) Color.White else DT.SubText,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun InventoryRow(
    product: Product,
    currency: String,
    canEdit: Boolean,
    onEdit: () -> Unit,
    onAdjustStock: () -> Unit,
    onDelete: () -> Unit,
    onCallSupplier: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val daysToExpiry = if (product.expiryDate > 0L)
        ((product.expiryDate - System.currentTimeMillis()) / (24L * 60 * 60 * 1000)).toInt() else null

    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(DT.Surface).border(1.dp, DT.Border, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(DT.TealDim),
                contentAlignment = Alignment.Center) {
                Text(product.name.firstOrNull()?.uppercase() ?: "?",
                    color = DT.TealLight, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(product.name, color = DT.OnSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOf(product.category, product.barcode).filter { it.isNotBlank() }.joinToString("  •  "),
                    color = DT.SubText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (daysToExpiry != null && daysToExpiry <= 30) {
                    Text(if (daysToExpiry < 0) "Expired" else "Expires in ${daysToExpiry}d",
                        color = if (daysToExpiry < 0) DT.Red else DT.Amber, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                StockPill(product)
                Spacer(Modifier.height(4.dp))
                val unitPrice = if (product.isWeighed) "${formatPrice(product.pricePerKg)}/kg" else formatPrice(product.price)
                Text("$currency $unitPrice", color = DT.SubText, fontSize = 12.sp, maxLines = 1)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ActionBtn("Stock", DT.TealLight, onAdjustStock)
            if (canEdit) ActionBtn("Edit", DT.Teal, onEdit)
            if (product.supplierPhone.isNotBlank()) ActionBtn("Call", DT.Teal, onCallSupplier)
            Spacer(Modifier.weight(1f))
            if (canEdit) {
                IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Delete, "Delete ${product.name}", tint = DT.Red.copy(0.85f), modifier = Modifier.size(20.dp))
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = DT.Surface,
            title = { Text("Delete product?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("\"${product.name}\" will be permanently removed. This cannot be undone.", color = DT.SubText) },
            confirmButton = {
                Button(
                    onClick = { onDelete(); showDeleteConfirm = false },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DT.Red, contentColor = Color.White)
                ) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }, shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, DT.Border)) { Text("Cancel", color = DT.SubText) }
            }
        )
    }
}

@Composable
private fun ActionBtn(label: String, textColor: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier.height(36.dp).clip(shape)
            .background(DT.Surface2)
            .border(1.dp, textColor.copy(0.3f), shape)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = textColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Receive or remove stock. Whole units normally; weighed products take kilograms with up to
 * three decimals. The result is previewed and removal beyond the current stock is blocked.
 */
@Composable
private fun StockAdjustDialog(product: Product, onDismiss: () -> Unit, onAdjust: (Double) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var isAddition by remember { mutableStateOf(true) }
    val qty = amount.toDoubleOrNull() ?: 0.0
    val current = if (product.isWeighed) product.stockKg else product.stock.toDouble()
    val newStock = if (isAddition) current + qty else current - qty
    val tooMuch = !isAddition && qty > current + 1e-9
    fun fmt(v: Double) = if (product.isWeighed) String.format(Locale.US, "%.2f kg", v) else v.toLong().toString()

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border,
        focusedTextColor = DT.OnSurface, unfocusedTextColor = DT.OnSurface, cursorColor = DT.Teal,
        focusedContainerColor = DT.Bg, unfocusedContainerColor = DT.Bg
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DT.Surface,
        title = { Text("Adjust stock", color = DT.OnSurface, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${product.name} — current: ${product.stockLabel}", color = DT.SubText, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryChip("Add", isAddition, DT.Green) { isAddition = true }
                    CategoryChip("Remove", !isAddition, DT.Red) { isAddition = false }
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        amount = if (product.isWeighed) sanitizeDecimalInput(it, maxDecimals = 3, maxWhole = 5)
                        else it.filter(Char::isDigit).take(6)
                    },
                    label = { Text(if (product.isWeighed) "Weight (kg)" else "Quantity", color = DT.SubText) },
                    keyboardOptions = KeyboardOptions(keyboardType = if (product.isWeighed) KeyboardType.Decimal else KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp), colors = fieldColors
                )
                if (qty > 0) {
                    Text(
                        if (tooMuch) "Only ${fmt(current)} on record" else "New stock: ${fmt(newStock)}",
                        color = if (tooMuch) DT.Red else DT.Green, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdjust(if (isAddition) qty else -qty) },
                enabled = qty > 0 && !tooMuch,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DT.Teal, contentColor = Color.White,
                    disabledContainerColor = DT.Teal.copy(0.4f), disabledContentColor = Color.White.copy(0.7f))
            ) { Text("Confirm", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, DT.Border)) { Text("Cancel", color = DT.SubText) }
        }
    )
}
