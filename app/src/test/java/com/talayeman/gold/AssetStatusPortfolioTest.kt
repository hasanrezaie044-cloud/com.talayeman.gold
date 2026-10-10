package com.talayeman.gold

import com.talayeman.gold.domain.model.Asset
import com.talayeman.gold.domain.model.AssetStatus
import com.talayeman.gold.domain.model.AssetType
import com.talayeman.gold.domain.model.MarketPrice
import com.talayeman.gold.domain.model.PriceType
import com.talayeman.gold.domain.usecase.PortfolioCalculator
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class AssetStatusPortfolioTest {

    private fun asset(id: Long, status: AssetStatus) = Asset(
        id = id,
        name = "طلا $id",
        type = AssetType.GOLD_18K,
        quantity = 1,
        weightMg = 10_000,
        purity = 18,
        purchasePrice = BigDecimal(100),
        purchaseDate = 0L,
        totalPurchaseCost = BigDecimal(100),
        status = status
    )

    private val prices = mapOf(PriceType.GOLD_18K to MarketPrice(PriceType.GOLD_18K, BigDecimal(1000)))
    private val calculator = PortfolioCalculator()

    @Test
    fun soldAndGiftedAssetsAreNotPartOfThePortfolio() {
        val onlyActive = calculator.calculate(listOf(asset(1, AssetStatus.ACTIVE)), prices)
        val mixed = calculator.calculate(
            listOf(
                asset(1, AssetStatus.ACTIVE),
                asset(2, AssetStatus.SOLD),
                asset(3, AssetStatus.GIFTED)
            ),
            prices
        )
        assertEquals(1, mixed.assetCount)
        assertEquals(0, onlyActive.totalPurchaseCost.compareTo(mixed.totalPurchaseCost))
        assertEquals(0, onlyActive.currentEstimatedValue.compareTo(mixed.currentEstimatedValue))
        assertEquals(onlyActive.totalGoldWeightMg, mixed.totalGoldWeightMg)
    }

    @Test
    fun portfolioWithOnlySoldAssetsIsEmpty() {
        val summary = calculator.calculate(listOf(asset(1, AssetStatus.SOLD)), prices)
        assertEquals(0, summary.assetCount)
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.currentEstimatedValue))
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.totalPurchaseCost))
    }
}
