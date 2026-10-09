package com.minimart.pos.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimart.pos.data.entity.Product
import com.minimart.pos.data.entity.PurchaseOrder
import com.minimart.pos.data.entity.PurchaseOrderItem
import com.minimart.pos.data.entity.PurchaseOrderStatus
import com.minimart.pos.data.entity.Supplier
import com.minimart.pos.data.repository.ProductRepository
import com.minimart.pos.data.repository.PurchasingRepository
import com.minimart.pos.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.floor

data class SupplierRow(val supplier: Supplier, val openOrders: Int, val owed: Double)

data class OrderRow(
    val order: PurchaseOrder,
    val supplierName: String,
    val total: Double,
    val itemCount: Int
) {
    val balance: Double get() = when (order.status) {
        PurchaseOrderStatus.DRAFT, PurchaseOrderStatus.CANCELLED -> 0.0
        else -> (total - order.amountPaid).coerceAtLeast(0.0)
    }
}

data class PurchasingUiState(
    val suppliers: List<SupplierRow> = emptyList(),
    val orders: List<OrderRow> = emptyList(),
    val filter: PurchaseOrderStatus? = null,
    val totalOwed: Double = 0.0,
    val importableSuppliers: Int = 0,
    val message: String? = null,
    val isError: Boolean = false
)

private fun owedOn(po: PurchaseOrder, items: List<PurchaseOrderItem>): Double {
    if (po.status == PurchaseOrderStatus.DRAFT || po.status == PurchaseOrderStatus.CANCELLED) return 0.0
    return (items.sumOf { it.receivedValue } - po.amountPaid).coerceAtLeast(0.0)
}

@HiltViewModel
class PurchasingViewModel @Inject constructor(
    private val repo: PurchasingRepository,
    private val products: ProductRepository
) : ViewModel() {

    private val _filter = MutableStateFlow<PurchaseOrderStatus?>(null)
    private val _msg = MutableStateFlow<Pair<String, Boolean>?>(null)
    private val _state = MutableStateFlow(PurchasingUiState())
    val uiState: StateFlow<PurchasingUiState> = _state

    init {
        viewModelScope.launch {
            combine(
                repo.getAllSuppliers(), repo.getOrders(), repo.getAllItems(),
                products.getAllProducts(), _filter
            ) { suppliers, orders, items, prods, filter ->
                val byPo = items.groupBy { it.poId }
                val names = suppliers.associate { it.id to it.name }
                val rows = suppliers.filter { it.isActive }.map { s ->
                    val mine = orders.filter { it.supplierId == s.id }
                    SupplierRow(
                        s,
                        openOrders = mine.count { it.status == PurchaseOrderStatus.ORDERED || it.status == PurchaseOrderStatus.PARTIAL },
                        owed = mine.sumOf { owedOn(it, byPo[it.id].orEmpty()) }
                    )
                }
                val orderRows = orders.map { po ->
                    val its = byPo[po.id].orEmpty()
                    OrderRow(po, names[po.supplierId] ?: "Supplier", its.sumOf { it.lineTotal }, its.size)
                }
                val known = suppliers.map { it.name.trim().lowercase() }.toSet()
                val importable = prods.map { it.supplierName.trim() }
                    .filter { it.isNotEmpty() && it.lowercase() !in known }
                    .distinctBy { it.lowercase() }.size
                PurchasingUiState(
                    suppliers = rows,
                    orders = orderRows.filter { filter == null || it.order.status == filter },
                    filter = filter,
                    totalOwed = orders.sumOf { owedOn(it, byPo[it.id].orEmpty()) },
                    importableSuppliers = importable
                )
            }.catch { }.collect { s ->
                _state.update { old -> s.copy(message = old.message, isError = old.isError) }
            }
        }
    }

    fun setFilter(f: PurchaseOrderStatus?) { _filter.value = f }
    fun clearMessage() { _state.update { it.copy(message = null) } }
    private fun say(text: String, error: Boolean = false) { _state.update { it.copy(message = text, isError = error) } }

    fun saveSupplier(s: Supplier) {
        val name = s.name.trim()
        if (name.isEmpty()) { say("Supplier name is required", true); return }
        viewModelScope.launch {
            try {
                repo.saveSupplier(s.copy(name = name, phone = s.phone.trim(), email = s.email.trim(),
                    address = s.address.trim(), notes = s.notes.trim()))
                say("Saved $name")
            } catch (e: Exception) { say(e.message ?: "Could not save supplier", true) }
        }
    }

    fun deactivateSupplier(id: Long) {
        viewModelScope.launch {
            try { repo.deactivateSupplier(id); say("Supplier removed") }
            catch (e: Exception) { say(e.message ?: "Could not remove supplier", true) }
        }
    }

    /** Creates suppliers from the names/phones already typed into products. */
    fun importFromProducts() {
        viewModelScope.launch {
            try {
                val existing = repo.getAllSuppliers().first().map { it.name.trim().lowercase() }.toMutableSet()
                var added = 0
                products.getAllProducts().first()
                    .filter { it.supplierName.isNotBlank() }
                    .groupBy { it.supplierName.trim().lowercase() }
                    .forEach { (key, list) ->
                        if (key in existing) return@forEach
                        val phone = list.map { it.supplierPhone.trim() }.firstOrNull { it.isNotEmpty() } ?: ""
                        repo.saveSupplier(Supplier(name = list.first().supplierName.trim(), phone = phone))
                        existing += key
                        added++
                    }
                say(if (added == 0) "No new suppliers found" else "Imported $added supplier${if (added == 1) "" else "s"}")
            } catch (e: Exception) { say(e.message ?: "Import failed", true) }
        }
    }
}

