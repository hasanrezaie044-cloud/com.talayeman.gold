package com.talayeman.gold.domain.usecase

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Purchase price calculation shared by ALL asset types (gold, coins, other).
 *
 *   base  = unit price x count      (gold: per gram x grams x pieces; coin / other: per piece x pieces)
 *   total = base x (1 + (makingPercent + taxPercent) / 100) + otherFees      <- additive percentages
 *
 * Example: 1 g x 25,000,000 with making 7 % and tax 5 % -> 25,000,000 x 1.12 = 28,000,000.
 *
 * For the stored data model the total is split into: purchasePrice (= base), makingCharge,
 * tax (both as amounts) and otherFees. The tax amount is derived from the exact total, so
 * base + making + tax + fees always equals the total with no rounding drift.
 */
object PurchasePricing {

    private val HUNDRED = BigDecimal(100)
    val MIN_PERCENT: BigDecimal = BigDecimal.ZERO
    val MAX_PERCENT: BigDecimal = HUNDRED

    data class Result(
        val base: BigDecimal,
        val making: BigDecimal,
        val tax: BigDecimal,
        val fees: BigDecimal,
        val total: BigDecimal
    )

    /** Percentages must be within 0..100 (inclusive). */
    fun isValidPercent(p: BigDecimal): Boolean = p >= MIN_PERCENT && p <= MAX_PERCENT

    fun calculate(
        unitPrice: BigDecimal,
        count: BigDecimal,
        makingPercent: BigDecimal,
        taxPercent: BigDecimal,
        otherFees: BigDecimal = BigDecimal.ZERO
    ): Result {
        val making = makingPercent.clampPercent()
        val tax = taxPercent.clampPercent()

        val base = unitPrice.multiply(count).setScale(0, RoundingMode.HALF_UP)
        val factor = BigDecimal.ONE.add(making.add(tax).divide(HUNDRED, 10, RoundingMode.HALF_UP))
        val baseWithPercents = base.multiply(factor).setScale(0, RoundingMode.HALF_UP)

        val makingAmount = base.multiply(making).divide(HUNDRED, 0, RoundingMode.HALF_UP)
        val taxAmount = baseWithPercents.subtract(base).subtract(makingAmount)
        val fees = otherFees.setScale(0, RoundingMode.HALF_UP)

        return Result(
            base = base,
            making = makingAmount,
            tax = taxAmount,
            fees = fees,
            total = baseWithPercents.add(fees)
        )
    }

    /** Percent of [part] relative to [base] (used when loading an old asset that stored amounts). */
    fun percentOf(part: BigDecimal, base: BigDecimal): BigDecimal =
        if (base.signum() <= 0) BigDecimal.ZERO
        else part.multiply(HUNDRED).divide(base, 6, RoundingMode.HALF_UP).stripTrailingZeros()
            .let { if (it.scale() < 0) it.setScale(0) else it }

    private fun BigDecimal.clampPercent(): BigDecimal =
        if (this < MIN_PERCENT) MIN_PERCENT else if (this > MAX_PERCENT) MAX_PERCENT else this
}
