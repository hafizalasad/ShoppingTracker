package com.example.data.parser

import com.example.domain.model.ProductLineItem
import java.util.regex.Pattern

class LineItemDetector {

    private val amountPattern = Pattern.compile("(?i)(?:[\\$€৳£¥₹]|rm|usd|sgd|bdt|eur|gbp)?\\s*(\\d{1,3}(?:,\\d{3})*(?:\\.\\d{2})|\\d+\\.\\d{2})\\b")

    private val ignoreKeywords = listOf(
        "total", "subtotal", "sub-total", "grand total", "net total", "amount due", "balance due",
        "tax", "vat", "mushak", "discount", "change", "cash", "tendered", "card", "visa", "mastercard",
        "invoice", "receipt", "date", "time", "counter", "tel", "phone", "thank you", "welcome",
        "branch", "address", "slip", "order #", "table #", "terminal", "server", "bill no", "cust"
    )

    fun detectLineItems(lines: List<String>): List<ProductLineItem> {
        val items = mutableListOf<ProductLineItem>()

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.length < 3) continue

            val lowerLine = line.lowercase()
            if (ignoreKeywords.any { lowerLine.contains(it) }) continue

            // Check if line contains a price/amount
            val matcher = amountPattern.matcher(line)
            if (matcher.find()) {
                val amountStr = matcher.group(1) ?: continue
                val amountVal = amountStr.replace(",", "").toDoubleOrNull() ?: 0.0
                if (amountVal <= 0.0 || amountVal > 100000.0) continue

                // Extract product name (the text preceding or following the amount)
                val rawName = line.replace(matcher.group(0) ?: "", "")
                    .replace("^[0-9]+[.\\-\\s]+".toRegex(), "") // strip leading item numbering e.g. "1. "
                    .replace("[*#@|:;]+".toRegex(), "")
                    .trim()

                val productName = if (rawName.length >= 2) rawName else "Item #${items.size + 1}"
                val guessedCategory = guessCategory(productName)

                items.add(
                    ProductLineItem(
                        name = productName,
                        amount = amountVal,
                        category = guessedCategory
                    )
                )
            }
        }

        // If no line items with price were detected, but lines of products exist, extract them as 0.0 items
        if (items.isEmpty()) {
            for (rawLine in lines) {
                val line = rawLine.trim()
                if (line.length in 3..40 && !ignoreKeywords.any { line.lowercase().contains(it) }) {
                    val lettersCount = line.count { it.isLetter() }
                    if (lettersCount >= 3) {
                        items.add(
                            ProductLineItem(
                                name = line,
                                amount = 0.0,
                                category = guessCategory(line)
                            )
                        )
                    }
                }
                if (items.size >= 10) break
            }
        }

        return items
    }

    private fun guessCategory(name: String): String {
        val lower = name.lowercase()
        return when {
            listOf("milk", "egg", "bread", "fruit", "apple", "banana", "rice", "dal", "oil", "sugar", "salt", "flour", "onion", "potato", "veg", "vegetable", "butter", "cheese", "yogurt", "water", "juice", "grocery", "snack", "biscuit", "cookie", "cereal").any { lower.contains(it) } -> "Groceries"
            listOf("burger", "pizza", "coffee", "tea", "coke", "pepsi", "lunch", "dinner", "breakfast", "meal", "cake", "sandwich", "pasta", "soup", "noodle", "grill", "fry", "biryani", "curry", "restaurant", "cafe", "dine").any { lower.contains(it) } -> "Food & Dining"
            listOf("shirt", "pant", "shoe", "dress", "jean", "jacket", "cloth", "t-shirt", "socks", "belt", "hat", "cap", "bag", "purse", "fashion", "apparel", "wear").any { lower.contains(it) } -> "Shopping"
            listOf("uber", "taxi", "bus", "fuel", "petrol", "gas", "octane", "diesel", "train", "metro", "fare", "ride", "parking").any { lower.contains(it) } -> "Transport"
            listOf("electricity", "water bill", "gas bill", "internet", "wifi", "broadband", "mobile bill", "recharge", "utility").any { lower.contains(it) } -> "Utilities"
            listOf("med", "medicine", "tablet", "pharma", "pharmacy", "syrup", "capsule", "clinic", "doctor", "health", "dental", "hospital").any { lower.contains(it) } -> "Healthcare"
            listOf("movie", "cinema", "game", "gaming", "ticket", "netflix", "spotify", "show", "theatre", "concert", "amusement").any { lower.contains(it) } -> "Entertainment"
            listOf("phone", "laptop", "cable", "charger", "mouse", "keyboard", "monitor", "headphone", "airpod", "gadget", "battery", "electronic").any { lower.contains(it) } -> "Electronics"
            listOf("book", "pen", "pencil", "notebook", "stationery", "course", "tuition", "exam", "school", "college", "univ").any { lower.contains(it) } -> "Education"
            listOf("soap", "shampoo", "lotion", "cream", "paste", "brush", "salon", "parlor", "haircut", "spa", "perfume", "deodorant").any { lower.contains(it) } -> "Personal Care"
            else -> "General"
        }
    }
}
