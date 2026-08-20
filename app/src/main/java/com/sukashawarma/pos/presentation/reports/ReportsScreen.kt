package com.sukashawarma.pos.presentation.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukashawarma.pos.data.remote.dto.OrderDto
import com.sukashawarma.pos.presentation.theme.*
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel,
    modifier: Modifier = Modifier
) {
    val analytics by viewModel.analyticsData.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val range by viewModel.selectedRange.collectAsState()
    val channel by viewModel.channelFilter.collectAsState()
    val payment by viewModel.paymentFilter.collectAsState()
    val status by viewModel.statusFilter.collectAsState()
    val customStartDate by viewModel.customStartDate.collectAsState()
    val customEndDate by viewModel.customEndDate.collectAsState()
    val customDateError by viewModel.customDateError.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isOnline by com.sukashawarma.pos.data.remote.NetworkMonitor.isOnline.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF9FAFB))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HeaderAndFilters(
                range = range,
                channel = channel,
                payment = payment,
                status = status,
                customStartDate = customStartDate,
                customEndDate = customEndDate,
                customDateError = customDateError,
                onRangeChanged = { viewModel.updateRange(it) },
                onCustomDateChanged = { start, end -> viewModel.updateCustomDate(start, end) },
                onChannelChanged = { viewModel.updateFilters(it, payment, status) },
                onPaymentChanged = { viewModel.updateFilters(channel, it, status) },
                onStatusChanged = { viewModel.updateFilters(channel, payment, it) }
            )
        }

        if (isLoading) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ShawarmaOrange)
                }
            }
        } else {
            if (analytics.isFromLocalCache && !isOnline) {
                item { OfflineDataBanner() }
            }
            item { KPICards(analytics) }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(modifier = Modifier.weight(1f)) { StatusTransaksi(analytics) }
                    Box(modifier = Modifier.weight(1f)) { DistribusiPembayaran(analytics) }
                    Box(modifier = Modifier.weight(1f)) { TotalMenuTerjual(analytics) }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(modifier = Modifier.weight(1f)) { TrenPendapatanChart(analytics) }
                    Box(modifier = Modifier.weight(1f)) { DistribusiPerJamChart(analytics) }
                }
            }
            item { Top10Products(analytics) }
            item { TransactionHistoryTable(analytics.orders, searchQuery, viewModel::updateSearchQuery) }
            item { LaporanLaciCash(analytics) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeaderAndFilters(
    range: DateRange,
    channel: String,
    payment: String,
    status: String,
    customStartDate: String,
    customEndDate: String,
    customDateError: String?,
    onRangeChanged: (DateRange) -> Unit,
    onCustomDateChanged: (String, String) -> Unit,
    onChannelChanged: (String) -> Unit,
    onPaymentChanged: (String) -> Unit,
    onStatusChanged: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Laporan & Analitik Cabang", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF111827))
                Text("Insight bisnis Anda secara real-time", fontSize = 12.sp, color = Color(0xFF6B7280))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterDropdown(
                selected = when (channel) {
                    "all" -> "Semua Channel"
                    "food_apps" -> "Semua Food Apps"
                    "offline" -> "POS Kasir"
                    "website" -> "Website Online"
                    "endorse" -> "Endorse"
                    "gofood" -> "GoFood"
                    "grabfood" -> "GrabFood"
                    "shopeefood" -> "ShopeeFood"
                    "tiktokgo" -> "TikTok Go"
                    else -> channel.capitalize()
                },
                options = listOf(
                    "Semua Channel" to "all",
                    "Semua Food Apps" to "food_apps",
                    "POS Kasir" to "offline",
                    "Website Online" to "website",
                    "Endorse" to "endorse",
                    "GoFood" to "gofood",
                    "GrabFood" to "grabfood",
                    "ShopeeFood" to "shopeefood",
                    "TikTok Go" to "tiktokgo"
                ),
                onSelect = { onChannelChanged(it) }
            )
            
            FilterDropdown(
                selected = when (payment) {
                    "all" -> "Semua Metode"
                    "cash" -> "Tunai"
                    "qris" -> "QRIS"
                    "card" -> "Kartu"
                    else -> "Semua Metode"
                },
                options = listOf("Semua Metode" to "all", "Tunai" to "cash", "QRIS" to "qris", "Kartu" to "card"),
                onSelect = { onPaymentChanged(it) }
            )

            FilterDropdown(
                selected = when (status) {
                    "all" -> "Semua Status"
                    "completed" -> "Selesai"
                    "cancelled" -> "Dibatalkan"
                    else -> "Semua Status"
                },
                options = listOf("Semua Status" to "all", "Selesai" to "completed", "Dibatalkan" to "cancelled"),
                onSelect = { onStatusChanged(it) }
            )

                FilterDropdown(
                    selected = range.label,
                    options = DateRange.values().map { it.label to it.name },
                    onSelect = { name -> onRangeChanged(DateRange.valueOf(name)) }
                )
            }
        }

        if (range == DateRange.CUSTOM) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ReportDatePickerField(
                    label = "Tanggal mulai",
                    date = customStartDate.toReportLocalDateOrNull(),
                    onDatePicked = { date -> onCustomDateChanged(date.toString(), customEndDate) },
                    modifier = Modifier.weight(1f)
                )
                ReportDatePickerField(
                    label = "Tanggal akhir",
                    date = customEndDate.toReportLocalDateOrNull(),
                    onDatePicked = { date -> onCustomDateChanged(customStartDate, date.toString()) },
                    modifier = Modifier.weight(1f)
                )
            }
            customDateError?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportDatePickerField(
    label: String,
    date: LocalDate?,
    onDatePicked: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier.clickable { showDialog = true },
        shape = RoundedCornerShape(10.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp), tint = ShawarmaOrange)
            Column {
                Text(label, fontSize = 10.sp, color = Color(0xFF9CA3AF))
                Text(date?.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale("id", "ID"))) ?: "Pilih tanggal", fontSize = 13.sp, color = Color(0xFF374151), fontWeight = FontWeight.Medium)
            }
        }
    }
    if (showDialog) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(enabled = state.selectedDateMillis != null, onClick = {
                    state.selectedDateMillis?.let { onDatePicked(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    showDialog = false
                }) { Text("Pilih") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Batal") } }
        ) { DatePicker(state = state) }
    }
}

