package com.example.data

import android.graphics.Bitmap
import com.example.api.*
import com.example.ocr.OcrEngine
import com.example.parser.ReceiptParser
import kotlinx.coroutines.flow.Flow
import java.io.ByteArrayOutputStream
import java.util.Locale

data class ReceiptScanResult(
    val merchant: String?,
    val amount: Double?,
    val date: Long?,
    val confidence: Int,
    val isConfidenceLow: Boolean,
    val scannedOffline: Boolean,
    val scannedWithAi: Boolean,
    val error: String? = null
)

class ExpenseRepository(private val expenseDao: ExpenseDao) {
    fun getAllExpenses(): Flow<List<Expense>> = expenseDao.getAllExpenses()

    fun getExpensesInDateRange(startDate: Long, endDate: Long): Flow<List<Expense>> {
        return expenseDao.getExpensesInDateRange(startDate, endDate)
    }

    suspend fun insertExpense(expense: Expense): Long {
        return expenseDao.insertExpense(expense)
    }

    suspend fun deleteExpenseById(id: Long) {
        expenseDao.deleteExpenseById(id)
    }

    suspend fun getPendingExpenses(): List<Expense> {
        return expenseDao.getPendingExpenses()
    }

    /**
     * scanReceipt: The master orchestrator deciding whether to use ML Kit OCR (Offline) or Gemini (AI Fallback)
     * based on the offlineMode, useAiLowConfidence, alwaysUseAi settings, and confidence score.
     */
    suspend fun scanReceipt(
        bitmap: Bitmap,
        offlineMode: Boolean,
        useAiLowConfidence: Boolean,
        alwaysUseAi: Boolean,
        aiProvider: String,
        geminiApiKey: String,
        deepseekApiKey: String,
        openaiApiKey: String,
        geminiModel: String,
        deepseekModel: String,
        openaiModel: String,
        deepseekBaseUrl: String,
        openaiBaseUrl: String,
        fallbackGeminiKey: String
    ): ReceiptScanResult {
        // 1. If "Always Use AI" is turned on, skip offline OCR and call AI directly
        if (alwaysUseAi) {
            return try {
                val aiResult = callAiProvider(
                    bitmap = bitmap,
                    aiProvider = aiProvider,
                    geminiApiKey = geminiApiKey,
                    deepseekApiKey = deepseekApiKey,
                    openaiApiKey = openaiApiKey,
                    geminiModel = geminiModel,
                    deepseekModel = deepseekModel,
                    openaiModel = openaiModel,
                    deepseekBaseUrl = deepseekBaseUrl,
                    openaiBaseUrl = openaiBaseUrl,
                    fallbackGeminiKey = fallbackGeminiKey
                )
                val parsedDateMillis = parseDateToMillis(aiResult.date)
                ReceiptScanResult(
                    merchant = aiResult.shopName,
                    amount = aiResult.amount,
                    date = parsedDateMillis,
                    confidence = 100,
                    isConfidenceLow = false,
                    scannedOffline = false,
                    scannedWithAi = true
                )
            } catch (e: Exception) {
                ReceiptScanResult(
                    merchant = "Error Scanning",
                    amount = 0.0,
                    date = System.currentTimeMillis(),
                    confidence = 0,
                    isConfidenceLow = true,
                    scannedOffline = false,
                    scannedWithAi = false,
                    error = "AI Scan failed: ${e.message}"
                )
            }
        }

        // 2. Perform Offline ML Kit OCR & custom ReceiptParser
        return try {
            val ocrEngine = OcrEngine()
            val rawText = ocrEngine.recognizeText(bitmap)

            val receiptParser = ReceiptParser()
            val parseResult = receiptParser.parse(rawText)
            val confidence = parseResult.confidence

            if (confidence >= 70) {
                // High confidence offline scan result
                ReceiptScanResult(
                    merchant = parseResult.merchant,
                    amount = parseResult.amount,
                    date = parseResult.date,
                    confidence = confidence,
                    isConfidenceLow = false,
                    scannedOffline = true,
                    scannedWithAi = false
                )
            } else {
                // Low confidence offline scan (< 70)
                // Check if AI Fallback is enabled
                if (useAiLowConfidence) {
                    try {
                        val aiResult = callAiProvider(
                            bitmap = bitmap,
                            aiProvider = aiProvider,
                            geminiApiKey = geminiApiKey,
                            deepseekApiKey = deepseekApiKey,
                            openaiApiKey = openaiApiKey,
                            geminiModel = geminiModel,
                            deepseekModel = deepseekModel,
                            openaiModel = openaiModel,
                            deepseekBaseUrl = deepseekBaseUrl,
                            openaiBaseUrl = openaiBaseUrl,
                            fallbackGeminiKey = fallbackGeminiKey
                        )
                        val parsedDateMillis = parseDateToMillis(aiResult.date)
                        ReceiptScanResult(
                            merchant = aiResult.shopName,
                            amount = aiResult.amount,
                            date = parsedDateMillis,
                            confidence = 100,
                            isConfidenceLow = false,
                            scannedOffline = false,
                            scannedWithAi = true
                        )
                    } catch (aiEx: Exception) {
                        // AI fallback failed - fallback to low confidence offline result with warning
                        ReceiptScanResult(
                            merchant = parseResult.merchant,
                            amount = parseResult.amount,
                            date = parseResult.date,
                            confidence = confidence,
                            isConfidenceLow = true,
                            scannedOffline = true,
                            scannedWithAi = false,
                            error = "AI Fallback failed: ${aiEx.message}. Showing low confidence local result."
                        )
                    }
                } else {
                    // AI Fallback disabled - show low confidence result with a warning
                    ReceiptScanResult(
                        merchant = parseResult.merchant,
                        amount = parseResult.amount,
                        date = parseResult.date,
                        confidence = confidence,
                        isConfidenceLow = true,
                        scannedOffline = true,
                        scannedWithAi = false
                    )
                }
            }
        } catch (e: Exception) {
            ReceiptScanResult(
                merchant = "Error Scanning",
                amount = 0.0,
                date = System.currentTimeMillis(),
                confidence = 0,
                isConfidenceLow = true,
                scannedOffline = false,
                scannedWithAi = false,
                error = "OCR failed: ${e.message}"
            )
        }
    }

