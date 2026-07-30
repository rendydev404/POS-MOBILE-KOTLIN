package com.sukashawarma.pos.presentation.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukashawarma.pos.presentation.theme.*
import java.time.LocalDate

@Composable
fun OrderHistoryScreen(
    viewModel: OrderHistoryViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) }
    
    val currentMonth = LocalDate.now().monthValue
    val currentYear = LocalDate.now().year
    var selectedMonth by remember { mutableStateOf(currentMonth) }
    var selectedYear by remember { mutableStateOf(currentYear) }

    LaunchedEffect(selectedTab, selectedMonth, selectedYear) {
        if (selectedTab == 1) {
            viewModel.fetchBonusData(selectedMonth, selectedYear)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        shape = RoundedCornerShape(12.dp),
        color = CreamSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, CreamBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "Histori & Bonus",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextDarkPrimary
                    )
                    Text(
                        text = "Suka Shawarma - Auto-refresh 10s",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextDarkSecondary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .background(Color(0xFFF3F4F6), RoundedCornerShape(20.dp))
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (selectedTab == 0) Color.White else Color.Transparent)
                                .clickable { selectedTab = 0 }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text("Histori Pesanan", fontWeight = FontWeight.SemiBold, color = if (selectedTab == 0) Color.Black else Color.Gray)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (selectedTab == 1) Color.White else Color.Transparent)
                                .clickable { selectedTab = 1 }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text("Lihat Bonus", fontWeight = FontWeight.SemiBold, color = if (selectedTab == 1) Color.Black else Color.Gray)
                        }
                    }

                    IconButton(
                        onClick = { 
                            if (selectedTab == 0) {
                                viewModel.fetchOrderHistory()
                            } else {
                                viewModel.fetchBonusData(selectedMonth, selectedYear)
                            }
                        },
                        modifier = Modifier
                            .background(Color(0xFFF3F4F6), RoundedCornerShape(8.dp))
                            .size(40.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedTab == 0) {
                OrderHistoryContent(viewModel = viewModel)
            } else {
                BonusCrewContent(
                    viewModel = viewModel,
                    selectedMonth = selectedMonth,
                    selectedYear = selectedYear,
                    onMonthChange = { selectedMonth = it },
                    onYearChange = { selectedYear = it }
                )
            }
        }
    }
}

@Composable
fun SummaryCard(title: String, amount: String, titleColor: Color, textColor: Color, bgColor: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = if (bgColor == Color.White) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)) else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, color = titleColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(amount, color = textColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

@Composable
fun FilterBox(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text, fontSize = 13.sp, color = Color(0xFF374151))
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown, 
                contentDescription = null, 
                modifier = Modifier.size(16.dp),
                tint = Color(0xFF6B7280)
            )
        }
    }
}

