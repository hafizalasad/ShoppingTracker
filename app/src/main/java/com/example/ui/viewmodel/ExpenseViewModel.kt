package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.Content
import com.example.api.GeminiApiClient
import com.example.api.GenerateContentRequest
import com.example.api.GenerationConfig
import com.example.api.InlineData
import com.example.api.Part
import com.example.data.Expense
import com.example.data.ExpenseDatabase
import com.example.data.ExpenseRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Calendar
import java.util.Locale

sealed class Screen {
    object Main : Screen()
    data class ShopDetails(val shopName: String) : Screen()
    data class ZoomImage(val imagePath: String, val returnScreen: Screen) : Screen()
    object ScanReceipt : Screen()
}

data class ShopExpenseSummary(
    val shopName: String,
    val totalAmount: Double,
    val expenseCount: Int
)

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ExpenseRepository = ExpenseRepository(ExpenseDatabase.getDatabase(application).expenseDao())
    private val sharedPrefs = application.getSharedPreferences("expense_tracker_prefs", Context.MODE_PRIVATE)

    // Supported Currencies
    val supportedCurrencies = listOf("USD", "BDT", "EUR", "GBP", "JPY", "INR", "CAD", "AUD", "SGD")

    private val _selectedCurrency = MutableStateFlow(sharedPrefs.getString("preferred_currency", "USD") ?: "USD")
    val selectedCurrency: StateFlow<String> = _selectedCurrency.asStateFlow()

    fun setCurrency(currencyCode: String) {
        _selectedCurrency.value = currencyCode
        sharedPrefs.edit().putString("preferred_currency", currencyCode).apply()
    }

    fun getCurrencySymbol(): String {
        return when (_selectedCurrency.value) {
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

    fun formatAmount(amount: Double): String {
        return String.format(java.util.Locale.getDefault(), "%s%.2f", getCurrencySymbol(), amount)
    }

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    // AI Analysis states
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _analysisError = MutableStateFlow<String?>(null)
    val analysisError: StateFlow<String?> = _analysisError.asStateFlow()

    private fun loadScanDraft(): ScanUiState {
        val prefs = getApplication<Application>().getSharedPreferences("shop_expense_prefs", Context.MODE_PRIVATE)
        val imagePath = prefs.getString("draft_image_path", null)
        val shopName = prefs.getString("draft_shop_name", "") ?: ""
        val amount = prefs.getString("draft_amount", "") ?: ""
        val selectedDate = prefs.getLong("draft_selected_date", System.currentTimeMillis())
        val isOffline = prefs.getBoolean("draft_is_offline", false)
        return ScanUiState(
            imagePath = imagePath,
            shopName = shopName,
            amount = amount,
            selectedDate = selectedDate,
            isOffline = isOffline
        )
    }

    // Unified MVI State and Intent processor for Scan Screen
    private val _scanUiState = MutableStateFlow(loadScanDraft())
    val scanUiState: StateFlow<ScanUiState> = combine(
        _scanUiState,
        _isAnalyzing,
        _analysisError
    ) { state, isAnalyzing, error ->
        state.copy(
            isAnalyzing = isAnalyzing,
            analysisError = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = loadScanDraft()
    )

    init {
        // Collect scan state and save to draft SharedPreferences reactively
        viewModelScope.launch {
            _scanUiState.collect { state ->
                val prefs = application.getSharedPreferences("shop_expense_prefs", Context.MODE_PRIVATE)
                prefs.edit().apply {
                    putString("draft_image_path", state.imagePath)
                    putString("draft_shop_name", state.shopName)
                    putString("draft_amount", state.amount)
                    putLong("draft_selected_date", state.selectedDate)
                    putBoolean("draft_is_offline", state.isOffline)
                    apply()
                }
            }
        }

        // Register default network callback for auto-syncing when online
        try {
            val connectivityManager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            connectivityManager?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    syncPendingExpenses()
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Initial sync check on launch
        syncPendingExpenses()
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
        // Start of start day to end of end day
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
        repository.getExpensesInDateRange(start, end)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Aggregate summaries of total spent per shop in date range
    val shopSummaries: StateFlow<List<ShopExpenseSummary>> = combine(
        expensesInRange
    ) { list ->
        list[0].groupBy { it.shopName.trim() }
            .map { (shopName, expenses) ->
                ShopExpenseSummary(
                    shopName = shopName,
                    totalAmount = expenses.sumOf { it.amount },
                    expenseCount = expenses.size
                )
            }.sortedByDescending { it.totalAmount }
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
            currencySymbol = getCurrencySymbol(),
            totalSpent = summaries.sumOf { it.totalAmount }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState()
    )

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
                val isOnline = isNetworkAvailable()
                _scanUiState.update { 
                    it.copy(
                        imagePath = intent.path,
                        isOffline = !isOnline,
                        shopName = if (!isOnline) "Offline Receipt (Pending)" else "",
                        amount = if (!isOnline) "0.00" else ""
                    ) 
                }
                if (isOnline) {
                    analyzeReceipt(intent.bitmap) { shop, amt, parsedDateLong ->
                        _scanUiState.update {
                            it.copy(
                                shopName = shop ?: "Unknown Shop",
                                amount = if (amt != null) String.format(Locale.US, "%.2f", amt) else "0.00",
                                selectedDate = parsedDateLong ?: it.selectedDate
                            )
                        }
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
            is ScanUiIntent.ToggleDatePicker -> {
                _scanUiState.update { it.copy(showDatePicker = intent.show) }
            }
            is ScanUiIntent.SaveExpense -> {
                val state = _scanUiState.value
                val amountVal = state.amount.toDoubleOrNull() ?: 0.0
                saveExpense(
                    shopName = state.shopName.ifBlank { if (state.isOffline) "Offline Receipt (Pending)" else "Unknown Shop" },
                    amount = amountVal,
                    date = state.selectedDate,
                    imagePath = state.imagePath,
                    isPendingAnalysis = state.isOffline
                )
                // Clear state upon saving
                _scanUiState.value = ScanUiState()
            }
            is ScanUiIntent.ResetScan -> {
                _scanUiState.value = ScanUiState()
            }
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
        }
    }

    fun analyzeReceipt(bitmap: Bitmap, onResult: (String?, Double?, Long?) -> Unit) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _analysisError.value = null
            try {
                val base64Image = bitmap.toBase64()
                val prompt = "Extract the shop name, total spent amount, and receipt issue date from this receipt image. Your response must be a JSON object with keys 'shopName' (String, name of the shop), 'amount' (Number, total spent amount. If not found, use 0.0), and 'date' (String, in format YYYY-MM-DD representing the receipt date issued, or null if not found)."
                
                val request = GenerateContentRequest(
                    contents = listOf(
                        Content(
                            parts = listOf(
                                Part(text = prompt),
                                Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image))
                            )
                        )
                    ),
                    generationConfig = GenerationConfig(
                        responseMimeType = "application/json",
                        temperature = 0.1
                    )
                )
                
                val apiKey = com.example.BuildConfig.GEMINI_API_KEY
                if (apiKey == "MY_GEMINI_API_KEY" || apiKey.isBlank()) {
                    throw Exception("API Key is not configured. Please add GEMINI_API_KEY to your Secrets panel in AI Studio.")
                }
                
                val response = GeminiApiClient.service.generateContent(apiKey, request)
                val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (jsonText != null) {
                    val parsed = GeminiApiClient.parseAnalysisResult(jsonText)
                    if (parsed != null) {
                        val parsedDateMillis = parseDateToMillis(parsed.date)
                        onResult(parsed.shopName, parsed.amount, parsedDateMillis)
                    } else {
                        onResult(null, null, null)
                    }
                } else {
                    onResult(null, null, null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _analysisError.value = e.message ?: "An unknown error occurred"
                onResult(null, null, null)
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    // Save expense to database
    fun saveExpense(shopName: String, amount: Double, date: Long, imagePath: String?, isPendingAnalysis: Boolean = false) {
        viewModelScope.launch {
            repository.insertExpense(
                Expense(
                    shopName = shopName,
                    amount = amount,
                    date = date,
                    imagePath = imagePath,
                    isPendingAnalysis = isPendingAnalysis
                )
            )
            _currentScreen.value = Screen.Main
            if (isPendingAnalysis) {
                // Try initial sync immediately if user got connected right after saving
                syncPendingExpenses()
            }
        }
    }

    fun isNetworkAvailable(): Boolean {
        return try {
            val connectivityManager = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNetwork = connectivityManager?.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            e.printStackTrace()
            // Default to true when exception occurs so that we can still attempt background work if network exists but capability query fails
            true
        }
    }

    fun syncPendingExpenses() {
        if (_isSyncing.value) return
        if (!isNetworkAvailable()) return

        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val pending = repository.getPendingExpenses()
                for (expense in pending) {
                    val path = expense.imagePath
                    if (path != null) {
                        val file = File(path)
                        if (file.exists()) {
                            val bitmap = BitmapFactory.decodeFile(path)
                            if (bitmap != null) {
                                val result = analyzeReceiptBackground(bitmap)
                                if (result != null) {
                                    val parsedDate = parseDateToMillis(result.date) ?: expense.date
                                    repository.insertExpense(
                                        expense.copy(
                                            shopName = result.shopName,
                                            amount = result.amount,
                                            date = parsedDate,
                                            isPendingAnalysis = false
                                        )
                                    )
                                }
                            } else {
                                repository.insertExpense(expense.copy(isPendingAnalysis = false))
                            }
                        } else {
                            repository.insertExpense(expense.copy(isPendingAnalysis = false))
                        }
                    } else {
                        repository.insertExpense(expense.copy(isPendingAnalysis = false))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isSyncing.value = false
            }
        }
    }

    private suspend fun analyzeReceiptBackground(bitmap: Bitmap): com.example.api.ReceiptAnalysisResult? {
        return try {
            val base64Image = bitmap.toBase64()
            val prompt = "Extract the shop name, total spent amount, and receipt issue date from this receipt image. Your response must be a JSON object with keys 'shopName' (String, name of the shop), 'amount' (Number, total spent amount. If not found, use 0.0), and 'date' (String, in format YYYY-MM-DD representing the receipt date issued, or null if not found)."
            
            val request = GenerateContentRequest(
                contents = listOf(
                    Content(
                        parts = listOf(
                            Part(text = prompt),
                            Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image))
                        )
                    )
                ),
                generationConfig = GenerationConfig(
                    responseMimeType = "application/json",
                    temperature = 0.1
                )
            )
            val apiKey = com.example.BuildConfig.GEMINI_API_KEY
            if (apiKey == "MY_GEMINI_API_KEY" || apiKey.isBlank()) {
                return null
            }
            val response = GeminiApiClient.service.generateContent(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                GeminiApiClient.parseAnalysisResult(jsonText)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deleteExpense(id: Long) {
        viewModelScope.launch {
            repository.deleteExpenseById(id)
        }
    }

    fun updateExpense(expense: Expense) {
        viewModelScope.launch {
            repository.insertExpense(expense)
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

    private fun parseDateToMillis(dateStr: String?): Long? {
        if (dateStr.isNullOrBlank()) return null
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            sdf.parse(dateStr.trim())?.time
        } catch (e: Exception) {
            null
        }
    }

    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        this.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
        val byteArray = outputStream.toByteArray()
        return android.util.Base64.encodeToString(byteArray, android.util.Base64.NO_WRAP)
    }
}
