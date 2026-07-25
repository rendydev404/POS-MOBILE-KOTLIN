package com.sukashawarma.pos.presentation.reports

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sukashawarma.pos.data.remote.SupabaseClient
import com.sukashawarma.pos.data.remote.dto.OrderDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

data class CrewBonusItem(
    val crewName: String,
    val role: String,
    val daysTargetAchieved: Int,
    val bonusAmountPerCrew: Double
)

class ReportsViewModel(application: Application) : AndroidViewModel(application) {
    private val api = SupabaseClient.api

    val currentOutletId = MutableStateFlow("")
    val totalSalesToday = MutableStateFlow(0.0)
    val totalOrdersCount = MutableStateFlow(0)
    val cashSales = MutableStateFlow(0.0)
    val qrisSales = MutableStateFlow(0.0)
    val cardSales = MutableStateFlow(0.0)

    val dailyTargetAmount = MutableStateFlow(2000000.0)
    val dailyBonusPool = MutableStateFlow(150000.0)

    val crewBonusList = MutableStateFlow<List<CrewBonusItem>>(emptyList())
    val isLoading = MutableStateFlow(false)

    fun setOutlet(outletId: String) {
        currentOutletId.value = outletId
        loadRealReportData()
    }

    fun loadRealReportData() {
        viewModelScope.launch {
            isLoading.value = true
            try {
                // 1. Fetch real orders today from Supabase
                val ordersRes = api.getOrders("eq.${currentOutletId.value}")
                if (ordersRes.isSuccessful && ordersRes.body() != null) {
                    val orders = ordersRes.body()!!
                    totalOrdersCount.value = orders.size
                    totalSalesToday.value = orders.sumOf { it.totalAmount }

                    cashSales.value = orders.filter { it.paymentMethod.equals("cash", ignoreCase = true) }.sumOf { it.totalAmount }
                    qrisSales.value = orders.filter { it.paymentMethod.equals("qris", ignoreCase = true) }.sumOf { it.totalAmount }
                    cardSales.value = orders.filter { it.paymentMethod.equals("card", ignoreCase = true) || it.paymentMethod.equals("edc", ignoreCase = true) }.sumOf { it.totalAmount }
                }

                // Target omset harian real (RPC get_my_target_progress, lihat daily_sales_targets)
                val targetRes = api.getMyTargetProgress()
                if (targetRes.isSuccessful && !targetRes.body().isNullOrEmpty()) {
                    dailyTargetAmount.value = targetRes.body()!!.first().targetAmount
                }
                // TODO(Fase berikutnya): crewBonusList (pembagian bonus crew) belum ada RPC-nya di database — placeholder.
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading.value = false
            }
        }
    }
}
