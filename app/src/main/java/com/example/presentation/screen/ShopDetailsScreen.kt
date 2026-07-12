package com.example.presentation.screen

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.core.util.CurrencyUtils
import com.example.core.util.DateFormatter
import com.example.domain.model.Expense
import com.example.presentation.component.SingleDatePickerDialog
import com.example.presentation.intent.ShopDetailsUiIntent
import com.example.presentation.viewmodel.ExpenseViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopDetailsScreen(
    shopName: String,
    viewModel: ExpenseViewModel,
    modifier: Modifier = Modifier
) {
    val stateFlow = remember(shopName) { viewModel.getShopDetailsUiState(shopName) }
    val state by stateFlow.collectAsState()
    val scanningIds by viewModel.manualScanningIds.collectAsState()
    val scanningErrors by viewModel.manualScanningErrors.collectAsState()

    BackHandler(enabled = true) {
        viewModel.onShopDetailsIntent(ShopDetailsUiIntent.GoBack)
    }

    var editingExpense by remember { mutableStateOf<Expense?>(null) }

    if (editingExpense != null) {
        val expenseToEdit = editingExpense!!
        var editShopName by remember(expenseToEdit) { mutableStateOf(expenseToEdit.shopName) }
        var editAmount by remember(expenseToEdit) { mutableStateOf(expenseToEdit.amount.toString()) }
        var editDate by remember(expenseToEdit) { mutableStateOf(expenseToEdit.date) }
        var editNote by remember(expenseToEdit) { mutableStateOf(expenseToEdit.note) }
        var showDatePicker by remember { mutableStateOf(false) }

        if (showDatePicker) {
            SingleDatePickerDialog(
                initialDate = editDate,
                onDismiss = { showDatePicker = false },
                onDateSelected = { date ->
                    editDate = date
                }
            )
        }

        androidx.compose.material3.AlertDialog(
            onDismissRequest = { editingExpense = null },
            title = {
                Text(
                    text = "Edit Transaction",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = editShopName,
                        onValueChange = { editShopName = it },
                        label = { Text("Shop Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("edit_dialog_shop_name")
                    )

                    OutlinedTextField(
                        value = editAmount,
                        onValueChange = { editAmount = it },
                        label = { Text("Spent Amount (${viewModel.getCurrencySymbol()})") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("edit_dialog_amount")
                    )

                    OutlinedTextField(
                        value = DateFormatter.formatDate(editDate),
                        onValueChange = {},
                        label = { Text("Date of Purchase") },
                        leadingIcon = {
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = "Select Date"
                                )
                            }
                        },
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDatePicker = true }
                            .testTag("edit_dialog_date"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = editNote,
                        onValueChange = { editNote = it },
                        label = { Text("Note / Description") },
                        placeholder = { Text("Add notes or item details...") },
                        maxLines = 3,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("edit_dialog_note")
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = {
                        val amountVal = editAmount.toDoubleOrNull() ?: expenseToEdit.amount
                        val updatedExpense = expenseToEdit.copy(
                            shopName = editShopName.ifBlank { expenseToEdit.shopName },
                            amount = amountVal,
                            date = editDate,
                            note = editNote
                        )
                        viewModel.onShopDetailsIntent(ShopDetailsUiIntent.UpdateExpense(updatedExpense))
                        editingExpense = null
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { editingExpense = null }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(shopName, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.onShopDetailsIntent(ShopDetailsUiIntent.GoBack) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
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
        if (state.expenses.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No expenses found for this shop in this period.",
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(state.expenses) { expense ->
                    val isScanning = scanningIds.contains(expense.id)
                    val scanError = scanningErrors[expense.id]
                    ExpenseDetailCard(
                        expense = expense,
                        formattedAmount = CurrencyUtils.formatBangladeshiStyle(viewModel.getCurrencySymbol(), expense.amount),
                        isScanning = isScanning,
                        scanError = scanError,
                        onRefreshScanClick = {
                            viewModel.onShopDetailsIntent(ShopDetailsUiIntent.TriggerScan(expense))
                        },
                        onImageClick = { path ->
                            viewModel.onShopDetailsIntent(ShopDetailsUiIntent.ZoomImage(path, shopName))
                        },
                        onEditClick = {
                            editingExpense = expense
                        },
                        onDeleteClick = {
                            viewModel.onShopDetailsIntent(ShopDetailsUiIntent.DeleteExpense(expense))
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ExpenseDetailCard(
    expense: Expense,
    formattedAmount: String,
    isScanning: Boolean,
    scanError: String?,
    onRefreshScanClick: () -> Unit,
    onImageClick: (String) -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("expense_detail_card_${expense.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Spent Amount",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formattedAmount,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (expense.imagePath != null) {
                        val file = File(expense.imagePath)
                        if (file.exists()) {
                            if (isScanning) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp)
                                        .size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                IconButton(
                                    onClick = onRefreshScanClick,
                                    colors = IconButtonDefaults.iconButtonColors(
                                        contentColor = MaterialTheme.colorScheme.tertiary
                                    ),
                                    modifier = Modifier.testTag("refresh_scan_${expense.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Re-scan Receipt Offline"
                                    )
                                }
                            }
                        }
                    }
                    IconButton(
                        onClick = onEditClick,
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("edit_expense_${expense.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Expense"
                        )
                    }
                    IconButton(
                        onClick = onDeleteClick,
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.testTag("delete_expense_${expense.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Expense"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Spent on: ${DateFormatter.formatDate(expense.date)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (expense.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notes,
                                contentDescription = "Note Icon",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Note",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = expense.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (scanError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("scan_error_card_${expense.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Scan Error Icon",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Scan Error: $scanError",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            if (expense.imagePath != null) {
                Spacer(modifier = Modifier.height(12.dp))
                val file = File(expense.imagePath)
                if (file.exists()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onImageClick(expense.imagePath) }
                            .testTag("image_preview_${expense.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = file,
                            contentDescription = "Receipt Image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            Text(
                                "Tap to zoom",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ImageNotSupported,
                            contentDescription = "Image missing",
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Image file not found on disk",
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
