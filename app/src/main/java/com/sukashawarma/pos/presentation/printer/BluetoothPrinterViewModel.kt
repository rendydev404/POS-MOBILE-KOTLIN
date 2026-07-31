package com.sukashawarma.pos.presentation.printer

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sukashawarma.pos.data.bluetooth.BluetoothPrinterManager
import com.sukashawarma.pos.presentation.order_manual.OrderSuccessInfo
import com.sukashawarma.pos.presentation.printer.ReceiptGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

enum class ConnectionStatus {
    DISCONNECTED, CONNECTING, CONNECTED, ERROR
}

class BluetoothPrinterViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs: SharedPreferences = application.getSharedPreferences("PrinterPrefs", Context.MODE_PRIVATE)
    // BluetoothPrinterManager is now a singleton
    
    val pairedDevices = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val discoveredDevices = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    
    val connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectedDeviceName = MutableStateFlow<String?>(null)
    val errorMessage = MutableStateFlow<String?>(null)
    
    val isScanning = MutableStateFlow(false)

    init {
        loadPairedDevices()
        autoConnectIfSaved()
    }

    fun refreshPairedDevices() {
        pairedDevices.value = BluetoothPrinterManager.getPairedDevices()
    }

    fun loadPairedDevices() {
        refreshPairedDevices()
    }

    fun autoConnectIfSaved() {
        val savedMac = prefs.getString("saved_mac", null)
        val savedName = prefs.getString("saved_name", null)
        if (savedMac != null && savedName != null) {
            connectToDevice(savedName, savedMac)
        }
    }

    fun connectToDevice(name: String, macAddress: String) {
        if (connectionStatus.value == ConnectionStatus.CONNECTING) return
        
        viewModelScope.launch {
            connectionStatus.value = ConnectionStatus.CONNECTING
            errorMessage.value = null
            
            val success = BluetoothPrinterManager.connectPrinter(macAddress)
            if (success) {
                connectionStatus.value = ConnectionStatus.CONNECTED
                connectedDeviceName.value = name
                prefs.edit().putString("saved_mac", macAddress).putString("saved_name", name).apply()
                com.sukashawarma.pos.data.local.PrinterPrefs.setSelectedMac(macAddress)
            } else {
                connectionStatus.value = ConnectionStatus.ERROR
                errorMessage.value = "Gagal menyambung ke $name"
            }
        }
    }
    
    fun disconnect() {
        BluetoothPrinterManager.closeConnection()
        connectionStatus.value = ConnectionStatus.DISCONNECTED
        connectedDeviceName.value = null
        prefs.edit().remove("saved_mac").remove("saved_name").apply()
        com.sukashawarma.pos.data.local.PrinterPrefs.setSelectedMac("")
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery(context: Context) {
        try {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
                val isLocationEnabled = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    locationManager.isLocationEnabled
                } else {
                    val mode = android.provider.Settings.Secure.getInt(context.contentResolver, android.provider.Settings.Secure.LOCATION_MODE, android.provider.Settings.Secure.LOCATION_MODE_OFF)
                    mode != android.provider.Settings.Secure.LOCATION_MODE_OFF
                }
                if (!isLocationEnabled) {
                    errorMessage.value = "Aktifkan GPS/Lokasi perangkat untuk mencari printer."
                    return
                }
            }

            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
            discoveredDevices.value = emptyList()
            errorMessage.value = null
            isScanning.value = true
            adapter.startDiscovery()
        } catch (e: SecurityException) {
            e.printStackTrace()
            isScanning.value = false
            errorMessage.value = "Izin Bluetooth belum diberikan."
        }
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
            isScanning.value = false
        } catch (e: SecurityException) {
            e.printStackTrace()
            isScanning.value = false
        }
    }

    // Call this from the UI when dialog is opened to register receiver
    fun registerReceiver(context: Context) {
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        context.registerReceiver(receiver, filter)
    }

    // Call this from the UI when dialog is closed to unregister receiver
    fun unregisterReceiver(context: Context) {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {
            // ignore
        }
    }

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        val name = it.name ?: "Unknown Device"
                        val mac = it.address
                        val list = discoveredDevices.value.toMutableList()
                        // Ensure no duplicates
                        if (list.none { existing -> existing.second == mac } && pairedDevices.value.none { existing -> existing.second == mac }) {
                            list.add(Pair(name, mac))
                            discoveredDevices.value = list
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    isScanning.value = false
                }
            }
        }
    }
    
    fun printTest() {
        viewModelScope.launch {
            if (connectionStatus.value == ConnectionStatus.CONNECTED) {
                // Simple test print
                val testBytes = byteArrayOf(
                    0x1B, 0x40, // init
                    0x1B, 0x61, 0x01, // center
                    0x1D, 0x21, 0x11, // double size
                    *("TEST PRINT\n").toByteArray(),
                    0x1D, 0x21, 0x00, // normal size
                    *("Suka Shawarma\n\n\n\n\n".toByteArray()),
                    0x1D, 0x56, 0x41, 0x00 // cut
                )
                BluetoothPrinterManager.printBytesChunked(testBytes)
            }
        }
    }

    // Added wrapper for use in view model when actual receipt bytes are generated
    suspend fun printBytes(bytes: ByteArray): Boolean {
        if (connectionStatus.value != ConnectionStatus.CONNECTED) return false
        return BluetoothPrinterManager.printBytesChunked(bytes)
    }



    suspend fun printReceiptAsync(context: android.content.Context, info: OrderSuccessInfo, cashierName: String, isKitchen: Boolean): Boolean {
        if (connectionStatus.value != ConnectionStatus.CONNECTED) return false
        
        val bytes = if (isKitchen) {
            ReceiptGenerator.generateKitchenReceipt(info, cashierName)
        } else {
            ReceiptGenerator.generateCustomerReceipt(context, info, cashierName)
        }
        return BluetoothPrinterManager.printBytesChunked(bytes)
    }
}
