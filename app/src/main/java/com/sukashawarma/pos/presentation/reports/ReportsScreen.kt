package com.sukashawarma.pos.presentation.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
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

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // LEFT 50%: Daily Sales Summary Report
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
                    Icon(Icons.Default.Assessment, contentDescription = null, tint = AmberPrimary)
                    Text(
                        text = "Laporan Penjualan Hari Ini",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = SlateBorder)
                Spacer(modifier = Modifier.height(16.dp))

                // Summary Cards
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = SlateCard
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("TOTAL OMSET PENJUALAN", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                        Text(
                            text = "Rp ${String.format("%,.0f", totalRevenue)}",
                            style = MaterialTheme.typography.headlineLarge,
                            color = AmberPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text("Total Order: $ordersCount Pesanan Lunas", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                ReportRow("Pembayaran Tunai", "Rp ${String.format("%,.0f", cashSales)}")
                ReportRow("Pembayaran QRIS", "Rp ${String.format("%,.0f", qrisSales)}")
                ReportRow("Pembayaran Card/EDC", "Rp ${String.format("%,.0f", cardSales)}")

                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = SlateBorder)
                Spacer(modifier = Modifier.height(12.dp))

                ReportRow("Target Omset Harian", "Rp ${String.format("%,.0f", targetAmount)}")
                val isTargetAchieved = totalRevenue >= targetAmount
                val targetStatus = if (isTargetAchieved) "TARGET TERCAPAI! 🎉" else "BELUM TERCAPAI"
                val targetColor = if (isTargetAchieved) StatusCompleted else StatusPending
                ReportRow("Status Target Harian", targetStatus, color = targetColor)
            }
        }

        // RIGHT 50%: Crew Bonus Distribution Calculator
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
                    Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = AmberPrimary)
                    Text(
                        text = "Kalkulasi Bonus Crew Outlet",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = SlateBorder)
                Spacer(modifier = Modifier.height(12.dp))

                Text("Pembagian Rata Bonus Bulanan per Staff Crew:", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(crewBonusList) { crew ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = SlateCard
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(text = crew.crewName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                    Text(text = "Target Tercapai: ${crew.daysTargetAchieved} Hari", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
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
    color: Color = TextPrimary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
    }
}
