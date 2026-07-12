package com.example.parser

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

/**
 * DateDetector: Detects issue dates from raw OCR text.
 * Supports formats like:
 * - 12/05/2026, 12-05-2026, 12.05.2026
 * - 2026-05-12, 2026/05/12
 * - 12 May 2026, 12-May-26
 * - May 12, 2026
 */
class DateDetector {

    private val datePatterns = listOf(
        // YYYY-MM-DD or YYYY/MM/DD
        Pattern.compile("\\b(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})\\b"),
        // DD/MM/YYYY or DD-MM-YYYY or DD.MM.YYYY
        Pattern.compile("\\b(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{2,4})\\b"),
        // DD May 2026 or DD-May-26 (Verbal Month)
        Pattern.compile("\\b(\\d{1,2})[\\s-](Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[\\s-](\\d{2,4})\\b", Pattern.CASE_INSENSITIVE),
        // May 12, 2026 (Verbal Month first)
        Pattern.compile("\\b(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[\\s-](\\d{1,2}),?[\\s-](\\d{2,4})\\b", Pattern.CASE_INSENSITIVE)
    )

    private val monthsMap = mapOf(
        "jan" to 0, "feb" to 1, "mar" to 2, "apr" to 3, "may" to 4, "jun" to 5,
        "jul" to 6, "aug" to 7, "sep" to 8, "oct" to 9, "nov" to 10, "dec" to 11
    )

    fun detectDate(lines: List<String>): Long? {
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            for (pattern in datePatterns) {
                val matcher = pattern.matcher(trimmed)
                if (matcher.find()) {
                    try {
                        val calendar = Calendar.getInstance()
                        calendar.set(Calendar.HOUR_OF_DAY, 0)
                        calendar.set(Calendar.MINUTE, 0)
                        calendar.set(Calendar.SECOND, 0)
                        calendar.set(Calendar.MILLISECOND, 0)

                        if (pattern == datePatterns[0]) {
                            // YYYY-MM-DD
                            val year = matcher.group(1)?.toIntOrNull() ?: continue
                            val month = (matcher.group(2)?.toIntOrNull() ?: continue) - 1
                            val day = matcher.group(3)?.toIntOrNull() ?: continue
                            if (isValidDate(year, month, day)) {
                                calendar.set(year, month, day)
                                return calendar.timeInMillis
                            }
                        } else if (pattern == datePatterns[1]) {
                            // DD/MM/YYYY or DD/MM/YY
                            val day = matcher.group(1)?.toIntOrNull() ?: continue
                            val month = (matcher.group(2)?.toIntOrNull() ?: continue) - 1
                            var year = matcher.group(3)?.toIntOrNull() ?: continue
                            if (year < 100) {
                                year += 2000
                            }
                            if (isValidDate(year, month, day)) {
                                calendar.set(year, month, day)
                                return calendar.timeInMillis
                            }
                        } else if (pattern == datePatterns[2]) {
                            // DD May 2026
                            val day = matcher.group(1)?.toIntOrNull() ?: continue
                            val monthStr = matcher.group(2)?.lowercase() ?: continue
                            val month = monthsMap[monthStr] ?: continue
                            var year = matcher.group(3)?.toIntOrNull() ?: continue
                            if (year < 100) {
                                year += 2000
                            }
                            if (isValidDate(year, month, day)) {
                                calendar.set(year, month, day)
                                return calendar.timeInMillis
                            }
                        } else if (pattern == datePatterns[3]) {
                            // May 12, 2026
                            val monthStr = matcher.group(1)?.lowercase() ?: continue
                            val month = monthsMap[monthStr] ?: continue
                            val day = matcher.group(2)?.toIntOrNull() ?: continue
                            var year = matcher.group(3)?.toIntOrNull() ?: continue
                            if (year < 100) {
                                year += 2000
                            }
                            if (isValidDate(year, month, day)) {
                                calendar.set(year, month, day)
                                return calendar.timeInMillis
                            }
                        }
                    } catch (e: Exception) {
                        // Keep searching if parsing this candidate failed
                    }
                }
            }
        }
        return null
    }

    private fun isValidDate(year: Int, month: Int, day: Int): Boolean {
        if (year < 1990 || year > 2100) return false
        if (month < 0 || month > 11) return false
        if (day < 1 || day > 31) return false
        return true
    }
}
