package com.example.data.parser

import java.util.regex.Pattern

/**
 * TotalDetector: Searches for total amounts in OCR text.
 * Triggers:
 * - Total keywords: TOTAL, Grand Total, Net Total, Amount, Amount Due, Payable, Balance Due (case-insensitive).
 * - Excludes lines containing: Subtotal, Sub Total, VAT, Tax, Service Charge, Discount, Change, Cash, Tip.
 * - Extracts amounts with support for commas, decimals, and currency prefixes.
 * - Returns the best candidate amount.
 */
class TotalDetector {

    private val totalKeywords = listOf(
        "grand total", "net total", "amount due", "balance due", "total payable", "total", "payable", "amount"
    )

    private val ignoreKeywords = listOf(
        "subtotal", "sub total", "vat", "tax", "service charge", "discount", "change", "cash", "tip", "refund"
    )

    // Matches standard decimal amounts like 123.45 or 1,234.56, optionally preceded by currency symbols/identifiers.
    // e.g., $120, ৳450, RM35.20, 123.45, 1,234
    private val amountPattern = Pattern.compile("(?i)(?:[\\$€৳£¥₹]|rm|usd|sgd|bdt|eur|gbp)?\\s*(\\d{1,3}(?:,\\d{3})*(?:\\.\\d{2})?|\\d+\\.\\d{2}|\\d{2,})\\b")

    fun detectTotal(lines: List<String>): Double? {
        val candidates = mutableListOf<Double>()

        for (i in lines.indices) {
            val line = lines[i].trim()
            val lowerLine = line.lowercase()

            // 1. Skip lines containing ignore keywords (Subtotal, VAT, Tax, etc.)
            if (ignoreKeywords.any { lowerLine.contains(it) }) {
                continue
            }

            // 2. Check if line contains any total keyword
            if (totalKeywords.any { lowerLine.contains(it) }) {
                // Search for an amount on the SAME line first
                val amountOnLine = extractAmountsFromLine(line)
                if (amountOnLine.isNotEmpty()) {
                    // Nearest to TOTAL is usually the last number on the line, or the one right after the keyword
                    candidates.add(amountOnLine.last())
                } else {
                    // If no amount on the same line, check the NEXT line (within reason)
                    if (i + 1 < lines.size) {
                        val nextLine = lines[i + 1].trim()
                        val nextLineAmounts = extractAmountsFromLine(nextLine)
                        if (nextLineAmounts.isNotEmpty() && !ignoreKeywords.any { nextLine.lowercase().contains(it) }) {
                            candidates.add(nextLineAmounts.first())
                        }
                    }
                }
            }
        }

        // If no keyword-associated totals were found, try finding any standalone decimal numbers
        if (candidates.isEmpty()) {
            for (line in lines) {
                val lowerLine = line.lowercase()
                if (ignoreKeywords.any { lowerLine.contains(it) }) continue
                val amounts = extractAmountsFromLine(line)
                if (amounts.isNotEmpty()) {
                    candidates.addAll(amounts)
                }
            }
        }

        // Return the largest reasonable amount if multiple candidates exist, or null
        return candidates.maxOrNull()
    }

    private fun extractAmountsFromLine(line: String): List<Double> {
        val list = mutableListOf<Double>()
        val matcher = amountPattern.matcher(line)
        while (matcher.find()) {
            val amountStr = matcher.group(1) ?: continue
            val cleaned = amountStr.replace(",", "")
            val parsed = cleaned.toDoubleOrNull()
            if (parsed != null && parsed > 0.0 && parsed < 1000000.0) { // filter out ridiculously large/small numbers
                list.add(parsed)
            }
        }
        return list
    }
}
