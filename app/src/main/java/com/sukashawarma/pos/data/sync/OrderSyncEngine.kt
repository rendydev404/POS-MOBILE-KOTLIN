package com.sukashawarma.pos.data.sync

import com.sukashawarma.pos.data.local.dao.OrderDao
import com.sukashawarma.pos.data.remote.SupabaseApi
import com.sukashawarma.pos.data.remote.dto.CreateOrderItemPayload
import com.sukashawarma.pos.data.remote.dto.CreateOrderPayload
import com.sukashawarma.pos.domain.model.OrderItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Fase 3: drains `local_orders` rows saved while offline (isPendingSync = true) to
 * Supabase once connectivity is back — the counterpart to the offline fallback in
 * POSManualOrderViewModel.processOrder(). One device syncing at a time is assumed
 * (single-cashier-per-outlet-tablet usage), so no cross-device locking is done.
 */
class OrderSyncEngine(
    private val orderDao: OrderDao,
    private val api: SupabaseApi
) {
    private val gson = Gson()

    /** Returns how many offline orders were successfully pushed to the server. */
    suspend fun syncPendingOrders(outletId: String): Int {
        if (outletId.isBlank()) return 0
        val pending = orderDao.getPendingSyncOrders(outletId)
        var syncedCount = 0

        for (entity in pending) {
            try {
                val createdAtIso = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(entity.createdAt))
                val payload = CreateOrderPayload(
                    id = entity.id,
                    outletId = entity.outletId,
                    customerName = entity.customerName,
                    status = entity.status.lowercase(),
                    source = entity.source.lowercase(),
                    paymentMethod = entity.paymentMethod.lowercase(),
                    discountAmount = entity.discountAmount,
                    totalAmount = entity.totalAmount,
                    amountReceived = entity.amountReceived,
                    changeAmount = entity.changeAmount,
                    createdAt = createdAtIso,
                    channel = entity.channel
                )

                val orderRes = api.createOrder(payload)
                if (!orderRes.isSuccessful || orderRes.body().isNullOrEmpty()) {
                    continue // leave isPendingSync = true, retry next pass
                }
                val serverOrderNumber = orderRes.body()!!.first().orderNumber

                val itemType = object : TypeToken<List<OrderItem>>() {}.type
                val items: List<OrderItem> = gson.fromJson(entity.itemsJson, itemType) ?: emptyList()
                val itemPayloads = items.map { item ->
                    CreateOrderItemPayload(
                        orderId = entity.id,
                        menuItemId = item.menuItemId,
                        menuItemName = item.name,
                        quantity = item.quantity,
                        unitPrice = item.unitPrice,
                        subtotal = item.subtotal
                    )
                }
                if (itemPayloads.isNotEmpty()) {
                    api.createOrderItems(itemPayloads)
                }

                orderDao.markSynced(entity.id, serverOrderNumber)
                syncedCount++
            } catch (e: Exception) {
                e.printStackTrace()
                // Network hiccup mid-loop — stop here, remaining orders retry next trigger.
                break
            }
        }
        return syncedCount
    }
}
