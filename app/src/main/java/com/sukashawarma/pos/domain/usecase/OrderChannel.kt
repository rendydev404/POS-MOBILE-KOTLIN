package com.sukashawarma.pos.domain.usecase

/**
 * Pemetaan filter kanal penjualan ke nilai `orders.channel` yang benar-benar ada
 * di database.
 *
 * Sebelumnya tiap halaman menebak sendiri dan tebakannya meleset:
 * - "Offline / Dine-in" memfilter `channel IS NULL`, padahal pesanan kasir
 *   sekarang ditulis dengan `channel = 'pos'` — 10.065 baris terlewat.
 * - Nilai literal `'food_apps'` (6.236 baris) tidak masuk daftar kanal ojol mana
 *   pun, jadi tidak pernah ikut terhitung.
 * - "TikTok Go" hanya mencari `'tiktokgo'`, padahal mayoritas barisnya `'tiktok'`.
 */
object OrderChannel {

    /** Kunci filter yang dipakai UI. */
    const val ALL = "all"
    const val OFFLINE = "offline"
    const val FOOD_APPS = "food_apps"
    const val ENDORSE = "endorse"
    const val WEBSITE = "website"

    /**
     * Pesanan kasir di tempat. `null` termasuk di sini — lihat [includesNull]
     * untuk kompatibilitas pesanan lama yang belum memiliki kanal.
     */
    private val OFFLINE_VALUES = listOf("pos")
    private val ENDORSE_VALUES = listOf(ENDORSE)

    private val GOFOOD = listOf("gofood")
    private val GRABFOOD = listOf("grabfood")
    private val SHOPEEFOOD = listOf("shopeefood")

    /** Semua ejaan TikTok yang pernah masuk ke kolom `channel`. */
    private val TIKTOK = listOf("tiktokgo", "tiktok", "tiktok_go")

    private val WEBSITE_VALUES = listOf(WEBSITE)

    /**
     * Seluruh kanal ojol, termasuk nilai generik `'food_apps'` untuk pesanan yang
     * tidak tercatat aplikasinya.
     */
    private val FOOD_APP_VALUES: List<String> =
        listOf("food_apps") + GOFOOD + GRABFOOD + SHOPEEFOOD + TIKTOK

    /**
     * Nilai `channel` yang cocok dengan [key], atau null bila tidak perlu difilter.
     * Selalu dibaca bersama [includesNull].
     */
    fun valuesFor(key: String): List<String>? = when (key) {
        ALL -> null
        OFFLINE -> OFFLINE_VALUES
        FOOD_APPS -> FOOD_APP_VALUES
        "gofood" -> GOFOOD
        "grabfood" -> GRABFOOD
        "shopeefood" -> SHOPEEFOOD
        "tiktokgo", "tiktok", "tiktok_go" -> TIKTOK
        ENDORSE -> ENDORSE_VALUES
        WEBSITE -> WEBSITE_VALUES
        else -> listOf(key)
    }

    /**
     * True bila baris dengan `channel IS NULL` ikut dihitung. Hanya berlaku untuk
     * offline: pesanan kasir lama tersimpan tanpa channel sama sekali.
     */
    fun includesNull(key: String): Boolean = key == OFFLINE

    /** Filter sisi klien untuk jalur offline; harus sepadan dengan [valuesFor]. */
    fun matches(key: String, channel: String?): Boolean {
        if (key == ALL) return true
        if (channel == null) return includesNull(key)
        return valuesFor(key)?.contains(channel) ?: true
    }

    /**
     * Satu kondisi PostgREST untuk [key], atau null bila semua kanal.
     *
     * Offline menghasilkan `or(...)` bersarang karena harus mencakup dua hal
     * sekaligus: pesanan lama yang `channel`-nya NULL dan pesanan kasir sekarang
     * yang `channel = 'pos'`.
     */
    fun postgrestCondition(key: String): String? {
        val values = valuesFor(key) ?: return null
        val inList = "channel.in.(${values.joinToString(",")})"
        return if (includesNull(key)) "or(channel.is.null,$inList)" else inList
    }
}
