package com.sukashawarma.pos.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class MigrationTest {

    private val dbName = "migration-test.db"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate1To2_preservesExistingSyncQueueRows() {
        var db = helper.createDatabase(dbName, 1)
        db.execSQL(
            "INSERT INTO sync_queue (queueId, actionType, payloadJson, createdAt) VALUES (1, 'CREATE_ORDER', '{}', 1000)"
        )
        db.close()

        db = helper.runMigrationsAndValidate(dbName, 2, true, AppDatabase.MIGRATION_1_2)

        val cursor = db.query("SELECT COUNT(*) FROM sync_queue")
        cursor.moveToFirst()
        assert(cursor.getInt(0) == 1) { "sync_queue row lost during migration" }
        cursor.close()

        val settingsCursor = db.query("SELECT COUNT(*) FROM local_kiosk_settings")
        settingsCursor.moveToFirst()
        assert(settingsCursor.getInt(0) == 0) { "local_kiosk_settings should exist and be empty" }
        settingsCursor.close()
        db.close()
    }

    @Test
    fun migrate2To3_preservesExistingLocalOrders() {
        var db = helper.createDatabase(dbName, 2)
        db.execSQL(
            """
            INSERT INTO local_orders
                (id, outletId, orderNumber, customerName, status, source, paymentMethod,
                 itemsJson, subtotal, discountAmount, totalAmount, amountReceived,
                 changeAmount, kitchenReceiptPrinted, createdAt, isPendingSync)
            VALUES
                ('order-1', 'outlet-a', 9001, 'Budi', 'PENDING', 'POS', 'CASH',
                 '[]', 10000.0, 0.0, 10000.0, 10000.0, 0.0, 0, 1000, 1)
            """.trimIndent()
        )
        db.close()

        db = helper.runMigrationsAndValidate(dbName, 3, true, AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)

        val cursor = db.query("SELECT COUNT(*), channel FROM local_orders WHERE id = 'order-1'")
        cursor.moveToFirst()
        assert(cursor.getInt(0) == 1) { "local_orders row lost during migration" }
        assert(cursor.isNull(1)) { "channel should default to null for pre-existing rows" }
        cursor.close()
        db.close()
    }

    @Test
    fun migrate3To4_preservesExistingMenuItemsAndAddsDescriptionColumn() {
        var db = helper.createDatabase(dbName, 3)
        db.execSQL(
            """
            INSERT INTO local_menu_items
                (id, categoryId, categoryName, outletId, name, price, strikePrice,
                 channelPricesJson, isAvailable, isAvailableOnline, availableOnlineChannelsJson,
                 prepTimeMinutes, imageUrl, isPackage, packageItemsJson)
            VALUES
                ('item-1', 'cat-1', 'Menu Utama', NULL, 'Shawarma Ayam', 25000.0, NULL,
                 NULL, 1, 1, NULL, 10, NULL, 0, NULL)
            """.trimIndent()
        )
        db.close()

        db = helper.runMigrationsAndValidate(
            dbName, 4, true,
            AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4
        )

        val cursor = db.query("SELECT COUNT(*), description FROM local_menu_items WHERE id = 'item-1'")
        cursor.moveToFirst()
        assert(cursor.getInt(0) == 1) { "local_menu_items row lost during migration" }
        assert(cursor.isNull(1)) { "description should default to null for pre-existing rows" }
        cursor.close()
        db.close()
    }

    @Test(expected = IllegalStateException::class)
    fun databaseTanpaMigrasiHarusGagalBukanMenghapusData() {
        // Database versi 4 dibuka sebagai versi 5 TANPA mendaftarkan MIGRATION_4_5.
        // Perilaku yang benar: melempar IllegalStateException.
        // Perilaku yang SALAH (fallbackToDestructiveMigration): menghapus semua tabel diam-diam.
        var db = helper.createDatabase(dbName, 4)
        db.execSQL(
            """
            INSERT INTO local_orders
                (id, outletId, orderNumber, customerName, status, source, paymentMethod,
                 itemsJson, subtotal, discountAmount, totalAmount, amountReceived,
                 changeAmount, kitchenReceiptPrinted, createdAt, isPendingSync, channel)
            VALUES
                ('order-penting', 'outlet-a', 12, 'Budi', 'PREPARING', 'POS', 'CASH',
                 '[]', 25000.0, 0.0, 25000.0, 25000.0, 0.0, 0, 1000, 1, NULL)
            """.trimIndent()
        )
        db.close()

        // Tanpa migrasi apa pun: harus melempar, bukan menghapus.
        helper.runMigrationsAndValidate(dbName, 5, true)
    }

    @Test
    fun migrate5To6_orderPendingJadiSyncStatePendingDanOutboxDapatKolomBaru() {
        var db = helper.createDatabase(dbName, 5)
        db.execSQL(
            """
            INSERT INTO local_orders
                (id, outletId, orderNumber, customerName, status, source, paymentMethod,
                 itemsJson, subtotal, discountAmount, totalAmount, amountReceived,
                 changeAmount, kitchenReceiptPrinted, customerReceiptPrinted,
                 cancellationStatus, cancellationUserName, createdAt, isPendingSync, channel)
            VALUES
                ('order-offline', 'outlet-a', 9001, 'Budi', 'PREPARING', 'POS', 'CASH',
                 '[]', 25000.0, 0.0, 25000.0, 25000.0, 0.0, 0, 0, NULL, NULL, 1000, 1, NULL),
                ('order-sudah-sync', 'outlet-a', 12, 'Sari', 'COMPLETED', 'POS', 'QRIS',
                 '[]', 30000.0, 0.0, 30000.0, 30000.0, 0.0, 1, 1, NULL, NULL, 2000, 0, NULL)
            """.trimIndent()
        )
        db.execSQL(
            "INSERT INTO sync_queue (queueId, actionType, payloadJson, createdAt) " +
                "VALUES (7, 'CREATE_ORDER', '{\"a\":1}', 500)"
        )
        db.close()

        db = helper.runMigrationsAndValidate(
            dbName, 6, true,
            AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6
        )

        val orders = db.query("SELECT id, syncState, dirtyFields FROM local_orders ORDER BY id")
        orders.moveToFirst()
        assert(orders.getString(0) == "order-offline")
        assert(orders.getString(1) == "PENDING") { "isPendingSync=1 harus jadi syncState PENDING" }
        assert(orders.getString(2) == "") { "dirtyFields harus kosong untuk baris lama" }
        orders.moveToNext()
        assert(orders.getString(0) == "order-sudah-sync")
        assert(orders.getString(1) == "SYNCED") { "isPendingSync=0 harus jadi syncState SYNCED" }
        orders.close()

        val queue = db.query(
            "SELECT idempotencyKey, entityId, status, attemptCount, nextAttemptAt, lastError " +
                "FROM sync_queue WHERE queueId = 7"
        )
        queue.moveToFirst()
        assert(queue.getString(0) == "legacy-7") { "baris outbox lama harus dapat idempotencyKey" }
        assert(queue.getString(1) == "") { "entityId default kosong" }
        assert(queue.getString(2) == "PENDING")
        assert(queue.getInt(3) == 0)
        assert(queue.getLong(4) == 0L)
        assert(queue.isNull(5))
        queue.close()
        db.close()
    }
}
