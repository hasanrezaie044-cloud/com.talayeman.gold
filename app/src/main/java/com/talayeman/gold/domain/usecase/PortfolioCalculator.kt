package com.talayeman.gold.domain.usecase

import com.talayeman.gold.domain.model.*
import com.talayeman.gold.util.MoneyUtils
import java.math.BigDecimal
import java.math.RoundingMode

class PortfolioCalculator {

    fun calculate(
        assets: List<Asset>,
        prices: Map<PriceType, MarketPrice>
    ): PortfolioSummary {
        if (assets.isEmpty()) return PortfolioSummary()

        var totalPurchase = BigDecimal.ZERO
        var totalCurrent = BigDecimal.ZERO
        var goldValue = BigDecimal.ZERO
        var coinValue = BigDecimal.ZERO
        var totalGoldMg = 0L
        var coinCount = 0

        for (asset in assets) {
            totalPurchase = totalPurchase.add(asset.totalPurchaseCost)
            val current = estimateAssetValue(asset, prices)
            totalCurrent = totalCurrent.add(current)
            if (asset.isCoin) {
                coinValue = coinValue.add(current)
                coinCount += asset.quantity
            } else {
                goldValue = goldValue.add(current)
                totalGoldMg += asset.weightMg * asset.quantity
            }
        }

        val pl = MoneyUtils.profitLoss(totalPurchase, totalCurrent)
        val plPercent = MoneyUtils.profitLossPercent(totalPurchase, totalCurrent)

        return PortfolioSummary(
            totalPurchaseCost = totalPurchase,
            currentEstimatedValue = totalCurrent,
            profitLoss = pl,
            profitLossPercent = plPercent,
            totalGoldWeightMg = totalGoldMg,
            totalCoinCount = coinCount,
            goldValue = goldValue,
            coinValue = coinValue,
            assetCount = assets.size
        )
    }

    fun estimateAssetValue(asset: Asset, prices: Map<PriceType, MarketPrice>): BigDecimal {
        return if (asset.isCoin) {
            estimateCoinValue(asset, prices)
        } else {
            estimateGoldValue(asset, prices)
        }
    }

    private fun estimateGoldValue(asset: Asset, prices: Map<PriceType, MarketPrice>): BigDecimal {
        val basis = goldPriceBasis(asset.purity, prices) ?: return BigDecimal.ZERO
        return MoneyUtils.estimateGoldValue(
            weightMg = asset.weightMg,
            quantity = asset.quantity,
            purity = asset.purity,
            pricePerGram18or24 = basis.price.price,
            is24kPrice = basis.is24k
        )
    }

    private fun estimateCoinValue(asset: Asset, prices: Map<PriceType, MarketPrice>): BigDecimal {
        val type = coinPriceTypeFor(asset.type)
        val unitPrice = type?.let { prices[it]?.price }
        return if (unitPrice != null) {
            unitPrice.multiply(BigDecimal(asset.quantity)).setScale(0, RoundingMode.HALF_UP)
        } else {
            // Fallback to gold weight valuation
            estimateGoldValue(asset, prices)
        }
    }

    /** Which per-gram gold price is used for weight-based valuation (same rule everywhere). */
    data class GoldPriceBasis(val price: MarketPrice, val is24k: Boolean)

    fun goldPriceBasis(purity: Int, prices: Map<PriceType, MarketPrice>): GoldPriceBasis? {
        val p18 = prices[PriceType.GOLD_18K]
        val p24 = prices[PriceType.GOLD_24K]
        return when {
            purity >= 24 && p24 != null -> GoldPriceBasis(p24, true)
            p18 != null -> GoldPriceBasis(p18, false)
            p24 != null -> GoldPriceBasis(p24, true)
            else -> null
        }
    }

    enum class CoinValueBasis {
        /** Market quote per coin × quantity. */
        PER_COIN,
        /** Weight × quantity × per-gram gold price × (purity / reference karat). */
        BY_WEIGHT
    }

    data class CoinAutoValue(
        val total: BigDecimal,
        val basis: CoinValueBasis,
        val priceType: PriceType,
        val referencePrice: BigDecimal,
        val referenceKarat: Int,
        val isCached: Boolean,
        val priceUpdatedAt: Long
    )

    /**
     * Automatic monetary value of a coin entry, using ONLY prices that already exist
     * in the app's market-price store (nothing is invented or hard-coded).
     *
     * 1. If the market provides a per-coin quote for this coin type → quote × quantity.
     * 2. Otherwise, for coins valued by gold content (Parsian, non-bank, custom coin) →
     *    weight × quantity × per-gram gold price × purity/karat, identical to the
     *    portfolio's weight-based valuation.
     * Returns null when the type is not a coin or the required data/price is missing,
     * in which case the amount stays manual.
     */
    fun autoCoinValue(
        type: AssetType,
        quantity: Int,
        weightMg: Long,
        purity: Int,
        prices: Map<PriceType, MarketPrice>
    ): CoinAutoValue? {
        if (!type.isCoin || quantity <= 0) return null

        val quoteType = coinPriceTypeFor(type)
        val quote = quoteType?.let { prices[it] }
        if (quoteType != null && quote != null && quote.price.signum() > 0) {
            return CoinAutoValue(
                total = quote.price.multiply(BigDecimal(quantity)).setScale(0, RoundingMode.HALF_UP),
                basis = CoinValueBasis.PER_COIN,
                priceType = quoteType,
                referencePrice = quote.price,
                referenceKarat = 0,
                isCached = quote.isCached,
                priceUpdatedAt = quote.updatedAt
            )
        }

        // Bank coins (Emami, Bahar Azadi, half, quarter, gram) trade on their own quote, which
        // includes the coin premium; valuing them by raw gold weight would be misleading, so
        // without a quote the amount stays manual.
        if (type in QUOTE_ONLY_COINS) return null

        if (weightMg <= 0 || purity <= 0) return null
        val basis = goldPriceBasis(purity, prices) ?: return null
        if (basis.price.price.signum() <= 0) return null
        val total = MoneyUtils.estimateGoldValue(
            weightMg = weightMg,
            quantity = quantity,
            purity = purity,
            pricePerGram18or24 = basis.price.price,
            is24kPrice = basis.is24k
        )
        return CoinAutoValue(
            total = total,
            basis = CoinValueBasis.BY_WEIGHT,
            priceType = basis.price.priceType,
            referencePrice = basis.price.price,
            referenceKarat = if (basis.is24k) 24 else 18,
            isCached = basis.price.isCached,
            priceUpdatedAt = basis.price.updatedAt
        )
    }

    companion object {
        private val QUOTE_ONLY_COINS = setOf(
            AssetType.EMAMI,
            AssetType.BAHAR_AZADI,
            AssetType.HALF_COIN,
            AssetType.QUARTER_COIN,
            AssetType.GRAM_COIN
        )

        /** Market quote (per coin) that corresponds to a coin asset type, if any. */
        fun coinPriceTypeFor(type: AssetType): PriceType? = when (type) {
            AssetType.EMAMI -> PriceType.EMAMI
            AssetType.BAHAR_AZADI -> PriceType.BAHAR_AZADI
            AssetType.HALF_COIN -> PriceType.HALF_COIN
            AssetType.QUARTER_COIN -> PriceType.QUARTER_COIN
            AssetType.GRAM_COIN -> PriceType.GRAM_COIN
            AssetType.PARSIAN -> PriceType.PARSIAN
            else -> null
        }
    }
}
