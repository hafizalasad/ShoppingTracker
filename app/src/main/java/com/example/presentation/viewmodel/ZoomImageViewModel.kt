package com.example.presentation.viewmodel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ZoomImageViewModel : ViewModel() {
    private val _imagePath = MutableStateFlow<String?>(null)
    val imagePath: StateFlow<String?> = _imagePath.asStateFlow()

    private val _returnScreen = MutableStateFlow<Screen>(Screen.Main)
    val returnScreen: StateFlow<Screen> = _returnScreen.asStateFlow()

    private val _imageVersion = MutableStateFlow(System.currentTimeMillis())
    val imageVersion: StateFlow<Long> = _imageVersion.asStateFlow()

    private val _isRotating = MutableStateFlow(false)
    val isRotating: StateFlow<Boolean> = _isRotating.asStateFlow()

    fun setup(imagePath: String, returnScreen: Screen) {
        _imagePath.value = imagePath
        _returnScreen.value = returnScreen
    }

    fun rotateImage(degrees: Float = 90f, onComplete: () -> Unit = {}) {
        val path = _imagePath.value ?: return
        if (_isRotating.value) return

        viewModelScope.launch {
            _isRotating.value = true
            withContext(Dispatchers.IO) {
                try {
                    val file = File(path)
                    if (file.exists() && file.length() > 0) {
                        // Decode at 100% original full resolution without downscaling
                        val options = BitmapFactory.Options().apply {
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                            inScaled = false
                            inSampleSize = 1
                        }
                        val originalBitmap = BitmapFactory.decodeFile(file.absolutePath, options)
                        if (originalBitmap != null) {
                            val matrix = Matrix().apply {
                                postRotate(degrees)
                            }
                            val rotatedBitmap = Bitmap.createBitmap(
                                originalBitmap,
                                0,
                                0,
                                originalBitmap.width,
                                originalBitmap.height,
                                matrix,
                                true
                            )

                            val isPng = path.endsWith(".png", ignoreCase = true)
                            val format = if (isPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG

                            val tempFile = File(file.parentFile, "rotated_${System.currentTimeMillis()}_${file.name}")
                            FileOutputStream(tempFile).use { out ->
                                // Compress with 100 maximum quality (lossless for PNG, 100 for JPEG)
                                rotatedBitmap.compress(format, 100, out)
                                out.flush()
                            }

                            if (tempFile.exists() && tempFile.length() > 0) {
                                val success = tempFile.renameTo(file)
                                if (!success) {
                                    tempFile.copyTo(file, overwrite = true)
                                    tempFile.delete()
                                }
                            }

                            if (rotatedBitmap != originalBitmap) {
                                originalBitmap.recycle()
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            _imageVersion.value = System.currentTimeMillis()
            _isRotating.value = false
            onComplete()
        }
    }
}
