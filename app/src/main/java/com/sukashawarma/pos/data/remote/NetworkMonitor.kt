package com.sukashawarma.pos.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sumber kebenaran tunggal status jaringan untuk seluruh aplikasi.
 *
 * Memakai callback OS (bukan polling) sehingga perubahan jaringan terdeteksi
 * seketika, dan memakai NET_CAPABILITY_VALIDATED sehingga "WiFi nyala tapi
 * internet mati" dihitung sebagai OFFLINE — lihat [NetworkStatus].
 */
object NetworkMonitor {

    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private var connectivityManager: ConnectivityManager? = null
    private var registered = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            _isOnline.value = NetworkStatus.isValidatedInternet(
                hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            )
        }

        override fun onLost(network: Network) {
            _isOnline.value = false
        }

        override fun onUnavailable() {
            _isOnline.value = false
        }
    }

    /** Dipanggil sekali dari POSApplication.onCreate(). Aman dipanggil berulang. */
    fun init(context: Context) {
        if (registered) return
        val cm = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        connectivityManager = cm

        // Nilai awal sebelum callback pertama datang, supaya UI tidak sempat
        // menampilkan OFFLINE palsu saat aplikasi baru dibuka dalam keadaan online.
        val active = cm.activeNetwork
        val caps = active?.let { cm.getNetworkCapabilities(it) }
        _isOnline.value = caps != null && NetworkStatus.isValidatedInternet(
            hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        )

        cm.registerDefaultNetworkCallback(callback)
        registered = true
    }
}
