package com.example.presentation.state

import com.example.domain.model.Expense

data class ShopDetailsUiState(
    val shopName: String = "",
    val expenses: List<Expense> = emptyList()
)
