package com.minimart.pos.data.dao

import androidx.room.*
import com.minimart.pos.data.entity.SyncLog
import com.minimart.pos.data.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: SyncLog): Long

    // Bug fix: neither SyncServer's /apply handler nor SyncClient's pull-side insert
    // checked whether a given remote change had already been applied before inserting
    // it — every insertLog() call created a brand-new row via Room's autoGenerate
    // primary key, regardless of whether the SAME logical change (same originating
    // device, same entity, same operation, same original timestamp) had already been
    // received in an earlier sync cycle. Since a change stays PENDING on the sender's
    // side until the sender's own markSynced() succeeds, ANY repeated sync attempt
    // (double-tapping "Sync Now", a dropped connection retry, two devices syncing to
    // the same server close together) would re-fetch and re-insert the identical
    // change as a new row every time. (deviceId, entityType, entityId, operation,
    // createdAt) is the natural composite key that identifies one specific originating
    // change, since createdAt is preserved as-is when relaying rather than
    // regenerated — so this key stays stable and consistent across repeated syncs.
    @Query("""
        SELECT COUNT(*) FROM sync_log
        WHERE deviceId = :deviceId AND entityType = :entityType AND entityId = :entityId
          AND operation = :operation AND createdAt = :createdAt
    """)
    suspend fun countMatchingLog(
        deviceId: String, entityType: com.minimart.pos.data.entity.SyncEntityType,
        entityId: Long, operation: com.minimart.pos.data.entity.SyncOperation, createdAt: Long
    ): Int

    /** Inserts only if an identical remote change hasn't already been received. */
    suspend fun insertLogIfNew(log: SyncLog): Long? {
        val already = countMatchingLog(log.deviceId, log.entityType, log.entityId, log.operation, log.createdAt) > 0
        return if (already) null else insertLog(log)
    }

    @Query("SELECT * FROM sync_log WHERE status = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPendingLogs(): List<SyncLog>

    @Query("SELECT * FROM sync_log ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 50): Flow<List<SyncLog>>

    @Query("UPDATE sync_log SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: SyncStatus)

    @Query("UPDATE sync_log SET status = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("DELETE FROM sync_log WHERE status = 'SYNCED' AND createdAt < :before")
    suspend fun pruneOldLogs(before: Long)

    @Query("SELECT COUNT(*) FROM sync_log WHERE status = 'PENDING'")
    fun getPendingCount(): Flow<Int>
}
