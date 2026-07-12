package com.example.domain.usecase

import com.example.domain.model.Expense
import com.example.domain.repository.ExpenseRepository

class SaveExpenseUseCase(private val repository: ExpenseRepository) {
    suspend operator fun invoke(expense: Expense): Long {
        return repository.insertExpense(expense)
    }
}
