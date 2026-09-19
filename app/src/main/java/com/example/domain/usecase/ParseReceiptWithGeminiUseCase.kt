package com.example.domain.usecase

import com.example.domain.model.ProductLineItem
import com.example.domain.repository.ExpenseRepository

class ParseReceiptWithGeminiUseCase(private val repository: ExpenseRepository) {
    suspend operator fun invoke(rawOcrText: String): List<ProductLineItem> {
        return repository.parseReceiptWithAi(rawOcrText)
    }
}
