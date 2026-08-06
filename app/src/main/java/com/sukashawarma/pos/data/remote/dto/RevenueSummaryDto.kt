package com.sukashawarma.pos.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Payload untuk RPC `pos_revenue_summary_guarded`.
 *
 * [start] dan [end] adalah ISO-8601 ber-offset Jakarta (`...+07:00`). Keduanya
 * boleh null untuk rentang "Semua Waktu". [channels] null berarti semua kanal.
 */
data class RevenueSummaryPayload(
    @SerializedName("p_outlet_id") val outletId: String,
    @SerializedName("p_start") val start: String?,
    @SerializedName("p_end") val end: String?,
    @SerializedName("p_payment_method") val paymentMethod: String? = null,
    @SerializedName("p_channels") val channels: List<String>? = null,
    @SerializedName("p_include_null_channel") val includeNullChannel: Boolean = false
)

/**
 * Hasil agregasi omzet dari database.
 *
 * [gross] adalah omzet kotor (SUM `order_items.subtotal`) dan satu-satunya angka
 * penjualan yang ditampilkan di UI. [net] dan [deductions] hanya untuk keperluan
 * internal/rekonsiliasi — jangan dirender.
 */
data class RevenueSummaryDto(
    val gross: Double = 0.0,
    val net: Double = 0.0,
    val deductions: Double = 0.0,
    @SerializedName("order_count") val orderCount: Int = 0,
    @SerializedName("items_sold") val itemsSold: Int = 0,
    @SerializedName("avg_order_gross") val avgOrderGross: Double = 0.0,
    @SerializedName("pending_count") val pendingCount: Int = 0,
    @SerializedName("cancelled_count") val cancelledCount: Int = 0,
    @SerializedName("by_payment") val byPayment: List<RevenueByPaymentDto> = emptyList(),
    @SerializedName("by_hour") val byHour: List<RevenueByHourDto> = emptyList(),
    @SerializedName("by_day") val byDay: List<RevenueByDayDto> = emptyList(),
    @SerializedName("top_items") val topItems: List<RevenueTopItemDto> = emptyList()
)

data class RevenueByPaymentDto(
    @SerializedName("payment_method") val paymentMethod: String,
    val gross: Double,
    val count: Int
)

/** [hour] 0-23 menurut Asia/Jakarta. */
data class RevenueByHourDto(
    val hour: Int,
    val count: Int
)

/** [day] dalam format `yyyy-MM-dd` menurut Asia/Jakarta. */
data class RevenueByDayDto(
    val day: String,
    val gross: Double
)

data class RevenueTopItemDto(
    val name: String,
    val qty: Int,
    val gross: Double
)
