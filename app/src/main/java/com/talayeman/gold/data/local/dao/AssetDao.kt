package com.talayeman.gold.data.local.dao

import androidx.room.*
import com.talayeman.gold.data.local.entity.AssetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetDao {
    @Query("SELECT * FROM assets ORDER BY updatedAt DESC")
    fun getAllAssets(): Flow<List<AssetEntity>>

    @Query("SELECT * FROM assets WHERE id = :id")
    suspend fun getAssetById(id: Long): AssetEntity?

    @Query("SELECT * FROM assets WHERE id = :id")
    fun getAssetByIdFlow(id: Long): Flow<AssetEntity?>

    @Query("SELECT * FROM assets WHERE name LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun searchAssets(query: String): Flow<List<AssetEntity>>

    @Query("SELECT * FROM assets WHERE type = :type ORDER BY updatedAt DESC")
    fun getAssetsByType(type: String): Flow<List<AssetEntity>>

    @Query("SELECT * FROM assets WHERE isCoin = 1 ORDER BY updatedAt DESC")
    fun getCoins(): Flow<List<AssetEntity>>

    @Query("SELECT * FROM assets WHERE isCoin = 0 ORDER BY updatedAt DESC")
    fun getGoldAssets(): Flow<List<AssetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(asset: AssetEntity): Long

    @Update
    suspend fun update(asset: AssetEntity)

    @Delete
    suspend fun delete(asset: AssetEntity)

    @Query("DELETE FROM assets WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM assets WHERE status = 'ACTIVE'")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM assets WHERE isCoin = 1 AND status = 'ACTIVE'")
    suspend fun getCoinCount(): Int

    @Query("SELECT SUM(weightMg * quantity) FROM assets WHERE isCoin = 0 AND status = 'ACTIVE'")
    suspend fun getTotalGoldWeightMg(): Long?

    @Query("SELECT SUM(CAST(totalPurchaseCost AS REAL)) FROM assets WHERE status = 'ACTIVE'")
    suspend fun getTotalPurchaseCost(): Double?
}
