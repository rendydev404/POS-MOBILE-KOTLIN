package com.sukashawarma.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sukashawarma.pos.data.local.entity.LocalOrderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {
    @Query("SELECT * FROM local_orders WHERE outletId = :outletId ORDER BY createdAt DESC")
    fun getOrdersByOutlet(outletId: String): Flow<List<LocalOrderEntity>>

    @Query("SELECT * FROM local_orders WHERE id = :orderId LIMIT 1")
    suspend fun getOrderById(orderId: String): LocalOrderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: LocalOrderEntity)

    /**
     * Menulis banyak pesanan dalam SATU transaksi.
     *
     * Menyisipkan satu per satu membuat Room mengirim sinyal perubahan berkali-kali,
     * dan tiap sinyal memicu komposisi ulang daftar pesanan, laporan, serta laci
     * kasir. Satu transaksi = satu sinyal.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrders(orders: List<LocalOrderEntity>)

    @Query("UPDATE local_orders SET status = :status WHERE id = :orderId")
    suspend fun updateOrderStatus(orderId: String, status: String)

    @Query("UPDATE local_orders SET status = :status, cashierName = :cashierName WHERE id = :orderId")
    suspend fun updateOrderStatusAndCashier(orderId: String, status: String, cashierName: String)

    @Query("UPDATE local_orders SET kitchenReceiptPrinted = :printed WHERE id = :orderId")
    suspend fun updateKitchenReceiptStatus(orderId: String, printed: Boolean)

    @Query("UPDATE local_orders SET customerReceiptPrinted = :printed WHERE id = :orderId")
    suspend fun updateCustomerReceiptStatus(orderId: String, printed: Boolean)

    @Query("UPDATE local_orders SET cancellationStatus = :status, cancellationUserName = :userName WHERE id = :orderId")
    suspend fun updateCancellationStatus(orderId: String, status: String?, userName: String?)

    @Query("SELECT MAX(orderNumber) FROM local_orders WHERE outletId = :outletId AND createdAt >= :startOfDayMillis AND createdAt <= :endOfDayMillis")
    suspend fun getMaxOrderNumberToday(outletId: String, startOfDayMillis: Long, endOfDayMillis: Long): Int?

    @Query("DELETE FROM local_orders WHERE id = :orderId")
    suspend fun deleteOrder(orderId: String)

    @Query(
        "SELECT * FROM local_orders WHERE outletId = :outletId AND syncState != 'SYNCED' " +
            "ORDER BY createdAt ASC"
    )
    suspend fun getUnsyncedOrders(outletId: String): List<LocalOrderEntity>

    @Query(
        "SELECT COUNT(*) FROM local_orders WHERE outletId = :outletId AND syncState = 'PENDING'"
    )
    fun getPendingSyncCountFlow(outletId: String): Flow<Int>

    @Query("UPDATE local_orders SET syncState = :syncState WHERE id = :orderId")
    suspend fun setSyncState(orderId: String, syncState: String)

    @Query("UPDATE local_orders SET dirtyFields = :dirtyFields WHERE id = :orderId")
    suspend fun setDirtyFields(orderId: String, dirtyFields: String)

    @Query("SELECT dirtyFields FROM local_orders WHERE id = :orderId")
    suspend fun getDirtyFields(orderId: String): String?

    @Query(
        "UPDATE local_orders SET orderNumber = :serverOrderNumber, syncState = 'SYNCED', " +
            "dirtyFields = '', isSyncedFromOffline = 1 WHERE id = :orderId"
    )
    suspend fun markSynced(orderId: String, serverOrderNumber: Int)

    /**
     * Tanpa filter outletId, omzet offline ikut menjumlahkan pesanan outlet lain
     * yang kebetulan masih tersisa di cache Room setelah pindah outlet.
     */
    @Query(
        "SELECT * FROM local_orders WHERE outletId = :outletId " +
            "AND createdAt >= :startMillis AND createdAt <= :endMillis ORDER BY createdAt DESC"
    )
    suspend fun getOrdersByDateRange(
        outletId: String,
        startMillis: Long,
        endMillis: Long
    ): List<LocalOrderEntity>

    /**
     * Versi reaktif dari [getOrdersByDateRange].
     *
     * Pembacaan sekali-jalan berlomba dengan proses yang menulis Room: keduanya
     * dipicu event yang sama, dan pembacanya hampir selalu menang karena penulis
     * harus menunggu HTTP dulu — hasilnya angka basi. Room mengirim ulang lewat
     * Flow ini setiap kali tabelnya berubah, jadi tidak ada lagi balapan.
     */
    @Query(
        "SELECT * FROM local_orders WHERE outletId = :outletId " +
            "AND createdAt >= :startMillis AND createdAt <= :endMillis ORDER BY createdAt DESC"
    )
    fun observeOrdersByDateRange(
        outletId: String,
        startMillis: Long,
        endMillis: Long
    ): Flow<List<LocalOrderEntity>>

    @Query("SELECT id FROM local_orders WHERE outletId = :outletId AND isSyncedFromOffline = 1")
    suspend fun getSyncedFromOfflineIds(outletId: String): List<String>

    /**
     * Snapshot sekali jalan untuk pembanding saat sync.
     *
     * Menggantikan pemanggilan getOrderById() per pesanan: dulu satu sync dengan
     * 200 pesanan berarti 200 kueri terpisah, setiap 15 detik.
     */
    @Query("SELECT * FROM local_orders WHERE outletId = :outletId")
    suspend fun getAllOrdersByOutlet(outletId: String): List<LocalOrderEntity>
}
