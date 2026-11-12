package com.minimart.pos.ui.screen

import com.minimart.pos.util.Lang
import com.minimart.pos.ui.components.Text
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.minimart.pos.data.entity.PurchaseOrderStatus
import com.minimart.pos.data.entity.Supplier
import com.minimart.pos.data.entity.poLabel
import com.minimart.pos.ui.theme.DT
import com.minimart.pos.ui.viewmodel.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─── shared bits ──────────────────────────────────────────────────────────────────────────

private fun statusColor(s: PurchaseOrderStatus): Color = when (s) {
    PurchaseOrderStatus.DRAFT -> DT.SubText
    PurchaseOrderStatus.ORDERED -> DT.TealLight
    PurchaseOrderStatus.PARTIAL -> DT.Amber
    PurchaseOrderStatus.RECEIVED -> DT.Green
    PurchaseOrderStatus.CANCELLED -> DT.Red
}

@Composable
private fun StatusPill(s: PurchaseOrderStatus) {
    val c = statusColor(s)
    Text(prettyEnumName(s.name), color = c, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(c.copy(0.15f)).padding(horizontal = 10.dp, vertical = 3.dp))
}

@Composable
private fun PoField(
    value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text, singleLine: Boolean = true, enabled: Boolean = true
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, enabled = enabled,
        label = { Text(label, fontSize = 12.sp) }, singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = DT.OnSurface, unfocusedTextColor = DT.OnSurface,
            disabledTextColor = DT.OnSurface, focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border,
            focusedLabelColor = DT.TealLight, unfocusedLabelColor = DT.SubText, cursorColor = DT.TealLight
        )
    )
}

@Composable
private fun PoButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = true, color: Color = DT.Teal) {
    Box(
        modifier = modifier.height(46.dp).clip(RoundedCornerShape(14.dp))
            .then(if (filled) Modifier.background(color) else Modifier.border(1.dp, color.copy(0.6f), RoundedCornerShape(14.dp)))
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (filled) Color.White else color, fontWeight = FontWeight.Bold, fontSize = 14.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp))
    }
}

private fun dial(context: android.content.Context, phone: String) {
    try { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone.trim())))) } catch (_: Exception) {}
}

private val dateFmt get() = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

// ─── Orders list ──────────────────────────────────────────────────────────────────────────

