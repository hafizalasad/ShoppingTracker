package com.example.presentation.intent

import android.graphics.Bitmap

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
