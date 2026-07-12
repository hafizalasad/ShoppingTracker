package com.example.domain.model

data class ReceiptScanResult(
    val merchant: String?,
    val amount: Double?,
    val date: Long?,
    val confidence: Int,
    val isConfidenceLow: Boolean,
    val scannedOffline: Boolean,
    val scannedWithAi: Boolean,
    val error: String? = null
)
