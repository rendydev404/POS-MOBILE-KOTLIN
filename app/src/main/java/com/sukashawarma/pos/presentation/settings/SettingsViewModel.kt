package com.sukashawarma.pos.presentation.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sukashawarma.pos.data.bluetooth.BluetoothPrinterManager
import com.sukashawarma.pos.data.local.PrinterPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val printerManager = BluetoothPrinterManager

    // Pair<name, macAddress> — only devices already paired via Android Bluetooth settings.
    val bluetoothDevices = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val selectedPrinterMac = MutableStateFlow(PrinterPrefs.getSelectedMac() ?: "")
    val isTestingPrinter = MutableStateFlow(false)
    val printerStatusMessage = MutableStateFlow<String?>(null)

    val currentOutletName = MutableStateFlow("")

    init {
        refreshPairedDevices()
    }

    fun refreshPairedDevices() {
        bluetoothDevices.value = printerManager.getPairedDevices()
    }

    fun selectPrinter(mac: String) {
        selectedPrinterMac.value = mac
        PrinterPrefs.setSelectedMac(mac)
    }

    fun testPrinterConnection() {
        val mac = selectedPrinterMac.value
        if (mac.isBlank()) {
            printerStatusMessage.value = "Pilih printer dulu."
            return
        }
        viewModelScope.launch {
            isTestingPrinter.value = true
            printerStatusMessage.value = null
            val connected = printerManager.ensureConnected(mac)
            printerStatusMessage.value = if (connected) {
                "Berhasil terhubung ke printer."
            } else {
                "Gagal terhubung. Pastikan printer menyala & sudah di-pairing di pengaturan Bluetooth Android."
            }
            isTestingPrinter.value = false
        }
    }
}
