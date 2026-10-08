package com.talayeman.gold.data.remote

import com.google.gson.JsonParser
import com.talayeman.gold.domain.model.Currency
import com.talayeman.gold.domain.model.MarketPrice
import com.talayeman.gold.domain.model.PriceType
import okhttp3.OkHttpClient
import okhttp3.Request
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.TimeUnit

/**
 * Fetches live Iranian gold/coin prices from free public JSON sources.
 *
 * Primary: HosseinOdd/Navasan-API gold.json (updated ~every 30 min)
 * Fallback: iran-market popular.json (18K + Emami minimum)
 *
 * All prices normalized to TOMAN. No API key required.
 */
class MarketPriceService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val NAVASAN_GOLD_URL =
            "https://raw.githubusercontent.com/HosseinOdd/Navasan-API/main/data/gold.json"
        private const val IRAN_MARKET_URL =
            "https://raw.githubusercontent.com/iran-market/iran-market.github.io/main/data/popular.json"
        private const val SOURCE_NAVASAN = "navasan-github"
        private const val SOURCE_IRAN_MARKET = "iran-market"
    }

    fun fetchLivePrices(): List<MarketPrice> {
        return try {
            fetchFromNavasan()
        } catch (e: Exception) {
            try {
                fetchFromIranMarket()
            } catch (e2: Exception) {
                throw IllegalStateException(
                    "دریافت قیمت بازار ناموفق بود: ${e.message} / ${e2.message}"
                )
            }
        }
    }

    private fun fetchFromNavasan(): List<MarketPrice> {
        val body = httpGet(NAVASAN_GOLD_URL)
        val root = JsonParser.parseString(body).asJsonObject
        val now = System.currentTimeMillis()
        val list = mutableListOf<MarketPrice>()

        fun add(type: PriceType, key: String) {
            val obj = root.getAsJsonObject(key) ?: return
            val valueEl = obj.get("value") ?: return
            val raw = when {
                valueEl.isJsonPrimitive && valueEl.asJsonPrimitive.isNumber ->
                    valueEl.asBigDecimal
                valueEl.isJsonPrimitive ->
                    runCatching {
                        BigDecimal(valueEl.asString.replace(",", "").trim())
                    }.getOrNull()
                else -> null
            } ?: return
            if (raw <= BigDecimal.ZERO) return
            list.add(
                MarketPrice(
                    priceType = type,
                    price = raw.setScale(0, RoundingMode.HALF_UP),
                    currency = Currency.TOMAN,
                    source = SOURCE_NAVASAN,
                    isCached = false,
                    updatedAt = now
                )
            )
        }

        add(PriceType.GOLD_18K, "18ayar")
        add(PriceType.MITHQAL, "abshodeh")
        add(PriceType.MELTED_GOLD, "abshodeh")
        add(PriceType.EMAMI, "sekkeh")
        add(PriceType.BAHAR_AZADI, "bahar")
        add(PriceType.HALF_COIN, "nim")
        add(PriceType.QUARTER_COIN, "rob")
        add(PriceType.GRAM_COIN, "gerami")

        val p18 = list.find { it.priceType == PriceType.GOLD_18K }?.price
        if (p18 != null) {
            val p24 = p18.multiply(BigDecimal(24))
                .divide(BigDecimal(18), 0, RoundingMode.HALF_UP)
            list.add(
                MarketPrice(
                    priceType = PriceType.GOLD_24K,
                    price = p24,
                    currency = Currency.TOMAN,
                    source = SOURCE_NAVASAN,
                    isCached = false,
                    updatedAt = now
                )
            )
        }

        if (list.isEmpty()) throw IllegalStateException("Navasan: no valid gold prices")
        return list
    }

    private fun fetchFromIranMarket(): List<MarketPrice> {
        val body = httpGet(IRAN_MARKET_URL)
        val root = JsonParser.parseString(body).asJsonObject
        val arr = root.getAsJsonArray("data")
            ?: throw IllegalStateException("iran-market: missing data array")
        val now = System.currentTimeMillis()
        val list = mutableListOf<MarketPrice>()

        val symbolMap = mapOf(
            "GOLD_18K_IRR" to PriceType.GOLD_18K,
            "COIN_EMAMI_IRR" to PriceType.EMAMI
        )

        for (el in arr) {
            val obj = el.asJsonObject
            val symbol = obj.get("symbol")?.asString ?: continue
            val type = symbolMap[symbol] ?: continue
            val priceEl = obj.get("price") ?: continue
            val raw = runCatching { priceEl.asBigDecimal }.getOrNull() ?: continue
            if (raw <= BigDecimal.ZERO) continue
            list.add(
                MarketPrice(
                    priceType = type,
                    price = raw.setScale(0, RoundingMode.HALF_UP),
                    currency = Currency.TOMAN,
                    source = SOURCE_IRAN_MARKET,
                    isCached = false,
                    updatedAt = now
                )
            )
        }

        val p18 = list.find { it.priceType == PriceType.GOLD_18K }?.price
        if (p18 != null) {
            list.add(
                MarketPrice(
                    priceType = PriceType.GOLD_24K,
                    price = p18.multiply(BigDecimal(24))
                        .divide(BigDecimal(18), 0, RoundingMode.HALF_UP),
                    currency = Currency.TOMAN,
                    source = SOURCE_IRAN_MARKET,
                    isCached = false,
                    updatedAt = now
                )
            )
            list.add(
                MarketPrice(
                    priceType = PriceType.MITHQAL,
                    price = p18.multiply(BigDecimal("4.608"))
                        .setScale(0, RoundingMode.HALF_UP),
                    currency = Currency.TOMAN,
                    source = SOURCE_IRAN_MARKET,
                    isCached = false,
                    updatedAt = now
                )
            )
        }

        if (list.isEmpty()) throw IllegalStateException("iran-market: no gold prices")
        return list
    }

    private fun httpGet(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "TalayeMan/1.1 (Android)")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code} for $url")
            }
            return response.body?.string()
                ?: throw IllegalStateException("Empty body from $url")
        }
    }
}
