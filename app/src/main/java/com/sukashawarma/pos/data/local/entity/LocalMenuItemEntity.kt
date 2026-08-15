package com.sukashawarma.pos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
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

/**
 * Outbox: satu baris per mutasi kasir yang belum sampai ke server.
 *
 * Ditulis dalam transaksi Room yang SAMA dengan perubahan tabel datanya,
 * sehingga tidak mungkin UI berubah tanpa antrian sync ikut terisi.
 */
@Entity(
    tableName = "sync_queue",
    indices = [Index(value = ["idempotencyKey"], unique = true)]
)
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val queueId: Long = 0,

    /** CREATE_ORDER, UPDATE_ORDER_STATUS, MARK_RECEIPT_PRINTED, REQUEST_CANCELLATION, UPLOAD_PAYMENT_PROOF */
    val actionType: String,

    /** id baris data yang diubah (mis. orders.id). Dipakai untuk menjaga urutan per-entitas. */
    @ColumnInfo(defaultValue = "")
    val entityId: String,

    /**
     * Kunci anti-kirim-ganda. Untuk mutasi yang idempoten secara alami
     * (create order, set status) berbentuk "$actionType:$entityId" sehingga
     * mutasi berulang menimpa baris antrian lama alih-alih menumpuk.
     */
    @ColumnInfo(defaultValue = "")
    val idempotencyKey: String,

    val payloadJson: String,

    /** Nama [SyncState]: PENDING (menunggu/berulang) atau FAILED (ditolak server). */
    @ColumnInfo(defaultValue = "PENDING")
    val status: String = SyncState.PENDING.name,

    @ColumnInfo(defaultValue = "0")
    val attemptCount: Int = 0,

    /** Epoch millis paling awal boleh dicoba lagi (backoff). 0 = boleh sekarang. */
    @ColumnInfo(defaultValue = "0")
    val nextAttemptAt: Long = 0,

    val lastError: String? = null,

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
