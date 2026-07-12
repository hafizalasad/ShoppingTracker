package com.example.data.parser

import java.util.regex.Pattern

/**
 * MerchantDetector: Detects the best merchant name candidate from OCR raw text.
 * Triggers:
 * - Scores lines based on features (position, keyword match, Title Case/ALL CAPS, word count, digit presence)
 * - Ignores common header, metadata, address and branch lines.
 */
class MerchantDetector {

    private val phonePattern = Pattern.compile("(?i)(tel|phone|ph|mobile|fax|contact|num|no)[:.\\s]*[+0-9\\s()-]{7,}")
    private val datePattern = Pattern.compile("\\b(\\d{1,4}[-/.]\\d{1,2}[-/.]\\d{1,4})\\b")
    private val timePattern = Pattern.compile("\\b(\\d{1,2}:\\d{2}(:\\d{2})?\\s*(AM|PM|am|pm)?)\\b")
    private val numericOnlyPattern = Pattern.compile("^[\\d.,\\s\\-\\/$€৳£¥₹]+$")

    fun detectMerchant(lines: List<String>, heights: List<Float>? = null): String? {
        var bestCandidate: String? = null
        var highestScore = -Double.MAX_VALUE

        val maxHeight = heights?.maxOrNull() ?: 1.0f

        lines.forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || shouldIgnore(line)) return@forEachIndexed

            // Score candidate
            var score = 0.0

            // 1. Appears near the top of the receipt
            val topScore = maxOf(0.0, 50.0 - index * 5.0)
            score += topScore

            // 2. Contains positive merchant-specific keywords
            val positiveKeywords = listOf(
                "limited", "ltd", "store", "mart", "bazar", "bazaar", "super shop", "supershop", "pharmacy", "restaurant", "cafe"
            )
            val lowerLine = line.lowercase()
            if (positiveKeywords.any { lowerLine.contains(it) }) {
                score += 40.0
            }

            // 3. Is Title Case or ALL CAPS
            if (isAllCaps(line)) {
                score += 25.0
            } else if (isTitleCase(line)) {
                score += 25.0
            }

            // 4. Has fewer than 5-6 words
            val words = line.split("\\s+".toRegex()).filter { it.isNotEmpty() }
            if (words.size in 1..5) {
                score += 20.0
            }

            // 5. Does not contain many numbers
            val digitCount = line.count { it.isDigit() }
            if (digitCount == 0) {
                score += 15.0
            } else if (digitCount <= 2 || digitCount.toDouble() / line.length < 0.15) {
                score += 10.0
            }

            // 6. Use ML Kit bounding box heights if available (prefer larger text near top)
            if (heights != null && heights.size == lines.size && maxHeight > 0f) {
                val lineRectHeight = heights[index]
                val heightRatio = lineRectHeight / maxHeight
                score += heightRatio * 50.0
            }

            if (score > highestScore) {
                highestScore = score
                bestCandidate = line
            }
        }

        return bestCandidate
    }

    private fun shouldIgnore(line: String): Boolean {
        val lowerLine = line.lowercase()

        // 1. Ignore very short lines (less than 3 characters)
        if (line.length < 3) return true

        // 2. Ignore numeric-only lines, amounts, etc.
        if (numericOnlyPattern.matcher(line).matches()) return true

        // 3. Ignore phone number, fax, etc.
        if (phonePattern.matcher(line).find() || lowerLine.contains("tel:") || lowerLine.contains("ph:")) {
            return true
        }

        // 4. Ignore lines containing dates
        if (datePattern.matcher(line).find()) {
            return true
        }

        // 5. Ignore lines containing time
        if (timePattern.matcher(line).find()) {
            return true
        }

        // New ignored/exclusion keywords
        val ignoredKeywordsList = listOf(
            "government", "people's republic", "peoples republic", "national board", "revenue", "nbr", "vat", "mushak", "mushok", "tax invoice", "fiscal receipt",
            "bin", "date", "invoice", "counter", "sales person", "salesperson", "customer", "cust name", "phone", "email",
            "branch", "pallabi", "mirpur", "dhaka", "road", "house", "building", "sector", "block",
            "receipt", "tax", "e-invoice", "bill", "payment", "slip", "cash sale", "duplicate", "copy", "original",
            "welcome", "retail", "merchant", "checkout", "statement", "terminal", "operator", "order", "purchase", "sale", "transaction"
        )

        if (ignoredKeywordsList.any { keyword ->
            lowerLine == keyword || lowerLine.contains(" $keyword ") || lowerLine.startsWith("$keyword ") || lowerLine.endsWith(" $keyword") || lowerLine.contains(keyword)
        }) {
            return true
        }

        return false
    }

    private fun isAllCaps(s: String): Boolean {
        val letters = s.filter { it.isLetter() }
        return letters.isNotEmpty() && letters.all { it.isUpperCase() }
    }

    private fun isTitleCase(s: String): Boolean {
        val words = s.split("\\s+".toRegex()).filter { it.isNotEmpty() && it[0].isLetter() }
        if (words.isEmpty()) return false
        val capitalizedCount = words.count { it[0].isUpperCase() }
        return capitalizedCount.toDouble() / words.size >= 0.75
    }
}
