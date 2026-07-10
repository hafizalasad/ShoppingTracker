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
        return com.example.util.CurrencyUtils.formatBangladeshiStyle(getCurrencySymbol(), amount)
    }

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    // AI Analysis states
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _analysisError = MutableStateFlow<String?>(null)
    val analysisError: StateFlow<String?> = _analysisError.asStateFlow()

    // AI Settings State
    private val _aiProvider = MutableStateFlow("gemini")
    val aiProvider: StateFlow<String> = _aiProvider.asStateFlow()

    private val _geminiApiKey = MutableStateFlow("")
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    private val _deepseekApiKey = MutableStateFlow("")
    val deepseekApiKey: StateFlow<String> = _deepseekApiKey.asStateFlow()

    private val _openaiApiKey = MutableStateFlow("")
    val openaiApiKey: StateFlow<String> = _openaiApiKey.asStateFlow()

    private val _geminiModel = MutableStateFlow("gemini-3.5-flash")
    val geminiModel: StateFlow<String> = _geminiModel.asStateFlow()

    private val _deepseekModel = MutableStateFlow("deepseek-chat")
    val deepseekModel: StateFlow<String> = _deepseekModel.asStateFlow()

    private val _openaiModel = MutableStateFlow("google/gemini-2.5-flash:free")
    val openaiModel: StateFlow<String> = _openaiModel.asStateFlow()

    private val _deepseekBaseUrl = MutableStateFlow("https://api.deepseek.com/v1/")
    val deepseekBaseUrl: StateFlow<String> = _deepseekBaseUrl.asStateFlow()

    private val _openaiBaseUrl = MutableStateFlow("https://openrouter.ai/api/v1/")
    val openaiBaseUrl: StateFlow<String> = _openaiBaseUrl.asStateFlow()

    // Legacy or active state flows (keep for backwards compatibility)
    private val _aiModel = MutableStateFlow("gemini-3.5-flash")
    val aiModel: StateFlow<String> = _aiModel.asStateFlow()

    private val _aiApiKey = MutableStateFlow("")
    val aiApiKey: StateFlow<String> = _aiApiKey.asStateFlow()

    private val _aiBaseUrl = MutableStateFlow("https://openrouter.ai/api/v1/")
    val aiBaseUrl: StateFlow<String> = _aiBaseUrl.asStateFlow()

    // Manual scanning states for already saved receipts
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

    private fun getSecurePrefs(context: Context): android.content.SharedPreferences {
        return try {
            val masterKeyAlias = androidx.security.crypto.MasterKeys.getOrCreate(androidx.security.crypto.MasterKeys.AES256_GCM_SPEC)
            androidx.security.crypto.EncryptedSharedPreferences.create(
                "secure_ai_settings",
                masterKeyAlias,
                context,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            e.printStackTrace()
            // If keystore fails for any reason, fall back to standard SharedPreferences for robustness
            context.getSharedPreferences("secure_ai_settings_fallback", Context.MODE_PRIVATE)
        }
    }

    init {
        val legacyPrefs = application.getSharedPreferences("shop_expense_prefs", Context.MODE_PRIVATE)
        val securePrefs = getSecurePrefs(application)

        // Migrating old plain-text keys to EncryptedSharedPreferences if present
        if (!securePrefs.contains("ai_provider") && legacyPrefs.contains("ai_provider")) {
            securePrefs.edit().apply {
                putString("ai_provider", legacyPrefs.getString("ai_provider", "gemini"))
                putString("gemini_api_key", legacyPrefs.getString("gemini_api_key", ""))
                putString("deepseek_api_key", legacyPrefs.getString("deepseek_api_key", ""))
                putString("openai_api_key", legacyPrefs.getString("openai_api_key", ""))
                val legacyGemini = legacyPrefs.getString("gemini_model", "gemini-3.5-flash")
                val safeGemini = if (legacyGemini == "gemini-1.5-flash") "gemini-2.5-flash" else legacyGemini
                putString("gemini_model", safeGemini)
                putString("deepseek_model", legacyPrefs.getString("deepseek_model", "deepseek-chat"))
                val legacyOpenai = legacyPrefs.getString("openai_model", "google/gemini-2.5-flash")
                val safeOpenai = if (legacyOpenai == "google/gemini-2.5-flash:free") "google/gemini-2.5-flash" else legacyOpenai
                putString("openai_model", safeOpenai)
                putString("deepseek_base_url", legacyPrefs.getString("deepseek_base_url", "https://api.deepseek.com/v1/"))
                putString("openai_base_url", legacyPrefs.getString("openai_base_url", "https://openrouter.ai/api/v1/"))
                apply()
            }
            // Securely wipe sensitive keys from insecure plain text SharedPreferences
            legacyPrefs.edit().apply {
                remove("ai_provider")
                remove("gemini_api_key")
                remove("deepseek_api_key")
                remove("openai_api_key")
                remove("gemini_model")
                remove("deepseek_model")
                remove("openai_model")
                remove("deepseek_base_url")
                remove("openai_base_url")
                remove("ai_api_key")
                apply()
            }
        }

        _aiProvider.value = securePrefs.getString("ai_provider", "gemini") ?: "gemini"

        val geminiKey = securePrefs.getString("gemini_api_key", "") ?: ""
        val deepseekKey = securePrefs.getString("deepseek_api_key", "") ?: ""
        val openaiKey = securePrefs.getString("openai_api_key", "") ?: ""

        // Migrate legacy api key if we had one
        val legacyKey = legacyPrefs.getString("ai_api_key", "") ?: ""
        val initialProvider = _aiProvider.value

        _geminiApiKey.value = if (geminiKey.isBlank() && initialProvider == "gemini") legacyKey else geminiKey
        _deepseekApiKey.value = if (deepseekKey.isBlank() && initialProvider == "deepseek") legacyKey else deepseekKey
        _openaiApiKey.value = if (openaiKey.isBlank() && initialProvider == "openai") legacyKey else openaiKey

        var currentGeminiModel = securePrefs.getString("gemini_model", "gemini-3.5-flash") ?: "gemini-3.5-flash"
        if (currentGeminiModel == "gemini-1.5-flash") {
            currentGeminiModel = "gemini-2.5-flash"
            securePrefs.edit().putString("gemini_model", "gemini-2.5-flash").apply()
        }
        _geminiModel.value = currentGeminiModel

        _deepseekModel.value = securePrefs.getString("deepseek_model", "deepseek-chat") ?: "deepseek-chat"

        var currentOpenaiModel = securePrefs.getString("openai_model", "google/gemini-2.5-flash") ?: "google/gemini-2.5-flash"
        if (currentOpenaiModel == "google/gemini-2.5-flash:free") {
            currentOpenaiModel = "google/gemini-2.5-flash"
            securePrefs.edit().putString("openai_model", "google/gemini-2.5-flash").apply()
        }
        _openaiModel.value = currentOpenaiModel

        _deepseekBaseUrl.value = securePrefs.getString("deepseek_base_url", "https://api.deepseek.com/v1/") ?: "https://api.deepseek.com/v1/"
        _openaiBaseUrl.value = securePrefs.getString("openai_base_url", "https://openrouter.ai/api/v1/") ?: "https://openrouter.ai/api/v1/"

        _aiModel.value = when (initialProvider) {
            "gemini" -> _geminiModel.value
            "deepseek" -> _deepseekModel.value
            else -> _openaiModel.value
        }
        _aiApiKey.value = when (initialProvider) {
            "gemini" -> _geminiApiKey.value
            "deepseek" -> _deepseekApiKey.value
            else -> _openaiApiKey.value
        }
        _aiBaseUrl.value = when (initialProvider) {
            "gemini" -> ""
            "deepseek" -> _deepseekBaseUrl.value
            else -> _openaiBaseUrl.value
        }

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
            is ShopDetailsUiIntent.TriggerScan -> {
                triggerManualReceiptScan(intent.expense)
            }
        }
    }

    fun updateAiSettings(provider: String, model: String, apiKey: String, baseUrl: String) {
        // Fallback overload for legacy call patterns
        updateAiSettings(
            provider = provider,
            geminiKey = if (provider == "gemini") apiKey else _geminiApiKey.value,
            deepseekKey = if (provider == "deepseek") apiKey else _deepseekApiKey.value,
            openaiKey = if (provider == "openai") apiKey else _openaiApiKey.value,
            geminiModel = if (provider == "gemini") model else _geminiModel.value,
            deepseekModel = if (provider == "deepseek") model else _deepseekModel.value,
            openaiModel = if (provider != "gemini" && provider != "deepseek") model else _openaiModel.value,
            deepseekBaseUrl = if (provider == "deepseek") baseUrl else _deepseekBaseUrl.value,
            openaiBaseUrl = if (provider != "gemini" && provider != "deepseek") baseUrl else _openaiBaseUrl.value
        )
    }

    fun updateAiSettings(
        provider: String,
        geminiKey: String,
        deepseekKey: String,
        openaiKey: String,
        geminiModel: String,
        deepseekModel: String,
        openaiModel: String,
        deepseekBaseUrl: String,
        openaiBaseUrl: String
    ) {
        val securePrefs = getSecurePrefs(getApplication())
        securePrefs.edit().apply {
            putString("ai_provider", provider)
            putString("gemini_api_key", geminiKey)
            putString("deepseek_api_key", deepseekKey)
            putString("openai_api_key", openaiKey)
            putString("gemini_model", geminiModel)
            putString("deepseek_model", deepseekModel)
            putString("openai_model", openaiModel)
            putString("deepseek_base_url", deepseekBaseUrl)
            putString("openai_base_url", openaiBaseUrl)
            apply()
        }
        _aiProvider.value = provider
        _geminiApiKey.value = geminiKey
        _deepseekApiKey.value = deepseekKey
        _openaiApiKey.value = openaiKey
        _geminiModel.value = geminiModel
        _deepseekModel.value = deepseekModel
        _openaiModel.value = openaiModel
        _deepseekBaseUrl.value = deepseekBaseUrl
        _openaiBaseUrl.value = openaiBaseUrl

        _aiModel.value = when (provider) {
            "gemini" -> geminiModel
            "deepseek" -> deepseekModel
            else -> openaiModel
        }
        _aiApiKey.value = when (provider) {
            "gemini" -> geminiKey
            "deepseek" -> deepseekKey
            else -> openaiKey
        }
        _aiBaseUrl.value = when (provider) {
            "gemini" -> ""
            "deepseek" -> deepseekBaseUrl
            else -> openaiBaseUrl
        }
    }

    private suspend fun tryProviderScan(
        provider: String,
        base64Image: String,
        prompt: String
    ): com.example.api.ReceiptAnalysisResult {
        return kotlinx.coroutines.withTimeout(15000L) {
            if (provider == "openai" || provider == "deepseek") {
                val customBaseUrl = if (provider == "deepseek") _deepseekBaseUrl.value else _openaiBaseUrl.value
                val customApiKey = if (provider == "deepseek") _deepseekApiKey.value else _openaiApiKey.value
                val model = if (provider == "deepseek") _deepseekModel.value else _openaiModel.value

                val finalBaseUrl = if (provider == "deepseek" && customBaseUrl.isBlank()) "https://api.deepseek.com/v1/" else customBaseUrl
                if (finalBaseUrl.isBlank()) {
                    throw Exception("API Base URL is empty.")
                }
                if (customApiKey.isBlank()) {
                    throw Exception("API Key is empty.")
                }

                val formattedUrl = when {
                    finalBaseUrl.endsWith("/chat/completions") -> finalBaseUrl
                    finalBaseUrl.endsWith("/") -> "${finalBaseUrl}chat/completions"
                    else -> "$finalBaseUrl/chat/completions"
                }

                val finalModel = if (provider == "deepseek" && model.isBlank()) "deepseek-chat" else model.ifBlank { "google/gemini-2.5-flash" }

                val request = com.example.api.OpenAiChatRequest(
                    model = finalModel,
                    messages = listOf(
                        com.example.api.OpenAiMessage(
                            role = "user",
                            content = listOf(
                                com.example.api.OpenAiContentPart(type = "text", text = prompt),
                                com.example.api.OpenAiContentPart(
                                    type = "image_url",
                                    image_url = com.example.api.OpenAiImageUrl(
                                        url = "data:image/jpeg;base64,$base64Image"
                                    )
                                )
                            )
                        )
                    ),
                    response_format = com.example.api.OpenAiResponseFormat(type = "json_object"),
                    temperature = 0.1
                )

                val authHeader = "Bearer $customApiKey"
                val response = com.example.api.GeminiApiClient.openAiService.chatCompletions(formattedUrl, authHeader, request)
                val jsonText = response.choices?.firstOrNull()?.message?.content
                if (jsonText != null) {
                    com.example.api.GeminiApiClient.parseAnalysisResult(jsonText)
                        ?: throw Exception("Failed to parse API response schema. Response text: $jsonText")
                } else {
                    throw Exception("API returned an empty response.")
                }
            } else {
                // Gemini provider
                val customApiKey = _geminiApiKey.value
                val finalApiKey = customApiKey.ifBlank { com.example.BuildConfig.GEMINI_API_KEY }
                if (finalApiKey == "MY_GEMINI_API_KEY" || finalApiKey.isBlank()) {
                    throw Exception("Gemini API Key is not configured.")
                }

                val model = _geminiModel.value
                val finalModel = if (model.isNotBlank() && model != "gemini-3.5-flash") model else "gemini-3.5-flash"
                val dynamicUrl = "https://generativelanguage.googleapis.com/v1beta/models/$finalModel:generateContent?key=$finalApiKey"

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

                val response = GeminiApiClient.service.generateContentDynamic(dynamicUrl, request)
                val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (jsonText != null) {
                    GeminiApiClient.parseAnalysisResult(jsonText)
                        ?: throw Exception("Failed to parse Gemini AI API response schema.")
                } else {
                    throw Exception("Gemini AI API returned an empty response.")
                }
            }
        }
    }

    private suspend fun analyzeReceiptUnified(bitmap: Bitmap): com.example.api.ReceiptAnalysisResult {
        val base64Image = bitmap.toBase64()
        val prompt = "Extract the shop name, total spent amount, and receipt issue date from this receipt image. Your response must be a JSON object with keys 'shopName' (String, name of the shop), 'amount' (Number, total spent amount. If not found, use 0.0), and 'date' (String, in format YYYY-MM-DD representing the receipt date issued, or null if not found)."

        // Try the preferred provider first
        val preferredProvider = _aiProvider.value
        val providersToTry = mutableListOf(preferredProvider)

        // Add backup vision-capable providers sequentially (Gemini and OpenAI/OpenRouter both support vision models)
        listOf("gemini", "openai").forEach { p ->
            if (!providersToTry.contains(p)) {
                providersToTry.add(p)
            }
        }

        // Only add deepseek as a fallback if it is explicitly configured with a custom vision model, since official DeepSeek is text-only
        val deepseekModelStr = _deepseekModel.value
        val isDeepseekVisionCapable = deepseekModelStr.isNotBlank() && 
                deepseekModelStr != "deepseek-chat" && 
                deepseekModelStr != "deepseek-reasoner"
        if (isDeepseekVisionCapable) {
            if (!providersToTry.contains("deepseek")) {
                providersToTry.add("deepseek")
            }
        }

        val errorsList = mutableListOf<String>()

        for (provider in providersToTry) {
            val isConfigured = when (provider) {
                "gemini" -> {
                    _geminiApiKey.value.isNotBlank() ||
                    (com.example.BuildConfig.GEMINI_API_KEY.isNotBlank() && com.example.BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY")
                }
                "deepseek" -> _deepseekApiKey.value.isNotBlank()
                "openai" -> _openaiApiKey.value.isNotBlank()
                else -> false
            }

            if (!isConfigured) {
                if (provider == preferredProvider) {
                    errorsList.add("$provider is selected but has no API Key.")
                }
                continue
            }

            try {
                android.util.Log.d("ReceiptScanner", "Attempting scan with provider: $provider")
                val result = tryProviderScan(provider, base64Image, prompt)
                return result
            } catch (e: Exception) {
                val msg = e.message ?: e.toString()
                android.util.Log.e("ReceiptScanner", "Scan failed for provider $provider: $msg")
                errorsList.add("$provider failed: $msg")
            }
        }

        val combinedErrorMessage = if (errorsList.isEmpty()) {
            "No AI providers are configured. Please enter your API Key(s) in the settings menu."
        } else {
            "Scan failed across all configured AI providers:\n" + errorsList.joinToString("\n") { "- $it" }
        }
        throw Exception(combinedErrorMessage)
    }

    fun analyzeReceipt(bitmap: Bitmap, onResult: (String?, Double?, Long?) -> Unit) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _analysisError.value = null
            try {
                val parsed = analyzeReceiptUnified(bitmap)
                val parsedDateMillis = parseDateToMillis(parsed.date)
                onResult(parsed.shopName, parsed.amount, parsedDateMillis)
            } catch (e: Exception) {
                e.printStackTrace()
                _analysisError.value = getErrorMessage(e)
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
                    try {
                        val path = expense.imagePath
                        if (path != null) {
                            val file = File(path)
                            if (file.exists()) {
                                val bitmap = BitmapFactory.decodeFile(path)
                                if (bitmap != null) {
                                    val result = analyzeReceiptBackground(bitmap)
                                    val parsedDate = parseDateToMillis(result.date) ?: expense.date
                                    repository.insertExpense(
                                        expense.copy(
                                            shopName = result.shopName,
                                            amount = result.amount,
                                            date = parsedDate,
                                            isPendingAnalysis = false
                                        )
                                    )
                                } else {
                                    repository.insertExpense(expense.copy(isPendingAnalysis = false))
                                }
                            } else {
                                repository.insertExpense(expense.copy(isPendingAnalysis = false))
                            }
                        } else {
                            repository.insertExpense(expense.copy(isPendingAnalysis = false))
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isSyncing.value = false
            }
        }
    }

    private suspend fun analyzeReceiptBackground(bitmap: Bitmap): com.example.api.ReceiptAnalysisResult {
        return analyzeReceiptUnified(bitmap)
    }

    internal fun getErrorMessage(e: Throwable): String {
        if (e is retrofit2.HttpException) {
            val errorBodyStr = try {
                e.response()?.errorBody()?.string()
            } catch (ex: Exception) {
                null
            }
            if (!errorBodyStr.isNullOrBlank()) {
                try {
                    val json = org.json.JSONObject(errorBodyStr)
                    if (json.has("error")) {
                        val errorObj = json.getJSONObject("error")
                        if (errorObj.has("message")) {
                            return errorObj.getString("message")
                        }
                    }
                } catch (ex: Exception) {
                    // Ignore and fall back to general HTTP status
                }
            }
            return "HTTP ${e.code()}: ${e.message()}"
        }
        return e.localizedMessage ?: e.message ?: "Unknown error occurred during AI analysis."
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
                if (!isNetworkAvailable()) {
                    throw Exception("No network connection available. Please connect to internet.")
                }

                val bitmap = BitmapFactory.decodeFile(path)
                if (bitmap == null) {
                    throw Exception("Failed to decode receipt image file.")
                }

                val result = analyzeReceiptBackground(bitmap)
                val parsedDate = parseDateToMillis(result.date) ?: expense.date
                val updatedExpense = expense.copy(
                    shopName = result.shopName.ifBlank { "Unknown Shop" },
                    amount = result.amount,
                    date = parsedDate,
                    isPendingAnalysis = false
                )
                repository.insertExpense(updatedExpense)
            } catch (e: Exception) {
                e.printStackTrace()
                val errorMsg = getErrorMessage(e)
                _manualScanningErrors.update { it + (expense.id to errorMsg) }
            } finally {
                _manualScanningIds.update { it - expense.id }
            }
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
