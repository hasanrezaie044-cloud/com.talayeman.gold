package com.talayeman.gold.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "price_alerts")
data class PriceAlertEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val priceType: String, // GOLD_18K, EMAMI, PORTFOLIO, etc.
    val condition: String, // ABOVE, BELOW, PERCENT_UP, PERCENT_DOWN
    val threshold: String, // BigDecimal as string
    val isEnabled: Boolean = true,
    val lastTriggeredAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
