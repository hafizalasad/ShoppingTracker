package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.ui.viewmodel.ExpenseViewModel
import com.example.ui.viewmodel.ScanUiIntent
import com.example.ui.viewmodel.Screen
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanReceiptScreen(
    viewModel: ExpenseViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Collect single unified MVI UI State (MVI Rule #1)
    val state by viewModel.scanUiState.collectAsState()

    val aiProvider by viewModel.aiProvider.collectAsState()
    val geminiApiKey by viewModel.geminiApiKey.collectAsState()
    val deepseekApiKey by viewModel.deepseekApiKey.collectAsState()
    val openaiApiKey by viewModel.openaiApiKey.collectAsState()
    val geminiModel by viewModel.geminiModel.collectAsState()
    val deepseekModel by viewModel.deepseekModel.collectAsState()
    val openaiModel by viewModel.openaiModel.collectAsState()
    val deepseekBaseUrl by viewModel.deepseekBaseUrl.collectAsState()
    val openaiBaseUrl by viewModel.openaiBaseUrl.collectAsState()

    val currentActiveModel = when (aiProvider) {
        "gemini" -> geminiModel
        "deepseek" -> deepseekModel
        else -> openaiModel
    }

    var showSettingsDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        viewModel.navigateTo(Screen.Main)
    }

    // Setup temp file for camera capture
    val tempFile = remember { File(context.cacheDir, "temp_camera_receipt.jpg") }
    val cameraUri = remember {
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempFile.exists()) {
            val savedPath = copyFileToInternalStorage(context, tempFile)
            if (savedPath != null) {
                val bitmap = BitmapFactory.decodeFile(savedPath)
                if (bitmap != null) {
                    // Emit MVI Intent to handle selected image (MVI Rule #2)
                    viewModel.onScanIntent(ScanUiIntent.SelectImage(savedPath, bitmap))
                }
            }
        }
    }

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val savedPath = copyUriToInternalStorage(context, uri)
            if (savedPath != null) {
                val bitmap = uriToBitmap(context, uri)
                if (bitmap != null) {
                    // Emit MVI Intent to handle selected image (MVI Rule #2)
                    viewModel.onScanIntent(ScanUiIntent.SelectImage(savedPath, bitmap))
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Receipt", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo(Screen.Main) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "AI Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (state.imagePath == null) {
                // Step 1: Selection layout
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = "Upload Receipt",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(100.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (aiProvider == "gemini") "Analyze Receipts using Gemini AI" else "Analyze Receipts using custom AI",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Take a photo of your receipt or upload it from your gallery. Our AI will automatically extract the shop name and amount spent in Taka (BDT) or other preferred currencies.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(40.dp))

                        Button(
                            onClick = { cameraLauncher.launch(cameraUri) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("take_photo_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(imageVector = Icons.Default.CameraAlt, contentDescription = "Camera")
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Take Photo", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedButton(
                            onClick = {
                                galleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("choose_gallery_button")
                        ) {
                            Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = "Gallery")
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Choose from Gallery", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            } else if (state.isAnalyzing) {
                // Step 2: AI Analyzing layout
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .size(240.dp)
                                .clip(RoundedCornerShape(20.dp)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            AsyncImage(
                                model = File(state.imagePath!!),
                                contentDescription = "Receipt scanning preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (aiProvider == "gemini") "Gemini AI is scanning..." else "${currentActiveModel.substringAfterLast("/")} is scanning...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Extracting store details, spent amounts, and items...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // Step 3: Edit and Confirm Extracted data layout
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
                    // Receipt thumbnail
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable {
                                    viewModel.navigateTo(Screen.ZoomImage(state.imagePath!!, Screen.ScanReceipt))
                                }
                                .testTag("scan_preview_zoom"),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = File(state.imagePath!!),
                                contentDescription = "Scanned Receipt preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.BottomEnd
                            ) {
                                Text(
                                    "Tap to zoom",
                                    color = androidx.compose.ui.graphics.Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Verify Receipt Details",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Review and edit the values extracted by Gemini AI before saving.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Notice if offline
                    AnimatedVisibility(visible = state.isOffline) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudUpload,
                                    contentDescription = "Offline mode",
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "You are currently offline. We saved your receipt locally! Gemini AI will automatically process this in the background once you're online.",
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // Warning if there was an error analyzing
                    AnimatedVisibility(visible = state.analysisError != null) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Error icon",
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "AI extraction error: ${state.analysisError}. Please enter the details manually below.",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // Text Field: Shop Name
                    OutlinedTextField(
                        value = state.shopName,
                        onValueChange = { viewModel.onScanIntent(ScanUiIntent.UpdateShopName(it)) },
                        label = { Text("Shop Name") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Shop Name",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("shop_name_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Text Field: Amount
                    OutlinedTextField(
                        value = state.amount,
                        onValueChange = { viewModel.onScanIntent(ScanUiIntent.UpdateAmount(it)) },
                        label = { Text("Spent Amount (${viewModel.getCurrencySymbol()})") },
                        leadingIcon = {
                            Text(
                                viewModel.getCurrencySymbol(),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("amount_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Date selector input field (readonly outlines textfield opening datepicker dialog)
                    OutlinedTextField(
                        value = formatDate(state.selectedDate),
                        onValueChange = {},
                        label = { Text("Date of Purchase") },
                        leadingIcon = {
                            IconButton(onClick = { viewModel.onScanIntent(ScanUiIntent.ToggleDatePicker(true)) }) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = "Select Date"
                                )
                            }
                        },
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.onScanIntent(ScanUiIntent.ToggleDatePicker(true)) }
                            .testTag("date_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = {
                            // Emit MVI Intent to save expense (MVI Rule #2)
                            viewModel.onScanIntent(ScanUiIntent.SaveExpense)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("save_expense_button")
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = "Save")
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Save Expense", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = { viewModel.onScanIntent(ScanUiIntent.ResetScan) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("cancel_button")
                    ) {
                        Text("Scan Another", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (state.showDatePicker) {
        SingleDatePickerDialog(
            initialDate = state.selectedDate,
            onDismiss = { viewModel.onScanIntent(ScanUiIntent.ToggleDatePicker(false)) },
            onDateSelected = { date ->
                viewModel.onScanIntent(ScanUiIntent.UpdateDate(date))
            }
        )
    }

    if (showSettingsDialog) {
        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

        var tempProvider by remember { mutableStateOf(aiProvider) }
        var tempGeminiKey by remember { mutableStateOf(geminiApiKey) }
        var tempDeepseekKey by remember { mutableStateOf(deepseekApiKey) }
        var tempOpenaiKey by remember { mutableStateOf(openaiApiKey) }
        var tempGeminiModel by remember { mutableStateOf(geminiModel) }
        var tempDeepseekModel by remember { mutableStateOf(deepseekModel) }
        var tempOpenaiModel by remember { mutableStateOf(openaiModel) }
        var tempDeepseekBaseUrl by remember { mutableStateOf(deepseekBaseUrl) }
        var tempOpenaiBaseUrl by remember { mutableStateOf(openaiBaseUrl) }

        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AI Provider Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Configure alternative models and providers to avoid API rate limits or use free endpoints.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Automatic failover info card
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Failover Enabled",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "🔄 Automatic Failover Enabled: If your preferred AI fails, the app will automatically try your other configured keys in real-time.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Dropdown for preferred provider selection
                    var dropdownExpanded by remember { mutableStateOf(false) }
                    val providerDisplay = when (tempProvider) {
                        "gemini" -> "Gemini (Google AI)"
                        "deepseek" -> "DeepSeek AI"
                        else -> "OpenAI / Custom"
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Preferred AI Provider",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Box(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = providerDisplay,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Priority Scanner") },
                                trailingIcon = {
                                    Icon(
                                        imageVector = if (dropdownExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Expand provider selection"
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = false,
                                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { dropdownExpanded = true }
                            )

                            DropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.8f)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Gemini (Google AI) - First Priority") },
                                    onClick = {
                                        tempProvider = "gemini"
                                        dropdownExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("DeepSeek AI - First Priority") },
                                    onClick = {
                                        tempProvider = "deepseek"
                                        dropdownExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("OpenAI / Custom - First Priority") },
                                    onClick = {
                                        tempProvider = "openai"
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // SECTION 1: Google Gemini settings
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (tempProvider == "gemini") MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Google Gemini settings",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (tempProvider == "gemini") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                if (tempProvider == "gemini") {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("★ Preferred", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                            }

                            OutlinedTextField(
                                value = tempGeminiModel,
                                onValueChange = { tempGeminiModel = it },
                                label = { Text("Model Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf("gemini-3.5-flash", "gemini-2.5-flash", "gemini-1.5-flash").forEach { suggestedModel ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable { tempGeminiModel = suggestedModel }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(suggestedModel, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = tempGeminiKey,
                                onValueChange = { tempGeminiKey = it },
                                label = { Text("Gemini API Key") },
                                placeholder = { Text("Default: AI Studio System Key") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                visualTransformation = if (tempGeminiKey.isBlank()) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation()
                            )

                            Button(
                                onClick = { uriHandler.openUri("https://aistudio.google.com/") },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), contentColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("🔑 Get Gemini API Key", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }

                    // SECTION 2: DeepSeek AI settings
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (tempProvider == "deepseek") MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "DeepSeek AI settings",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (tempProvider == "deepseek") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                if (tempProvider == "deepseek") {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("★ Preferred", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                            }

                            OutlinedTextField(
                                value = tempDeepseekBaseUrl,
                                onValueChange = { tempDeepseekBaseUrl = it },
                                label = { Text("Base URL") },
                                placeholder = { Text("https://api.deepseek.com/v1/") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = tempDeepseekModel,
                                onValueChange = { tempDeepseekModel = it },
                                label = { Text("Model Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf("deepseek-chat", "deepseek-reasoner").forEach { suggestedModel ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable { tempDeepseekModel = suggestedModel }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(suggestedModel, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = tempDeepseekKey,
                                onValueChange = { tempDeepseekKey = it },
                                label = { Text("DeepSeek API Key") },
                                placeholder = { Text("Enter DeepSeek API key") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                visualTransformation = if (tempDeepseekKey.isBlank()) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation()
                            )

                            Button(
                                onClick = { uriHandler.openUri("https://platform.deepseek.com/") },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), contentColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("🔑 Get DeepSeek API Key", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }

                    // SECTION 3: OpenAI / OpenRouter settings
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (tempProvider == "openai") MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "OpenAI / Custom / OpenRouter settings",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (tempProvider == "openai") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                if (tempProvider == "openai") {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("★ Preferred", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                            }

                            OutlinedTextField(
                                value = tempOpenaiBaseUrl,
                                onValueChange = { tempOpenaiBaseUrl = it },
                                label = { Text("Base URL") },
                                placeholder = { Text("e.g. https://openrouter.ai/api/v1/") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = tempOpenaiModel,
                                onValueChange = { tempOpenaiModel = it },
                                label = { Text("Model Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf("google/gemini-2.5-flash:free", "deepseek/deepseek-chat").forEach { suggestedModel ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable { tempOpenaiModel = suggestedModel }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(suggestedModel.substringAfterLast("/"), style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = tempOpenaiKey,
                                onValueChange = { tempOpenaiKey = it },
                                label = { Text("OpenAI / OpenRouter API Key") },
                                placeholder = { Text("Enter custom/OpenRouter API key") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                visualTransformation = if (tempOpenaiKey.isBlank()) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation()
                            )

                            Button(
                                onClick = { uriHandler.openUri("https://openrouter.ai/") },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), contentColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("🔑 Get OpenRouter API Key", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateAiSettings(
                            provider = tempProvider,
                            geminiKey = tempGeminiKey,
                            deepseekKey = tempDeepseekKey,
                            openaiKey = tempOpenaiKey,
                            geminiModel = tempGeminiModel,
                            deepseekModel = tempDeepseekModel,
                            openaiModel = tempOpenaiModel,
                            deepseekBaseUrl = tempDeepseekBaseUrl,
                            openaiBaseUrl = tempOpenaiBaseUrl
                        )
                        showSettingsDialog = false
                    }
                ) {
                    Text("Save All Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleDatePickerDialog(
    initialDate: Long,
    onDismiss: () -> Unit,
    onDateSelected: (Long) -> Unit
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initialDate)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val selected = state.selectedDateMillis
                    if (selected != null) {
                        onDateSelected(selected)
                    }
                    onDismiss()
                }
            ) {
                Text("Select", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DatePicker(state = state)
    }
}

private fun uriToBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        BitmapFactory.decodeStream(inputStream)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun copyUriToInternalStorage(context: Context, uri: Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val filename = "receipt_${System.currentTimeMillis()}.jpg"
        val file = File(context.filesDir, filename)
        val outputStream = FileOutputStream(file)
        inputStream.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }
        file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun copyFileToInternalStorage(context: Context, sourceFile: File): String? {
    return try {
        val filename = "receipt_${System.currentTimeMillis()}.jpg"
        val destFile = File(context.filesDir, filename)
        sourceFile.copyTo(destFile, overwrite = true)
        destFile.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
