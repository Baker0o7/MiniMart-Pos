package com.minimart.pos.sync

import androidx.room.withTransaction
import com.minimart.pos.data.dao.SyncDao
import com.minimart.pos.data.db.AppDatabase
import com.minimart.pos.data.entity.SaleStatus
import com.minimart.pos.data.entity.SyncEntityType
import com.minimart.pos.data.entity.SyncLog
import com.minimart.pos.data.entity.SyncOperation
import com.minimart.pos.data.entity.SyncStatus
import com.minimart.pos.data.repository.SaleRepository
import com.minimart.pos.data.repository.UserRepository
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Main-device side of a till's push. Each queued change is applied exactly once: the log row that
 * marks it as received and the change itself commit together, so a retry after a dropped connection
 * is recognised as a duplicate instead of selling the goods twice. A change that fails validation is
 * rolled back and reported, never half-applied.
 */
@Singleton
class SyncApplier @Inject constructor(
    private val db: AppDatabase,
    private val syncDao: SyncDao,
    private val saleRepo: SaleRepository,
    private val userRepo: UserRepository
) {
    /** Returns one result per entry: {"id":<till's log id>,"status":"ok|duplicate|rejected","error":...}. */
    suspend fun apply(entries: JSONArray): JSONArray {
        val results = JSONArray()
        for (i in 0 until entries.length()) {
            val e = entries.getJSONObject(i)
            val id = e.optLong("id", -1)
            val r = JSONObject().put("id", id)
            try {
                val log = SyncLog(
                    entityType = SyncEntityType.valueOf(e.getString("entityType")),
                    entityId = e.getLong("entityId"),
                    operation = SyncOperation.valueOf(e.getString("operation")),
                    deviceId = e.getString("deviceId"),
                    payload = e.getString("payload"),
                    status = SyncStatus.SYNCED,
                    createdAt = e.getLong("createdAt")
                )
                val fresh = db.withTransaction {
                    if (syncDao.insertLogIfNew(log) == null) false else { applyOne(log); true }
                }
                r.put("status", if (fresh) "ok" else "duplicate")
            } catch (ex: Exception) {
                r.put("status", "rejected").put("error", ex.message ?: "Could not apply")
            }
            results.put(r)
        }
        return results
    }

    private suspend fun applyOne(log: SyncLog) {
        if (log.entityType != SyncEntityType.SALE) throw IllegalArgumentException("Unsupported change: ${log.entityType}")
        val payload = JSONObject(log.payload)
        when (log.operation) {
            SyncOperation.CREATE -> {
                val remote = SyncPayloads.parseSale(payload)
                val cashierId = remote.cashierUsername?.let { userRepo.getUserByUsername(it)?.id }
                    ?: userRepo.getAllUsersFirstId()
                saleRepo.applyRemoteSale(
                    sale = remote.sale.copy(cashierId = cashierId),
                    items = remote.items,
                    customerName = remote.customerName, customerPhone = remote.customerPhone,
                    creditAmount = remote.creditAmount, purchaseAmount = remote.purchaseAmount
                )
            }
            SyncOperation.UPDATE -> {
                val rev = SyncPayloads.parseReversal(payload)
                val local = saleRepo.findSale(rev.receiptNumber, rev.createdAt)
                    ?: throw IllegalStateException("Sale ${rev.receiptNumber} was never received")
                if (local.status == SaleStatus.COMPLETED) {
                    if (rev.status == SaleStatus.REFUNDED) saleRepo.refundSale(local.id, rev.reason)
                    else saleRepo.voidSale(local.id, rev.reason)
                }
            }
            SyncOperation.DELETE -> throw IllegalArgumentException("Sales cannot be deleted by sync")
        }
    }
}
