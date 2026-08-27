package com.sukashawarma.pos.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sukashawarma.pos.data.local.dao.ImageCacheDao
import com.sukashawarma.pos.data.local.dao.KioskSettingDao
import com.sukashawarma.pos.data.local.dao.MenuItemDao
import com.sukashawarma.pos.data.local.dao.OrderDao
import com.sukashawarma.pos.data.local.dao.SyncQueueDao
import com.sukashawarma.pos.data.local.entity.LocalImageCacheEntity
import com.sukashawarma.pos.data.local.entity.LocalKioskSettingEntity
import com.sukashawarma.pos.data.local.entity.LocalMenuItemEntity
import com.sukashawarma.pos.data.local.entity.LocalOrderEntity
import com.sukashawarma.pos.data.local.entity.SyncQueueEntity

@Database(
    entities = [
        LocalOrderEntity::class,
        LocalMenuItemEntity::class,
        SyncQueueEntity::class,
        LocalKioskSettingEntity::class,
        LocalImageCacheEntity::class
    ],
    version = 11,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun orderDao(): OrderDao
    abstract fun menuItemDao(): MenuItemDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun kioskSettingDao(): KioskSettingDao
    abstract fun imageCacheDao(): ImageCacheDao

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

        // sync_queue/local_orders hold unsynced sales — same reasoning as MIGRATION_1_2
        // and MIGRATION_2_3: a destructive migration here would silently drop them, so
        // the new column is added explicitly instead.
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE local_menu_items ADD COLUMN description TEXT")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE local_orders ADD COLUMN customerReceiptPrinted INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE local_orders ADD COLUMN cancellationStatus TEXT")
                    db.execSQL("ALTER TABLE local_orders ADD COLUMN cancellationUserName TEXT")
                } catch (e: Exception) {
                    // Ignore if columns already exist
                }
            }
        }

        /**
         * v6: local_orders.isPendingSync (boolean) -> syncState (enum string) + dirtyFields,
         * dan sync_queue mendapat kolom yang dibutuhkan SyncEngine.
         *
         * local_orders dibuat ulang karena SQLite di minSdk 26 tidak punya DROP COLUMN.
         * Datanya DISALIN, bukan dibuang — tabel ini menyimpan penjualan yang belum naik.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE local_orders_baru (
                        id TEXT NOT NULL PRIMARY KEY,
                        outletId TEXT NOT NULL,
                        orderNumber INTEGER NOT NULL,
                        customerName TEXT NOT NULL,
                        status TEXT NOT NULL,
                        source TEXT NOT NULL,
                        paymentMethod TEXT NOT NULL,
                        itemsJson TEXT NOT NULL,
                        subtotal REAL NOT NULL,
                        discountAmount REAL NOT NULL,
                        totalAmount REAL NOT NULL,
                        amountReceived REAL NOT NULL,
                        changeAmount REAL NOT NULL,
                        kitchenReceiptPrinted INTEGER NOT NULL,
                        customerReceiptPrinted INTEGER NOT NULL,
                        cancellationStatus TEXT,
                        cancellationUserName TEXT,
                        createdAt INTEGER NOT NULL,
                        syncState TEXT NOT NULL DEFAULT 'SYNCED',
                        dirtyFields TEXT NOT NULL DEFAULT '',
                        channel TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO local_orders_baru (
                        id, outletId, orderNumber, customerName, status, source, paymentMethod,
                        itemsJson, subtotal, discountAmount, totalAmount, amountReceived,
                        changeAmount, kitchenReceiptPrinted, customerReceiptPrinted,
                        cancellationStatus, cancellationUserName, createdAt,
                        syncState, dirtyFields, channel
                    )
                    SELECT
                        id, outletId, orderNumber, customerName, status, source, paymentMethod,
                        itemsJson, subtotal, discountAmount, totalAmount, amountReceived,
                        changeAmount, kitchenReceiptPrinted, customerReceiptPrinted,
                        cancellationStatus, cancellationUserName, createdAt,
                        CASE WHEN isPendingSync = 1 THEN 'PENDING' ELSE 'SYNCED' END,
                        '', channel
                    FROM local_orders
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE local_orders")
                db.execSQL("ALTER TABLE local_orders_baru RENAME TO local_orders")

                db.execSQL("ALTER TABLE sync_queue ADD COLUMN entityId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE sync_queue ADD COLUMN idempotencyKey TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE sync_queue ADD COLUMN status TEXT NOT NULL DEFAULT 'PENDING'")
                db.execSQL("ALTER TABLE sync_queue ADD COLUMN attemptCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sync_queue ADD COLUMN nextAttemptAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sync_queue ADD COLUMN lastError TEXT")
                // Baris outbox lama (kalau ada) belum punya kunci; beri kunci unik dari queueId
                // supaya unique index di bawah tidak menolaknya.
                db.execSQL("UPDATE sync_queue SET idempotencyKey = 'legacy-' || queueId")
                db.execSQL(
                    "CREATE UNIQUE INDEX index_sync_queue_idempotencyKey ON sync_queue (idempotencyKey)"
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE local_orders ADD COLUMN isSyncedFromOffline INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE local_orders ADD COLUMN localPaymentProofPath TEXT")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE local_orders ADD COLUMN notes TEXT")
                } catch (e: Exception) {
                    // Ignore if column already exists
                }
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE local_orders ADD COLUMN effectiveReleaseTime INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE local_orders ADD COLUMN cashierName TEXT")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE local_orders ADD COLUMN promoSubsidy REAL NOT NULL DEFAULT 0.0")
                } catch (e: Exception) {
                    // Ignore if column already exists
                }
            }
        }

        // Indeks foto permanen (menu, dst) — lihat LocalImageCacheEntity untuk kenapa
        // ini terpisah dari cacheDir bawaan Coil/Android.
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS local_image_cache (
                        remoteUrl TEXT NOT NULL PRIMARY KEY,
                        localPath TEXT NOT NULL,
                        sizeBytes INTEGER NOT NULL,
                        lastAccessedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pos_sukashawarma.db"
                ).addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                    MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
                    MIGRATION_9_10, MIGRATION_10_11
                )
                 // TIDAK ADA fallbackToDestructiveMigration di sini, dan jangan pernah
                 // ditambahkan: local_orders + sync_queue menyimpan penjualan yang belum
                 // naik ke server. Lebih baik aplikasi gagal terbuka dan kita perbaiki
                 // migrasinya daripada omzet satu hari hilang tanpa jejak.
                 .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
