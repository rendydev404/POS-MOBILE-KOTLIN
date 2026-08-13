package com.sukashawarma.pos.domain.usecase

import com.sukashawarma.pos.domain.gate.JakartaTime
import java.time.LocalDate

/** Pilihan rentang tanggal yang dipakai halaman Laporan maupun Riwayat. */
enum class ReportRange(val label: String) {
    TODAY("Hari Ini"),
    YESTERDAY("Kemarin"),
    LAST_7_DAYS("7 Hari Terakhir"),
    LAST_30_DAYS("30 Hari Terakhir"),
    ALL_TIME("Semua Waktu"),
    CUSTOM("Kustom Tanggal");

    companion object {
        /** Memetakan kunci filter lama halaman Riwayat ("today", "7d", ...). */
        fun fromHistoryKey(key: String): ReportRange = when (key) {
            "today" -> TODAY
            "yesterday" -> YESTERDAY
            "7d" -> LAST_7_DAYS
            "30d" -> LAST_30_DAYS
            "custom" -> CUSTOM
            else -> ALL_TIME
        }
    }
}

/**
 * Batas rentang tanggal yang sudah pasti konsisten antara server dan cache lokal:
 * [startIso]/[endIso] dipakai untuk PostgREST, [startMillis]/[endMillis] untuk Room,
 * dan keduanya diturunkan dari titik waktu yang sama.
 *
 * `null` pada [startIso]/[endIso] berarti tidak ada batas di sisi itu.
 */
data class ResolvedDateRange(
    val startIso: String?,
    val endIso: String?,
    val startMillis: Long,
    val endMillis: Long
) {
    val isUnbounded: Boolean get() = startIso == null && endIso == null
}

/**
 * Menghitung batas rentang tanggal, selalu dalam zona Asia/Jakarta.
 *
 * Ini satu-satunya tempat rentang tanggal laporan dihitung. Sebelumnya tiap halaman
 * punya versinya sendiri: Laporan memakai Jakarta tapi memformat tanpa offset (dibaca
 * server sebagai UTC, batas hari meleset 7 jam), Riwayat memakai zona perangkat, dan
 * "7 hari terakhir" sebenarnya mencakup 8 hari.
 */
object ReportDateRangeResolver {

    fun resolve(
        range: ReportRange,
        customStart: LocalDate? = null,
        customEnd: LocalDate? = null,
        today: LocalDate = JakartaTime.today()
    ): ResolvedDateRange = when (range) {
        ReportRange.TODAY -> bounded(today, today)

        ReportRange.YESTERDAY -> {
            val yesterday = today.minusDays(1)
            bounded(yesterday, yesterday)
        }

        // 7 hari termasuk hari ini, jadi mundur 6 hari — bukan 7.
        ReportRange.LAST_7_DAYS -> bounded(today.minusDays(6), today)

        ReportRange.LAST_30_DAYS -> bounded(today.minusDays(29), today)

        ReportRange.ALL_TIME -> ResolvedDateRange(
            startIso = null,
            endIso = null,
            startMillis = 0L,
            endMillis = Long.MAX_VALUE
        )

        ReportRange.CUSTOM -> {
            if (customStart == null || customEnd == null) {
                resolve(ReportRange.ALL_TIME, today = today)
            } else {
                // Toleran terhadap tanggal yang terbalik.
                val from = minOf(customStart, customEnd)
                val to = maxOf(customStart, customEnd)
                bounded(from, to)
            }
        }
    }

    /**
     * Rentang inklusif dari awal hari [from] sampai akhir hari [to], zona Jakarta.
     *
     * Batas ISO dan batas milidetik diturunkan dari titik waktu yang sama, jadi
     * jalur server dan jalur Room mustahil berbeda. Bentuk ISO-nya UTC (`...Z`) —
     * lihat [JakartaTime.startOfDayIso] untuk alasannya.
     */
    private fun bounded(from: LocalDate, to: LocalDate) = ResolvedDateRange(
        startIso = JakartaTime.startOfDayIso(from),
        endIso = JakartaTime.endOfDayIso(to),
        startMillis = JakartaTime.startOfDayMillis(from),
        endMillis = JakartaTime.endOfDayMillis(to)
    )
}
