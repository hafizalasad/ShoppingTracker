package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.MainScreen
import com.example.ui.screens.ShopDetailsScreen
import com.example.ui.screens.ZoomImageScreen
import com.example.ui.screens.ScanReceiptScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ExpenseViewModel
import com.example.ui.viewmodel.Screen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: ExpenseViewModel = viewModel()
                val currentScreen by viewModel.currentScreen.collectAsState()

                Surface(modifier = Modifier.fillMaxSize()) {
                    Crossfade(targetState = currentScreen, label = "screen_transition") { screen ->
                        when (screen) {
                            is Screen.Main -> {
                                MainScreen(viewModel = viewModel)
                            }
                            is Screen.ShopDetails -> {
                                ShopDetailsScreen(shopName = screen.shopName, viewModel = viewModel)
                            }
                            is Screen.ZoomImage -> {
                                ZoomImageScreen(
                                    imagePath = screen.imagePath,
                                    returnScreen = screen.returnScreen,
                                    viewModel = viewModel
                                )
                            }
                            is Screen.ScanReceipt -> {
                                ScanReceiptScreen(viewModel = viewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}
