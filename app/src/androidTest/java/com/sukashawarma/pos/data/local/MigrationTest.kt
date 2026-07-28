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
}
