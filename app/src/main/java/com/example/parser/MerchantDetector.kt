package com.example.parser

import java.util.regex.Pattern

/**
 * MerchantDetector: Detects the best merchant name candidate from OCR raw text.
 * Triggers:
 * - Usually the first non-empty line.
 * - Ignore lines that are too short (less than 3 characters).
 * - Ignore lines with only numbers or numeric values.
 * - Ignore common receipt-related keywords like "receipt", "invoice", "tax invoice", etc.
 * - Ignore lines containing date, time, or telephone/fax patterns.
 */
class MerchantDetector {

    private val ignoredKeywords = setOf(
        "receipt", "invoice", "tax invoice", "tax", "e-invoice", "bill", "payment", "slip", "cash sale",
        "duplicate", "copy", "original", "welcome", "retail", "store", "merchant", "checkout", "statement",
        "terminal", "operator", "customer", "order", "purchase", "sale", "transaction"
    )

    private val phonePattern = Pattern.compile("(?i)(tel|phone|ph|mobile|fax|contact|num|no)[:.\\s]*[+0-9\\s()-]{7,}")
    private val datePattern = Pattern.compile("\\b(\\d{1,4}[-/.]\\d{1,2}[-/.]\\d{1,4})\\b")
    private val timePattern = Pattern.compile("\\b(\\d{1,2}:\\d{2}(:\\d{2})?\\s*(AM|PM|am|pm)?)\\b")
    private val numericOnlyPattern = Pattern.compile("^[\\d.,\\s\\-\\/$€৳£¥₹]+$")

    fun detectMerchant(lines: List<String>): String? {
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            // 1. Ignore very short lines (less than 3 characters)
            if (line.length < 3) continue

            // 2. Ignore numeric-only lines, amounts, etc.
            if (numericOnlyPattern.matcher(line).matches()) continue

            // 3. Ignore common receipt structure keywords (case-insensitive)
            val lowerLine = line.lowercase()
            if (ignoredKeywords.any { lowerLine == it || lowerLine.contains(" $it ") || lowerLine.startsWith("$it ") || lowerLine.endsWith(" $it") }) {
                continue
            }

            // 4. Ignore phone number, fax, etc.
            if (phonePattern.matcher(line).find() || lowerLine.contains("tel:") || lowerLine.contains("ph:")) {
                continue
            }

            // 5. Ignore lines containing dates
            if (datePattern.matcher(line).find()) {
                continue
            }

            // 6. Ignore lines containing time
            if (timePattern.matcher(line).find()) {
                continue
            }

            // If we pass all filters, this is our best merchant candidate!
            return line
        }
        return null
    }
}
