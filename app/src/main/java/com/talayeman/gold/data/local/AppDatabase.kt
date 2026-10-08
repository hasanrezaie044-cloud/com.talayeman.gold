package com.talayeman.gold.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.talayeman.gold.data.local.converter.Converters
import com.talayeman.gold.data.local.dao.*
import com.talayeman.gold.data.local.entity.*

@Database(
    entities = [
        AssetEntity::class,
        AttachmentEntity::class,
        MarketPriceEntity::class,
        PriceHistoryEntity::class,
        PriceAlertEntity::class,
        AppSettingsEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun assetDao(): AssetDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun marketPriceDao(): MarketPriceDao
    abstract fun priceAlertDao(): PriceAlertDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        const val DB_NAME = "gold_management.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
