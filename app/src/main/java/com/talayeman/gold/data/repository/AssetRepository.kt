package com.talayeman.gold.data.repository

import com.talayeman.gold.data.local.dao.AssetDao
import com.talayeman.gold.data.local.dao.AttachmentDao
import com.talayeman.gold.data.local.entity.AssetEntity
import com.talayeman.gold.data.local.entity.AttachmentEntity
import com.talayeman.gold.domain.model.*
import com.talayeman.gold.util.MoneyUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.math.BigDecimal

class AssetRepository(
    private val assetDao: AssetDao,
    private val attachmentDao: AttachmentDao
) {
    fun getAllAssets(): Flow<List<Asset>> =
        assetDao.getAllAssets().map { list -> list.map { it.toDomain() } }

    fun searchAssets(query: String): Flow<List<Asset>> =
        assetDao.searchAssets(query).map { list -> list.map { it.toDomain() } }

    fun getAssetsByType(type: AssetType): Flow<List<Asset>> =
        assetDao.getAssetsByType(type.name).map { list -> list.map { it.toDomain() } }

    suspend fun getAssetById(id: Long): Asset? =
        assetDao.getAssetById(id)?.toDomain()

    fun getAssetByIdFlow(id: Long): Flow<Asset?> =
        assetDao.getAssetByIdFlow(id).map { it?.toDomain() }

    suspend fun insertAsset(asset: Asset): Long {
        val id = assetDao.insert(asset.toEntity())
        return id
    }

    suspend fun updateAsset(asset: Asset) {
        assetDao.update(asset.toEntity())
    }

    suspend fun deleteAsset(id: Long) {
        attachmentDao.deleteForAsset(id)
        assetDao.deleteById(id)
    }

    suspend fun addAttachment(attachment: Attachment): Long =
        attachmentDao.insert(attachment.toEntity())

    fun getAttachments(assetId: Long): Flow<List<Attachment>> =
        attachmentDao.getAttachmentsForAsset(assetId).map { list -> list.map { it.toDomain() } }

    fun getAllInvoices(): Flow<List<Attachment>> =
        attachmentDao.getAllInvoices().map { list -> list.map { it.toDomain() } }

    suspend fun getAttachmentById(id: Long): Attachment? =
        attachmentDao.getById(id)?.toDomain()

    suspend fun getAttachmentsOnce(assetId: Long): List<Attachment> =
        attachmentDao.getAttachmentsForAsset(assetId).first().map { it.toDomain() }

    suspend fun deleteAttachment(id: Long) {
        attachmentDao.deleteById(id)
    }

    // Mappers
    private fun AssetEntity.toDomain() = Asset(
        id = id,
        name = name,
        type = AssetType.fromString(type),
        quantity = quantity,
        weightMg = weightMg,
        purity = purity,
        purchasePrice = MoneyUtils.parse(purchasePrice),
        purchaseDate = purchaseDate,
        seller = seller,
        makingCharge = MoneyUtils.parse(makingCharge),
        tax = MoneyUtils.parse(tax),
        otherFees = MoneyUtils.parse(otherFees),
        totalPurchaseCost = MoneyUtils.parse(totalPurchaseCost),
        notes = notes,
        isCoin = isCoin,
        coinType = coinType,
        createdAt = createdAt,
        updatedAt = updatedAt,
        status = AssetStatus.fromString(status),
        statusDate = statusDate,
        soldPrice = soldPrice?.let { MoneyUtils.parse(it) },
        statusNote = statusNote
    )

    private fun Asset.toEntity() = AssetEntity(
        id = id,
        name = name,
        type = type.name,
        quantity = quantity,
        weightMg = weightMg,
        purity = purity,
        purchasePrice = purchasePrice.toPlainString(),
        purchaseDate = purchaseDate,
        seller = seller,
        makingCharge = makingCharge.toPlainString(),
        tax = tax.toPlainString(),
        otherFees = otherFees.toPlainString(),
        totalPurchaseCost = totalPurchaseCost.toPlainString(),
        notes = notes,
        isCoin = isCoin,
        coinType = coinType,
        createdAt = createdAt,
        updatedAt = System.currentTimeMillis(),
        status = status.name,
        statusDate = statusDate,
        soldPrice = soldPrice?.toPlainString(),
        statusNote = statusNote
    )

    private fun AttachmentEntity.toDomain() = Attachment(
        id = id,
        assetId = assetId,
        type = AttachmentType.valueOf(type),
        filePath = filePath,
        fileName = fileName,
        mimeType = mimeType,
        fileSize = fileSize,
        createdAt = createdAt
    )

    private fun Attachment.toEntity() = AttachmentEntity(
        id = id,
        assetId = assetId,
        type = type.name,
        filePath = filePath,
        fileName = fileName,
        mimeType = mimeType,
        fileSize = fileSize,
        createdAt = createdAt
    )
}