// ─── Editor ───────────────────────────────────────────────────────────────────────────────

data class PoLine(
    val productId: Long,
    val name: String,
    val weighed: Boolean,
    val qtyText: String,
    val costText: String,
    val receivedQty: Double = 0.0,
    val itemId: Long = 0
) {
    val qty: Double get() = parseQty(qtyText, weighed)
    val cost: Double get() = costText.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: 0.0
    val total: Double get() = qty * cost
    val remaining: Double get() = (qty - receivedQty).coerceAtLeast(0.0)
}

private fun parseQty(text: String, weighed: Boolean): Double {
    val v = text.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 } ?: return 0.0
    return if (weighed) v else floor(v)
}

data class PoEditorState(
    val loading: Boolean = true,
    val poId: Long = 0,
    val status: PurchaseOrderStatus = PurchaseOrderStatus.DRAFT,
    val supplierId: Long = 0,
    val notes: String = "",
    val amountPaid: Double = 0.0,
    val orderedAt: Long = 0,
    val createdAt: Long = 0,
    val lines: List<PoLine> = emptyList(),
    val suppliers: List<Supplier> = emptyList(),
    val products: List<Product> = emptyList(),
    val storeName: String = "",
    val message: String? = null,
    val isError: Boolean = false,
    val finished: Boolean = false
) {
    val editable: Boolean get() = status == PurchaseOrderStatus.DRAFT
    val supplier: Supplier? get() = suppliers.firstOrNull { it.id == supplierId }
    val total: Double get() = lines.sumOf { it.total }
    val receivedValue: Double get() = lines.sumOf { it.receivedQty * it.cost }
    val balance: Double get() = (total - amountPaid).coerceAtLeast(0.0)
    val canReceive: Boolean get() =
        (status == PurchaseOrderStatus.ORDERED || status == PurchaseOrderStatus.PARTIAL) && lines.any { it.remaining > 0 }
}

