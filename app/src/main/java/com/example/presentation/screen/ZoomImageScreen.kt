package com.example.presentation.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.presentation.viewmodel.ExpenseViewModel
import com.example.presentation.viewmodel.Screen
import com.example.presentation.viewmodel.ZoomImageViewModel
import java.io.File
import java.util.Locale

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
    val imageVersion by zoomViewModel.imageVersion.collectAsState()
    val isRotating by zoomViewModel.isRotating.collectAsState()

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
        val context = LocalContext.current

        if (path != null) {
            val file = remember(path) { File(path) }
            if (file.exists()) {
                val imageRequest = remember(path, imageVersion) {
                    ImageRequest.Builder(context)
                        .data(file)
                        .memoryCacheKey("${file.absolutePath}?v=$imageVersion")
                        .diskCacheKey("${file.absolutePath}?v=$imageVersion")
                        .crossfade(true)
                        .build()
                }

                AsyncImage(
                    model = imageRequest,
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

        // Top Navigation & Action Controls Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 40.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Close button
            IconButton(
                onClick = { viewModel.navigateTo(liveReturnScreen) },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.6f),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .testTag("btn_close_zoom")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Zoom",
                    modifier = Modifier.size(22.dp)
                )
            }

            // Action Buttons: Rotate Left, Rotate Right, Reset Zoom
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(24.dp),
                contentColor = Color.White
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rotate Left (-90°)
                    IconButton(
                        onClick = {
                            zoomViewModel.rotateImage(-90f) {
                                scale = 1f
                                offset = Offset.Zero
                            }
                        },
                        enabled = !isRotating,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("btn_rotate_left")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RotateLeft,
                            contentDescription = "Rotate Left (-90°)",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Rotate Right (+90°)
                    IconButton(
                        onClick = {
                            zoomViewModel.rotateImage(90f) {
                                scale = 1f
                                offset = Offset.Zero
                            }
                        },
                        enabled = !isRotating,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("btn_rotate_right")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RotateRight,
                            contentDescription = "Rotate Right (+90°)",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Reset Zoom Button (when zoomed)
                    if (scale > 1.05f || offset != Offset.Zero) {
                        IconButton(
                            onClick = {
                                scale = 1f
                                offset = Offset.Zero
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("btn_reset_zoom")
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = "Reset Zoom",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // Saving / Rotating Progress Indicator Overlay
        AnimatedVisibility(
            visible = isRotating,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.85f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Saving rotation (100% quality)...",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Bottom Zoom Info Badge
        Surface(
            color = Color.Black.copy(alpha = 0.5f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${String.format(Locale.US, "%.1f", scale)}x",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Double-tap or pinch to zoom • Rotate to save",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 11.sp
                )
            }
        }
    }
}
