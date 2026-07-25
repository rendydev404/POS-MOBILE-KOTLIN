package com.sukashawarma.pos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_menu_items")
data class LocalMenuItemEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val categoryName: String,
    val name: String,
    val price: Double,
    val isAvailable: Boolean,
    val prepTimeMinutes: Int,
    val imageUrl: String?
)

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val queueId: Long = 0,
    val actionType: String, // CREATE_ORDER, UPDATE_ORDER_STATUS, CREATE_PETTY_CASH
    val payloadJson: String,
    val createdAt: Long = System.currentTimeMillis()
)
