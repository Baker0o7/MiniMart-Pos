package com.minimart.pos.ui.viewmodel

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
    val error: String? = null
)

@HiltViewModel
class ReceiptViewModel @Inject constructor(
    private val saleRepo: SaleRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ReceiptUiState())
    val state: StateFlow<ReceiptUiState> = _state.asStateFlow()

    fun loadSale(saleId: Long) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val sale = saleRepo.getSaleWithItems(saleId)
                _state.update { it.copy(saleWithItems = sale, isLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
