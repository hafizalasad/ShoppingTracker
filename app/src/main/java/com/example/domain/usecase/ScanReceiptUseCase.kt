package com.example.domain.usecase

import android.graphics.Bitmap
import com.example.domain.model.ReceiptScanResult
import com.example.domain.repository.ExpenseRepository

class ScanReceiptUseCase(private val repository: ExpenseRepository) {
    suspend operator fun invoke(bitmap: Bitmap): ReceiptScanResult {
        return repository.scanReceipt(bitmap)
    }
}
