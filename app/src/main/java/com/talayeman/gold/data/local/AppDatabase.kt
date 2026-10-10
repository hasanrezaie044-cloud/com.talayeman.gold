package com.talayeman.gold.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 2,
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

        /** v1 -> v2: sold / gifted status for assets. Existing rows become ACTIVE; no data is lost. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE assets ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE'")
                db.execSQL("ALTER TABLE assets ADD COLUMN statusDate INTEGER")
                db.execSQL("ALTER TABLE assets ADD COLUMN soldPrice TEXT")
                db.execSQL("ALTER TABLE assets ADD COLUMN statusNote TEXT")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                    // Never wipe user data silently: every schema change needs a Migration.
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
