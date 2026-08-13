package com.sukashawarma.pos.presentation.shift

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.delay
import java.util.*

@Composable
fun CloseShiftScreen(
    viewModel: ShiftViewModel,
    onBack: () -> Unit
) {
    val activeShift by viewModel.activeShift.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()
    
    val actualCashInput by viewModel.actualCashInput.collectAsState()
    val actualPettyCashInput by viewModel.actualPettyCashInput.collectAsState()
    
    val currentDrawerBalance by viewModel.expectedCash.collectAsState()
    val shiftSalesTotal by viewModel.shiftSalesTotal.collectAsState()
    
    val pettyCashBalance by viewModel.pettyCashBalance.collectAsState()
    val approvedTopupsTotal by viewModel.approvedTopupsTotal.collectAsState()
    val expensesTotal by viewModel.expensesTotal.collectAsState()
    
    val isClosingAllowed by viewModel.isClosingAllowed.collectAsState()

    var showConfirmDialog by remember { mutableStateOf(false) }

    // Run time check periodically
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.checkTimeRestriction()
            delay(60000)
        }
    }

    val cashDiff = actualCashInput.toDoubleOrNull()?.let { it - currentDrawerBalance }
    val pettyCashDiff = actualPettyCashInput.toDoubleOrNull()?.let { it - pettyCashBalance }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF9FAFB))) {
        // Warning Banner
        if (!isClosingAllowed) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(Color(0xFFFFFBEB), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(Icons.Default.Warning, contentDescription = "Warning", tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Belum Waktunya Tutup Shift. Sesuai aturan, penutupan petty cash (shift) hanya dapat dilakukan mulai jam 22:00 malam hingga 06:00 pagi. Silakan kembali lagi nanti.",
                    color = Color(0xFF92400E),
                    fontSize = 14.sp
                )
            }
        }

        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.background(Color.White, RoundedCornerShape(12.dp))) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.Gray)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Tutup Shift Laci", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color(0xFF111827))
                Text("Hitung dan selaraskan sisa uang sebelum pergantian shift", color = Color.Gray, fontSize = 14.sp)
            }
        }

        // Error & Success Messages
        if (errorMessage != null) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).background(Color(0xFFFEF2F2), RoundedCornerShape(12.dp)).border(1.dp, Color(0xFFFCA5A5), RoundedCornerShape(12.dp)).padding(16.dp)) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(errorMessage!!, color = Color(0xFFB91C1C), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
        if (successMessage != null) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).background(Color(0xFFECFDF5), RoundedCornerShape(12.dp)).border(1.dp, Color(0xFF6EE7B7), RoundedCornerShape(12.dp)).padding(16.dp)) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(successMessage!!, color = Color(0xFF047857), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.height(16.dp))

        BoxWithConstraints(modifier = Modifier.fillMaxSize().weight(1f)) {
            val isWideScreen = maxWidth > 600.dp
            
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = if (isWideScreen) Alignment.CenterHorizontally else Alignment.Start
            ) {
                if (isWideScreen) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Box(Modifier.weight(1f)) {
                            ManualHitunganCard(viewModel, cashDiff, pettyCashDiff, isClosingAllowed, isLoading) { showConfirmDialog = true }
                        }
                        Box(Modifier.weight(1f)) {
                            PerhitunganSistemCard(activeShift, currentDrawerBalance, shiftSalesTotal, pettyCashBalance, approvedTopupsTotal, expensesTotal)
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        ManualHitunganCard(viewModel, cashDiff, pettyCashDiff, isClosingAllowed, isLoading) { showConfirmDialog = true }
                        PerhitunganSistemCard(activeShift, currentDrawerBalance, shiftSalesTotal, pettyCashBalance, approvedTopupsTotal, expensesTotal)
                    }
                }
                Spacer(Modifier.height(48.dp))
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Tutup Shift Sekarang?", fontWeight = FontWeight.Bold) },
            text = { Text("Setelah ditutup, shift ini tidak dapat menerima transaksi lagi. Apakah Anda yakin uang fisik sudah dihitung dengan benar?") },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        viewModel.closeShift()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Ya, Tutup Shift")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Batal", color = Color.Gray)
                }
            }
        )
    }
}

