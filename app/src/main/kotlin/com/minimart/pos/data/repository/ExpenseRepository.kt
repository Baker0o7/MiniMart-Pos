package com.minimart.pos.data.repository

import com.minimart.pos.data.dao.ExpenseDao
import com.minimart.pos.data.entity.Expense
import com.minimart.pos.data.entity.ExpenseCategory
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseRepository @Inject constructor(private val dao: ExpenseDao) {
    fun getAllExpenses(): Flow<List<Expense>> = dao.getAllExpenses()
    fun getExpensesByDateRange(start: Long, end: Long): Flow<List<Expense>> = dao.getExpensesByDateRange(start, end)
    fun getTotalExpenses(start: Long, end: Long): Flow<Double?> = dao.getTotalExpenses(start, end)
    fun getTotalExpensesToday(startMs: Long): Flow<Double?> = dao.getTotalExpensesToday(startMs)
    fun getByCategory(cat: ExpenseCategory): Flow<List<Expense>> = dao.getExpensesByCategory(cat)
    suspend fun insert(expense: Expense): Long = dao.insertExpense(expense)
    suspend fun update(expense: Expense) = dao.updateExpense(expense)
    suspend fun delete(expense: Expense) = dao.deleteExpense(expense)
}
