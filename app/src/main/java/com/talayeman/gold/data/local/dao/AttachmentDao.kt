package com.talayeman.gold.data.local.dao

import androidx.room.*
import com.talayeman.gold.data.local.entity.AttachmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE assetId = :assetId ORDER BY createdAt DESC")
    fun getAttachmentsForAsset(assetId: Long): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE assetId = :assetId AND type = :type ORDER BY createdAt DESC")
    fun getAttachmentsByType(assetId: Long, type: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE type = 'INVOICE' ORDER BY createdAt DESC")
    fun getAllInvoices(): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun getById(id: Long): AttachmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attachment: AttachmentEntity): Long

    @Delete
    suspend fun delete(attachment: AttachmentEntity)

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM attachments WHERE assetId = :assetId")
    suspend fun deleteForAsset(assetId: Long)
}
