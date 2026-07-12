package com.example.presentation.intent

sealed interface MainUiIntent {
    data class SetDateRange(val start: Long, val end: Long) : MainUiIntent
    data class SetCurrency(val currencyCode: String) : MainUiIntent
    data object NavigateToScan : MainUiIntent
    data class NavigateToShopDetails(val shopName: String) : MainUiIntent
}
