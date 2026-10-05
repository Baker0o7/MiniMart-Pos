package com.minimart.pos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimart.pos.data.entity.Product
import com.minimart.pos.data.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProductUiState(
    val products: List<Product> = emptyList(),
    val categories: List<String> = emptyList(),
    val lowStockProducts: List<Product> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProductViewModel @Inject constructor(
    private val repo: ProductRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedCategory = MutableStateFlow<String?>(null)

    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _uiState = MutableStateFlow(ProductUiState())
    val uiState: StateFlow<ProductUiState> = _uiState.asStateFlow()

    // Products: reactive to search + category filter
    val products: StateFlow<List<Product>> = combine(
        _searchQuery, _selectedCategory
    ) { query, category -> Pair(query, category) }
        .flatMapLatest { (query, category) ->
            when {
                query.isNotBlank() -> repo.searchProducts(query)
                category != null   -> repo.getProductsByCategory(category)
                else               -> repo.getAllProducts()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(3000), emptyList())

    val categories: StateFlow<List<String>> = repo.getCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(3000), emptyList())

    val lowStockProducts: StateFlow<List<Product>> = repo.getLowStockProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(3000), emptyList())

    fun setSearchQuery(q: String) { _searchQuery.value = q }
    fun setCategory(cat: String?) { _selectedCategory.value = cat }

    fun saveProduct(product: Product) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // insertProduct uses REPLACE and barcode is unique, so saving a new product
                // with an existing barcode used to silently delete the other product.
                val clash = repo.getByBarcode(product.barcode)
                if (clash != null && clash.id != product.id) {
                    _uiState.update { it.copy(isLoading = false, error = "Barcode already used by \"${clash.name}\"") }
                    return@launch
                }
                if (product.id == 0L) repo.insert(product) else repo.update(product)
                _uiState.update { it.copy(isLoading = false, successMessage = "Product saved") }
            } catch (e: android.database.sqlite.SQLiteConstraintException) {
                _uiState.update { it.copy(isLoading = false, error = "Barcode already exists (it may belong to a deleted product)") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun deleteProduct(productId: Long) {
        viewModelScope.launch {
            repo.softDelete(productId)
            _uiState.update { it.copy(successMessage = "Product deleted") }
        }
    }

    fun adjustStock(productId: Long, delta: Int) {
        viewModelScope.launch {
            if (delta > 0) repo.incrementStock(productId, delta)
            else if (delta < 0) {
                if (repo.decrementStock(productId, -delta) == 0)
                    _uiState.update { it.copy(error = "Cannot remove more than the current stock") }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }
}
