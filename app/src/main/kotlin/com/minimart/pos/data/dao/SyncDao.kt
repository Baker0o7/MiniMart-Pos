package com.minimart.pos.data.dao

import androidx.room.*
import com.minimart.pos.data.entity.SyncLog
import com.minimart.pos.data.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: SyncLog): Long

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
