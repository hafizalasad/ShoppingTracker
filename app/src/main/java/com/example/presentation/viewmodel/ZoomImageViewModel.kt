package com.example.presentation.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ZoomImageViewModel : ViewModel() {
    private val _imagePath = MutableStateFlow<String?>(null)
    val imagePath: StateFlow<String?> = _imagePath.asStateFlow()

    private val _returnScreen = MutableStateFlow<Screen>(Screen.Main)
    val returnScreen: StateFlow<Screen> = _returnScreen.asStateFlow()

    fun setup(imagePath: String, returnScreen: Screen) {
        _imagePath.value = imagePath
        _returnScreen.value = returnScreen
    }
}
