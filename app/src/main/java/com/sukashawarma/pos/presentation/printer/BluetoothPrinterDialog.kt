package com.sukashawarma.pos.presentation.printer

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.SignalCellularAlt1Bar
import androidx.compose.material.icons.filled.SignalCellularAlt2Bar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sukashawarma.pos.presentation.theme.AmberPrimary
import com.sukashawarma.pos.presentation.theme.TwBlue50
import com.sukashawarma.pos.presentation.theme.TwBlue100
import com.sukashawarma.pos.presentation.theme.TwBlue500
import com.sukashawarma.pos.presentation.theme.TwBlue600
import com.sukashawarma.pos.presentation.theme.TwEmerald50
import com.sukashawarma.pos.presentation.theme.TwEmerald100
import com.sukashawarma.pos.presentation.theme.TwEmerald500
import com.sukashawarma.pos.presentation.theme.TwGray50
import com.sukashawarma.pos.presentation.theme.TwGray100
import com.sukashawarma.pos.presentation.theme.TwGray200
import com.sukashawarma.pos.presentation.theme.TwGray400
import com.sukashawarma.pos.presentation.theme.TwGray500
import com.sukashawarma.pos.presentation.theme.TwGray700
import com.sukashawarma.pos.presentation.theme.TwGray900
import com.sukashawarma.pos.presentation.theme.TwRed50
import com.sukashawarma.pos.presentation.theme.TwRed100
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

    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            elevation = CardDefaults.cardElevation(defaultElevation = 18.dp)
        ) {
            Column(
                modifier = Modifier
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFFFFF7ED), Color.White, TwBlue50)
                        )
                    )
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(TwBlue50),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (connectionStatus) {
                                ConnectionStatus.CONNECTED -> Icons.Default.BluetoothConnected
                                ConnectionStatus.ERROR -> Icons.Default.BluetoothDisabled
                                ConnectionStatus.CONNECTING -> Icons.Default.BluetoothSearching
                                else -> Icons.Default.Bluetooth
                            },
                            contentDescription = null,
                            tint = when (connectionStatus) {
                                ConnectionStatus.CONNECTED -> TwEmerald500
                                ConnectionStatus.ERROR -> TwRed500
                                else -> TwBlue600
                            },
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Printer Bluetooth",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TwGray900
                        )
                        Text(
                            text = when (connectionStatus) {
                                ConnectionStatus.CONNECTED -> "Siap mencetak melalui ${connectedName ?: "printer"}"
                                ConnectionStatus.CONNECTING -> "Sedang menyambungkan printer..."
                                ConnectionStatus.ERROR -> "Koneksi printer bermasalah"
                                else -> "Pilih printer kasir yang akan digunakan"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TwGray500,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup", tint = TwGray500)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                PrinterStatusCard(connectionStatus, connectedName, isScanning)

                if (connectionStatus != ConnectionStatus.CONNECTED) {
                    Spacer(modifier = Modifier.height(12.dp))
                    BluetoothScanAnimation(isScanning = isScanning || connectionStatus == ConnectionStatus.CONNECTING)
                }

                if (errorMsg != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(color = TwRed50, shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, TwRed100)) {
                        Text(
                            text = errorMsg.orEmpty(),
                            color = TwRed500,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                if (connectionStatus == ConnectionStatus.CONNECTED) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = viewModel::printTest,
                            modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary, contentColor = Color.White)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Tes Cetak", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = viewModel::disconnect,
                            modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Putuskan", color = TwGray700, fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else {
                    val hasDevices = pairedDevices.isNotEmpty() || discoveredDevices.isNotEmpty()
                    if (hasDevices) {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (pairedDevices.isNotEmpty()) {
                                item { DeviceSectionLabel("PERANGKAT TERSIMPAN") }
                                items(pairedDevices, key = { "paired-${it.mac}" }) { device ->
                                    DeviceRow(device.name, device.mac, device.rssi) {
                                        viewModel.connectToDevice(device.name, device.mac)
                                    }
                                }
                            }
                            if (discoveredDevices.isNotEmpty()) {
                                item { DeviceSectionLabel("PERANGKAT DITEMUKAN") }
                                items(discoveredDevices, key = { "found-${it.mac}" }) { device ->
                                    DeviceRow(device.name, device.mac, device.rssi) {
                                        viewModel.connectToDevice(device.name, device.mac)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = TwGray50,
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, TwGray200)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Belum ada printer ditemukan", fontWeight = FontWeight.SemiBold, color = TwGray700)
                                Text(
                                    "Nyalakan printer lalu tekan tombol pindai.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TwGray500
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    Button(
                        onClick = { if (isScanning) viewModel.stopDiscovery() else viewModel.startDiscovery(context) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isScanning) TwGray700 else AmberPrimary,
                            contentColor = Color.White
                        )
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(19.dp))
                        }
                        Spacer(modifier = Modifier.width(9.dp))
                        Text(if (isScanning) "Hentikan Pemindaian" else "Pindai Printer", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun BluetoothScanAnimation(isScanning: Boolean) {
    val transition = rememberInfiniteTransition(label = "bluetooth_scan")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "scan_rotation"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scan_pulse"
    )

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.size(92.dp), contentAlignment = Alignment.Center) {
            if (isScanning) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .scale(pulse)
                        .clip(CircleShape)
                        .border(1.dp, TwBlue500.copy(alpha = 0.28f), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(82.dp)
                        .clip(CircleShape)
                        .rotate(rotation)
                        .background(
                            Brush.sweepGradient(
                                0f to Color.Transparent,
                                0.72f to Color.Transparent,
                                1f to TwBlue500.copy(alpha = 0.4f)
                            )
                        )
                )
            }
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color.White, TwBlue100)))
                    .border(1.dp, TwBlue100, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isScanning) Icons.Default.BluetoothSearching else Icons.Default.Bluetooth,
                    contentDescription = if (isScanning) "Sedang memindai printer Bluetooth" else "Bluetooth siap dipindai",
                    tint = TwBlue600,
                    modifier = Modifier.size(29.dp)
                )
            }
        }
    }
}

