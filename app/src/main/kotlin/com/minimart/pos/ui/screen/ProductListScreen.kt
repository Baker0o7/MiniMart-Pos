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
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.minimart.pos.data.entity.Product
import com.minimart.pos.scanner.BarcodeScannerView
import com.minimart.pos.scanner.ScannerOverlay
import com.minimart.pos.ui.theme.DT
import com.minimart.pos.ui.viewmodel.ProductViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductListScreen(
    onBack: () -> Unit,
    vm: ProductViewModel = hiltViewModel()
) {
    val products by vm.products.collectAsState()
    val categories by vm.categories.collectAsState()
    val selectedCat by vm.selectedCategory.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()
    val uiState by vm.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editProduct by remember { mutableStateOf<Product?>(null) }

    LaunchedEffect(uiState.successMessage) {
        if (uiState.successMessage != null) { kotlinx.coroutines.delay(2000); vm.clearMessages() }
    }

    Box(modifier = Modifier.fillMaxSize().background(DT.Bg)) {
        Column {
            // ── Top bar ──────────────────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = DT.Teal)
                }
                Text("Products", color = DT.Teal, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(DT.TealDim),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, null, tint = DT.Teal, modifier = Modifier.size(22.dp))
                    }
                }
            }

            // ── Search ────────────────────────────────────────────────────────
            OutlinedTextField(
                value = searchQuery,
                onValueChange = vm::setSearchQuery,
                placeholder = { Text("Search products...", color = DT.SubText) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = DT.SubText, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) IconButton(onClick = { vm.setSearchQuery("") }) {
                        Icon(Icons.Default.Close, null, tint = DT.SubText)
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
            Spacer(Modifier.height(10.dp))

            // ── Category chips ────────────────────────────────────────────────
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { DarkFilterChip("All", selectedCat == null) { vm.setCategory(null) } }
                items(categories) { cat -> DarkFilterChip(cat, selectedCat == cat) { vm.setCategory(cat) } }
            }
            Spacer(Modifier.height(8.dp))

            uiState.successMessage?.let {
                Text(it, color = DT.Green, modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelSmall)
            }

            // ── Product list ──────────────────────────────────────────────────
            LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(products, key = { it.id }) { product ->
                    DarkProductRow(product = product,
                        onEdit = { editProduct = it; showAddDialog = true },
                        onDelete = { vm.deleteProduct(product.id) })
                }
                if (products.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                            Text(if (searchQuery.isNotBlank()) "No results for \"$searchQuery\"" else "No products yet", color = DT.SubText)
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddEditProductDialog(product = editProduct,
            onDismiss = { showAddDialog = false; editProduct = null },
            onSave = { vm.saveProduct(it); showAddDialog = false; editProduct = null })
    }
}

