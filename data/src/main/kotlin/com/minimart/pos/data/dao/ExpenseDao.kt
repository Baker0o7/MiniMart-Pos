package com.minimart.pos.data.dao

import androidx.room.*
import com.minimart.pos.data.entity.Expense
import com.minimart.pos.data.entity.ExpenseCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Query("SELECT * FROM expenses ORDER BY createdAt DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE createdAt >= :startMs AND createdAt <= :endMs ORDER BY createdAt DESC")
    fun getExpensesByDateRange(startMs: Long, endMs: Long): Flow<List<Expense>>

    @Query("SELECT SUM(amount) FROM expenses WHERE createdAt >= :startMs AND createdAt <= :endMs")
    fun getTotalExpenses(startMs: Long, endMs: Long): Flow<Double?>

    @Query("SELECT * FROM expenses WHERE category = :category ORDER BY createdAt DESC")
    fun getExpensesByCategory(category: ExpenseCategory): Flow<List<Expense>>

    @Query("SELECT SUM(amount) FROM expenses WHERE createdAt >= :startMs")
    fun getTotalExpensesToday(startMs: Long): Flow<Double?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExpense(expense: Expense): Long

    @Update
    suspend fun updateExpense(expense: Expense)

    @Delete
    suspend fun deleteExpense(expense: Expense)
}
