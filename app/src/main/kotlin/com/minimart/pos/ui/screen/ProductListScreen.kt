package com.minimart.pos.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.minimart.pos.data.entity.Product
import com.minimart.pos.scanner.BarcodeScannerView
import com.minimart.pos.scanner.ScannerOverlay
import com.minimart.pos.ui.theme.DT
import com.minimart.pos.ui.viewmodel.ProductViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private enum class StockLevel { OUT, LOW, OK }

private fun stockLevel(p: Product): StockLevel = when {
    (if (p.isWeighed) p.stockKg <= 0.0 else p.stock <= 0) -> StockLevel.OUT
    p.stock <= p.lowStockThreshold -> StockLevel.LOW
    else -> StockLevel.OK
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductListScreen(
    onBack: () -> Unit,
    canEditPrices: Boolean = true,
    currency: String = "KES",
    vm: ProductViewModel = hiltViewModel()
) {
    val products by vm.products.collectAsState()
    val categories by vm.categories.collectAsState()
    val lowStock by vm.lowStockProducts.collectAsState()
    val selectedCat by vm.selectedCategory.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()
    val uiState by vm.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editProduct by remember { mutableStateOf<Product?>(null) }
    var lowOnly by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.successMessage) {
        if (uiState.successMessage != null) { kotlinx.coroutines.delay(2000); vm.clearMessages() }
    }
    // Errors (duplicate barcode, "cannot remove more than stock", ...) were never shown.
    LaunchedEffect(uiState.error) {
        if (uiState.error != null) { kotlinx.coroutines.delay(4000); vm.clearMessages() }
    }

    val visible = if (lowOnly) products.filter { stockLevel(it) != StockLevel.OK } else products
    val filtering = searchQuery.isNotBlank() || selectedCat != null || lowOnly
    val subtitle = buildString {
        append(if (filtering) "${visible.size} shown" else "${products.size} product${if (products.size != 1) "s" else ""}")
        if (lowStock.isNotEmpty()) append("  •  ${lowStock.size} low on stock")
    }

    Box(modifier = Modifier.fillMaxSize().background(DT.Bg)) {
        Column(Modifier.fillMaxSize()) {
            // ── Header (this screen used to show two stacked "Products" titles) ──
            GradientHeader(title = "Products", subtitle = subtitle, onBack = onBack) {
                if (canEditPrices) {
                    HeaderPillButton("Add", Icons.Default.Add) { editProduct = null; showAddDialog = true }
                }
            }
            Spacer(Modifier.height(12.dp))

            // ── Search ────────────────────────────────────────────────────────
            OutlinedTextField(
                value = searchQuery,
                onValueChange = vm::setSearchQuery,
                placeholder = { Text("Search name, barcode or SKU", color = DT.SubText) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = DT.SubText, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) IconButton(onClick = { vm.setSearchQuery("") }) {
                        Icon(Icons.Default.Close, "Clear search", tint = DT.SubText)
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

            // ── Filter chips ──────────────────────────────────────────────────
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { DarkFilterChip("All", selectedCat == null && !lowOnly) { vm.setCategory(null); lowOnly = false } }
                if (lowStock.isNotEmpty()) {
                    item { DarkFilterChip("⚠ Low stock (${lowStock.size})", lowOnly, accent = DT.Amber) { lowOnly = !lowOnly } }
                }
                items(categories) { cat -> DarkFilterChip(cat, selectedCat == cat) { vm.setCategory(cat) } }
            }
            Spacer(Modifier.height(8.dp))

            val feedback = uiState.error ?: uiState.successMessage
            if (feedback != null) {
                FeedbackBanner(feedback, isError = uiState.error != null,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
                Spacer(Modifier.height(4.dp))
            }

            // ── Product list ──────────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(visible, key = { it.id }) { product ->
                    ProductRow(
                        product = product, currency = currency, canEdit = canEditPrices,
                        onEdit = { editProduct = product; showAddDialog = true },
                        onDelete = { vm.deleteProduct(product.id) }
                    )
                }
                if (visible.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Inventory2, null, modifier = Modifier.size(56.dp), tint = DT.SubText.copy(0.3f))
                                Text(when {
                                    searchQuery.isNotBlank() -> "No results for \"$searchQuery\""
                                    filtering -> "Nothing matches this filter"
                                    else -> "No products yet"
                                }, color = DT.SubText, fontWeight = FontWeight.SemiBold)
                                if (filtering) {
                                    TextButton(onClick = { vm.setSearchQuery(""); vm.setCategory(null); lowOnly = false }) {
                                        Text("Clear filters", color = DT.Teal)
                                    }
                                } else if (canEditPrices) {
                                    Button(onClick = { editProduct = null; showAddDialog = true },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = DT.Teal, contentColor = Color.White)) {
                                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Add your first product", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddEditProductDialog(product = editProduct, currency = currency, categories = categories,
            onDismiss = { showAddDialog = false; editProduct = null },
            onSave = { vm.saveProduct(it); showAddDialog = false; editProduct = null })
    }
}

@Composable
private fun DarkFilterChip(label: String, selected: Boolean, accent: Color = DT.Teal, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) accent else DT.Surface2)
            .border(1.dp, if (selected) accent else DT.Border, RoundedCornerShape(20.dp))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, color = if (selected) Color.White else DT.SubText,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun StockPill(product: Product) {
    val level = stockLevel(product)
    val color = when (level) { StockLevel.OUT -> DT.Red; StockLevel.LOW -> DT.Amber; StockLevel.OK -> DT.Green }
    val text = when (level) {
        StockLevel.OUT -> "Out of stock"
        StockLevel.LOW -> "${product.stockLabel} left"
        StockLevel.OK  -> "${product.stockLabel} in stock"
    }
    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(0.15f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun ProductRow(product: Product, currency: String, canEdit: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val daysToExpiry = if (product.expiryDate > 0L)
        ((product.expiryDate - System.currentTimeMillis()) / (24L * 60 * 60 * 1000)).toInt() else null
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(DT.Surface).border(1.dp, DT.Border, RoundedCornerShape(16.dp))
            // Read-only for roles that can't edit prices — the edit/delete icons used to be shown to everyone.
            .then(if (canEdit) Modifier.clickable(onClick = onEdit) else Modifier)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(DT.TealDim),
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
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (product.isWeighed) "$currency ${formatPrice(product.pricePerKg)}/kg"
                     else "$currency ${formatPrice(product.price)}",
                    color = DT.OnSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                StockPill(product)
            }
            if (canEdit) {
                IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, "Delete product", tint = DT.Red.copy(0.85f), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
    if (showDeleteConfirm) {
        AlertDialog(onDismissRequest = { showDeleteConfirm = false }, containerColor = DT.Surface,
            title = { Text("Delete product?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("\"${product.name}\" will be removed from your catalogue. Past sales keep their records.", color = DT.SubText) },
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
                    border = androidx.compose.foundation.BorderStroke(1.dp, DT.Border)) { Text("Cancel", color = DT.SubText) }
            })
    }
}

// ─── Add/Edit Product Dialog ──────────────────────────────────────────────────

/** Blank = no expiry (0). Returns null when the text isn't a real date. */
private fun parseExpiry(text: String): Long? {
    val t = text.trim()
    if (t.isEmpty()) return 0L
    val fmt = SimpleDateFormat(if (t.length <= 8) "dd/MM/yy" else "dd/MM/yyyy", Locale.getDefault()).apply { isLenient = false }
    return try { fmt.parse(t)?.time } catch (_: Exception) { null }
}

private fun formatExpiry(ms: Long): String = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(ms))

