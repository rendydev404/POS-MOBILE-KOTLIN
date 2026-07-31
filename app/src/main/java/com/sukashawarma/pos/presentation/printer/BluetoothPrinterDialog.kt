package com.sukashawarma.pos.presentation.printer

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.sukashawarma.pos.presentation.theme.AmberPrimary
import com.sukashawarma.pos.presentation.theme.TwEmerald500
import com.sukashawarma.pos.presentation.theme.TwRed500

@Composable
fun BluetoothPrinterDialog(
    viewModel: BluetoothPrinterViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val connectedName by viewModel.connectedDeviceName.collectAsState()
    val pairedDevices by viewModel.pairedDevices.collectAsState()
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val errorMsg by viewModel.errorMessage.collectAsState()

    // Permission handling
    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val allGranted = result.values.all { it }
        if (allGranted) {
            viewModel.refreshPairedDevices()
            viewModel.autoConnectIfSaved()
        }
    }

    DisposableEffect(Unit) {
        permissionLauncher.launch(permissionsToRequest)
        viewModel.registerReceiver(context)
        onDispose {
            viewModel.stopDiscovery()
            viewModel.unregisterReceiver(context)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Advanced Radar/Pulse Animation
                val infiniteTransition = rememberInfiniteTransition()
                val isBusy = connectionStatus == ConnectionStatus.CONNECTING || isScanning
                
                val rotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ), label = "radar_rotation"
                )

                val ripple1 by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = LinearOutSlowInEasing),
                        repeatMode = RepeatMode.Restart
                    ), label = "ripple1"
                )

                val ripple2 by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = LinearOutSlowInEasing, delayMillis = 500),
                        repeatMode = RepeatMode.Restart
                    ), label = "ripple2"
                )

                val ripple3 by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = LinearOutSlowInEasing, delayMillis = 1000),
                        repeatMode = RepeatMode.Restart
                    ), label = "ripple3"
                )
                
                val iconScale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = if (isBusy) 1.15f else 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ), label = "icon_scale"
                )

                val connectedPulse by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = if (connectionStatus == ConnectionStatus.CONNECTED) 1.2f else 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ), label = "connected_pulse"
                )

                val Blue500 = Color(0xFF3B82F6)

                Box(
                    modifier = Modifier.size(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isBusy) {
                        // Radar Sweep Beam
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .rotate(rotation)
                                .background(
                                    Brush.sweepGradient(
                                        0.0f to Color.Transparent,
                                        0.6f to Color.Transparent,
                                        0.9f to Blue500.copy(alpha = 0.3f),
                                        1.0f to Blue500.copy(alpha = 0.6f)
                                    )
                                )
                        )

                        // Concentric Ripples
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .scale(0.3f + (ripple1 * 0.7f))
                                .clip(CircleShape)
                                .border(1.dp, Blue500.copy(alpha = 1f - ripple1), CircleShape)
                                .background(Blue500.copy(alpha = (1f - ripple1) * 0.15f))
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .scale(0.3f + (ripple2 * 0.7f))
                                .clip(CircleShape)
                                .border(1.dp, Blue500.copy(alpha = 1f - ripple2), CircleShape)
                                .background(Blue500.copy(alpha = (1f - ripple2) * 0.15f))
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .scale(0.3f + (ripple3 * 0.7f))
                                .clip(CircleShape)
                                .border(1.dp, Blue500.copy(alpha = 1f - ripple3), CircleShape)
                                .background(Blue500.copy(alpha = (1f - ripple3) * 0.15f))
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .scale(if (connectionStatus == ConnectionStatus.CONNECTED) connectedPulse else iconScale)
                            .clip(CircleShape)
                            .background(
                                when (connectionStatus) {
                                    ConnectionStatus.CONNECTED -> TwEmerald500.copy(alpha = 0.15f)
                                    ConnectionStatus.ERROR -> TwRed500.copy(alpha = 0.15f)
                                    else -> Blue500.copy(alpha = 0.15f)
                                }
                            )
                            .border(
                                2.dp,
                                when (connectionStatus) {
                                    ConnectionStatus.CONNECTED -> TwEmerald500.copy(alpha = 0.5f)
                                    ConnectionStatus.ERROR -> TwRed500.copy(alpha = 0.5f)
                                    else -> Blue500.copy(alpha = 0.5f)
                                },
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (connectionStatus == ConnectionStatus.CONNECTED) {
                            // Extra glow effect for connected state
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .scale(1.2f)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(TwEmerald500.copy(alpha = 0.4f), Color.Transparent)
                                        )
                                    )
                            )
                        }
                        Icon(
                            imageVector = when (connectionStatus) {
                                ConnectionStatus.CONNECTED -> Icons.Default.BluetoothConnected
                                ConnectionStatus.ERROR -> Icons.Default.BluetoothDisabled
                                ConnectionStatus.CONNECTING -> Icons.Default.BluetoothSearching
                                else -> if (isScanning) Icons.Default.BluetoothSearching else Icons.Default.Bluetooth
                            },
                            contentDescription = "Bluetooth Status",
                            tint = when (connectionStatus) {
                                ConnectionStatus.CONNECTED -> TwEmerald500
                                ConnectionStatus.ERROR -> TwRed500
                                else -> Blue500
                            },
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = when (connectionStatus) {
                        ConnectionStatus.CONNECTED -> "Terhubung ke $connectedName"
                        ConnectionStatus.CONNECTING -> "Menyambungkan..."
                        ConnectionStatus.ERROR -> "Gagal Menyambung"
                        else -> "Pilih Printer Bluetooth"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                if (errorMsg != null) {
                    Text(
                        text = errorMsg ?: "",
                        color = TwRed500,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (connectionStatus == ConnectionStatus.CONNECTED) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = { viewModel.printTest() },
                            colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Test Print")
                        }
                        OutlinedButton(
                            onClick = { viewModel.disconnect() }
                        ) {
                            Text("Putuskan")
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 250.dp)
                    ) {
                        if (pairedDevices.isNotEmpty()) {
                            item {
                                Text(
                                    "Perangkat Tersimpan (Paired)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            items(pairedDevices) { device ->
                                DeviceRow(name = device.first, mac = device.second) {
                                    viewModel.connectToDevice(device.first, device.second)
                                }
                            }
                        }

                        if (discoveredDevices.isNotEmpty()) {
                            item {
                                Text(
                                    "Perangkat Baru",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            items(discoveredDevices) { device ->
                                DeviceRow(name = device.first, mac = device.second) {
                                    viewModel.connectToDevice(device.first, device.second)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (isScanning) viewModel.stopDiscovery() else viewModel.startDiscovery(context)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isScanning) Color.Gray else AmberPrimary
                        )
                    ) {
                        Text(if (isScanning) "Berhenti Memindai" else "Pindai Perangkat Baru")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Tutup", color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun DeviceRow(name: String, mac: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Bluetooth,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(text = mac, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}
