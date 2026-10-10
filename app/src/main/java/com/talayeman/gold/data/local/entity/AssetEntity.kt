package com.talayeman.gold.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "assets",
    indices = [
        Index(value = ["type"]),
        Index(value = ["name"]),
        Index(value = ["purchaseDate"])
    ]
)
data class AssetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: String, // AssetType enum name
    val quantity: Int = 1,
    val weightMg: Long, // Canonical weight in milligrams
    val purity: Int, // Karat: 18, 24, or custom
    val purchasePrice: String, // BigDecimal as string for precision
    val purchaseDate: Long, // Epoch millis
    val seller: String? = null,
    val makingCharge: String = "0",
    val tax: String = "0",
    val otherFees: String = "0",
    val totalPurchaseCost: String, // Calculated
    val notes: String? = null,
    val isCoin: Boolean = false,
    val coinType: String? = null, // Emami, BaharAzadi, etc.
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    // --- added in DB version 2 ---
    val status: String = "ACTIVE", // AssetStatus enum name
    val statusDate: Long? = null,
    val soldPrice: String? = null, // BigDecimal as string
    val statusNote: String? = null
)
