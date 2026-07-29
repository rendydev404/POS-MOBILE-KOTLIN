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
                val response = api.getOrders(mapOf("outlet_id" to "eq.${currentOutletId.value}"))
                if (response.isSuccessful && response.body() != null) {
                    ordersHistory.value = response.body()!!
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading.value = false
            }
        }
    }

    val monthlyBonusResult = MutableStateFlow<com.sukashawarma.pos.data.remote.dto.MonthlyBonusResultDto?>(null)
    val dailyBonusBreakdown = MutableStateFlow<List<com.sukashawarma.pos.data.remote.dto.DailyBonusBreakdownDto>>(emptyList())

    fun fetchBonusData(month: Int, year: Int) {
        val outletId = currentOutletId.value
        if (outletId.isBlank()) return
        viewModelScope.launch {
            isLoading.value = true
            try {
                val payload = com.sukashawarma.pos.data.remote.dto.MonthlyBonusPayload(month, year, outletId)
                val monthlyRes = api.calculateMonthlyCrewBonus(payload)
                if (monthlyRes.isSuccessful && !monthlyRes.body().isNullOrEmpty()) {
                    monthlyBonusResult.value = monthlyRes.body()!!.first()
                } else {
                    monthlyBonusResult.value = null
                }

                val dailyRes = api.getDailyBonusBreakdown(payload)
                if (dailyRes.isSuccessful && dailyRes.body() != null) {
                    dailyBonusBreakdown.value = dailyRes.body()!!
                } else {
                    dailyBonusBreakdown.value = emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading.value = false
            }
        }
    }
}
