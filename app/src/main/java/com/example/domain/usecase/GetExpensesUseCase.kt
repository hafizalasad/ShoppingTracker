package com.example.domain.usecase

import com.example.domain.model.Expense
import com.example.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow

class GetExpensesUseCase(private val repository: ExpenseRepository) {
    fun getAllExpenses(): Flow<List<Expense>> = repository.getAllExpenses()
    
    fun getExpensesInDateRange(startDate: Long, endDate: Long): Flow<List<Expense>> {
        return repository.getExpensesInDateRange(startDate, endDate)
    }
}
