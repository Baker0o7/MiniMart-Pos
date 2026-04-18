package com.minimart.pos.ui.screen

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
import com.minimart.pos.ui.theme.DT
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

    Box(modifier = Modifier.fillMaxSize().background(DT.Bg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top bar ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = DT.OnSurface)
                }
                Spacer(Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Inventory", color = DT.Teal, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    Text("${products.size} products • ${lowStock.size} low stock",
                        color = DT.SubText, style = MaterialTheme.typography.labelMedium)
                }
                // Settings icon
                Box(
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(DT.Surface2),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, null, tint = DT.Teal, modifier = Modifier.size(22.dp))
                    }
                }
            }

            // ── Search bar ────────────────────────────────────────────────────
            OutlinedTextField(
                value = searchQuery,
                onValueChange = vm::setSearchQuery,
                placeholder = { Text("Search", color = DT.SubText) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = DT.SubText, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) IconButton(onClick = { vm.setSearchQuery("") }) {
                        Icon(Icons.Default.Close, null, tint = DT.SubText, modifier = Modifier.size(18.dp))
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DT.Teal,
                    unfocusedBorderColor = DT.Border,
                    focusedTextColor = DT.OnSurface,
                    unfocusedTextColor = DT.OnSurface,
                    cursorColor = DT.Teal,
                    focusedContainerColor = DT.Surface,
                    unfocusedContainerColor = DT.Surface
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(52.dp)
            )

            Spacer(Modifier.height(12.dp))

            // ── Category chips ────────────────────────────────────────────────
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    CategoryChip("All", selectedCat == null) { vm.setCategory(null) }
                }
                items(categories) { cat ->
                    CategoryChip(cat, selectedCat == cat) { vm.setCategory(cat) }
                }
            }

            Spacer(Modifier.height(12.dp))

            uiState.successMessage?.let {
                Text(it, color = DT.Green, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium)
            }

            // ── Product list ──────────────────────────────────────────────────
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(products, key = { it.id }) { product ->
                    DarkInventoryRow(
                        product = product,
                        onEdit = { editProduct = it; showAddDialog = true },
                        onAdjustStock = { showStockDialog = it },
                        onDelete = { vm.deleteProduct(product.id) }
                    )
                }
                if (products.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Inventory2, null, modifier = Modifier.size(56.dp), tint = DT.SubText.copy(0.4f))
                                Spacer(Modifier.height(8.dp))
                                Text(if (searchQuery.isNotBlank()) "No results for \"$searchQuery\"" else "No products yet", color = DT.SubText)
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
        StockAdjustDialog(product = product, onDismiss = { showStockDialog = null },
            onAdjust = { delta -> vm.adjustStock(product.id, delta); showStockDialog = null })
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) DT.Teal else DT.Surface2)
            .border(1.dp, if (selected) DT.Teal else DT.Border, RoundedCornerShape(20.dp))
            .then(Modifier.clickableNoRipple(onClick))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(label, color = if (selected) Color.White else DT.SubText,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.labelMedium)
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.clickable(
        indication = null,
        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
        onClick = onClick
    ))

@Composable
private fun DarkInventoryRow(
    product: Product,
    onEdit: (Product) -> Unit,
    onAdjustStock: (Product) -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val stockColor = when {
        product.stock == 0 -> DT.Red
        product.stock <= product.lowStockThreshold -> DT.Amber
        else -> DT.Green
    }

    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DT.Surface)
            .border(1.dp, DT.Border, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Product icon placeholder
                Box(
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(DT.TealDim),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Inventory2, null, tint = DT.Teal, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(product.name, color = DT.OnSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
                    // Barcode row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.QrCode, null, tint = DT.SubText, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(product.barcode, color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                    }
                    if (product.sku.isNotBlank()) {
                        Text("SKU ${product.sku}", color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                    }
                }
                // Stock badge
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(DT.TealDim).padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(product.stock.toString(), color = stockColor, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        Text("  KES${String.format("%.0f", product.price)}", color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionBtn("Stock", DT.Surface2, DT.TealLight) { onAdjustStock(product) }
                ActionBtn("Edit",  DT.Surface2, DT.Teal)      { onEdit(product) }
                Spacer(Modifier.weight(1f))
                ActionBtn("Delete", DT.Surface2, DT.Red)       { showDeleteConfirm = true }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = DT.Surface,
            title = { Text("Delete Product?", color = DT.OnSurface) },
            text = { Text("Remove '${product.name}'?", color = DT.SubText) },
            confirmButton = { TextButton(onClick = { onDelete(); showDeleteConfirm = false }) { Text("Delete", color = DT.Red) } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel", color = DT.SubText) } }
        )
    }
}

@Composable
private fun ActionBtn(label: String, bg: Color, textColor: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(1.dp, textColor.copy(0.3f), RoundedCornerShape(20.dp))
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                onClick = onClick
            ))
            .padding(horizontal = 16.dp, vertical = 7.dp)
    ) {
        Text(label, color = textColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StockAdjustDialog(product: Product, onDismiss: () -> Unit, onAdjust: (Int) -> Unit) {
    var delta by remember { mutableStateOf("") }
    var isAddition by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DT.Surface,
        title = { Text("Adjust Stock", color = DT.OnSurface, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${product.name} — Current: ${product.stock}", color = DT.SubText, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = isAddition, onClick = { isAddition = true }, label = { Text("Add") })
                    FilterChip(selected = !isAddition, onClick = { isAddition = false }, label = { Text("Remove") })
                }
                OutlinedTextField(delta, { delta = it }, label = { Text("Quantity", color = DT.SubText) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border, focusedTextColor = DT.OnSurface, unfocusedTextColor = DT.OnSurface))
                val qty = delta.toIntOrNull() ?: 0
                if (qty > 0) {
                    val newStock = if (isAddition) product.stock + qty else product.stock - qty
                    Text("New stock: $newStock", color = if (newStock < 0) DT.Red else DT.Green, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onAdjust(if (isAddition) (delta.toIntOrNull() ?: 0) else -(delta.toIntOrNull() ?: 0)) },
                enabled = (delta.toIntOrNull() ?: 0) > 0,
                colors = ButtonDefaults.buttonColors(containerColor = DT.Teal)
            ) { Text("Confirm") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = DT.SubText) } }
    )
}
