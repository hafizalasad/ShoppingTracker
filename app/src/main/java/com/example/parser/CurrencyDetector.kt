package com.example.parser

/**
 * CurrencyDetector: Detects currency symbols or ISO codes from the OCR text.
 * Supported/Configured currencies match the app's supported list:
 * - USD ($)
 * - BDT (৳, TK)
 * - EUR (€)
 * - GBP (£)
 * - JPY (¥)
 * - INR (₹, RS)
 * - CAD (CA$)
 * - AUD (AU$)
 * - SGD (SG$)
 */
class CurrencyDetector {

    private val currencyMappings = listOf(
        Pair(setOf("$", "usd", "u.s.d"), "USD"),
        Pair(setOf("৳", "bdt", "tk", "taka"), "BDT"),
        Pair(setOf("€", "eur", "euro"), "EUR"),
        Pair(setOf("£", "gbp", "pound"), "GBP"),
        Pair(setOf("¥", "jpy", "yen"), "JPY"),
        Pair(setOf("₹", "inr", "rs", "rupee"), "INR"),
        Pair(setOf("ca$", "cad"), "CAD"),
        Pair(setOf("au$", "aud"), "AUD"),
        Pair(setOf("sg$", "sgd"), "SGD"),
        Pair(setOf("rm", "myr"), "MYR") // Support extra Malaysian Ringgit from prompts
    )

    fun detectCurrency(lines: List<String>): String? {
        for (line in lines) {
            val lowerLine = line.lowercase()
            for ((keywords, code) in currencyMappings) {
                for (keyword in keywords) {
                    if (lowerLine.contains(keyword)) {
                        return code
                    }
                }
            }
        }
        return null
    }
}
