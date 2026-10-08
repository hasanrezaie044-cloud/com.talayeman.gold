package com.talayeman.gold.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = AssetEntity::class,
            parentColumns = ["id"],
            childColumns = ["assetId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["assetId"]), Index(value = ["type"])]
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val assetId: Long,
    val type: String, // PHOTO, INVOICE
    val filePath: String,
    val fileName: String,
    val mimeType: String? = null,
    val fileSize: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)