@HiltViewModel
class PurchaseOrderEditorViewModel @Inject constructor(
    private val repo: PurchasingRepository,
    private val productRepo: ProductRepository,
    private val settings: SettingsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val initialId: Long = savedStateHandle.get<Long>("poId") ?: 0L

    private val _state = MutableStateFlow(PoEditorState(poId = initialId, loading = initialId != 0L))
    val uiState: StateFlow<PoEditorState> = _state

    init {
        viewModelScope.launch {
            repo.getSuppliers().catch { emit(emptyList()) }.collect { list -> _state.update { it.copy(suppliers = list) } }
        }
        viewModelScope.launch {
            productRepo.getAllProducts().catch { emit(emptyList()) }.collect { list -> _state.update { it.copy(products = list) } }
        }
        viewModelScope.launch {
            val name = try { settings.storeName.first() } catch (_: Exception) { "" }
            _state.update { it.copy(storeName = name) }
        }
        if (initialId != 0L) viewModelScope.launch { load(initialId) }
    }

    private suspend fun load(id: Long) {
        val po = repo.getOrder(id)
        if (po == null) { _state.update { it.copy(loading = false, message = "Order not found", isError = true) }; return }
        val items = repo.getItems(id)
        val prods = productRepo.getAllProducts().first().associateBy { it.id }
        val lines = items.map { i ->
            val weighed = prods[i.productId]?.isWeighed == true
            PoLine(i.productId, i.productName, weighed,
                qtyText = fmt(i.quantity, weighed), costText = fmt(i.unitCost, true, 2),
                receivedQty = i.receivedQty, itemId = i.id)
        }
        _state.update {
            it.copy(loading = false, poId = po.id, status = po.status, supplierId = po.supplierId,
                notes = po.notes, amountPaid = po.amountPaid, orderedAt = po.orderedAt,
                createdAt = po.createdAt, lines = lines)
        }
    }

    private fun fmt(v: Double, decimals: Boolean, max: Int = 3): String {
        val s = String.format(java.util.Locale.US, "%.${if (decimals) max else 0}f", v)
        return if ('.' in s) s.trimEnd('0').trimEnd('.') else s
    }

    private fun say(text: String, error: Boolean = false) { _state.update { it.copy(message = text, isError = error) } }
    fun clearMessage() { _state.update { it.copy(message = null) } }

    fun setSupplier(id: Long) { if (_state.value.editable) _state.update { it.copy(supplierId = id) } }
    fun setNotes(text: String) { if (_state.value.editable) _state.update { it.copy(notes = text.take(300)) } }

    fun setQty(index: Int, text: String) = editLine(index) { it.copy(qtyText = text) }
    fun setCost(index: Int, text: String) = editLine(index) { it.copy(costText = text) }
    private fun editLine(index: Int, f: (PoLine) -> PoLine) {
        _state.update { s ->
            if (!s.editable || index !in s.lines.indices) s
            else s.copy(lines = s.lines.toMutableList().also { it[index] = f(it[index]) })
        }
    }

    fun removeLine(index: Int) {
        _state.update { s ->
            if (!s.editable || index !in s.lines.indices) s
            else s.copy(lines = s.lines.toMutableList().also { it.removeAt(index) })
        }
    }

    private fun newLine(p: Product): PoLine {
        val suggested = when {
            p.reorderQuantity > 0 -> p.reorderQuantity.toDouble()
            p.isWeighed -> 10.0
            else -> (p.lowStockThreshold * 2 - p.stock).coerceAtLeast(1).toDouble()
        }
        return PoLine(p.id, p.name, p.isWeighed, fmt(suggested, p.isWeighed),
            if (p.costPrice > 0) fmt(p.costPrice, true, 2) else "")
    }

    fun addProduct(p: Product) {
        _state.update { s ->
            if (!s.editable || s.lines.any { it.productId == p.id }) s
            else s.copy(lines = s.lines + newLine(p))
        }
    }

    /** Adds every low-stock product (preferring this supplier's own products when it has any). */
    fun addLowStock() {
        val s = _state.value
        if (!s.editable) return
        val low = s.products.filter { p ->
            (if (p.isWeighed) p.stockKg <= p.lowStockThreshold else p.stock <= p.lowStockThreshold) &&
                s.lines.none { it.productId == p.id }
        }
        val name = s.supplier?.name?.trim()?.lowercase()
        val mine = if (name != null) low.filter { it.supplierName.trim().lowercase() == name } else emptyList()
        val chosen = if (mine.isNotEmpty()) mine else low
        if (chosen.isEmpty()) { say("No low-stock items to add"); return }
        _state.update { it.copy(lines = it.lines + chosen.map(::newLine)) }
        say("Added ${chosen.size} item${if (chosen.size == 1) "" else "s"}")
    }

    private fun validate(): String? {
        val s = _state.value
        if (s.supplierId == 0L || s.supplier == null) return "Choose a supplier"
        if (s.lines.isEmpty()) return "Add at least one item"
        if (s.lines.any { it.qty <= 0.0 }) return "Every item needs a quantity"
        return null
    }

    private suspend fun persistDraft(): Long {
        val s = _state.value
        val items = s.lines.map {
            PurchaseOrderItem(poId = 0, productId = it.productId, productName = it.name, quantity = it.qty, unitCost = it.cost)
        }
        val id = repo.saveDraft(PurchaseOrder(id = s.poId, supplierId = s.supplierId, notes = s.notes.trim()), items)
        _state.update { it.copy(poId = id) }
        return id
    }

    private fun run(success: String?, finish: Boolean = false, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                val id = _state.value.poId
                if (id != 0L && !finish) load(id)
                if (success != null) say(success)
                if (finish) _state.update { it.copy(finished = true) }
            } catch (e: Exception) { say(e.message ?: "Something went wrong", true) }
        }
    }

    fun saveDraft() {
        validate()?.let { say(it, true); return }
        run("Draft saved") { persistDraft() }
    }

    fun markOrdered() {
        validate()?.let { say(it, true); return }
        run("Order marked as sent") { repo.markOrdered(persistDraft()) }
    }

    fun receive(quantities: Map<Int, Double>, updateCosts: Boolean) {
        val s = _state.value
        val byItem = quantities.mapNotNull { (idx, q) ->
            s.lines.getOrNull(idx)?.takeIf { q > 0 }?.let { it.itemId to q }
        }.toMap()
        if (byItem.isEmpty()) { say("Enter a quantity received", true); return }
        run("Stock updated") { repo.receive(s.poId, byItem, updateCosts) }
    }

    fun pay(amount: Double) { val id = _state.value.poId; run("Payment recorded") { repo.recordPayment(id, amount) } }
    fun cancel() { val id = _state.value.poId; run("Order cancelled") { repo.cancel(id) } }
    fun closeShort() { val id = _state.value.poId; run("Order closed") { repo.closeShort(id) } }
    fun deleteDraft() { val id = _state.value.poId; run(null, finish = true) { if (id != 0L) repo.deleteDraft(id) } }
}
