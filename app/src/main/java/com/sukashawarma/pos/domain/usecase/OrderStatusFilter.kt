package com.sukashawarma.pos.domain.usecase

import com.sukashawarma.pos.data.remote.dto.OrderDto

/**
 * Pemetaan pill status di halaman Riwayat ke kondisi yang benar-benar cocok
 * dengan isi tabel `orders`.
 *
 * Dua hal yang dulu salah:
 * - "Menunggu" memfilter `status = 'pending'`, padahal tidak ada satu pun baris
 *   berstatus itu — pesanan yang belum selesai tersimpan sebagai `preparing`,
 *   sehingga pill-nya selalu kosong.
 * - "Dibatalkan" hanya melihat `status = 'cancelled'`, melewatkan pesanan yang
 *   pembatalannya sudah disetujui owner tapi `status`-nya belum ikut berubah
 *   (`cancellation_status = 'approved'`). App sendiri sudah memperlakukan baris
 *   seperti itu sebagai batal saat sinkronisasi, jadi Riwayat ikut disamakan.
 */
object OrderStatusFilter {

    const val ALL = "Semua"
    const val WAITING = "Menunggu"
    const val DONE = "Selesai"
    const val CANCELLED = "Dibatalkan"

    /** Status yang berarti "pesanan masih berjalan". */
    private val WAITING_STATUSES = listOf("pending", "preparing", "ready")

    const val APPROVED_CANCELLATION = "approved"

    /** Batal bila status-nya cancelled ATAU pembatalannya sudah disetujui. */
    fun isCancelled(status: String?, cancellationStatus: String?): Boolean =
        status.equals("cancelled", ignoreCase = true) ||
            cancellationStatus.equals(APPROVED_CANCELLATION, ignoreCase = true)

    fun isCancelled(dto: OrderDto): Boolean = isCancelled(dto.status, dto.cancellationStatus)

    /**
     * Satu kondisi PostgREST untuk [pill], atau null bila tidak perlu menyaring.
     *
     * Dikembalikan sebagai potongan kondisi (bukan pasangan parameter) supaya bisa
     * digabung ke dalam satu ekspresi `and=(...)` bersama filter tanggal dan kanal.
     * PostgREST hanya menerima satu `or` per level, jadi menaruh masing-masing di
     * parameter `or` sendiri akan saling menimpa.
     *
     * Untuk "Selesai" server sengaja mengembalikan superset (semua `completed`);
     * pesanan yang sudah disetujui batal disaring lagi di klien lewat [matches],
     * karena menyempitkan di server berisiko ikut membuang baris yang
     * `cancellation_status`-nya NULL.
     */
    fun postgrestCondition(pill: String): String? = when (pill) {
        WAITING -> "status.in.(${WAITING_STATUSES.joinToString(",")})"
        DONE -> "status.eq.completed"
        CANCELLED -> "or(status.eq.cancelled,cancellation_status.eq.$APPROVED_CANCELLATION)"
        else -> null
    }

    /** Predikat sisi klien; sepadan dengan [postgrestCondition] dan boleh mempersempit. */
    fun matches(pill: String, dto: OrderDto): Boolean = when (pill) {
        WAITING -> !isCancelled(dto) && dto.status.lowercase() in WAITING_STATUSES
        DONE -> dto.status.equals("completed", ignoreCase = true) && !isCancelled(dto)
        CANCELLED -> isCancelled(dto)
        else -> true
    }
}
