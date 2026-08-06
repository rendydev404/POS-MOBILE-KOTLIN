package com.sukashawarma.pos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Status sinkronisasi satu baris data lokal terhadap server. */
enum class SyncState {
    /** Sudah sama dengan server. */
    SYNCED,

    /** Ada perubahan lokal yang belum naik; masih punya baris di sync_queue. */
    PENDING,

    /** Server MENOLAK perubahan ini (bukan gangguan jaringan). Butuh perhatian kasir. */
    FAILED
}

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
    /** Nama [SyncState]. Menggantikan kolom isPendingSync yang lama. */
    val syncState: String = SyncState.SYNCED.name,
    /**
     * Daftar kolom yang diubah kasir dan BELUM naik ke server, dipisah koma
     * (mis. "status,customerReceiptPrinted"). Selama tidak kosong, baris ini
     * tidak boleh ditimpa oleh data tarikan server — inilah bentuk konkret
     * aturan "data transaksi kasir lokal menang".
     */
    val dirtyFields: String = "",
    val channel: String? = null,
    val isSyncedFromOffline: Boolean = false,
    val localPaymentProofPath: String? = null,
    val notes: String? = null
)
