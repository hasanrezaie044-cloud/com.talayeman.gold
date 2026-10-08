package com.talayeman.gold.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "market_prices",
    indices = [Index(value = ["priceType"], unique = true)]
)
data class MarketPriceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val priceType: String, // GOLD_18K, GOLD_24K, MITHQAL, EMAMI, BAHAR_AZADI, etc.
    val price: String, // BigDecimal as string (Toman)
    val currency: String = "TOMAN",
    val source: String = "unknown",
    val isCached: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
