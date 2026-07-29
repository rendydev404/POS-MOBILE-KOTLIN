package com.sukashawarma.pos.presentation.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.sukashawarma.pos.presentation.theme.*

@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel,
    modifier: Modifier = Modifier
) {
    val totalRevenue by viewModel.totalSalesToday.collectAsState()
    val ordersCount by viewModel.totalOrdersCount.collectAsState()
    val cashSales by viewModel.cashSales.collectAsState()
    val qrisSales by viewModel.qrisSales.collectAsState()
    val cardSales by viewModel.cardSales.collectAsState()
    val targetAmount by viewModel.dailyTargetAmount.collectAsState()
    val crewBonusList by viewModel.crewBonusList.collectAsState()
    
    val bestSellers by viewModel.bestSellers.collectAsState()
    val hourlySales by viewModel.hourlySales.collectAsState()
    
    val context = LocalContext.current

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // LEFT: Daily Sales Summary Report
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Assessment, contentDescription = null, tint = ShawarmaOrange)
                        Text(
                            text = "Laporan Penjualan Hari Ini",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextDarkPrimary
                        )
                    }
                    
                    Button(
                        onClick = { viewModel.exportToPdf(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f))
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export PDF")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = CreamBorder)
                Spacer(modifier = Modifier.height(16.dp))

                // Summary Cards
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = CreamBackground
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("TOTAL OMSET PENJUALAN", style = MaterialTheme.typography.bodyMedium, color = TextDarkSecondary)
                        Text(
                            text = "Rp ${String.format("%,.0f", totalRevenue)}",
                            style = MaterialTheme.typography.headlineLarge,
                            color = ShawarmaOrange,
                            fontWeight = FontWeight.Bold
                        )
                        Text("Total Order: $ordersCount Pesanan Lunas", style = MaterialTheme.typography.bodyLarge, color = TextDarkMuted)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                ReportRow("Pembayaran Tunai", "Rp ${String.format("%,.0f", cashSales)}")
                ReportRow("Pembayaran QRIS", "Rp ${String.format("%,.0f", qrisSales)}")
                ReportRow("Pembayaran Card/EDC", "Rp ${String.format("%,.0f", cardSales)}")

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = CreamBorder)
                Spacer(modifier = Modifier.height(12.dp))

                ReportRow("Target Omset Harian", "Rp ${String.format("%,.0f", targetAmount)}")
                val isTargetAchieved = totalRevenue >= targetAmount
                val targetStatus = if (isTargetAchieved) "TARGET TERCAPAI! 🎉" else "BELUM TERCAPAI"
                val targetColor = if (isTargetAchieved) StatusCompleted else TextDarkSecondary
                ReportRow("Status Target Harian", targetStatus, color = targetColor)
                
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = CreamBorder)
                Spacer(modifier = Modifier.height(16.dp))
                
                // Best Sellers
                Text("Top 5 Menu Terlaris", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                bestSellers.forEach { (name, qty) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row {
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(name, style = MaterialTheme.typography.bodyMedium)
                        }
                        Text("$qty Terjual", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // RIGHT: Hourly Trend and Crew Bonus Distribution
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            shape = RoundedCornerShape(12.dp),
            color = CreamSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, CreamBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Hourly Trend 
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.TrendingUp, contentDescription = null, tint = ShawarmaOrange)
                    Text(
                        text = "Tren Penjualan Per Jam",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextDarkPrimary
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(hourlySales.toList()) { (hour, amount) ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = CreamBackground
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("$hour:00", fontWeight = FontWeight.Bold, color = TextDarkSecondary)
                                Text("Rp ${String.format("%,.0f", amount)}", fontWeight = FontWeight.Bold, color = ShawarmaOrange)
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = CreamBorder)
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = ShawarmaOrange)
                    Text(
                        text = "Kalkulasi Bonus Crew Outlet",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextDarkPrimary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Pembagian Rata Bonus Bulanan per Staff Crew:", style = MaterialTheme.typography.titleMedium, color = TextDarkSecondary)
                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(crewBonusList) { crew ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = CreamBackground
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(text = crew.crewName, style = MaterialTheme.typography.titleMedium, color = TextDarkPrimary)
                                    Text(text = "Target Tercapai: ${crew.daysTargetAchieved} Hari", style = MaterialTheme.typography.bodyMedium, color = TextDarkSecondary)
                                }

                                Text(
                                    text = "+Rp ${String.format("%,.0f", crew.bonusAmountPerCrew)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = StatusCompleted,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportRow(
    label: String,
    value: String,
    color: Color = TextDarkPrimary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = TextDarkSecondary)
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
    }
}
