package com.minimart.pos.data.dao

import androidx.room.*
import com.minimart.pos.data.entity.Shift
import com.minimart.pos.data.entity.ShiftStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ShiftDao {

    @Query("SELECT * FROM shifts WHERE cashierId = :cashierId AND status = 'OPEN' LIMIT 1")
    suspend fun getOpenShift(cashierId: Long): Shift?

    @Query("SELECT * FROM shifts ORDER BY clockIn DESC")
    fun getAllShifts(): Flow<List<Shift>>

    @Query("SELECT * FROM shifts WHERE cashierId = :cashierId ORDER BY clockIn DESC")
    fun getShiftsForCashier(cashierId: Long): Flow<List<Shift>>

    @Query("SELECT * FROM shifts WHERE id = :id LIMIT 1")
    suspend fun getShiftById(id: Long): Shift?

    @Query("SELECT * FROM shifts WHERE clockIn >= :startMs ORDER BY clockIn DESC")
    fun getRecentShifts(startMs: Long): Flow<List<Shift>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertShift(shift: Shift): Long

    @Update
    suspend fun updateShift(shift: Shift)
}