/** The date picker works in UTC midnights; stock expiry is stored as local midnight. */
private fun pickerDayToLocalStart(utcMidnightMs: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMidnightMs }
    return Calendar.getInstance().apply {
        clear()
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
    }.timeInMillis
}

private fun localDayToPickerUtc(localMs: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = localMs }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
    }.timeInMillis
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun AddEditProductDialog(
    product: Product?,
    onDismiss: () -> Unit,
    onSave: (Product) -> Unit,
    currency: String = "KES",
    categories: List<String> = emptyList()
) {
    var barcode    by remember { mutableStateOf(product?.barcode ?: "") }
    var name       by remember { mutableStateOf(product?.name ?: "") }
    var price      by remember { mutableStateOf(product?.takeIf { !it.isWeighed }?.price?.let { plainNumber(it) } ?: "") }
    var costPrice  by remember { mutableStateOf(product?.costPrice?.takeIf { it > 0.0 }?.let { plainNumber(it) } ?: "") }
    var isWeighed  by remember { mutableStateOf(product?.isWeighed ?: false) }
    var pricePerKg by remember { mutableStateOf(product?.pricePerKg?.takeIf { it > 0.0 }?.let { plainNumber(it) } ?: "") }
    var pluCode    by remember { mutableStateOf(product?.pluCode ?: "") }
    var stock      by remember { mutableStateOf(product?.let { if (it.isWeighed) plainNumber(it.stockKg, 3) else it.stock.toString() } ?: "") }
    var category   by remember { mutableStateOf(product?.category ?: "") }
    var unit       by remember { mutableStateOf(product?.unit ?: "pcs") }
    var sku        by remember { mutableStateOf(product?.sku ?: "") }
    var lowAt      by remember { mutableStateOf((product?.lowStockThreshold ?: 5).toString()) }
    var vat        by remember { mutableStateOf(product?.taxRate?.takeIf { it > 0.0 }?.let { plainNumber(it * 100) } ?: "") }
    var supplierName  by remember { mutableStateOf(product?.supplierName ?: "") }
    var supplierPhone by remember { mutableStateOf(product?.supplierPhone ?: "") }
    var reorderQty    by remember { mutableStateOf(product?.reorderQuantity?.takeIf { it > 0 }?.toString() ?: "") }
    var batchNumber   by remember { mutableStateOf(product?.batchNumber ?: "") }
    var expiryDateStr by remember {
        mutableStateOf(if ((product?.expiryDate ?: 0L) > 0L) formatExpiry(product?.expiryDate ?: 0L) else "")
    }
    var showScanner by remember { mutableStateOf(false) }
    var showExpiryPicker by remember { mutableStateOf(false) }
    val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // Focus chain so the keyboard's "Next" key walks the form instead of needing a tap per field.
    val nameFocus = remember { FocusRequester() }
    val priceFocus = remember { FocusRequester() }
    val costFocus = remember { FocusRequester() }
    val pluFocus = remember { FocusRequester() }
    val stockFocus = remember { FocusRequester() }
    val unitFocus = remember { FocusRequester() }
    val categoryFocus = remember { FocusRequester() }
    val skuFocus = remember { FocusRequester() }
    val lowAtFocus = remember { FocusRequester() }
    val vatFocus = remember { FocusRequester() }
    val supplierNameFocus = remember { FocusRequester() }
    val supplierPhoneFocus = remember { FocusRequester() }
    val reorderFocus = remember { FocusRequester() }
    val batchFocus = remember { FocusRequester() }
    val expiryFocus = remember { FocusRequester() }

    val sellPrice = (if (isWeighed) pricePerKg else price).toDoubleOrNull() ?: 0.0
    val costValue = costPrice.toDoubleOrNull() ?: 0.0
    val expiryMs = parseExpiry(expiryDateStr)
    val canSave = barcode.isNotBlank() && name.isNotBlank() && sellPrice > 0.0 && expiryMs != null &&
        (!isWeighed || pluCode.isNotBlank())

    if (showExpiryPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = expiryMs?.takeIf { it > 0L }?.let(::localDayToPickerUtc)
        )
        val pickerColors = DatePickerDefaults.colors(
            containerColor = DT.Surface,
            titleContentColor = DT.SubText,
            headlineContentColor = DT.OnSurface,
            weekdayContentColor = DT.SubText,
            subheadContentColor = DT.SubText,
            navigationContentColor = DT.OnSurface,
            yearContentColor = DT.OnSurface,
            currentYearContentColor = DT.TealLight,
            selectedYearContainerColor = DT.Teal,
            selectedYearContentColor = Color.White,
            dayContentColor = DT.OnSurface,
            selectedDayContainerColor = DT.Teal,
            selectedDayContentColor = Color.White,
            todayContentColor = DT.TealLight,
            todayDateBorderColor = DT.TealLight
        )
        DatePickerDialog(
            onDismissRequest = { showExpiryPicker = false },
            colors = pickerColors,
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { expiryDateStr = formatExpiry(pickerDayToLocalStart(it)) }
                    showExpiryPicker = false
                }) { Text("OK", color = DT.Teal) }
            },
            dismissButton = { TextButton(onClick = { showExpiryPicker = false }) { Text("Cancel", color = DT.SubText) } }
        ) { DatePicker(state = pickerState, colors = pickerColors) }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.92f),
            shape = RoundedCornerShape(24.dp),
            color = DT.Surface
        ) {
            Column(Modifier.fillMaxSize()) {
                // Title bar
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(if (product == null) "Add product" else "Edit product", color = DT.OnSurface,
                        fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close", tint = DT.SubText) }
                }
                HorizontalDivider(color = DT.Border)

                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        DarkField(barcode, { barcode = it.trim() }, "Barcode *", modifier = Modifier.weight(1f), nextFocusRequester = nameFocus)
                        FilledIconButton(onClick = {
                            if (!cameraPermission.status.isGranted) cameraPermission.launchPermissionRequest()
                            else showScanner = !showScanner
                        }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = DT.Teal)) {
                            Icon(Icons.Default.QrCode, "Scan barcode", tint = Color.White)
                        }
                    }
                    if (showScanner && cameraPermission.status.isGranted) {
                        Box(modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(10.dp))) {
                            BarcodeScannerView(modifier = Modifier.fillMaxSize(), lifecycleOwner = lifecycleOwner,
                                onBarcodeDetected = { barcode = it; showScanner = false })
                            ScannerOverlay(modifier = Modifier.fillMaxSize())
                            IconButton(onClick = { showScanner = false }, modifier = Modifier.align(Alignment.TopEnd)) {
                                Icon(Icons.Default.Close, "Close scanner", tint = Color.White)
                            }
                        }
                    }
                    DarkField(name, { name = it }, "Product name *", focusRequester = nameFocus, nextFocusRequester = priceFocus)

                    // Sold by weight: fruit, grain, meat... priced per kg and weighed at the till or on a scale
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(DT.Surface2)
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                                isWeighed = !isWeighed
                                if (isWeighed && unit == "pcs") unit = "kg"
                                if (!isWeighed && unit == "kg") unit = "pcs"
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Sold by weight", color = DT.OnSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Priced per kg; weight comes from the scale label or is typed at the till",
                                color = DT.SubText, fontSize = 11.sp)
                        }
                        Switch(checked = isWeighed, onCheckedChange = null,
                            colors = SwitchDefaults.colors(checkedTrackColor = DT.Teal))
                    }

                    if (isWeighed) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DarkField(pricePerKg, { pricePerKg = sanitizeMoneyInput(it) }, "Price per kg ($currency) *", Modifier.weight(1f),
                                KeyboardType.Decimal, focusRequester = priceFocus, nextFocusRequester = costFocus)
                            DarkField(costPrice, { costPrice = sanitizeMoneyInput(it) }, "Cost per kg", Modifier.weight(1f),
                                KeyboardType.Decimal, focusRequester = costFocus, nextFocusRequester = pluFocus)
                        }
                        DarkField(pluCode, { pluCode = it.filter(Char::isDigit).take(5) }, "PLU code (1–5 digits) *",
                            keyboardType = KeyboardType.Number, focusRequester = pluFocus, nextFocusRequester = stockFocus,
                            supportingText = "The 5 digits after the leading 2 on scale labels")
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DarkField(price, { price = sanitizeMoneyInput(it) }, "Price ($currency) *", Modifier.weight(1f),
                                KeyboardType.Decimal, focusRequester = priceFocus, nextFocusRequester = costFocus)
                            DarkField(costPrice, { costPrice = sanitizeMoneyInput(it) }, "Cost price", Modifier.weight(1f),
                                KeyboardType.Decimal, focusRequester = costFocus, nextFocusRequester = stockFocus)
                        }
                    }
                    if (sellPrice > 0.0 && costValue > 0.0) {
                        val profit = sellPrice - costValue
                        val margin = profit / sellPrice * 100
                        Text("Profit $currency ${String.format("%.2f", profit)} per ${if (isWeighed) "kg" else "unit"}  •  margin ${String.format("%.0f", margin)}%",
                            color = if (profit >= 0) DT.Green else DT.Red, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DarkField(stock, {
                            stock = if (isWeighed) sanitizeDecimalInput(it, maxDecimals = 3, maxWhole = 6) else it.filter(Char::isDigit).take(7)
                        }, if (isWeighed) "Stock (kg)" else "Stock", Modifier.weight(1f),
                            if (isWeighed) KeyboardType.Decimal else KeyboardType.Number,
                            focusRequester = stockFocus, nextFocusRequester = unitFocus)
                        DarkField(unit, { unit = it }, "Unit", Modifier.weight(1f), focusRequester = unitFocus, nextFocusRequester = categoryFocus)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DarkField(category, { category = it }, "Category", Modifier.weight(1f), focusRequester = categoryFocus, nextFocusRequester = skuFocus)
                        DarkField(sku, { sku = it }, "SKU", Modifier.weight(1f), focusRequester = skuFocus, nextFocusRequester = lowAtFocus)
                    }
                    // One tap for an existing category instead of retyping it (and creating "drinks" next to "Drinks")
                    if (categories.isNotEmpty()) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            categories.forEach { c ->
                                val picked = category.equals(c, ignoreCase = true)
                                Box(Modifier.clip(RoundedCornerShape(16.dp))
                                    .background(if (picked) DT.Teal else DT.Surface2)
                                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { category = c }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)) {
                                    Text(c, color = if (picked) Color.White else DT.SubText, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DarkField(lowAt, { lowAt = it.filter(Char::isDigit).take(5) }, "Low-stock alert at", Modifier.weight(1f),
                            KeyboardType.Number, focusRequester = lowAtFocus, nextFocusRequester = vatFocus)
                        DarkField(vat, { vat = sanitizeDecimalInput(it, maxDecimals = 2, maxWhole = 3) }, "VAT % (in price)", Modifier.weight(1f),
                            KeyboardType.Decimal, focusRequester = vatFocus, nextFocusRequester = supplierNameFocus)
                    }

                    Spacer(Modifier.height(4.dp))
                    Text("Supplier", color = DT.SubText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    DarkField(supplierName, { supplierName = it }, "Supplier name", Modifier.fillMaxWidth(),
                        focusRequester = supplierNameFocus, nextFocusRequester = supplierPhoneFocus)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DarkField(supplierPhone, { supplierPhone = it }, "Supplier phone", Modifier.weight(1f),
                            KeyboardType.Phone, focusRequester = supplierPhoneFocus, nextFocusRequester = reorderFocus)
                        DarkField(reorderQty, { reorderQty = it.filter(Char::isDigit).take(6) }, "Reorder qty", Modifier.weight(1f),
                            KeyboardType.Number, focusRequester = reorderFocus, nextFocusRequester = batchFocus)
                    }

                    Spacer(Modifier.height(4.dp))
                    Text("Batch & expiry", color = DT.SubText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DarkField(batchNumber, { batchNumber = it }, "Batch no.", Modifier.weight(1f),
                            focusRequester = batchFocus, nextFocusRequester = expiryFocus)
                        DarkField(expiryDateStr, { expiryDateStr = it.take(10) }, "Expiry (dd/MM/yy)", Modifier.weight(1f),
                            focusRequester = expiryFocus, isError = expiryMs == null,
                            supportingText = if (expiryMs == null) "Use dd/MM/yy, or pick from the calendar" else null,
                            trailingIcon = {
                                IconButton(onClick = { showExpiryPicker = true }) {
                                    Icon(Icons.Default.CalendarMonth, "Pick expiry date", tint = DT.Teal)
                                }
                            })
                    }
                }

                HorizontalDivider(color = DT.Border)
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DT.Border)
                    ) { Text("Cancel", color = DT.SubText) }
                    Button(
                        onClick = {
                            // Editing copies the existing product so fields this form doesn't show
                            // (description, image, created date, active flag) are kept. It used to rebuild
                            // the product from scratch on every save.
                            val base = product ?: Product(barcode = "", name = "", price = 0.0, stock = 0)
                            val stockValue = stock.toDoubleOrNull() ?: 0.0
                            onSave(base.copy(
                                barcode = barcode.trim(), sku = sku.trim(), name = name.trim(),
                                price = sellPrice.coerceAtLeast(0.0),
                                costPrice = costValue.coerceAtLeast(0.0),
                                stock = if (isWeighed) stockValue.coerceAtLeast(0.0).toInt() else (stock.toIntOrNull() ?: 0).coerceAtLeast(0),
                                stockKg = if (isWeighed) stockValue.coerceAtLeast(0.0) else 0.0,
                                lowStockThreshold = (lowAt.toIntOrNull() ?: 5).coerceAtLeast(0),
                                taxRate = ((vat.toDoubleOrNull() ?: 0.0).coerceIn(0.0, 100.0)) / 100.0,
                                category = category.trim().ifBlank { "General" }, unit = unit.trim().ifBlank { if (isWeighed) "kg" else "pcs" },
                                supplierName = supplierName.trim(), supplierPhone = supplierPhone.trim(),
                                reorderQuantity = (reorderQty.toIntOrNull() ?: 0).coerceAtLeast(0),
                                batchNumber = batchNumber.trim(), expiryDate = expiryMs ?: 0L,
                                // Only weighed products carry a PLU (non-weighed ones were saved as "00000").
                                pluCode = if (isWeighed) pluCode.trim().padStart(5, '0') else "",
                                isWeighed = isWeighed,
                                pricePerKg = if (isWeighed) sellPrice.coerceAtLeast(0.0) else 0.0,
                                updatedAt = System.currentTimeMillis()
                            ))
                        },
                        // Price must be positive, not just parseable: a pasted "-5" would corrupt totals and reports.
                        enabled = canSave,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DT.Green, contentColor = Color.White,
                            disabledContainerColor = DT.Green.copy(0.45f), disabledContentColor = Color.White.copy(0.7f)
                        )
                    ) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DarkField(value: String, onValueChange: (String) -> Unit, label: String,
    modifier: Modifier = Modifier.fillMaxWidth(), keyboardType: KeyboardType = KeyboardType.Text,
    // Focus chaining: pass focusRequester to anchor this field and nextFocusRequester to jump to the
    // next one on the keyboard's "Next" key. Omitting nextFocusRequester (last field) shows "Done".
    focusRequester: FocusRequester? = null,
    nextFocusRequester: FocusRequester? = null,
    isError: Boolean = false,
    supportingText: String? = null,
    trailingIcon: (@Composable () -> Unit)? = null) {
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = if (isError) DT.Red else DT.SubText, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        isError = isError,
        supportingText = supportingText?.let { msg -> { Text(msg, color = if (isError) DT.Red else DT.SubText, fontSize = 11.sp) } },
        trailingIcon = trailingIcon,
        modifier = modifier.let { if (focusRequester != null) it.focusRequester(focusRequester) else it },
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = if (nextFocusRequester != null) ImeAction.Next else ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onNext = { nextFocusRequester?.requestFocus() },
            onDone = { keyboardController?.hide() }
        ),
        textStyle = androidx.compose.ui.text.TextStyle(color = DT.OnSurface, fontSize = 15.sp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = DT.Teal,
            unfocusedBorderColor = DT.Border,
            errorBorderColor = DT.Red,
            focusedTextColor = DT.OnSurface,
            unfocusedTextColor = DT.OnSurface,
            cursorColor = DT.Teal,
            focusedLabelColor = DT.Teal,
            unfocusedLabelColor = DT.SubText,
            focusedContainerColor = DT.Surface,
            unfocusedContainerColor = DT.Surface,
            errorContainerColor = DT.Surface
        )
    )
}
