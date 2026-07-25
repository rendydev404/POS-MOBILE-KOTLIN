package com.sukashawarma.pos.presentation.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
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

@Composable
fun OrderHistoryScreen(
    viewModel: OrderHistoryViewModel,
    modifier: Modifier = Modifier
) {
    val ordersHistory by viewModel.ordersHistory.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedFilter by viewModel.selectedPaymentFilter.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val filteredOrders = ordersHistory.filter { order ->
        (selectedFilter == "Semua" || order.paymentMethod.equals(selectedFilter, ignoreCase = true)) &&
                (searchQuery.isEmpty() || order.orderNumber.toString().contains(searchQuery) || (order.customerName?.contains(searchQuery, ignoreCase = true) == true))
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
                Icon(Icons.Default.Assignment, contentDescription = null, tint = ShawarmaOrange)
                Text(
                    text = "Histori Pesanan & Bonus Crew Supabase",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextDarkPrimary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = CreamBorder)
            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar & Filter Row
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

            // Order History List
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
}
