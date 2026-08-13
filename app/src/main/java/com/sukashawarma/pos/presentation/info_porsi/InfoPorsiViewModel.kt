package com.sukashawarma.pos.presentation.info_porsi

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sukashawarma.pos.data.remote.SupabaseClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

data class PorsiInfo(
    val menuName: String,
    val portions: Int
)

class InfoPorsiViewModel(application: Application) : AndroidViewModel(application) {

    private val api = SupabaseClient.api

    val isLoading = MutableStateFlow(false)
    val porsiList = MutableStateFlow<List<PorsiInfo>>(emptyList())
    val errorMessage = MutableStateFlow<String?>(null)
    
    val currentOutletId = MutableStateFlow("")

    init {
        viewModelScope.launch {
            currentOutletId.collect { outletId ->
                if (outletId.isNotBlank()) {
                    loadPorsi(outletId)
                }
            }
        }
        // Proyeksi porsi bergerak mengikuti stok dan pesanan yang masuk; tanpa ini
        // angkanya beku sampai halaman dibuka ulang.
        viewModelScope.launch {
            com.sukashawarma.pos.data.remote.GlobalEventBus.stockEvent.collect { loadPorsi() }
        }
        viewModelScope.launch {
            com.sukashawarma.pos.data.remote.GlobalEventBus.orderSyncEvent.collect { loadPorsi() }
        }
    }

    fun loadPorsi(outletId: String = currentOutletId.value) {
        if (outletId.isBlank()) return
        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null
            try {
                val response = api.getMonitoringViewCrew("eq.$outletId")
                if (response.isSuccessful) {
                    val dtos = response.body() ?: emptyList()
                    val map = mutableMapOf<String, Int>()

                    dtos.forEach { dto ->
                        dto.projectionText?.split(" atau ")?.forEach { part ->
                            val regex = """(.*?)\s*\((\d+)\s*porsi\)""".toRegex()
                            val match = regex.find(part)
                            if (match != null) {
                                val name = match.groupValues[1].trim()
                                val qty = match.groupValues[2].toIntOrNull() ?: 0
                                if (map.containsKey(name)) {
                                    map[name] = minOf(map[name]!!, qty)
                                } else {
                                    map[name] = qty
                                }
                            }
                        }
                    }

                    porsiList.value = map.map { PorsiInfo(it.key, it.value) }
                        .sortedWith(compareBy({ it.portions }, { it.menuName }))
                } else {
                    errorMessage.value = "Gagal memuat data porsi"
                }
            } catch (e: Exception) {
                errorMessage.value = e.message
            } finally {
                isLoading.value = false
            }
        }
    }
}
