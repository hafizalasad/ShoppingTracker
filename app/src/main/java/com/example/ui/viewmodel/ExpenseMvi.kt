package com.example.ui.viewmodel

import android.graphics.Bitmap
import com.example.data.Expense

// ==========================================
// Main Screen MVI Components
// ==========================================

data class MainUiState(
    val shopSummaries: List<ShopExpenseSummary> = emptyList(),
    val expensesInRange: List<Expense> = emptyList(),
    val startDate: Long = 0L,
    val endDate: Long = 0L,
    val selectedCurrency: String = "USD",
    val currencySymbol: String = "$",
    val totalSpent: Double = 0.0
)

sealed interface MainUiIntent {
    data class SetDateRange(val start: Long, val end: Long) : MainUiIntent
    data class SetCurrency(val currencyCode: String) : MainUiIntent
    data object NavigateToScan : MainUiIntent
    data class NavigateToShopDetails(val shopName: String) : MainUiIntent
}

// ==========================================
// Scan Receipt Screen MVI Components
// ==========================================

data class ScanUiState(
    val isAnalyzing: Boolean = false,
    val analysisError: String? = null,
    val imagePath: String? = null,
    val shopName: String = "",
    val amount: String = "",
    val selectedDate: Long = System.currentTimeMillis(),
    val showDatePicker: Boolean = false,
    val isOffline: Boolean = false,
    val note: String = "",
    val isManualEntry: Boolean = false,
    val confidenceScore: Int? = null,
    val isConfidenceLow: Boolean = false,
    val scannedOffline: Boolean = false,
    val scannedWithAi: Boolean = false
)

sealed interface ScanUiIntent {
    data class SelectImage(val path: String, val bitmap: Bitmap) : ScanUiIntent
    data class UpdateShopName(val name: String) : ScanUiIntent
    data class UpdateAmount(val amount: String) : ScanUiIntent
    data class UpdateDate(val date: Long) : ScanUiIntent
    data class UpdateNote(val note: String) : ScanUiIntent
    data class StartManualEntry(val isManual: Boolean) : ScanUiIntent
    data class ToggleDatePicker(val show: Boolean) : ScanUiIntent
    data object SaveExpense : ScanUiIntent
    data object ResetScan : ScanUiIntent
}

// ==========================================
// Shop Details Screen MVI Components
// ==========================================

data class ShopDetailsUiState(
    val shopName: String = "",
    val expenses: List<Expense> = emptyList()
)

sealed interface ShopDetailsUiIntent {
    data class DeleteExpense(val expense: Expense) : ShopDetailsUiIntent
    data class UpdateExpense(val expense: Expense) : ShopDetailsUiIntent
    data object GoBack : ShopDetailsUiIntent
    data class ZoomImage(val imagePath: String, val shopName: String) : ShopDetailsUiIntent
    data class TriggerScan(val expense: Expense) : ShopDetailsUiIntent
}
