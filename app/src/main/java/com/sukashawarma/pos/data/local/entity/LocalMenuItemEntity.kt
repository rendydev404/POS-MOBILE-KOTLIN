package com.sukashawarma.pos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_menu_items")
data class LocalMenuItemEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val categoryName: String,
    val outletId: String?,
    val name: String,
    val description: String?,
    val price: Double,
    val strikePrice: Double?,
    val channelPricesJson: String?,
    val isAvailable: Boolean,
    val isAvailableOnline: Boolean,
    val availableOnlineChannelsJson: String?,
    val prepTimeMinutes: Int,
    val imageUrl: String?,
    val isPackage: Boolean,
    val packageItemsJson: String?
)

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val queueId: Long = 0,
    val actionType: String, // CREATE_ORDER, UPDATE_ORDER_STATUS, CREATE_PETTY_CASH
    val payloadJson: String,
    val createdAt: Long = System.currentTimeMillis()
)

/** One row per resolved (already precedence-applied) kiosk_settings key — mirrors the
 *  web's Dexie cache shape (`db.kiosk_settings.put({ id: key, settings_data, synced_at })`). */
@Entity(tableName = "local_kiosk_settings")
data class LocalKioskSettingEntity(
    @PrimaryKey val settingKey: String,
    val jsonValue: String,
    val syncedAt: Long
)
