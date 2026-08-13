package com.sukashawarma.pos.presentation.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sukashawarma.pos.presentation.theme.*

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val printerDevices by viewModel.bluetoothDevices.collectAsState()
    val selectedMac by viewModel.selectedPrinterMac.collectAsState()
    val isTestingPrinter by viewModel.isTestingPrinter.collectAsState()
    val printerStatusMessage by viewModel.printerStatusMessage.collectAsState()

    // Tanpa izin Bluetooth runtime (Android 12+), getPairedDevices() diam-diam
    // mengembalikan list kosong — layar terlihat "belum ada printer" padahal
    // sebenarnya izinnya yang belum diberikan. Minta izin dulu lalu refresh.
    var hasBluetoothPermission by remember { mutableStateOf(false) }
    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        hasBluetoothPermission = result.values.all { it }
        if (hasBluetoothPermission) viewModel.refreshPairedDevices()
    }
    DisposableEffect(Unit) {
        permissionLauncher.launch(permissionsToRequest)
        onDispose { }
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // LEFT 50%: Bluetooth Thermal Printer Driver Setup
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            shape = RoundedCornerShape(12.dp),
            color = SlateSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(AmberPrimary.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, tint = AmberPrimary, modifier = Modifier.size(20.dp))
                    }
                    Text(
                        text = "Pengaturan Printer Thermal (Bluetooth)",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = SlateBorder)
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Perangkat Bluetooth Ter-pairing:", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
                    IconButton(onClick = { viewModel.refreshPairedDevices() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Muat ulang", tint = AmberPrimary)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                if (printerDevices.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = SlateCard
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (hasBluetoothPermission) Icons.Default.Info else Icons.Default.Bluetooth,
                                contentDescription = null,
                                tint = TextMuted
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (hasBluetoothPermission) {
                                    "Belum ada printer ter-pairing. Pairing printer dulu lewat Pengaturan Bluetooth Android, lalu tekan refresh."
                                } else {
                                    "Izin Bluetooth belum diberikan. Terima izin yang diminta, lalu tekan refresh untuk memuat printer ter-pairing."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    printerDevices.forEach { (name, mac) ->
                        val isSelected = mac == selectedMac
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectPrinter(mac) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) AmberPrimary.copy(alpha = 0.12f) else SlateCard,
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, AmberPrimary) else androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(
                                                if (isSelected) AmberPrimary.copy(alpha = 0.2f) else SlateBorder.copy(alpha = 0.4f),
                                                RoundedCornerShape(10.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Bluetooth, contentDescription = null, tint = if (isSelected) AmberPrimary else TextMuted, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(text = name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                        Text(text = mac, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                                    }
                                }

                                if (isSelected) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .background(StatusCompleted.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusCompleted, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("DIPILIH", style = MaterialTheme.typography.labelSmall, color = StatusCompleted, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { viewModel.testPrinterConnection() },
                    enabled = selectedMac.isNotBlank() && !isTestingPrinter,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary)
                ) {
                    if (isTestingPrinter) {
                        CircularProgressIndicator(color = SlateBackground, modifier = Modifier.size(20.dp))
                    } else {
                        Text("TES KONEKSI PRINTER", color = SlateBackground, fontWeight = FontWeight.Bold)
                    }
                }

                printerStatusMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(msg, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            }
        }

        // RIGHT 50%: Kiosk Pairing (belum tersedia di versi native)
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            shape = RoundedCornerShape(12.dp),
            color = SlateSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(AmberPrimary.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Tablet, contentDescription = null, tint = AmberPrimary, modifier = Modifier.size(20.dp))
                    }
                    Text(
                        text = "Pairing Tablet Self-Order (Kiosk)",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = SlateBorder)
                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = SlateCard
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Belum tersedia di versi native ini.",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Generate kode/QR pairing kiosk butuh operasi admin (reset password akun kiosk) yang hanya boleh dijalankan di server, bukan di aplikasi kasir. " +
                                "Untuk sekarang, pairing tablet kiosk tetap dilakukan lewat Portal Web.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}
