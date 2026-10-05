package com.minimart.pos.domain.usecase

import com.minimart.pos.data.entity.Sale
import com.minimart.pos.data.entity.SaleItem
import com.minimart.pos.data.repository.SaleRepository
import javax.inject.Inject

/**
 * Use-case layer for sales. ViewModels depend on these single-purpose classes instead of
 * calling the repository directly, so business rules live in one testable place.
 */

/** Records a sale atomically (sale + stock + customer credit). Returns the new sale id. */
class CompleteSaleUseCase @Inject constructor(private val saleRepo: SaleRepository) {
    suspend operator fun invoke(
        sale: Sale,
        items: List<SaleItem>,
        customerId: Long? = null,
        creditAmount: Double = 0.0,
        purchaseAmount: Double = 0.0
    ): Long {
        require(items.isNotEmpty()) { "Cannot complete a sale with no items" }
        return saleRepo.completeSale(sale, items, customerId, creditAmount, purchaseAmount)
    }
}

/** Refunds a COMPLETED sale (restores stock, returns credit). No-op if already refunded/voided. */
class RefundSaleUseCase @Inject constructor(private val saleRepo: SaleRepository) {
    suspend operator fun invoke(saleId: Long, reason: String) = saleRepo.refundSale(saleId, reason.trim())
}

/** Voids a COMPLETED sale (restores stock, returns credit). No-op if already refunded/voided. */
class VoidSaleUseCase @Inject constructor(private val saleRepo: SaleRepository) {
    suspend operator fun invoke(saleId: Long, reason: String) = saleRepo.voidSale(saleId, reason.trim())
}
