package com.minimart.pos.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
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
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.minimart.pos.data.entity.CartItem
import com.minimart.pos.data.entity.Product
import com.minimart.pos.data.repository.ProductRepository
import com.minimart.pos.scanner.BarcodeScannerView
import com.minimart.pos.scanner.ScannerOverlay
import com.minimart.pos.ui.theme.Brand500
import com.minimart.pos.ui.theme.ErrorRed
import com.minimart.pos.ui.theme.SuccessGreen
import com.minimart.pos.ui.viewmodel.CartViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import javax.inject.Inject

// ─── Search ViewModel ─────────────────────────────────────────────────────────

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProductSearchViewModel @Inject constructor(
    private val repo: ProductRepository
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val results: StateFlow<List<Product>> = _query
        .debounce(200)
        .flatMapLatest { q ->
            if (q.isBlank()) flowOf(emptyList())
            else repo.searchProducts(q)
        }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(3000), emptyList())

    fun setQuery(q: String) { _query.value = q }
    fun clear() { _query.value = "" }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScannerCartScreen(
    onNavigateToCheckout: () -> Unit,
    onBack: () -> Unit,
    vm: CartViewModel = hiltViewModel(),
    searchVm: ProductSearchViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsState()
    val currency by vm.currency.collectAsState()
    val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)
    var showScanner by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    val searchResults by searchVm.results.collectAsState()

    LaunchedEffect(state.lastScannedProduct) {
        if (state.lastScannedProduct != null) {
            kotlinx.coroutines.delay(1500)
            vm.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("New Sale", fontWeight = FontWeight.Bold)
                        if (state.itemCount > 0)
                            Text("${state.itemCount} items • $currency ${String.format("%.2f", state.total)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f))
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Brand500, titleContentColor = Color.White, navigationIconContentColor = Color.White),
                actions = {
                    if (state.items.isNotEmpty()) {
                        IconButton(onClick = { vm.clearCart() }) {
                            Icon(Icons.Default.DeleteSweep, "Clear cart", tint = Color.White)
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (state.items.isNotEmpty()) {
                Surface(shadowElevation = 8.dp) {
                    Column(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        state.items.forEach { item ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text("${item.product.name} ×${item.quantity}",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall)
                                Text("$currency ${String.format("%.2f", item.lineTotal)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium)
                            }
                        }
                        HorizontalDivider()
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("TOTAL", fontWeight = FontWeight.Bold)
                            Text("$currency ${String.format("%.2f", state.total)}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Brand500)
                        }
                        Button(
                            onClick = onNavigateToCheckout,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Brand500)
                        ) {
                            Icon(Icons.Default.ShoppingCartCheckout, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Checkout  •  $currency ${String.format("%.2f", state.total)}", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // ── Scanner / Search ─────────────────────────────────────────────
            AnimatedContent(targetState = showScanner, label = "scanner") { scanning ->
                if (scanning && cameraPermission.status.isGranted) {
                    Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                        BarcodeScannerView(modifier = Modifier.fillMaxSize(), onBarcodeDetected = {
                            vm.processBarcode(it)
                            searchVm.clear()
                            searchText = ""
                        })
                        ScannerOverlay(modifier = Modifier.fillMaxSize())
                        IconButton(onClick = { showScanner = false }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                            Icon(Icons.Default.Close, null, tint = Color.White)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchText,
                            onValueChange = { searchText = it; searchVm.setQuery(it) },
                            placeholder = { Text("Barcode or product name", maxLines = 1) },
                            leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(20.dp)) },
                            modifier = Modifier.weight(1f).height(56.dp),
                            singleLine = true,
                            shape = RoundedCornerShape(28.dp),
                            trailingIcon = {
                                if (searchText.isNotEmpty()) {
                                    IconButton(onClick = {
                                        vm.processBarcode(searchText)
                                        searchText = ""
                                        searchVm.clear()
                                    }) { Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(20.dp)) }
                                }
                            }
                        )
                        FilledIconButton(
                            onClick = {
                                if (!cameraPermission.status.isGranted) cameraPermission.launchPermissionRequest()
                                else showScanner = true
                            },
                            modifier = Modifier.size(56.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Brand500),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.QrCodeScanner, null, tint = Color.White, modifier = Modifier.size(26.dp))
                        }
                    }
                }
            }

            // ── Search suggestions dropdown ──────────────────────────────────
            if (searchResults.isNotEmpty() && searchText.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    shape = RoundedCornerShape(8.dp),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    searchResults.take(6).forEach { product ->
                        ListItem(
                            headlineContent = { Text(product.name, fontWeight = FontWeight.Medium) },
                            supportingContent = { Text("$currency ${String.format("%.2f", product.price)} • Stock: ${product.stock}") },
                            trailingContent = {
                                FilledIconButton(
                                    onClick = { vm.addToCart(product); searchText = ""; searchVm.clear() },
                                    modifier = Modifier.size(36.dp),
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Brand500)
                                ) { Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }

            // ── Feedback banner ──────────────────────────────────────────────
            AnimatedVisibility(visible = state.lastScannedProduct != null || state.error != null) {
                val isError = state.error != null
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(if (isError) ErrorRed else SuccessGreen)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(if (isError) Icons.Default.Error else Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(state.error ?: "Added: ${state.lastScannedProduct?.name}", color = Color.White)
                }
            }

            // ── Cart items ───────────────────────────────────────────────────
            if (state.isEmpty) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.ShoppingCart, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        Text("Cart is empty", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                        Text("Scan a barcode or search by name above", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.items, key = { it.product.id }) { item ->
                        CartItemRow(item = item, currency = currency,
                            onQuantityChange = { vm.updateQuantity(item.product.id, it) },
                            onRemove = { vm.removeFromCart(item.product.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CartItemRow(item: CartItem, currency: String, onQuantityChange: (Int) -> Unit, onRemove: () -> Unit) {
    Card(shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Brand500.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Inventory2, null, tint = Brand500, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.product.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Text("$currency ${String.format("%.2f", item.product.price)} each", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilledIconButton(onClick = { onQuantityChange(item.quantity - 1) }, modifier = Modifier.size(32.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) { Icon(Icons.Default.Remove, null, modifier = Modifier.size(16.dp)) }
                Text(item.quantity.toString(), fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 28.dp), style = MaterialTheme.typography.bodyLarge)
                FilledIconButton(onClick = { onQuantityChange(item.quantity + 1) }, modifier = Modifier.size(32.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Brand500)
                ) { Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text("$currency ${String.format("%.2f", item.lineTotal)}", fontWeight = FontWeight.Bold, color = Brand500)
                IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
