package com.talayeman.gold.data.repository

import com.talayeman.gold.data.local.dao.MarketPriceDao
import com.talayeman.gold.data.local.entity.MarketPriceEntity
import com.talayeman.gold.data.local.entity.PriceHistoryEntity
import com.talayeman.gold.data.remote.MarketPriceService
import com.talayeman.gold.domain.model.MarketPrice
import com.talayeman.gold.domain.model.PriceType
import com.talayeman.gold.util.MarketClosedException
import com.talayeman.gold.util.MarketHours
import com.talayeman.gold.util.MoneyUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.math.BigDecimal

class MarketRepository(
    private val marketPriceDao: MarketPriceDao,
    private val marketPriceService: MarketPriceService
) {
    fun getAllPrices(): Flow<List<MarketPrice>> =
        marketPriceDao.getAllPrices().map { list ->
            list.map { entity ->
                entity.toDomain().copy(
                    // Mark as cached if older than 2 hours
                    isCached = entity.isCached ||
                        (System.currentTimeMillis() - entity.updatedAt > 2 * 60 * 60 * 1000L)
                )
            }
        }

    suspend fun getPrice(type: PriceType): MarketPrice? =
        marketPriceDao.getPrice(type.name)?.toDomain()

    /** (time, price) points for one price type, oldest first; [days] = null means all stored history. */
    fun getHistory(type: PriceType, days: Int?): Flow<List<Pair<Long, BigDecimal>>> {
        val since = if (days == null) 0L else System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
        return marketPriceDao.getHistorySince(type.name, since).map { list ->
            list.map { it.recordedAt to MoneyUtils.parse(it.price) }
        }
    }

    /**
     * Fills the chart with TGJU's daily history for [type] (up to about a year). Only days that have
     * no stored point yet are added, so it is safe to call repeatedly. This reads past data only; it
     * never changes today's prices, so it is not blocked by the market hours.
     * Returns the number of added days.
     */
    suspend fun importHistory(type: PriceType): Result<Int> = withContext(Dispatchers.IO) {
        val key = MarketPriceService.HISTORY_KEYS[type] ?: return@withContext Result.success(0)
        try {
            val remote = marketPriceService.fetchHistory(key)
            if (remote.isEmpty()) return@withContext Result.success(0)
            val existingDays = marketPriceDao.getHistoryListSince(type.name, remote.first().first - 86_400_000L)
                .map { java.time.Instant.ofEpochMilli(it.recordedAt).atZone(MarketHours.ZONE).toLocalDate() }
                .toHashSet()
            val fresh = remote.filter { (t, _) ->
                java.time.Instant.ofEpochMilli(t).atZone(MarketHours.ZONE).toLocalDate() !in existingDays
            }.map { (t, price) ->
                PriceHistoryEntity(
                    priceType = type.name,
                    price = price.toPlainString(),
                    currency = "TOMAN",
                    source = "tgju-history",
                    recordedAt = t
                )
            }
            if (fresh.isNotEmpty()) marketPriceDao.insertHistoryAll(fresh)
            Result.success(fresh.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches live prices. Prices are only updated while the Iranian market is open
     * (11:00-18:00 Tehran time); otherwise NO request is made and a [MarketClosedException]
     * ("بازار بسته است") is returned - the stored prices stay unchanged.
     */
    suspend fun refreshPrices(): Result<List<MarketPrice>> = withContext(Dispatchers.IO) {
        if (!MarketHours.isOpen()) return@withContext Result.failure(MarketClosedException())
        try {
            val live = marketPriceService.fetchLivePrices()
            val entities = live.map { it.toEntity(isCached = false) }
            marketPriceDao.upsertAll(entities)
            val history = live.map {
                PriceHistoryEntity(
                    priceType = it.priceType.name,
                    price = it.price.toPlainString(),
                    currency = it.currency.name,
                    source = it.source,
                    recordedAt = it.updatedAt
                )
            }
            marketPriceDao.insertHistoryAll(history)
            // Keep about 2 years of points; older ones are not needed by any chart range.
            marketPriceDao.purgeOldHistory(System.currentTimeMillis() - 800L * 24 * 60 * 60 * 1000)
            Result.success(live)
        } catch (e: Exception) {
            // Keep existing cache; mark them as cached on next read via Flow
            Result.failure(e)
        }
    }

    suspend fun getCachedPricesSnapshot(): List<MarketPrice> = withContext(Dispatchers.IO) {
        marketPriceDao.getAllPrices().first().map { it.toDomain().copy(isCached = true) }
    }

    private fun MarketPriceEntity.toDomain() = MarketPrice(
        priceType = PriceType.fromString(priceType),
        price = MoneyUtils.parse(price),
        currency = com.talayeman.gold.domain.model.Currency.fromString(currency),
        source = source,
        isCached = isCached,
        updatedAt = updatedAt
    )

    private fun MarketPrice.toEntity(isCached: Boolean) = MarketPriceEntity(
        priceType = priceType.name,
        price = price.toPlainString(),
        currency = currency.name,
        source = source,
        isCached = isCached,
        updatedAt = updatedAt
    )
}
