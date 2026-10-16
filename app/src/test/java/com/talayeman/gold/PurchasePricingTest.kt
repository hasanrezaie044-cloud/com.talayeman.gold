package com.talayeman.gold

import com.talayeman.gold.domain.usecase.PurchasePricing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class PurchasePricingTest {

    private fun bd(s: String) = BigDecimal(s)

    @Test
    fun specExample_oneGramGold_7and5percent() {
        // 1 g x 25,000,000 with making 7 % and tax 5 % -> 25,000,000 x 1.12 = 28,000,000
        val r = PurchasePricing.calculate(bd("25000000"), bd("1"), bd("7"), bd("5"))
        assertEquals(0, bd("28000000").compareTo(r.total))
        assertEquals(0, bd("25000000").compareTo(r.base))
        assertEquals(0, bd("1750000").compareTo(r.making))
        assertEquals(0, bd("1250000").compareTo(r.tax))
    }

    @Test
    fun zeroPercentsGiveBase() {
        val r = PurchasePricing.calculate(bd("1000000"), bd("3"), BigDecimal.ZERO, BigDecimal.ZERO)
        assertEquals(0, bd("3000000").compareTo(r.total))
    }

    @Test
    fun partsAlwaysAddUpToTotal() {
        val r = PurchasePricing.calculate(bd("3333333"), bd("2.345"), bd("6.17"), bd("9"), bd("12345"))
        assertEquals(0, r.total.compareTo(r.base.add(r.making).add(r.tax).add(r.fees)))
    }

    @Test
    fun otherFeesAreAddedAfterPercents() {
        val r = PurchasePricing.calculate(bd("1000"), bd("1"), bd("10"), bd("0"), bd("50"))
        assertEquals(0, bd("1150").compareTo(r.total))
    }

    @Test
    fun percentRangeIsZeroToHundred() {
        assertTrue(PurchasePricing.isValidPercent(bd("0")))
        assertTrue(PurchasePricing.isValidPercent(bd("100")))
        assertFalse(PurchasePricing.isValidPercent(bd("100.01")))
        assertFalse(PurchasePricing.isValidPercent(bd("-1")))
    }

    @Test
    fun percentOfRoundTrip() {
        val base = bd("20000000")
        val making = bd("1234567")
        val pct = PurchasePricing.percentOf(making, base)
        val back = PurchasePricing.calculate(base, bd("1"), pct, BigDecimal.ZERO)
        assertEquals(0, making.compareTo(back.making))
    }
}
