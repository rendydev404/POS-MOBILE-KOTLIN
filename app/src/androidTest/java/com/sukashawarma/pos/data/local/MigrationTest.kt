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
}
