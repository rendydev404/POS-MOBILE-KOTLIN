package com.sukashawarma.pos.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sukashawarma.pos.data.local.dao.KioskSettingDao
import com.sukashawarma.pos.data.local.dao.MenuItemDao
import com.sukashawarma.pos.data.local.dao.OrderDao
import com.sukashawarma.pos.data.local.dao.SyncQueueDao
import com.sukashawarma.pos.data.local.entity.LocalKioskSettingEntity
import com.sukashawarma.pos.data.local.entity.LocalMenuItemEntity
import com.sukashawarma.pos.data.local.entity.LocalOrderEntity
import com.sukashawarma.pos.data.local.entity.SyncQueueEntity

@Database(
    entities = [
        LocalOrderEntity::class,
        LocalMenuItemEntity::class,
        SyncQueueEntity::class,
        LocalKioskSettingEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun orderDao(): OrderDao
    abstract fun menuItemDao(): MenuItemDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun kioskSettingDao(): KioskSettingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // sync_queue holds offline orders not yet uploaded — a destructive migration
        // here would silently delete unsynced sales, so 1->2 is handled explicitly.
        // (The plan this ships under assumed the live schema was already at version 2
        // and wrote this as Migration(2, 3); the actual pre-existing @Database version
        // here is 1, so the same column/table changes are applied as Migration(1, 2).)
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE local_menu_items ADD COLUMN outletId TEXT")
                db.execSQL("ALTER TABLE local_menu_items ADD COLUMN strikePrice REAL")
                db.execSQL("ALTER TABLE local_menu_items ADD COLUMN channelPricesJson TEXT")
                db.execSQL("ALTER TABLE local_menu_items ADD COLUMN isAvailableOnline INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE local_menu_items ADD COLUMN availableOnlineChannelsJson TEXT")
                db.execSQL("ALTER TABLE local_menu_items ADD COLUMN isPackage INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE local_menu_items ADD COLUMN packageItemsJson TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS local_kiosk_settings (
                        settingKey TEXT NOT NULL PRIMARY KEY,
                        jsonValue TEXT NOT NULL,
                        syncedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        // local_orders holds offline-created orders (isPendingSync) — same reasoning
        // as MIGRATION_1_2: never drop this table via a destructive migration.
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE local_orders ADD COLUMN channel TEXT")
                } catch (e: Exception) {
                    // Ignore if column already exists
                }
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pos_sukashawarma.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                 .fallbackToDestructiveMigration()
                 .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
