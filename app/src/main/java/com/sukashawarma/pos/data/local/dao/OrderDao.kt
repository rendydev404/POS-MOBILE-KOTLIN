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

    @Query("UPDATE local_orders SET status = :status WHERE id = :orderId")
    suspend fun updateOrderStatus(orderId: String, status: String)

    @Query("UPDATE local_orders SET kitchenReceiptPrinted = :printed WHERE id = :orderId")
    suspend fun updateKitchenReceiptStatus(orderId: String, printed: Boolean)

    @Query("SELECT MAX(orderNumber) FROM local_orders WHERE outletId = :outletId AND orderNumber >= 9001")
    suspend fun getMaxOfflineOrderNumber(outletId: String): Int?

    @Query("SELECT MAX(orderNumber) FROM local_orders WHERE outletId = :outletId AND orderNumber < 9001")
    suspend fun getMaxServerOrderNumber(outletId: String): Int?

    @Query("DELETE FROM local_orders WHERE id = :orderId")
    suspend fun deleteOrder(orderId: String)

    @Query("SELECT * FROM local_orders WHERE outletId = :outletId AND isPendingSync = 1 ORDER BY createdAt ASC")
    suspend fun getPendingSyncOrders(outletId: String): List<LocalOrderEntity>

    @Query("SELECT COUNT(*) FROM local_orders WHERE outletId = :outletId AND isPendingSync = 1")
    fun getPendingSyncCountFlow(outletId: String): Flow<Int>

    @Query("UPDATE local_orders SET orderNumber = :serverOrderNumber, isPendingSync = 0 WHERE id = :orderId")
    suspend fun markSynced(orderId: String, serverOrderNumber: Int)
}