    private suspend fun callAiProvider(
        bitmap: Bitmap,
        aiProvider: String,
        geminiApiKey: String,
        deepseekApiKey: String,
        openaiApiKey: String,
        geminiModel: String,
        deepseekModel: String,
        openaiModel: String,
        deepseekBaseUrl: String,
        openaiBaseUrl: String,
        fallbackGeminiKey: String
    ): ReceiptAnalysisResult {
        val base64Image = bitmap.toBase64()
        val prompt = "Extract the shop name, total spent amount, and receipt issue date from this receipt image. Your response must be a JSON object with keys 'shopName' (String, name of the shop), 'amount' (Number, total spent amount. If not found, use 0.0), and 'date' (String, in format YYYY-MM-DD representing the receipt date issued, or null if not found)."

        if (aiProvider == "openai" || aiProvider == "deepseek") {
            val provider = aiProvider
            val customApiKey = if (provider == "deepseek") deepseekApiKey else openaiApiKey
            val customBaseUrl = if (provider == "deepseek") deepseekBaseUrl else openaiBaseUrl
            val model = if (provider == "deepseek") deepseekModel else openaiModel

            val finalBaseUrl = if (provider == "deepseek" && customBaseUrl.isBlank()) "https://api.deepseek.com/v1/" else customBaseUrl
            if (finalBaseUrl.isBlank()) throw Exception("API Base URL is empty.")
            if (customApiKey.isBlank()) throw Exception("API Key is empty.")

            val formattedUrl = when {
                finalBaseUrl.endsWith("/chat/completions") -> finalBaseUrl
                finalBaseUrl.endsWith("/") -> "${finalBaseUrl}chat/completions"
                else -> "$finalBaseUrl/chat/completions"
            }

            val finalModel = if (provider == "deepseek" && model.isBlank()) "deepseek-chat" else model.ifBlank { "google/gemini-2.5-flash" }

            val request = OpenAiChatRequest(
                model = finalModel,
                messages = listOf(
                    OpenAiMessage(
                        role = "user",
                        content = listOf(
                            OpenAiContentPart(type = "text", text = prompt),
                            OpenAiContentPart(
                                type = "image_url",
                                image_url = OpenAiImageUrl(url = "data:image/jpeg;base64,$base64Image")
                            )
                        )
                    )
                ),
                response_format = OpenAiResponseFormat(type = "json_object"),
                temperature = 0.1
            )

            val authHeader = "Bearer $customApiKey"
            val response = GeminiApiClient.openAiService.chatCompletions(formattedUrl, authHeader, request)
            val jsonText = response.choices?.firstOrNull()?.message?.content
            return if (jsonText != null) {
                GeminiApiClient.parseAnalysisResult(jsonText)
                    ?: throw Exception("Failed to parse API response schema. Response text: $jsonText")
            } else {
                throw Exception("API returned an empty response.")
            }
        } else {
            // Gemini provider
            val finalApiKey = geminiApiKey.ifBlank { fallbackGeminiKey }
            if (finalApiKey == "MY_GEMINI_API_KEY" || finalApiKey.isBlank()) {
                throw Exception("Gemini API Key is not configured.")
            }

            val model = if (geminiModel.isNotBlank() && geminiModel != "gemini-3.5-flash") geminiModel else "gemini-3.5-flash"
            val dynamicUrl = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$finalApiKey"

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
            return if (jsonText != null) {
                GeminiApiClient.parseAnalysisResult(jsonText)
                    ?: throw Exception("Failed to parse Gemini AI API response schema.")
            } else {
                throw Exception("Gemini AI API returned an empty response.")
            }
        }
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
