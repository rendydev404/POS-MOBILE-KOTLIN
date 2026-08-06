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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

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

    private fun isoInstantOf(date: LocalDate, time: LocalTime): String {
        return date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toString()
    }

    fun fetchOrderHistory() {
        val outletId = currentOutletId.value
        if (outletId.isBlank()) return

        viewModelScope.launch {
            isLoading.value = true
            try {
                val today = LocalDate.now(ZoneId.systemDefault())
                var startIso: String? = null
                var endIso: String? = null
                val filter = dateFilter.value

                when (filter) {
                    "today" -> {
                        startIso = isoInstantOf(today, LocalTime.MIDNIGHT)
                    }
                    "yesterday" -> {
                        val yesterday = today.minusDays(1)
                        startIso = isoInstantOf(yesterday, LocalTime.MIDNIGHT)
                        endIso = isoInstantOf(today, LocalTime.MIDNIGHT)
                    }
                    "7d" -> {
                        startIso = isoInstantOf(today.minusDays(7), LocalTime.MIDNIGHT)
                    }
                    "30d" -> {
                        startIso = isoInstantOf(today.minusDays(30), LocalTime.MIDNIGHT)
                    }
                    "custom" -> {
                        val start = customStartDate.value
                        val end = customEndDate.value
                        if (start == null || end == null) {
                            isLoading.value = false
                            return@launch
                        }
                        startIso = isoInstantOf(start, LocalTime.MIDNIGHT)
                        endIso = isoInstantOf(end, LocalTime.of(23, 59, 59, 999_000_000))
                    }
                    // "all" -> no date bounds
                }

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

                val startMillis = if (startIso != null) java.time.Instant.parse(startIso).toEpochMilli() else 0L
                val endMillis = if (endIso != null) java.time.Instant.parse(endIso).toEpochMilli() else Long.MAX_VALUE

                if (NetworkMonitor.isOnline.value) {
                    val response = api.getOrders(filters)
                    if (response.isSuccessful && response.body() != null) {
                        val dtos = response.body()!!
                        val offlineSyncedIds = orderDao.getSyncedFromOfflineIds(currentOutletId.value).toSet()
                        ordersHistory.value = dtos.map { 
                            if (offlineSyncedIds.contains(it.id)) it.copy(isSyncedFromOffline = true) else it 
                        }
                    }
                } else {
                    val entities = orderDao.getOrdersByDateRange(startMillis, endMillis)
                    val dtos = entities.map { it.toOrderDto() }.filter { dto ->
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
