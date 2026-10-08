package com.talayeman.gold.util

import com.talayeman.gold.domain.model.Currency
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object MoneyUtils {

    private val symbols = DecimalFormatSymbols(Locale("fa", "IR")).apply {
        groupingSeparator = ','
        decimalSeparator = '.'
    }

    private val formatter = DecimalFormat("#,###", symbols)

    /**
     * Parse user input string to BigDecimal safely.
     */
    fun parse(value: String?): BigDecimal {
        if (value.isNullOrBlank()) return BigDecimal.ZERO
        val cleaned = normalizeDigits(value)
            .replace(",", "")
            .replace("٬", "")
            .replace(" ", "")
            .trim()
        return runCatching { BigDecimal(cleaned) }.getOrDefault(BigDecimal.ZERO)
    }

    /**
     * Convert Persian (۰-۹) and Arabic-Indic (٠-٩) digits to ASCII digits and
     * the Persian decimal separator (٫) to '.', so that input typed with a
     * Persian keyboard is parsed correctly. Other characters are kept as-is.
     */
    fun normalizeDigits(value: String): String {
        val sb = StringBuilder(value.length)
        for (c in value) {
            sb.append(
                when (c) {
                    in '\u06F0'..'\u06F9' -> '0' + (c - '\u06F0')
                    in '\u0660'..'\u0669' -> '0' + (c - '\u0660')
                    '\u066B' -> '.'
                    else -> c
                }
            )
        }
        return sb.toString()
    }

    /**
     * Raw (unformatted) editable representation of a stored amount, used to
     * pre-fill money input fields. Zero becomes empty so the field shows its label.
     * Never rounds: fractional values are preserved exactly.
     */
    fun toInputString(amount: BigDecimal?): String {
        if (amount == null || amount.signum() == 0) return ""
        return amount.stripTrailingZeros().toPlainString()
    }

    /**
     * Format BigDecimal for display with thousand separators.
     */
    fun format(amount: BigDecimal, withCurrency: Boolean = false, currency: Currency = Currency.TOMAN): String {
        val scaled = amount.setScale(0, RoundingMode.HALF_UP)
        val formatted = formatter.format(scaled)
        return if (withCurrency) "$formatted ${currency.persianName}" else formatted
    }

    fun format(amount: String, withCurrency: Boolean = false, currency: Currency = Currency.TOMAN): String {
        return format(parse(amount), withCurrency, currency)
    }

    /**
     * Convert between Toman and Rial.
     * 1 Toman = 10 Rial
     */
    fun convert(amount: BigDecimal, from: Currency, to: Currency): BigDecimal {
        if (from == to) return amount
        return when {
            from == Currency.TOMAN && to == Currency.RIAL -> amount.multiply(BigDecimal.TEN)
            from == Currency.RIAL && to == Currency.TOMAN -> amount.divide(BigDecimal.TEN, 0, RoundingMode.HALF_UP)
            else -> amount
        }
    }

    /**
     * Calculate profit/loss.
     */
    fun profitLoss(purchase: BigDecimal, current: BigDecimal): BigDecimal = current.subtract(purchase)

    fun profitLossPercent(purchase: BigDecimal, current: BigDecimal): BigDecimal {
        if (purchase.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO
        return current.subtract(purchase)
            .divide(purchase, 6, RoundingMode.HALF_UP)
            .multiply(BigDecimal(100))
            .setScale(2, RoundingMode.HALF_UP)
    }

    /**
     * Estimate gold value: weight in grams * price per gram * (purity / 24)
     */
    fun estimateGoldValue(
        weightMg: Long,
        quantity: Int,
        purity: Int,
        pricePerGram18or24: BigDecimal,
        is24kPrice: Boolean = false
    ): BigDecimal {
        val totalMg = weightMg * quantity
        val grams = BigDecimal.valueOf(totalMg).divide(BigDecimal(1000), 8, RoundingMode.HALF_UP)
        val purityFactor = if (is24kPrice) {
            BigDecimal(purity).divide(BigDecimal(24), 8, RoundingMode.HALF_UP)
        } else {
            // Price is for 18K; scale relative to 18
            BigDecimal(purity).divide(BigDecimal(18), 8, RoundingMode.HALF_UP)
        }
        return grams.multiply(pricePerGram18or24).multiply(purityFactor)
            .setScale(0, RoundingMode.HALF_UP)
    }
}
