package com.example.presentation.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.presentation.viewmodel.ExpenseViewModel
import com.example.presentation.viewmodel.Screen
import com.example.presentation.viewmodel.ZoomImageViewModel
import java.io.File

@Composable
fun ZoomImageScreen(
    imagePath: String,
    returnScreen: Screen,
    viewModel: ExpenseViewModel,
    modifier: Modifier = Modifier,
    zoomViewModel: ZoomImageViewModel = viewModel()
) {
    LaunchedEffect(imagePath, returnScreen) {
        zoomViewModel.setup(imagePath, returnScreen)
    }

    val liveImagePath by zoomViewModel.imagePath.collectAsState()
    val liveReturnScreen by zoomViewModel.returnScreen.collectAsState()

    BackHandler(enabled = true) {
        viewModel.navigateTo(liveReturnScreen)
    }

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("zoom_screen"),
        contentAlignment = Alignment.Center
    ) {
        val path = liveImagePath
        if (path != null) {
            val file = remember(path) { File(path) }
            if (file.exists()) {
                AsyncImage(
                    model = file,
                    contentDescription = "Zoomed Receipt",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (scale > 1f) {
                                        scale = 1f
                                        offset = Offset.Zero
                                    } else {
                                        scale = 3.5f
                                        offset = Offset.Zero
                                    }
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 6f)
                                if (scale > 1f) {
                                    offset = offset + pan
                                } else {
                                    offset = Offset.Zero
                                }
                            }
                        }
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                )
            }
        }

        // Close button at top-left
        IconButton(
            onClick = { viewModel.navigateTo(liveReturnScreen) },
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Black.copy(alpha = 0.5f),
                contentColor = Color.White
            ),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(24.dp)
                .size(48.dp)
                .clip(CircleShape)
                .testTag("btn_close_zoom")
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close Zoom",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
