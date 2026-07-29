package com.sukashawarma.pos.presentation.shift

import android.net.Uri
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftScreen(
    viewModel: ShiftViewModel,
    onNavigateToCloseShift: () -> Unit
) {
    val context = LocalContext.current
    val activeShift by viewModel.activeShift.collectAsState()
    val isShiftOpen by viewModel.isShiftOpen.collectAsState()
    val ledgerItems by viewModel.ledgerItems.collectAsState()
    
    val initialCash by viewModel.initialCash.collectAsState()
    val expectedCash by viewModel.expectedCash.collectAsState()
    val shiftSalesTotal by viewModel.shiftSalesTotal.collectAsState()
    
    val pettyCashBalance by viewModel.pettyCashBalance.collectAsState()
    val approvedTopupsTotal by viewModel.approvedTopupsTotal.collectAsState()
    val expensesTotal by viewModel.expensesTotal.collectAsState()
    
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()

    var showOpenShiftDialog by remember { mutableStateOf(false) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    var expenseAmount by remember { mutableStateOf("") }
    var expenseDesc by remember { mutableStateOf("") }
    var expenseCategory by remember { mutableStateOf("operasional") }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
        if (uri != null) {
            try {
                if (Build.VERSION.SDK_INT < 28) {
                    selectedBitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                } else {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    selectedBitmap = ImageDecoder.decodeBitmap(source)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadRealShiftData()
    }

    Scaffold(
        snackbarHost = {
            if (errorMessage != null) {
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ) {
                    Text(errorMessage ?: "")
                }
            }
            if (successMessage != null) {
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    containerColor = Color(0xFFD1FAE5),
                    contentColor = Color(0xFF065F46)
                ) {
                    Text(successMessage ?: "")
                }
            }
        }
    ) { padding ->
        if (isLoading && activeShift == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (!isShiftOpen) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.Gray)
                    Spacer(Modifier.height(16.dp))
                    Text("Shift Belum Dibuka", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Buka shift untuk memulai transaksi dan mencatat petty cash", color = Color.Gray)
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { showOpenShiftDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Text("Buka Shift Sekarang")
                    }
                }
            }
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding).background(Color(0xFFF9FAFB))) {
                val isWideScreen = maxWidth > 800.dp
                
                if (isWideScreen) {
                    Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column(Modifier.weight(1f)) {
                            ShiftHeader(
                                staffName = activeShift?.staffId ?: "Kasir",
                                startTime = activeShift?.startTime,
                                onNavigateToCloseShift = onNavigateToCloseShift
                            )
                            Spacer(Modifier.height(24.dp))
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                item {
                                    LaciKasirCard(
                                        initialCash = initialCash,
                                        shiftSalesTotal = shiftSalesTotal,
                                        expectedCash = expectedCash
                                    )
                                }
                                item {
                                    PettyCashCard(
                                        pettyCashBalance = pettyCashBalance,
                                        approvedTopupsTotal = approvedTopupsTotal,
                                        expensesTotal = expensesTotal
                                    )
                                }
                                item {
                                    CatatPengeluaranForm(
                                        amount = expenseAmount,
                                        onAmountChange = { expenseAmount = it },
                                        description = expenseDesc,
                                        onDescriptionChange = { expenseDesc = it },
                                        category = expenseCategory,
                                        onCategoryChange = { expenseCategory = it },
                                        hasImage = selectedBitmap != null,
                                        onPickImage = { imagePickerLauncher.launch("image/*") },
                                        onSubmit = {
                                            viewModel.pettyCashAmount.value = expenseAmount
                                            viewModel.pettyCashDescription.value = expenseDesc
                                            viewModel.pettyCashCategory.value = expenseCategory
                                            viewModel.addPettyCashExpense(selectedBitmap)
                                            // Reset
                                            expenseAmount = ""
                                            expenseDesc = ""
                                            selectedBitmap = null
                                            selectedImageUri = null
                                        },
                                        isLoading = isLoading
                                    )
                                }
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            RiwayatAktivitas(ledgerItems = ledgerItems, viewModel = viewModel)
                        }
                    }
                } else {
                    // Mobile layout
                    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item {
                            ShiftHeader(
                                staffName = activeShift?.staffId ?: "Kasir",
                                startTime = activeShift?.startTime,
                                onNavigateToCloseShift = onNavigateToCloseShift
                            )
                        }
                        item {
                            LaciKasirCard(
                                initialCash = initialCash,
                                shiftSalesTotal = shiftSalesTotal,
                                expectedCash = expectedCash
                            )
                        }
                        item {
                            PettyCashCard(
                                pettyCashBalance = pettyCashBalance,
                                approvedTopupsTotal = approvedTopupsTotal,
                                expensesTotal = expensesTotal
                            )
                        }
                        item {
                            CatatPengeluaranForm(
                                amount = expenseAmount,
                                onAmountChange = { expenseAmount = it },
                                description = expenseDesc,
                                onDescriptionChange = { expenseDesc = it },
                                category = expenseCategory,
                                onCategoryChange = { expenseCategory = it },
                                hasImage = selectedBitmap != null,
                                onPickImage = { imagePickerLauncher.launch("image/*") },
                                onSubmit = {
                                    viewModel.pettyCashAmount.value = expenseAmount
                                    viewModel.pettyCashDescription.value = expenseDesc
                                    viewModel.pettyCashCategory.value = expenseCategory
                                    viewModel.addPettyCashExpense(selectedBitmap)
                                    expenseAmount = ""
                                    expenseDesc = ""
                                    selectedBitmap = null
                                    selectedImageUri = null
                                },
                                isLoading = isLoading
                            )
                        }
                        item {
                            RiwayatAktivitasMobile(ledgerItems = ledgerItems, viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }

    if (showOpenShiftDialog) {
        var modalAwal by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showOpenShiftDialog = false },
            title = { Text("Buka Shift", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Masukkan modal awal di laci kasir (uang kembalian).")
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = modalAwal,
                        onValueChange = { modalAwal = it },
                        label = { Text("Modal Awal (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.openShiftInput.value = modalAwal
                        viewModel.openShift()
                        showOpenShiftDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("Buka Shift")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOpenShiftDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
fun ShiftHeader(staffName: String, startTime: String?, onNavigateToCloseShift: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text("Shift & Petty Cash", fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text(
                text = "Aktif: $staffName • Mulai: ${formatTime(startTime)}", 
                color = Color.Gray, 
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Button(
            onClick = onNavigateToCloseShift,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.height(48.dp)
        ) {
            Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Tutup Shift", fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
fun LaciKasirCard(initialCash: Double, shiftSalesTotal: Double, expectedCash: Double) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(12.dp))
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFECFDF5)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Color(0xFF10B981))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Laci Kasir", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Transaksi tunai shift ini", color = Color.Gray, fontSize = 12.sp)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Modal Awal", color = Color.Gray, fontSize = 12.sp)
                    Text(formatRupiah(initialCash), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Text("+", fontWeight = FontWeight.Bold, color = Color.Gray)
                Column(horizontalAlignment = Alignment.End) {
                    Text("Penjualan Tunai", color = Color.Gray, fontSize = 12.sp)
                    Text(formatRupiah(shiftSalesTotal), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF10B981))
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Color(0xFFF3F4F6))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Estimasi Uang Fisik:", fontWeight = FontWeight.Medium, color = Color.Gray, fontSize = 14.sp)
                Text(formatRupiah(expectedCash), fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color(0xFF1F2937))
            }
        }
    }
}

@Composable
fun PettyCashCard(pettyCashBalance: Double, approvedTopupsTotal: Double, expensesTotal: Double) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Saldo Petty Cash", color = Color(0xFF9CA3AF), fontSize = 12.sp)
                    Text(formatRupiah(pettyCashBalance), fontWeight = FontWeight.Black, fontSize = 28.sp, color = Color.White)
                }
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.CreditCard, contentDescription = null, tint = Color.White)
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Uang Masuk", color = Color(0xFF9CA3AF), fontSize = 12.sp)
                    }
                    Text(formatRupiah(approvedTopupsTotal), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = Color(0xFFF87171), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Uang Keluar", color = Color(0xFF9CA3AF), fontSize = 12.sp)
                    }
                    Text(formatRupiah(expensesTotal), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatatPengeluaranForm(
    amount: String, onAmountChange: (String) -> Unit,
    description: String, onDescriptionChange: (String) -> Unit,
    category: String, onCategoryChange: (String) -> Unit,
    hasImage: Boolean, onPickImage: () -> Unit,
    onSubmit: () -> Unit, isLoading: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(12.dp))
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Catat Pengeluaran", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("Gunakan dana petty cash untuk operasional", color = Color.Gray, fontSize = 12.sp)
            Spacer(Modifier.height(16.dp))
            
            OutlinedTextField(
                value = amount,
                onValueChange = onAmountChange,
                label = { Text("Nominal (Rp)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = description,
                onValueChange = onDescriptionChange,
                label = { Text("Keterangan Pengeluaran") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            
            // Simple dropdown for category simulation using ExposedDropdownMenuBox is ideal, 
            // but a row of selectable chips is easier and matches some web designs.
            Text("Kategori", fontSize = 12.sp, color = Color.Gray)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("operasional" to "Operasional", "bahan_baku" to "Bahan Baku", "lainnya" to "Lainnya").forEach { (key, label) ->
                    FilterChip(
                        selected = category == key,
                        onClick = { onCategoryChange(key) },
                        label = { Text(label) }
                    )
                }
            }
            
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onPickImage,
                colors = ButtonDefaults.buttonColors(containerColor = if (hasImage) Color(0xFFECFDF5) else Color(0xFFF3F4F6)),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    Icons.Default.CameraAlt, 
                    contentDescription = null, 
                    tint = if (hasImage) Color(0xFF10B981) else Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (hasImage) "Foto Bukti Terlampir" else "Lampirkan Foto Struk", 
                    color = if (hasImage) Color(0xFF10B981) else Color.DarkGray
                )
            }
            
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onSubmit,
                enabled = !isLoading && amount.isNotBlank() && description.isNotBlank() && hasImage,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827)),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                } else {
                    Text("Catat Pengeluaran", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun RiwayatAktivitas(ledgerItems: List<LedgerItem>, viewModel: ShiftViewModel) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxSize().border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(12.dp))
    ) {
        Column {
            Column(Modifier.padding(20.dp)) {
                Text("Riwayat Aktivitas", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Gabungan transaksi Laci dan Petty Cash", color = Color.Gray, fontSize = 12.sp)
            }
            HorizontalDivider(color = Color(0xFFF3F4F6))
            if (ledgerItems.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Belum ada aktivitas shift ini", color = Color.Gray)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(ledgerItems) { item ->
                        LedgerItemRow(item, viewModel)
                        HorizontalDivider(color = Color(0xFFF3F4F6))
                    }
                }
            }
        }
    }
}

