package com.talayeman.gold.data.local.dao

import androidx.room.*
import com.talayeman.gold.data.local.entity.MarketPriceEntity
import com.talayeman.gold.data.local.entity.PriceHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MarketPriceDao {
    @Query("SELECT * FROM market_prices")
    fun getAllPrices(): Flow<List<MarketPriceEntity>>

    @Query("SELECT * FROM market_prices WHERE priceType = :type LIMIT 1")
    suspend fun getPrice(type: String): MarketPriceEntity?

    @Query("SELECT * FROM market_prices WHERE priceType = :type LIMIT 1")
    fun getPriceFlow(type: String): Flow<MarketPriceEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(price: MarketPriceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(prices: List<MarketPriceEntity>)

    @Query("DELETE FROM market_prices")
    suspend fun clearAll()

    // History
    @Query("SELECT * FROM price_history WHERE priceType = :type ORDER BY recordedAt DESC LIMIT :limit")
    fun getHistory(type: String, limit: Int = 100): Flow<List<PriceHistoryEntity>>

    @Insert
    suspend fun insertHistory(entry: PriceHistoryEntity)

    @Insert
    suspend fun insertHistoryAll(entries: List<PriceHistoryEntity>)

    @Query("DELETE FROM price_history WHERE recordedAt < :before")
    suspend fun purgeOldHistory(before: Long)
}
