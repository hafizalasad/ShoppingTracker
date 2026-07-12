package com.example.presentation.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.datasource.ExpenseDatabase
import com.example.data.repository.ExpenseRepositoryImpl
import com.example.domain.model.Expense
import com.example.domain.usecase.DeleteExpenseUseCase
import com.example.domain.usecase.GetExpensesUseCase
import com.example.domain.usecase.SaveExpenseUseCase
import com.example.domain.usecase.ScanReceiptUseCase
import com.example.presentation.intent.MainUiIntent
import com.example.presentation.intent.ScanUiIntent
import com.example.presentation.intent.ShopDetailsUiIntent
import com.example.presentation.state.MainUiState
import com.example.presentation.state.ScanUiState
import com.example.presentation.state.ShopDetailsUiState
import com.example.presentation.state.ShopExpenseSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar
import java.util.Locale

sealed class Screen {
    object Main : Screen()
    data class ShopDetails(val shopName: String) : Screen()
    data class ZoomImage(val imagePath: String, val returnScreen: Screen) : Screen()
    object ScanReceipt : Screen()
    object Settings : Screen()
}

class ExpenseViewModel @JvmOverloads constructor(
    application: Application,
    private val getExpensesUseCase: GetExpensesUseCase = GetExpensesUseCase(
        ExpenseRepositoryImpl(ExpenseDatabase.getDatabase(application).expenseDao())
    ),
    private val saveExpenseUseCase: SaveExpenseUseCase = SaveExpenseUseCase(
        ExpenseRepositoryImpl(ExpenseDatabase.getDatabase(application).expenseDao())
    ),
    private val deleteExpenseUseCase: DeleteExpenseUseCase = DeleteExpenseUseCase(
        ExpenseRepositoryImpl(ExpenseDatabase.getDatabase(application).expenseDao())
    ),
    private val scanReceiptUseCase: ScanReceiptUseCase = ScanReceiptUseCase(
        ExpenseRepositoryImpl(ExpenseDatabase.getDatabase(application).expenseDao())
    )
) : AndroidViewModel(application) {

    private val sharedPrefs = application.getSharedPreferences("expense_tracker_prefs", Context.MODE_PRIVATE)

    // Supported Currencies
    val supportedCurrencies = listOf("USD", "BDT", "EUR", "GBP", "JPY", "INR", "CAD", "AUD", "SGD")

    private val _selectedCurrency = MutableStateFlow(sharedPrefs.getString("preferred_currency", "USD") ?: "USD")
    val selectedCurrency: StateFlow<String> = _selectedCurrency.asStateFlow()

    fun setCurrency(currencyCode: String) {
        _selectedCurrency.value = currencyCode
        sharedPrefs.edit().putString("preferred_currency", currencyCode).apply()
    }

    fun getCurrencySymbol(code: String = _selectedCurrency.value): String {
        return when (code) {
            "USD" -> "$"
            "BDT" -> "৳"
            "EUR" -> "€"
            "GBP" -> "£"
            "JPY" -> "¥"
            "INR" -> "₹"
            "CAD" -> "CA$"
            "AUD" -> "AU$"
            "SGD" -> "SG$"
            else -> "$"
        }
    }

    // Screen navigation state
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Main)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    // Date range filter states (initially first day of current month to today's date)
    private val _startDate = MutableStateFlow<Long>(getStartOfMonthTimestamp())
    val startDate: StateFlow<Long> = _startDate.asStateFlow()

    private val _endDate = MutableStateFlow<Long>(System.currentTimeMillis())
    val endDate: StateFlow<Long> = _endDate.asStateFlow()

    fun setDateRange(start: Long, end: Long) {
        _startDate.value = start
        _endDate.value = end
    }

    // Dynamic filtering of expenses in date range
    @OptIn(ExperimentalCoroutinesApi::class)
    val expensesInRange: StateFlow<List<Expense>> = combine(
        _startDate, _endDate
    ) { start, end ->
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = start
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val adjustedStart = calendar.timeInMillis

        calendar.timeInMillis = end
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val adjustedEnd = calendar.timeInMillis

        adjustedStart to adjustedEnd
    }.flatMapLatest { (start, end) ->
        getExpensesUseCase.getExpensesInDateRange(start, end)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Aggregate summaries of total spent per shop in date range, sorted by date DESC
    val shopSummaries: StateFlow<List<ShopExpenseSummary>> = expensesInRange.map { list ->
        if (list.isEmpty()) return@map emptyList()
        list.groupBy { it.shopName.trim() }
            .map { (shopName, expenses) ->
                val latestDate = expenses.maxOfOrNull { it.date } ?: 0L
                ShopExpenseSummary(
                    shopName = shopName,
                    totalAmount = expenses.sumOf { it.amount },
                    expenseCount = expenses.size,
                    latestDate = latestDate
                )
            }.sortedByDescending { it.latestDate }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Unified MVI State Flow for Main Screen
    val mainUiState: StateFlow<MainUiState> = combine(
        shopSummaries,
        expensesInRange,
        _startDate,
        _endDate,
        _selectedCurrency
    ) { summaries, expenses, start, end, currency ->
        MainUiState(
            shopSummaries = summaries,
            expensesInRange = expenses,
            startDate = start,
            endDate = end,
            selectedCurrency = currency,
            currencySymbol = getCurrencySymbol(currency),
            totalSpent = summaries.sumOf { it.totalAmount }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState()
    )

    // Unified MVI State for Scan Screen
    private val _scanUiState = MutableStateFlow(loadScanDraft())
    val scanUiState: StateFlow<ScanUiState> = _scanUiState.asStateFlow()

    private val _manualScanningIds = MutableStateFlow<Set<Long>>(emptySet())
    val manualScanningIds: StateFlow<Set<Long>> = _manualScanningIds.asStateFlow()

    private val _manualScanningErrors = MutableStateFlow<Map<Long, String>>(emptyMap())
    val manualScanningErrors: StateFlow<Map<Long, String>> = _manualScanningErrors.asStateFlow()

    private fun loadScanDraft(): ScanUiState {
        val prefs = getApplication<Application>().getSharedPreferences("shop_expense_prefs", Context.MODE_PRIVATE)
        val imagePath = prefs.getString("draft_image_path", null)
        val shopName = prefs.getString("draft_shop_name", "") ?: ""
        val amount = prefs.getString("draft_amount", "") ?: ""
        val selectedDate = prefs.getLong("draft_selected_date", System.currentTimeMillis())
        return ScanUiState(
            imagePath = imagePath,
            shopName = shopName,
            amount = amount,
            selectedDate = selectedDate,
            isOffline = true
        )
    }

    init {
        // Collect scan state and save to draft SharedPreferences reactively
        viewModelScope.launch {
            _scanUiState.collect { state ->
                val prefs = getApplication<Application>().getSharedPreferences("shop_expense_prefs", Context.MODE_PRIVATE)
                prefs.edit().apply {
                    putString("draft_image_path", state.imagePath)
                    putString("draft_shop_name", state.shopName)
                    putString("draft_amount", state.amount)
                    putLong("draft_selected_date", state.selectedDate)
                    putBoolean("draft_is_offline", true)
                    apply()
                }
            }
        }
    }

    fun onMainIntent(intent: MainUiIntent) {
        when (intent) {
            is MainUiIntent.SetDateRange -> setDateRange(intent.start, intent.end)
            is MainUiIntent.SetCurrency -> setCurrency(intent.currencyCode)
            is MainUiIntent.NavigateToScan -> navigateTo(Screen.ScanReceipt)
            is MainUiIntent.NavigateToShopDetails -> navigateTo(Screen.ShopDetails(intent.shopName))
        }
    }

    fun onScanIntent(intent: ScanUiIntent) {
        when (intent) {
            is ScanUiIntent.SelectImage -> {
                viewModelScope.launch {
                    _scanUiState.update { 
                        it.copy(
                            isAnalyzing = true,
                            analysisError = null,
                            imagePath = intent.path,
                            confidenceScore = null,
                            isConfidenceLow = false
                        ) 
                    }
                    
                    try {
                        val result = scanReceiptUseCase(intent.bitmap)
                        _scanUiState.update {
                            it.copy(
                                shopName = result.merchant ?: "Unknown Shop",
                                amount = if (result.amount != null) String.format(Locale.US, "%.2f", result.amount) else "0.00",
                                selectedDate = result.date ?: it.selectedDate,
                                confidenceScore = result.confidence,
                                isConfidenceLow = result.isConfidenceLow,
                                analysisError = result.error,
                                scannedOffline = true,
                                scannedWithAi = false
                            )
                        }
                    } catch (e: Exception) {
                        _scanUiState.update { 
                            it.copy(analysisError = "Scanning failed: ${e.message}") 
                        }
                    } finally {
                        _scanUiState.update { it.copy(isAnalyzing = false) }
                    }
                }
            }
            is ScanUiIntent.UpdateShopName -> {
                _scanUiState.update { it.copy(shopName = intent.name) }
            }
            is ScanUiIntent.UpdateAmount -> {
                _scanUiState.update { it.copy(amount = intent.amount) }
            }
            is ScanUiIntent.UpdateDate -> {
                _scanUiState.update { it.copy(selectedDate = intent.date) }
            }
            is ScanUiIntent.UpdateNote -> {
                _scanUiState.update { it.copy(note = intent.note) }
            }
            is ScanUiIntent.StartManualEntry -> {
                _scanUiState.update { it.copy(isManualEntry = intent.isManual, imagePath = null) }
            }
            is ScanUiIntent.ToggleDatePicker -> {
                _scanUiState.update { it.copy(showDatePicker = intent.show) }
            }
            is ScanUiIntent.SaveExpense -> {
                val state = _scanUiState.value
                val amountVal = state.amount.toDoubleOrNull() ?: 0.0
                saveExpense(
                    shopName = state.shopName.ifBlank { "Offline Receipt" },
                    amount = amountVal,
                    date = state.selectedDate,
                    imagePath = state.imagePath,
                    isPendingAnalysis = false,
                    note = state.note
                )
                _scanUiState.value = ScanUiState()
            }
            is ScanUiIntent.ResetScan -> {
                _scanUiState.value = ScanUiState()
            }
        }
    }

    fun handleBackNavigationFromScan() {
        val state = _scanUiState.value
        val hasData = state.imagePath != null ||
                state.shopName.isNotBlank() ||
                state.amount.isNotBlank() ||
                state.note.isNotBlank()

        if (hasData) {
            val amountVal = state.amount.toDoubleOrNull() ?: 0.0
            saveExpense(
                shopName = state.shopName.ifBlank { "Offline Receipt" },
                amount = amountVal,
                date = state.selectedDate,
                imagePath = state.imagePath,
                isPendingAnalysis = false,
                note = state.note
            )
            _scanUiState.value = ScanUiState()
        }
        navigateTo(Screen.Main)
    }

    // Unified MVI State and Intent processor for Shop Details Screen
    fun getShopDetailsUiState(shopName: String): StateFlow<ShopDetailsUiState> {
        return expensesInRange
            .map { list ->
                val filtered = list.filter { it.shopName.trim().lowercase() == shopName.trim().lowercase() }
                ShopDetailsUiState(shopName = shopName, expenses = filtered)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = ShopDetailsUiState(shopName = shopName)
            )
    }

    fun onShopDetailsIntent(intent: ShopDetailsUiIntent) {
        when (intent) {
            is ShopDetailsUiIntent.DeleteExpense -> {
                deleteExpense(intent.expense.id)
            }
            is ShopDetailsUiIntent.UpdateExpense -> {
                updateExpense(intent.expense)
            }
            is ShopDetailsUiIntent.GoBack -> {
                navigateTo(Screen.Main)
            }
            is ShopDetailsUiIntent.ZoomImage -> {
                navigateTo(Screen.ZoomImage(intent.imagePath, Screen.ShopDetails(intent.shopName)))
            }
            is ShopDetailsUiIntent.TriggerScan -> {
                triggerManualReceiptScan(intent.expense)
            }
        }
    }

    fun saveExpense(shopName: String, amount: Double, date: Long, imagePath: String?, isPendingAnalysis: Boolean, note: String) {
        viewModelScope.launch {
            saveExpenseUseCase(
                Expense(
                    shopName = shopName,
                    amount = amount,
                    date = date,
                    imagePath = imagePath,
                    isPendingAnalysis = isPendingAnalysis,
                    note = note
                )
            )
        }
    }

    fun deleteExpense(id: Long) {
        viewModelScope.launch {
            deleteExpenseUseCase(id)
        }
    }

    fun updateExpense(expense: Expense) {
        viewModelScope.launch {
            saveExpenseUseCase(expense)
        }
    }

    fun triggerManualReceiptScan(expense: Expense) {
        val path = expense.imagePath ?: return
        val file = File(path)
        if (!file.exists()) {
            _manualScanningErrors.update { it + (expense.id to "Receipt image file not found on disk.") }
            return
        }

        viewModelScope.launch {
            _manualScanningIds.update { it + expense.id }
            _manualScanningErrors.update { it - expense.id }

            try {
                val bitmap = BitmapFactory.decodeFile(path)
                if (bitmap == null) {
                    throw Exception("Failed to decode receipt image file.")
                }

                val result = scanReceiptUseCase(bitmap)
                val updatedExpense = expense.copy(
                    shopName = result.merchant ?: "Unknown Shop",
                    amount = result.amount ?: 0.0,
                    date = result.date ?: expense.date,
                    isPendingAnalysis = false
                )
                saveExpenseUseCase(updatedExpense)
            } catch (e: Exception) {
                e.printStackTrace()
                _manualScanningErrors.update { it + (expense.id to (e.localizedMessage ?: "Scanning failed")) }
            } finally {
                _manualScanningIds.update { it - expense.id }
            }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            // Delete expenses from repository (since there is no clearAll, we can delete them one by one or fetch and delete)
            val db = ExpenseDatabase.getDatabase(getApplication())
            db.clearAllTables()
        }
    }

    private fun getStartOfMonthTimestamp(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
