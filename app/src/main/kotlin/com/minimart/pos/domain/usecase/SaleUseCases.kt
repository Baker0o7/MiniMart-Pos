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
class CompleteSaleUseCase @Inject constructor(
    private val saleRepo: SaleRepository,
    private val syncEnqueuer: com.minimart.pos.sync.SyncEnqueuer
) {
    suspend operator fun invoke(
        sale: Sale,
        items: List<SaleItem>,
        customerId: Long? = null,
        creditAmount: Double = 0.0,
        purchaseAmount: Double = 0.0
    ): Long {
        require(items.isNotEmpty()) { "Cannot complete a sale with no items" }
        val saleId = saleRepo.completeSale(sale, items, customerId, creditAmount, purchaseAmount)
        // Queue it for the main device; a sync problem must never undo or fail the sale.
        runCatching { syncEnqueuer.saleCreated(saleId, customerId, creditAmount, purchaseAmount) }
        return saleId
    }
}

/** Refunds a COMPLETED sale (restores stock, returns credit). No-op if already refunded/voided. */
class RefundSaleUseCase @Inject constructor(
    private val saleRepo: SaleRepository,
    private val syncEnqueuer: com.minimart.pos.sync.SyncEnqueuer
) {
    suspend operator fun invoke(saleId: Long, reason: String) {
        val before = saleRepo.getSaleWithItems(saleId)?.sale
        saleRepo.refundSale(saleId, reason.trim())
        if (before != null && before.status == com.minimart.pos.data.entity.SaleStatus.COMPLETED)
            runCatching { syncEnqueuer.saleReversed(before, com.minimart.pos.data.entity.SaleStatus.REFUNDED, reason.trim()) }
    }
}

/** Voids a COMPLETED sale (restores stock, returns credit). No-op if already refunded/voided. */
class VoidSaleUseCase @Inject constructor(
    private val saleRepo: SaleRepository,
    private val syncEnqueuer: com.minimart.pos.sync.SyncEnqueuer
) {
    suspend operator fun invoke(saleId: Long, reason: String) {
        val before = saleRepo.getSaleWithItems(saleId)?.sale
        saleRepo.voidSale(saleId, reason.trim())
        if (before != null && before.status == com.minimart.pos.data.entity.SaleStatus.COMPLETED)
            runCatching { syncEnqueuer.saleReversed(before, com.minimart.pos.data.entity.SaleStatus.VOIDED, reason.trim()) }
    }
}
