package com.sukashawarma.pos.presentation.shift

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sukashawarma.pos.presentation.theme.*

@Composable
fun ShiftScreen(
    viewModel: ShiftViewModel,
    modifier: Modifier = Modifier
) {
    val isShiftOpen by viewModel.isShiftOpen.collectAsState()
    val initialCash by viewModel.initialCash.collectAsState()
    val expectedCash by viewModel.expectedCash.collectAsState()
    val totalPettyCashOut by viewModel.totalPettyCashOut.collectAsState()
    val pettyCashBalance by viewModel.pettyCashBalance.collectAsState()
    val openShiftInput by viewModel.openShiftInput.collectAsState()
    val actualCashInput by viewModel.actualCashInput.collectAsState()
    val pettyCashDescription by viewModel.pettyCashDescription.collectAsState()
    val pettyCashAmount by viewModel.pettyCashAmount.collectAsState()
    val pettyCashHistory by viewModel.pettyCashHistory.collectAsState()
    val topupsHistory by viewModel.topupsHistory.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val lastCloseVariance by viewModel.lastCloseVariance.collectAsState()

    var selectedTab by remember { mutableStateOf(0) } // 0 = Pengeluaran, 1 = Topup

    // Photo picker for petty cash receipt
    var receiptBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val inputStream = context.contentResolver.openInputStream(it)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            receiptBitmap = bitmap
        }
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // LEFT 50%: Shift Operational Control
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.LockClock, contentDescription = null, tint = AmberPrimary)
                    Text(
                        text = "Kelola Shift & Laci Kasir",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = SlateBorder)
                Spacer(modifier = Modifier.height(16.dp))

                // Shift Status Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = SlateCard
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("STATUS SHIFT SAAT INI", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                            Text(
                                text = if (isShiftOpen) "SHIFT AKTIF (OPEN)" else "SHIFT DITUTUP (CLOSED)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isShiftOpen) StatusCompleted else StatusPending
                            )
                        }
                    }
                }

                errorMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = StatusPending.copy(alpha = 0.15f)
                    ) {
                        Text(msg, modifier = Modifier.padding(12.dp), color = StatusPending)
                    }
                }

                lastCloseVariance?.let { variance ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = (if (variance >= 0) StatusCompleted else StatusPending).copy(alpha = 0.15f)
                    ) {
                        Text(
                            "Shift terakhir ditutup. Selisih: Rp ${String.format("%,.0f", variance)}",
                            modifier = Modifier.padding(12.dp),
                            fontWeight = FontWeight.Bold,
                            color = if (variance >= 0) StatusCompleted else StatusPending
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Buka Shift Form
                if (!isShiftOpen) {
                    OutlinedTextField(
                        value = openShiftInput,
                        onValueChange = { viewModel.openShiftInput.value = it },
                        label = { Text("Modal Awal Laci (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.openShift() },
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("BUKA SHIFT BARU", color = SlateBackground, fontWeight = FontWeight.Bold)
                    }
                }

                if (isShiftOpen) {
                    ShiftDetailRow("Modal Awal Tunai Laci", "Rp ${String.format("%,.0f", initialCash)}")
                    ShiftDetailRow("Total Pengeluaran Kas Kecil", "-Rp ${String.format("%,.0f", totalPettyCashOut)}", color = StatusPending)
                    ShiftDetailRow("Saldo Petty Cash Tersisa", "Rp ${String.format("%,.0f", pettyCashBalance)}")

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = SlateBorder)
                    Spacer(modifier = Modifier.height(8.dp))

                    ShiftDetailRow("ESTIMASI UANG LACI", "Rp ${String.format("%,.0f", expectedCash)}", isBold = true)

                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = actualCashInput,
                        onValueChange = { viewModel.actualCashInput.value = it },
                        label = { Text("Hitung Uang Fisik Laci (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { viewModel.closeShift() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = StatusPending),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("TUTUP SHIFT & HITUNG SELISIH", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // RIGHT 50%: Ledger Tabs (Pengeluaran & Topup)
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
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = AmberPrimary
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Pengeluaran Kasir", fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Modal Tambahan (Topup)", fontWeight = FontWeight.Bold) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (selectedTab == 0) {
                    // Pengeluaran Kasir Tab
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Form Kiri (Deskripsi & Nominal)
                        Column(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = pettyCashDescription,
                                onValueChange = { viewModel.pettyCashDescription.value = it },
                                label = { Text("Keterangan (mis. Beli Es Batu)") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = pettyCashAmount,
                                onValueChange = { viewModel.pettyCashAmount.value = it },
                                label = { Text("Nominal (Rp)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true
                            )
                        }
                        
                        // Foto Struk Upload Kanan
                        Surface(
                            modifier = Modifier
                                .weight(0.5f)
                                .height(134.dp)
                                .clickable { imagePicker.launch("image/*") },
                            shape = RoundedCornerShape(10.dp),
                            color = SlateCard,
                            border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
                        ) {
                            if (receiptBitmap != null) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    androidx.compose.foundation.Image(
                                        bitmap = receiptBitmap!!.asImageBitmap(),
                                        contentDescription = "Struk",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    IconButton(
                                        onClick = { receiptBitmap = null },
                                        modifier = Modifier.align(Alignment.TopEnd)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Hapus", tint = Color.Red)
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = TextMuted)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Upload Struk", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { 
                            viewModel.addPettyCashExpense(receiptBitmap) 
                            receiptBitmap = null
                        },
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("CATAT PENGELUARAN", color = SlateBackground, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = SlateBorder)
                    Spacer(modifier = Modifier.height(12.dp))

                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = AmberPrimary)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(pettyCashHistory) { item ->
                                val isVoided = item.status == "voided"
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    color = SlateCard
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            if (!item.receiptUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(LocalContext.current)
                                                        .data(item.receiptUrl)
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = item.description,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(60.dp)
                                                        .background(SlateBorder, RoundedCornerShape(8.dp))
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(60.dp)
                                                        .background(SlateBorder, RoundedCornerShape(8.dp)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Default.Receipt, contentDescription = null, tint = TextMuted)
                                                }
                                            }

                                            Column {
                                                Text(
                                                    text = item.description ?: "Pengeluaran Kas Kecil",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isVoided) TextMuted else TextPrimary,
                                                    textDecoration = if (isVoided) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                                                )
                                                Text(
                                                    text = if (isVoided) "DIBATALKAN" else "Kategori: ${item.category?.uppercase() ?: "OPERASIONAL"}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = if (isVoided) StatusPending else TextSecondary
                                                )
                                            }
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "-Rp ${String.format("%,.0f", item.amount)}",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isVoided) TextMuted else StatusPending,
                                                textDecoration = if (isVoided) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                                            )
                                            if (!isVoided) {
                                                Text(
                                                    text = "BATALKAN",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = StatusPending,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier
                                                        .clickable { viewModel.voidPettyCash(item.id, "Kesalahan Kasir") }
                                                        .padding(top = 8.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Topup (Modal Tambahan) Tab
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = AmberPrimary)
                        }
                    } else if (topupsHistory.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Tidak ada riwayat topup", color = TextMuted)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(topupsHistory) { topup ->
                                val isPending = topup.status == "pending"
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isPending) AmberPrimary.copy(alpha = 0.1f) else SlateCard,
                                    border = if (isPending) androidx.compose.foundation.BorderStroke(1.dp, AmberPrimary) else null
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Modal Tambahan (Topup)",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                            Text(
                                                text = if (isPending) "Menunggu Diterima Kasir" else "Telah Diterima",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (isPending) AmberPrimary else TextSecondary
                                            )
                                        }
                                        
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "+Rp ${String.format("%,.0f", topup.amount)}",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = StatusCompleted
                                            )
                                            if (isPending) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Button(
                                                    onClick = { viewModel.receiveTopup(topup.id) },
                                                    colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(32.dp)
                                                ) {
                                                    Text("TERIMA DANA", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShiftDetailRow(
    label: String,
    value: String,
    color: Color = TextPrimary,
    isBold: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = color
        )
    }
}
