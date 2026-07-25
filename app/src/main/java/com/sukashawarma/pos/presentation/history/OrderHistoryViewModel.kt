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
                val response = api.getOrders("eq.${currentOutletId.value}")
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
}
