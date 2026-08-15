package com.sukashawarma.pos.presentation.kiosk

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sukashawarma.pos.data.remote.SupabaseClient
import com.sukashawarma.pos.data.remote.dto.KioskAccountDto
import com.sukashawarma.pos.data.remote.realtime.KioskPresence
import com.sukashawarma.pos.data.remote.realtime.KioskPresenceRealtimeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

class KioskControlViewModel(application: Application) : AndroidViewModel(application) {
    private val api = SupabaseClient.api
    private var outletId = ""
    private val presenceManager = KioskPresenceRealtimeManager(viewModelScope)

    private val _devices = MutableStateFlow<List<KioskPresence>>(emptyList())
    val devices = _devices.asStateFlow()
    private val _accounts = MutableStateFlow<List<KioskAccountDto>>(emptyList())
    val accounts = _accounts.asStateFlow()
    val isLoadingAccounts = MutableStateFlow(false)
    val busyTarget = MutableStateFlow<String?>(null)
    val message = MutableStateFlow<String?>(null)
    val pairingLink = MutableStateFlow<String?>(null)
    val isRealtimeConnected = MutableStateFlow(false)

    init {
        presenceManager.onPresence = { _devices.value = it }
        presenceManager.onConnection = { isRealtimeConnected.value = it }
    }

    fun setOutlet(value: String) {
        if (outletId == value) return
        outletId = value
        presenceManager.connect(value)
    }

    fun refresh() = presenceManager.refresh()

    fun loadAccounts() = viewModelScope.launch {
        if (outletId.isBlank()) return@launch
        isLoadingAccounts.value = true
        pairingLink.value = null
        try {
            val response = api.getKioskAccounts("https://pos.sukashawarma.com/api/kasir/kiosk-accounts")
            if (response.isSuccessful) {
                _accounts.value = response.body()?.accounts.orEmpty()
                message.value = null
            } else message.value = "Akun device tidak dapat dimuat."
        } catch (_: Exception) {
            message.value = "Tidak dapat menghubungi server device."
        } finally { isLoadingAccounts.value = false }
    }

    fun createPairingLink(account: KioskAccountDto) = viewModelScope.launch {
        busyTarget.value = account.id
        pairingLink.value = null
        try {
            val response = api.generateKioskQr(
                "https://pos.sukashawarma.com/api/kasir/generate-kiosk-qr",
                mapOf("kiosk_id" to account.id)
            )
            pairingLink.value = response.body()?.actionLink
            if (!response.isSuccessful || pairingLink.value.isNullOrBlank()) message.value = "Gagal membuat akses QR device."
        } catch (_: Exception) { message.value = "Tidak dapat membuat akses QR device." }
        finally { busyTarget.value = null }
    }

    fun logout(target: String, label: String) = viewModelScope.launch {
        busyTarget.value = target
        try {
            val response = api.logoutKiosk("https://pos.sukashawarma.com/api/kiosk/logout", mapOf("target" to target))
            message.value = if (response.isSuccessful) "$label berhasil di-logout." else {
                val body = response.errorBody()?.string()
                try { JSONObject(body ?: "").optString("error", "Gagal logout device.") } catch (_: Exception) { "Gagal logout device." }
            }
        } catch (_: Exception) { message.value = "Tidak dapat menghubungi server device." }
        finally { busyTarget.value = null }
    }

    fun clearPairingLink() { pairingLink.value = null }
    fun clearMessage() { message.value = null }

    override fun onCleared() { presenceManager.disconnect() }
}
