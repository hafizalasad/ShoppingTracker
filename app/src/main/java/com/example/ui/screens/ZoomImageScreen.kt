package com.example.ui.screens

import android.net.Uri
import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.ImageView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.ui.viewmodel.ExpenseViewModel
import com.example.ui.viewmodel.Screen
import com.example.ui.viewmodel.ZoomImageViewModel
import java.io.File

@Composable
fun ZoomImageScreen(
    imagePath: String,
    returnScreen: Screen,
    viewModel: ExpenseViewModel,
    modifier: Modifier = Modifier,
    zoomViewModel: ZoomImageViewModel = viewModel()
) {
    // Setup MVVM variables
    LaunchedEffect(imagePath, returnScreen) {
        zoomViewModel.setup(imagePath, returnScreen)
    }

    val liveImagePath by zoomViewModel.imagePath.collectAsState()
    val liveReturnScreen by zoomViewModel.returnScreen.collectAsState()

    AndroidView(
        factory = { context ->
            val view = LayoutInflater.from(context).inflate(R.layout.screen_zoom_image, null)
            val imageView = view.findViewById<ImageView>(R.id.zoomed_image_view)
            val btnClose = view.findViewById<ImageButton>(R.id.btn_close_zoom)

            btnClose.setOnClickListener {
                viewModel.navigateTo(liveReturnScreen)
            }

            // Set content tags or accessible tags
            btnClose.contentDescription = "Close Zoom"
            view
        },
        update = { view ->
            val imageView = view.findViewById<ImageView>(R.id.zoomed_image_view)
            val path = liveImagePath
            if (path != null) {
                val file = File(path)
                if (file.exists()) {
                    imageView.setImageURI(Uri.fromFile(file))
                }
            }
        },
        modifier = modifier
            .fillMaxSize()
            .testTag("zoom_screen")
    )
}
