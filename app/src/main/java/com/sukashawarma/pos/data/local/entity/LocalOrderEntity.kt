package com.sukashawarma.pos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_orders")
data class LocalOrderEntity(
    @PrimaryKey val id: String,
    val outletId: String,
    val orderNumber: Int,
    val customerName: String,
    val status: String, // PENDING, PREPARING, READY, COMPLETED, CANCELLED
    val source: String, // POS, KIOSK, ONLINE
    val paymentMethod: String, // CASH, QRIS, CARD
    val itemsJson: String, // JSON String array of items
    val subtotal: Double,
    val discountAmount: Double,
    val totalAmount: Double,
    val amountReceived: Double,
    val changeAmount: Double,
    val kitchenReceiptPrinted: Boolean,
    val customerReceiptPrinted: Boolean,
    val cancellationStatus: String?,
    val cancellationUserName: String?,
    val createdAt: Long,
    val isPendingSync: Boolean = false,
    val channel: String? = null
)