@Composable
private fun DarkFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) DT.Teal else DT.Surface2)
            .border(1.dp, if (selected) DT.Teal else DT.Border, RoundedCornerShape(20.dp))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(label, color = if (selected) Color.White else DT.SubText,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun DarkProductRow(product: Product, onEdit: (Product) -> Unit, onDelete: () -> Unit) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val stockColor = when {
        product.stock == 0 -> DT.Red
        product.stock <= product.lowStockThreshold -> DT.Amber
        else -> DT.Green
    }
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(DT.Surface).border(1.dp, DT.Border, RoundedCornerShape(14.dp))
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Product image placeholder
            Box(modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(DT.TealDim),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.ShoppingCart, null, tint = DT.Teal, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row {
                    Text(product.name.substringBeforeLast(" "), color = DT.OnSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    if (product.name.contains(" ")) {
                        Text(" ${product.name.substringAfterLast(" ")}", color = DT.SubText, fontSize = 15.sp)
                    }
                }
                Text("${product.stock} stock", color = DT.SubText, style = MaterialTheme.typography.labelSmall)
            }
            Text("KES ${String.format("%.0f", product.price)}", color = DT.OnSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.width(10.dp))
            // Action icon
            if (product.stock > 0) {
                IconButton(onClick = { onEdit(product) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Edit, null, tint = DT.Teal, modifier = Modifier.size(20.dp))
                }
            } else {
                IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, null, tint = DT.Red, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
    if (showDeleteConfirm) {
        AlertDialog(onDismissRequest = { showDeleteConfirm = false }, containerColor = DT.Surface,
            title = { Text("Delete?", color = DT.OnSurface) },
            text = { Text(product.name, color = DT.SubText) },
            confirmButton = { TextButton(onClick = { onDelete(); showDeleteConfirm = false }) { Text("Delete", color = DT.Red) } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel", color = DT.SubText) } })
    }
}

// ─── Add/Edit Product Dialog ──────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun AddEditProductDialog(product: Product?, onDismiss: () -> Unit, onSave: (Product) -> Unit) {
    var barcode    by remember { mutableStateOf(product?.barcode ?: "") }
    var name       by remember { mutableStateOf(product?.name ?: "") }
    var price      by remember { mutableStateOf(product?.price?.toString() ?: "") }
    var costPrice  by remember { mutableStateOf(product?.costPrice?.toString() ?: "") }
    var stock      by remember { mutableStateOf(product?.stock?.toString() ?: "") }
    var category   by remember { mutableStateOf(product?.category ?: "") }
    var unit       by remember { mutableStateOf(product?.unit ?: "pcs") }
    var sku        by remember { mutableStateOf(product?.sku ?: "") }
    var showScanner by remember { mutableStateOf(false) }
    val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DT.Surface,
        title = { Text(if (product == null) "Add Product" else "Edit Product", color = DT.OnSurface, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    DarkField(barcode, { barcode = it }, "Barcode *", modifier = Modifier.weight(1f))
                    FilledIconButton(onClick = {
                        if (!cameraPermission.status.isGranted) cameraPermission.launchPermissionRequest()
                        else showScanner = !showScanner
                    }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = DT.Teal)) {
                        Icon(Icons.Default.QrCode, null, tint = Color.White)
                    }
                }
                if (showScanner && cameraPermission.status.isGranted) {
                    Box(modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(10.dp))) {
                        BarcodeScannerView(modifier = Modifier.fillMaxSize(), lifecycleOwner = lifecycleOwner,
                            onBarcodeDetected = { barcode = it; showScanner = false })
                        ScannerOverlay(modifier = Modifier.fillMaxSize())
                        IconButton(onClick = { showScanner = false }, modifier = Modifier.align(Alignment.TopEnd)) {
                            Icon(Icons.Default.Close, null, tint = Color.White)
                        }
                    }
                }
                DarkField(name, { name = it }, "Product Name *")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DarkField(price, { price = it }, "Price (KES)", Modifier.weight(1f), KeyboardType.Decimal)
                    DarkField(costPrice, { costPrice = it }, "Cost Price", Modifier.weight(1f), KeyboardType.Decimal)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DarkField(stock, { stock = it }, "Stock", Modifier.weight(1f), KeyboardType.Number)
                    DarkField(unit, { unit = it }, "Unit", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DarkField(category, { category = it }, "Category", Modifier.weight(1f))
                    DarkField(sku, { sku = it }, "SKU", Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(Product(id = product?.id ?: 0L, barcode = barcode.trim(), sku = sku.trim(),
                    name = name.trim(), price = price.toDoubleOrNull() ?: 0.0,
                    costPrice = costPrice.toDoubleOrNull() ?: 0.0, stock = stock.toIntOrNull() ?: 0,
                    category = category.ifBlank { "General" }, unit = unit.ifBlank { "pcs" })) },
                enabled = barcode.isNotBlank() && name.isNotBlank() && price.toDoubleOrNull() != null,
                colors = ButtonDefaults.buttonColors(containerColor = DT.Teal)
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = DT.SubText) } }
    )
}

@Composable
private fun DarkField(value: String, onValueChange: (String) -> Unit, label: String,
    modifier: Modifier = Modifier.fillMaxWidth(), keyboardType: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label, color = DT.SubText, style = MaterialTheme.typography.labelSmall) },
        singleLine = true, modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border,
            focusedTextColor = DT.OnSurface, unfocusedTextColor = DT.OnSurface, cursorColor = DT.Teal,
            focusedContainerColor = DT.Bg, unfocusedContainerColor = DT.Bg))
}
