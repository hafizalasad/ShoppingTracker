package com.example.util

import java.util.Locale

object CurrencyUtils {
    /**
     * Formats an amount with the specified currency symbol in Bangladeshi grouping style:
     * - Grouping of 3 for the rightmost digits (thousands).
     * - Grouping of 2 for all digits to the left of thousands.
     * E.g., 1234567.89 -> 12,34,567.89
     */
    fun formatBangladeshiStyle(currencySymbol: String, amount: Double, includeDecimals: Boolean = true): String {
        val isNegative = amount < 0
        val absAmount = Math.abs(amount)
        val formattedStr = if (includeDecimals) {
            String.format(Locale.US, "%.2f", absAmount)
        } else {
            String.format(Locale.US, "%.0f", absAmount)
        }
        val parts = formattedStr.split(".")
        val integerPart = parts[0]
        val fractionalPart = if (parts.size > 1) parts[1] else null

        val groupedInteger = if (integerPart.length <= 3) {
            integerPart
        } else {
            val thousands = integerPart.substring(integerPart.length - 3)
            val remaining = integerPart.substring(0, integerPart.length - 3)
            val reversedRemaining = remaining.reversed()
            val chunked = reversedRemaining.chunked(2).joinToString(",")
            val regroupedRemaining = chunked.reversed()
            "$regroupedRemaining,$thousands"
        }

        val sign = if (isNegative) "-" else ""
        return if (fractionalPart != null) {
            "$sign$currencySymbol$groupedInteger.$fractionalPart"
        } else {
            "$sign$currencySymbol$groupedInteger"
        }
    }
}
