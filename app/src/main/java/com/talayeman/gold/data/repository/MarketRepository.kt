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

    /** (time, price) points of the last 7 days for one price type, oldest first. */
    fun getWeeklyHistory(type: PriceType): Flow<List<Pair<Long, BigDecimal>>> {
        val since = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        return marketPriceDao.getHistorySince(type.name, since).map { list ->
            list.map { it.recordedAt to MoneyUtils.parse(it.price) }
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