@Composable
fun PurchaseOrdersScreen(
    onBack: () -> Unit,
    onSuppliers: () -> Unit,
    onOpenOrder: (Long) -> Unit,
    currency: String,
    vm: PurchasingViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(DT.Bg)) {
        GradientHeader(
            title = tr(com.minimart.pos.R.string.purchasing),
            subtitle = if (state.totalOwed > 0) Lang.t("Owed to suppliers: $currency ${formatPrice(state.totalOwed)}", "المستحق للموردين: $currency ${formatPrice(state.totalOwed)}") else Lang.t("Orders to suppliers", "طلبات الموردين"),
            onBack = onBack
        ) {
            HeaderPillButton(Lang.t("Suppliers", "الموردون"), Icons.Default.Store, onSuppliers)
            Spacer(Modifier.width(8.dp))
            HeaderPillButton(Lang.t("New order", "طلب جديد"), Icons.Default.Add) { onOpenOrder(0) }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(selected = state.filter == null, onClick = { vm.setFilter(null) }, label = { Text(tr(com.minimart.pos.R.string.all)) }) }
            items(PurchaseOrderStatus.values().toList()) { st ->
                FilterChip(selected = state.filter == st, onClick = { vm.setFilter(st) }, label = { Text(prettyEnumName(st.name)) })
            }
        }
        if (state.orders.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.LocalShipping, null, tint = DT.SubText, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text(tr(com.minimart.pos.R.string.no_pos), color = DT.OnSurface, fontWeight = FontWeight.Bold)
                Text(tr(com.minimart.pos.R.string.tap_order_stock), color = DT.SubText, fontSize = 13.sp)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.orders, key = { it.order.id }) { row ->
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(DT.Surface)
                            .border(1.dp, DT.Border, RoundedCornerShape(16.dp))
                            .clickable(role = androidx.compose.ui.semantics.Role.Button) { onOpenOrder(row.order.id) }.padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(poLabel(row.order.id), color = DT.TealLight, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                            Spacer(Modifier.weight(1f))
                            StatusPill(row.order.status)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(row.supplierName, color = DT.OnSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        Row {
                            Text("${row.itemCount} item${if (row.itemCount == 1) "" else "s"} · ${dateFmt.format(Date(row.order.createdAt))}", color = DT.SubText, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Text("$currency ${formatPrice(row.total)}", color = DT.OnSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        if (row.balance > 0) Text(tr(com.minimart.pos.R.string.po_balance_fmt, currency, formatPrice(row.balance)), color = DT.Amber, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ─── Suppliers ────────────────────────────────────────────────────────────────────────────

@Composable
fun SuppliersScreen(onBack: () -> Unit, currency: String, vm: PurchasingViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editing by remember { mutableStateOf<Supplier?>(null) }

    Column(Modifier.fillMaxSize().background(DT.Bg)) {
        GradientHeader(Lang.t("Suppliers", "الموردون"), Lang.t("${state.suppliers.size} active", "${state.suppliers.size} نشط"), onBack) {
            HeaderPillButton(Lang.t("Add supplier", "إضافة مورد"), Icons.Default.Add) { editing = Supplier(name = "") }
        }
        FeedbackBanner(state.message, state.isError, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.importableSuppliers > 0) item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(DT.Teal.copy(0.14f))
                        .clickable(role = androidx.compose.ui.semantics.Role.Button) { vm.importFromProducts() }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Download, null, tint = DT.TealLight)
                    Spacer(Modifier.width(10.dp))
                    Text(androidx.compose.ui.res.pluralStringResource(com.minimart.pos.R.plurals.import_suppliers, state.importableSuppliers, state.importableSuppliers),
                        color = DT.OnSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (state.suppliers.isEmpty()) item {
                Text(tr(com.minimart.pos.R.string.no_suppliers), color = DT.SubText, modifier = Modifier.padding(24.dp))
            }
            items(state.suppliers, key = { it.supplier.id }) { row ->
                val s = row.supplier
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(DT.Surface)
                        .border(1.dp, DT.Border, RoundedCornerShape(16.dp))
                        .clickable(role = androidx.compose.ui.semantics.Role.Button) { editing = s }.padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.name, color = DT.OnSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (s.phone.isNotBlank()) Text(s.phone, color = DT.SubText, fontSize = 13.sp)
                        }
                        if (s.phone.isNotBlank()) IconButton(onClick = { dial(context, s.phone) }) {
                            Icon(Icons.Default.Call, Lang.t("Call ${s.name}", "اتصال بـ ${s.name}"), tint = DT.Green)
                        }
                    }
                    if (row.openOrders > 0 || row.owed > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            listOfNotNull(
                                if (row.openOrders > 0) "${row.openOrders} open order${if (row.openOrders == 1) "" else "s"}" else null,
                                if (row.owed > 0) Lang.t("Owed $currency ${formatPrice(row.owed)}", "مستحق $currency ${formatPrice(row.owed)}") else null
                            ).joinToString(" · "),
                            color = if (row.owed > 0) DT.Amber else DT.SubText, fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }

    editing?.let { s ->
        var name by remember(s) { mutableStateOf(s.name) }
        var phone by remember(s) { mutableStateOf(s.phone) }
        var email by remember(s) { mutableStateOf(s.email) }
        var address by remember(s) { mutableStateOf(s.address) }
        var notes by remember(s) { mutableStateOf(s.notes) }
        AlertDialog(
            onDismissRequest = { editing = null },
            properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false), containerColor = DT.Surface,
            title = { Text(if (s.id == 0L) Lang.t("New supplier", "مورد جديد") else Lang.t("Edit supplier", "تعديل المورد"), color = DT.OnSurface) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PoField(name, { name = it.take(80) }, Lang.t("Name", "الاسم"), Modifier.fillMaxWidth())
                    PoField(phone, { phone = it.take(20) }, Lang.t("Phone", "الهاتف"), Modifier.fillMaxWidth(), KeyboardType.Phone)
                    PoField(email, { email = it.take(80) }, Lang.t("Email", "البريد الإلكتروني"), Modifier.fillMaxWidth(), KeyboardType.Email)
                    PoField(address, { address = it.take(120) }, Lang.t("Address", "العنوان"), Modifier.fillMaxWidth())
                    PoField(notes, { notes = it.take(300) }, Lang.t("Notes", "ملاحظات"), Modifier.fillMaxWidth(), singleLine = false)
                    if (s.id != 0L) TextButton(onClick = { vm.deactivateSupplier(s.id); editing = null }) {
                        Text(tr(com.minimart.pos.R.string.remove_supplier), color = DT.Red)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.saveSupplier(s.copy(name = name, phone = phone, email = email, address = address, notes = notes))
                    editing = null
                }) { Text(tr(com.minimart.pos.R.string.save), color = DT.TealLight) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(tr(com.minimart.pos.R.string.cancel), color = DT.SubText) } }
        )
    }
}

// ─── Editor ───────────────────────────────────────────────────────────────────────────────

private fun buildShareText(s: PoEditorState, currency: String): String = buildString {
    val sup = s.supplier
    appendLine("${if (s.storeName.isNotBlank()) s.storeName + " — " else ""}Purchase order ${if (s.poId != 0L) poLabel(s.poId) else ""}".trim())
    appendLine("Date: ${dateFmt.format(Date(if (s.orderedAt > 0) s.orderedAt else System.currentTimeMillis()))}")
    if (sup != null) appendLine(Lang.t("Supplier: ${sup.name}", "المورد: ${sup.name}"))
    appendLine()
    s.lines.forEachIndexed { i, l ->
        val qty = plainNumber(l.qty, 3)
        append("${i + 1}. ${l.name} × $qty")
        if (l.cost > 0) append(" @ ${formatPrice(l.cost)} = ${formatPrice(l.total)}")
        appendLine()
    }
    appendLine()
    appendLine(Lang.t("Total: $currency ${formatPrice(s.total)}", "الإجمالي: $currency ${formatPrice(s.total)}"))
    if (s.notes.isNotBlank()) { appendLine(); appendLine(Lang.t("Notes: ${s.notes}", "ملاحظات: ${s.notes}")) }
}.trimEnd()

@Composable
fun PurchaseOrderEditorScreen(onBack: () -> Unit, currency: String, vm: PurchaseOrderEditorViewModel = hiltViewModel()) {
    val s by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showPicker by remember { mutableStateOf(false) }
    var showReceive by remember { mutableStateOf(false) }
    var showPay by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(s.finished) { if (s.finished) onBack() }

    Column(Modifier.fillMaxSize().background(DT.Bg)) {
        GradientHeader(
            title = if (s.poId == 0L) Lang.t("New order", "طلب جديد") else poLabel(s.poId),
            subtitle = prettyEnumName(s.status.name),
            onBack = onBack
        ) {
            if (s.lines.isNotEmpty() && s.supplier != null) HeaderPillButton(Lang.t("Share", "مشاركة"), Icons.Default.Share) {
                val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, buildShareText(s, currency)) }
                try { context.startActivity(Intent.createChooser(send, Lang.t("Share order", "مشاركة الطلب")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
            }
        }
        FeedbackBanner(s.message, s.isError, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

        if (s.loading) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DT.Teal) }; return@Column }

        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                if (s.editable) {
                    var open by remember { mutableStateOf(false) }
                    Box {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(DT.Surface)
                                .border(1.dp, DT.Border, RoundedCornerShape(14.dp)).clickable(role = androidx.compose.ui.semantics.Role.Button) { open = true }.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Store, null, tint = DT.TealLight)
                            Spacer(Modifier.width(10.dp))
                            Text(s.supplier?.name ?: Lang.t("Choose supplier", "اختر المورد"), color = if (s.supplier == null) DT.SubText else DT.OnSurface,
                                modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Icon(Icons.Default.ArrowDropDown, null, tint = DT.SubText)
                        }
                        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            if (s.suppliers.isEmpty()) DropdownMenuItem(text = { Text(tr(com.minimart.pos.R.string.add_supplier_first)) }, onClick = { open = false })
                            s.suppliers.forEach { sup ->
                                DropdownMenuItem(text = { Text(sup.name) }, onClick = { vm.setSupplier(sup.id); open = false })
                            }
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(DT.Surface).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.supplier?.name ?: Lang.t("Supplier", "المورد"), color = DT.OnSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            if (s.orderedAt > 0) Text(tr(com.minimart.pos.R.string.po_sent_fmt, dateFmt.format(Date(s.orderedAt))), color = DT.SubText, fontSize = 12.sp)
                        }
                        StatusPill(s.status)
                        val phone = s.supplier?.phone.orEmpty()
                        if (phone.isNotBlank()) IconButton(onClick = { dial(context, phone) }) { Icon(Icons.Default.Call, Lang.t("Call supplier", "اتصال بالمورد"), tint = DT.Green) }
                    }
                }
            }

            if (s.editable) item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PoButton(Lang.t("Add item", "إضافة صنف"), { showPicker = true }, Modifier.weight(1f), filled = false)
                    PoButton(Lang.t("Add low stock", "إضافة المخزون المنخفض"), { vm.addLowStock() }, Modifier.weight(1f), filled = false, color = DT.Amber)
                }
            }

            if (s.lines.isEmpty()) item { Text(tr(com.minimart.pos.R.string.no_items_yet), color = DT.SubText, fontSize = 13.sp) }

            itemsIndexed(s.lines, key = { _, l -> l.productId }) { i, l ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(DT.Surface).border(1.dp, DT.Border, RoundedCornerShape(14.dp)).padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(l.name, color = DT.OnSurface, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (s.editable) IconButton(onClick = { vm.removeLine(i) }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.Close, Lang.t("Remove ${l.name}", "إزالة ${l.name}"), tint = DT.Red, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    if (s.editable) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            PoField(l.qtyText, { vm.setQty(i, sanitizeDecimalInput(it, if (l.weighed) 3 else 0, 6)) },
                                if (l.weighed) Lang.t("Qty (kg)", "الكمية (كجم)") else Lang.t("Qty", "الكمية"), Modifier.weight(1f), KeyboardType.Decimal)
                            PoField(l.costText, { vm.setCost(i, sanitizeMoneyInput(it)) }, Lang.t("Unit cost", "تكلفة الوحدة"), Modifier.weight(1f), KeyboardType.Decimal)
                        }
                        Text(tr(com.minimart.pos.R.string.po_line_total, currency, formatPrice(l.total)), color = DT.SubText, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    } else {
                        Text(tr(com.minimart.pos.R.string.po_line_fmt, plainNumber(l.qty, 3), plainNumber(l.receivedQty, 3), formatPrice(l.cost)),
                            color = if (l.remaining > 0) DT.Amber else DT.Green, fontSize = 13.sp)
                        Text("$currency ${formatPrice(l.total)}", color = DT.SubText, fontSize = 12.sp)
                    }
                }
            }

            item {
                PoField(s.notes, vm::setNotes, Lang.t("Notes (optional)", "ملاحظات (اختياري)"), Modifier.fillMaxWidth(), singleLine = false, enabled = s.editable)
            }

            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(DT.Surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row { Text(tr(com.minimart.pos.R.string.order_total), color = DT.SubText, modifier = Modifier.weight(1f)); Text("$currency ${formatPrice(s.total)}", color = DT.OnSurface, fontWeight = FontWeight.ExtraBold) }
                    if (!s.editable && s.status != PurchaseOrderStatus.CANCELLED) {
                        Row { Text(tr(com.minimart.pos.R.string.paid), color = DT.SubText, modifier = Modifier.weight(1f)); Text("$currency ${formatPrice(s.amountPaid)}", color = DT.Green) }
                        Row { Text(tr(com.minimart.pos.R.string.balance), color = DT.SubText, modifier = Modifier.weight(1f)); Text("$currency ${formatPrice(s.balance)}", color = if (s.balance > 0) DT.Amber else DT.Green, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }

        // Actions
        Column(Modifier.fillMaxWidth().background(DT.Surface).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (s.status) {
                PurchaseOrderStatus.DRAFT -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PoButton(Lang.t("Save draft", "حفظ المسودة"), vm::saveDraft, Modifier.weight(1f), filled = false)
                        PoButton(Lang.t("Mark as sent", "تعليم كمُرسل"), vm::markOrdered, Modifier.weight(1f))
                    }
                    if (s.poId != 0L) TextButton(onClick = { confirm = "delete" }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(tr(com.minimart.pos.R.string.delete_draft), color = DT.Red) }
                }
                PurchaseOrderStatus.ORDERED, PurchaseOrderStatus.PARTIAL -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (s.canReceive) PoButton(Lang.t("Receive goods", "استلام البضاعة"), { showReceive = true }, Modifier.weight(1f), color = DT.Green)
                        PoButton(Lang.t("Record payment", "تسجيل دفعة"), { showPay = true }, Modifier.weight(1f), filled = false)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (s.status == PurchaseOrderStatus.PARTIAL) PoButton(Lang.t("Close short", "إغلاق ناقصًا"), { confirm = "short" }, Modifier.weight(1f), filled = false, color = DT.Amber)
                        if (s.lines.none { it.receivedQty > 0 }) PoButton(Lang.t("Cancel order", "إلغاء الطلب"), { confirm = "cancel" }, Modifier.weight(1f), filled = false, color = DT.Red)
                    }
                }
                PurchaseOrderStatus.RECEIVED -> if (s.balance > 0) PoButton(Lang.t("Record payment", "تسجيل دفعة"), { showPay = true }, Modifier.fillMaxWidth())
                PurchaseOrderStatus.CANCELLED -> {}
            }
        }
    }

    if (showPicker) ProductPickerDialog(s, onPick = { vm.addProduct(it) }, onDismiss = { showPicker = false })

    if (showReceive) {
        val entries = remember(s.lines) {
            mutableStateMapOf<Int, String>().also { m -> s.lines.forEachIndexed { i, l -> if (l.remaining > 0) m[i] = plainNumber(l.remaining, 3) } }
        }
        var updateCosts by rememberSaveable { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { showReceive = false }, properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false), containerColor = DT.Surface,
            title = { Text(tr(com.minimart.pos.R.string.receive_goods), color = DT.OnSurface) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.lines.forEachIndexed { i, l ->
                        if (l.remaining > 0) PoField(entries[i].orEmpty(), { entries[i] = sanitizeDecimalInput(it, if (l.weighed) 3 else 0, 6) },
                            "${l.name} (of ${plainNumber(l.remaining, 3)})", Modifier.fillMaxWidth(), KeyboardType.Decimal)
                    }
                    Row(Modifier.fillMaxWidth().clickable(role = androidx.compose.ui.semantics.Role.Button) { updateCosts = !updateCosts }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = updateCosts, onCheckedChange = { updateCosts = it })
                        Text(tr(com.minimart.pos.R.string.update_cost_prices), color = DT.OnSurface, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val q = entries.mapNotNull { (i, t) -> t.toDoubleOrNull()?.takeIf { it > 0 }?.let { i to it } }.toMap()
                    vm.receive(q, updateCosts); showReceive = false
                }) { Text(tr(com.minimart.pos.R.string.add_to_stock), color = DT.Green) }
            },
            dismissButton = { TextButton(onClick = { showReceive = false }) { Text(tr(com.minimart.pos.R.string.cancel), color = DT.SubText) } }
        )
    }

    if (showPay) {
        var amount by remember { mutableStateOf(if (s.balance > 0) plainNumber(s.balance) else "") }
        AlertDialog(
            onDismissRequest = { showPay = false }, properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false), containerColor = DT.Surface,
            title = { Text(tr(com.minimart.pos.R.string.pay_supplier), color = DT.OnSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr(com.minimart.pos.R.string.po_balance_colon, currency, formatPrice(s.balance)), color = DT.SubText, fontSize = 13.sp)
                    PoField(amount, { amount = sanitizeMoneyInput(it) }, Lang.t("Amount paid", "المبلغ المدفوع"), Modifier.fillMaxWidth(), KeyboardType.Decimal)
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.pay(amount.toDoubleOrNull() ?: 0.0); showPay = false }) { Text(tr(com.minimart.pos.R.string.record), color = DT.TealLight) }
            },
            dismissButton = { TextButton(onClick = { showPay = false }) { Text(tr(com.minimart.pos.R.string.cancel), color = DT.SubText) } }
        )
    }

    confirm?.let { kind ->
        val (title, body) = when (kind) {
            "delete" -> Lang.t("Delete draft?", "حذف المسودة؟") to Lang.t("This draft will be removed.", "ستُزال هذه المسودة.")
            "cancel" -> Lang.t("Cancel this order?", "إلغاء هذا الطلب؟") to Lang.t("The order will be marked cancelled.", "سيُعلَّم الطلب كملغى.")
            else -> Lang.t("Close order short?", "إغلاق الطلب ناقصًا؟") to Lang.t("Remaining items will not be received and the order is marked received.", "لن تُستلم الأصناف المتبقية ويُعلَّم الطلب كمستلم.")
        }
        AlertDialog(
            onDismissRequest = { confirm = null }, containerColor = DT.Surface,
            title = { Text(title, color = DT.OnSurface) }, text = { Text(body, color = DT.SubText) },
            confirmButton = {
                TextButton(onClick = {
                    when (kind) { "delete" -> vm.deleteDraft(); "cancel" -> vm.cancel(); else -> vm.closeShort() }
                    confirm = null
                }) { Text(tr(com.minimart.pos.R.string.confirm), color = DT.Red) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(tr(com.minimart.pos.R.string.back), color = DT.SubText) } }
        )
    }
}

@Composable
private fun ProductPickerDialog(s: PoEditorState, onPick: (com.minimart.pos.data.entity.Product) -> Unit, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val used = s.lines.map { it.productId }.toSet()
    val matches = remember(q, s.products, used) {
        val t = q.trim().lowercase()
        s.products.filter { it.id !in used && (t.isEmpty() || it.name.lowercase().contains(t) || it.barcode.contains(t)) }.take(40)
    }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = DT.Surface,
        title = { Text(tr(com.minimart.pos.R.string.add_item), color = DT.OnSurface) },
        text = {
            Column {
                PoField(q, { q = it.take(40) }, Lang.t("Search products", "ابحث عن المنتجات"), Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(matches, key = { it.id }) { p ->
                        Text(p.name, color = DT.OnSurface, modifier = Modifier.fillMaxWidth().clickable(role = androidx.compose.ui.semantics.Role.Button) { onPick(p) }.padding(vertical = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr(com.minimart.pos.R.string.done), color = DT.TealLight) } }
    )
}
