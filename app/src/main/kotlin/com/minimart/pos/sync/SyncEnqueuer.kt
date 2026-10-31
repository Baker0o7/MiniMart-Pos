package com.minimart.pos.sync

import com.minimart.pos.data.dao.SyncDao
import com.minimart.pos.data.entity.Sale
import com.minimart.pos.data.entity.SaleStatus
import com.minimart.pos.data.entity.SyncEntityType
import com.minimart.pos.data.entity.SyncLog
import com.minimart.pos.data.entity.SyncOperation
import com.minimart.pos.data.repository.CustomerRepository
import com.minimart.pos.data.repository.SaleRepository
import com.minimart.pos.data.repository.UserRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Queues a till's sales (and later refunds/voids) so the next sync can send them to the main
 * device. Nothing used to write to the sync log, so sync had nothing to send. Callers wrap this in
 * runCatching: a sync problem must never fail or undo a sale.
 */
@Singleton
class SyncEnqueuer @Inject constructor(
    private val syncDao: SyncDao,
    private val saleRepo: SaleRepository,
    private val customerRepo: CustomerRepository,
    private val userRepo: UserRepository,
    private val device: DeviceIdProvider,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) {
    /** Writes the sync record only. Call it inside the sale's own transaction, then [kick] once that has committed. */
    suspend fun queueSaleCreated(saleId: Long, customerId: Long?, creditAmount: Double, purchaseAmount: Double) {
        if (!device.isPaired()) return
        val sw = saleRepo.getSaleWithItems(saleId) ?: return
        val customer = customerId?.let { customerRepo.getById(it) }
        val cashier = userRepo.getUserById(sw.sale.cashierId)
        val payload = SyncPayloads.saleCreated(
            sw.sale, sw.items, customer?.name, customer?.phone, cashier?.username, creditAmount, purchaseAmount
        )
        syncDao.insertLog(SyncLog(
            entityType = SyncEntityType.SALE, entityId = saleId, operation = SyncOperation.CREATE,
            deviceId = device.id(), payload = payload.toString()
        ))
    }

    /** Writes the sync record for a refund or void only (same rule as [queueSaleCreated]). */
    suspend fun queueSaleReversed(sale: Sale, status: SaleStatus, reason: String) {
        if (!device.isPaired()) return
        syncDao.insertLog(SyncLog(
            entityType = SyncEntityType.SALE, entityId = sale.id, operation = SyncOperation.UPDATE,
            deviceId = device.id(), payload = SyncPayloads.saleReversed(sale, status, reason).toString()
        ))
    }

    /** Asks WorkManager to send what is queued. Safe to fail: the periodic job picks it up anyway. */
    fun kick() { runCatching { com.minimart.pos.worker.SyncWorker.kick(context) } }
}
