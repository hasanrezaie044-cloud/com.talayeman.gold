package com.talayeman.gold.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "price_history",
    indices = [Index(value = ["priceType", "recordedAt"])]
)
data class PriceHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val priceType: String,
    val price: String,
    val currency: String = "TOMAN",
    val source: String = "unknown",
    val recordedAt: Long = System.currentTimeMillis()
)
