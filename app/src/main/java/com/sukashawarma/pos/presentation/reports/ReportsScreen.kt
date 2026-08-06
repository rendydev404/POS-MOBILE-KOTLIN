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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukashawarma.pos.data.remote.dto.OrderDto
import com.sukashawarma.pos.presentation.theme.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
    val searchQuery by viewModel.searchQuery.collectAsState()

    val context = LocalContext.current

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
                onRangeChanged = { viewModel.updateRange(it) },
                onChannelChanged = { viewModel.updateFilters(it, payment, status) },
                onPaymentChanged = { viewModel.updateFilters(channel, it, status) },
                onStatusChanged = { viewModel.updateFilters(channel, payment, it) },
                onExport = { viewModel.exportToPdf(context) },
                exportDisabled = analytics.totalOrders == 0
            )
        }

        if (isLoading) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ShawarmaOrange)
                }
            }
        } else {
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
    onRangeChanged: (DateRange) -> Unit,
    onChannelChanged: (String) -> Unit,
    onPaymentChanged: (String) -> Unit,
    onStatusChanged: (String) -> Unit,
    onExport: () -> Unit,
    exportDisabled: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
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
                    else -> channel.capitalize()
                },
                options = listOf("Semua Channel" to "all", "Semua Food Apps" to "food_apps", "POS Kasir" to "offline", "GoFood" to "gofood", "GrabFood" to "grabfood"),
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

            Button(
                onClick = onExport,
                enabled = !exportDisabled,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Cetak Laporan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterDropdown(selected: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        Surface(
            modifier = Modifier.menuAnchor().clickable { expanded = true }.height(36.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(selected, fontSize = 12.sp, color = Color(0xFF374151), fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown, 
                    contentDescription = null, 
                    modifier = Modifier.size(16.dp),
                    tint = Color(0xFF6B7280)
                )
            }
        }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.first, fontSize = 12.sp) },
                    onClick = {
                        onSelect(option.second)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun KPICards(data: AnalyticsData) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        KPICard(Modifier.weight(1f), "Omzet Kotor", formatRupiah(data.totalRevenue), "*Sebelum potongan", Color(0xFFF59E0B), Icons.Default.MonetizationOn)
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
                Box(modifier = Modifier.height(120.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Belum ada data", color = Color(0xFF9CA3AF))
                }
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
                Box(modifier = Modifier.height(80.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Belum ada item terjual", fontSize = 12.sp, color = Color(0xFF9CA3AF))
                }
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
                Box(modifier = Modifier.height(180.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Belum ada data untuk ditampilkan", fontSize = 14.sp, color = Color(0xFF9CA3AF))
                }
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
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    Text("Belum ada data penjualan", color = Color(0xFF9CA3AF))
                }
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
                it.orderItems?.any { item -> item.menuItemName.contains(searchQuery, ignoreCase = true) } == true ||
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
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("Data tidak ditemukan", color = Color(0xFF9CA3AF))
                            }
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
                                val dateStr = try {
                                    val cleanDate = if(order.createdAt.contains("+")) order.createdAt.substringBefore("+") else order.createdAt.substringBefore("Z")
                                    LocalDateTime.parse(cleanDate).format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))
                                } catch(e:Exception) { order.createdAt }
                                Text(dateStr, fontSize = 12.sp, color = Color(0xFF6B7280), modifier = Modifier.weight(1f))
                                val itemsStr = order.orderItems?.joinToString(", ") { it.menuItemName } ?: ""
                                Text(itemsStr, fontSize = 14.sp, color = Color(0xFF4B5563), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(2f))
                                Text(order.channel ?: "-", fontSize = 12.sp, color = Color(0xFF6B7280), modifier = Modifier.weight(0.8f))
                                Text(order.paymentMethod?.uppercase() ?: "-", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB), modifier = Modifier.weight(0.8f).background(Color(0xFFEFF6FF), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp))
                                Text(formatRupiah(order.totalAmount), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF111827), modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
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
                Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFF9FAFB), RoundedCornerShape(12.dp)).border(1.dp, Color(0xFFF3F4F6), RoundedCornerShape(12.dp)).padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Data shift tidak ditemukan", color = Color(0xFF9CA3AF), fontWeight = FontWeight.Medium)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    data.shifts.forEach { shift ->
                        val dateStr = try {
                            val dt = LocalDateTime.parse(shift.startTime?.substringBefore("Z") ?: "")
                            dt.format(DateTimeFormatter.ofPattern("d MMMM yyyy"))
                        } catch(e:Exception) { "Tanggal Shift" }
                        val startTimeStr = try { LocalDateTime.parse(shift.startTime?.substringBefore("Z") ?: "").format(DateTimeFormatter.ofPattern("HH:mm")) } catch(e:Exception) { "" }
                        val endTimeStr = if (shift.endTime != null) {
                            try { LocalDateTime.parse(shift.endTime.substringBefore("Z")).format(DateTimeFormatter.ofPattern("HH:mm")) } catch(e:Exception) { "" }
                        } else "Berjalan"
                        
                        val variance = shift.variance ?: 0.0
                        val expectedPc = 0.0 // not available in ShiftDto currently
                        val actualPc = 0.0 
                        val pcVariance = 0.0
                        val totalDiff = variance + pcVariance

                        Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB))) {
                            Column {
                                // Header
                                Row(modifier = Modifier.fillMaxWidth().background(Color(0xFFFFFBEB)).border(1.dp, Color(0xFFFEF3C7)).padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(36.dp).background(Color(0xFFFEF3C7), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.CalendarToday, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(16.dp))
                                        }
                                        Column {
                                            Text("Shift $dateStr", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF78350F))
                                            Text("$startTimeStr - $endTimeStr", fontSize = 12.sp, color = Color(0xFFB45309))
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
                                            Text("UANG LACI (SALES)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF374151))
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
                                            Text("STATUS UANG LACI", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9CA3AF))
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
                                            Text("DANA OPERASIONAL", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF374151))
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
                                            Text("STATUS OPERASIONAL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9CA3AF))
                                            Text("Pas (Balance)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4B5563), modifier = Modifier.background(Color(0xFFF3F4F6), RoundedCornerShape(8.dp)).border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp))
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