@Composable
fun RiwayatAktivitasMobile(ledgerItems: List<LedgerItem>, viewModel: ShiftViewModel) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth().height(400.dp).border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(12.dp))
    ) {
        Column {
            Column(Modifier.padding(20.dp)) {
                Text("Riwayat Aktivitas", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Gabungan transaksi Laci dan Petty Cash", color = Color.Gray, fontSize = 12.sp)
            }
            HorizontalDivider(color = Color(0xFFF3F4F6))
            if (ledgerItems.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Belum ada aktivitas shift ini", color = Color.Gray)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(ledgerItems) { item ->
                        LedgerItemRow(item, viewModel)
                        HorizontalDivider(color = Color(0xFFF3F4F6))
                    }
                }
            }
        }
    }
}

@Composable
fun LedgerItemRow(item: LedgerItem, viewModel: ShiftViewModel) {
    var showVoidDialog by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.Top) {
            when (item) {
                is LedgerItem.Expense -> {
                    val exp = item.data
                    val isVoided = exp.status == "voided"
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                            .background(if (isVoided) Color(0xFFF3F4F6) else Color(0xFFFEF2F2)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Receipt, null, tint = if (isVoided) Color.Gray else Color(0xFFEF4444))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            exp.description ?: "-", 
                            fontWeight = FontWeight.Bold, 
                            fontSize = 14.sp,
                            textDecoration = if (isVoided) TextDecoration.LineThrough else null,
                            color = if (isVoided) Color.Gray else Color.Black
                        )
                        Text(
                            "Pengeluaran Petty Cash (${exp.category}) ${if (isVoided) "(DIBATALKAN)" else ""}", 
                            color = if (isVoided) Color(0xFFEF4444) else Color.Gray, 
                            fontSize = 11.sp, 
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(formatTime(exp.createdAt ?: exp.expenseDate), color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                is LedgerItem.Topup -> {
                    val top = item.data
                    val isPending = top.status == "pending" || top.status.startsWith("forwarded")
                    val isRejected = top.status == "rejected"
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    isPending -> Color(0xFFFFFBEB)
                                    isRejected -> Color(0xFFF3F4F6)
                                    else -> Color(0xFFEFF6FF)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.South, 
                            null, 
                            tint = when {
                                isPending -> Color(0xFFF59E0B)
                                isRejected -> Color.Gray
                                else -> Color(0xFF3B82F6)
                            }
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Top Up Petty Cash", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            when {
                                isPending -> "⏳ Menunggu Review"
                                isRejected -> "❌ Ditolak"
                                else -> "✅ Selesai"
                            }, 
                            color = when {
                                isPending -> Color(0xFFF59E0B)
                                isRejected -> Color(0xFFEF4444)
                                else -> Color(0xFF3B82F6)
                            }, 
                            fontSize = 11.sp, 
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(formatTime(top.createdAt), color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                is LedgerItem.Sale -> {
                    val sale = item.data
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFECFDF5)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AccountBalanceWallet, null, tint = Color(0xFF10B981))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Pesanan #${sale.orderNumber}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Penjualan ${if (sale.channel != null) "(Food Apps - ${sale.channel})" else "(Tunai)"}", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text(formatTime(sale.createdAt), color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        
        Column(horizontalAlignment = Alignment.End) {
            when (item) {
                is LedgerItem.Expense -> {
                    val exp = item.data
                    val isVoided = exp.status == "voided"
                    Text(
                        "-${formatRupiah(exp.amount)}", 
                        fontWeight = FontWeight.Black, 
                        fontSize = 14.sp, 
                        color = if (isVoided) Color.Gray else Color(0xFFEF4444),
                        textDecoration = if (isVoided) TextDecoration.LineThrough else null
                    )
                    if (!isVoided) {
                        Text(
                            "Batal",
                            color = Color(0xFFEF4444),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 6.dp).clickable { showVoidDialog = true }.background(Color(0xFFFEF2F2), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                is LedgerItem.Topup -> {
                    val top = item.data
                    val isRejected = top.status == "rejected"
                    Text(
                        "+${formatRupiah(top.amount)}", 
                        fontWeight = FontWeight.Black, 
                        fontSize = 14.sp, 
                        color = if (isRejected) Color.Gray else Color(0xFF3B82F6),
                        textDecoration = if (isRejected) TextDecoration.LineThrough else null
                    )
                    if (top.status == "approved_by_finance" || top.status == "forwarded_by_leader") {
                        Text(
                            "Terima Dana",
                            color = Color(0xFF3B82F6),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 6.dp).clickable { viewModel.receiveTopup(top.id) }.background(Color(0xFFEFF6FF), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                is LedgerItem.Sale -> {
                    Text(
                        "+${formatRupiah(item.data.totalAmount)}", 
                        fontWeight = FontWeight.Black, 
                        fontSize = 14.sp, 
                        color = Color(0xFF10B981)
                    )
                }
            }
        }
    }

    if (showVoidDialog && item is LedgerItem.Expense) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showVoidDialog = false },
            title = { Text("Batalkan Pengeluaran", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Alasan pembatalan (wajib diisi):")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (reason.isNotBlank()) {
                            viewModel.voidPettyCash(item.data.id, reason)
                            showVoidDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Batalkan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showVoidDialog = false }) {
                    Text("Tutup")
                }
            }
        )
    }
}

fun formatRupiah(amount: Double): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    formatter.maximumFractionDigits = 0
    return formatter.format(amount).replace("Rp", "Rp ")
}

fun formatTime(dateStr: String?): String {
    if (dateStr == null) return "-"
    return try {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        format.timeZone = TimeZone.getTimeZone("UTC")
        val date = format.parse(dateStr) ?: return "-"
        
        val outFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        outFormat.format(date)
    } catch (e: Exception) {
        dateStr.substringBefore("T")
    }
}
