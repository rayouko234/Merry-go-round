package com.example.merrygoround.core.common

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Money is ALWAYS handled as integer minor units (cents) plus an ISO currency code.
 * KES uses a two-decimal convention: KES 5,000 == 500_000 minor units.
 * Floating point is never used for authoritative amounts.
 */
object Money {

    private val groupingSymbols = DecimalFormatSymbols(Locale.US).apply {
        groupingSeparator = ','
        decimalSeparator = '.'
    }

    /** Number of minor units per major unit for the currencies we support. */
    private fun fractionDigits(currency: String): Int = when (currency.uppercase(Locale.US)) {
        "JPY", "KRW" -> 0
        else -> 2
    }

    /** Convert a major-unit decimal string ("5000", "5000.50") to minor units. */
    fun parseToMinor(input: String, currency: String = "KES"): Long? {
        val trimmed = input.trim().replace(",", "")
        if (trimmed.isEmpty()) return null
        if (!trimmed.matches(Regex("""^-?\d{0,12}(\.\d{0,6})?$"""))) return null
        return try {
            val digits = fractionDigits(currency)
            val value = BigDecimal(trimmed).setScale(digits, RoundingMode.HALF_UP)
            if (value.signum() < 0) return null
            value.movePointRight(digits).longValueExact()
        } catch (e: ArithmeticException) {
            null
        } catch (e: NumberFormatException) {
            null
        }
    }

    /** "500000" minor units, KES -> "KES 5,000.00" */
    fun format(minor: Long, currency: String = "KES", includeCode: Boolean = true): String {
        val digits = fractionDigits(currency)
        val major = BigDecimal.valueOf(minor, digits)
        // "0" (not "#") so trailing zeros are always shown for money.
        val pattern = if (digits == 0) "#,##0" else "#,##0.${"0".repeat(digits)}"
        val formatted = DecimalFormat(pattern, groupingSymbols).format(major)
        return if (includeCode) "$currency $formatted" else formatted
    }

    /** Sum of minor units without overflow surprises for our scale. */
    fun sum(minors: Iterable<Long>): Long = minors.fold(0L) { acc, v -> acc + v }
}
