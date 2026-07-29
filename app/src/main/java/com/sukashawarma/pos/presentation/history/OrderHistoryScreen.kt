package com.sukashawarma.pos.presentation.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(if (selectedTab == 0) Icons.Default.Assignment else Icons.Default.MonetizationOn, contentDescription = null, tint = ShawarmaOrange)
                Text(
                    text = "Histori Pesanan & Bonus Crew Supabase",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextDarkPrimary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = ShawarmaOrange
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Histori Pesanan", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Simulasi Bonus Crew", fontWeight = FontWeight.Bold) }
                )
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
fun OrderHistoryContent(viewModel: OrderHistoryViewModel) {
    val ordersHistory by viewModel.ordersHistory.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedFilter by viewModel.selectedPaymentFilter.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val filteredOrders = ordersHistory.filter { order ->
        (selectedFilter == "Semua" || order.paymentMethod.equals(selectedFilter, ignoreCase = true)) &&
                (searchQuery.isEmpty() || order.orderNumber.toString().contains(searchQuery) || (order.customerName?.contains(searchQuery, ignoreCase = true) == true))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.searchQuery.value = it },
                placeholder = { Text("Cari No. Antrian / Pelanggan...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                singleLine = true
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Semua", "CASH", "QRIS", "CARD").forEach { filter ->
                    val isSelected = selectedFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.selectedPaymentFilter.value = filter },
                        label = { Text(filter) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ShawarmaOrange,
                            selectedLabelColor = Color.White
                        )
                    )
                }
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
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = CreamBackground,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CreamBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = ShawarmaOrange
                                    ) {
                                        Text(
                                            text = "#${order.orderNumber}",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = order.customerName ?: "Pelanggan Walk-in",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TextDarkPrimary
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Metode: ${order.paymentMethod} • Sumber: ${order.source.uppercase()}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextDarkSecondary
                                )
                                Text(
                                    text = "Waktu: ${order.createdAt}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextDarkMuted
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Rp ${String.format("%,.0f", order.totalAmount)}",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = ShawarmaOrange
                                    )
                                    Text(
                                        text = order.status.uppercase(),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = StatusCompleted
                                    )
                                }

                                OutlinedButton(
                                    onClick = { },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Cetak Struk")
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
    val monthlyBonus by viewModel.monthlyBonusResult.collectAsState()
    val dailyBreakdown by viewModel.dailyBonusBreakdown.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

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

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = ShawarmaOrange.copy(alpha = 0.1f),
                border = androidx.compose.foundation.BorderStroke(1.dp, ShawarmaOrange)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Estimasi Bonus Crew (Total)", style = MaterialTheme.typography.bodyMedium, color = TextDarkSecondary)
                    Text(
                        "Rp ${String.format("%,.0f", monthlyBonus?.totalBonus ?: 0.0)}",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = ShawarmaOrange
                    )
                    
                    HorizontalDivider(color = ShawarmaOrange.copy(alpha = 0.2f))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Jumlah Crew:", color = TextDarkSecondary)
                        Text("${monthlyBonus?.crewCount ?: 0} Orang", fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Bonus per Crew:", color = TextDarkSecondary)
                        Text("Rp ${String.format("%,.0f", monthlyBonus?.bonusPerCrew ?: 0.0)}", fontWeight = FontWeight.Bold, color = StatusCompleted)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Hari Capai Target:", color = TextDarkSecondary)
                        Text("${monthlyBonus?.daysAchieved ?: 0} Hari", fontWeight = FontWeight.Bold)
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
                    Text("Tidak ada data bonus di bulan ini", color = TextDarkMuted)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text("Rincian Harian", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    items(dailyBreakdown) { day ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = if (day.isReached) StatusCompleted.copy(alpha = 0.1f) else CreamBorder.copy(alpha = 0.3f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (day.isReached) StatusCompleted else CreamBorder)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(day.date ?: "-", fontWeight = FontWeight.Bold)
                                    Text("Omset: Rp ${String.format("%,.0f", day.dailySales)}", style = MaterialTheme.typography.bodySmall)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        if (day.isReached) "+ Rp ${String.format("%,.0f", day.bonusAmount)}" else "Tidak Capai Target",
                                        fontWeight = FontWeight.Bold,
                                        color = if (day.isReached) StatusCompleted else TextDarkMuted
                                    )
                                    if (day.isReached) {
                                        Text("${day.additionalItems} porsi xtra", style = MaterialTheme.typography.bodySmall, color = ShawarmaOrange)
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
