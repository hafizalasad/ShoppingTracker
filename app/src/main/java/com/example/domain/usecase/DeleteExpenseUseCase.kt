package com.example.domain.usecase

import com.example.domain.repository.ExpenseRepository

class DeleteExpenseUseCase(private val repository: ExpenseRepository) {
    suspend operator fun invoke(id: Long) {
        repository.deleteExpenseById(id)
    }
}
