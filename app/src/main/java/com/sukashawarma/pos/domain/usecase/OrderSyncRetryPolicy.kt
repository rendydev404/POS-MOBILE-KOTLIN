package com.sukashawarma.pos.domain.usecase

/**
 * Menentukan jeda percobaan ulang order yang belum sampai server.
 *
 * Jeda bertambah agar tablet tidak membanjiri server ketika sedang bermasalah,
 * tetapi dibatasi dua menit supaya order tidak tertinggal terlalu lama.
 */
object OrderSyncRetryPolicy {
    const val INITIAL_DELAY_MS = 10_000L
    const val MAX_DELAY_MS = 120_000L

    fun afterAttempt(previousDelayMs: Long, syncedAnyOrder: Boolean): Long {
        if (syncedAnyOrder) return INITIAL_DELAY_MS
        return (previousDelayMs * 2).coerceIn(INITIAL_DELAY_MS, MAX_DELAY_MS)
    }
}
