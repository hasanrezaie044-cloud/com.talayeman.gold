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
        // Sold / gifted assets are no longer part of the portfolio or the user's capital.
        val assets = assets.filter { it.status == AssetStatus.ACTIVE }
        if (assets.isEmpty()) return PortfolioSummary()

        var totalPurchase = BigDecimal.ZERO
        var totalCurrent = BigDecimal.ZERO
        var goldValue = BigDecimal.ZERO
        var coinValue = BigDecimal.ZERO
        var totalGoldMg = 0L
        var coinCount = 0
        var giftValue = BigDecimal.ZERO

        for (asset in assets) {
            totalPurchase = totalPurchase.add(asset.totalPurchaseCost)
            val current = estimateAssetValue(asset, prices)
            totalCurrent = totalCurrent.add(current)
            // Gifts count in the portfolio value, but cost nothing, so they are not "profit".
            if (asset.acquisition == AcquisitionType.GIFT_RECEIVED) giftValue = giftValue.add(current)
            if (asset.isCoin) {
                coinValue = coinValue.add(current)
                coinCount += asset.quantity
            } else {
                goldValue = goldValue.add(current)
                totalGoldMg += asset.weightMg * asset.quantity
            }
        }

        // Profit / loss is computed on purchased assets only: a gift has no cost basis, so counting it
        // would show its whole value as profit. The gift value is reported separately (giftValue).
        val valueOfPurchased = totalCurrent.subtract(giftValue)
        val pl = MoneyUtils.profitLoss(totalPurchase, valueOfPurchased)
        val plPercent = MoneyUtils.profitLossPercent(totalPurchase, valueOfPurchased)

        return PortfolioSummary(
            totalPurchaseCost = totalPurchase,
            currentEstimatedValue = totalCurrent,
            profitLoss = pl,
            profitLossPercent = plPercent,
            totalGoldWeightMg = totalGoldMg,
            totalCoinCount = coinCount,
            goldValue = goldValue,
            coinValue = coinValue,
            assetCount = assets.size,
            giftValue = giftValue
        )
    }

    /** Melted gold per gram: the melted quote is per mithqal (4.608 g, 750 fineness). */
    data class MeltedGram(val perGram: BigDecimal, val priceType: PriceType, val isCached: Boolean)

    fun meltedGoldPerGram(prices: Map<PriceType, MarketPrice>): MeltedGram? {
        for (t in listOf(PriceType.MELTED_GOLD, PriceType.MITHQAL)) {
            val p = prices[t] ?: continue
            if (p.price.signum() > 0) {
                return MeltedGram(p.price.divide(MITHQAL_GRAMS, 2, RoundingMode.HALF_UP), t, p.isCached)
            }
        }
        // No melted / mithqal quote: 18K gram price is the same 750 fineness.
        val p18 = prices[PriceType.GOLD_18K]?.takeIf { it.price.signum() > 0 } ?: return null
        return MeltedGram(p18.price, PriceType.GOLD_18K, p18.isCached)
    }

    /** Gold bank: grams x quantity x melted-gold price per gram. */
    private fun estimateGoldBankValue(asset: Asset, prices: Map<PriceType, MarketPrice>): BigDecimal {
        val melted = meltedGoldPerGram(prices) ?: return BigDecimal.ZERO
        val grams = BigDecimal.valueOf(asset.weightMg).divide(BigDecimal(1000), 8, RoundingMode.HALF_UP)
        return melted.perGram.multiply(grams).multiply(BigDecimal(asset.quantity)).setScale(0, RoundingMode.HALF_UP)
    }

    fun estimateAssetValue(asset: Asset, prices: Map<PriceType, MarketPrice>): BigDecimal {
        if (asset.type == AssetType.GOLD_BANK) return estimateGoldBankValue(asset, prices)
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

    /** Market reference for the "unit price" field of the Add-Asset form. */
    data class MarketUnitPrice(
        /** Gold: price of ONE GRAM at the given purity. Coin: price of ONE coin. */
        val price: BigDecimal,
        val priceType: PriceType,
        val isCached: Boolean,
        val perCoin: Boolean
    )

    /**
     * Today's market price used to pre-fill the unit price for any asset type (not only coins):
     * - coins: per-coin quote (or gold-content value for Parsian / non-bank / custom coins),
     * - gold: per-gram price scaled to the asset's purity (same rule as the portfolio valuation).
     * Returns null for "other" assets or when no price is stored; then the price is typed manually.
     */
    fun marketUnitPrice(
        type: AssetType,
        purity: Int,
        unitWeightMg: Long,
        prices: Map<PriceType, MarketPrice>
    ): MarketUnitPrice? {
        if (type.isCoin) {
            val a = autoCoinValue(type, 1, unitWeightMg, purity, prices) ?: return null
            return MarketUnitPrice(a.total, a.priceType, a.isCached, perCoin = true)
        }
        if (type == AssetType.GOLD_BANK) {
            val m = meltedGoldPerGram(prices) ?: return null
            return MarketUnitPrice(m.perGram.setScale(0, RoundingMode.HALF_UP), m.priceType, m.isCached, perCoin = false)
        }
        if (!type.pricedPerGram || purity <= 0) return null
        val basis = goldPriceBasis(purity, prices) ?: return null
        if (basis.price.price.signum() <= 0) return null
        val perGram = MoneyUtils.estimateGoldValue(
            weightMg = 1000,
            quantity = 1,
            purity = purity,
            pricePerGram18or24 = basis.price.price,
            is24kPrice = basis.is24k
        )
        return MarketUnitPrice(perGram, basis.price.priceType, basis.price.isCached, perCoin = false)
    }

    companion object {
        /** One mithqal in grams (same constant as the weight converter). */
        val MITHQAL_GRAMS: BigDecimal = BigDecimal("4.608")

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
