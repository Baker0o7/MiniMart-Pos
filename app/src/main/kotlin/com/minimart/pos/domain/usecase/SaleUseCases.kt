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
        // The sync record is written in the same transaction as the sale: either both are saved or neither,
        // so a crash or a dead battery can never leave a sale that the main device will not hear about.
        val saleId = com.minimart.pos.util.Telemetry.timed("complete_sale") {
            saleRepo.completeSale(sale, items, customerId, creditAmount, purchaseAmount) { id ->
                syncEnqueuer.queueSaleCreated(id, customerId, creditAmount, purchaseAmount)
            }
        }
        syncEnqueuer.kick()
        return saleId
    }
}

/** Refunds a COMPLETED sale (restores stock, returns credit). No-op if already refunded/voided. */
class RefundSaleUseCase @Inject constructor(
    private val saleRepo: SaleRepository,
    private val syncEnqueuer: com.minimart.pos.sync.SyncEnqueuer,
    private val audit: com.minimart.pos.util.AuditLogger,
    private val guard: com.minimart.pos.util.PermissionGuard
) {
    suspend operator fun invoke(saleId: Long, reason: String) {
        if (!guard.allow("refund a sale", com.minimart.pos.util.RoleManager::canVoidSales))
            throw com.minimart.pos.util.PermissionDeniedException("refund sales")
        val before = saleRepo.getSaleWithItems(saleId)?.sale
        saleRepo.refundSale(saleId, reason.trim()) { s ->
            syncEnqueuer.queueSaleReversed(s, com.minimart.pos.data.entity.SaleStatus.REFUNDED, reason.trim())
        }
        syncEnqueuer.kick()
        if (before != null && before.status == com.minimart.pos.data.entity.SaleStatus.COMPLETED) {
            runCatching { audit.log(com.minimart.pos.util.AuditEvent.SALE_REFUNDED,
                detail = "Sale #$saleId total ${String.format(java.util.Locale.US, "%.2f", before.totalAmount)} · reason: ${reason.trim().ifBlank { "none given" }}") }
        }
    }
}

/** Voids a COMPLETED sale (restores stock, returns credit). No-op if already refunded/voided. */
class VoidSaleUseCase @Inject constructor(
    private val saleRepo: SaleRepository,
    private val syncEnqueuer: com.minimart.pos.sync.SyncEnqueuer,
    private val audit: com.minimart.pos.util.AuditLogger,
    private val guard: com.minimart.pos.util.PermissionGuard
) {
    suspend operator fun invoke(saleId: Long, reason: String) {
        if (!guard.allow("void a sale", com.minimart.pos.util.RoleManager::canVoidSales))
            throw com.minimart.pos.util.PermissionDeniedException("void sales")
        val before = saleRepo.getSaleWithItems(saleId)?.sale
        saleRepo.voidSale(saleId, reason.trim()) { s ->
            syncEnqueuer.queueSaleReversed(s, com.minimart.pos.data.entity.SaleStatus.VOIDED, reason.trim())
        }
        syncEnqueuer.kick()
        if (before != null && before.status == com.minimart.pos.data.entity.SaleStatus.COMPLETED) {
            runCatching { audit.log(com.minimart.pos.util.AuditEvent.SALE_VOIDED,
                detail = "Sale #$saleId total ${String.format(java.util.Locale.US, "%.2f", before.totalAmount)} · reason: ${reason.trim().ifBlank { "none given" }}") }
        }
    }
}
