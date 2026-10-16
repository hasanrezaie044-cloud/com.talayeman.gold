package com.talayeman.gold.data.remote

import com.google.gson.JsonObject
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
 * Fetches live Iranian gold / coin prices from TGJU (tgju.org), the same data the site shows
 * (call*.tgju.org/ajax.json, section "current").
 *
 *  - TGJU quotes RIAL as comma-formatted strings ("190,542,000"); everything is converted to
 *    TOMAN (rial / 10), the unit used all over the app.
 *  - TGJU runs several identical data hosts (call4, call5, call1, call). They are tried in order,
 *    so one blocked / overloaded host does not break the update.
 *  - No API key needed. Parsing lives in [parseTgju] so it can be unit-tested without network.
 *
 * TGJU keys used: geram18 (18K gram), geram24 (24K gram), mesghal (mithqal), gold_melted_wholesale
 * (melted gold), sekee (Emami), sekeb (Bahar Azadi), nim (half), rob (quarter), gerami (gram coin).
 */
class MarketPriceService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()
) {

    /** Blocking; call from a background dispatcher. Throws if no TGJU host could be read. */
    fun fetchLivePrices(): List<MarketPrice> {
        var lastError: Exception? = null
        for (url in TGJU_URLS) {
            try {
                return parseTgju(httpGet(url), System.currentTimeMillis())
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw IllegalStateException(
            "دریافت قیمت از TGJU ناموفق بود: ${lastError?.message ?: "خطای نامشخص"}"
        )
    }

    private fun httpGet(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "fa-IR,fa;q=0.9,en;q=0.5")
            // TGJU rejects the default okhttp agent on some hosts; identify as a normal mobile browser.
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36 TalayeMan/1.3"
            )
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code} (${request.url.host})")
            }
            return response.body?.string()
                ?: throw IllegalStateException("پاسخ خالی از ${request.url.host}")
        }
    }

    companion object {
        const val SOURCE = "tgju"

        /** Identical TGJU data hosts, tried in this order. */
        private val TGJU_URLS = listOf(
            "https://call4.tgju.org/ajax.json",
            "https://call5.tgju.org/ajax.json",
            "https://call1.tgju.org/ajax.json",
            "https://call.tgju.org/ajax.json"
        )

        private val RIAL_PER_TOMAN = BigDecimal.TEN
        private val MITHQAL_GRAMS = BigDecimal("4.608")
        private val RATIO_MIN = BigDecimal("4.0")
        private val RATIO_MAX = BigDecimal("5.5")

        /**
         * Parses a TGJU ajax.json body into prices in TOMAN.
         * 24K and (if TGJU omits them) mithqal / melted gold are derived from the 18K price.
         * Throws [IllegalStateException] if the payload has no usable 18K gold price.
         */
        fun parseTgju(body: String, now: Long): List<MarketPrice> {
            val root = JsonParser.parseString(body).asJsonObject
            val current: JsonObject = root.getAsJsonObject("current")
                ?: throw IllegalStateException("TGJU: ساختار پاسخ نامعتبر است")

            fun toman(key: String): BigDecimal? {
                val obj = runCatching { current.getAsJsonObject(key) }.getOrNull() ?: return null
                val el = obj.get("p") ?: return null
                if (!el.isJsonPrimitive) return null
                val rial = runCatching {
                    BigDecimal(el.asString.replace(",", "").replace("٬", "").trim())
                }.getOrNull() ?: return null
                if (rial.signum() <= 0) return null
                return rial.divide(RIAL_PER_TOMAN, 0, RoundingMode.HALF_UP)
            }

            val g18 = toman("geram18")
                ?: throw IllegalStateException("TGJU: قیمت طلای ۱۸ عیار در پاسخ نیست")
            val g24 = toman("geram24")
                ?: g18.multiply(BigDecimal(24)).divide(BigDecimal(18), 0, RoundingMode.HALF_UP)

            // Guard against a unit mix-up: a mithqal is ~4.6 g, so its price must be ~4.6x the gram price.
            val derivedMithqal = g18.multiply(MITHQAL_GRAMS).setScale(0, RoundingMode.HALF_UP)
            val mesghal = toman("mesghal")?.takeIf {
                val ratio = it.divide(g18, 4, RoundingMode.HALF_UP)
                ratio >= RATIO_MIN && ratio <= RATIO_MAX
            } ?: derivedMithqal
            val melted = toman("gold_melted_wholesale")?.takeIf {
                val ratio = it.divide(g18, 4, RoundingMode.HALF_UP)
                ratio >= RATIO_MIN && ratio <= RATIO_MAX
            } ?: mesghal

            val prices = linkedMapOf<PriceType, BigDecimal>(
                PriceType.GOLD_18K to g18,
                PriceType.GOLD_24K to g24,
                PriceType.MITHQAL to mesghal,
                PriceType.MELTED_GOLD to melted
            )
            toman("sekee")?.let { prices[PriceType.EMAMI] = it }
            toman("sekeb")?.let { prices[PriceType.BAHAR_AZADI] = it }
            toman("nim")?.let { prices[PriceType.HALF_COIN] = it }
            toman("rob")?.let { prices[PriceType.QUARTER_COIN] = it }
            toman("gerami")?.let { prices[PriceType.GRAM_COIN] = it }

            return prices.map { (type, price) ->
                MarketPrice(
                    priceType = type,
                    price = price,
                    currency = Currency.TOMAN,
                    source = SOURCE,
                    isCached = false,
                    updatedAt = now
                )
            }
        }
    }
}
