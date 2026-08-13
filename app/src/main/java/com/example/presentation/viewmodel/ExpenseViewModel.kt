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

import com.example.data.backup.BackupResult
import com.example.data.backup.DriveBackupManager
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import org.json.JSONArray
import org.json.JSONObject

data class SavedDateFilter(
    val id: String,
    val name: String,
    val startDate: Long,
    val endDate: Long,
    val isCustom: Boolean = true,
    val startDay: Int? = null,
    val endDay: Int? = null,
    val isStartFromPreviousMonth: Boolean = true
)

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

    // Saved Date Filters State & Management
    private val _savedDateFilters = MutableStateFlow<List<SavedDateFilter>>(emptyList())
    val savedDateFilters: StateFlow<List<SavedDateFilter>> = _savedDateFilters.asStateFlow()

    init {
        loadSavedDateFilters()
    }

    fun calculateMonthlyCycleDates(
        startDay: Int,
        endDay: Int,
        isStartFromPreviousMonth: Boolean = true,
        refTimeMs: Long = System.currentTimeMillis()
    ): Pair<Long, Long> {
        val startCal = Calendar.getInstance().apply {
            timeInMillis = refTimeMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val endCal = Calendar.getInstance().apply {
            timeInMillis = refTimeMs
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }

        if (isStartFromPreviousMonth) {
            // From day is in previous month, To day is in current month
            startCal.add(Calendar.MONTH, -1)
            val maxDayStart = startCal.getActualMaximum(Calendar.DAY_OF_MONTH)
            startCal.set(Calendar.DAY_OF_MONTH, startDay.coerceIn(1, maxDayStart))

            val maxDayEnd = endCal.getActualMaximum(Calendar.DAY_OF_MONTH)
            endCal.set(Calendar.DAY_OF_MONTH, endDay.coerceIn(1, maxDayEnd))
        } else {
            // From day is in current month, To day is in future (next) month
            val maxDayStart = startCal.getActualMaximum(Calendar.DAY_OF_MONTH)
            startCal.set(Calendar.DAY_OF_MONTH, startDay.coerceIn(1, maxDayStart))

            endCal.add(Calendar.MONTH, 1)
            val maxDayEnd = endCal.getActualMaximum(Calendar.DAY_OF_MONTH)
            endCal.set(Calendar.DAY_OF_MONTH, endDay.coerceIn(1, maxDayEnd))
        }

        return Pair(startCal.timeInMillis, endCal.timeInMillis)
    }

    private fun loadSavedDateFilters() {
        val json = sharedPrefs.getString("saved_date_filters_v1", null)
        val list = mutableListOf<SavedDateFilter>()
        if (json != null) {
            try {
                val array = JSONArray(json)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val startDay = if (obj.has("startDay")) obj.getInt("startDay") else null
                    val endDay = if (obj.has("endDay")) obj.getInt("endDay") else null
                    val isStartFromPrev = if (obj.has("isStartFromPreviousMonth")) obj.getBoolean("isStartFromPreviousMonth") else true

                    val (sDate, eDate) = if (startDay != null && endDay != null) {
                        calculateMonthlyCycleDates(startDay, endDay, isStartFromPrev)
                    } else {
                        Pair(obj.getLong("startDate"), obj.getLong("endDate"))
                    }

                    list.add(
                        SavedDateFilter(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            startDate = sDate,
                            endDate = eDate,
                            isCustom = true,
                            startDay = startDay,
                            endDay = endDay,
                            isStartFromPreviousMonth = isStartFromPrev
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        _savedDateFilters.value = list.sortedByDescending { it.id.toLongOrNull() ?: 0L }
    }

    fun saveCustomDateFilter(name: String, startDate: Long, endDate: Long) {
        val filterName = name.ifBlank { "Custom Filter" }
        val newFilter = SavedDateFilter(
            id = System.currentTimeMillis().toString(),
            name = filterName,
            startDate = startDate,
            endDate = endDate,
            isCustom = true
        )
        val current = _savedDateFilters.value.toMutableList()
        current.add(0, newFilter)
        _savedDateFilters.value = current
        persistSavedDateFilters(current)
    }

    fun saveMonthlyCycleFilter(name: String, startDay: Int, endDay: Int, isStartFromPreviousMonth: Boolean = true) {
        val (sDate, eDate) = calculateMonthlyCycleDates(startDay, endDay, isStartFromPreviousMonth)
        val defaultName = if (isStartFromPreviousMonth) "Monthly Cycle ($startDay-$endDay)" else "Monthly Cycle ($startDay-$endDay Next)"
        val filterName = name.ifBlank { defaultName }
        val newFilter = SavedDateFilter(
            id = System.currentTimeMillis().toString(),
            name = filterName,
            startDate = sDate,
            endDate = eDate,
            isCustom = true,
            startDay = startDay,
            endDay = endDay,
            isStartFromPreviousMonth = isStartFromPreviousMonth
        )
        val current = _savedDateFilters.value.toMutableList()
        current.add(0, newFilter)
        _savedDateFilters.value = current
        persistSavedDateFilters(current)
    }

    fun deleteSavedDateFilter(id: String) {
        val updated = _savedDateFilters.value.filter { it.id != id }
        _savedDateFilters.value = updated
        persistSavedDateFilters(updated)
    }

    private fun persistSavedDateFilters(list: List<SavedDateFilter>) {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("name", item.name)
            obj.put("startDate", item.startDate)
            obj.put("endDate", item.endDate)
            item.startDay?.let { obj.put("startDay", it) }
            item.endDay?.let { obj.put("endDay", it) }
            obj.put("isStartFromPreviousMonth", item.isStartFromPreviousMonth)
            array.put(obj)
        }
        sharedPrefs.edit().putString("saved_date_filters_v1", array.toString()).apply()
    }

    // Google Drive Sync & Backup State Management
    val driveBackupManager by lazy { DriveBackupManager(getApplication()) }

    private val _backupStatusMessage = MutableStateFlow<String?>(null)
    val backupStatusMessage: StateFlow<String?> = _backupStatusMessage.asStateFlow()

    private val _isBackupInProgress = MutableStateFlow(false)
    val isBackupInProgress: StateFlow<Boolean> = _isBackupInProgress.asStateFlow()

    private val _lastBackupTime = MutableStateFlow<Long?>(
        sharedPrefs.getLong("last_backup_timestamp", 0L).takeIf { it > 0L }
    )
    val lastBackupTime: StateFlow<Long?> = _lastBackupTime.asStateFlow()

    fun clearBackupStatus() {
        _backupStatusMessage.value = null
    }

    fun backupToDrive(account: GoogleSignInAccount) {
        viewModelScope.launch {
            _isBackupInProgress.value = true
            _backupStatusMessage.value = "Uploading expense backup to your Google Drive..."
            val result = driveBackupManager.backupToDrive(account)
            _isBackupInProgress.value = false
            when (result) {
                is BackupResult.Success -> {
                    val now = System.currentTimeMillis()
                    _lastBackupTime.value = now
                    sharedPrefs.edit().putLong("last_backup_timestamp", now).apply()
                    _backupStatusMessage.value = result.message
                }
                is BackupResult.Error -> {
                    _backupStatusMessage.value = result.message
                }
            }
        }
    }

    fun restoreFromDrive(account: GoogleSignInAccount) {
        viewModelScope.launch {
            _isBackupInProgress.value = true
            _backupStatusMessage.value = "Restoring expenses from your Google Drive..."
            val result = driveBackupManager.restoreFromDrive(account)
            _isBackupInProgress.value = false
            when (result) {
                is BackupResult.Success -> {
                    _backupStatusMessage.value = result.message
                }
                is BackupResult.Error -> {
                    _backupStatusMessage.value = result.message
                }
            }
        }
    }

    fun exportLocalBackup(onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val json = driveBackupManager.exportToJsonString()
                onSuccess(json)
                _backupStatusMessage.value = "Local JSON backup copied/ready for export."
            } catch (e: Exception) {
                _backupStatusMessage.value = "Failed to export backup: ${e.message}"
            }
        }
    }

    fun importLocalBackup(jsonString: String) {
        viewModelScope.launch {
            _isBackupInProgress.value = true
            val result = driveBackupManager.importFromJsonString(jsonString)
            _isBackupInProgress.value = false
            when (result) {
                is BackupResult.Success -> _backupStatusMessage.value = result.message
                is BackupResult.Error -> _backupStatusMessage.value = result.message
            }
        }
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

    // Search query state for filtering shops
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private val _dateRange = combine(_startDate, _endDate) { start, end -> start to end }

    // Unified MVI State Flow for Main Screen
    val mainUiState: StateFlow<MainUiState> = combine(
        _dateRange,
        shopSummaries,
        expensesInRange,
        _selectedCurrency,
        _searchQuery
    ) { (start, end), summaries, expenses, currency, query ->
        val filteredSummaries = if (query.isBlank()) {
            summaries
        } else {
            summaries.filter { it.shopName.contains(query, ignoreCase = true) }
        }

        val filteredExpenses = if (query.isBlank()) {
            expenses
        } else {
            expenses.filter { it.shopName.contains(query, ignoreCase = true) }
        }

        MainUiState(
            shopSummaries = filteredSummaries,
            expensesInRange = filteredExpenses,
            startDate = start,
            endDate = end,
            selectedCurrency = currency,
            currencySymbol = getCurrencySymbol(currency),
            totalSpent = filteredSummaries.sumOf { it.totalAmount },
            searchQuery = query
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

    fun openGeneralScan(isManual: Boolean = false) {
        _scanUiState.value = ScanUiState(
            shopName = "",
            isShopNameLocked = false,
            selectedDate = System.currentTimeMillis(),
            isManualEntry = isManual
        )
        navigateTo(Screen.ScanReceipt)
    }

    fun startAddExpenseForShop(shopName: String) {
        _scanUiState.value = ScanUiState(
            shopName = shopName,
            isShopNameLocked = true,
            selectedDate = System.currentTimeMillis(),
            isManualEntry = true
        )
        navigateTo(Screen.ScanReceipt)
    }

    fun onMainIntent(intent: MainUiIntent) {
        when (intent) {
            is MainUiIntent.SetDateRange -> setDateRange(intent.start, intent.end)
            is MainUiIntent.SetCurrency -> setCurrency(intent.currencyCode)
            is MainUiIntent.SetSearchQuery -> setSearchQuery(intent.query)
            is MainUiIntent.NavigateToScan -> {
                val currentIsManual = _scanUiState.value.isManualEntry
                openGeneralScan(isManual = currentIsManual)
            }
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
                        _scanUiState.update { current ->
                            current.copy(
                                shopName = if (current.isShopNameLocked) current.shopName else (result.merchant ?: "Unknown Shop"),
                                amount = if (result.amount != null) String.format(Locale.US, "%.2f", result.amount) else "0.00",
                                selectedDate = result.date ?: current.selectedDate,
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
                _scanUiState.update { 
                    if (it.isShopNameLocked) it else it.copy(shopName = intent.name) 
                }
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
                _scanUiState.update { 
                    it.copy(
                        isManualEntry = intent.isManual, 
                        imagePath = null,
                        selectedDate = System.currentTimeMillis()
                    ) 
                }
            }
            is ScanUiIntent.ToggleDatePicker -> {
                _scanUiState.update { it.copy(showDatePicker = intent.show) }
            }
            is ScanUiIntent.SaveExpense -> {
                val state = _scanUiState.value
                val amountVal = state.amount.toDoubleOrNull() ?: 0.0
                val targetShopName = if (state.isShopNameLocked) state.shopName else null
                saveExpense(
                    shopName = state.shopName.ifBlank { "Offline Receipt" },
                    amount = amountVal,
                    date = state.selectedDate,
                    imagePath = state.imagePath,
                    isPendingAnalysis = false,
                    note = state.note
                )
                _scanUiState.value = ScanUiState()
                if (targetShopName != null) {
                    navigateTo(Screen.ShopDetails(targetShopName))
                } else {
                    navigateTo(Screen.Main)
                }
            }
            is ScanUiIntent.ResetScan -> {
                val state = _scanUiState.value
                if (state.isShopNameLocked) {
                    _scanUiState.value = ScanUiState(
                        shopName = state.shopName,
                        isShopNameLocked = true,
                        isManualEntry = true,
                        selectedDate = System.currentTimeMillis()
                    )
                } else {
                    _scanUiState.value = ScanUiState(
                        isManualEntry = state.isManualEntry,
                        selectedDate = System.currentTimeMillis()
                    )
                }
            }
        }
    }

    fun handleBackNavigationFromScan() {
        val state = _scanUiState.value
        val targetShopName = if (state.isShopNameLocked) state.shopName else null
        val hasData = state.imagePath != null ||
                (state.shopName.isNotBlank() && !state.isShopNameLocked) ||
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
        }
        _scanUiState.value = ScanUiState()
        if (targetShopName != null) {
            navigateTo(Screen.ShopDetails(targetShopName))
        } else {
            navigateTo(Screen.Main)
        }
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
