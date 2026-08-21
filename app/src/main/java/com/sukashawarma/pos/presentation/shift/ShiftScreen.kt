package com.sukashawarma.pos.presentation.shift

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
    val shiftsHistory by viewModel.shiftsHistory.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val lastCloseVariance by viewModel.lastCloseVariance.collectAsState()

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

                    ShiftDetailRow("ESTIMASI PENJUALAN CASH", "Rp ${String.format("%,.0f", expectedCash)}", isBold = true)

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

        // RIGHT 50%: Live Petty Cash Expenses & Shift History from Supabase
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
                    Icon(Icons.Default.Receipt, contentDescription = null, tint = AmberPrimary)
                    Text(
                        text = "Riwayat Kas Kecil & Struk Supabase",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Form Tambah Pengeluaran Kas Kecil
                OutlinedTextField(
                    value = pettyCashDescription,
                    onValueChange = { viewModel.pettyCashDescription.value = it },
                    label = { Text("Keterangan (mis. Beli Es Batu)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = pettyCashAmount,
                        onValueChange = { viewModel.pettyCashAmount.value = it },
                        label = { Text("Nominal (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                    Button(
                        onClick = { viewModel.addPettyCashExpense() },
                        enabled = !isLoading,
                        modifier = Modifier.height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("CATAT", color = SlateBackground, fontWeight = FontWeight.Bold)
                    }
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
                                        // Real Receipt Image from Supabase Storage CDN
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
                                                color = TextPrimary
                                            )
                                            Text(
                                                text = "Kategori: ${item.category?.uppercase() ?: "OPERASIONAL"}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = TextSecondary
                                            )
                                            item.expenseDate?.let {
                                                Text(text = "Tgl: $it", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                                            }
                                        }
                                    }

                                    Text(
                                        text = "-Rp ${String.format("%,.0f", item.amount)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = StatusPending
                                    )
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
