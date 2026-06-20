package com.minimart.pos.ui.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.minimart.pos.util.vibrateShort
import com.minimart.pos.ui.viewmodel.CartViewModel
import com.minimart.pos.ui.viewmodel.ProductSearchViewModel

private val PanelBg   = Color(0xFF0C2420)
private val PanelBg2  = Color(0xFF0A1E1B)
private val TopGrad1  = DT.Teal
private val TopGrad2  = Color(0xFF006B5E)
private val TopGrad3  = Color(0xFF004D40)

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
    var continuousScan by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var scanCount by remember { mutableIntStateOf(0) }
    var showScanFlash by remember { mutableStateOf(false) }
    val flashAlpha by animateFloatAsState(if (showScanFlash) 0.35f else 0f, animationSpec = tween(150), label = "flash")
    LaunchedEffect(scanCount) {
        if (showScanFlash) { kotlinx.coroutines.delay(150); showScanFlash = false }
    }

    val scanScope = rememberCoroutineScope()
    val searchResults: List<Product> by searchVm.results.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(DT.Bg)) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Teal top bar ──────────────────────────────────────────────────
            Box(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                    .background(Brush.verticalGradient(listOf(TopGrad1, TopGrad2, TopGrad3)))
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 22.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(42.dp).clip(CircleShape)
                            .background(Color.White.copy(0.18f))
                            .clickable(indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = onBack),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null,
                                tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("New Sale", color = Color.White,
                                fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            AnimatedContent(
                                targetState = if (state.itemCount > 0)
                                    "${state.itemCount} item${if (state.itemCount != 1) "s" else ""}  •  $currency ${String.format("%.2f", state.total)}"
                                else "Scan or search items",
                                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                                label = "sub"
                            ) { subtitle ->
                                Text(subtitle, color = Color.White.copy(0.78f), fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Box(modifier = Modifier.size(42.dp).clip(CircleShape)
                            .background(
                                if (state.items.isNotEmpty()) Color.White.copy(0.18f)
                                else Color.Transparent
                            )
                            .clickable(
                                enabled = state.items.isNotEmpty(),
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { vm.clearCart() },
                            contentAlignment = Alignment.Center) {
                            if (state.items.isNotEmpty())
                                Icon(Icons.Default.Delete, null,
                                    tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            // ── Search bar ────────────────────────────────────────────────────
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it; searchVm.setQuery(it) },
                placeholder = { Text("Barcode or product name", color = DT.SubText) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = DT.SubText.copy(0.6f), modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (searchText.isNotEmpty())
                        IconButton(onClick = { vm.processBarcode(searchText); searchText = ""; searchVm.clear() }) {
                            Icon(Icons.AutoMirrored.Filled.Send, null, tint = DT.Teal, modifier = Modifier.size(20.dp))
                        }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Teal.copy(0.4f),
                    focusedTextColor = DT.OnSurface, unfocusedTextColor = DT.OnSurface,
                    cursorColor = DT.Teal,
                    focusedContainerColor = DT.Surface, unfocusedContainerColor = DT.Surface
                ),
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 6.dp)
            )

            // ── Search results dropdown ───────────────────────────────────────
            AnimatedVisibility(visible = searchResults.isNotEmpty() && searchText.isNotBlank()) {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)
                    .clip(RoundedCornerShape(16.dp)).background(DT.Surface2)
                    .border(1.dp, DT.Border, RoundedCornerShape(16.dp))) {
                    Column {
                        searchResults.take(5).forEachIndexed { i, product ->
                            Row(modifier = Modifier.fillMaxWidth()
                                .clickable { vm.addToCart(product); searchText = ""; searchVm.clear() }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                                    .background(DT.TealDim), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Inventory2, null, tint = DT.Teal, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(product.name, color = DT.OnSurface, fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("$currency ${String.format("%.2f", product.price)}  •  ${product.stock} in stock",
                                        color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                                }
                                Icon(Icons.Default.Add, null, tint = DT.Teal, modifier = Modifier.size(20.dp))
                            }
                            if (i < minOf(searchResults.size, 5) - 1)
                                HorizontalDivider(color = DT.Border, thickness = 0.5.dp)
                        }
                    }
                }
            }

            // ── Camera scanner ────────────────────────────────────────────────
            AnimatedVisibility(
                visible = showScanner && cameraPermission.status.isGranted,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)
                    .height(200.dp).clip(RoundedCornerShape(20.dp))) {
                    BarcodeScannerView(modifier = Modifier.fillMaxSize(),
                        onBarcodeDetected = {
                            context.vibrateShort(); vm.processBarcode(it)
                            searchText = ""; searchVm.clear()
                            scanCount++
                            showScanFlash = true
                            if (!continuousScan) showScanner = false
                        })
                    ScannerOverlay(modifier = Modifier.fillMaxSize())
                    // Green flash overlay on scan
                    if (flashAlpha > 0f)
                        Box(Modifier.fillMaxSize().background(DT.Green.copy(flashAlpha)))
                    // Scan count badge
                    if (scanCount > 0)
                        Box(modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(DT.Teal.copy(0.85f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)) {
                            Text("$scanCount scanned", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    // Close button
                    IconButton(onClick = { showScanner = false },
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                        Box(Modifier.size(26.dp).clip(CircleShape).background(Color.Black.copy(0.6f)),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // ── Cart label ────────────────────────────────────────────────────
            if (state.items.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth()
                    .padding(start = 18.dp, end = 14.dp, top = 10.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Cart", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Box(modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .background(DT.Surface2).border(1.dp, DT.Border, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)) {
                        Text("${state.itemCount} item${if (state.itemCount != 1) "s" else ""}",
                            color = DT.Teal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ── Cart items ────────────────────────────────────────────────────
            if (state.items.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.items, key = { it.product.id }) { item ->
                        CartItemCard(item, currency,
                            onQtyChange = { vm.updateQuantity(item.product.id, it) },
                            onRemove    = { vm.removeFromCart(item.product.id) })
                    }
                }
            } else {
                // Empty state
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.ShoppingCart, null,
                            modifier = Modifier.size(64.dp), tint = DT.SubText.copy(0.25f))
                        Text("Cart is empty", color = DT.SubText.copy(0.7f),
                            fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text("Scan a barcode or search above",
                            color = DT.SubText.copy(0.45f), fontSize = 12.sp)
                    }
                }
            }

            // ── Bottom panel: total + checkout + scan FABs ────────────────────
            Box(modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(Brush.verticalGradient(listOf(PanelBg, PanelBg2)))
                .navigationBarsPadding()
                .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Total + checkout
                    Row(modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("TOTAL", color = DT.SubText, fontSize = 11.sp,
                                fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                            Text("$currency ${String.format("%.2f", state.total)}",
                                color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
                        }
                        // Checkout pill button
                        Button(
                            onClick = onNavigateToCheckout,
                            modifier = Modifier.height(52.dp),
                            enabled = state.items.isNotEmpty(),
                            shape = RoundedCornerShape(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DT.Teal),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                        ) {
                            Icon(Icons.Default.ShoppingCart, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Checkout", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color.White)
                        }
                    }
                    // ∞ + Scan row
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        // ∞ tile
                        Box(modifier = Modifier.size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (continuousScan) DT.Teal else DT.Surface2)
                            .border(1.dp, if (continuousScan) DT.Teal else DT.Border, RoundedCornerShape(16.dp))
                            .clickable(indication = null,
                                interactionSource = remember { MutableInteractionSource() }) { continuousScan = !continuousScan },
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.AllInclusive, null,
                                tint = if (continuousScan) Color.White else DT.SubText,
                                modifier = Modifier.size(24.dp))
                        }
                        // Scan pill
                        Box(modifier = Modifier.weight(1f).height(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (showScanner) DT.Red else DT.Teal)
                            .clickable(indication = null,
                                interactionSource = remember { MutableInteractionSource() }) {
                                if (!cameraPermission.status.isGranted) cameraPermission.launchPermissionRequest()
                                else showScanner = !showScanner
                            },
                            contentAlignment = Alignment.Center) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(
                                    if (showScanner) Icons.Default.Close else Icons.Default.QrCode, null,
                                    tint = Color.White, modifier = Modifier.size(22.dp))
                                Text(
                                    if (showScanner) "Close Scanner" else "Scan Barcode",
                                    color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Cart Item Card ────────────────────────────────────────────────────────────

@Composable
private fun CartItemCard(
    item: CartItem, currency: String,
    onQtyChange: (Int) -> Unit, onRemove: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(18.dp))
        .background(DT.Surface)
        .border(1.dp, DT.Border, RoundedCornerShape(18.dp))
        .padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Product icon
            Box(modifier = Modifier.size(54.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(DT.Teal.copy(0.3f), DT.TealDim))),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Inventory2, null, tint = DT.Teal, modifier = Modifier.size(26.dp))
            }
            // Name + controls
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                // Name + price
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.product.name, color = Color.White, fontWeight = FontWeight.Bold,
                        fontSize = 15.sp, modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("$currency ${String.format("%.2f", item.lineTotal)}",
                        color = DT.Teal, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                }
                // Unit price + qty pill + delete
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (item.product.isWeighed && item.weightKg > 0)
                        "${String.format("%.3f", item.weightKg)} kg @ $currency ${String.format("%.2f", item.product.pricePerKg)}/kg"
                    else "$currency ${String.format("%.2f", item.product.price)}/unit",
                        color = DT.SubText, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    if (item.product.isWeighed && item.weightKg > 0) {
                        // Weighed items: quantity is meaningless (weight comes from scale scan).
                        // Show a read-only badge instead of a +/- stepper that would silently do nothing.
                        Box(modifier = Modifier.clip(RoundedCornerShape(50.dp))
                            .background(DT.Teal.copy(0.15f))
                            .border(1.dp, DT.Teal.copy(0.35f), RoundedCornerShape(50.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text("Re-scan to adjust", color = DT.Teal, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                    // Qty pill (dark bg matching reference)
                    Box(modifier = Modifier.clip(RoundedCornerShape(50.dp))
                        .background(Color(0xFF0A1410))
                        .border(1.dp, DT.Border, RoundedCornerShape(50.dp))) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onQtyChange(item.quantity - 1) },
                                modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Remove, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                            Text(item.quantity.toString(), color = Color.White,
                                fontWeight = FontWeight.ExtraBold, fontSize = 15.sp,
                                textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 24.dp))
                            IconButton(onClick = { onQtyChange(item.quantity + 1) },
                                modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                    }
                    Spacer(Modifier.width(8.dp))
                    // Red circle trash
                    Box(modifier = Modifier.size(36.dp).clip(CircleShape)
                        .background(DT.Red)
                        .clickable(indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onRemove),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Delete, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
