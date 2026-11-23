package com.minimart.pos.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minimart.pos.data.entity.Uom
import com.minimart.pos.ui.components.Text
import com.minimart.pos.ui.theme.DT
import com.minimart.pos.ui.viewmodel.UomViewModel
import com.minimart.pos.util.Lang

/** Owner/manager screen for the managed list of units (pcs, kg, carton...). Pack sizes are set per product in the product dialog. */
@Composable
fun UomScreen(onBack: () -> Unit, vm: UomViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Uom?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Uom?>(null) }

    Column(Modifier.fillMaxSize().background(DT.Bg)) {
        GradientHeader(Lang.t("Units of measure", "وحدات القياس"), Lang.t("${s.units.size} units", "${s.units.size} وحدة"), onBack) {
            HeaderPillButton(Lang.t("Add unit", "إضافة وحدة"), Icons.Default.Add) { adding = true }
        }
        FeedbackBanner(s.message, s.isError)
        Text(Lang.t("Stock and sales are always counted in a product's main unit. Add pack sizes (1 carton = 24 pcs) in the product's edit screen to sell or order in packs.",
            "يُحسب المخزون والمبيعات دائمًا بالوحدة الأساسية للمنتج. أضف أحجام العبوات (1 كرتون = 24 قطعة) من شاشة تعديل المنتج للبيع أو الطلب بالعبوات."),
            color = DT.SubText, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(s.units, key = { it.id }) { u ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(DT.Surface)
                        .border(1.dp, DT.Border, RoundedCornerShape(14.dp)).padding(start = 14.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(u.name, color = if (u.isActive) DT.OnSurface else DT.SubText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(u.code + if (u.isActive) "" else " · " + Lang.t("hidden", "مخفية"), color = DT.SubText, fontSize = 12.sp)
                    }
                    Switch(checked = u.isActive, onCheckedChange = { vm.setActive(u.id, it) },
                        modifier = Modifier.semantics { contentDescription = Lang.t("Show ${u.name} in lists", "إظهار ${u.name} في القوائم") })
                    IconButton(onClick = { editing = u }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Edit, Lang.t("Edit ${u.name}", "تعديل ${u.name}"), tint = DT.Teal, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { deleting = u }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Delete, Lang.t("Delete ${u.name}", "حذف ${u.name}"), tint = DT.Red, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }

    if (adding) UnitDialog(null, onDismiss = { adding = false }) { name, code -> vm.add(name, code); adding = false }
    editing?.let { u -> UnitDialog(u, onDismiss = { editing = null }) { name, code -> vm.edit(u.id, name, code); editing = null } }
    deleting?.let { u ->
        AlertDialog(onDismissRequest = { deleting = null }, containerColor = DT.Surface,
            title = { Text(Lang.t("Delete ${u.name}?", "حذف ${u.name}؟"), color = DT.OnSurface, fontWeight = FontWeight.Bold) },
            text = { Text(Lang.t("Only units no product uses can be deleted.", "يمكن حذف الوحدات التي لا يستخدمها أي منتج فقط."), color = DT.SubText) },
            confirmButton = { TextButton(onClick = { vm.delete(u.id); deleting = null }) { Text(Lang.t("Delete", "حذف"), color = DT.Red) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(Lang.t("Cancel", "إلغاء"), color = DT.SubText) } })
    }
}

@Composable
private fun UnitDialog(unit: Uom?, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf(unit?.name ?: "") }
    var code by remember { mutableStateOf(unit?.code ?: "") }
    AlertDialog(onDismissRequest = onDismiss, containerColor = DT.Surface,
        title = { Text(if (unit == null) Lang.t("New unit", "وحدة جديدة") else Lang.t("Edit unit", "تعديل الوحدة"), color = DT.OnSurface, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                UomTextField(name, { name = it.take(30) }, Lang.t("Name (e.g. Carton)", "الاسم (مثل كرتون)"))
                UomTextField(code, { code = it.take(20) }, Lang.t("Short code (shown on receipts)", "رمز قصير (يظهر على الإيصالات)"))
                if (unit != null) Text(Lang.t("Changing the code updates every product that uses it.", "تغيير الرمز يحدّث كل منتج يستخدمه."), color = DT.SubText, fontSize = 11.sp)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, code.ifBlank { name }) }, enabled = name.isNotBlank()) { Text(Lang.t("Save", "حفظ"), color = DT.Teal) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Lang.t("Cancel", "إلغاء"), color = DT.SubText) } })
}

