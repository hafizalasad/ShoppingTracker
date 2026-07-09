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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
    val aiModel by viewModel.aiModel.collectAsState()
    val aiApiKey by viewModel.aiApiKey.collectAsState()
    val aiBaseUrl by viewModel.aiBaseUrl.collectAsState()

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
                            text = if (aiProvider == "gemini") "Gemini AI is scanning..." else "${aiModel.substringAfterLast("/")} is scanning...",
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
        var tempProvider by remember { mutableStateOf(aiProvider) }
        var tempModel by remember { mutableStateOf(aiModel) }
        var tempApiKey by remember { mutableStateOf(aiApiKey) }
        var tempBaseUrl by remember { mutableStateOf(aiBaseUrl) }

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
                        text = "Configure alternative models and providers to avoid Gemini API rate limits or use free endpoints.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Provider selection card
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "API Provider Type",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { tempProvider = "gemini" }
                                ) {
                                    androidx.compose.material3.RadioButton(
                                        selected = tempProvider == "gemini",
                                        onClick = { tempProvider = "gemini" }
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Gemini", style = MaterialTheme.typography.bodyMedium)
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { tempProvider = "openai" }
                                ) {
                                    androidx.compose.material3.RadioButton(
                                        selected = tempProvider == "openai",
                                        onClick = { tempProvider = "openai" }
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("OpenAI / Custom", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }

                    // Base URL (only for OpenAI-compatible)
                    if (tempProvider == "openai") {
                        OutlinedTextField(
                            value = tempBaseUrl,
                            onValueChange = { tempBaseUrl = it },
                            label = { Text("Base URL") },
                            placeholder = { Text("e.g., https://openrouter.ai/api/v1/") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Model name field
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = tempModel,
                            onValueChange = { tempModel = it },
                            label = { Text("Model Name") },
                            placeholder = { Text(if (tempProvider == "gemini") "e.g., gemini-1.5-flash" else "e.g., google/gemini-2.5-flash:free") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Model suggestions row
                        Text("Suggested Models:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (tempProvider == "gemini") {
                                listOf("gemini-3.5-flash", "gemini-2.5-flash", "gemini-1.5-flash").forEach { suggestedModel ->
                                    val displayName = suggestedModel
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.secondaryContainer)
                                            .clickable { tempModel = suggestedModel }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = displayName,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            } else {
                                listOf(
                                    "google/gemini-2.5-flash:free",
                                    "meta-llama/llama-3.2-11b-vision-instruct:free",
                                    "deepseek-chat"
                                ).forEach { suggestedModel ->
                                    val displayName = suggestedModel.substringAfterLast("/")
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.secondaryContainer)
                                            .clickable {
                                                tempModel = suggestedModel
                                                if (suggestedModel.contains("deepseek")) {
                                                    tempBaseUrl = "https://api.deepseek.com/v1/"
                                                } else {
                                                    tempBaseUrl = "https://openrouter.ai/api/v1/"
                                                }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = displayName,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // API Key field
                    OutlinedTextField(
                        value = tempApiKey,
                        onValueChange = { tempApiKey = it },
                        label = { Text("API Key") },
                        placeholder = { Text(if (tempProvider == "gemini") "Default: App Secrets key" else "Your API key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (tempApiKey.isBlank()) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation()
                    )

                    if (tempProvider == "openai") {
                        Text(
                            text = "💡 OpenRouter provides FREE models like google/gemini-2.5-flash:free. You can get an API key at openrouter.ai.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = "💡 If left blank, the app will use the default GEMINI_API_KEY from the system secrets.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateAiSettings(
                            provider = tempProvider,
                            model = tempModel,
                            apiKey = tempApiKey,
                            baseUrl = tempBaseUrl
                        )
                        showSettingsDialog = false
                    }
                ) {
                    Text("Save")
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
