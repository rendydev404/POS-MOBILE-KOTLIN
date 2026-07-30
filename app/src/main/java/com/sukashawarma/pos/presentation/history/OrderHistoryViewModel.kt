package com.sukashawarma.pos.presentation.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sukashawarma.pos.data.remote.SupabaseClient
import com.sukashawarma.pos.data.remote.dto.OrderDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class OrderHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val api = SupabaseClient.api

    val currentOutletId = MutableStateFlow("")
    val ordersHistory = MutableStateFlow<List<OrderDto>>(emptyList())
    val searchQuery = MutableStateFlow("")
    val selectedPaymentFilter = MutableStateFlow("Semua")
    val isLoading = MutableStateFlow(false)

    fun setOutlet(outletId: String) {
        currentOutletId.value = outletId
        fetchOrderHistory()
    }

    fun fetchOrderHistory() {
        viewModelScope.launch {
            isLoading.value = true
            try {
                val today = java.time.LocalDate.now(java.time.ZoneId.systemDefault())
                val response = api.getOrders(mapOf(
                    "outlet_id" to "eq.${currentOutletId.value}",
                    "order" to "created_at.desc",
                    "limit" to "200"
                ))
                if (response.isSuccessful && response.body() != null) {
                    val filtered = response.body()!!.filter {
                        try {
                            val iso = it.createdAt
                            val instant = java.time.Instant.parse(if (iso.endsWith("Z") || iso.contains("+")) iso else "${iso}Z")
                            instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate() == today
                        } catch(e: Exception) { false }
                    }
                    ordersHistory.value = filtered
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading.value = false
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
}
