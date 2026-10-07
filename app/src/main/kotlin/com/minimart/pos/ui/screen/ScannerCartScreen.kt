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
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
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
import com.minimart.pos.util.PluDecoder

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
    canAddProducts: Boolean = false,
    vm: CartViewModel = hiltViewModel(),
    searchVm: ProductSearchViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsState()
    var showClearConfirm by remember { mutableStateOf(false) }
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = DT.Surface,
            title = { Text("Clear the cart?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("All ${state.items.size} item(s) will be removed.", color = DT.SubText) },
            confirmButton = {
                Button(onClick = { showClearConfirm = false; vm.clearCart() },
                    colors = ButtonDefaults.buttonColors(containerColor = DT.Red, contentColor = Color.White)) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("Keep items", color = DT.SubText) } }
        )
    }
    val currency by vm.currency.collectAsState()
    val context = LocalContext.current
    val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)
    var showScanner by remember { mutableStateOf(false) }
    var continuousScan by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }
    var hasTorch by remember { mutableStateOf(false) }
    LaunchedEffect(showScanner) { if (!showScanner) torchOn = false }
    var searchText by remember { mutableStateOf("") }
    var scanCount by remember { mutableIntStateOf(0) }
    var showScanFlash by remember { mutableStateOf(false) }
    val flashAlpha by animateFloatAsState(if (showScanFlash) 0.35f else 0f, animationSpec = tween(150), label = "flash")
    LaunchedEffect(scanCount) {
        if (showScanFlash) { kotlinx.coroutines.delay(150); showScanFlash = false }
    }

    val scanScope = rememberCoroutineScope()
    val searchResults: List<Product> by searchVm.results.collectAsState()

    // Dialogs: tap a quantity to type it; weighed products need a weight (no scale ticket).
    var qtyEditItem by remember { mutableStateOf<CartItem?>(null) }
    var weightEdit by remember { mutableStateOf<Pair<Product, Double>?>(null) }
    val weightRequest: Pair<Product, Double>? = weightEdit ?: state.pendingWeighProduct?.let { p ->
        p to (state.items.firstOrNull { it.product.id == p.id }?.weightKg ?: 0.0)
    }

    // Scan / add feedback: errors (unknown barcode, out of stock, max stock) used to be dropped
    // silently, so a failed scan looked like nothing had happened.
    val bannerText: String? = state.error ?: state.lastScannedProduct?.let { "Added ${it.name}" }
    val bannerIsError = state.error != null
    var shownBanner by remember { mutableStateOf("") }
    var shownIsError by remember { mutableStateOf(false) }
    LaunchedEffect(bannerText) { if (bannerText != null) { shownBanner = bannerText; shownIsError = bannerIsError } }
    LaunchedEffect(state.error, state.lastScannedProduct) {
        when {
            state.error != null -> { kotlinx.coroutines.delay(3500); vm.clearError() }
            state.lastScannedProduct != null -> { kotlinx.coroutines.delay(1500); vm.clearError() }
        }
    }

    // Enter / search key: exact barcode -> look it up, otherwise take the top match.
    val submitSearch: () -> Unit = submit@{
        val q = searchText.trim()
        if (q.isEmpty()) return@submit
        val first = searchResults.firstOrNull()
        val looksLikeBarcode = q.length >= 6 && q.all { it.isDigit() }
        if (!looksLikeBarcode && first != null) vm.addToCart(first) else vm.processBarcode(q)
        searchText = ""; searchVm.clear()
    }

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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
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
                            ) { showClearConfirm = true },
                            contentAlignment = Alignment.Center) {
                            if (state.items.isNotEmpty())
                                Icon(Icons.Default.Delete, null,
                                    tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            AnimatedVisibility(visible = bannerText != null,
                enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                FeedbackBanner(shownBanner, shownIsError,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp))
            }

            // ── Search bar ────────────────────────────────────────────────────
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it; searchVm.setQuery(it) },
                placeholder = { Text("Barcode or product name", color = DT.SubText) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = DT.SubText.copy(0.6f), modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (searchText.isNotEmpty())
                        IconButton(onClick = { submitSearch() }) {
                            Icon(Icons.AutoMirrored.Filled.Send, null, tint = DT.Teal, modifier = Modifier.size(20.dp))
                        }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
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
                            // Weighed products can still be sold at 0 stock (the weight is what counts).
                            val soldOut = !product.isWeighed && product.stock <= 0
                            Row(modifier = Modifier.fillMaxWidth()
                                .clickable(enabled = !soldOut) { vm.addToCart(product); searchText = ""; searchVm.clear() }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                                    .background(DT.TealDim), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Inventory2, null, tint = DT.Teal, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(product.name, color = if (soldOut) DT.SubText else DT.OnSurface, fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val priceText = if (product.isWeighed) "$currency ${formatPrice(product.pricePerKg)}/kg"
                                                    else "$currency ${formatPrice(product.price)}"
                                    if (soldOut) Text("$priceText  •  Out of stock", color = DT.Red,
                                        style = MaterialTheme.typography.labelSmall)
                                    else Text("$priceText  •  ${product.stockLabel} in stock", color = DT.SubText,
                                        style = MaterialTheme.typography.labelSmall)
                                }
                                if (!soldOut) Icon(Icons.Default.Add, null, tint = DT.Teal, modifier = Modifier.size(20.dp))
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
                        torchOn = torchOn, onTorchAvailable = { hasTorch = it },
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
                    Row(Modifier.align(Alignment.TopEnd).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        // Flash: only offered when the camera has a torch.
                        if (hasTorch) {
                            IconButton(onClick = { torchOn = !torchOn }) {
                                Box(Modifier.size(32.dp).clip(CircleShape)
                                    .background(if (torchOn) Color(0xFFFFC107) else Color.Black.copy(0.6f)),
                                    contentAlignment = Alignment.Center) {
                                    Icon(if (torchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                        if (torchOn) "Turn flash off" else "Turn flash on",
                                        tint = if (torchOn) Color.Black else Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        IconButton(onClick = { showScanner = false }) {
                            Box(Modifier.size(32.dp).clip(CircleShape).background(Color.Black.copy(0.6f)),
                                contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Close, "Close scanner", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
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
                            onEditQty   = { qtyEditItem = item },
                            onEditWeight = { weightEdit = item.product to item.weightKg },
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
                        Text("Scan a barcode, or search by name or barcode above",
                            color = DT.SubText.copy(0.6f), fontSize = 12.sp, textAlign = TextAlign.Center)
                        Text("Tap a quantity to type it, e.g. 24 cans",
                            color = DT.SubText.copy(0.4f), fontSize = 11.sp, textAlign = TextAlign.Center)
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

    // Unknown barcode: offer to add the product (managers/owners only), then add it to the cart.
    val unknown = state.unknownBarcode
    var addingUnknown by remember { mutableStateOf(false) }
    if (unknown != null && canAddProducts) {
        LaunchedEffect(unknown) { showScanner = false }
        if (!addingUnknown) {
            AlertDialog(
                onDismissRequest = { vm.clearUnknownBarcode() },
                containerColor = DT.Surface,
                title = { Text("Product not found", color = Color.White, fontWeight = FontWeight.Bold) },
                text = { Text("No product has the barcode $unknown. Add it now and put it in this sale?", color = DT.SubText) },
                confirmButton = {
                    Button(onClick = { addingUnknown = true }, shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DT.Teal, contentColor = Color.White)) {
                        Text("Add product", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = { TextButton(onClick = { vm.clearUnknownBarcode() }) { Text("Not now", color = DT.SubText) } }
            )
        } else {
            AddEditProductDialog(
                product = null, currency = currency, initialBarcode = unknown,
                onDismiss = { addingUnknown = false; vm.clearUnknownBarcode() },
                onSave = { addingUnknown = false; vm.createAndAdd(it) }
            )
        }
    }

    qtyEditItem?.let { item ->
        QuantityDialog(
            name = item.product.name, current = item.quantity, inStock = item.product.stock,
            onDismiss = { qtyEditItem = null },
            onSet = { vm.updateQuantity(item.product.id, it); qtyEditItem = null }
        )
    }
    weightRequest?.let { (product, existingKg) ->
        WeightDialog(
            product = product, initialKg = existingKg, currency = currency,
            onDismiss = { weightEdit = null; vm.clearWeightRequest() },
            onConfirm = { kg ->
                vm.addWeighedItem(product, kg, PluDecoder.calculatePrice(product.pricePerKg, kg))
                weightEdit = null
            }
        )
    }
}

// ─── Cart Item Card ────────────────────────────────────────────────────────────

@Composable
private fun CartItemCard(
    item: CartItem, currency: String,
    onQtyChange: (Int) -> Unit, onEditQty: () -> Unit, onEditWeight: () -> Unit, onRemove: () -> Unit
) {
    val byWeight = item.product.isWeighed && item.weightKg > 0
    Box(modifier = Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(18.dp))
        .background(DT.Surface)
        .border(1.dp, DT.Border, RoundedCornerShape(18.dp))
        .padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Product badge: first letter reads faster than a generic box icon
            Box(modifier = Modifier.size(50.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(DT.Teal.copy(0.3f), DT.TealDim))),
                contentAlignment = Alignment.Center) {
                Text(item.product.name.firstOrNull()?.uppercase() ?: "?",
                    color = DT.TealLight, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Name + line total
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.product.name, color = Color.White, fontWeight = FontWeight.Bold,
                        fontSize = 15.sp, modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(8.dp))
                    Text("$currency ${String.format("%.2f", item.lineTotal)}",
                        color = DT.Teal, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                }
                // Unit price line
                Text(if (byWeight)
                    "${String.format("%.3f", item.weightKg)} kg @ $currency ${formatPrice(item.product.pricePerKg)}/kg"
                else "$currency ${formatPrice(item.product.price)} each",
                    color = DT.SubText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // Controls
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (item.product.isWeighed) {
                        // Weighed items have no unit count; tapping lets the cashier type a new weight.
                        Row(modifier = Modifier.clip(RoundedCornerShape(50.dp))
                            .background(DT.Teal.copy(0.15f))
                            .border(1.dp, DT.Teal.copy(0.35f), RoundedCornerShape(50.dp))
                            .clickable(onClick = onEditWeight)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Edit, null, tint = DT.Teal, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Edit weight", color = DT.Teal, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Box(modifier = Modifier.clip(RoundedCornerShape(50.dp))
                            .background(Color(0xFF0A1410))
                            .border(1.dp, DT.Border, RoundedCornerShape(50.dp))) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { onQtyChange(item.quantity - 1) },
                                    modifier = Modifier.size(38.dp)) {
                                    Icon(Icons.Default.Remove, "Decrease", tint = Color.White, modifier = Modifier.size(15.dp))
                                }
                                // Tap the number to type a quantity (24 cans = one tap instead of 24)
                                Text(item.quantity.toString(), color = Color.White,
                                    fontWeight = FontWeight.ExtraBold, fontSize = 16.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.widthIn(min = 32.dp)
                                        .clickable(indication = null,
                                            interactionSource = remember { MutableInteractionSource() },
                                            onClick = onEditQty)
                                        .padding(vertical = 8.dp))
                                IconButton(onClick = { onQtyChange(item.quantity + 1) },
                                    modifier = Modifier.size(38.dp)) {
                                    Icon(Icons.Default.Add, "Increase", tint = Color.White, modifier = Modifier.size(15.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    // Quiet remove button — a solid red disc on every row was the loudest thing on screen
                    Box(modifier = Modifier.size(38.dp).clip(CircleShape)
                        .background(DT.Red.copy(0.14f))
                        .clickable(indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onRemove),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Delete, "Remove item", tint = DT.Red, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

// ─── Dialogs ───────────────────────────────────────────────────────────────────

@Composable
private fun QuantityDialog(name: String, current: Int, inStock: Int, onDismiss: () -> Unit, onSet: (Int) -> Unit) {
    var field by remember { mutableStateOf(TextFieldValue(current.toString(), TextRange(0, current.toString().length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val value = field.text.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DT.Surface,
        title = { Text(name, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = field,
                    onValueChange = { field = it.copy(text = it.text.filter(Char::isDigit).take(5)) },
                    label = { Text("Quantity", color = DT.SubText) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (value != null && value > 0) onSet(value) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border,
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        cursorColor = DT.Teal, focusedContainerColor = DT.Bg, unfocusedContainerColor = DT.Bg))
                val over = value != null && value > inStock
                Text(if (over) "Only $inStock in stock — it will be set to $inStock" else "$inStock in stock",
                    color = if (over) DT.Amber else DT.SubText, fontSize = 12.sp)
            }
        },
        confirmButton = {
            Button(onClick = { value?.let(onSet) }, enabled = value != null && value > 0,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DT.Green, contentColor = Color.White,
                    disabledContainerColor = DT.Green.copy(0.45f), disabledContentColor = Color.White.copy(0.7f))) {
                Text("Set", fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = DT.SubText) } }
    )
}

@Composable
private fun WeightDialog(product: Product, initialKg: Double, currency: String,
    onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var text by remember {
        mutableStateOf(if (initialKg > 0) String.format(java.util.Locale.US, "%.3f", initialKg).trimEnd('0').trimEnd('.') else "")
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val kg = text.toDoubleOrNull()?.takeIf { it > 0.0 }
    val price = kg?.let { PluDecoder.calculatePrice(product.pricePerKg, it) } ?: 0.0
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DT.Surface,
        title = { Text(product.name, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("$currency ${formatPrice(product.pricePerKg)} per kg", color = DT.SubText, fontSize = 13.sp)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = sanitizeDecimalInput(it, maxDecimals = 3, maxWhole = 4) },
                    label = { Text("Weight (kg)", color = DT.SubText) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { kg?.let(onConfirm) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border,
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        cursorColor = DT.Teal, focusedContainerColor = DT.Bg, unfocusedContainerColor = DT.Bg))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Price", color = DT.SubText, fontSize = 13.sp)
                    Text("$currency ${String.format("%.2f", price)}", color = DT.Teal, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                }
                // Informational only — selling is never blocked on weighed stock.
                if (kg != null && kg > product.stockKg)
                    Text("Only ${product.stockLabel} on record", color = DT.Amber, fontSize = 12.sp)
            }
        },
        confirmButton = {
            Button(onClick = { kg?.let(onConfirm) }, enabled = kg != null,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DT.Green, contentColor = Color.White,
                    disabledContainerColor = DT.Green.copy(0.45f), disabledContentColor = Color.White.copy(0.7f))) {
                Text("Add to cart", fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = DT.SubText) } }
    )
}