@Composable
private fun PrinterStatusCard(status: ConnectionStatus, connectedName: String?, isScanning: Boolean) {
    val (background, border, accent) = when (status) {
        ConnectionStatus.CONNECTED -> Triple(TwEmerald50, TwEmerald100, TwEmerald500)
        ConnectionStatus.ERROR -> Triple(TwRed50, TwRed100, TwRed500)
        else -> Triple(TwBlue50, TwBlue100, TwBlue500)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = background,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, border)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(accent))
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (status) {
                        ConnectionStatus.CONNECTED -> "Printer terhubung"
                        ConnectionStatus.CONNECTING -> "Menghubungkan printer"
                        ConnectionStatus.ERROR -> "Printer gagal terhubung"
                        else -> if (isScanning) "Mencari printer di sekitar" else "Printer belum terhubung"
                    },
                    fontWeight = FontWeight.Bold,
                    color = TwGray900
                )
                if (status == ConnectionStatus.CONNECTED && !connectedName.isNullOrBlank()) {
                    Text(connectedName, style = MaterialTheme.typography.bodySmall, color = TwGray500)
                }
            }
            if (status == ConnectionStatus.CONNECTING || isScanning) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = accent)
            }
        }
    }
}

@Composable
private fun DeviceSectionLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = TwGray400,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
fun DeviceRow(name: String, mac: String, rssi: Int? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, TwGray200, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(TwBlue50),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Bluetooth, contentDescription = null, tint = TwBlue600, modifier = Modifier.size(21.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = TwGray900, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(mac, style = MaterialTheme.typography.bodySmall, color = TwGray500)
        }
        if (rssi != null) SignalStrengthIndicator(rssi)
    }
}

@Composable
fun SignalStrengthIndicator(rssi: Int) {
    val (icon, tint, label) = when {
        rssi >= -60 -> Triple(Icons.Default.SignalCellularAlt, TwEmerald500, "Kuat")
        rssi >= -80 -> Triple(Icons.Default.SignalCellularAlt2Bar, AmberPrimary, "Sedang")
        else -> Triple(Icons.Default.SignalCellularAlt1Bar, TwRed500, "Lemah")
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = "Sinyal $label ($rssi dBm)", tint = tint, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("$rssi dBm", style = MaterialTheme.typography.labelSmall, color = tint)
    }
}
