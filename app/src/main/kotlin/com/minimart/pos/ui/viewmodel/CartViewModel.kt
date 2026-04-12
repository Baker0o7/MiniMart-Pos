package com.minimart.pos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimart.pos.data.entity.*
import com.minimart.pos.data.repository.ProductRepository
import com.minimart.pos.data.repository.SaleRepository
import com.minimart.pos.data.repository.SettingsRepository
import com.minimart.pos.scanner.KeyboardScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class CartUiState(
    val items: List<CartItem> = emptyList(),
    val discount: Double = 0.0,
    val isLoading: Boolean = false,
    val lastScannedProduct: Product? = null,
    val error: String? = null,
    val completedSaleId: Long? = null
) {
    val subtotal: Double get() = items.sumOf { it.lineSubtotal }
    val totalTax: Double get() = items.sumOf { it.lineTax }
    val totalDiscount: Double get() = items.sumOf { it.lineDiscount } + discount
    val total: Double get() = subtotal + totalTax - totalDiscount
    val itemCount: Int get() = items.sumOf { it.quantity }
    val isEmpty: Boolean get() = items.isEmpty()
}

sealed class CheckoutResult {
    data class Success(val saleId: Long, val change: Double) : CheckoutResult()
    data class Error(val message: String) : CheckoutResult()
}

@HiltViewModel
class CartViewModel @Inject constructor(
    private val productRepo: ProductRepository,
    private val saleRepo: SaleRepository,
    private val settingsRepo: SettingsRepository,
    keyboardScanner: KeyboardScanner
) : ViewModel() {

    private val _uiState = MutableStateFlow(CartUiState())
    val uiState: StateFlow<CartUiState> = _uiState.asStateFlow()

    private val _checkoutResult = MutableSharedFlow<CheckoutResult>(extraBufferCapacity = 1)
    val checkoutResult: SharedFlow<CheckoutResult> = _checkoutResult.asSharedFlow()

    val currency = settingsRepo.currency.stateIn(viewModelScope, SharingStarted.Eagerly, "KES")
    val loggedInUserId = settingsRepo.loggedInUserId.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // Listen to keyboard/HID scanner events
        viewModelScope.launch {
            keyboardScanner.barcodeFlow.collect { barcode ->
                processBarcode(barcode)
            }
        }
    }

    // ── Barcode handling ──────────────────────────────────────────────────────

    fun processBarcode(barcode: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val product = productRepo.getByBarcode(barcode)
            if (product == null) {
                _uiState.update { it.copy(isLoading = false, error = "Product not found: $barcode") }
                return@launch
            }
            if (product.stock <= 0) {
                _uiState.update { it.copy(isLoading = false, error = "${product.name} is out of stock") }
                return@launch
            }
            addToCart(product)
            _uiState.update { it.copy(isLoading = false, lastScannedProduct = product, error = null) }
        }
    }

    // ── Cart mutations ────────────────────────────────────────────────────────

    fun addToCart(product: Product) {
        _uiState.update { state ->
            val existing = state.items.indexOfFirst { it.product.id == product.id }
            val updated = if (existing >= 0) {
                val item = state.items[existing]
                if (item.quantity < product.stock) {
                    state.items.toMutableList().also { it[existing] = item.copy(quantity = item.quantity + 1) }
                } else {
                    _uiState.value = state.copy(error = "Max stock reached for ${product.name}")
                    return
                }
            } else {
                state.items + CartItem(product = product, quantity = 1)
            }
            state.copy(items = updated, error = null)
        }
    }

    fun updateQuantity(productId: Long, quantity: Int) {
        _uiState.update { state ->
            if (quantity <= 0) {
                state.copy(items = state.items.filter { it.product.id != productId })
            } else {
                state.copy(items = state.items.map {
                    if (it.product.id == productId) it.copy(quantity = quantity.coerceAtMost(it.product.stock)) else it
                })
            }
        }
    }

    fun removeFromCart(productId: Long) {
        _uiState.update { it.copy(items = it.items.filter { item -> item.product.id != productId }) }
    }

    fun setItemDiscount(productId: Long, discount: Double) {
        _uiState.update { state ->
            state.copy(items = state.items.map {
                if (it.product.id == productId) it.copy(discount = discount.coerceAtLeast(0.0)) else it
            })
        }
    }

    fun setGlobalDiscount(discount: Double) {
        _uiState.update { it.copy(discount = discount.coerceAtLeast(0.0)) }
    }

    fun clearCart() {
        _uiState.update { CartUiState() }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null, lastScannedProduct = null) }
    }

    // ── Checkout ──────────────────────────────────────────────────────────────

    fun checkout(
        paymentMethod: PaymentMethod,
        amountPaid: Double,
        mpesaRef: String? = null
    ) {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.isEmpty) return@launch
            _uiState.update { it.copy(isLoading = true) }

            try {
                val counter = settingsRepo.incrementReceiptCounter()
                val receiptNum = buildReceiptNumber(counter)
                val userId = loggedInUserId.value ?: 1L

                val sale = Sale(
                    receiptNumber = receiptNum,
                    subtotal = state.subtotal,
                    taxAmount = state.totalTax,
                    discountAmount = state.totalDiscount,
                    totalAmount = state.total,
                    amountPaid = amountPaid,
                    changeGiven = (amountPaid - state.total).coerceAtLeast(0.0),
                    paymentMethod = paymentMethod,
                    mpesaRef = mpesaRef,
                    cashierId = userId
                )

                val saleItems = state.items.map { cartItem ->
                    SaleItem(
                        saleId = 0L, // set by DAO
                        productId = cartItem.product.id,
                        productBarcode = cartItem.product.barcode,
                        productName = cartItem.product.name,
                        unitPrice = cartItem.product.price,
                        quantity = cartItem.quantity,
                        discountAmount = cartItem.lineDiscount,
                        taxAmount = cartItem.lineTax,
                        lineTotal = cartItem.lineTotal
                    )
                }

                val saleId = saleRepo.completeSale(sale, saleItems)
                _uiState.update { CartUiState() } // clear cart after sale
                _checkoutResult.emit(CheckoutResult.Success(saleId, sale.changeGiven))
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Checkout failed: ${e.message}") }
                _checkoutResult.emit(CheckoutResult.Error(e.message ?: "Unknown error"))
            }
        }
    }

    private fun buildReceiptNumber(counter: Int): String {
        val date = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        return "RCP-$date-${counter.toString().padStart(4, '0')}"
    }
}
