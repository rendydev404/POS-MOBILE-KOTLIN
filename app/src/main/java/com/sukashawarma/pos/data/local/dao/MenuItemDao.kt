package com.sukashawarma.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sukashawarma.pos.data.local.entity.LocalKioskSettingEntity
import com.sukashawarma.pos.data.local.entity.LocalMenuItemEntity
import com.sukashawarma.pos.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MenuItemDao {
    @Query("SELECT * FROM local_menu_items ORDER BY name ASC")
    fun getAllMenuItems(): Flow<List<LocalMenuItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMenuItems(items: List<LocalMenuItemEntity>)

    @Query("DELETE FROM local_menu_items")
    suspend fun clearMenuItems()
}

@Dao
interface KioskSettingDao {
    @Query("SELECT * FROM local_kiosk_settings")
    fun getAllSettings(): Flow<List<LocalKioskSettingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(settings: List<LocalKioskSettingEntity>)
}

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue ORDER BY queueId ASC")
    suspend fun getAllQueueItems(): List<SyncQueueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueue(item: SyncQueueEntity)

    @Query("DELETE FROM sync_queue WHERE queueId = :queueId")
    suspend fun deleteQueue(queueId: Long)
}
