package com.example.presentation.state

data class ScanUiState(
    val isAnalyzing: Boolean = false,
    val analysisError: String? = null,
    val imagePath: String? = null,
    val shopName: String = "",
    val amount: String = "",
    val selectedDate: Long = System.currentTimeMillis(),
    val showDatePicker: Boolean = false,
    val isOffline: Boolean = true,
    val note: String = "",
    val isManualEntry: Boolean = false,
    val confidenceScore: Int? = null,
    val isConfidenceLow: Boolean = false,
    val scannedOffline: Boolean = true,
    val scannedWithAi: Boolean = false
)
