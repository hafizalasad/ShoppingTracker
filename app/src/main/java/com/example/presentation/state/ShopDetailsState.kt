package com.example.presentation.state

import com.example.domain.model.Expense

data class ShopCategorySummary(
    val category: String,
    val totalAmount: Double,
    val count: Int
)

data class ShopDetailsUiState(
    val shopName: String = "",
    val expenses: List<Expense> = emptyList(),
    val categorySummaries: List<ShopCategorySummary> = emptyList(),
    val totalSpent: Double = 0.0
)
