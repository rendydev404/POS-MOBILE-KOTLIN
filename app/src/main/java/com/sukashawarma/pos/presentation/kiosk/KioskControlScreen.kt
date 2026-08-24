package com.sukashawarma.pos.presentation.kiosk

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.sukashawarma.pos.data.remote.dto.KioskAccountDto
import com.sukashawarma.pos.data.remote.realtime.KioskPresence
import com.sukashawarma.pos.presentation.theme.CreamBackground
import com.sukashawarma.pos.presentation.theme.ShawarmaOrange

@Composable
fun KioskControlScreen(
    viewModel: KioskControlViewModel,
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    val devices by viewModel.devices.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val isLoadingAccounts by viewModel.isLoadingAccounts.collectAsState()
    val busyTarget by viewModel.busyTarget.collectAsState()
    val message by viewModel.message.collectAsState()
    val pairingLink by viewModel.pairingLink.collectAsState()
    val realtimeConnected by viewModel.isRealtimeConnected.collectAsState()
    var logoutTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showPairing by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    logoutTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { logoutTarget = null },
            title = { Text(if (target.first == "all") "Logout semua device?" else "Logout device?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (target.first == "all")
                        "Semua ${devices.size} device di cabang ini akan kembali ke halaman login."
                    else "Device \"${target.second}\" akan kembali ke halaman login."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.logout(target.first, target.second)
                        logoutTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) { Text("Logout", color = Color.White) }
            },
            dismissButton = { OutlinedButton(onClick = { logoutTarget = null }) { Text("Batal") } }
        )
    }

    if (showPairing) {
        PairingDialog(
            accounts = accounts,
            isLoadingAccounts = isLoadingAccounts,
            busyTarget = busyTarget,
            pairingLink = pairingLink,
            onGenerate = viewModel::createPairingLink,
            onBack = viewModel::clearPairingLink,
            onDismiss = {
                viewModel.clearPairingLink()
                showPairing = false
            }
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(CreamBackground)) {
        val isNarrow = maxWidth < 820.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isNarrow) 14.dp else 20.dp)
        ) {
            // Header Section
            if (isNarrow) {
                // Portrait tablet layout: Title on top, action buttons row below
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column {
                        Text("Kontrol Device Pelanggan", fontWeight = FontWeight.Black, fontSize = 22.sp, color = Color(0xFF111827))
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Device aktif di cabang Anda. ", color = Color(0xFF6B7280), fontSize = 12.sp)
                            Box(Modifier.size(8.dp).background(if (realtimeConnected) Color(0xFF10B981) else Color(0xFFD1D5DB), CircleShape))
                            Spacer(Modifier.width(5.dp))
                            Text(
                                if (realtimeConnected) "Terhubung" else "Menghubungkan…",
                                color = if (realtimeConnected) Color(0xFF059669) else Color(0xFF9CA3AF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item {
                            OutlinedButton(
                                onClick = viewModel::refresh,
                                enabled = isOnline,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Refresh", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        item {
                            Button(
                                onClick = {
                                    showPairing = true
                                    viewModel.loadAccounts()
                                },
                                enabled = isOnline,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.QrCode, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Hubungkan via QR", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (devices.isNotEmpty()) {
                            item {
                                Button(
                                    onClick = { logoutTarget = "all" to "Semua device" },
                                    enabled = isOnline && busyTarget == null,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    if (busyTarget == "all") CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                    else Icon(Icons.Default.Logout, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Logout Semua", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            } else {
                // Landscape layout: side by side
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Kontrol Device Pelanggan", fontWeight = FontWeight.Black, fontSize = 24.sp, color = Color(0xFF111827))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Device yang sedang aktif di cabang Anda.  ", color = Color(0xFF6B7280), fontSize = 13.sp)
                            Box(Modifier.size(8.dp).background(if (realtimeConnected) Color(0xFF10B981) else Color(0xFFD1D5DB), CircleShape))
                            Spacer(Modifier.width(5.dp))
                            Text(
                                if (realtimeConnected) "Terhubung" else "Menghubungkan…",
                                color = if (realtimeConnected) Color(0xFF059669) else Color(0xFF9CA3AF),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = viewModel::refresh,
                        enabled = isOnline,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Refresh", fontSize = 12.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            showPairing = true
                            viewModel.loadAccounts()
                        },
                        enabled = isOnline,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.QrCode, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Hubungkan via QR", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    if (devices.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { logoutTarget = "all" to "Semua device" },
                            enabled = isOnline && busyTarget == null,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            if (busyTarget == "all") CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            else Icon(Icons.Default.Logout, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Logout Semua Device", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Guidance Banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFFFFBEB),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A))
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(shape = CircleShape, color = Color(0xFFFEF3C7)) {
                        Icon(Icons.Default.Warning, null, tint = ShawarmaOrange, modifier = Modifier.padding(6.dp).size(18.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Panduan Mengelola Device Pelanggan", color = Color(0xFF78350F), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(
                            "Cocokkan ID Device di pojok kiri atas layar pelanggan dengan daftar ini sebelum melakukan logout.",
                            color = Color(0xFF92400E), fontSize = 11.sp, lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Device List / Empty State Container
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB))
            ) {
                if (devices.isEmpty()) {
                    DeviceEmptyState(
                        onPairClick = {
                            showPairing = true
                            viewModel.loadAccounts()
                        },
                        isOnline = isOnline
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = if (isNarrow) 260.dp else 300.dp),
                        contentPadding = PaddingValues(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        gridItems(devices, key = { it.userId }) { device ->
                            DeviceCard(
                                device = device,
                                isBusy = busyTarget == device.userId,
                                enabled = busyTarget == null,
                                onLogout = { logoutTarget = device.userId to device.deviceLabel }
                            )
                        }
                    }
                }
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(20.dp))

        if (!isOnline) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .42f)), contentAlignment = Alignment.Center) {
                Surface(shape = RoundedCornerShape(18.dp), color = Color.White) {
                    Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Wifi, null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(38.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Kontrol device memerlukan internet", fontWeight = FontWeight.Bold)
                        Text("Sambungkan internet untuk melihat dan mengatur device kiosk.", color = Color(0xFF6B7280), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(device: KioskPresence, isBusy: Boolean, enabled: Boolean, onLogout: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFECFDF5)) {
                    Icon(Icons.Default.Devices, null, tint = Color(0xFF059669), modifier = Modifier.padding(10.dp).size(22.dp))
                }
                Box(Modifier.align(Alignment.TopEnd).size(10.dp).background(Color(0xFF10B981), CircleShape))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(device.deviceLabel, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF111827), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Wifi, null, tint = Color(0xFF059669), modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Online", color = Color(0xFF059669), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onLogout,
                enabled = enabled,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827))
            ) {
                if (isBusy) CircularProgressIndicator(Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                else Icon(Icons.Default.Logout, null, Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
                Text("Logout", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun DeviceEmptyState(onPairClick: () -> Unit, isOnline: Boolean) = Box(
    modifier = Modifier.fillMaxSize().padding(24.dp),
    contentAlignment = Alignment.Center
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = Color(0xFFF3F4F6),
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Devices, null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(32.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Tidak Ada Device Online", color = Color(0xFF374151), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            "Device pelanggan yang aktif di cabang Anda akan muncul otomatis di sini.",
            color = Color(0xFF6B7280),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onPairClick,
            enabled = isOnline,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(Icons.Default.QrCode, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Hubungkan Device Baru", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PairingDialog(
    accounts: List<KioskAccountDto>,
    isLoadingAccounts: Boolean,
    busyTarget: String?,
    pairingLink: String?,
    onGenerate: (KioskAccountDto) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf<KioskAccountDto?>(null) }
    val showingQr = selected != null && pairingLink != null
    val generatingQr = selected != null && busyTarget == selected?.id

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = Color.White) {
            Column(
                Modifier.widthIn(max = 390.dp).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFEEF2FF)) {
                    Icon(Icons.Default.QrCode, null, tint = Color(0xFF4F46E5), modifier = Modifier.padding(12.dp).size(25.dp))
                }
                Spacer(Modifier.height(12.dp))
                when {
                    isLoadingAccounts -> {
                        Text("Memuat akun device…", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(26.dp))
                        CircularProgressIndicator(color = Color(0xFF4F46E5))
                        Spacer(Modifier.height(26.dp))
                    }
                    generatingQr -> {
                        Text("Scan QR ${selected?.username.orEmpty()}", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("Menghasilkan kode…", color = Color(0xFF6B7280), fontSize = 13.sp)
                        Box(Modifier.size(232.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color(0xFF4F46E5))
                        }
                    }
                    showingQr -> {
                        Text("Scan QR ${selected?.username.orEmpty()}", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("Arahkan kamera tablet Device ke kode ini.", color = Color(0xFF6B7280), fontSize = 13.sp)
                        Spacer(Modifier.height(14.dp))
                        QrImage(pairingLink!!)
                        TextButton(onClick = {
                            selected = null
                            onBack()
                        }) { Text("← Kembali ke daftar akun") }
                    }
                    else -> {
                        Text("Pilih Akun Device", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("Pilih akun yang akan dihubungkan ke perangkat.", color = Color(0xFF6B7280), fontSize = 13.sp)
                        Spacer(Modifier.height(14.dp))
                        if (accounts.isEmpty()) {
                            Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFEF2F2)) {
                                Text("Tidak ada akun Device untuk cabang ini. Minta Admin untuk membuatnya.", color = Color(0xFFDC2626), fontSize = 12.sp, modifier = Modifier.padding(14.dp))
                            }
                        } else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(accounts, key = { it.id }) { account ->
                                OutlinedButton(
                                    onClick = {
                                        selected = account
                                        onGenerate(account)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(account.username ?: "Kiosk", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("Buat QR", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Tutup") }
            }
        }
    }
}

@Composable
private fun QrImage(value: String) {
    val bitmap = remember(value) {
        val matrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, 600, 600)
        android.graphics.Bitmap.createBitmap(600, 600, android.graphics.Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until 600) for (x in 0 until 600) {
                setPixel(x, y, if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
            }
        }
    }
    Surface(shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFF3F4F6))) {
        Image(bitmap.asImageBitmap(), contentDescription = "QR login device", modifier = Modifier.size(232.dp).padding(12.dp))
    }
}
