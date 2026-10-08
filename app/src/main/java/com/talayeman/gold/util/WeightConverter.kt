package com.talayeman.gold.util

import com.talayeman.gold.domain.model.WeightUnit
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Canonical internal weight unit: milligrams (Long for integer precision).
 *
 * Iranian market standard:
 * - 1 gram = 1000 mg = 1000 soot
 * - 1 Mithqal (مثقال شرعی / market) = 4.608 grams = 4608 mg
 *
 * Using exact market Mithqal conversion used in Iranian gold trade.
 */
object WeightConverter {

    private val GRAM_TO_MG = BigDecimal("1000")
    private val SOOT_TO_MG = BigDecimal("1") // 1 soot = 1 mg in common Iranian usage for gold
    private val MITHQAL_TO_MG = BigDecimal("4608") // 4.608 g * 1000

    /**
     * Convert any unit to canonical milligrams (Long).
     */
    fun toMilligrams(value: BigDecimal, unit: WeightUnit): Long {
        val mg = when (unit) {
            WeightUnit.MILLIGRAM -> value
            WeightUnit.SOOT -> value.multiply(SOOT_TO_MG)
            WeightUnit.GRAM -> value.multiply(GRAM_TO_MG)
            WeightUnit.MITHQAL -> value.multiply(MITHQAL_TO_MG)
        }
        return mg.setScale(0, RoundingMode.HALF_UP).toLong()
    }

    /**
     * Convert canonical milligrams to the requested unit.
     */
    fun fromMilligrams(mg: Long, unit: WeightUnit): BigDecimal {
        val bd = BigDecimal.valueOf(mg)
        return when (unit) {
            WeightUnit.MILLIGRAM -> bd
            WeightUnit.SOOT -> bd.divide(SOOT_TO_MG, 6, RoundingMode.HALF_UP)
            WeightUnit.GRAM -> bd.divide(GRAM_TO_MG, 6, RoundingMode.HALF_UP)
            WeightUnit.MITHQAL -> bd.divide(MITHQAL_TO_MG, 6, RoundingMode.HALF_UP)
        }
    }

    /**
     * Convenience: convert between any two units.
     */
    fun convert(value: BigDecimal, from: WeightUnit, to: WeightUnit): BigDecimal {
        if (from == to) return value
        val mg = toMilligrams(value, from)
        return fromMilligrams(mg, to)
    }

    /**
     * Format weight for display in Persian locale style.
     */
    fun format(mg: Long, unit: WeightUnit = WeightUnit.GRAM, scale: Int = 3): String {
        val value = fromMilligrams(mg, unit)
        return value.setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }

    /**
     * Standard coin weights in milligrams (approximate standard values).
     * Emami / Bahar Azadi full coin ≈ 8.133 g (18K equivalent or pure depending on type).
     * These are reference values; user can override with actual measured weight.
     */
    object StandardCoinWeights {
        val EMAMI_MG = 8133L          // ~8.133 g
        val BAHAR_AZADI_MG = 8133L
        val HALF_COIN_MG = 4066L
        val QUARTER_COIN_MG = 2033L
        val GRAM_COIN_MG = 1000L
        val PARSIAN_1G_MG = 1000L
    }
}
