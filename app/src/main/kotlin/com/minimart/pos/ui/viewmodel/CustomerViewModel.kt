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

@OptIn(kotlinx.coroutines.FlowPreview::class)
@HiltViewModel
class CustomerViewModel @Inject constructor(
    private val repo: CustomerRepository
) : ViewModel() {

    private val _query   = MutableStateFlow("")
    private val _state   = MutableStateFlow(CustomerUiState())
    val uiState: StateFlow<CustomerUiState> = _state

    // Bug fix: previously selectCustomer() launched a brand-new viewModelScope.launch {
    // repo.getTransactions(id).collect {...} } on every call, with nothing cancelling the
    // PREVIOUS collector. Expanding customer A then B then C left three live Flow
    // collectors running forever — and if A's transactions changed later (e.g. via LAN
    // sync) while C was the one displayed, A's stale collector would fire and silently
    // overwrite the screen with the wrong customer's data. flatMapLatest auto-cancels the
    // previous inner Flow the moment a new customer ID comes in, same pattern already used
    // for the search debounce below.
    private val _selectedCustomerId = MutableStateFlow<Long?>(null)

    init {
        viewModelScope.launch {
            _query.debounce(200).collectLatest { q ->
                val flow = if (q.isBlank()) repo.getAllCustomers() else repo.searchCustomers(q)
                flow.catch { emit(emptyList()) }.collect { list ->
                    _state.update { it.copy(customers = list) }
                }
            }
        }
        viewModelScope.launch {
            _selectedCustomerId.flatMapLatest { id ->
                if (id == null) flowOf(emptyList()) else repo.getTransactions(id)
            }.catch { emit(emptyList()) }.collect { txs ->
                _state.update { it.copy(transactions = txs) }
            }
        }
    }

    fun setQuery(q: String) { _query.value = q; _state.update { it.copy(query = q) } }

    fun selectCustomer(customer: Customer?) {
        _state.update { it.copy(selectedCustomer = customer) }
        _selectedCustomerId.value = customer?.id
    }

    fun saveCustomer(customer: Customer) = viewModelScope.launch {
        try {
            repo.saveCustomer(customer)
            _state.update { it.copy(message = "Customer saved") }
        } catch (e: Exception) {
            _state.update { it.copy(message = "Error: ${e.localizedMessage}") }
        }
    }

    /**
     * Creates a customer from the checkout quick-add form and hands it back so the sale can use it
     * straight away ("Save & Select" used to save but select nothing). A phone number that already
     * belongs to someone reuses that customer instead of creating a duplicate.
     */
    fun createCustomer(name: String, phone: String, onReady: (Customer) -> Unit) {
        viewModelScope.launch {
            try {
                val existing = if (phone.isNotBlank()) repo.getByPhone(phone) else null
                if (existing != null) {
                    _state.update { it.copy(message = "Already saved as ${existing.name}") }
                    onReady(existing)
                    return@launch
                }
                val draft = Customer(name = name, phone = phone)
                onReady(draft.copy(id = repo.saveCustomer(draft)))
            } catch (e: Exception) {
                _state.update { it.copy(message = "Error: ${e.localizedMessage}") }
            }
        }
    }

    fun deleteCustomer(customer: Customer) = viewModelScope.launch {
        repo.deleteCustomer(customer)
        _state.update { it.copy(message = "Customer deleted") }
    }

    fun addCredit(customerId: Long, amount: Double, notes: String = "", currency: String = "KES") = viewModelScope.launch {
        val ok = repo.addCredit(customerId, amount, notes)
        if (ok) {
            // Bug fix: was hardcoded "KES" in this success message regardless of the
            // app's configurable currency setting.
            _state.update { it.copy(message = "$currency ${String.format("%.2f", amount)} credit added") }
            // Refresh selected customer
            val updated = repo.getById(customerId)
            _state.update { it.copy(selectedCustomer = updated) }
        } else {
            _state.update { it.copy(message = "Customer not found") }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
}
