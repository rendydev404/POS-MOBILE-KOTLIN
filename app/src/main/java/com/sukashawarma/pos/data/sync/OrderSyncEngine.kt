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
import java.io.File
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody

/**
 * Fase 3: drains `local_orders` rows saved while offline (isPendingSync = true) to
 * Supabase once connectivity is back — the counterpart to the offline fallback in
 * POSManualOrderViewModel.submitOrder(). One device syncing at a time is assumed
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
        val pending = orderDao.getUnsyncedOrders(outletId)
        var syncedCount = 0

        for (entity in pending) {
            try {
                val createdAtIso = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(entity.createdAt))
                val payload = CreateOrderPayload(
                    id = entity.id,
                    orderNumber = entity.orderNumber,
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
                    continue // leave syncState != SYNCED, retry next pass
                }
                val serverOrderNumber = orderRes.body()!!.first().orderNumber

                val itemType = object : TypeToken<List<OrderItem>>() {}.type
                val items: List<OrderItem> = gson.fromJson(entity.itemsJson, itemType) ?: emptyList()
                val itemPayloads = items.map { item ->
                    CreateOrderItemPayload(
                        orderId = entity.id,
                        menuItemId = item.menuItemId,
                        menuItemName = item.encodedMenuItemName(),
                        quantity = item.quantity,
                        unitPrice = item.unitPrice,
                        subtotal = item.subtotal
                    )
                }
                if (itemPayloads.isNotEmpty()) {
                    api.createOrderItems(itemPayloads)
                }

                orderDao.markSynced(entity.id, serverOrderNumber)
                
                // Upload local payment proof if it exists
                entity.localPaymentProofPath?.let { path ->
                    try {
                        val file = File(path)
                        if (file.exists()) {
                            val fileName = "${entity.outletId}_${serverOrderNumber}_${java.time.LocalDate.now()}.jpg"
                            val objectPath = "payment_proofs/$fileName"
                            val body = file.asRequestBody("image/jpeg".toMediaTypeOrNull())

                            val uploadRes = api.uploadPaymentProof(objectPath = objectPath, contentType = "image/jpeg", file = body)
                            if (uploadRes.isSuccessful) {
                                val publicUrl = "${com.sukashawarma.pos.data.remote.SupabaseClient.BASE_URL}storage/v1/object/public/$objectPath"
                                api.updateOrderStatus(orderIdFilter = "eq.${entity.id}", patch = mapOf("payment_proof_url" to publicUrl))
                                file.delete() // clean up local file
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                syncedCount++
            } catch (e: Exception) {
                e.printStackTrace()
                // Network hiccup mid-loop — stop here, remaining orders retry next trigger.
                break
            }
        }
        if (syncedCount > 0) {
            // Pesanan offline baru sampai ke server: omzet & laporan berubah untuk
            // semua layar, dan angka target sekarang sudah bisa dihitung server.
            com.sukashawarma.pos.data.remote.GlobalEventBus.orderSyncEvent.tryEmit(Unit)
            com.sukashawarma.pos.data.remote.GlobalEventBus.targetRefreshEvent.tryEmit(Unit)
        }
        return syncedCount
    }
}
