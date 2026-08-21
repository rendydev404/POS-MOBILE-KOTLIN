package com.sukashawarma.pos.domain.usecase

/**
 * Menahan tap status ganda untuk order yang sama sampai request pertama selesai.
 * Thread-safe karena callback UI, realtime, dan coroutine jaringan dapat berimpit.
 */
class OrderStatusUpdateGuard {
    private val inFlightOrderIds = mutableSetOf<String>()

    @Synchronized
    fun tryBegin(orderId: String): Boolean = inFlightOrderIds.add(orderId)

    @Synchronized
    fun finish(orderId: String) {
        inFlightOrderIds.remove(orderId)
    }
}

/** Aturan commit: HTTP sukses saja tidak cukup; PATCH harus mengembalikan baris. */
object OrderStatusUpdatePolicy {
    fun canCommit(httpSuccessful: Boolean, updatedRowCount: Int): Boolean =
        httpSuccessful && updatedRowCount > 0
}
