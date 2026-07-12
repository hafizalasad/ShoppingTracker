package com.example.domain.repository

import android.graphics.Bitmap
import com.example.domain.model.Expense
import com.example.domain.model.ReceiptScanResult
import kotlinx.coroutines.flow.Flow

interface ExpenseRepository {
    fun getAllExpenses(): Flow<List<Expense>>
    fun getExpensesInDateRange(startDate: Long, endDate: Long): Flow<List<Expense>>
    suspend fun insertExpense(expense: Expense): Long
    suspend fun deleteExpenseById(id: Long)
    suspend fun getPendingExpenses(): List<Expense>
    suspend fun scanReceipt(bitmap: Bitmap): ReceiptScanResult
}
