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
    // Bug fix: these aggregate properties used Double's sumOf{} — repeated floating-
    // point addition across every cart line, which is exactly where IEEE-754 rounding
    // error compounds (0.1 + 0.2 != 0.3 in binary floating point). On a cart with many
    // lines this can produce a subtotal/total that's off by a fraction of a cent from
    // what summing the same prices on paper would give. Rewritten to sum via Money
    // (exact Long cents) internally, converting back to Double only at the public API
    // boundary — so state.total (the number that decides how much cash to collect) is
    // now cent-exact, with zero changes needed anywhere else that reads these
    // properties (CheckoutScreen, ScannerCartScreen, ReceiptScreen all keep working
    // exactly as before).
    val subtotal: Double get() = items.fold(com.minimart.pos.util.Money.ZERO) { acc, item ->
        acc + com.minimart.pos.util.Money.fromDouble(item.lineSubtotal) }.toDouble()
    val totalTax: Double get() = items.fold(com.minimart.pos.util.Money.ZERO) { acc, item ->
        acc + com.minimart.pos.util.Money.fromDouble(item.lineTax) }.toDouble()          // extracted VAT (display only)
    val totalDiscount: Double get() = items.fold(com.minimart.pos.util.Money.fromDouble(discount)) { acc, item ->
        acc + com.minimart.pos.util.Money.fromDouble(item.lineDiscount) }.toDouble()
    // Bug fix: total had no floor — if a discount (global or per-item) ever exceeded the
    // subtotal, total went negative. This broke checkout validation downstream: `cashAmount
    // >= total` became trivially true for ANY amount (even KES 0) since any number is >= a
    // negative number, and `change` could show a large bogus amount owed back to the
    // customer. The setters above now clamp discounts at the source, but this floor is kept
    // as a second line of defense in case any future code path mutates discount/lineDiscount
    // directly without going through setItemDiscount/setGlobalDiscount.
    val total: Double get() = (com.minimart.pos.util.Money.fromDouble(subtotal) -
        com.minimart.pos.util.Money.fromDouble(totalDiscount)).coerceAtLeast(com.minimart.pos.util.Money.ZERO).toDouble()
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
    private val cashDrawer: com.minimart.pos.printer.CashDrawerManager,
    private val customerRepo: com.minimart.pos.data.repository.CustomerRepository,
    private val auditLogger: com.minimart.pos.util.AuditLogger,
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
            keyboardScanner.barcodeFlow
                .catch { /* keyboard scanner error — ignore, flow auto-resumes */ }
                .collect { barcode -> processBarcode(barcode) }
        }
    }

    // ── Barcode handling ──────────────────────────────────────────────────────

    fun processBarcode(barcode: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val clean = barcode.trim()

            // ── PLU / variable-weight barcode (scale-printed, prefix "2", 13 digits) ──
            val pluResult = com.minimart.pos.util.PluDecoder.decode(clean)
            if (pluResult != null) {
                val weighedProduct = productRepo.getProductByPlu(pluResult.pluCode)
                if (weighedProduct != null && weighedProduct.isWeighed) {
                    val price = com.minimart.pos.util.PluDecoder.calculatePrice(
                        weighedProduct.pricePerKg, pluResult.weightKg)
                    addWeighedItem(weighedProduct, pluResult.weightKg, price)
                    _uiState.update { it.copy(isLoading = false, lastScannedProduct = weighedProduct, error = null) }
                    return@launch
                }
                // Not a recognized PLU product — fall through and try as a regular barcode
            }

            // ── Regular fixed-price barcode ──────────────────────────────────
            val product = productRepo.getByBarcode(clean)
            if (product == null) {
                _uiState.update { it.copy(isLoading = false, error = "Product not found: $clean") }
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

    /** Adds or updates a weighed item in the cart. Weight comes from a scale-printed PLU barcode. */
    fun addWeighedItem(product: Product, weightKg: Double, price: Double) {
        _uiState.update { state ->
            val existingIdx = state.items.indexOfFirst { it.product.id == product.id }
            val newItems = if (existingIdx >= 0) {
                // Re-scanning the same weighed product replaces its weight (doesn't stack — a
                // second scale ticket means a fresh weighing, not "add another").
                state.items.toMutableList().also {
                    it[existingIdx] = it[existingIdx].copy(weightKg = weightKg, product = product.copy(price = price))
                }
            } else {
                state.items + CartItem(product = product.copy(price = price), quantity = 1, weightKg = weightKg)
            }
            state.copy(items = newItems, error = null)
        }
    }

    // ── Cart mutations ────────────────────────────────────────────────────────

    fun addToCart(product: Product) {
        val state = _uiState.value
        val existing = state.items.indexOfFirst { it.product.id == product.id }
        if (existing >= 0) {
            val item = state.items[existing]
            if (item.quantity >= product.stock) {
                _uiState.update { it.copy(error = "Max stock reached for ${product.name}") }
                return
            }
            val updated = state.items.toMutableList().also {
                it[existing] = item.copy(quantity = item.quantity + 1)
            }
            _uiState.update { it.copy(items = updated, error = null) }
        } else {
            // Bug fix: previously a brand-new item skipped the stock check entirely,
            // allowing out-of-stock products to be added with quantity = 1.
            if (product.stock <= 0 && !product.isWeighed) {
                _uiState.update { it.copy(error = "${product.name} is out of stock") }
                return
            }
            _uiState.update { it.copy(items = it.items + CartItem(product = product, quantity = 1), error = null) }
        }
    }

    fun updateQuantity(productId: Long, quantity: Int) {
        _uiState.update { state ->
            if (quantity <= 0) {
                state.copy(items = state.items.filter { it.product.id != productId })
            } else {
                // Bug fix: coerceAtMost(stock) could silently produce 0 when stock == 0,
                // leaving a ghost cart line (KES 0.00) that the stepper couldn't remove.
                // Now: if the coerced result is 0, remove the line instead of keeping it.
                val newItems = state.items.mapNotNull {
                    if (it.product.id != productId) return@mapNotNull it
                    if (it.product.isWeighed) return@mapNotNull it // weighed items aren't unit-tracked
                    val coerced = quantity.coerceAtMost(it.product.stock)
                    if (coerced <= 0) null else it.copy(quantity = coerced)
                }
                state.copy(items = newItems)
            }
        }
    }

    fun removeFromCart(productId: Long) {
        _uiState.update { it.copy(items = it.items.filter { item -> item.product.id != productId }) }
    }

    fun setItemDiscount(productId: Long, discount: Double) {
        _uiState.update { state ->
            state.copy(items = state.items.map {
                if (it.product.id == productId) {
                    // Bug fix: discount was only floored at 0, never capped — a discount
                    // larger than the line's own subtotal made that line's total negative,
                    // which then let `total` (cart-wide) go negative too (see below).
                    val maxAllowed = it.lineSubtotal
                    it.copy(discount = discount.coerceIn(0.0, maxAllowed))
                } else it
            })
        }
    }

    fun setGlobalDiscount(discount: Double) {
        _uiState.update { state ->
            val remainingAfterLineDiscounts = (state.subtotal - state.items.sumOf { it.lineDiscount }).coerceAtLeast(0.0)
            val clamped = discount.coerceIn(0.0, remainingAfterLineDiscounts)
            if (clamped > 0.0) {
                viewModelScope.launch {
                    val user = loggedInUserId.value
                    auditLogger.log(com.minimart.pos.util.AuditEvent.DISCOUNT_APPLIED,
                        detail = "Global discount KES ${String.format("%.2f", clamped)} on cart of KES ${String.format("%.2f", state.subtotal)}")
                }
            }
            state.copy(discount = clamped)
        }
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
        mpesaRef: String? = null,
        customerId: Long? = null
    ) {
        viewModelScope.launch {
            // Bug fix: no guard existed against calling checkout() twice in quick
            // succession — a fast double-tap on "Complete Sale" (before Compose
            // recomposes the button to enabled=false, which only happens on the NEXT
            // frame after _uiState.update completes) could launch two concurrent
            // coroutines that both read the same cart state, both insert a sale, and
            // both decrement stock — producing two receipts for one physical
            // transaction. This check must run synchronously as the very first line,
            // before any suspension point, so the second call sees isLoading=true
            // immediately rather than racing to read a stale false value.
            if (_uiState.value.isLoading) return@launch
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
                    // Only CASH payments put physical cash in the till; everything else
                    // (MPESA, CARD, CREDIT) is 0 for till-reconciliation purposes.
                    cashPortion = if (paymentMethod == PaymentMethod.CASH) amountPaid else 0.0,
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
                        lineTotal = cartItem.lineTotal,
                        weightKg = cartItem.weightKg  // 0.0 for non-weighed items
                    )
                }

                val saleId = saleRepo.completeSale(sale, saleItems)
                // Auto-open cash drawer on cash payment if enabled
                if (sale.paymentMethod == PaymentMethod.CASH) {
                    try {
                        val autoOpen = settingsRepo.cashDrawerOnSale.first()
                        if (autoOpen) cashDrawer.openDrawer()
                    } catch (_: Exception) {}
                }
                // Deduct credit and record purchase for credit payment
                customerId?.let { cId ->
                    try {
                        if (sale.paymentMethod == PaymentMethod.CREDIT)
                            customerRepo.useCredit(cId, sale.totalAmount, saleId)
                        else
                            customerRepo.recordPurchase(cId, sale.totalAmount, saleId)
                    } catch (_: Exception) {}
                }
                _uiState.update { CartUiState() } // clear cart after sale
                auditLogger.log(com.minimart.pos.util.AuditEvent.SALE_COMPLETED,
                    detail = "Receipt #${sale.receiptNumber} • KES ${String.format("%.2f", sale.totalAmount)} • ${sale.paymentMethod.name}")
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

    /** Split payment: credit portion + cash portion */
    fun checkoutSplit(creditAmount: Double, cashAmount: Double, customerId: Long, mpesaRef: String? = null) {
        viewModelScope.launch {
            // Bug fix: same double-checkout race as checkout() above — checkoutSplit()
            // had no guard at all (not even the isEmpty check checkout() has).
            if (_uiState.value.isLoading) return@launch
            if (_uiState.value.isEmpty) return@launch
            _uiState.update { it.copy(isLoading = true) }
            try {
                val state = _uiState.value
                val userId = settingsRepo.loggedInUserId.first()
                // Bug fix: was using (System.currentTimeMillis() % 9999) which is NOT unique —
                // two split-payment sales whose timestamps land on the same value mod 9999
                // (recurs roughly every ~10 seconds) would silently get the same receipt
                // number on the same day. Use the real persisted, atomically-incremented
                // counter — the same one the regular checkout() path uses — instead.
                val counter = settingsRepo.incrementReceiptCounter()
                val receiptNum = buildReceiptNumber(counter)
                val sale = Sale(
                    receiptNumber = receiptNum,
                    subtotal = state.subtotal, taxAmount = state.totalTax,
                    discountAmount = state.totalDiscount, totalAmount = state.total,
                    amountPaid = creditAmount + cashAmount,
                    changeGiven = ((creditAmount + cashAmount) - state.total).coerceAtLeast(0.0),
                    // Bug fix: only the cash portion of a split payment is physical money
                    // in the till. Previously this was never recorded anywhere, so
                    // ShiftRepository's end-of-shift cash count silently dropped it
                    // entirely (its `when` had no MIXED branch) — cashiers using split
                    // payment would show a false "shortage" at shift close.
                    cashPortion = cashAmount,
                    paymentMethod = PaymentMethod.MIXED, mpesaRef = mpesaRef, cashierId = userId ?: 0L
                )
                val saleItems = state.items.map { ci ->
                    SaleItem(saleId = 0L, productId = ci.product.id, productName = ci.product.name,
                        productBarcode = ci.product.barcode, unitPrice = ci.product.price,
                        quantity = ci.quantity, discountAmount = ci.lineDiscount,
                        taxAmount = ci.lineTax, lineTotal = ci.lineTotal,
                        weightKg = ci.weightKg  // 0.0 for non-weighed items
                    )
                }
                val saleId = saleRepo.completeSale(sale, saleItems)
                if (creditAmount > 0) customerRepo.useCredit(customerId, creditAmount, saleId)
                if (cashAmount > 0) {
                    try { val ao = settingsRepo.cashDrawerOnSale.first(); if (ao) cashDrawer.openDrawer() }
                    catch (_: Exception) {}
                }
                _uiState.update { CartUiState() }
                auditLogger.log(com.minimart.pos.util.AuditEvent.SALE_COMPLETED,
                    detail = "Receipt #${sale.receiptNumber} • KES ${String.format("%.2f", sale.totalAmount)} • SPLIT (credit=${String.format("%.2f", creditAmount)} cash=${String.format("%.2f", cashAmount)})")
                if (creditAmount > 0)
                    auditLogger.log(com.minimart.pos.util.AuditEvent.CREDIT_USED,
                        detail = "KES ${String.format("%.2f", creditAmount)} from customer #$customerId • Sale #$saleId")
                _checkoutResult.emit(CheckoutResult.Success(saleId, sale.changeGiven))
            } catch (e: Exception) {
                // Bug fix: this catch block updated uiState.error but never emitted a
                // CheckoutResult.Error to the shared flow, unlike the regular checkout()
                // path's catch block. A failed split-payment checkout (e.g. a DB error,
                // an invalid customer ID) would silently reset isLoading with zero
                // feedback to the cashier — the screen just sat there looking like
                // nothing happened.
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage) }
                _checkoutResult.emit(CheckoutResult.Error(e.localizedMessage ?: "Split checkout failed"))
            }
        }
    }
}
