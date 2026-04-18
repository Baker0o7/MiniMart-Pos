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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.minimart.pos.data.entity.Product
import com.minimart.pos.ui.theme.Brand500
import com.minimart.pos.ui.theme.ErrorRed
import com.minimart.pos.ui.theme.SuccessGreen
import com.minimart.pos.ui.viewmodel.ProductViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    onBack: () -> Unit,
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

    LaunchedEffect(uiState.successMessage) {
        if (uiState.successMessage != null) { kotlinx.coroutines.delay(2000); vm.clearMessages() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Inventory", fontWeight = FontWeight.Bold)
                        Text("${products.size} products • ${lowStock.size} low stock",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(0.8f))
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Brand500, titleContentColor = Color.White, navigationIconContentColor = Color.White),
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, "Add product", tint = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // ── Search bar ────────────────────────────────────────────────────
            OutlinedTextField(
                value = searchQuery,
                onValueChange = vm::setSearchQuery,
                placeholder = { Text("Search name, barcode or SKU…", maxLines = 1) },
                leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) IconButton(onClick = { vm.setSearchQuery("") }) {
                        Icon(Icons.Default.Clear, null, modifier = Modifier.size(18.dp))
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).height(54.dp)
            )

            // ── Category chips ────────────────────────────────────────────────
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(selected = selectedCat == null, onClick = { vm.setCategory(null) }, label = { Text("All") })
                }
                items(categories) { cat ->
                    FilterChip(selected = selectedCat == cat, onClick = { vm.setCategory(cat) }, label = { Text(cat) })
                }
            }

            // ── Low stock banner ──────────────────────────────────────────────
            if (lowStock.isNotEmpty() && searchQuery.isBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(ErrorRed.copy(alpha = 0.12f)).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, null, tint = ErrorRed, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("${lowStock.size} items low on stock", color = ErrorRed, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }

            uiState.successMessage?.let {
                Text(it, color = SuccessGreen, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
            }

            // ── Product list ──────────────────────────────────────────────────
            LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(products, key = { it.id }) { product ->
                    InventoryProductRow(
                        product = product,
                        onEdit = { editProduct = it; showAddDialog = true },
                        onAdjustStock = { showStockDialog = it },
                        onDelete = { vm.deleteProduct(product.id) }
                    )
                }
                if (products.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Inventory2, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
                                Spacer(Modifier.height(8.dp))
                                Text(if (searchQuery.isNotBlank()) "No results for \"$searchQuery\"" else "No products yet",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            onSave = { vm.saveProduct(it); showAddDialog = false; editProduct = null }
        )
    }

    showStockDialog?.let { product ->
        StockAdjustDialog(
            product = product,
            onDismiss = { showStockDialog = null },
            onAdjust = { delta -> vm.adjustStock(product.id, delta); showStockDialog = null }
        )
    }
}

@Composable
private fun InventoryProductRow(
    product: Product,
    onEdit: (Product) -> Unit,
    onAdjustStock: (Product) -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val stockColor = when {
        product.stock == 0 -> ErrorRed
        product.stock <= product.lowStockThreshold -> MaterialTheme.colorScheme.tertiary
        else -> SuccessGreen
    }
    val stockBg = when {
        product.stock == 0 -> ErrorRed.copy(alpha = 0.12f)
        product.stock <= product.lowStockThreshold -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
        else -> SuccessGreen.copy(alpha = 0.1f)
    }

    Card(shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Stock badge
                Box(
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(stockBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(product.stock.toString(), fontWeight = FontWeight.Bold, color = stockColor, fontSize = 16.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(product.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    Text(product.category, style = MaterialTheme.typography.labelSmall, color = Brand500)
                }
                Text("KES ${String.format("%.2f", product.price)}", fontWeight = FontWeight.Bold, color = Brand500)
            }

            // Meta row — barcode, SKU
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (product.barcode.isNotBlank()) {
                    AssistChip(onClick = {}, label = { Text(product.barcode, style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = { Icon(Icons.Default.QrCode, null, modifier = Modifier.size(14.dp)) })
                }
                if (product.sku.isNotBlank()) {
                    AssistChip(onClick = {}, label = { Text("SKU: ${product.sku}", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = { Icon(Icons.Default.Label, null, modifier = Modifier.size(14.dp)) })
                }
            }

            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { onAdjustStock(product) },
                    modifier = Modifier.weight(1f).height(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Settings, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Stock", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = { onEdit(product) },
                    modifier = Modifier.weight(1f).height(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp), tint = Brand500)
                    Spacer(Modifier.width(4.dp))
                    Text("Edit", style = MaterialTheme.typography.labelSmall, color = Brand500)
                }
                IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, null, tint = ErrorRed, modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Product?") },
            text = { Text("Remove '${product.name}' from inventory?") },
            confirmButton = { TextButton(onClick = { onDelete(); showDeleteConfirm = false }) { Text("Delete", color = ErrorRed) } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun StockAdjustDialog(product: Product, onDismiss: () -> Unit, onAdjust: (Int) -> Unit) {
    var delta by remember { mutableStateOf("") }
    var isAddition by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust Stock — ${product.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Current stock: ${product.stock} ${product.unit}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = isAddition, onClick = { isAddition = true }, label = { Text("Add stock") },
                        leadingIcon = { Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)) })
                    FilterChip(selected = !isAddition, onClick = { isAddition = false }, label = { Text("Remove stock") },
                        leadingIcon = { Icon(Icons.Default.Remove, null, modifier = Modifier.size(16.dp)) })
                }
                OutlinedTextField(
                    value = delta,
                    onValueChange = { delta = it },
                    label = { Text("Quantity") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                val qty = delta.toIntOrNull() ?: 0
                if (qty > 0) {
                    val newStock = if (isAddition) product.stock + qty else product.stock - qty
                    Text("New stock will be: $newStock ${product.unit}",
                        color = if (newStock < 0) ErrorRed else SuccessGreen,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val qty = delta.toIntOrNull() ?: 0
                    onAdjust(if (isAddition) qty else -qty)
                },
                enabled = (delta.toIntOrNull() ?: 0) > 0
            ) { Text("Confirm") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