private fun String.toReportLocalDateOrNull(): LocalDate? =
    runCatching { LocalDate.parse(this) }.getOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterDropdown(selected: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        Surface(
            modifier = Modifier.menuAnchor().clickable { expanded = true }.height(40.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(selected, fontSize = 13.sp, color = Color(0xFF374151), fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, 
                    contentDescription = null, 
                    modifier = Modifier.size(18.dp),
                    tint = Color(0xFF6B7280)
                )
            }
        }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color.White)
        ) {
            options.forEach { option ->
                val isSelected = selected == option.first
                DropdownMenuItem(
                    text = { 
                        Text(
                            text = option.first, 
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) ShawarmaOrange else Color(0xFF374151)
                        ) 
                    },
                    onClick = {
                        onSelect(option.second)
                        expanded = false
                    },
                    modifier = Modifier.background(if (isSelected) ShawarmaOrangeLight else Color.Transparent)
                )
            }
        }
    }
}

/** Ditampilkan saat angka berasal dari cache Room, bukan dari agregasi server. */
@Composable
fun OfflineDataBanner(isOnline: Boolean = false) {
    if (isOnline) return
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFFEF3C7)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.CloudOff, contentDescription = null, tint = Color(0xFF92400E), modifier = Modifier.size(18.dp))
            Text(
                "Data offline — dihitung dari penyimpanan lokal dan mungkin belum lengkap.",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF92400E)
            )
        }
    }
}

