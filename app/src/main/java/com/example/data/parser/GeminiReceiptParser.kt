package com.example.data.parser

import android.util.Log
import com.example.BuildConfig
import com.example.api.Candidate
import com.example.api.Content
import com.example.api.GeminiApiClient
import com.example.api.GenerateContentRequest
import com.example.api.GenerationConfig
import com.example.api.Part
import com.example.domain.model.ProductLineItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * GeminiReceiptParser:
 * Parses raw OCR text using the Gemini API to extract accurate purchased product line items,
 * quantities, unit prices, and line totals while strictly filtering out non-product lines
 * such as subtotals, tax, discounts, payments, and headers/footers.
 */
class GeminiReceiptParser {

    companion object {
        private const val TAG = "GeminiReceiptParser"

        fun buildGeminiPrompt(rawOcrText: String): String {
            return """
You are an AI assistant for a personal expense tracker app.
Extract ONLY the actual purchased products from this receipt OCR text.

IGNORE these — they are NOT purchased products:
- Subtotal, Sub Total, Item Total, Grand Total, Net Total
- Net Payable, Net Amount, Amount Due, Balance Due
- Tax, VAT, GST, Service Charge
- Discount, Item Discount, Special Discount, Rounding
- Paid, Cash Tendered, Change, Cash Back
- Payment method lines (VISA, MasterCard, Cash, bKash, Nagad)
- Total Item Qty, Item Count
- Loyalty points, Reward points, Earned points
- Invoice No, Receipt No, Counter No, BIN, Sales Person, Customer Name, Date
- Store name, address, header, footer lines
- Any line that is ONLY a number with no product name

IMPORTANT RECEIPT FORMAT RULES:
- If a line has a barcode/SKU like "GRO00444 [1.0 @187.00]", the quantity is 1.0, 
  the unit price is 187.00, and the product name is on the line DIRECTLY BELOW it.
- Price notation [X @Y] means quantity=X, unit price=Y, total=X*Y.
- If quantity is not specified, assume 1.0.

Return ONLY a valid JSON array, no explanation, no markdown backticks, in this exact format:
[
  {
    "name": "Product Name Here",
    "unit_price": 187.00,
    "quantity": 1.0,
    "line_total": 187.00
  }
]

Receipt OCR text:
$rawOcrText
""".trimIndent()
        }
    }

    suspend fun parseProductsFromOcr(rawOcrText: String): List<ProductLineItem> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w(TAG, "Gemini API key is not configured or is placeholder.")
            return@withContext emptyList()
        }

        val prompt = buildGeminiPrompt(rawOcrText)
        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = prompt)
                    )
                )
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.1
            )
        )

        try {
            val response = GeminiApiClient.service.generateContent(
                apiKey = apiKey,
                request = request
            )

            val textResponse = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (textResponse.isNullOrBlank()) {
                Log.w(TAG, "Empty response from Gemini")
                return@withContext emptyList()
            }

            val items = GeminiApiClient.parseProductLineItems(textResponse)
            items.map { item ->
                val qty = item.quantity ?: 1.0
                val unitPrice = item.unit_price ?: 0.0
                val total = item.line_total ?: (qty * unitPrice)
                ProductLineItem(
                    name = item.name.trim(),
                    amount = if (total > 0.0) total else (qty * unitPrice),
                    quantity = if (qty > 0.0) qty else 1.0,
                    unitPrice = if (unitPrice > 0.0) unitPrice else (if (qty > 0) total / qty else total),
                    category = "General"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse receipt with Gemini: ${e.message}", e)
            emptyList()
        }
    }
}
