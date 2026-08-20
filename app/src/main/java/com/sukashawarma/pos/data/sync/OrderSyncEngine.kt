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
                    discountAmount = entity.discountAmount.toLong(),
                    promoSubsidy = entity.promoSubsidy.toLong(),
                    totalAmount = entity.totalAmount.toLong(),
                    amountReceived = entity.amountReceived.toLong(),
                    changeAmount = entity.changeAmount.toLong(),
                    createdAt = createdAtIso,
                    channel = entity.channel,
                    pickupTime = entity.effectiveReleaseTime.takeIf { it > 0L }?.let {
                        DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(it + 20 * 60_000L))
                    },
                    releaseTime = entity.effectiveReleaseTime.takeIf { it > 0L }?.let {
                        DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(it))
                    },
                    // Ikut dikirim seperti jalur online di POSManualOrderViewModel.submitOrder.
                    // Tanpa ini setiap pesanan yang dibuat saat offline mendarat di server
                    // tanpa nama kasir — padahal Room menyimpannya — sehingga atribusi dan
                    // perhitungan bonus kasir untuk transaksi offline permanen kosong.
                    cashierName = entity.cashierName
                )

                val orderRes = api.createOrder(payload)
                if (!orderRes.isSuccessful) {
                    val errorBody = orderRes.errorBody()?.string().orEmpty()
                    if (orderRes.code() == 409 || errorBody.contains("duplicate key", ignoreCase = true)) {
                        // Percobaan sebelumnya sebenarnya sukses di server, cuma responsnya
                        // gagal diterima klien — tanpa ini, retry akan tabrak unique constraint
                        // id yang sama terus-menerus dan order tampak "OFFLINE" selamanya.
                        orderDao.markSynced(entity.id, entity.orderNumber)
                        syncedCount++
                        continue
                    }
                    android.util.Log.e(
                        "OrderSyncEngine",
                        "Gagal sync order ${entity.id}: HTTP ${orderRes.code()} $errorBody"
                    )
                    continue // leave syncState != SYNCED, retry next pass
                }
                if (orderRes.body().isNullOrEmpty()) {
                    android.util.Log.e("OrderSyncEngine", "Sync order ${entity.id} sukses tapi body kosong")
                    continue
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
                        unitPrice = item.unitPrice.toLong(),
                        subtotal = item.subtotal.toLong()
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
                            // Ekstensi & content-type mengikuti file yang benar-benar
                            // ditulis saat offline (sekarang WebP), bukan hardcode jpeg —
                            // file lama yang masih .jpg tetap terkirim dengan benar.
                            val isWebp = file.name.endsWith(".webp", ignoreCase = true)
                            val ext = if (isWebp) "webp" else "jpg"
                            val mime = if (isWebp) "image/webp" else "image/jpeg"
                            val fileName = "${entity.outletId}_${serverOrderNumber}_${java.time.LocalDate.now()}.$ext"
                            val objectPath = "payment_proofs/$fileName"
                            val body = file.asRequestBody(mime.toMediaTypeOrNull())

                            val uploadRes = api.uploadPaymentProof(objectPath = objectPath, contentType = mime, file = body)
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
