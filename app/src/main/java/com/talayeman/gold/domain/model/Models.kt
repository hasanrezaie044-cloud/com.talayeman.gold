package com.talayeman.gold.domain.model

import java.math.BigDecimal

data class Asset(
    val id: Long = 0,
    val name: String,
    val type: AssetType,
    val quantity: Int = 1,
    val weightMg: Long,
    val purity: Int,
    val purchasePrice: BigDecimal,
    val purchaseDate: Long,
    val seller: String? = null,
    val makingCharge: BigDecimal = BigDecimal.ZERO,
    val tax: BigDecimal = BigDecimal.ZERO,
    val otherFees: BigDecimal = BigDecimal.ZERO,
    val totalPurchaseCost: BigDecimal,
    val notes: String? = null,
    val isCoin: Boolean = false,
    val coinType: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val photos: List<Attachment> = emptyList(),
    val invoices: List<Attachment> = emptyList()
)

data class Attachment(
    val id: Long = 0,
    val assetId: Long,
    val type: AttachmentType,
    val filePath: String,
    val fileName: String,
    val mimeType: String? = null,
    val fileSize: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)

enum class AttachmentType { PHOTO, INVOICE }

data class MarketPrice(
    val priceType: PriceType,
    val price: BigDecimal,
    val currency: Currency = Currency.TOMAN,
    val source: String = "unknown",
    val isCached: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

data class PortfolioSummary(
    val totalPurchaseCost: BigDecimal = BigDecimal.ZERO,
    val currentEstimatedValue: BigDecimal = BigDecimal.ZERO,
    val profitLoss: BigDecimal = BigDecimal.ZERO,
    val profitLossPercent: BigDecimal = BigDecimal.ZERO,
    val totalGoldWeightMg: Long = 0,
    val totalCoinCount: Int = 0,
    val goldValue: BigDecimal = BigDecimal.ZERO,
    val coinValue: BigDecimal = BigDecimal.ZERO,
    val assetCount: Int = 0
)

data class PriceAlert(
    val id: Long = 0,
    val priceType: String,
    val condition: AlertCondition,
    val threshold: BigDecimal,
    val isEnabled: Boolean = true,
    val lastTriggeredAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

enum class AlertCondition {
    ABOVE, BELOW, PERCENT_UP, PERCENT_DOWN
}
