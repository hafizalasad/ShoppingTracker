package com.example.presentation.state

import com.example.domain.model.Expense

data class ShopExpenseSummary(
    val shopName: String,
    val totalAmount: Double,
    val expenseCount: Int,
    val latestDate: Long = 0L
)

data class CategoryExpenseSummary(
    val category: String,
    val totalAmount: Double,
    val expenseCount: Int
)

data class MainUiState(
    val shopSummaries: List<ShopExpenseSummary> = emptyList(),
    val categorySummaries: List<CategoryExpenseSummary> = emptyList(),
    val expensesInRange: List<Expense> = emptyList(),
    val startDate: Long = 0L,
    val endDate: Long = 0L,
    val selectedCurrency: String = "USD",
    val currencySymbol: String = "$",
    val totalSpent: Double = 0.0,
    val searchQuery: String = ""
)
