package com.example.data.parser

import com.example.domain.model.ProductLineItem

data class ReceiptResult(
    val merchant: String?,
    val amount: Double?,
    val date: Long?,
    val currency: String?,
    val confidence: Int,
    val lineItems: List<ProductLineItem> = emptyList()
)

/**
 * ReceiptParser: Orchestrates the individual detector classes to parse a raw OCR text string.
 */
class ReceiptParser(
    private val merchantDetector: MerchantDetector = MerchantDetector(),
    private val totalDetector: TotalDetector = TotalDetector(),
    private val dateDetector: DateDetector = DateDetector(),
    private val currencyDetector: CurrencyDetector = CurrencyDetector(),
    private val confidenceCalculator: ConfidenceCalculator = ConfidenceCalculator(),
    private val lineItemDetector: LineItemDetector = LineItemDetector()
) {

    fun parse(rawOcrText: String): ReceiptResult {
        // Split text into individual non-empty lines
        val lines = rawOcrText.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val merchant = merchantDetector.detectMerchant(lines)
        val amount = totalDetector.detectTotal(lines)
        val date = dateDetector.detectDate(lines)
        val currency = currencyDetector.detectCurrency(lines)
        val lineItems = lineItemDetector.detectLineItems(lines)

        val confidence = confidenceCalculator.calculateConfidence(
            hasMerchant = merchant != null,
            hasTotal = amount != null || lineItems.isNotEmpty(),
            hasDate = date != null,
            hasCurrency = currency != null
        )

        return ReceiptResult(
            merchant = merchant,
            amount = amount,
            date = date,
            currency = currency,
            confidence = confidence,
            lineItems = lineItems
        )
    }
}
