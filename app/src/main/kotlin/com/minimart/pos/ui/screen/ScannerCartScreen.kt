package com.minimart.pos.ui.screen

import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

private val TopGrad   = Color(0xFF006B5E)
private val TopGrad2  = Color(0xFF004D40)

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
    val searchResults: List<Product> by searchVm.results.collectAsState()

    LaunchedEffect(state.lastScannedProduct) {
        if (state.lastScannedProduct != null) { kotlinx.coroutines.delay(2500); vm.clearError() }
    }

    Box(modifier = Modifier.fillMaxSize().background(DT.Bg)) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top bar ───────────────────────────────────────────────────────
            Box(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                    .background(Brush.verticalGradient(listOf(DT.Teal, TopGrad, TopGrad2)))
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Circular back
                        Box(modifier = Modifier.size(40.dp).clip(CircleShape)
                            .background(Color.White.copy(0.15f))
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onBack),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("New Sale", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            AnimatedContent(
                                targetState = if (state.itemCount > 0)
                                    "${state.itemCount} item${if (state.itemCount != 1) "s" else ""}  •  $currency ${String.format("%.2f", state.total)}"
                                else "Scan or add products",
                                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                                label = "subtitle"
                            ) { subtitle ->
                                Text(subtitle, color = Color.White.copy(0.75f), fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        // Circular delete
                        AnimatedVisibility(visible = state.items.isNotEmpty()) {
                            Box(modifier = Modifier.size(40.dp).clip(CircleShape)
                                .background(Color.White.copy(0.15f))
                                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { vm.clearCart() },
                                contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Delete, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                        if (state.items.isEmpty()) Spacer(Modifier.width(40.dp))
                    }
                }
            }

            // ── Search bar ────────────────────────────────────────────────────
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it; searchVm.setQuery(it) },
                placeholder = { Text("Barcode or product name", color = DT.SubText) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = DT.SubText, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = { vm.processBarcode(searchText); searchText = ""; searchVm.clear() }) {
                            Icon(Icons.AutoMirrored.Filled.Send, null, tint = DT.Teal, modifier = Modifier.size(20.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border,
                    focusedTextColor = DT.OnSurface, unfocusedTextColor = DT.OnSurface,
                    cursorColor = DT.Teal, focusedContainerColor = DT.Surface, unfocusedContainerColor = DT.Surface
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
            )

            // ── Camera scanner ────────────────────────────────────────────────
            AnimatedVisibility(
                visible = showScanner && cameraPermission.status.isGranted,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .height(220.dp).clip(RoundedCornerShape(20.dp))) {
                    BarcodeScannerView(modifier = Modifier.fillMaxSize(), onBarcodeDetected = {
                        context.vibrateShort(); vm.processBarcode(it)
                        searchText = ""; searchVm.clear()
                        if (!continuousScan) showScanner = false
                    })
                    ScannerOverlay(modifier = Modifier.fillMaxSize())
                    IconButton(onClick = { showScanner = false },
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(Color.Black.copy(0.6f)),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                    if (continuousScan) {
                        Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(0.55f))
                            .padding(horizontal = 12.dp, vertical = 4.dp)) {
                            Text("∞ Continuous", color = DT.Teal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ── Search results dropdown ───────────────────────────────────────
            AnimatedVisibility(visible = searchResults.isNotEmpty() && searchText.isNotBlank()) {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp)).background(DT.Surface2)
                    .border(1.dp, DT.Border, RoundedCornerShape(16.dp))) {
                    Column {
                        searchResults.take(5).forEachIndexed { i, product ->
                            Row(modifier = Modifier.fillMaxWidth()
                                .clickable { vm.addToCart(product); searchText = ""; searchVm.clear() }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(DT.TealDim),
                                    contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Inventory2, null, tint = DT.Teal, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(product.name, color = DT.OnSurface, fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("$currency ${String.format("%.2f", product.price)}  •  Stock: ${product.stock}",
                                        color = DT.SubText, style = MaterialTheme.typography.labelSmall)
                                }
                                Box(Modifier.size(32.dp).clip(CircleShape).background(DT.Teal),
                                    contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                            if (i < searchResults.take(5).size - 1)
                                HorizontalDivider(color = DT.Border, thickness = 0.5.dp)
                        }
                    }
                }
            }

            // ── Error banner ──────────────────────────────────────────────────
            AnimatedVisibility(visible = state.error != null) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp)).background(DT.Red.copy(0.12f))
                    .border(1.dp, DT.Red.copy(0.3f), RoundedCornerShape(12.dp)).padding(12.dp)) {
                    Icon(Icons.Default.ErrorOutline, null, tint = DT.Red, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(state.error ?: "", color = DT.Red, style = MaterialTheme.typography.bodySmall)
                }
            }

            // ── Cart section ──────────────────────────────────────────────────
            if (state.items.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 6.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Cart", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(DT.Teal.copy(0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)) {
                        Text("${state.itemCount} item${if (state.itemCount != 1) "s" else ""}",
                            color = DT.Teal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.items, key = { it.product.id }) { item ->
                        DarkCartRow(item, currency,
                            onQtyChange = { vm.updateQuantity(item.product.id, it) },
                            onRemove = { vm.removeFromCart(item.product.id) })
                    }
                }
            } else {
                // Empty state
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(modifier = Modifier.size(100.dp).clip(CircleShape)
                            .background(Brush.radialGradient(listOf(DT.Teal.copy(0.15f), Color.Transparent))),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.ShoppingCart, null, modifier = Modifier.size(52.dp), tint = DT.Teal.copy(0.4f))
                        }
                        Text("Cart is empty", color = DT.OnSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Use the Scan button below or search above", color = DT.SubText.copy(0.7f),
                            fontSize = 13.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp))
                    }
                }
            }

            // ── Bottom total + checkout ───────────────────────────────────────
            AnimatedVisibility(visible = state.items.isNotEmpty(),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()) {
                Box(modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(Brush.verticalGradient(listOf(DT.Surface2, DT.Surface)))
                    .border(1.dp, DT.Border.copy(0.5f), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp)
                    .navigationBarsPadding().padding(bottom = 82.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("TOTAL", color = DT.SubText, fontSize = 11.sp,
                                fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                            Text("$currency ${String.format("%.2f", state.total)}", color = Color.White,
                                fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
                        }
                        Box(Modifier.width(1.dp).height(44.dp).background(DT.Border))
                        Spacer(Modifier.width(16.dp))
                        Button(onClick = onNavigateToCheckout,
                            modifier = Modifier.height(52.dp),
                            shape = RoundedCornerShape(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DT.Teal),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)) {
                            Icon(Icons.Default.ShoppingCart, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Checkout", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color.White)
                        }
                    }
                }
            }
        }

        // ── Bottom FAB: ∞ + Scan ─────────────────────────────────────────────
        Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .background(DT.Bg).navigationBarsPadding()
            .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                // ∞ toggle
                Box(modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (continuousScan) DT.Teal else DT.Surface2)
                    .border(1.dp, if (continuousScan) DT.Teal else DT.Border, RoundedCornerShape(16.dp))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { continuousScan = !continuousScan },
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.AllInclusive, "Continuous",
                        tint = if (continuousScan) Color.White else DT.SubText,
                        modifier = Modifier.size(24.dp))
                }
                // Scan pill
                Box(modifier = Modifier.weight(1f).height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (showScanner) DT.Red else DT.Teal)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                        if (!cameraPermission.status.isGranted) cameraPermission.launchPermissionRequest()
                        else showScanner = !showScanner
                    },
                    contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(if (showScanner) Icons.Default.Close else Icons.Default.QrCode, null,
                            tint = Color.White, modifier = Modifier.size(22.dp))
                        Text(if (showScanner) "Close Scanner" else "Scan Barcode",
                            color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

// ─── Cart Item Row ─────────────────────────────────────────────────────────────

@Composable
private fun DarkCartRow(item: CartItem, currency: String, onQtyChange: (Int) -> Unit, onRemove: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(20.dp))
        .background(Brush.horizontalGradient(listOf(DT.Surface, DT.Surface2)))
        .border(1.dp, DT.Border, RoundedCornerShape(20.dp))
        .padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Icon
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(DT.Teal.copy(0.25f), DT.TealDim))),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Inventory2, null, tint = DT.Teal, modifier = Modifier.size(28.dp))
            }
            // Info column
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Name + total
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.product.name, color = Color.White, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("$currency ${String.format("%.2f", item.lineTotal)}",
                        color = DT.Teal, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                }
                // Unit price + qty controls + remove
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$currency ${String.format("%.2f", item.product.price)}/unit",
                        color = DT.SubText, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    // Pill qty control
                    Box(modifier = Modifier.clip(RoundedCornerShape(50.dp))
                        .background(DT.Bg).border(1.dp, DT.Border, RoundedCornerShape(50.dp))) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onQtyChange(item.quantity - 1) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Remove, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                            Text(item.quantity.toString(), color = Color.White, fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 26.dp))
                            IconButton(onClick = { onQtyChange(item.quantity + 1) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Add, null, tint = DT.Teal, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    // Remove
                    Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(DT.Red.copy(0.12f))
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onRemove),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Delete, null, tint = DT.Red.copy(0.8f), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
