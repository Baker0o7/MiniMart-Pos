package com.minimart.pos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimart.pos.data.entity.Product
import com.minimart.pos.data.repository.ProductRepository
import com.minimart.pos.data.repository.SettingsRepository
import com.minimart.pos.printer.LabelSpec
import com.minimart.pos.printer.PrintResult
import com.minimart.pos.printer.ThermalPrinter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LabelUiState(
    val products: List<Product> = emptyList(),
    val query: String = "",
    val copies: Map<Long, Int> = emptyMap(),
    val showPrice: Boolean = true,
    val printing: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    val currency: String = "KES",
    val printerConnected: Boolean = false
) {
    val visible: List<Product> get() {
        val t = query.trim().lowercase()
        return if (t.isEmpty()) products else products.filter { it.name.lowercase().contains(t) || it.barcode.contains(t) }
    }
    val totalLabels: Int get() = copies.values.sum()
}

@HiltViewModel
class LabelPrintViewModel @Inject constructor(
    private val productRepo: ProductRepository,
    private val settings: SettingsRepository,
    private val printer: ThermalPrinter
) : ViewModel() {

    private val _state = MutableStateFlow(LabelUiState(printerConnected = printer.isConnected))
    val uiState: StateFlow<LabelUiState> = _state

    init {
        viewModelScope.launch {
            productRepo.getAllProducts().catch { emit(emptyList()) }.collect { list ->
                _state.update { s -> s.copy(products = list.filter { it.barcode.isNotBlank() }) }
            }
        }
        viewModelScope.launch {
            settings.currency.catch { }.collect { c -> _state.update { it.copy(currency = c) } }
        }
    }

    fun setQuery(q: String) { _state.update { it.copy(query = q.take(40)) } }
    fun setShowPrice(v: Boolean) { _state.update { it.copy(showPrice = v) } }
    fun clearMessage() { _state.update { it.copy(message = null) } }

    fun setCopies(productId: Long, n: Int) {
        _state.update { s ->
            val m = s.copies.toMutableMap()
            if (n <= 0) m.remove(productId) else m[productId] = n.coerceAtMost(99)
            s.copy(copies = m)
        }
    }

    fun clearSelection() { _state.update { it.copy(copies = emptyMap()) } }

    /** One label for every product at or below its low-stock threshold. */
    fun selectLowStock() {
        _state.update { s ->
            val low = s.products.filter { if (it.isWeighed) it.stockKg <= it.lowStockThreshold else it.stock <= it.lowStockThreshold }
            s.copy(copies = s.copies + low.associate { it.id to (s.copies[it.id] ?: 1) })
        }
    }

    fun print() {
        val s = _state.value
        if (s.copies.isEmpty() || s.printing) return
        val byId = s.products.associateBy { it.id }
        val jobs = s.copies.mapNotNull { (id, n) ->
            byId[id]?.let { p -> LabelSpec(p.name, p.barcode, if (p.isWeighed) p.pricePerKg else p.price, n) }
        }
        viewModelScope.launch {
            _state.update { it.copy(printing = true, message = null) }
            val r = try { printer.printLabels(jobs, s.showPrice, s.currency) }
                    catch (e: Exception) { PrintResult.Error(e.message ?: "Print failed") }
            _state.update {
                when (r) {
                    is PrintResult.Success -> it.copy(printing = false, printerConnected = true, message = "Printed ${jobs.sumOf { j -> j.copies }} label(s)", isError = false)
                    is PrintResult.Error -> it.copy(printing = false, printerConnected = printer.isConnected,
                        message = if (printer.isConnected) r.message else "Printer not connected. Connect one in Settings.", isError = true)
                }
            }
        }
    }
}
