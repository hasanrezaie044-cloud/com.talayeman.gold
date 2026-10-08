package com.talayeman.gold

import com.talayeman.gold.domain.model.AssetType
import com.talayeman.gold.domain.model.MarketPrice
import com.talayeman.gold.domain.model.PriceType
import com.talayeman.gold.domain.usecase.PortfolioCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class CoinAutoValueTest {

    private val calc = PortfolioCalculator()

    // Test fixtures only: arbitrary numbers, not market data.
    private fun prices(vararg pairs: Pair<PriceType, String>) =
        pairs.associate { (t, p) -> t to MarketPrice(priceType = t, price = BigDecimal(p)) }

    @Test
    fun perCoinQuoteTimesQuantity() {
        val p = prices(PriceType.EMAMI to "1000", PriceType.QUARTER_COIN to "300")
        val emami = calc.autoCoinValue(AssetType.EMAMI, 3, 8133, 18, p)!!
        assertEquals(PortfolioCalculator.CoinValueBasis.PER_COIN, emami.basis)
        assertEquals(0, BigDecimal("3000").compareTo(emami.total))
        val quarter = calc.autoCoinValue(AssetType.QUARTER_COIN, 2, 0, 18, p)!!
        assertEquals(0, BigDecimal("600").compareTo(quarter.total))
    }

    @Test
    fun bankCoinWithoutQuoteStaysManual() {
        val p = prices(PriceType.GOLD_18K to "100")
        assertNull(calc.autoCoinValue(AssetType.BAHAR_AZADI, 1, 8133, 18, p))
    }

    @Test
    fun weightBasedCoinUsesGoldPriceAndPurity() {
        val p = prices(PriceType.GOLD_18K to "100")
        // 1.5 g × 2 × 100 × 18/18 = 300
        val v = calc.autoCoinValue(AssetType.PARSIAN, 2, 1500, 18, p)!!
        assertEquals(PortfolioCalculator.CoinValueBasis.BY_WEIGHT, v.basis)
        assertEquals(0, BigDecimal("300").compareTo(v.total))
    }

    @Test
    fun notACoinOrNoPriceReturnsNull() {
        assertNull(calc.autoCoinValue(AssetType.GOLD_18K, 1, 1000, 18, prices(PriceType.GOLD_18K to "100")))
        assertNull(calc.autoCoinValue(AssetType.CUSTOM_COIN, 1, 1000, 18, emptyMap()))
        assertNull(calc.autoCoinValue(AssetType.CUSTOM_COIN, 1, 0, 18, prices(PriceType.GOLD_18K to "100")))
    }
}
