package com.example.presentation.state

import com.example.domain.model.ProductLineItem

data class ScanUiState(
    val isAnalyzing: Boolean = false,
    val isAiAnalyzing: Boolean = false,
    val analysisError: String? = null,
    val imagePath: String? = null,
    val rawOcrText: String? = null,
    val shopName: String = "",
    val amount: String = "",
    val selectedDate: Long = System.currentTimeMillis(),
    val showDatePicker: Boolean = false,
    val isOffline: Boolean = true,
    val note: String = "",
    val category: String = "General",
    val availableCategories: List<String> = listOf("General", "Groceries", "Food & Dining", "Shopping", "Transport", "Utilities", "Healthcare", "Entertainment"),
    val showAddCategoryDialog: Boolean = false,
    val lineItems: List<ProductLineItem> = emptyList(),
    val isManualEntry: Boolean = false,
    val confidenceScore: Int? = null,
    val isConfidenceLow: Boolean = false,
    val scannedOffline: Boolean = true,
    val scannedWithAi: Boolean = false,
    val isShopNameLocked: Boolean = false
)
