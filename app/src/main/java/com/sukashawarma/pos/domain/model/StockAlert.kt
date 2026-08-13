package com.sukashawarma.pos.domain.model

import com.sukashawarma.pos.data.remote.dto.MonitoringViewCrewDto

/**
 * Bahan baku yang stoknya perlu diperhatikan di outlet ini.
 *
 * Status datang apa adanya dari `monitoring_view_crew` — POS tidak menghitung
 * ulang ambang batas. Sebelumnya banner "STOK KRITIS/HABIS" di dashboard justru
 * membaca `menu_items.is_available`, yaitu menu yang dimatikan kasir, bukan stok
 * bahan baku sama sekali; itu sebabnya banner selalu kosong walau papan stok
 * menunjukkan bahan kritis.
 */
data class StockAlert(
    val id: String,
    val name: String,
    val status: StockAlertStatus,
    /** Mis. "Shawarma Ayam (12 porsi) atau Kebab (8 porsi)". Null bila bahan tak dipakai resep aktif. */
    val projectionText: String?
) {
    /**
     * Teks satu baris untuk marquee: `AYAM (sisa 0 porsi)`.
     *
     * Yang ditampilkan adalah porsi TERKECIL dari seluruh resep yang memakai
     * bahan ini, karena resep terboros itulah yang lebih dulu tidak bisa dibuat.
     * `projection_text` mentah tidak dipakai: satu bahan bisa menyebut belasan
     * resep sekaligus ("Shawarma Ayam Sedang (0 porsi) atau ... " × 12), dan
     * dengan belasan bahan kritis per outlet pita marquee jadi ribuan karakter
     * yang mustahil dibaca kasir.
     *
     * Saldo mentah sengaja tidak ikut: satuannya bisa gram atau satuan besar
     * tergantung tulisan terakhir ke baris stok (kolom `saldo_is_gram`), jadi
     * menampilkannya tanpa formatter komposit justru menyesatkan.
     */
    val marqueeText: String
        get() = minPorsi?.let { "$name (sisa $it porsi)" } ?: name

    /** Porsi terkecil di `projection_text`, atau null kalau tidak ada angka porsi. */
    val minPorsi: Int?
        get() = projectionText?.let { text ->
            PORSI_REGEX.findAll(text).mapNotNull { it.groupValues[1].toIntOrNull() }.minOrNull()
        }

    private companion object {
        val PORSI_REGEX = Regex("""\((-?\d+)\s*porsi\)""", RegexOption.IGNORE_CASE)
    }
}

enum class StockAlertStatus {
    /** Habis / di bawah ambang kritis. Inilah yang ditampilkan di marquee merah. */
    BELOW,

    /** Menipis, sudah di bawah reorder point tapi belum kritis. */
    WARNING;

    companion object {
        fun from(raw: String?): StockAlertStatus? = when (raw?.lowercase()) {
            "below" -> BELOW
            "warning" -> WARNING
            else -> null
        }
    }
}

/**
 * Baris view → model. Baris tanpa nama atau tanpa status yang dikenal dibuang;
 * baris ganda per bahan (view pernah menghasilkan duplikat, lihat dedupe di
 * apps/stok/src/lib/queries/monitoring.ts) diambil yang pertama saja.
 */
fun List<MonitoringViewCrewDto>.toStockAlerts(): List<StockAlert> {
    val seen = mutableSetOf<String>()
    return mapNotNull { dto ->
        val name = dto.itemName?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val status = StockAlertStatus.from(dto.status) ?: return@mapNotNull null
        val id = dto.bahanBakuId ?: name
        if (!seen.add(id)) return@mapNotNull null
        StockAlert(
            id = id,
            name = name.uppercase(),
            status = status,
            projectionText = dto.projectionText?.trim()?.takeIf { it.isNotEmpty() }
        )
    }
}
