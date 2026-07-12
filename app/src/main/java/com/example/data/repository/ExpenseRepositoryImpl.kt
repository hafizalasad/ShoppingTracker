package com.example.data.repository

import android.graphics.Bitmap
import com.example.data.datasource.ExpenseDao
import com.example.data.ocr.OcrEngine
import com.example.data.parser.ReceiptParser
import com.example.domain.model.Expense
import com.example.domain.model.ReceiptScanResult
import com.example.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow

class ExpenseRepositoryImpl(
    private val expenseDao: ExpenseDao,
    private val ocrEngine: OcrEngine = OcrEngine(),
    private val receiptParser: ReceiptParser = ReceiptParser()
) : ExpenseRepository {

    override fun getAllExpenses(): Flow<List<Expense>> = expenseDao.getAllExpenses()

    override fun getExpensesInDateRange(startDate: Long, endDate: Long): Flow<List<Expense>> {
        return expenseDao.getExpensesInDateRange(startDate, endDate)
    }

    override suspend fun insertExpense(expense: Expense): Long {
        return expenseDao.insertExpense(expense)
    }

    override suspend fun deleteExpenseById(id: Long) {
        expenseDao.deleteExpenseById(id)
    }

    override suspend fun getPendingExpenses(): List<Expense> {
        return expenseDao.getPendingExpenses()
    }

    override suspend fun scanReceipt(bitmap: Bitmap): ReceiptScanResult {
        return try {
            val rawText = ocrEngine.recognizeText(bitmap)
            val parseResult = receiptParser.parse(rawText)
            val confidence = parseResult.confidence

            ReceiptScanResult(
                merchant = parseResult.merchant,
                amount = parseResult.amount,
                date = parseResult.date,
                confidence = confidence,
                isConfidenceLow = confidence < 70,
                scannedOffline = true,
                scannedWithAi = false
            )
        } catch (e: Exception) {
            ReceiptScanResult(
                merchant = "Error Scanning",
                amount = 0.0,
                date = System.currentTimeMillis(),
                confidence = 0,
                isConfidenceLow = true,
                scannedOffline = true,
                scannedWithAi = false,
                error = "OCR failed: ${e.message}"
            )
        }
    }
}