@Composable
fun KPICards(data: AnalyticsData) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        KPICard(Modifier.weight(1f), "Omzet Kotor", formatRupiah(data.grossRevenue), "", Color(0xFFF59E0B), Icons.Default.MonetizationOn)
        KPICardLight(Modifier.weight(1f), "Pesanan Sukses", "${data.totalOrders}", "Transaksi berhasil diproses", Color(0xFF3B82F6), Icons.Default.ShoppingBag)
        KPICardLight(Modifier.weight(1f), "Rata-rata / Order", formatRupiah(data.avgOrderValue), "Rata-rata belanja per pesanan", Color(0xFFA855F7), Icons.Default.ShowChart)
        KPICardLight(Modifier.weight(1f), "Jam Tersibuk", data.peakHour?.let { "${it.toString().padStart(2, '0')}:00" } ?: "—", "Jam dengan pesanan terbanyak", Color(0xFF6366F1), Icons.Default.AccessTime)
    }
}

@Composable
fun KPICard(modifier: Modifier, title: String, value: String, subtitle: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = color, shadowElevation = 2.dp) {
        Column(modifier = Modifier.padding(20.dp)) {
            Box(modifier = Modifier.size(32.dp).background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(title.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.8f))
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            Text(subtitle, fontSize = 10.sp, color = Color.White.copy(alpha = 0.9f))
        }
    }
}

@Composable
fun KPICardLight(modifier: Modifier, title: String, value: String, subtitle: String, iconColor: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 2.dp) {
        Column(modifier = Modifier.padding(20.dp)) {
            Box(modifier = Modifier.size(32.dp).background(iconColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(title.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9CA3AF))
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color(0xFF111827))
            Spacer(modifier = Modifier.height(8.dp))
            Text(subtitle, fontSize = 10.sp, color = Color(0xFF9CA3AF))
        }
    }
}

@Composable
fun StatusTransaksi(data: AnalyticsData) {
    val totalProcessed = data.totalOrders + data.canceledCount
    val successRate = if (totalProcessed > 0) (data.totalOrders * 100 / totalProcessed) else 0
    val failureRate = if (totalProcessed > 0) (100 - successRate) else 0

    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 2.dp) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Status Transaksi", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF111827))
            Text("Pesanan Lunas vs Batal", fontSize = 12.sp, color = Color(0xFF9CA3AF))
            Spacer(modifier = Modifier.height(16.dp))

            // Selesai
            Row(modifier = Modifier.fillMaxWidth().background(Color(0xFFECFDF5), RoundedCornerShape(12.dp)).border(1.dp, Color(0xFFD1FAE5), RoundedCornerShape(12.dp)).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.size(32.dp).background(Color(0xFFD1FAE5), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(16.dp))
                    }
                    Column {
                        Text("Selesai", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF064E3B))
                        Text("Pembayaran sukses", fontSize = 10.sp, color = Color(0xFF059669))
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${data.totalOrders}", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color(0xFF047857))
                    Text("$successRate%", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(0xFF10B981))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Dibatalkan
            Row(modifier = Modifier.fillMaxWidth().background(Color(0xFFFEF2F2), RoundedCornerShape(12.dp)).border(1.dp, Color(0xFFFEE2E2), RoundedCornerShape(12.dp)).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.size(32.dp).background(Color(0xFFFEE2E2), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Cancel, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                    }
                    Column {
                        Text("Dibatalkan", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF7F1D1D))
                        Text("Kadaluarsa / Batal Kasir", fontSize = 10.sp, color = Color(0xFFDC2626))
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${data.canceledCount}", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color(0xFFB91C1C))
                    Text("$failureRate%", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(0xFFEF4444))
                }
            }
        }
    }
}

