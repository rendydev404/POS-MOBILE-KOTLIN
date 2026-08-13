package com.sukashawarma.pos.data.sync

import java.util.concurrent.ConcurrentHashMap

/**
 * Melindungi status "pending_approval" yang baru ditulis lokal dari respons GET
 * basi — respons yang berangkat SEBELUM PATCH pengajuan mendarat di server, tapi
 * baru tiba sesudahnya, sehingga membawa nilai lama ('none').
 *
 * Perlindungannya sengaja dibatasi waktu. Versi sebelumnya melindungi tanpa batas:
 * begitu AM MENOLAK pembatalan, server mengembalikan `cancellation_status` ke
 * 'none' — nilai yang sah — tapi penjaga lama tidak bisa membedakannya dari
 * respons basi, jadi status lokal dipaksa balik ke "pending_approval" selamanya
 * dan kartu pesanan terus berputar "Menunggu Persetujuan Batal".
 *
 * Lewat jendela ini, server selalu dianggap sumber kebenaran. Isinya hanya di
 * memori: kalau proses mati, perlindungan hilang dan server yang menang — persis
 * perilaku yang diinginkan.
 */
object PendingCancellationGuard {
    private const val WINDOW_MS = 15_000L

    private val marks = ConcurrentHashMap<String, Long>()

    /** Tandai sesaat sebelum PATCH pengajuan pembatalan dikirim. */
    fun mark(orderId: String) {
        if (orderId.isNotBlank()) marks[orderId] = System.currentTimeMillis()
    }

    /**
     * Lepas perlindungan: dipanggil saat pengajuan gagal dikirim, dan saat AM
     * sudah memutuskan (setuju/tolak) — keputusan itu selalu menang.
     */
    fun clear(orderId: String) {
        marks.remove(orderId)
    }

    /** True selama status lokal masih dalam jendela perlindungan. */
    fun isProtected(orderId: String): Boolean {
        val markedAt = marks[orderId] ?: return false
        if (System.currentTimeMillis() - markedAt > WINDOW_MS) {
            marks.remove(orderId)
            return false
        }
        return true
    }
}
