package com.talayeman.gold

import com.talayeman.gold.domain.model.AcquisitionType
import com.talayeman.gold.domain.model.Asset
import com.talayeman.gold.domain.model.AssetType
import com.talayeman.gold.domain.model.MarketPrice
import com.talayeman.gold.domain.model.PriceType
import com.talayeman.gold.domain.usecase.PortfolioCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class GiftAndGoldBankTest {

    private val calculator = PortfolioCalculator()

    private fun gold(id: Long, cost: Long, acquisition: AcquisitionType) = Asset(
        id = id, name = "طلا $id", type = AssetType.GOLD_18K, quantity = 1, weightMg = 10_000,
        purity = 18, purchasePrice = BigDecimal(cost), purchaseDate = 0L,
        totalPurchaseCost = BigDecimal(cost), acquisition = acquisition
    )

    private val prices = mapOf(
        PriceType.GOLD_18K to MarketPrice(PriceType.GOLD_18K, BigDecimal(1000)),
        // melted gold quote is per mithqal (4.608 g): 46,080,000 / 4.608 = 10,000,000 per gram
        PriceType.MELTED_GOLD to MarketPrice(PriceType.MELTED_GOLD, BigDecimal(46_080_000))
    )

    @Test
    fun giftCountsInPortfolioValueButNotInProfit() {
        val purchased = gold(1, 100, AcquisitionType.PURCHASED)   // worth 10 g x 1000 = 10,000
        val gift = gold(2, 0, AcquisitionType.GIFT_RECEIVED)      // worth 10,000, cost 0
        val s = calculator.calculate(listOf(purchased, gift), prices)

        assertEquals(0, BigDecimal(20_000).compareTo(s.currentEstimatedValue))   // gift is in the value
        assertEquals(0, BigDecimal(10_000).compareTo(s.giftValue))
        assertEquals(0, BigDecimal(100).compareTo(s.totalPurchaseCost))          // gift cost nothing
        assertEquals(0, BigDecimal(9_900).compareTo(s.profitLoss))               // profit excludes the gift
        assertEquals(2, s.assetCount)
    }

    @Test
    fun onlyGiftsGiveZeroProfitAndNoDivisionByZero() {
        val s = calculator.calculate(listOf(gold(1, 0, AcquisitionType.GIFT_RECEIVED)), prices)
        assertEquals(0, BigDecimal(10_000).compareTo(s.currentEstimatedValue))
        assertEquals(0, BigDecimal.ZERO.compareTo(s.profitLoss))
        assertEquals(0, BigDecimal.ZERO.compareTo(s.profitLossPercent))
    }

    @Test
    fun goldBankIsValuedWithMeltedGoldPerGram() {
        val bank = Asset(
            id = 3, name = "بانک طلا", type = AssetType.GOLD_BANK, quantity = 1, weightMg = 2_500, // 2.5 g
            purity = 18, purchasePrice = BigDecimal.ZERO, purchaseDate = 0L, totalPurchaseCost = BigDecimal.ZERO
        )
        // 2.5 g x 10,000,000 = 25,000,000
        assertEquals(0, BigDecimal(25_000_000).compareTo(calculator.estimateAssetValue(bank, prices)))
    }

    @Test
    fun goldBankFallsBackTo18kGramPriceWithoutMeltedQuote() {
        val bank = Asset(
            id = 3, name = "بانک طلا", type = AssetType.GOLD_BANK, quantity = 1, weightMg = 2_000,
            purity = 18, purchasePrice = BigDecimal.ZERO, purchaseDate = 0L, totalPurchaseCost = BigDecimal.ZERO
        )
        val only18k = mapOf(PriceType.GOLD_18K to MarketPrice(PriceType.GOLD_18K, BigDecimal(1000)))
        assertEquals(0, BigDecimal(2_000).compareTo(calculator.estimateAssetValue(bank, only18k)))
    }

    @Test
    fun goldBankIsGramAndMilligramOnly() {
        assertTrue(AssetType.GOLD_BANK.isGoldBank)
        assertEquals(2, AssetType.GOLD_BANK.allowedWeightUnits.size)
        assertFalse(AssetType.GOLD_18K.allowedWeightUnits.size == 2)
    }
}
