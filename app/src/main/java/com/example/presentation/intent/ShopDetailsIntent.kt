package com.example.presentation.intent

import com.example.domain.model.Expense

sealed interface ShopDetailsUiIntent {
    data class DeleteExpense(val expense: Expense) : ShopDetailsUiIntent
    data class UpdateExpense(val expense: Expense) : ShopDetailsUiIntent
    data object GoBack : ShopDetailsUiIntent
    data class ZoomImage(val imagePath: String, val shopName: String) : ShopDetailsUiIntent
    data class TriggerScan(val expense: Expense) : ShopDetailsUiIntent
}
