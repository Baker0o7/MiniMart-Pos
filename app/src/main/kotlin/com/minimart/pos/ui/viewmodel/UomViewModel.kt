package com.minimart.pos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimart.pos.data.entity.ProductUnit
import com.minimart.pos.data.entity.Uom
import com.minimart.pos.data.repository.UomError
import com.minimart.pos.data.repository.UomRepository
import com.minimart.pos.data.repository.UomResult
import com.minimart.pos.util.Lang
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Plain words for why a unit change was refused. */
fun uomErrorText(e: UomError): String = when (e) {
    UomError.BAD_NAME -> Lang.t("Use a short name with letters or numbers (up to 30 characters).", "استخدم اسمًا قصيرًا من حروف أو أرقام (حتى 30 حرفًا).")
    UomError.DUPLICATE -> Lang.t("That unit already exists.", "هذه الوحدة موجودة بالفعل.")
    UomError.IN_USE -> Lang.t("Some products use this unit, so it can't be deleted. Hide it instead.", "بعض المنتجات تستخدم هذه الوحدة فلا يمكن حذفها. أخفِها بدلًا من ذلك.")
    UomError.BAD_FACTOR -> Lang.t("A pack must hold a whole number of units, at least 2.", "يجب أن تحتوي العبوة على عدد صحيح من الوحدات، 2 على الأقل.")
    UomError.UNKNOWN_UNIT -> Lang.t("Pick a unit from the list first (Units screen).", "اختر وحدة من القائمة أولًا (شاشة الوحدات).")
    UomError.SAME_AS_BASE -> Lang.t("That is already this product's main unit.", "هذه هي الوحدة الأساسية لهذا المنتج بالفعل.")
    UomError.NO_PRODUCT -> Lang.t("Save the product first.", "احفظ المنتج أولًا.")
    UomError.WEIGHED_PRODUCT -> Lang.t("Weighed products are sold by the kilogram and take no pack sizes.", "المنتجات الموزونة تُباع بالكيلوغرام ولا تأخذ أحجام عبوات.")
    UomError.NOT_FOUND -> Lang.t("That unit no longer exists.", "هذه الوحدة لم تعد موجودة.")
    UomError.BAD_BARCODE -> Lang.t("A barcode has 4 to 40 letters or digits, with no spaces.", "الباركود من 4 إلى 40 حرفًا أو رقمًا بدون مسافات.")
    UomError.BARCODE_TAKEN -> Lang.t("That barcode is already used by a product or another pack.", "هذا الباركود مستخدم بالفعل لمنتج أو عبوة أخرى.")
    UomError.BAD_PRICE -> Lang.t("Enter a price or cost that is zero or more (leave empty to use the automatic one).", "أدخل سعرًا أو تكلفة صفرًا أو أكثر (اتركه فارغًا للحساب التلقائي).")
}

data class UomUiState(val units: List<Uom> = emptyList(), val message: String? = null, val isError: Boolean = false)

@HiltViewModel
class UomViewModel @Inject constructor(private val repo: UomRepository) : ViewModel() {
    private val _msg = MutableStateFlow<Pair<String, Boolean>?>(null)

    val state: StateFlow<UomUiState> = combine(repo.units, _msg) { units, m -> UomUiState(units, m?.first, m?.second == true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(3000), UomUiState())

    /** Pack sizes of every product, by product id. */
    val packs: StateFlow<Map<Long, List<ProductUnit>>> = repo.packsByProduct
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(3000), emptyMap())

    fun clearMessage() { _msg.value = null }

    private fun report(r: UomResult, ok: String) {
        _msg.value = when (r) { UomResult.Ok -> ok to false; is UomResult.Refused -> uomErrorText(r.error) to true }
    }

    fun add(name: String, code: String) = viewModelScope.launch { report(repo.addUnit(name, code.ifBlank { null }), Lang.t("Unit added.", "تمت إضافة الوحدة.")) }
    fun edit(id: Long, name: String, code: String) = viewModelScope.launch { report(repo.edit(id, name, code), Lang.t("Unit saved.", "تم حفظ الوحدة.")) }
    fun setActive(id: Long, active: Boolean) = viewModelScope.launch { report(repo.setActive(id, active), if (active) Lang.t("Unit shown again.", "أُعيد إظهار الوحدة.") else Lang.t("Unit hidden from the pickers.", "أُخفيت الوحدة من القوائم.")) }
    fun delete(id: Long) = viewModelScope.launch { report(repo.delete(id), Lang.t("Unit deleted.", "تم حذف الوحدة.")) }

    /** Returns null when saved, otherwise the reason in plain words. */
    suspend fun savePack(
        productId: Long, code: String, factor: Int, sellable: Boolean, purchasable: Boolean,
        price: Double = 0.0, barcode: String = "", cost: Double = 0.0
    ): String? =
        (repo.savePack(productId, code, factor, sellable, purchasable, price, barcode, cost) as? UomResult.Refused)?.let { uomErrorText(it.error) }

    fun removePack(productId: Long, code: String) = viewModelScope.launch { repo.removePack(productId, code) }
}
