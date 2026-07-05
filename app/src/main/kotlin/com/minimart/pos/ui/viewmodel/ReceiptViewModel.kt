package com.minimart.pos.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimart.pos.data.entity.SaleWithItems
import com.minimart.pos.data.repository.SaleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReceiptUiState(
    val saleWithItems: SaleWithItems? = null,
    val isLoading: Boolean = true,
    val isProcessing: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class ReceiptViewModel @Inject constructor(
    private val saleRepo: SaleRepository,
    // Bug fix: previously had no SavedStateHandle — saleId was passed via LaunchedEffect
    // from the composable after every recomposition. On process death, the ViewModel
    // was recreated with no saleId, showing a blank screen until LaunchedEffect ran.
    // SavedStateHandle persists the saleId across process death/recreation automatically
    // because Hilt wires it from the NavBackStackEntry arguments.
    private val savedState: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(ReceiptUiState())
    val state: StateFlow<ReceiptUiState> = _state.asStateFlow()

    init {
        // Auto-load if saleId is already in saved state (e.g. after process death)
        val savedSaleId = savedState.get<Long>("saleId") ?: 0L
        if (savedSaleId > 0L) loadSale(savedSaleId)
    }

    fun loadSale(saleId: Long) {
        savedState["saleId"] = saleId           // persist for process-death recovery
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val sale = saleRepo.getSaleWithItems(saleId)
                _state.update { it.copy(saleWithItems = sale, isLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun refundSale(reason: String) {
        val saleId = _state.value.saleWithItems?.sale?.id ?: return
        viewModelScope.launch {
            _state.update { it.copy(isProcessing = true, error = null) }
            try {
                saleRepo.refundSale(saleId, reason)
                val updated = saleRepo.getSaleWithItems(saleId)
                _state.update { it.copy(saleWithItems = updated, isProcessing = false,
                    successMessage = "Sale refunded. Stock restored.") }
            } catch (e: Exception) {
                _state.update { it.copy(isProcessing = false, error = "Refund failed: ${e.message}") }
            }
        }
    }

    fun voidSale(reason: String) {
        val saleId = _state.value.saleWithItems?.sale?.id ?: return
        viewModelScope.launch {
            _state.update { it.copy(isProcessing = true, error = null) }
            try {
                saleRepo.voidSale(saleId, reason)
                val updated = saleRepo.getSaleWithItems(saleId)
                _state.update { it.copy(saleWithItems = updated, isProcessing = false,
                    successMessage = "Sale voided. Stock restored.") }
            } catch (e: Exception) {
                _state.update { it.copy(isProcessing = false, error = "Void failed: ${e.message}") }
            }
        }
    }

    fun clearMessages() { _state.update { it.copy(successMessage = null, error = null) } }
}