/** Small text field in the app's dark style, shared by the unit and pack dialogs. */
@Composable
internal fun UomTextField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier.fillMaxWidth(),
                          keyboard: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text) {
    OutlinedTextField(value, onChange, label = { Text(label, fontSize = 12.sp) }, singleLine = true, modifier = modifier,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboard),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DT.Teal, unfocusedBorderColor = DT.Border,
            focusedTextColor = DT.OnSurface, unfocusedTextColor = DT.OnSurface, cursorColor = DT.Teal,
            focusedContainerColor = DT.Bg, unfocusedContainerColor = DT.Bg))
}

/**
 * Pick the product's main unit from the managed list (hidden units are left out), with the free text field still available
 * for anything not listed yet.
 */
@Composable
internal fun UnitChips(selected: String, onPick: (String) -> Unit, vm: UomViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val shown = s.units.filter { it.isActive || it.code == selected }
    if (shown.isEmpty()) return
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(shown, key = { it.id }) { u ->
            FilterChip(selected = u.code == selected, onClick = { onPick(u.code) }, label = { Text(u.code, fontSize = 12.sp) })
        }
    }
}

/**
 * Pack sizes of one product ("1 carton = 24 pcs"). Shown when editing an existing product, because a pack belongs to a
 * saved product. Packs only change how a quantity is typed or shown; stock stays in the main unit.
 */
@Composable
internal fun PackSizesSection(productId: Long, baseUnit: String, isWeighed: Boolean, vm: UomViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val allPacks by vm.packs.collectAsStateWithLifecycle()
    val packs = allPacks[productId].orEmpty()
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var factor by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(Lang.t("Pack sizes", "أحجام العبوات"), color = DT.OnSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        if (isWeighed) {
            Text(Lang.t("Weighed products are sold by the kilogram and take no pack sizes.", "المنتجات الموزونة تُباع بالكيلوغرام ولا تأخذ أحجام عبوات."), color = DT.SubText, fontSize = 11.sp)
            return@Column
        }
        packs.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("1 ${p.unitCode} = ${p.factor} $baseUnit", color = DT.OnSurface, fontSize = 13.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.removePack(productId, p.unitCode) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(Lang.t("Remove", "إزالة"), color = DT.Red, fontSize = 12.sp)
                }
            }
        }
        Text(Lang.t("Add a pack: pick the unit, then how many $baseUnit it holds.", "أضف عبوة: اختر الوحدة ثم عدد الـ$baseUnit التي تحتويها."), color = DT.SubText, fontSize = 11.sp)
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(s.units.filter { it.isActive && it.code != baseUnit && packs.none { p -> p.unitCode == it.code } }, key = { it.id }) { u ->
                FilterChip(selected = code == u.code, onClick = { code = u.code; error = null }, label = { Text(u.code, fontSize = 12.sp) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            UomTextField(factor, { factor = it.filter(Char::isDigit).take(6); error = null }, Lang.t("Holds how many $baseUnit", "عدد الـ$baseUnit"),
                Modifier.weight(1f), androidx.compose.ui.text.input.KeyboardType.Number)
            Button(enabled = code.isNotBlank() && factor.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp),
                onClick = {
                    val f = com.minimart.pos.util.UnitMath.parseFactor(factor)
                    if (f == null) { error = com.minimart.pos.ui.viewmodel.uomErrorText(com.minimart.pos.data.repository.UomError.BAD_FACTOR); return@Button }
                    scope.launch {
                        val problem = vm.savePack(productId, code, f, sellable = true, purchasable = true)
                        error = problem
                        if (problem == null) { code = ""; factor = "" }
                    }
                }) { Text(Lang.t("Add pack", "إضافة عبوة"), fontSize = 12.sp) }
        }
        error?.let { Text(it, color = DT.Red, fontSize = 12.sp) }
    }
}
