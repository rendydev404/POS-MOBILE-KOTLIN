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
import com.sukashawarma.pos.domain.usecase.OrderChannel
import com.sukashawarma.pos.domain.usecase.OrderStatusFilter
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

    // Pill status; nilainya konstanta di OrderStatusFilter, bukan literal lepas.
    val selectedPaymentFilter = MutableStateFlow(OrderStatusFilter.ALL)

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

                // `order` sengaja tidak diisi di sini: getOrders() sudah punya default
                // `created_at.desc`, dan mengirimnya lagi lewat QueryMap membuat
                // parameter itu muncul dua kali di URL.
                val filters = mutableMapOf(
                    "outlet_id" to "eq.$outletId",
                    "limit" to ORDER_LIST_LIMIT
                )

                val statusFilter = selectedPaymentFilter.value
                val paymentMethod = selectedPaymentMethodFilter.value
                val channel = selectedChannelFilter.value

                // Semua kondisi digabung ke satu `and=(...)`. Sebuah Map tidak bisa
                // memuat dua entri "created_at" (gte dan lte), dan PostgREST hanya
                // menerima satu `or` per level — sementara pill "Dibatalkan" dan
                // kanal "Offline" sama-sama butuh `or`. Menyatukannya di sini membuat
                // kombinasi filter apa pun tetap benar.
                val conditions = mutableListOf<String>()
                if (startIso != null) conditions += "created_at.gte.$startIso"
                if (endIso != null) conditions += "created_at.lte.$endIso"
                OrderStatusFilter.postgrestCondition(statusFilter)?.let { conditions += it }
                OrderChannel.postgrestCondition(channel)?.let { conditions += it }
                if (paymentMethod != "all") conditions += "payment_method.eq.$paymentMethod"

                if (conditions.isNotEmpty()) {
                    filters["and"] = "(${conditions.joinToString(",")})"
                }

                val startMillis = range.startMillis
                val endMillis = range.endMillis

                // Start with empty to show loading state effectively
                ordersHistory.value = emptyList()

                if (NetworkMonitor.isOnline.value) {
                    loadRevenueSummary(outletId, range, paymentMethod, channel)
                    val response = api.getOrders(filters)
                    if (response.isSuccessful && response.body() != null) {
                        val dtos = response.body()!!.filter { dto ->
                            matchesInRange(dto, startMillis, endMillis) &&
                                matchesFilters(dto, statusFilter, paymentMethod, channel)
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
                    ordersHistory.value = cached.filter {
                        matchesFilters(it, statusFilter, paymentMethod, channel)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading.value = false
            }
        }
    }

    /**
     * Penjaga rentang tanggal di sisi klien.
     *
     * `Instant.parse` yang dipakai sebelumnya selalu melempar untuk timestamp
     * ber-offset seperti `2026-08-06T10:00:00+07:00`, dan pengecualiannya ditelan
     * menjadi "lolos" — jadi penyaring ini sebenarnya tidak pernah bekerja.
     */
    private fun matchesInRange(dto: OrderDto, startMillis: Long, endMillis: Long): Boolean {
        val millis = com.sukashawarma.pos.domain.gate.JakartaTime
            .instantOrNull(dto.createdAt)?.toEpochMilli() ?: return true
        return millis in startMillis..endMillis
    }

    /** Sepadan dengan kondisi yang dikirim ke server; boleh mempersempit, tidak melebarkan. */
    private fun matchesFilters(
        dto: OrderDto,
        statusFilter: String,
        paymentMethod: String,
        channel: String
    ): Boolean {
        if (!OrderStatusFilter.matches(statusFilter, dto)) return false
        if (paymentMethod != "all" && !dto.paymentMethod.equals(paymentMethod, ignoreCase = true)) return false
        return OrderChannel.matches(channel, dto.channel)
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
        // Karena migrasi `20260806150000_revenue_summary.sql` mungkin belum
        // diaplikasikan ke production server oleh user, server akan menghitung
        // pesanan yang 'dibatalkan' sebagai omzet.
        // Karenanya, kita BAPAS RPC dan langsung menghitung dari lokal.
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
            if (paymentMethod != "all" && !dto.paymentMethod.equals(paymentMethod, ignoreCase = true)) {
                return@filter false
            }
            OrderChannel.matches(channel, dto.channel)
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
            pendingCount = scoped.count { OrderStatusFilter.matches(OrderStatusFilter.WAITING, it) },
            cancelledCount = scoped.count { OrderStatusFilter.isCancelled(it) },
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

    private companion object {
        /**
         * Batas baris untuk tabel transaksi saja. Dulu "Semua Waktu" dibatasi 100
         * baris sehingga daftarnya tampak terpotong sembarangan. Angka ringkasan
         * tidak lagi bergantung pada daftar ini — semuanya dari RPC.
         */
        const val ORDER_LIST_LIMIT = "500"
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
