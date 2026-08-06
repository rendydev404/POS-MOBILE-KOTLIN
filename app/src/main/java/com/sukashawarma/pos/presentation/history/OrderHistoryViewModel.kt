package com.sukashawarma.pos.presentation.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sukashawarma.pos.POSApplication
import com.sukashawarma.pos.data.local.entity.LocalOrderEntity
import com.sukashawarma.pos.data.remote.NetworkMonitor
import com.sukashawarma.pos.data.remote.SupabaseClient
import com.sukashawarma.pos.data.remote.dto.OrderDto
import com.sukashawarma.pos.data.remote.dto.OrderItemDto
import com.sukashawarma.pos.data.remote.dto.RevenueByPaymentDto
import com.sukashawarma.pos.data.remote.dto.RevenueSummaryDto
import com.sukashawarma.pos.data.remote.dto.RevenueSummaryPayload
import com.sukashawarma.pos.domain.usecase.ReportDateRangeResolver
import com.sukashawarma.pos.domain.usecase.ReportRange
import com.sukashawarma.pos.domain.usecase.ResolvedDateRange
import com.sukashawarma.pos.domain.usecase.RevenueCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class OrderHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val database = (application as POSApplication).database
    private val orderDao = database.orderDao()
    private val api = SupabaseClient.api

    val currentOutletId = MutableStateFlow("")
    val ordersHistory = MutableStateFlow<List<OrderDto>>(emptyList())
    val searchQuery = MutableStateFlow("")

    // Status filter pill: "Semua" / "Selesai" / "Menunggu" / "Dibatalkan"
    val selectedPaymentFilter = MutableStateFlow("Semua")

    // Date-range filter: "today" / "yesterday" / "7d" / "30d" / "all" / "custom"
    val dateFilter = MutableStateFlow("today")
    val customStartDate = MutableStateFlow<LocalDate?>(null)
    val customEndDate = MutableStateFlow<LocalDate?>(null)

    // Payment-method dropdown filter: "all" / "cash" / "qris" / "card"
    val selectedPaymentMethodFilter = MutableStateFlow("all")

    // Channel dropdown filter: "all" / "offline" / <channel id>
    val selectedChannelFilter = MutableStateFlow("all")

    val isLoading = MutableStateFlow(false)

    /**
     * Omzet kotor untuk rentang & filter yang sedang aktif, diagregasi di database.
     *
     * Kartu ringkasan dulu menjumlahkan [ordersHistory] di layar, padahal daftar itu
     * dibatasi 100-200 baris — omzet ikut terpotong tanpa ada tandanya.
     */
    val revenueSummary = MutableStateFlow(RevenueSummaryDto())

    /** True bila [revenueSummary] dihitung dari cache Room, bukan dari server. */
    val isSummaryFromLocalCache = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            // Status pembatalan berubah di server -> riwayat ikut berubah tanpa refresh manual.
            com.sukashawarma.pos.data.remote.GlobalEventBus.orderSyncEvent.collect {
                fetchOrderHistory()
            }
        }
    }

    fun setOutlet(outletId: String) {
        currentOutletId.value = outletId
        fetchOrderHistory()
    }

    /**
     * Rentang tanggal selalu dihitung di Asia/Jakarta lewat [ReportDateRangeResolver].
     * Sebelumnya halaman ini memakai zona perangkat sementara Dashboard dan Laporan
     * memakai Jakarta, sehingga "hari ini" bisa berbeda antar halaman.
     */
    private fun resolveRange(): ResolvedDateRange = ReportDateRangeResolver.resolve(
        range = ReportRange.fromHistoryKey(dateFilter.value),
        customStart = customStartDate.value,
        customEnd = customEndDate.value
    )

    fun fetchOrderHistory() {
        val outletId = currentOutletId.value
        if (outletId.isBlank()) return

        val filter = dateFilter.value
        if (filter == "custom" && (customStartDate.value == null || customEndDate.value == null)) return

        viewModelScope.launch {
            isLoading.value = true
            try {
                val range = resolveRange()
                val startIso = range.startIso
                val endIso = range.endIso

                val filters = mutableMapOf(
                    "outlet_id" to "eq.$outletId",
                    "order" to "created_at.desc",
                    "limit" to if (filter == "all") "100" else "200"
                )

                val statusFilter = selectedPaymentFilter.value
                when (statusFilter) {
                    "Selesai" -> filters["status"] = "eq.completed"
                    "Menunggu" -> filters["status"] = "eq.pending"
                    "Dibatalkan" -> filters["status"] = "eq.cancelled"
                }

                val paymentMethod = selectedPaymentMethodFilter.value
                if (paymentMethod != "all") {
                    filters["payment_method"] = "eq.$paymentMethod"
                }

                val channel = selectedChannelFilter.value
                if (channel != "all") {
                    filters["channel"] = if (channel == "offline") "is.null" else "eq.$channel"
                }

                // A plain Map can't hold two "created_at" entries (one gte, one lte), so
                // when both bounds are present they're combined via PostgREST's "and"
                // combinator instead of two separate query params.
                if (startIso != null && endIso != null) {
                    filters["and"] = "(created_at.gte.$startIso,created_at.lte.$endIso)"
                } else if (startIso != null) {
                    filters["created_at"] = "gte.$startIso"
                } else if (endIso != null) {
                    filters["created_at"] = "lte.$endIso"
                }

                val startMillis = range.startMillis
                val endMillis = range.endMillis

                // Start with empty to show loading state effectively
                ordersHistory.value = emptyList()

                if (NetworkMonitor.isOnline.value) {
                    loadRevenueSummary(outletId, range, paymentMethod, channel)
                    val response = api.getOrders(filters)
                    if (response.isSuccessful && response.body() != null) {
                        var dtos = response.body()!!
                        
                        // Apply local date filter fallback
                        if (startIso != null || endIso != null) {
                            dtos = dtos.filter { dto ->
                                try {
                                    val dtoMillis = java.time.Instant.parse(dto.createdAt).toEpochMilli()
                                    dtoMillis in startMillis..endMillis
                                } catch (e: Exception) {
                                    true
                                }
                            }
                        }
                        
                        // Apply other filters locally as fallback
                        dtos = dtos.filter { dto ->
                            var match = true
                            if (statusFilter != "Semua") {
                                val targetStatus = when (statusFilter) {
                                    "Selesai" -> "completed"
                                    "Menunggu" -> "pending"
                                    "Dibatalkan" -> "cancelled"
                                    else -> ""
                                }
                                if (dto.status != targetStatus) match = false
                            }
                            if (paymentMethod != "all" && dto.paymentMethod != paymentMethod) match = false
                            if (channel != "all") {
                                if (channel == "offline" && dto.channel != null) match = false
                                if (channel != "offline" && dto.channel != channel) match = false
                            }
                            match
                        }

                        val offlineSyncedIds = orderDao.getSyncedFromOfflineIds(currentOutletId.value).toSet()
                        ordersHistory.value = dtos.map { 
                            if (offlineSyncedIds.contains(it.id)) it.copy(isSyncedFromOffline = true) else it 
                        }
                    }
                } else {
                    // Sekali parse saja — toOrderDto() men-deserialisasi itemsJson tiap panggilan.
                    val cached = orderDao.getOrdersByDateRange(outletId, startMillis, endMillis)
                        .map { it.toOrderDto() }
                    summarizeFromLocalCache(cached, paymentMethod, channel)
                    val dtos = cached.filter { dto ->
                        var match = true
                        if (statusFilter != "Semua") {
                            val targetStatus = when (statusFilter) {
                                "Selesai" -> "completed"
                                "Menunggu" -> "pending"
                                "Dibatalkan" -> "cancelled"
                                else -> ""
                            }
                            if (dto.status != targetStatus) match = false
                        }
                        if (paymentMethod != "all" && dto.paymentMethod != paymentMethod) match = false
                        if (channel != "all") {
                            if (channel == "offline" && dto.channel != null) match = false
                            if (channel != "offline" && dto.channel != channel) match = false
                        }
                        match
                    }
                    ordersHistory.value = dtos
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading.value = false
            }
        }
    }

    /**
     * Filter status pesanan sengaja TIDAK ikut dikirim: omzet menurut definisinya
     * hanya menghitung pesanan `completed`, jadi memilih pill "Menunggu" atau
     * "Dibatalkan" tidak boleh membuat omzet berubah — itu hanya menyaring tabel.
     */
    private suspend fun loadRevenueSummary(
        outletId: String,
        range: ResolvedDateRange,
        paymentMethod: String,
        channel: String
    ) {
        try {
            val res = api.getRevenueSummary(
                RevenueSummaryPayload(
                    outletId = outletId,
                    start = range.startIso,
                    end = range.endIso,
                    paymentMethod = paymentMethod.takeIf { it != "all" },
                    channels = if (channel == "all" || channel == "offline") null else listOf(channel),
                    includeNullChannel = channel == "offline"
                )
            )
            val body = res.body()
            if (body != null) {
                revenueSummary.value = body
                isSummaryFromLocalCache.value = false
                return
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // RPC gagal — lebih baik pakai angka lokal bertanda daripada menampilkan Rp 0.
        val entities = orderDao.getOrdersByDateRange(outletId, range.startMillis, range.endMillis)
        summarizeFromLocalCache(entities.map { it.toOrderDto() }, paymentMethod, channel)
    }

    /** Rumusnya sama persis dengan RPC, hanya sumber datanya cache Room. */
    private fun summarizeFromLocalCache(
        dtos: List<OrderDto>,
        paymentMethod: String,
        channel: String
    ) {
        val scoped = dtos.filter { dto ->
            if (paymentMethod != "all" && dto.paymentMethod != paymentMethod) return@filter false
            when (channel) {
                "all" -> true
                "offline" -> dto.channel == null
                else -> dto.channel == channel
            }
        }
        val completed = scoped.filter { RevenueCalculator.isRevenue(it) }
        val summary = RevenueCalculator.summarize(scoped)

        revenueSummary.value = RevenueSummaryDto(
            gross = summary.gross,
            net = summary.net,
            deductions = summary.deductions,
            orderCount = summary.orderCount,
            itemsSold = summary.itemsSold,
            avgOrderGross = if (summary.orderCount > 0) summary.gross / summary.orderCount else 0.0,
            pendingCount = scoped.count { it.status == "pending" },
            cancelledCount = scoped.count { it.status == "cancelled" },
            byPayment = completed
                .groupBy { it.paymentMethod ?: "unknown" }
                .map { (method, list) ->
                    RevenueByPaymentDto(
                        paymentMethod = method,
                        gross = list.sumOf { RevenueCalculator.grossOf(it) },
                        count = list.size
                    )
                }
        )
        isSummaryFromLocalCache.value = true
    }

    fun updateOrderStatus(orderId: String, status: String) {
        viewModelScope.launch {
            try {
                val response = api.updateOrderStatus(
                    orderIdFilter = "eq.$orderId",
                    patch = mapOf(
                        "status" to status,
                        "updated_at" to java.time.Instant.now().toString()
                    )
                )
                if (response.isSuccessful) {
                    fetchOrderHistory()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val activeCrewCount = MutableStateFlow(0)
    val simulatedCrewCount = MutableStateFlow(0)
    val dailyBonusBreakdown = MutableStateFlow<List<com.sukashawarma.pos.data.remote.dto.DailyBonusBreakdownDto>>(emptyList())

    val totalDaysReached = MutableStateFlow(0)
    val totalBonusPool = MutableStateFlow(0.0)

    fun fetchBonusData(month: Int, year: Int) {
        val outletId = currentOutletId.value
        if (outletId.isBlank()) return
        viewModelScope.launch {
            isLoading.value = true
            try {
                val payload = com.sukashawarma.pos.data.remote.dto.MonthlyBonusPayload(month, year, outletId)

                // Fetch daily breakdown
                val dailyRes = api.getDailyBonusBreakdown(payload)
                var dailyData = emptyList<com.sukashawarma.pos.data.remote.dto.DailyBonusBreakdownDto>()
                if (dailyRes.isSuccessful && dailyRes.body() != null) {
                    dailyData = dailyRes.body()!!
                    dailyBonusBreakdown.value = dailyData
                } else {
                    dailyBonusBreakdown.value = emptyList()
                }

                // Calculate summary stats
                totalDaysReached.value = dailyData.count { it.isReached }
                totalBonusPool.value = dailyData.filter { it.isReached }.sumOf { (it.additionalItems * it.perItemBonus) }

                // Fetch active crew
                val monthlyRes = api.calculateMonthlyCrewBonus(payload)
                if (monthlyRes.isSuccessful && monthlyRes.body() != null) {
                    val count = monthlyRes.body()!!.size
                    activeCrewCount.value = count
                    if (count > 0 && simulatedCrewCount.value == 0) {
                        simulatedCrewCount.value = count
                    }
                } else {
                    activeCrewCount.value = 0
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading.value = false
            }
        }
    }

    private fun LocalOrderEntity.toOrderDto(): OrderDto {
        val itemsType = object : com.google.gson.reflect.TypeToken<List<com.sukashawarma.pos.domain.model.OrderItem>>() {}.type
        val itemsList: List<com.sukashawarma.pos.domain.model.OrderItem> = com.google.gson.Gson().fromJson(itemsJson, itemsType) ?: emptyList()
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
}