@Composable
fun DistribusiPembayaran(data: AnalyticsData) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 2.dp) {
        Column(modifier = Modifier.padding(20.dp).fillMaxHeight()) {
            Text("Distribusi Pembayaran", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF111827))
            Text("Rincian per metode bayar", fontSize = 12.sp, color = Color(0xFF9CA3AF))
            Spacer(modifier = Modifier.height(16.dp))

            if (data.paymentBreakdown.isEmpty()) {
                com.sukashawarma.pos.presentation.components.EmptyState(
                    title = "Belum ada data pembayaran",
                    icon = Icons.Default.CreditCard,
                    minHeight = 120.dp
                )
            } else {
                val sorted = data.paymentBreakdown.entries.sortedByDescending { it.value.count }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    sorted.forEach { (method, stats) ->
                        val (label, color) = when (method.lowercase()) {
                            "cash" -> "Tunai" to Color(0xFF10B981)
                            "qris" -> "QRIS" to Color(0xFF3B82F6)
                            "card" -> "Kartu" to Color(0xFF8B5CF6)
                            else -> "Lainnya" to Color(0xFF6B7280)
                        }
                        val pct = if (data.totalOrders > 0) (stats.count * 100 / data.totalOrders) else 0

                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(if(method=="cash") Icons.Default.Money else Icons.Default.CreditCard, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                                    Column {
                                        Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF374151))
                                        Text("${stats.count} pesanan", fontSize = 10.sp, color = Color(0xFF9CA3AF))
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("$pct%", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF111827))
                                    Text(formatRupiah(stats.revenue), fontSize = 10.sp, color = Color(0xFF9CA3AF))
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Box(modifier = Modifier.fillMaxWidth().height(8.dp).background(Color(0xFFF3F4F6), RoundedCornerShape(4.dp))) {
                                Box(modifier = Modifier.fillMaxWidth(pct / 100f).fillMaxHeight().background(color, RoundedCornerShape(4.dp)))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TotalMenuTerjual(data: AnalyticsData) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFFFFBEB), shadowElevation = 2.dp) {
        Column(modifier = Modifier.padding(20.dp).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.size(40.dp).background(Color(0xFFFDE68A), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.ShoppingBasket, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(20.dp))
                }
                Column {
                    Text("Total Menu Terjual", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF111827))
                    Text("Proporsi porsi terjual dari pesanan lunas", fontSize = 12.sp, color = Color(0xFF9CA3AF))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            Surface(shape = RoundedCornerShape(12.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFEF3C7))) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Column {
                        Text("TOTAL PORSI / ITEM", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9CA3AF))
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${data.totalItemsSold}", fontSize = 36.sp, fontWeight = FontWeight.Black, color = Color(0xFFD97706))
                            Text("Menu Terjual", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF92400E), modifier = Modifier.background(Color(0xFFFEF3C7), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp).padding(bottom = 6.dp))
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Variasi Menu", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF9CA3AF))
                        Text("${data.bestSellers.size} Produk", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFF374151))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            if (data.totalItemsSold == 0 || data.bestSellers.isEmpty()) {
                com.sukashawarma.pos.presentation.components.EmptyState(
                    title = "Belum ada item terjual",
                    icon = Icons.Default.ShoppingBasket,
                    minHeight = 100.dp
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    data.bestSellers.take(4).forEach { (name, stats) ->
                        val pct = if (data.totalItemsSold > 0) (stats.qty * 100 / data.totalItemsSold) else 0
                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF374151), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text("${stats.qty} porsi ($pct%)", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF111827))
                            }
                            Spacer(Modifier.height(4.dp))
                            Box(modifier = Modifier.fillMaxWidth().height(6.dp).background(Color(0xFFF3F4F6), RoundedCornerShape(3.dp))) {
                                Box(modifier = Modifier.fillMaxWidth(pct / 100f).fillMaxHeight().background(Color(0xFFF59E0B), RoundedCornerShape(3.dp)))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TrenPendapatanChart(data: AnalyticsData) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 2.dp) {
        Column(modifier = Modifier.padding(20.dp).fillMaxHeight()) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("Tren Pendapatan", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF111827))
                    Text("Pendapatan harian dalam periode", fontSize = 12.sp, color = Color(0xFF9CA3AF))
                }
                Text("${data.dailyEntries.size} hari", color = Color(0xFF10B981), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.background(Color(0xFFD1FAE5), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 6.dp))
            }
            Spacer(modifier = Modifier.height(24.dp))

            if (data.dailyEntries.isEmpty()) {
                com.sukashawarma.pos.presentation.components.EmptyState(
                    title = "Belum ada data untuk ditampilkan",
                    icon = Icons.Default.TrendingUp,
                    minHeight = 180.dp
                )
            } else {
                val maxDaily = data.dailyEntries.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1.0
                Row(modifier = Modifier.height(180.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                    data.dailyEntries.forEach { (date, rev) ->
                        val pct = (rev / maxDaily).toFloat()
                        val dayLabel = try {
                            LocalDate.parse(date).format(DateTimeFormatter.ofPattern("d MMM"))
                        } catch(e:Exception) { date }
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Box(modifier = Modifier.fillMaxWidth(0.8f).fillMaxHeight(Math.max(pct, 0.05f)).background(Color(0xFFFBBF24), RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)))
                            Spacer(Modifier.height(4.dp))
                            if (data.dailyEntries.size <= 14) {
                                Text(dayLabel, fontSize = 8.sp, color = Color(0xFF9CA3AF), maxLines = 1, overflow = TextOverflow.Visible)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DistribusiPerJamChart(data: AnalyticsData) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 2.dp) {
        Column(modifier = Modifier.padding(20.dp).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AccessTime, contentDescription = null, tint = Color(0xFFA855F7), modifier = Modifier.size(20.dp))
                Text("Distribusi Per Jam", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF111827))
            }
            Spacer(modifier = Modifier.height(24.dp))

            val maxHourly = data.hourly.maxOrNull()?.takeIf { it > 0 } ?: 1
            Row(modifier = Modifier.height(180.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                data.hourly.forEachIndexed { hour, count ->
                    val pct = (count.toFloat() / maxHourly)
                    val isActive = hour in 8..22
                    val isPeak = hour == data.peakHour && count > 0
                    
                    val color = if (isPeak) Color(0xFFA855F7) else if (isActive) Color(0xFFD8B4FE) else Color(0xFFE5E7EB)
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Box(modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(Math.max(pct, 0.02f)).background(color, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)))
                        Spacer(Modifier.height(2.dp))
                        if (hour % 3 == 0) {
                            Text("${hour.toString().padStart(2, '0')}", fontSize = 8.sp, color = Color(0xFF9CA3AF))
                        } else {
                            Text(" ", fontSize = 8.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Top10Products(data: AnalyticsData) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 2.dp) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                Text("Top 10 Produk Terlaris", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF111827))
            }
            Spacer(modifier = Modifier.height(20.dp))

            if (data.bestSellers.isEmpty()) {
                com.sukashawarma.pos.presentation.components.EmptyState(
                    title = "Belum ada data penjualan",
                    icon = Icons.Default.Star,
                    minHeight = 100.dp
                )
            } else {
                val maxQty = data.bestSellers.firstOrNull()?.second?.qty ?: 1
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    data.bestSellers.take(10).forEachIndexed { idx, (name, stats) ->
                        val pct = (stats.qty.toFloat() / maxQty)
                        val rank = when(idx) {
                            0 -> "🥇"
                            1 -> "🥈"
                            2 -> "🥉"
                            else -> "#${idx + 1}"
                        }
                        
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(rank, fontSize = 16.sp, modifier = Modifier.width(30.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Bold, color = Color(0xFF9CA3AF))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF374151), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("${stats.qty} terjual", fontSize = 12.sp, color = Color(0xFF6B7280), fontWeight = FontWeight.Medium)
                                        Text(formatRupiah(stats.revenue), fontSize = 12.sp, color = Color(0xFF111827), fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Box(modifier = Modifier.fillMaxWidth().height(6.dp).background(Color(0xFFF3F4F6), RoundedCornerShape(3.dp))) {
                                    Box(modifier = Modifier.fillMaxWidth(pct).fillMaxHeight().background(Color(0xFFFBBF24), RoundedCornerShape(3.dp)))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistoryTable(orders: List<OrderDto>, searchQuery: String, onSearchQueryChange: (String) -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 2.dp) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Histori Transaksi Detail", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF111827))
                    Text("Semua transaksi sukses pada periode ini", fontSize = 12.sp, color = Color(0xFF9CA3AF))
                }
                
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Cari no antrian / item...", fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF9CA3AF)) },
                    modifier = Modifier.width(300.dp).height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color(0xFFE5E7EB), focusedBorderColor = ShawarmaOrange)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            val filteredOrders = orders.filter { it.status == "completed" }.filter {
                searchQuery.isEmpty() || 
                it.orderNumber.toString().contains(searchQuery, ignoreCase = true) ||
                it.orderItems?.any { item -> item.displayName.contains(searchQuery, ignoreCase = true) } == true ||
                it.paymentMethod?.contains(searchQuery, ignoreCase = true) == true
            }

            Surface(shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF3F4F6))) {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 400.dp)) {
                    item {
                        Row(modifier = Modifier.fillMaxWidth().background(Color(0xFFF9FAFB)).padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("No. Antrian", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF6B7280), modifier = Modifier.weight(0.5f))
                            Text("Waktu", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF6B7280), modifier = Modifier.weight(1f))
                            Text("Nama Item", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF6B7280), modifier = Modifier.weight(2f))
                            Text("Channel", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF6B7280), modifier = Modifier.weight(0.8f))
                            Text("Metode Bayar", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF6B7280), modifier = Modifier.weight(0.8f))
                            Text("Total Transaksi", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF6B7280), modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                        }
                        HorizontalDivider(color = Color(0xFFF3F4F6))
                    }
                    if (filteredOrders.isEmpty()) {
                        item {
                            com.sukashawarma.pos.presentation.components.EmptyState(
                                title = "Data tidak ditemukan",
                                icon = Icons.Default.Search,
                                minHeight = 140.dp
                            )
                        }
                    } else {
                        items(filteredOrders) { order ->
                            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(0.5f)) {
                                    Text("#${order.orderNumber}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF111827))
                                    if (order.isSyncedFromOffline == true) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF10B981).copy(alpha = 0.12f)) {
                                            Text("SYNC", color = Color(0xFF10B981), fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                        }
                                    }
                                }
                                // Membuang offset lalu mem-parse sebagai LocalDateTime membaca
                                // jam APA ADANYA: baris server ber-offset "+07:00" kebetulan
                                // benar, tapi baris dari cache Room berformat UTC ("…Z") tampil
                                // mundur 7 jam — dan pesanan dini hari WIB ikut salah tanggal.
                                val dateStr = com.sukashawarma.pos.domain.gate.JakartaTime.dateTimeStringOf(order.createdAt)
                                Text(dateStr, fontSize = 12.sp, color = Color(0xFF6B7280), modifier = Modifier.weight(1f))
                                val itemsStr = order.orderItems?.joinToString(", ") { it.displayName } ?: ""
                                Text(itemsStr, fontSize = 14.sp, color = Color(0xFF4B5563), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(2f))
                                Box(modifier = Modifier.weight(0.8f)) {
                                    com.sukashawarma.pos.presentation.components.OrderSourceBadge(source = order.source, channel = order.channel)
                                }
                                Text(order.paymentMethod?.uppercase() ?: "-", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB), modifier = Modifier.weight(0.8f).background(Color(0xFFEFF6FF), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp))
                                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                    Text(formatRupiah(order.totalAmount), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF111827), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                                    val subsidy = order.promoSubsidy?.takeIf { it > 0.0 }
                                    if (subsidy != null) {
                                        Text("Potongan App: -${formatRupiah(subsidy)}", color = Color(0xFFEF4444), fontSize = 10.sp, fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                                    }
                                    val discount = order.discountAmount?.takeIf { it > 0.0 }
                                    if (discount != null) {
                                        Text("Diskon: -${formatRupiah(discount)}", color = Color(0xFFF59E0B), fontSize = 10.sp, fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                                    }
                                }
                            }
                            HorizontalDivider(color = Color(0xFFF3F4F6))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LaporanLaciCash(data: AnalyticsData) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 2.dp) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Laporan Laci Cash", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF111827))
            Text("Perbandingan uang menurut sistem vs hasil hitung manual kasir saat tutup shift", fontSize = 12.sp, color = Color(0xFF9CA3AF))
            Spacer(modifier = Modifier.height(24.dp))

            if (data.shifts.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFF9FAFB), RoundedCornerShape(12.dp)).border(1.dp, Color(0xFFF3F4F6), RoundedCornerShape(12.dp))) {
                    com.sukashawarma.pos.presentation.components.EmptyState(
                        title = "Data shift tidak ditemukan",
                        icon = Icons.Default.PointOfSale,
                        minHeight = 140.dp
                    )
                }
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(end = 4.dp)
                ) {
                    items(data.shifts) { shift ->
                        // Postgres/PostgREST balikin timestamp berformat "+00:00" (bukan "Z"),
                        // jadi LocalDateTime.parse polos selalu gagal — pakai JakartaTime yang
                        // sudah menangani semua bentuk offset PostgREST (lihat LedgerItem.parseDate).
                        val zone = com.sukashawarma.pos.domain.gate.JakartaTime.ZONE
                        val indoLocale = Locale("id", "ID")
                        val fullFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy 'pukul' HH:mm", indoLocale)
                        val dateOnlyFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", indoLocale)
                        val timeOnlyFormatter = DateTimeFormatter.ofPattern("HH:mm")

                        val startZoned = com.sukashawarma.pos.domain.gate.JakartaTime.instantOrNull(shift.startTime)?.atZone(zone)
                        val endZoned = com.sukashawarma.pos.domain.gate.JakartaTime.instantOrNull(shift.endTime)?.atZone(zone)
                        val sameDay = startZoned != null && endZoned != null && startZoned.toLocalDate() == endZoned.toLocalDate()

                        // Shift yang start & selesainya beda hari (mis. hasil koreksi manual di
                        // database) ditampilkan dengan tanggal lengkap di kedua sisi, bukan cuma
                        // satu tanggal + dua jam — supaya rentang aslinya tidak terlihat ngawur.
                        val dateStr = when {
                            startZoned == null -> "Tanggal Shift"
                            sameDay -> startZoned.format(dateOnlyFormatter)
                            else -> "${startZoned.format(fullFormatter)} → ${endZoned?.format(fullFormatter) ?: "Berjalan"} WIB"
                        }
                        val startTimeStr = startZoned?.format(timeOnlyFormatter) ?: "-"
                        val endTimeStr = endZoned?.format(timeOnlyFormatter) ?: "Berjalan"
                        
                        val variance = shift.variance ?: 0.0
                        val expectedPc = shift.expectedEndingPettyCash ?: 0.0
                        val actualPc = shift.actualEndingPettyCash ?: 0.0
                        val pcVariance = actualPc - expectedPc
                        val totalDiff = variance + pcVariance

                        Surface(
                            modifier = Modifier.widthIn(min = 360.dp, max = 460.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB))
                        ) {
                            Column {
                                // Header
                                Row(modifier = Modifier.fillMaxWidth().background(Color(0xFFFFFBEB)).border(1.dp, Color(0xFFFEF3C7)).padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(36.dp).background(Color(0xFFFEF3C7), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.CalendarToday, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(16.dp))
                                        }
                                        Column {
                                            Text(
                                                dateStr,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = Color(0xFF78350F),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (sameDay) {
                                                Text("$startTimeStr - $endTimeStr WIB", fontSize = 12.sp, color = Color(0xFFB45309))
                                            }
                                        }
                                    }
                                    val (label, bg, fg, border) = when {
                                        totalDiff == 0.0 -> listOf("✅ Uang Pas", Color(0xFFECFDF5), Color(0xFF047857), Color(0xFFA7F3D0))
                                        totalDiff > 0 -> listOf("Uang Lebih ${formatRupiah(totalDiff)}", Color(0xFFEFF6FF), Color(0xFF1D4ED8), Color(0xFFBFDBFE))
                                        else -> listOf("Uang Kurang ${formatRupiah(Math.abs(totalDiff))}", Color(0xFFFEF2F2), Color(0xFFB91C1C), Color(0xFFFECACA))
                                    }
                                    Text(label as String, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = fg as Color, modifier = Modifier.background(bg as Color, RoundedCornerShape(8.dp)).border(1.dp, border as Color, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp))
                                }
                                
                                // Body
                                Row(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    // Uang Laci
                                    Column(modifier = Modifier.weight(1f).background(Color(0xFFF9FAFB), RoundedCornerShape(12.dp)).border(1.dp, Color(0xFFF3F4F6), RoundedCornerShape(12.dp)).padding(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(16.dp))
                                            Text("PENJUALAN CASH (SALES)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF374151))
                                        }
                                        Spacer(Modifier.height(16.dp))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Hitungan Manual Kasir", fontSize = 14.sp, color = Color(0xFF6B7280))
                                            Text(formatRupiah(shift.actualEndingCash ?: 0.0), fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFF111827))
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Perhitungan Sistem", fontSize = 12.sp, color = Color(0xFF9CA3AF))
                                            Text(formatRupiah(shift.expectedEndingCash ?: 0.0), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF6B7280))
                                        }
                                        Spacer(Modifier.height(16.dp))
                                        HorizontalDivider(color = Color(0xFFE5E7EB))
                                        Spacer(Modifier.height(12.dp))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text("STATUS Penjualan Cash", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9CA3AF))
                                            val (l, b, f, br) = when {
                                                variance == 0.0 -> listOf("Pas (Balance)", Color(0xFFF3F4F6), Color(0xFF4B5563), Color(0xFFE5E7EB))
                                                variance > 0 -> listOf("Lebih ${formatRupiah(variance)}", Color(0xFFECFDF5), Color(0xFF047857), Color(0xFFA7F3D0))
                                                else -> listOf("Kurang ${formatRupiah(Math.abs(variance))}", Color(0xFFFEF2F2), Color(0xFFB91C1C), Color(0xFFFECACA))
                                            }
                                            Text(l as String, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = f as Color, modifier = Modifier.background(b as Color, RoundedCornerShape(8.dp)).border(1.dp, br as Color, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp))
                                        }
                                    }
                                    // Petty Cash (Since not in API, we leave it as 0 logic)
                                    Column(modifier = Modifier.weight(1f).background(Color(0xFFF9FAFB), RoundedCornerShape(12.dp)).border(1.dp, Color(0xFFF3F4F6), RoundedCornerShape(12.dp)).padding(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(Icons.Default.Money, contentDescription = null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(16.dp))
                                            Text("Patty Cash", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF374151))
                                        }
                                        Spacer(Modifier.height(16.dp))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Hitungan Manual Kasir", fontSize = 14.sp, color = Color(0xFF6B7280))
                                            Text(formatRupiah(actualPc), fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFF1D4ED8))
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Perhitungan Sistem", fontSize = 12.sp, color = Color(0xFF9CA3AF))
                                            Text(formatRupiah(expectedPc), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF6B7280))
                                        }
                                        Spacer(Modifier.height(16.dp))
                                        HorizontalDivider(color = Color(0xFFE5E7EB))
                                        Spacer(Modifier.height(12.dp))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text("PATTY CASH", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9CA3AF))
                                            val (lPc, bPc, fPc, brPc) = when {
                                                pcVariance == 0.0 -> listOf("Pas (Balance)", Color(0xFFF3F4F6), Color(0xFF4B5563), Color(0xFFE5E7EB))
                                                pcVariance > 0 -> listOf("Lebih ${formatRupiah(pcVariance)}", Color(0xFFECFDF5), Color(0xFF047857), Color(0xFFA7F3D0))
                                                else -> listOf("Kurang ${formatRupiah(Math.abs(pcVariance))}", Color(0xFFFEF2F2), Color(0xFFB91C1C), Color(0xFFFECACA))
                                            }
                                            Text(lPc as String, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = fPc as Color, modifier = Modifier.background(bPc as Color, RoundedCornerShape(8.dp)).border(1.dp, brPc as Color, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp))
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

fun formatRupiah(amount: Double): String {
    return "Rp " + String.format("%,.0f", amount).replace(',', '.')
}
