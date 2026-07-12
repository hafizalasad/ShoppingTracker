package com.example.data.parser

/**
 * ConfidenceCalculator: Computes a confidence score based on what components were successfully extracted.
 * Allocation:
 * - Merchant found = +30
 * - Total found = +40
 * - Date found = +20
 * - Currency found = +10
 * - Maximum score = 100
 */
class ConfidenceCalculator {

    fun calculateConfidence(
        hasMerchant: Boolean,
        hasTotal: Boolean,
        hasDate: Boolean,
        hasCurrency: Boolean
    ): Int {
        var score = 0
        if (hasMerchant) score += 30
        if (hasTotal) score += 40
        if (hasDate) score += 20
        if (hasCurrency) score += 10
        return score
    }
}
