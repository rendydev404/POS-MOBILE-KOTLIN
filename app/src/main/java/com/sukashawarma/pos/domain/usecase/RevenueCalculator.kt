package com.sukashawarma.pos.domain.usecase

import com.sukashawarma.pos.data.local.entity.LocalOrderEntity
import com.sukashawarma.pos.data.remote.dto.OrderDto
import com.sukashawarma.pos.domain.model.OrderStatus

/**
 * Ringkasan penjualan untuk satu kumpulan pesanan.
 *
 * [gross] adalah omzet kotor — harga menu sebelum diskon dan subsidi promo dipotong.
 * Ini angka yang ditampilkan di seluruh halaman. [net] dan [deductions] ikut dihitung
 * supaya rekonsiliasi kas tetap mungkin, tapi tidak dirender di UI.
 */
data class RevenueSummary(
    val gross: Double = 0.0,
    val deductions: Double = 0.0,
    val net: Double = 0.0,
    val orderCount: Int = 0,
    val itemsSold: Int = 0
)

/**
 * Satu-satunya definisi "omzet" di aplikasi ini.
 *
 * Aturannya: hanya pesanan berstatus [OrderStatus.COMPLETED] yang dihitung, dan
 * nilainya adalah jumlah `order_items.subtotal` (omzet KOTOR), bukan `total_amount`
 * yang sudah terpotong diskon.
 *
 * Catatan penting: rekonsiliasi kas di layar Shift TIDAK boleh memakai [grossOf].
 * Uang yang masuk laci adalah `total_amount` — lihat [netOf].
 */
object RevenueCalculator {

    /** Status pesanan yang diakui sebagai penjualan. */
    val REVENUE_STATUS: OrderStatus = OrderStatus.COMPLETED

    fun isRevenue(status: String?, cancellationStatus: String?): Boolean =
        status?.equals(REVENUE_STATUS.name, ignoreCase = true) == true &&
            !OrderStatusFilter.isCancelled(status, cancellationStatus)

    /**
     * Pembatalan yang sudah disetujui owner tidak selalu ikut mengubah kolom
     * `status` di server, jadi `status = 'completed'` saja tidak cukup — tanpa
     * pengecekan ini pesanan yang sudah dibatalkan tetap terhitung sebagai omzet.
     */
    fun isRevenue(order: OrderDto): Boolean = isRevenue(order.status, order.cancellationStatus)

    fun isRevenue(entity: LocalOrderEntity): Boolean = isRevenue(entity.status, entity.cancellationStatus)

    /**
     * Omzet kotor satu pesanan: jumlah subtotal seluruh baris item.
     *
     * Bila `order_items` tidak ikut terbawa (mis. response yang dipangkas), nilainya
     * direkonstruksi dari `total_amount` ditambah potongan yang tercatat — hasilnya
     * sama persis selama diskon memang tersimpan di kolomnya.
     */
    fun grossOf(order: OrderDto): Double {
        val items = order.orderItems
        if (!items.isNullOrEmpty()) return items.sumOf { it.subtotal }
        return order.totalAmount + (order.discountAmount ?: 0.0) + (order.promoSubsidy ?: 0.0)
    }

    /** Omzet kotor dari cache Room. Kolom `subtotal` sudah berisi jumlah subtotal item. */
    fun grossOf(entity: LocalOrderEntity): Double =
        if (entity.subtotal > 0.0) entity.subtotal
        else entity.totalAmount + entity.discountAmount

    /** Uang yang benar-benar dibayar pelanggan. Dipakai untuk kas, bukan untuk omzet. */
    fun netOf(order: OrderDto): Double = order.totalAmount

    fun netOf(entity: LocalOrderEntity): Double = entity.totalAmount

    fun itemsSoldOf(order: OrderDto): Int = order.orderItems?.sumOf { it.quantity } ?: 0

    /** Menjumlahkan hanya pesanan yang memenuhi [isRevenue]; sisanya diabaikan. */
    @JvmName("summarizeDtos")
    fun summarize(orders: List<OrderDto>): RevenueSummary {
        val completed = orders.filter { isRevenue(it) }
        val gross = completed.sumOf { grossOf(it) }
        val net = completed.sumOf { netOf(it) }
        return RevenueSummary(
            gross = gross,
            deductions = maxOf(0.0, gross - net),
            net = net,
            orderCount = completed.size,
            itemsSold = completed.sumOf { itemsSoldOf(it) }
        )
    }

    @JvmName("summarizeEntities")
    fun summarize(entities: List<LocalOrderEntity>): RevenueSummary {
        val completed = entities.filter { isRevenue(it) }
        val gross = completed.sumOf { grossOf(it) }
        val net = completed.sumOf { netOf(it) }
        return RevenueSummary(
            gross = gross,
            deductions = maxOf(0.0, gross - net),
            net = net,
            orderCount = completed.size,
            itemsSold = 0 // butuh parse itemsJson; pemanggil yang perlu angka ini menghitung sendiri
        )
    }
}
