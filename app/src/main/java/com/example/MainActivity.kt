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
import com.example.presentation.screen.MainScreen
import com.example.presentation.screen.ScanReceiptScreen
import com.example.presentation.screen.SettingsScreen
import com.example.presentation.screen.ShopDetailsScreen
import com.example.presentation.screen.ZoomImageScreen
import com.example.presentation.viewmodel.ExpenseViewModel
import com.example.presentation.viewmodel.Screen
import com.example.ui.theme.MyApplicationTheme

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
                            is Screen.Settings -> {
                                SettingsScreen(viewModel = viewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}