@Composable
fun OrderHistoryContent(viewModel: OrderHistoryViewModel) {
    val ordersHistory by viewModel.ordersHistory.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedFilter by viewModel.selectedPaymentFilter.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val filteredOrders = ordersHistory.filter { order ->
        val statusMatches = when(selectedFilter) {
            "Selesai" -> order.status.equals("completed", true)
            "Menunggu" -> order.status.equals("pending", true)
            "Dibatalkan" -> order.status.equals("cancelled", true)
            else -> true
        }
        statusMatches && (searchQuery.isEmpty() || order.orderNumber.toString().contains(searchQuery) || (order.customerName?.contains(searchQuery, ignoreCase = true) == true))
    }

    val totalCash = ordersHistory.filter { it.paymentMethod.equals("CASH", true) }.sumOf { it.totalAmount }
    val totalQris = ordersHistory.filter { it.paymentMethod.equals("QRIS", true) }.sumOf { it.totalAmount }
    val totalCard = ordersHistory.filter { it.paymentMethod.equals("CARD", true) }.sumOf { it.totalAmount }
    val totalRevenue = totalCash + totalQris + totalCard

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SummaryCard("TUNAI / CASH", "Rp ${String.format("%,.0f", totalCash)}", Color(0xFF10B981), Color(0xFF10B981), Color.White, Modifier.weight(1f))
            SummaryCard("QRIS", "Rp ${String.format("%,.0f", totalQris)}", Color(0xFF3B82F6), Color(0xFF3B82F6), Color.White, Modifier.weight(1f))
            SummaryCard("DEBIT / CARD", "Rp ${String.format("%,.0f", totalCard)}", Color(0xFF8B5CF6), Color(0xFF8B5CF6), Color.White, Modifier.weight(1f))
            SummaryCard("TOTAL REVENUE", "Rp ${String.format("%,.0f", totalRevenue)}", Color.White, Color.White, ShawarmaOrange, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .background(Color(0xFFF3F4F6), RoundedCornerShape(20.dp))
                    .padding(4.dp)
            ) {
                listOf("Semua Pesanan", "Menunggu", "Selesai", "Dibatalkan").forEach { filter ->
                    val filterKey = if (filter == "Semua Pesanan") "Semua" else filter
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selectedFilter == filterKey) Color(0xFF1F2937) else Color.Transparent)
                            .clickable { viewModel.selectedPaymentFilter.value = filterKey }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(filter, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (selectedFilter == filterKey) Color.White else Color(0xFF4B5563))
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterBox("Hari Ini")
                FilterBox("Semua Channel")
                FilterBox("Semua Pembayaran")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ShawarmaOrange)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredOrders, key = { it.id }) { order ->
                    var isExpanded by remember { mutableStateOf(false) }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isExpanded = !isExpanded },
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CreamBorder)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("#${order.orderNumber}", fontWeight = FontWeight.Bold, color = Color(0xFF6B7280), modifier = Modifier.width(40.dp))
                                
                                Column(modifier = Modifier.weight(2f)) {
                                    val statusText = when(order.status.lowercase()) {
                                        "completed" -> "Selesai"
                                        "pending" -> "Menunggu"
                                        "cancelled" -> "Dibatalkan"
                                        else -> order.status
                                    }
                                    val statusColor = when(order.status.lowercase()) {
                                        "completed" -> Color(0xFF10B981)
                                        "pending" -> ShawarmaOrange
                                        "cancelled" -> Color(0xFFEF4444)
                                        else -> Color.Gray
                                    }
                                    Text(statusText, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(order.customerName ?: "Pelanggan Walk-in", fontWeight = FontWeight.Bold)
                                }
                                
                                Column(modifier = Modifier.weight(2f)) {
                                    Text(order.createdAt.take(10), color = Color(0xFF6B7280), fontSize = 12.sp)
                                    Text(order.createdAt.takeLast(8).take(5), color = Color(0xFF9CA3AF), fontSize = 12.sp)
                                }
                                
                                Column(modifier = Modifier.weight(1.5f)) {
                                    Text("${order.orderItems?.size ?: 0} item", color = Color(0xFF6B7280), fontSize = 13.sp)
                                }
                                
                                Column(modifier = Modifier.weight(1.5f)) {
                                    Text("Kasir", color = Color(0xFF9CA3AF), fontSize = 12.sp)
                                    Text("N/A", color = Color(0xFF374151), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }

                                Row(modifier = Modifier.weight(2.5f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                                    Text(
                                        text = "Rp ${String.format("%,.0f", order.totalAmount)}",
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF111827)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    val badgeColor = when(order.paymentMethod?.uppercase()) {
                                        "CASH" -> Color(0xFF10B981)
                                        "QRIS" -> Color(0xFF3B82F6)
                                        else -> Color(0xFF8B5CF6)
                                    }
                                    Surface(shape = RoundedCornerShape(16.dp), color = badgeColor.copy(alpha = 0.1f)) {
                                        Text(
                                            order.paymentMethod?.uppercase() ?: "N/A", 
                                            color = badgeColor, 
                                            fontSize = 10.sp, 
                                            fontWeight = FontWeight.Bold, 
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, 
                                        contentDescription = null, 
                                        tint = Color(0xFF9CA3AF), 
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            
                            if (isExpanded && order.orderItems != null && order.orderItems.isNotEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFF9FAFB))
                                        .padding(16.dp)
                                ) {
                                    order.orderItems.forEach { item ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row {
                                                Text("${item.quantity}x", fontWeight = FontWeight.Bold, color = Color(0xFF374151), modifier = Modifier.width(30.dp))
                                                Text(item.menuItemName, color = Color(0xFF4B5563))
                                            }
                                            Text("Rp ${String.format("%,.0f", item.subtotal)}", color = Color(0xFF374151), fontWeight = FontWeight.Medium)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Button(
                                            onClick = { /* Print Receipt */ },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                        ) {
                                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Cetak Struk", fontSize = 12.sp)
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
fun BonusCrewContent(
    viewModel: OrderHistoryViewModel,
    selectedMonth: Int,
    selectedYear: Int,
    onMonthChange: (Int) -> Unit,
    onYearChange: (Int) -> Unit
) {
    val activeCrewCount by viewModel.activeCrewCount.collectAsState()
    val simulatedCrewCount by viewModel.simulatedCrewCount.collectAsState()
    val dailyBreakdown by viewModel.dailyBonusBreakdown.collectAsState()
    val totalDaysReached by viewModel.totalDaysReached.collectAsState()
    val totalBonusPool by viewModel.totalBonusPool.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val displayCrewCount = if (simulatedCrewCount > 0) simulatedCrewCount else (if (activeCrewCount > 0) activeCrewCount else 1)
    val bonusPerPerson = if (displayCrewCount > 0) Math.floor(totalBonusPool / displayCrewCount) else 0.0

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Left Column: Filter & Summary
        Column(
            modifier = Modifier.weight(0.35f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = CreamBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, CreamBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Pilih Bulan & Tahun", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { 
                                var newMonth = selectedMonth - 1
                                var newYear = selectedYear
                                if (newMonth < 1) { newMonth = 12; newYear-- }
                                onMonthChange(newMonth)
                                onYearChange(newYear)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CreamBorder)
                        ) { Text("<", color = TextDarkPrimary) }
                        
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(
                                "$selectedMonth / $selectedYear", 
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Button(
                            onClick = { 
                                var newMonth = selectedMonth + 1
                                var newYear = selectedYear
                                if (newMonth > 12) { newMonth = 1; newYear++ }
                                onMonthChange(newMonth)
                                onYearChange(newYear)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CreamBorder)
                        ) { Text(">", color = TextDarkPrimary) }
                    }
                }
            }

            // Summary Card 1: Target Tercapai
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = ShawarmaOrange,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(8.dp), color = Color.White.copy(alpha = 0.2f)) {
                            Icon(Icons.Default.Print, contentDescription = null, tint = Color.White, modifier = Modifier.padding(8.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Target Tercapai", color = Color.White, style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(if (isLoading) "-" else "$totalDaysReached", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Hari di bulan ini", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                }
            }

            // Summary Card 2: Estimasi Bonus Per Orang
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, CreamBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(8.dp), color = StatusCompleted.copy(alpha = 0.2f)) {
                            Icon(Icons.Default.MonetizationOn, contentDescription = null, tint = StatusCompleted, modifier = Modifier.padding(8.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Bonus Per Orang", color = TextDarkSecondary, style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(if (isLoading) "-" else "Rp ${String.format("%,.0f", bonusPerPerson)}", color = TextDarkPrimary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Estimasi dibagikan ke $displayCrewCount orang", color = TextDarkMuted, style = MaterialTheme.typography.bodySmall)
                }
            }

            // Summary Card 3: Total Terkumpul
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, CreamBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFE0F2FE)) {
                            Icon(Icons.Default.MonetizationOn, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.padding(8.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Total Terkumpul", color = TextDarkSecondary, style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(if (isLoading) "-" else "Rp ${String.format("%,.0f", totalBonusPool)}", color = TextDarkPrimary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Keseluruhan sebelum dibagi", color = TextDarkMuted, style = MaterialTheme.typography.bodySmall)
                }
            }

            // Bonus Simulation Card
            if (dailyBreakdown.isNotEmpty()) {
                val lastDay = dailyBreakdown.last()
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = CreamBackground,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CreamBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Simulasi Bonus", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Jumlah Crew (aktif):", color = TextDarkSecondary)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = { if (displayCrewCount > 1) viewModel.simulatedCrewCount.value = displayCrewCount - 1 },
                                    colors = ButtonDefaults.buttonColors(containerColor = CreamBorder),
                                    modifier = Modifier.size(36.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) { Text("-", color = TextDarkPrimary) }
                                Text("$displayCrewCount", modifier = Modifier.padding(horizontal = 12.dp), fontWeight = FontWeight.Bold)
                                Button(
                                    onClick = { viewModel.simulatedCrewCount.value = displayCrewCount + 1 },
                                    colors = ButtonDefaults.buttonColors(containerColor = CreamBorder),
                                    modifier = Modifier.size(36.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) { Text("+", color = TextDarkPrimary) }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Target Harian:", color = TextDarkSecondary)
                            Text("Rp ${String.format("%,.0f", lastDay.targetAmount)}", fontWeight = FontWeight.Medium)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Bonus Per Porsi:", color = TextDarkSecondary)
                            Text("Rp ${String.format("%,.0f", lastDay.perItemBonus)}", fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        // Right Column: Daily Breakdown List
        Surface(
            modifier = Modifier.weight(0.65f).fillMaxHeight(),
            shape = RoundedCornerShape(10.dp),
            color = CreamBackground,
            border = androidx.compose.foundation.BorderStroke(1.dp, CreamBorder)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ShawarmaOrange)
                }
            } else if (dailyBreakdown.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Belum ada data untuk bulan ini", color = TextDarkMuted)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text("Rincian Penjualan per Hari", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Lihat rekam jejak pencapaian target harian outletmu", style = MaterialTheme.typography.bodySmall, color = TextDarkMuted)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    items(dailyBreakdown.reversed()) { day ->
                        val dateStr = day.date ?: ""
                        val isToday = if (dateStr.isNotEmpty()) java.time.LocalDate.parse(dateStr.split("T")[0]) == java.time.LocalDate.now() else false
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = if (isToday) ShawarmaOrange.copy(alpha = 0.1f) else Color.White,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isToday) ShawarmaOrange.copy(alpha = 0.5f) else CreamBorder)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text((day.date ?: "").split("T")[0], fontWeight = FontWeight.Bold, color = if (isToday) ShawarmaOrange else TextDarkPrimary)
                                        if (isToday) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(color = ShawarmaOrange.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                                Text("HARI INI", fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp), color = ShawarmaOrange, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    Text("Penjualan: Rp ${String.format("%,.0f", day.dailySales)}", style = MaterialTheme.typography.bodySmall)
                                    Text("Target: Rp ${String.format("%,.0f", day.targetAmount)}", style = MaterialTheme.typography.bodySmall, color = TextDarkSecondary)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        if (day.isReached) "+ Rp ${String.format("%,.0f", day.bonusAmount)}" else "-",
                                        fontWeight = FontWeight.Bold,
                                        color = if (day.isReached) StatusCompleted else TextDarkMuted
                                    )
                                    if (day.isReached) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Assignment, contentDescription = null, tint = StatusCompleted, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("${day.additionalItems} porsi xtra", style = MaterialTheme.typography.bodySmall, color = StatusCompleted)
                                        }
                                    } else {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Print, contentDescription = null, tint = Color.Red, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Tidak Capai", style = MaterialTheme.typography.bodySmall, color = Color.Red)
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
