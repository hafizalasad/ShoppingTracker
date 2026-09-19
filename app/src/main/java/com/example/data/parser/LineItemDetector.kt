package com.example.data.parser

import com.example.domain.model.ProductLineItem
import java.util.regex.Pattern

class LineItemDetector {

    // Regex for amounts like 187.00, 1,234.50, 385
    private val amountPattern = Pattern.compile("(?i)(?:[\\$€৳£¥₹]|rm|usd|sgd|bdt|eur|gbp)?\\s*(\\d{1,3}(?:,\\d{3})*(?:\\.\\d{2})|\\d+\\.\\d{2})\\b")

    // Regex for SKU header lines: e.g. "1. GRO00444 [1.0 @187.00] 187.00" or "[1.0 @187.00]"
    private val skuHeaderPattern = Pattern.compile("(?i)^(?:\\d+[.\\-\\s]+)?([A-Z0-9]{4,20})\\s*\\[([0-9.]+)\\s*@([0-9.]+)\\]\\s*(\\d+(?:\\.\\d+)?.*)?$")

    // General pattern with bracketed qty & price e.g. [1.0 @187.00]
    private val qtyPriceBracketPattern = Pattern.compile("\\[([0-9.]+)\\s*@([0-9.]+)\\]")

    private val ignoreKeywords = listOf(
        "subtotal", "sub total", "item total", "grand total", "net total", "net payable", "net amount",
        "amount due", "balance due", "tax", "vat", "gst", "service charge", "service tax",
        "item discount", "special discount", "discount", "rounding", "+/- rounding",
        "paid", "payment", "cash", "change", "tendered", "balance due", "cash back",
        "total item qty", "total items", "item count", "points", "earned", "loyalty",
        "visa card", "master card", "mastercard", "visa", "mobile banking", "bkash", "nagad",
        "invoice", "receipt", "counter no", "table #", "bin", "sales person", "cust. name",
        "customer name", "date", "time", "tel", "phone", "thank you", "welcome", "branch", "slip"
    )

    fun detectLineItems(lines: List<String>): List<ProductLineItem> {
        val items = mutableListOf<ProductLineItem>()
        val n = lines.size
        var i = 0

        while (i < n) {
            val rawLine = lines[i].trim()
            if (rawLine.length < 3) {
                i++
                continue
            }

            val lowerLine = rawLine.lowercase()
            if (ignoreKeywords.any { lowerLine.contains(it) }) {
                i++
                continue
            }

            // Check if this line is an SKU/header item row: e.g.,
            // "1. GRO00444 [1.0 @187.00] 187.00"
            val skuMatcher = skuHeaderPattern.matcher(rawLine)
            val qtyBracketMatcher = qtyPriceBracketPattern.matcher(rawLine)

            if (skuMatcher.find() || qtyBracketMatcher.find()) {
                val qty: Double
                val unitPrice: Double
                if (qtyBracketMatcher.find(0)) {
                    qty = qtyBracketMatcher.group(1)?.toDoubleOrNull() ?: 1.0
                    unitPrice = qtyBracketMatcher.group(2)?.toDoubleOrNull() ?: 0.0
                } else {
                    qty = 1.0
                    unitPrice = 0.0
                }

                // Check if total price is at the end of this line
                val amtMatcher = amountPattern.matcher(rawLine)
                var lineTotal = 0.0
                while (amtMatcher.find()) {
                    val candidate = amtMatcher.group(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
                    // If candidate is not the unit price or is found at the end of the line
                    if (candidate > 0.0) {
                        lineTotal = candidate
                    }
                }
                if (lineTotal == 0.0) {
                    lineTotal = qty * unitPrice
                }

                // The next line is likely the product description (e.g., "Prince Fresh Eggs Red (12Pcs Box)")
                var productName = ""
                if (i + 1 < n) {
                    val nextLine = lines[i + 1].trim()
                    val nextLower = nextLine.lowercase()
                    if (nextLine.length >= 2 && !ignoreKeywords.any { nextLower.contains(it) } && !skuHeaderPattern.matcher(nextLine).find()) {
                        productName = nextLine
                        i++ // consume next line as product name
                    }
                }

                if (productName.isBlank()) {
                    // Fallback: extract name from current line by stripping codes
                    productName = rawLine.replace("\\[.*?\\]".toRegex(), "")
                        .replace(amountPattern.pattern().toRegex(), "")
                        .replace("^[0-9]+[.\\-\\s]+".toRegex(), "")
                        .trim()
                }

                if (productName.isNotBlank() && lineTotal > 0.0) {
                    items.add(
                        ProductLineItem(
                            name = productName,
                            amount = lineTotal,
                            quantity = qty,
                            unitPrice = if (unitPrice > 0.0) unitPrice else (lineTotal / qty),
                            category = guessCategory(productName)
                        )
                    )
                    i++
                    continue
                }
            }

            // Standard line item with price in the same line
            val matcher = amountPattern.matcher(rawLine)
            if (matcher.find()) {
                val amountStr = matcher.group(1) ?: ""
                val amountVal = amountStr.replace(",", "").toDoubleOrNull() ?: 0.0
                if (amountVal > 0.0 && amountVal <= 100000.0) {
                    val rawName = rawLine.replace(matcher.group(0) ?: "", "")
                        .replace("^[0-9]+[.\\-\\s]+".toRegex(), "")
                        .replace("[*#@|:;]+".toRegex(), "")
                        .trim()

                    if (rawName.length >= 2 && !ignoreKeywords.any { rawName.lowercase().contains(it) }) {
                        items.add(
                            ProductLineItem(
                                name = rawName,
                                amount = amountVal,
                                quantity = 1.0,
                                unitPrice = amountVal,
                                category = guessCategory(rawName)
                            )
                        )
                    }
                }
            }
            i++
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
