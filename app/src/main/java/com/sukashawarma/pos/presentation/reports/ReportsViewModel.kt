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
import com.sukashawarma.pos.data.remote.dto.ShiftDto
import com.sukashawarma.pos.domain.usecase.OrderChannel
import com.sukashawarma.pos.domain.usecase.OrderStatusFilter
import com.sukashawarma.pos.domain.usecase.ReportDateRangeResolver
import com.sukashawarma.pos.domain.usecase.ReportRange
import com.sukashawarma.pos.domain.usecase.ResolvedDateRange
import com.sukashawarma.pos.domain.usecase.RevenueCalculator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.merge
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
    private val _customDateError = MutableStateFlow<String?>(null)
    val customDateError = _customDateError.asStateFlow()

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

    /** Pengamat Room yang sedang aktif; dipasang ulang saat rentang/filter berubah. */
    private var localComputeJob: Job? = null
    /** Satu request manual/awal yang sedang berjalan; request terbaru menang. */
    private var reportRefreshJob: Job? = null


    init {
        viewModelScope.launch {
            // Satu subscription WebSocket pusat mengalirkan perubahan pesanan dan
            // shift. collectLatest membatalkan fetch lama saat beberapa perubahan
            // tiba beruntun, sehingga tidak ada polling maupun request bertumpuk.
            merge(
                com.sukashawarma.pos.data.remote.GlobalEventBus.orderSyncEvent,
                com.sukashawarma.pos.data.remote.GlobalEventBus.pettyCashEvent
            ).collectLatest {
                refreshReportData()
            }
        }
    }

    fun setOutlet(outletId: String) {
        _currentOutletId.value = outletId
        observeLocalOrders()
        loadRealReportData()
    }


    fun updateRange(range: DateRange) {
        selectedRange.value = range
        _customDateError.value = null
        if (range != DateRange.CUSTOM) {
            observeLocalOrders()
            loadRealReportData()
        } else if (customStartDate.value.toLocalDateOrNull() != null && customEndDate.value.toLocalDateOrNull() != null) {
            observeLocalOrders()
            loadRealReportData()
        }
    }

    fun updateCustomDate(start: String, end: String) {
        customStartDate.value = start
        customEndDate.value = end
        val startDate = start.toLocalDateOrNull()
        val endDate = end.toLocalDateOrNull()
        _customDateError.value = when {
            startDate == null || endDate == null -> "Pilih tanggal mulai dan tanggal akhir."
            endDate.isBefore(startDate) -> "Tanggal akhir harus sama atau setelah tanggal mulai."
            else -> null
        }
        if (_customDateError.value == null) {
            observeLocalOrders()
            loadRealReportData()
        }
    }

    fun updateFilters(channel: String, payment: String, status: String) {
        channelFilter.value = channel
        paymentFilter.value = payment
        statusFilter.value = status
        // Filter ikut menentukan hasil hitungan, jadi pengamat dipasang ulang.
        observeLocalOrders()
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

    fun loadRealReportData() {
        reportRefreshJob?.cancel()
        reportRefreshJob = viewModelScope.launch { refreshReportData() }
    }

    private suspend fun refreshReportData() {
        val outletId = _currentOutletId.value
        if (outletId.isEmpty()) return

        _isLoading.value = true
        try {
            val range = resolveRange()
            if (NetworkMonitor.isOnline.value) {
                loadFromServer(outletId, range)
            } else {
                loadFromLocalCache(outletId, range)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Omzet diambil dari RPC `pos_revenue_summary_guarded` yang mengagregasi di
     * database. Daftar transaksi tetap ditarik terpisah dan boleh terpotong limit —
     * itu memang hanya tabel, tidak dipakai untuk menghitung angka apa pun.
     */
    private suspend fun loadFromServer(outletId: String, range: ResolvedDateRange) {
        // Angka omzet TIDAK dihitung di sini: [observeLocalOrders] sudah menghitung
        // ulang tiap kali Room berubah. Dulu perhitungannya dipanggil dari sini,
        // dan karena penulis Room dipicu event yang sama secara paralel, laporan
        // membaca Room sebelum data baru masuk — itu sumber "laporan tidak realtime".
        val orders = fetchOrderList(outletId, range)
        val shifts = fetchShifts(outletId, range)

        _analyticsData.value = _analyticsData.value.copy(
            orders = orders,
            shifts = shifts,
            totalCashVariance = shifts.sumOf { it.variance ?: 0.0 },
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
        recomputeFrom(entities)
        _analyticsData.value = _analyticsData.value.copy(
            orders = entities.map { it.toOrderDto() }
                .filter { matchesNonStatusFilters(it) && matchesStatusFilter(it) },
            shifts = emptyList(),
            isFromLocalCache = true
        )
    }

    /**
     * Pasang ulang pengamat Room untuk outlet + rentang + filter yang sedang aktif.
     * Setiap perubahan tabel pesanan langsung menghitung ulang seluruh angka
     * laporan, tanpa menunggu event atau polling.
     */
    private fun observeLocalOrders() {
        localComputeJob?.cancel()
        val outletId = _currentOutletId.value
        if (outletId.isEmpty()) return
        val range = resolveRange()
        localComputeJob = viewModelScope.launch {
            orderDao.observeOrdersByDateRange(outletId, range.startMillis, range.endMillis)
                .collect { entities -> recomputeFrom(entities) }
        }
    }

    private fun recomputeFrom(entities: List<LocalOrderEntity>) {
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
                val curr = itemMap[oi.displayName] ?: ItemStats(0, 0.0)
                itemMap[oi.displayName] = ItemStats(curr.qty + oi.quantity, curr.revenue + oi.subtotal)
            }
        }

        val maxHourly = hourly.maxOrNull() ?: 0

        // `orders`, `shifts`, dan `isFromLocalCache` sengaja dipertahankan: itu
        // urusan jalur server/offline. Kalau ditimpa di sini, daftar transaksi
        // hasil tarikan server akan terhapus tiap kali Room berubah.
        val current = _analyticsData.value
        _analyticsData.value = current.copy(
            grossRevenue = summary.gross,
            totalOrders = summary.orderCount,
            totalItemsSold = itemMap.values.sumOf { it.qty },
            pendingCount = allDtos.count { OrderStatusFilter.matches(OrderStatusFilter.WAITING, it) },
            canceledCount = allDtos.count { OrderStatusFilter.isCancelled(it) },
            paymentBreakdown = paymentBreakdown,
            hourly = hourly,
            dailyEntries = dailyMap.toList().sortedBy { it.first },
            bestSellers = itemMap.toList().sortedByDescending { it.second.qty }.take(10),
            avgOrderValue = if (summary.orderCount > 0) summary.gross / summary.orderCount else 0.0,
            totalCashVariance = current.shifts.sumOf { it.variance ?: 0.0 },
            peakHour = if (maxHourly > 0) hourly.indexOf(maxHourly) else null
        )
    }

    private suspend fun fetchOrderList(outletId: String, range: ResolvedDateRange): List<OrderDto> {
        val filters = mutableMapOf("outlet_id" to "eq.$outletId", "limit" to ORDER_LIST_LIMIT)

        // Semua kondisi masuk ke satu `and=(...)`; kanal "offline" butuh `or`
        // bersarang dan PostgREST hanya menerima satu `or` per level.
        val conditions = mutableListOf<String>()
        range.startIso?.let { conditions += "created_at.gte.$it" }
        range.endIso?.let { conditions += "created_at.lte.$it" }
        if (statusFilter.value != "all") conditions += "status.eq.${statusFilter.value}"
        if (paymentFilter.value != "all") conditions += "payment_method.eq.${paymentFilter.value}"
        OrderChannel.postgrestCondition(channelFilter.value)?.let { conditions += it }
        if (conditions.isNotEmpty()) filters["and"] = "(${conditions.joinToString(",")})"

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
        if (paymentFilter.value != "all" && !dto.paymentMethod.equals(paymentFilter.value, ignoreCase = true)) {
            return false
        }
        return OrderChannel.matches(channelFilter.value, dto.channel)
    }

    private fun matchesStatusFilter(dto: OrderDto): Boolean =
        statusFilter.value == "all" || dto.status == statusFilter.value

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
            cashierName = cashierName,
            cancellationReason = null,
            voidReason = null,
            isSyncedFromOffline = isSyncedFromOffline
        )
    }

    private companion object {
        const val ORDER_LIST_LIMIT = "500"
    }
}
