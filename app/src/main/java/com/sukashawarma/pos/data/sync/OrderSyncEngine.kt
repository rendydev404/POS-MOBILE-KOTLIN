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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    /**
     * Pemicu sync datang dari beberapa tempat (koneksi kembali, jumlah pending
     * berubah, retry berkala, dan tombol kasir). Satu mutex memastikan order yang
     * sama tidak dikirim bersamaan oleh dua pemicu tersebut.
     */
    private val syncMutex = Mutex()

    private fun itemPayloadsOf(
        entity: com.sukashawarma.pos.data.local.entity.LocalOrderEntity
    ): List<CreateOrderItemPayload> {
        val itemType = object : TypeToken<List<OrderItem>>() {}.type
        val items: List<OrderItem> = gson.fromJson(entity.itemsJson, itemType) ?: emptyList()
        return items.map { item ->
            CreateOrderItemPayload(
                orderId = entity.id,
                menuItemId = item.menuItemId,
                menuItemName = item.encodedMenuItemName(),
                quantity = item.quantity,
                unitPrice = item.unitPrice.toLong(),
                subtotal = item.subtotal.toLong(),
                isPromoReward = item.isPromoReward,
                promoId = item.promoId,
                promoName = item.promoName,
                promoBuyQuantity = item.promoBuyQuantity,
                promoGetQuantity = item.promoGetQuantity,
                originalUnitPrice = item.originalUnitPrice?.toLong()
            )
        }
    }

    /**
     * Unggah bukti transfer yang tersimpan di disk, lalu tempelkan URL-nya ke order.
     * Dipakai kedua cabang sukses (insert baru maupun 409 "sudah ada"): begitu barisnya
     * ditandai SYNCED tidak ada lagi yang akan mencoba ulang, jadi ini kesempatan
     * terakhirnya. Non-fatal — pesanannya sendiri sudah aman di server.
     */
    private suspend fun uploadStoredProof(
        entity: com.sukashawarma.pos.data.local.entity.LocalOrderEntity,
        serverOrderNumber: Int
    ) {
        val path = entity.localPaymentProofPath ?: return
        try {
            val file = File(path)
            if (!file.exists()) return
            // Ekstensi & content-type mengikuti file yang benar-benar ditulis saat
            // offline (sekarang WebP), bukan hardcode jpeg — file lama yang masih .jpg
            // tetap terkirim dengan benar.
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
            } else {
                // Dicatat, bukan ditelan: bukti pembayaran yang hilang tanpa jejak tidak
                // bisa didiagnosis saat rekonsiliasi kas tidak cocok.
                android.util.Log.e(
                    "OrderSyncEngine",
                    "Upload bukti transfer order ${entity.id} gagal: HTTP ${uploadRes.code()}"
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("OrderSyncEngine", "Upload bukti transfer order ${entity.id} melempar exception", e)
        }
    }

    /** Returns how many offline orders were successfully pushed to the server. */
    suspend fun syncPendingOrders(outletId: String): Int = syncMutex.withLock {
        if (outletId.isBlank()) return@withLock 0
        // 60 dtk cukup longgar: pengiriman latar belakang yang sehat selesai dalam
        // hitungan ratusan milidetik, dan timeout OkHttp terburuk (15 dtk connect +
        // 15 dtk read, dua request) masih di bawah angka ini.
        val sendingStaleBefore = System.currentTimeMillis() - 60_000L
        val pending = orderDao.getUnsyncedOrders(outletId, sendingStaleBefore)
        var syncedCount = 0

        for (entity in pending) {
            try {
                if (pushOrder(entity)) syncedCount++
            } catch (e: Exception) {
                android.util.Log.e("OrderSyncEngine", "Sync order ${entity.id} melempar exception", e)
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
        return@withLock syncedCount
    }

    /**
     * Mencoba satu order tertentu. Dipakai tombol "Coba Kirim Sekarang" dan
     * aksi status supaya order lokal selalu dibuat di server sebelum di-PATCH.
     */
    suspend fun syncOrder(orderId: String): Boolean = syncMutex.withLock {
        val entity = orderDao.getOrderById(orderId) ?: return@withLock false
        if (entity.syncState == com.sukashawarma.pos.data.local.entity.SyncState.SYNCED.name) {
            return@withLock true
        }

        val synced = try {
            pushOrder(entity)
        } catch (e: Exception) {
            android.util.Log.e("OrderSyncEngine", "Sync manual order $orderId melempar exception", e)
            false
        }
        if (synced) {
            com.sukashawarma.pos.data.remote.GlobalEventBus.orderSyncEvent.tryEmit(Unit)
            com.sukashawarma.pos.data.remote.GlobalEventBus.targetRefreshEvent.tryEmit(Unit)
        }
        synced
    }

    /** Satu-satunya implementasi pengiriman order agar retry otomatis dan manual identik. */
    private suspend fun pushOrder(
        entity: com.sukashawarma.pos.data.local.entity.LocalOrderEntity
    ): Boolean {
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
            cashierName = entity.cashierName,
            isOfflineSync = true
        )

        // RPC ini idempoten terhadap UUID order. Bila respons percobaan pertama
        // terputus sesudah server menyimpan, pengiriman ulang mengambil baris yang
        // sama dan tidak membuat transaksi kedua.
        val orderRes = api.createOrderWithItems(
            com.sukashawarma.pos.data.remote.dto.CreateOrderWithItemsPayload(
                order = payload,
                items = itemPayloadsOf(entity)
            )
        )
        if (!orderRes.isSuccessful) {
            android.util.Log.e(
                "OrderSyncEngine",
                "Gagal sync order ${entity.id}: HTTP ${orderRes.code()} ${orderRes.errorBody()?.string().orEmpty()}"
            )
            return false
        }
        val serverOrderNumber = orderRes.body()?.orderNumber
        if (serverOrderNumber == null) {
            android.util.Log.e("OrderSyncEngine", "Sync order ${entity.id} sukses tapi body kosong")
            return false
        }

        orderDao.markSynced(entity.id, serverOrderNumber)
        uploadStoredProof(entity, serverOrderNumber)
        return true
    }
}
