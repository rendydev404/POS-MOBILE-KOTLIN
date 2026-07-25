package com.sukashawarma.pos.data.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

class BluetoothPrinterManager {
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var connectedAddress: String? = null

    /** Devices already paired via Android system Bluetooth settings (name to MAC address). */
    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<Pair<String, String>> {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()
        return try {
            adapter.bondedDevices.map { device -> Pair(device.name ?: device.address, device.address) }
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    /** Connects only if not already connected to this exact printer. */
    suspend fun ensureConnected(deviceAddress: String): Boolean {
        if (isConnected() && connectedAddress == deviceAddress) return true
        return connectPrinter(deviceAddress)
    }

    @SuppressLint("MissingPermission")
    suspend fun connectPrinter(deviceAddress: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter() ?: return@withContext false
            val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(deviceAddress)

            bluetoothAdapter.cancelDiscovery()
            val socket = device.createRfcommSocketToServiceRecord(sppUuid)
            socket.connect()

            bluetoothSocket = socket
            outputStream = socket.outputStream
            connectedAddress = deviceAddress
            true
        } catch (e: Exception) {
            e.printStackTrace()
            closeConnection()
            false
        }
    }

    suspend fun printBytesChunked(bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val stream = outputStream ?: return@withContext false
        try {
            val chunkSize = 128
            var offset = 0
            while (offset < bytes.size) {
                val length = minOf(chunkSize, bytes.size - offset)
                stream.write(bytes, offset, length)
                stream.flush()
                offset += length
                Thread.sleep(25) // Delay 25ms agar printer thermal tidak loss buffer
            }
            true
        } catch (e: IOException) {
            e.printStackTrace()
            closeConnection()
            false
        }
    }

    fun closeConnection() {
        try {
            outputStream?.close()
            bluetoothSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            outputStream = null
            bluetoothSocket = null
            connectedAddress = null
        }
    }

    fun isConnected(): Boolean {
        return bluetoothSocket?.isConnected == true
    }
}
