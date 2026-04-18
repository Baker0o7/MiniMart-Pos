package com.minimart.pos.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.Logout
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.minimart.pos.data.entity.Product
import com.minimart.pos.scanner.BarcodeScannerView
import com.minimart.pos.scanner.ScannerOverlay
import com.minimart.pos.ui.theme.Brand500
import com.minimart.pos.ui.theme.ErrorRed
import com.minimart.pos.ui.theme.SuccessGreen
import com.minimart.pos.ui.viewmodel.ProductViewModel

// ─── Product List ─────────────────────────────────────────────────────────────

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
        if (uiState.successMessage != null) {
            kotlinx.coroutines.delay(2000)
            vm.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Products", fontWeight = FontWeight.Bold) },
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
            // Search
            OutlinedTextField(
                value = searchQuery,
                onValueChange = vm::setSearchQuery,
                label = { Text("Search products…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { vm.setSearchQuery("") }) { Icon(Icons.Default.Clear, null) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            )
            // Category filter
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedCat == null,
                        onClick = { vm.setCategory(null) },
                        label = { Text("All") }
                    )
                }
                items(categories) { cat ->
                    FilterChip(selected = selectedCat == cat, onClick = { vm.setCategory(cat) }, label = { Text(cat) })
                }
            }

            uiState.successMessage?.let {
                Text(it, color = SuccessGreen, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
            }

            LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(products, key = { it.id }) { product ->
                    ProductRow(
                        product = product,
                        onEdit = { editProduct = it; showAddDialog = true },
                        onDelete = { vm.deleteProduct(product.id) }
                    )
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
}

@Composable
private fun ProductRow(product: Product, onEdit: (Product) -> Unit, onDelete: () -> Unit) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(product.name, fontWeight = FontWeight.SemiBold)
                Text(product.barcode, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(product.category, style = MaterialTheme.typography.labelSmall, color = Brand500)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("KES ${String.format("%.2f", product.price)}", fontWeight = FontWeight.Bold, color = Brand500)
                val stockColor = when {
                    product.stock == 0 -> ErrorRed
                    product.stock <= product.lowStockThreshold -> MaterialTheme.colorScheme.tertiary
                    else -> SuccessGreen
                }
                Text("Stock: ${product.stock}", style = MaterialTheme.typography.labelSmall, color = stockColor, fontWeight = FontWeight.Medium)
            }
            IconButton(onClick = { onEdit(product) }) { Icon(Icons.Default.Edit, null, tint = Brand500, modifier = Modifier.size(20.dp)) }
            IconButton(onClick = { showDeleteConfirm = true }) { Icon(Icons.Default.Delete, null, tint = ErrorRed, modifier = Modifier.size(20.dp)) }
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

// ─── Add/Edit Dialog ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun AddEditProductDialog(product: Product?, onDismiss: () -> Unit, onSave: (Product) -> Unit) {
    var barcode by remember { mutableStateOf(product?.barcode ?: "") }
    var name by remember { mutableStateOf(product?.name ?: "") }
    var price by remember { mutableStateOf(product?.price?.toString() ?: "") }
    var costPrice by remember { mutableStateOf(product?.costPrice?.toString() ?: "") }
    var stock by remember { mutableStateOf(product?.stock?.toString() ?: "") }
    var category by remember { mutableStateOf(product?.category ?: "") }
    var unit by remember { mutableStateOf(product?.unit ?: "pcs") }
    var showBarcodeScanner by remember { mutableStateOf(false) }
    val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)
    // Use Activity lifecycle owner — Dialogs have their own context that breaks CameraX
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (product == null) "Add Product" else "Edit Product", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Barcode field with scan button
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = barcode,
                        onValueChange = { barcode = it },
                        label = { Text("Barcode *") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        leadingIcon = { Icon(Icons.Default.QrCode, null) }
                    )
                    FilledIconButton(
                        onClick = {
                            if (!cameraPermission.status.isGranted) cameraPermission.launchPermissionRequest()
                            else showBarcodeScanner = true
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Brand500)
                    ) { Icon(Icons.Default.QrCode, null, tint = Color.White) }
                }
                // Inline barcode scanner
                if (showBarcodeScanner && cameraPermission.status.isGranted) {
                    Box(modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(8.dp))) {
                        BarcodeScannerView(
                            modifier = Modifier.fillMaxSize(),
                            lifecycleOwner = lifecycleOwner,
                            onBarcodeDetected = { scanned ->
                                barcode = scanned
                                showBarcodeScanner = false
                            }
                        )
                        ScannerOverlay(modifier = Modifier.fillMaxSize())
                        IconButton(onClick = { showBarcodeScanner = false }, modifier = Modifier.align(Alignment.TopEnd)) {
                            Icon(Icons.Default.Close, null, tint = Color.White)
                        }
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text("Product Name *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(price, { price = it }, label = { Text("Price (KES)") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                        singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(costPrice, { costPrice = it }, label = { Text("Cost Price") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                        singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(stock, { stock = it }, label = { Text("Stock") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(unit, { unit = it }, label = { Text("Unit") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(category, { category = it }, label = { Text("Category") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(Product(
                        id = product?.id ?: 0L,
                        barcode = barcode.trim(),
                        name = name.trim(),
                        price = price.toDoubleOrNull() ?: 0.0,
                        costPrice = costPrice.toDoubleOrNull() ?: 0.0,
                        stock = stock.toIntOrNull() ?: 0,
                        category = category.ifBlank { "General" },
                        unit = unit.ifBlank { "pcs" }
                    ))
                },
                enabled = barcode.isNotBlank() && name.isNotBlank() && price.toDoubleOrNull() != null
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
