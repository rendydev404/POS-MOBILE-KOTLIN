package com.sukashawarma.pos.presentation.reports

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sukashawarma.pos.POSApplication
import com.sukashawarma.pos.data.local.entity.LocalOrderEntity
import com.sukashawarma.pos.data.remote.NetworkMonitor
import com.sukashawarma.pos.data.remote.SupabaseClient
import com.sukashawarma.pos.data.remote.dto.OrderDto
import com.sukashawarma.pos.data.remote.dto.OrderItemDto
import com.sukashawarma.pos.data.remote.dto.RevenueSummaryPayload
import com.sukashawarma.pos.data.remote.dto.ShiftDto
import com.sukashawarma.pos.domain.usecase.ReportDateRangeResolver
import com.sukashawarma.pos.domain.usecase.ReportRange
import com.sukashawarma.pos.domain.usecase.ResolvedDateRange
import com.sukashawarma.pos.domain.usecase.RevenueCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Rentang tanggal laporan. Definisinya tinggal satu, di [ReportRange]. */
typealias DateRange = ReportRange

data class PaymentStats(val count: Int, val revenue: Double)
data class ItemStats(val qty: Int, val revenue: Double)

/**
 * Angka yang ditampilkan halaman Laporan.
 *
 * [grossRevenue] adalah omzet KOTOR (jumlah `order_items.subtotal`) dari pesanan
 * berstatus `completed` saja. Tidak ada angka bersih di sini — memang tidak
 * ditampilkan di UI.
 */
data class AnalyticsData(
    val grossRevenue: Double = 0.0,
    val totalOrders: Int = 0,
    val totalItemsSold: Int = 0,
    val pendingCount: Int = 0,
    val canceledCount: Int = 0,
    val paymentBreakdown: Map<String, PaymentStats> = emptyMap(),
    val hourly: List<Int> = List(24) { 0 },
    val dailyEntries: List<Pair<String, Double>> = emptyList(),
    val bestSellers: List<Pair<String, ItemStats>> = emptyList(),
    val avgOrderValue: Double = 0.0,
    val totalCashVariance: Double = 0.0,
    val peakHour: Int? = null,
    val orders: List<OrderDto> = emptyList(),
    val shifts: List<ShiftDto> = emptyList(),
    /** True bila angka di atas dihitung dari cache lokal, bukan dari server. */
    val isFromLocalCache: Boolean = false
)

class ReportsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = (application as POSApplication).database
    private val orderDao = database.orderDao()
    private val api = SupabaseClient.api

    private val _currentOutletId = MutableStateFlow("")

    val selectedRange = MutableStateFlow(DateRange.TODAY)
    val customStartDate = MutableStateFlow("")
    val customEndDate = MutableStateFlow("")

    val channelFilter = MutableStateFlow("all")
    val paymentFilter = MutableStateFlow("all")

    /**
     * Filter status hanya menyaring tabel transaksi di bawah, BUKAN angka omzet.
     * Omzet menurut definisinya selalu pesanan `completed`; dulu filter ini ikut
     * masuk ke query omzet sehingga memilih "pending" membuat omzet jadi Rp 0.
     */
    val statusFilter = MutableStateFlow("all")
    val searchQuery = MutableStateFlow("")

    private val _analyticsData = MutableStateFlow(AnalyticsData())
    val analyticsData = _analyticsData.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    init {
        viewModelScope.launch {
            // Pembatalan yang disetujui owner mengubah angka laporan — ikut realtime.
            com.sukashawarma.pos.data.remote.GlobalEventBus.orderSyncEvent.collect {
                loadRealReportData()
            }
        }
    }

    fun setOutlet(outletId: String) {
        _currentOutletId.value = outletId
        loadRealReportData()
    }

    fun updateRange(range: DateRange) {
        selectedRange.value = range
        if (range != DateRange.CUSTOM) loadRealReportData()
    }

    fun updateCustomDate(start: String, end: String) {
        customStartDate.value = start
        customEndDate.value = end
        if (start.isNotEmpty() && end.isNotEmpty()) loadRealReportData()
    }

    fun updateFilters(channel: String, payment: String, status: String) {
        channelFilter.value = channel
        paymentFilter.value = payment
        statusFilter.value = status
        loadRealReportData()
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    private fun resolveRange(): ResolvedDateRange = ReportDateRangeResolver.resolve(
        range = selectedRange.value,
        customStart = customStartDate.value.toLocalDateOrNull(),
        customEnd = customEndDate.value.toLocalDateOrNull()
    )

    /** Daftar kanal yang cocok dengan [channelFilter], atau null bila semua kanal. */
    private fun channelsOrNull(): List<String>? = when (channelFilter.value) {
        "all", "offline" -> null
        "food_apps" -> FOOD_APP_CHANNELS
        "tiktokgo", "tiktok" -> TIKTOK_CHANNELS
        else -> listOf(channelFilter.value)
    }

    private fun includeNullChannel(): Boolean = channelFilter.value == "offline"

    fun loadRealReportData() {
        val outletId = _currentOutletId.value
        if (outletId.isEmpty()) return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val range = resolveRange()
                if (NetworkMonitor.isOnline.value) {
                    loadFromServer(outletId, range)
                } else {
                    loadFromLocalCache(outletId, range)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Omzet diambil dari RPC `pos_revenue_summary_guarded` yang mengagregasi di
     * database. Daftar transaksi tetap ditarik terpisah dan boleh terpotong limit —
     * itu memang hanya tabel, tidak dipakai untuk menghitung angka apa pun.
     */
    private suspend fun loadFromServer(outletId: String, range: ResolvedDateRange) {
        val summaryRes = api.getRevenueSummary(
            RevenueSummaryPayload(
                outletId = outletId,
                start = range.startIso,
                end = range.endIso,
                paymentMethod = paymentFilter.value.takeIf { it != "all" },
                channels = channelsOrNull(),
                includeNullChannel = includeNullChannel()
            )
        )
        val summary = summaryRes.body()
        if (summary == null) {
            // RPC gagal (mis. migrasi belum diterapkan) — jangan tampilkan Rp 0 palsu.
            loadFromLocalCache(outletId, range)
            return
        }

        val orders = fetchOrderList(outletId, range)
        val shifts = fetchShifts(outletId, range)

        val hourly = MutableList(24) { 0 }
        summary.byHour.forEach { if (it.hour in 0..23) hourly[it.hour] = it.count }
        val maxHourly = hourly.maxOrNull() ?: 0

        _analyticsData.value = AnalyticsData(
            grossRevenue = summary.gross,
            totalOrders = summary.orderCount,
            totalItemsSold = summary.itemsSold,
            pendingCount = summary.pendingCount,
            canceledCount = summary.cancelledCount,
            paymentBreakdown = summary.byPayment.associate {
                it.paymentMethod to PaymentStats(it.count, it.gross)
            },
            hourly = hourly,
            dailyEntries = summary.byDay.map { it.day to it.gross },
            bestSellers = summary.topItems.map { it.name to ItemStats(it.qty, it.gross) },
            avgOrderValue = summary.avgOrderGross,
            totalCashVariance = shifts.sumOf { it.variance ?: 0.0 },
            peakHour = if (maxHourly > 0) hourly.indexOf(maxHourly) else null,
            orders = orders,
            shifts = shifts,
            isFromLocalCache = false
        )
    }

    /**
     * Jalur offline. Rumusnya identik dengan RPC lewat [RevenueCalculator], tapi
     * hanya melihat pesanan yang sempat tersimpan di Room — karena itu hasilnya
     * ditandai [AnalyticsData.isFromLocalCache] supaya UI bisa memberi peringatan.
     */
    private suspend fun loadFromLocalCache(outletId: String, range: ResolvedDateRange) {
        val entities = orderDao.getOrdersByDateRange(outletId, range.startMillis, range.endMillis)
        val allDtos = entities.map { it.toOrderDto() }.filter { matchesNonStatusFilters(it) }
        val completed = allDtos.filter { RevenueCalculator.isRevenue(it) }
        val summary = RevenueCalculator.summarize(allDtos)

        val hourly = MutableList(24) { 0 }
        val dailyMap = linkedMapOf<String, Double>()
        val itemMap = linkedMapOf<String, ItemStats>()
        val paymentBreakdown = linkedMapOf<String, PaymentStats>()

        completed.forEach { o ->
            val gross = RevenueCalculator.grossOf(o)

            val pm = o.paymentMethod ?: "unknown"
            val prev = paymentBreakdown[pm] ?: PaymentStats(0, 0.0)
            paymentBreakdown[pm] = PaymentStats(prev.count + 1, prev.revenue + gross)

            // Jam dan hari dihitung di zona Jakarta, sama seperti RPC.
            val instant = com.sukashawarma.pos.domain.gate.JakartaTime.instantOrNull(o.createdAt)
            if (instant != null) {
                val local = instant.atZone(com.sukashawarma.pos.domain.gate.JakartaTime.ZONE)
                hourly[local.hour] += 1
                val key = local.toLocalDate().toString()
                dailyMap[key] = (dailyMap[key] ?: 0.0) + gross
            }

            o.orderItems?.forEach { oi ->
                val curr = itemMap[oi.menuItemName] ?: ItemStats(0, 0.0)
                itemMap[oi.menuItemName] = ItemStats(curr.qty + oi.quantity, curr.revenue + oi.subtotal)
            }
        }

        val maxHourly = hourly.maxOrNull() ?: 0

        _analyticsData.value = AnalyticsData(
            grossRevenue = summary.gross,
            totalOrders = summary.orderCount,
            totalItemsSold = itemMap.values.sumOf { it.qty },
            pendingCount = allDtos.count { it.status == "pending" },
            canceledCount = allDtos.count { it.status == "cancelled" },
            paymentBreakdown = paymentBreakdown,
            hourly = hourly,
            dailyEntries = dailyMap.toList().sortedBy { it.first },
            bestSellers = itemMap.toList().sortedByDescending { it.second.qty }.take(10),
            avgOrderValue = if (summary.orderCount > 0) summary.gross / summary.orderCount else 0.0,
            totalCashVariance = 0.0,
            peakHour = if (maxHourly > 0) hourly.indexOf(maxHourly) else null,
            orders = allDtos.filter { matchesStatusFilter(it) },
            shifts = emptyList(),
            isFromLocalCache = true
        )
    }

    private suspend fun fetchOrderList(outletId: String, range: ResolvedDateRange): List<OrderDto> {
        val filters = mutableMapOf("outlet_id" to "eq.$outletId", "limit" to ORDER_LIST_LIMIT)

        if (range.startIso != null && range.endIso != null) {
            filters["and"] = "(created_at.gte.${range.startIso},created_at.lte.${range.endIso})"
        } else if (range.startIso != null) {
            filters["created_at"] = "gte.${range.startIso}"
        } else if (range.endIso != null) {
            filters["created_at"] = "lte.${range.endIso}"
        }

        if (statusFilter.value != "all") filters["status"] = "eq.${statusFilter.value}"
        if (paymentFilter.value != "all") filters["payment_method"] = "eq.${paymentFilter.value}"
        when (channelFilter.value) {
            "all" -> Unit
            "offline" -> filters["channel"] = "is.null"
            "food_apps" -> filters["channel"] = "in.(${FOOD_APP_CHANNELS.joinToString(",")})"
            "tiktokgo", "tiktok" -> filters["channel"] = "in.(${TIKTOK_CHANNELS.joinToString(",")})"
            else -> filters["channel"] = "eq.${channelFilter.value}"
        }

        val dtos = api.getOrders(filters).body() ?: return emptyList()
        val offlineSyncedIds = orderDao.getSyncedFromOfflineIds(outletId).toSet()
        return dtos.map { if (offlineSyncedIds.contains(it.id)) it.copy(isSyncedFromOffline = true) else it }
    }

    private suspend fun fetchShifts(outletId: String, range: ResolvedDateRange): List<ShiftDto> {
        val shiftFilters = mutableMapOf("outlet_id" to "eq.$outletId", "status" to "eq.closed")
        if (range.startIso != null && range.endIso != null) {
            shiftFilters["and"] = "(end_time.gte.${range.startIso},end_time.lte.${range.endIso})"
        } else if (range.startIso != null) {
            shiftFilters["end_time"] = "gte.${range.startIso}"
        }
        return api.getShifts(shiftFilters).body() ?: emptyList()
    }

    /** Filter selain status — dipakai untuk angka omzet di jalur offline. */
    private fun matchesNonStatusFilters(dto: OrderDto): Boolean {
        if (paymentFilter.value != "all" && dto.paymentMethod != paymentFilter.value) return false
        return when (channelFilter.value) {
            "all" -> true
            "offline" -> dto.channel == null
            "food_apps" -> dto.channel in FOOD_APP_CHANNELS
            "tiktokgo", "tiktok" -> dto.channel in TIKTOK_CHANNELS
            else -> dto.channel == channelFilter.value
        }
    }

    private fun matchesStatusFilter(dto: OrderDto): Boolean =
        statusFilter.value == "all" || dto.status == statusFilter.value

    fun exportToPdf(context: android.content.Context) {
        viewModelScope.launch {
            try {
                val data = analyticsData.value
                val pdfDocument = android.graphics.pdf.PdfDocument()
                val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas
                val paint = android.graphics.Paint()

                paint.textSize = 24f
                paint.isFakeBoldText = true
                canvas.drawText("Laporan Analitik Performa", 50f, 50f, paint)

                paint.textSize = 16f
                paint.isFakeBoldText = false
                canvas.drawText("Omzet Kotor: Rp ${String.format("%,.0f", data.grossRevenue)}", 50f, 100f, paint)
                canvas.drawText("Pesanan Sukses: ${data.totalOrders}", 50f, 130f, paint)

                var y = 170f
                paint.isFakeBoldText = true
                canvas.drawText("Distribusi Pembayaran", 50f, y, paint)
                paint.isFakeBoldText = false
                y += 30f
                data.paymentBreakdown.forEach { (method, stats) ->
                    canvas.drawText("${method.uppercase()}: ${stats.count} pesanan (Rp ${String.format("%,.0f", stats.revenue)})", 50f, y, paint)
                    y += 30f
                }

                pdfDocument.finishPage(page)

                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val file = java.io.File(downloadsDir, "Laporan_POS_${System.currentTimeMillis()}.pdf")
                pdfDocument.writeTo(java.io.FileOutputStream(file))
                pdfDocument.close()

                android.widget.Toast.makeText(context, "PDF berhasil diexport ke Downloads", android.widget.Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                e.printStackTrace()
                android.widget.Toast.makeText(context, "Gagal export PDF", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun String.toLocalDateOrNull(): LocalDate? =
        try { if (isBlank()) null else LocalDate.parse(this) } catch (e: Exception) { null }

    private fun LocalOrderEntity.toOrderDto(): OrderDto {
        val itemsType = object : com.google.gson.reflect.TypeToken<List<com.sukashawarma.pos.domain.model.OrderItem>>() {}.type
        val itemsList: List<com.sukashawarma.pos.domain.model.OrderItem> =
            com.google.gson.Gson().fromJson(itemsJson, itemsType) ?: emptyList()
        val orderItems = itemsList.map {
            OrderItemDto(
                id = it.id,
                orderId = id,
                menuItemId = it.menuItemId,
                menuItemName = it.name,
                quantity = it.quantity,
                unitPrice = it.unitPrice,
                subtotal = it.subtotal
            )
        }

        val dtString = java.time.format.DateTimeFormatter.ISO_INSTANT.format(java.time.Instant.ofEpochMilli(createdAt))
        return OrderDto(
            id = id,
            outletId = outletId,
            orderNumber = orderNumber,
            customerName = customerName,
            status = status.lowercase(),
            source = source.lowercase(),
            paymentMethod = paymentMethod.lowercase(),
            discountAmount = discountAmount,
            promoSubsidy = 0.0,
            totalAmount = totalAmount,
            amountReceived = amountReceived,
            changeAmount = changeAmount,
            kitchenReceiptPrinted = kitchenReceiptPrinted,
            customerReceiptPrinted = customerReceiptPrinted,
            cancellationStatus = cancellationStatus,
            cancellationUserName = cancellationUserName,
            createdAt = dtString,
            channel = channel,
            orderItems = orderItems,
            notes = null,
            paymentProofUrl = localPaymentProofPath,
            cashierName = null,
            cancellationReason = null,
            voidReason = null,
            isSyncedFromOffline = isSyncedFromOffline
        )
    }

    private companion object {
        const val ORDER_LIST_LIMIT = "500"
        val FOOD_APP_CHANNELS = listOf("gofood", "grabfood", "shopeefood", "tiktokgo", "tiktok", "tiktok_go")
        val TIKTOK_CHANNELS = listOf("tiktokgo", "tiktok", "tiktok_go")
    }
}