@Composable
fun ManualHitunganCard(
    viewModel: ShiftViewModel,
    cashDiff: Double?,
    pettyCashDiff: Double?,
    isClosingAllowed: Boolean,
    isLoading: Boolean,
    onSubmit: () -> Unit
) {
    val actualCashInput by viewModel.actualCashInput.collectAsState()
    val actualPettyCashInput by viewModel.actualPettyCashInput.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFFFECACA), RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
    ) {
        // Red Header
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFFFEF2F2)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Hitungan Manual Kasir & Tutup Shift", fontWeight = FontWeight.Bold, color = Color(0xFF7F1D1D), fontSize = 16.sp)
        }
        
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                "Hitung seluruh uang fisik secara manual. Masukkan angka untuk Penjualan Cash (sales) dan Petty Cash secara terpisah.",
                color = Color.Gray, fontSize = 14.sp
            )
            Spacer(Modifier.height(24.dp))
            
            // Cash Input
            Text("Penjualan Cash (Hitungan Manual)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF374151))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = actualCashInput,
                onValueChange = { newValue ->
                    if (newValue.all { it.isDigit() }) viewModel.actualCashInput.value = newValue
                },
                placeholder = { Text("Contoh: 850000", color = Color.Gray) },
                leadingIcon = { Text("Rp", fontWeight = FontWeight.Bold, color = Color.Gray, modifier = Modifier.padding(start = 16.dp)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = Color(0xFFECFDF5).copy(alpha = 0.5f),
                    focusedContainerColor = Color.White,
                    unfocusedBorderColor = Color(0xFFA7F3D0),
                    focusedBorderColor = Color(0xFF10B981)
                ),
                enabled = isClosingAllowed && !isLoading
            )
            if (cashDiff != null) {
                val diffColor = if (cashDiff == 0.0) Color(0xFF059669) else if (cashDiff > 0) Color(0xFF2563EB) else Color(0xFFDC2626)
                val diffText = if (cashDiff == 0.0) "✓ Pas dengan sistem" else if (cashDiff > 0) "Lebih ${formatRupiah(cashDiff)} dari sistem" else "Kurang ${formatRupiah(kotlin.math.abs(cashDiff))} dari sistem"
                Text(diffText, color = diffColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            }

            Spacer(Modifier.height(20.dp))

            // Petty Cash Input
            Text("Hitung sisa petty cash", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF374151))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = actualPettyCashInput,
                onValueChange = { newValue ->
                    if (newValue.all { it.isDigit() }) viewModel.actualPettyCashInput.value = newValue
                },
                placeholder = { Text("Contoh: 250000", color = Color.Gray) },
                leadingIcon = { Text("Rp", fontWeight = FontWeight.Bold, color = Color.Gray, modifier = Modifier.padding(start = 16.dp)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = Color(0xFFEFF6FF).copy(alpha = 0.5f),
                    focusedContainerColor = Color.White,
                    unfocusedBorderColor = Color(0xFFBFDBFE),
                    focusedBorderColor = Color(0xFF3B82F6)
                ),
                enabled = isClosingAllowed && !isLoading
            )
            if (pettyCashDiff != null) {
                val diffColor = if (pettyCashDiff == 0.0) Color(0xFF059669) else if (pettyCashDiff > 0) Color(0xFF2563EB) else Color(0xFFDC2626)
                val diffText = if (pettyCashDiff == 0.0) "✓ Pas dengan sistem" else if (pettyCashDiff > 0) "Lebih ${formatRupiah(pettyCashDiff)} dari sistem" else "Kurang ${formatRupiah(kotlin.math.abs(pettyCashDiff))} dari sistem"
                Text(diffText, color = diffColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = onSubmit,
                enabled = isClosingAllowed && !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626), disabledContainerColor = Color(0xFFFCA5A5)),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Kunci & Tutup Shift", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            Text("Setelah shift ditutup, Anda akan diarahkan ke halaman Laporan.", color = Color.LightGray, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
fun PerhitunganSistemCard(
    activeShift: com.sukashawarma.pos.data.remote.dto.ShiftDto?,
    currentDrawerBalance: Double,
    shiftSalesTotal: Double,
    pettyCashBalance: Double,
    approvedTopupsTotal: Double,
    expensesTotal: Double
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
    ) {
        // Amber Header
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFFFFFBEB)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Calculate, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Perhitungan Sistem (Otomatis)", fontWeight = FontWeight.Bold, color = Color(0xFF78350F), fontSize = 16.sp)
        }
        
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                "Sistem menghitung otomatis berapa uang yang seharusnya ada saat ini. Bandingkan dengan hitungan manual Anda di samping.",
                color = Color.Gray, fontSize = 14.sp
            )
            Spacer(Modifier.height(24.dp))

            // Cash Box
            Column(modifier = Modifier.fillMaxWidth().background(Color(0xFFECFDF5).copy(alpha = 0.6f), RoundedCornerShape(12.dp)).border(1.dp, Color(0xFFD1FAE5), RoundedCornerShape(12.dp)).padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Penjualan Cash Seharusnya", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF374151))
                    Text(formatRupiah(currentDrawerBalance), fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color(0xFF047857))
                }
                Spacer(Modifier.height(4.dp))
                Text("Modal awal ${formatRupiah(activeShift?.startingCash ?: 0.0)} + Penjualan tunai ${formatRupiah(shiftSalesTotal)}", color = Color.Gray, fontSize = 12.sp)
            }

            Spacer(Modifier.height(16.dp))

            // Petty Cash Box
            Column(modifier = Modifier.fillMaxWidth().background(Color(0xFFEFF6FF).copy(alpha = 0.6f), RoundedCornerShape(12.dp)).border(1.dp, Color(0xFFDBEAFE), RoundedCornerShape(12.dp)).padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Petty Cash Seharusnya", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF374151))
                    Text(formatRupiah(pettyCashBalance), fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color(0xFF1D4ED8))
                }
                Spacer(Modifier.height(4.dp))
                // Note: We don't have starting_petty_cash in ShiftDto easily accessible here, we fallback to assuming 0 if not tracked natively or it's handled via the calculated balance
                Text("Awal Rp 0 + Top up ${formatRupiah(approvedTopupsTotal)} − Pengeluaran ${formatRupiah(expensesTotal)}", color = Color.Gray, fontSize = 12.sp)
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = Color(0xFFF3F4F6))
            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("TOTAL SEHARUSNYA", fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 14.sp)
                Text(formatRupiah(currentDrawerBalance + pettyCashBalance), fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color(0xFF111827))
            }
        }
    }
}
