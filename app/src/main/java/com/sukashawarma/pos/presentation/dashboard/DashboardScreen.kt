package com.sukashawarma.pos.presentation.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukashawarma.pos.domain.model.Order
import com.sukashawarma.pos.domain.model.OrderStatus
import com.sukashawarma.pos.presentation.components.OrderCard
import com.sukashawarma.pos.presentation.theme.*

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNewOrderClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val pendingOrders by viewModel.pendingOrders.collectAsState()
    val preparingOrders by viewModel.preparingOrders.collectAsState()
    val completedOrders by viewModel.completedOrders.collectAsState()

    val currentOutletName by viewModel.currentOutletName.collectAsState()
    val totalLunasToday by viewModel.totalLunasToday.collectAsState()
    val criticalStockNames by viewModel.criticalStockNames.collectAsState()

    var activeSourceFilter by remember { mutableStateOf("Semua") }
    var preparingSubTab by remember { mutableStateOf("Antrean") }
    var completedSearchQuery by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CreamBackground)
    ) {
        // 1. Top Banner 1: TARGET HARI INI Progress Bar (Matching Screenshot)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            color = TargetPink
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Cancel, contentDescription = null, tint = MarqueeRed, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("TARGET HARI INI", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MarqueeRed)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Rp ${String.format("%,.0f", totalLunasToday)} / Rp 5 Jt", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = TextDarkPrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    val pct = if (totalLunasToday > 0) ((totalLunasToday / 5000000.0) * 100).toInt() else 0
                    Text("$pct%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MarqueeRed)
                }
            }
        }

        // 2. Top Banner 2: STOK KRITIS / HABIS Red Marquee (Matching Screenshot)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            color = MarqueeRed
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "STOK KRITIS/HABIS: $criticalStockNames",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // 3. Top Header Row (Title + Outlet Location Pill + Action Buttons)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Order",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextDarkPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = ShawarmaOrangeLight,
                        border = androidx.compose.foundation.BorderStroke(1.dp, ShawarmaOrange.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Home, contentDescription = null, tint = ShawarmaOrange, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Anda berada di cabang: $currentOutletName",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = ShawarmaOrangeDark
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // + Pesanan Baru Button (Matching Primary Orange Button in Screenshot)
                    Button(
                        onClick = onNewOrderClick,
                        colors = ButtonDefaults.buttonColors(containerColor = ShawarmaOrange),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Icon(Icons.Default.AddCircle, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pesanan Baru", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    // PENDAPATAN LUNAS Pill Card (Matching Screenshot)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CreamSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CreamBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = ShawarmaOrangeLight,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = ShawarmaOrange, modifier = Modifier.size(18.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("PENDAPATAN LUNAS", style = MaterialTheme.typography.bodySmall, fontSize = 9.sp, color = TextDarkMuted, fontWeight = FontWeight.Bold)
                                Text("Rp ${String.format("%,.0f", totalLunasToday)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextDarkPrimary)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. Status Bar & Source Filters (Matching Screenshot)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = CreamSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CreamBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = TextDarkMuted, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("PRINTER KASIR BELUM TERHUBUNG", style = MaterialTheme.typography.bodySmall, fontSize = 10.sp, color = TextDarkMuted, fontWeight = FontWeight.Bold)
                    }
                }

                // Filters: Semua | Online | Offline
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .background(CreamSurface, RoundedCornerShape(8.dp))
                        .border(1.dp, CreamBorder, RoundedCornerShape(8.dp))
                        .padding(4.dp)
                ) {
                    listOf("Semua", "🌐 Online", "📱 Offline").forEach { filter ->
                        val isSelected = activeSourceFilter == filter
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) ShawarmaOrangeLight else Color.Transparent)
                                .clickable { activeSourceFilter = filter }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = filter,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) ShawarmaOrange else TextDarkSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 5. 3-Column Order Board (Matching Screenshot Layout & Styling)
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Column 1: Menunggu Pembayaran
                BoardColumn(
                    modifier = Modifier.weight(1f),
                    title = "Menunggu Pembayaran",
                    icon = Icons.Default.Schedule,
                    badgeCount = pendingOrders.size,
                    emptyMessage = "Tidak ada pesanan tertunda\nPesanan baru akan muncul otomatis di sini."
                ) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(pendingOrders, key = { it.id }) { order ->
                            OrderCard(
                                order = order,
                                onStatusChange = { o, newStatus -> viewModel.updateOrderStatus(o, newStatus) },
                                onPrintReceipt = { o -> viewModel.printReceipt(o, isKitchen = false) }
                            )
                        }
                    }
                }

                // Column 2: Sedang Diproses
                BoardColumn(
                    modifier = Modifier.weight(1f),
                    title = "Sedang Diproses",
                    icon = Icons.Default.Restaurant,
                    badgeCount = preparingOrders.size,
                    hasSubTabs = true,
                    subTabState = preparingSubTab,
                    onSubTabChange = { preparingSubTab = it },
                    emptyMessage = "Tidak ada antrean masak\nDapur sedang santai, pesanan aktif akan muncul di sini."
                ) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(preparingOrders, key = { it.id }) { order ->
                            OrderCard(
                                order = order,
                                onStatusChange = { o, newStatus -> viewModel.updateOrderStatus(o, newStatus) },
                                onPrintReceipt = { o -> viewModel.printReceipt(o, isKitchen = true) }
                            )
                        }
                    }
                }

                // Column 3: Selesai / Lunas
                BoardColumn(
                    modifier = Modifier.weight(1f),
                    title = "Selesai / Lunas",
                    icon = Icons.Default.CheckCircle,
                    badgeCount = completedOrders.size,
                    hasSearchBar = true,
                    searchQuery = completedSearchQuery,
                    onSearchQueryChange = { completedSearchQuery = it },
                    emptyMessage = "Belum ada pesanan selesai hari ini"
                ) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(completedOrders.filter { completedSearchQuery.isEmpty() || it.orderNumber.toString().contains(completedSearchQuery) }, key = { it.id }) { order ->
                            OrderCard(
                                order = order,
                                onStatusChange = { o, newStatus -> viewModel.updateOrderStatus(o, newStatus) },
                                onPrintReceipt = { o -> viewModel.printReceipt(o, isKitchen = false) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardColumn(
    modifier: Modifier = Modifier,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badgeCount: Int,
    hasSubTabs: Boolean = false,
    subTabState: String = "Antrean",
    onSubTabChange: (String) -> Unit = {},
    hasSearchBar: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    emptyMessage: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(12.dp),
        color = CreamSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, CreamBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = ShawarmaOrange, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextDarkPrimary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CreamBackground
                ) {
                    Text(
                        text = "$badgeCount Pesanan",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = TextDarkSecondary
                    )
                }
            }

            if (hasSubTabs) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CreamBackground, RoundedCornerShape(8.dp))
                        .padding(3.dp)
                ) {
                    listOf("Antrean", "Terjadwal").forEach { tab ->
                        val isSelected = subTabState == tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) CreamSurface else Color.Transparent)
                                .clickable { onSubTabChange(tab) }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$tab 0",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) ShawarmaOrange else TextDarkSecondary
                            )
                        }
                    }
                }
            }

            if (hasSearchBar) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Cari antrian...", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (badgeCount == 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .border(1.dp, CreamBorder, RoundedCornerShape(10.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(icon, contentDescription = null, tint = TextDarkMuted, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = emptyMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextDarkMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.weight(1f)) {
                    content()
                }
            }
        }
    }
}
