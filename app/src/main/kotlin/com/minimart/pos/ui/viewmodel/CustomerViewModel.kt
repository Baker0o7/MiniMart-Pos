package com.minimart.pos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimart.pos.data.entity.CreditTransaction
import com.minimart.pos.data.entity.Customer
import com.minimart.pos.data.repository.CustomerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CustomerUiState(
    val customers: List<Customer> = emptyList(),
    val selectedCustomer: Customer? = null,
    val transactions: List<CreditTransaction> = emptyList(),
    val query: String = "",
    val isLoading: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class CustomerViewModel @Inject constructor(
    private val repo: CustomerRepository
) : ViewModel() {

    private val _query   = MutableStateFlow("")
    private val _state   = MutableStateFlow(CustomerUiState())
    val uiState: StateFlow<CustomerUiState> = _state

    init {
        viewModelScope.launch {
            _query.debounce(200).collectLatest { q ->
                val flow = if (q.isBlank()) repo.getAllCustomers() else repo.searchCustomers(q)
                flow.collect { list ->
                    _state.update { it.copy(customers = list) }
                }
            }
        }
    }

    fun setQuery(q: String) { _query.value = q; _state.update { it.copy(query = q) } }

    fun selectCustomer(customer: Customer?) {
        _state.update { it.copy(selectedCustomer = customer) }
        if (customer != null) {
            viewModelScope.launch {
                repo.getTransactions(customer.id).collect { txs ->
                    _state.update { it.copy(transactions = txs) }
                }
            }
        }
    }

    fun saveCustomer(customer: Customer) = viewModelScope.launch {
        try {
            repo.saveCustomer(customer)
            _state.update { it.copy(message = "Customer saved") }
        } catch (e: Exception) {
            _state.update { it.copy(message = "Error: ${e.localizedMessage}") }
        }
    }

    fun deleteCustomer(customer: Customer) = viewModelScope.launch {
        repo.deleteCustomer(customer)
        _state.update { it.copy(message = "Customer deleted") }
    }

    fun addCredit(customerId: Long, amount: Double, notes: String = "") = viewModelScope.launch {
        val ok = repo.addCredit(customerId, amount, notes)
        if (ok) {
            _state.update { it.copy(message = "KES ${String.format("%.2f", amount)} credit added") }
            // Refresh selected customer
            val updated = repo.getById(customerId)
            _state.update { it.copy(selectedCustomer = updated) }
        } else {
            _state.update { it.copy(message = "Customer not found") }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
}
