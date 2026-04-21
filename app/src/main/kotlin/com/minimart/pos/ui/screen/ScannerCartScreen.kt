package com.minimart.pos.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.minimart.pos.data.entity.CartItem
import com.minimart.pos.data.entity.Product
import com.minimart.pos.scanner.BarcodeScannerView
import com.minimart.pos.scanner.ScannerOverlay
import com.minimart.pos.ui.theme.DT
import com.minimart.pos.ui.viewmodel.CartViewModel
import com.minimart.pos.ui.viewmodel.ProductSearchViewModel

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
    val context = LocalContext.current
    val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)
    var showScanner by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    val searchResults: List<Product> by searchVm.results.collectAsState()

    fun vibrate() {
        val vib = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vib.vibrate(android.os.VibrationEffect.createOneShot(60, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION") vib.vibrate(60)
        }
    }

    LaunchedEffect(state.lastScannedProduct) {
        if (state.lastScannedProduct != null) { kotlinx.coroutines.delay(1500); vm.clearError() }
    }

    Box(modifier = Modifier.fillMaxSize().background(DT.Bg)) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Teal top bar ──────────────────────────────────────────────────
            Box(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                    .background(Brush.horizontalGradient(listOf(DT.Teal, Color(0xFF00695C))))
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                        }
                        Spacer(Modifier.weight(1f))
                        Text("New Sale", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Spacer(Modifier.weight(1f))
                        if (state.items.isNotEmpty()) {
                            IconButton(onClick = { vm.clearCart() }) {
                                Icon(Icons.Default.Delete, null, tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                        } else {
                            Spacer(Modifier.width(48.dp))
                        }
                    }
                    if (state.itemCount > 0) {
                        Text(
                            "${state.itemCount} items • $currency ${String.format("%.2f", state.total)}",
                            color = Color.White.copy(0.85f),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }

            // ── Search bar ────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it; searchVm.setQuery(it) },
                    placeholder = { Text("Barcode or product name", color = DT.SubText, maxLines = 1) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = DT.SubText, modifier = Modifier.size(20.dp)) },
                    trailingIcon = {
                        if (searchText.isNotEmpty()) {
                            IconButton(onClick = {
                                vm.processBarcode(searchText); searchText = ""; searchVm.clear()
                            }) {
                                Icon(Icons.AutoMirrored.Filled.Send, null, tint = DT.Teal, modifier = Modifier.size(20.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border,
                        focusedTextColor = DT.OnSurface, unfocusedTextColor = DT.OnSurface,
                        cursorColor = DT.Teal, focusedContainerColor = DT.Surface, unfocusedContainerColor = DT.Surface
                    ),
                    modifier = Modifier.weight(1f)
                )
                // Scan toggle button
                FilledIconButton(
                    onClick = {
                        if (!cameraPermission.status.isGranted) cameraPermission.launchPermissionRequest()
                        else showScanner = !showScanner
                    },
                    modifier = Modifier.size(52.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (showScanner) DT.Surface2 else DT.Teal
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        if (showScanner) Icons.Default.Close else Icons.Default.QrCode,
                        null, tint = Color.White, modifier = Modifier.size(24.dp)
                    )
                }
            }

            // ── Compact inline scanner (like Add Product dialog) ──────────────
            AnimatedVisibility(visible = showScanner && cameraPermission.status.isGranted) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    BarcodeScannerView(
                        modifier = Modifier.fillMaxSize(),
                        onBarcodeDetected = {
                            vibrate()
                            vm.processBarcode(it)
                            searchText = ""
                            searchVm.clear()
                            showScanner = false
                        }
                    )
                    // Corner bracket overlay
                    ScannerOverlay(modifier = Modifier.fillMaxSize())
                    // Close X button top-right
                    IconButton(
                        onClick = { showScanner = false },
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(28.dp).clip(CircleShape).background(Color.Black.copy(0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // ── Search suggestions ────────────────────────────────────────────
            if (searchResults.isNotEmpty() && searchText.isNotBlank()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DT.Surface2)
                        .border(1.dp, DT.Border, RoundedCornerShape(12.dp))
                ) {
                    Column {
                        searchResults.take(5).forEach { product: Product ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(product.name, color = DT.OnSurface, fontWeight = FontWeight.Medium,
                                        style = MaterialTheme.typography.bodyMedium)
                                    Text("$currency ${String.format("%.2f", product.price)} • Stock: ${product.stock}",
                                        color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                                }
                                FilledIconButton(
                                    onClick = { vm.addToCart(product); searchText = ""; searchVm.clear() },
                                    modifier = Modifier.size(32.dp),
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = DT.Teal)
                                ) {
                                    Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                            HorizontalDivider(color = DT.Border, thickness = 0.5.dp)
                        }
                    }
                }
            }

            // ── Error banner ──────────────────────────────────────────────────
            AnimatedVisibility(visible = state.error != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp)).background(DT.Red.copy(0.15f)).padding(10.dp)
                ) {
                    Icon(Icons.Default.Error, null, tint = DT.Red, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(state.error ?: "", color = DT.Red, style = MaterialTheme.typography.bodySmall)
                }
            }

            // ── Cart label ────────────────────────────────────────────────────
            if (state.items.isNotEmpty()) {
                Text("Cart", color = DT.OnSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            }

            // ── Cart items ────────────────────────────────────────────────────
            if (state.isEmpty) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ShoppingCart, null, modifier = Modifier.size(72.dp), tint = DT.SubText.copy(0.3f))
                        Spacer(Modifier.height(12.dp))
                        Text("Cart is empty", color = DT.SubText, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("Scan or search a product", color = DT.SubText.copy(0.6f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.items, key = { it.product.id }) { item ->
                        DarkCartRow(item, currency,
                            onQtyChange = { vm.updateQuantity(item.product.id, it) },
                            onRemove = { vm.removeFromCart(item.product.id) })
                    }
                }
            }

            // ── Bottom total + checkout ───────────────────────────────────────
            if (state.items.isNotEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .background(DT.Surface2).padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("TOTAL", color = DT.SubText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("$currency ${String.format("%.2f", state.total)}", color = Color.White,
                                fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                        }
                        Button(
                            onClick = onNavigateToCheckout,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DT.Teal)
                        ) {
                            Icon(Icons.Default.ShoppingCart, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Checkout", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                        }
                    }
                }
            }
        }

    }
}

@Composable
private fun DarkCartRow(
    item: CartItem,
    currency: String,
    onQtyChange: (Int) -> Unit,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DT.Surface)
            .border(1.dp, DT.Border, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("${item.product.name} x${item.quantity}", color = DT.OnSurface, fontWeight = FontWeight.SemiBold)
                    Text("$currency ${String.format("%.2f", item.lineTotal)}", color = DT.TealLight,
                        fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Box(
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(DT.Surface2),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, null, tint = DT.SubText, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape)
                        .background(DT.Surface2).border(1.dp, DT.Border, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = { onQtyChange(item.quantity - 1) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Remove, null, tint = DT.OnSurface, modifier = Modifier.size(18.dp))
                    }
                }
                Text(item.quantity.toString(), color = DT.OnSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape)
                        .background(DT.Surface2).border(1.dp, DT.Border, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = { onQtyChange(item.quantity + 1) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Add, null, tint = DT.OnSurface, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
