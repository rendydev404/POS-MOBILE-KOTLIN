package com.sukashawarma.pos.presentation.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sukashawarma.pos.data.remote.dto.OrderDto
import com.sukashawarma.pos.data.remote.dto.OrderItemDto
import com.sukashawarma.pos.domain.gate.JakartaTime
import com.sukashawarma.pos.domain.usecase.OrderStatusFilter
import com.sukashawarma.pos.presentation.reports.FilterDropdown
import com.sukashawarma.pos.presentation.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.floor

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

    val dateFilter by viewModel.dateFilter.collectAsState()
    val statusFilter by viewModel.selectedPaymentFilter.collectAsState()
    val paymentMethodFilter by viewModel.selectedPaymentMethodFilter.collectAsState()
    val channelFilter by viewModel.selectedChannelFilter.collectAsState()
    val customStart by viewModel.customStartDate.collectAsState()
    val customEnd by viewModel.customEndDate.collectAsState()

    LaunchedEffect(selectedTab, selectedMonth, selectedYear) {
        if (selectedTab == 1) {
            viewModel.fetchBonusData(selectedMonth, selectedYear)
        }
    }

    // Re-fetch whenever any histori filter changes — mirrors web's queryKey deps.
    LaunchedEffect(selectedTab, dateFilter, statusFilter, paymentMethodFilter, channelFilter, customStart, customEnd) {
        if (selectedTab == 0) {
            if (dateFilter != "custom" || (customStart != null && customEnd != null)) {
                viewModel.fetchOrderHistory()
            }
        }
    }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        val isScreenNarrow = maxWidth < 760.dp
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(12.dp),
            color = CreamSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, CreamBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                val titleBlock: @Composable () -> Unit = {
                    Column {
                        Text(
                            text = "Histori & Bonus",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextDarkPrimary
                        )
                        if (selectedTab == 0 && dateFilter == "today") {
                            val isRealtimeConnected by com.sukashawarma.pos.data.remote.GlobalEventBus.isRealtimeConnected.collectAsState()
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(if (isRealtimeConnected) Color(0xFF10B981) else Color(0xFFCBD5E1))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isRealtimeConnected) "Suka Shawarma - Update realtime aktif" else "Suka Shawarma - Menyambungkan realtime...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextDarkSecondary
                                )
                            }
                        } else {
                            Text(
                                text = "Suka Shawarma",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextDarkSecondary
                            )
                        }
                    }
                }

                val tabsBlock: @Composable () -> Unit = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .background(Color(0xFFF3F4F6), RoundedCornerShape(20.dp))
                                .padding(4.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (selectedTab == 0) Color.White else Color.Transparent,
                                onClick = { selectedTab = 0 }
                            ) {
                                Text(
                                    "Histori Pesanan",
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selectedTab == 0) Color.Black else Color.Gray,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (selectedTab == 1) Color.White else Color.Transparent,
                                onClick = { selectedTab = 1 }
                            ) {
                                Text(
                                    "Lihat Bonus",
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selectedTab == 1) Color.Black else Color.Gray,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
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

                if (isScreenNarrow) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        titleBlock()
                        tabsBlock()
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        titleBlock()
                        tabsBlock()
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
            Text(title, color = titleColor, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(4.dp))
            Text(amount, color = textColor, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ── Channel helpers (mirrors lib/channels.ts) ──────────────────────────────

private fun normalizeChannelId(id: String?): String? {
    if (id.isNullOrBlank()) return null
    val norm = id.lowercase()
    return if (norm in listOf("tiktok", "tiktok_go")) "tiktokgo" else norm
}

fun channelLabel(id: String?): String {
    return when (normalizeChannelId(id)) {
        "gofood" -> "GoFood"
        "shopeefood" -> "ShopeeFood"
        "grabfood" -> "GrabFood"
        "tiktokgo" -> "TikTok Go"
        "website" -> "Website Online"
        else -> id ?: ""
    }
}

fun channelColor(id: String?): Color {
    return when (normalizeChannelId(id)) {
        "gofood" -> Color(0xFF00AA13)
        "shopeefood" -> Color(0xFFEE4D2D)
        "grabfood" -> Color(0xFF00B14F)
        "tiktokgo" -> Color(0xFF000000)
        "website" -> Color(0xFF2563EB)
        else -> Color(0xFF6B7280)
    }
}

private val dateFilterOptions = listOf(
    "Hari Ini" to "today",
    "Kemarin" to "yesterday",
    "7 Hari Terakhir" to "7d",
    "30 Hari Terakhir" to "30d",
    "Semua Waktu" to "all",
    "Custom Tanggal" to "custom"
)

private val paymentMethodOptions = listOf(
    "Semua Pembayaran" to "all",
    "Tunai (Cash)" to "cash",
    "QRIS" to "qris",
    "Kartu (Card)" to "card"
)

// Nilai kunci di sini harus dikenali OrderChannel — di situlah pemetaannya ke isi
// kolom `orders.channel` yang sebenarnya. "Semua Food Apps" perlu ada karena
// sebagian pesanan ojol tersimpan dengan channel generik 'food_apps' dan tanpa
// opsi ini tidak akan pernah muncul di filter mana pun.
private val channelFilterOptions = listOf(
    "Semua Channel" to "all",
    "Kasir / Dine-in" to "offline",
    "Semua Food Apps" to "food_apps",
    "GoFood" to "gofood",
    "ShopeeFood" to "shopeefood",
    "GrabFood" to "grabfood",
    "TikTok Go" to "tiktokgo",
    "Website Online" to "website"
)

private fun labelFor(options: List<Pair<String, String>>, value: String): String =
    options.firstOrNull { it.second == value }?.first ?: value

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickField(label: String, date: LocalDate?, onDatePicked: (LocalDate) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.clickable { showDialog = true },
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF6B7280))
            Column {
                Text(label, fontSize = 10.sp, color = Color(0xFF9CA3AF))
                Text(date?.toString() ?: "Pilih tanggal", fontSize = 13.sp, color = Color(0xFF374151), fontWeight = FontWeight.Medium)
            }
        }
    }

    if (showDialog) {
        val initialMillis = date?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onDatePicked(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDialog = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Batal") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

// ── Order item name marker parsing (mirrors page.tsx:469-554) ─────────────

data class ParsedOrderItem(
    val raw: OrderItemDto,
    val name: String,
    val note: String,
    val itemId: String,
    val parentId: String?
)

private fun parseOrderItemName(item: OrderItemDto, index: Int): ParsedOrderItem {
    var name = item.resolvedName
    var note = ""
    var id = item.id ?: "idx-$index"
    var parentId: String? = null

    val noteSplit = name.split("|NOTE|")
    if (noteSplit.size > 1) {
        note = noteSplit[1]
        name = noteSplit[0]
    }

    val parentSplit = name.split("|PARENT|")
    if (parentSplit.size > 1) {
        parentId = parentSplit[1]
        name = parentSplit[0]
    }

    val idSplit = name.split("|ID|")
    if (idSplit.size > 1) {
        id = idSplit[1]
        name = idSplit[0]
    }

    return ParsedOrderItem(item, name, note, id, parentId)
}

private fun groupOrderItems(items: List<OrderItemDto>): List<Pair<ParsedOrderItem, List<ParsedOrderItem>>> {
    val parsed = items.mapIndexed { index, item -> parseOrderItemName(item, index) }
    val rootItems = parsed.filter { it.parentId == null }.toMutableList()
    val validRootIds = rootItems.map { it.itemId }.toSet()

    val childrenMap = mutableMapOf<String, MutableList<ParsedOrderItem>>()
    parsed.filter { it.parentId != null }.forEach { child ->
        if (!validRootIds.contains(child.parentId)) {
            rootItems.add(child)
        } else {
            childrenMap.getOrPut(child.parentId!!) { mutableListOf() }.add(child)
        }
    }

    return rootItems.map { root -> root to (childrenMap[root.itemId] ?: emptyList()) }
}

@Composable
fun OrderHistoryContent(viewModel: OrderHistoryViewModel) {
    val ordersHistory by viewModel.ordersHistory.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedFilter by viewModel.selectedPaymentFilter.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val dateFilter by viewModel.dateFilter.collectAsState()
    val paymentMethodFilter by viewModel.selectedPaymentMethodFilter.collectAsState()
    val channelFilter by viewModel.selectedChannelFilter.collectAsState()
    val customStart by viewModel.customStartDate.collectAsState()
    val customEnd by viewModel.customEndDate.collectAsState()

    var previewImageUrl by remember { mutableStateOf<String?>(null) }

    val filteredOrders = ordersHistory.filter { order ->
        searchQuery.isEmpty() || order.orderNumber.toString().contains(searchQuery) || (order.customerName?.contains(searchQuery, ignoreCase = true) == true)
    }

    val summary by viewModel.revenueSummary.collectAsState()
    val isSummaryFromCache by viewModel.isSummaryFromLocalCache.collectAsState()
    val isOnline by com.sukashawarma.pos.data.remote.NetworkMonitor.isOnline.collectAsState()

    fun grossOf(method: String) =
        summary.byPayment.firstOrNull { it.paymentMethod.equals(method, true) }?.gross ?: 0.0

    Column(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isNarrow = maxWidth < 760.dp
            if (isNarrow) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SummaryCard("TUNAI / CASH", "Rp ${String.format("%,.0f", grossOf("cash"))}", Color(0xFF10B981), Color(0xFF10B981), Color.White, Modifier.weight(1f))
                        SummaryCard("QRIS", "Rp ${String.format("%,.0f", grossOf("qris"))}", Color(0xFF3B82F6), Color(0xFF3B82F6), Color.White, Modifier.weight(1f))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SummaryCard("DEBIT / CARD", "Rp ${String.format("%,.0f", grossOf("card"))}", Color(0xFF8B5CF6), Color(0xFF8B5CF6), Color.White, Modifier.weight(1f))
                        SummaryCard("OMZET KOTOR", "Rp ${String.format("%,.0f", summary.gross)}", Color.White, Color.White, ShawarmaOrange, Modifier.weight(1f))
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SummaryCard("TUNAI / CASH", "Rp ${String.format("%,.0f", grossOf("cash"))}", Color(0xFF10B981), Color(0xFF10B981), Color.White, Modifier.weight(1f))
                    SummaryCard("QRIS", "Rp ${String.format("%,.0f", grossOf("qris"))}", Color(0xFF3B82F6), Color(0xFF3B82F6), Color.White, Modifier.weight(1f))
                    SummaryCard("DEBIT / CARD", "Rp ${String.format("%,.0f", grossOf("card"))}", Color(0xFF8B5CF6), Color(0xFF8B5CF6), Color.White, Modifier.weight(1f))
                    SummaryCard("OMZET KOTOR", "Rp ${String.format("%,.0f", summary.gross)}", Color.White, Color.White, ShawarmaOrange, Modifier.weight(1f))
                }
            }
        }

        if (isSummaryFromCache && !isOnline) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Data offline — omzet dihitung dari penyimpanan lokal dan mungkin belum lengkap.",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF92400E),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFEF3C7), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isFiltersNarrow = maxWidth < 840.dp
            val statusPills: @Composable () -> Unit = {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .background(Color(0xFFF3F4F6), RoundedCornerShape(20.dp))
                        .padding(4.dp)
                ) {
                    // "Diproses" menggantikan label "Menunggu" yang lama: tidak ada
                    // pesanan berstatus `pending` di database, yang ada `preparing`.
                    items(listOf(
                        "Semua Pesanan" to OrderStatusFilter.ALL,
                        "Diproses" to OrderStatusFilter.WAITING,
                        "Selesai" to OrderStatusFilter.DONE,
                        "Dibatalkan" to OrderStatusFilter.CANCELLED
                    )) { (filter, filterKey) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (selectedFilter == filterKey) Color(0xFF1F2937) else Color.Transparent)
                                .clickable { viewModel.selectedPaymentFilter.value = filterKey }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(filter, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (selectedFilter == filterKey) Color.White else Color(0xFF4B5563))
                        }
                    }
                }
            }

            val dropdowns: @Composable () -> Unit = {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        FilterDropdown(labelFor(dateFilterOptions, dateFilter), dateFilterOptions) { picked ->
                            viewModel.dateFilter.value = picked
                            if (picked != "custom") {
                                viewModel.customStartDate.value = null
                                viewModel.customEndDate.value = null
                            }
                        }
                    }
                    item {
                        FilterDropdown(labelFor(channelFilterOptions, channelFilter), channelFilterOptions) { picked ->
                            viewModel.selectedChannelFilter.value = picked
                        }
                    }
                    item {
                        FilterDropdown(labelFor(paymentMethodOptions, paymentMethodFilter), paymentMethodOptions) { picked ->
                            viewModel.selectedPaymentMethodFilter.value = picked
                        }
                    }
                }
            }

            if (isFiltersNarrow) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    statusPills()
                    dropdowns()
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    statusPills()
                    dropdowns()
                }
            }
        }

        if (dateFilter == "custom") {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .background(Color(0xFFF9FAFB), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DatePickField("Mulai Tanggal", customStart) { viewModel.customStartDate.value = it }
                Text("-", color = Color(0xFF9CA3AF))
                DatePickField("Sampai Tanggal", customEnd) { viewModel.customEndDate.value = it }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ShawarmaOrange)
            }
        } else if (filteredOrders.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                com.sukashawarma.pos.presentation.components.EmptyState(
                    title = "Belum ada transaksi",
                    subtitle = "Pesanan pada rentang & filter ini akan muncul di sini secara otomatis.",
                    icon = Icons.Default.Assignment
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredOrders, key = { it.id }) { order ->
                    OrderRow(
                        order = order,
                        onPreviewImage = { previewImageUrl = it },
                        onMarkCompleted = { viewModel.updateOrderStatus(order.id, "completed") }
                    )
                }
            }
        }
    }

    if (previewImageUrl != null) {
        Dialog(onDismissRequest = { previewImageUrl = null }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { previewImageUrl = null },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(previewImageUrl)
                            // Decode satu gambar yang cukup tajam untuk dialog dan
                            // cache hasilnya; hindari decode foto asli 12 MP.
                            .size(1600, 1600)
                            .crossfade(false)
                            .build(),
                        contentDescription = "Bukti Pembayaran",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp, max = 500.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { previewImageUrl = null },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Tutup")
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderRow(
    order: OrderDto,
    onPreviewImage: (String) -> Unit,
    onMarkCompleted: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val isCancelled = OrderStatusFilter.isCancelled(order)
    val statusText = when {
        isCancelled -> "Dibatalkan"
        order.status.equals("completed", true) -> "Selesai"
        order.status.lowercase() in listOf("pending", "preparing", "ready") -> "Diproses"
        else -> order.status
    }
    val statusColor = when {
        isCancelled -> Color(0xFFEF4444)
        order.status.equals("completed", true) -> Color(0xFF10B981)
        order.status.lowercase() in listOf("pending", "preparing", "ready") -> ShawarmaOrange
        else -> Color.Gray
    }
    val badgeColor = when (order.paymentMethod?.uppercase()) {
        "CASH" -> Color(0xFF10B981)
        "QRIS" -> Color(0xFF3B82F6)
        else -> Color(0xFF8B5CF6)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
        shape = RoundedCornerShape(10.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, CreamBorder)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isNarrow = maxWidth < 720.dp

                if (isNarrow) {
                    // Portrait tablet / compact structured card layout
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Top line: #OrderNo, Status, Channel, Sync, and Date/Time
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFF3F4F6)
                                ) {
                                    Text(
                                        "#${order.orderNumber}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color(0xFF374151),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Text(statusText, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                com.sukashawarma.pos.presentation.components.OrderSourceBadge(source = order.source, channel = order.channel)
                                if (order.isSyncedFromOffline == true) {
                                    Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF10B981).copy(alpha = 0.12f)) {
                                        Text("SYNC", color = Color(0xFF10B981), fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                    }
                                }
                            }

                            val dateStr = JakartaTime.dateTimeStringOf(order.createdAt)
                            Text(dateStr, color = Color(0xFF9CA3AF), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }

                        // Middle line: Customer name • item count • Cashier
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    order.customerName ?: "Pelanggan Walk-in",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF111827),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("${order.orderItems?.size ?: 0} item", color = Color(0xFF6B7280), fontSize = 12.sp)
                                    if (order.cashierName != null) {
                                        Text("•", color = Color(0xFFD1D5DB), fontSize = 12.sp)
                                        Text("Kasir: ${order.cashierName}", color = Color(0xFF6B7280), fontSize = 12.sp)
                                    }
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Rp ${String.format("%,.0f", order.totalAmount)}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color(0xFF111827)
                                    )
                                    val subsidy = order.promoSubsidy?.takeIf { it > 0.0 }
                                    if (subsidy != null) {
                                        Text("-Rp ${String.format("%,.0f", subsidy)}", color = Color(0xFFEF4444), fontSize = 9.sp, fontWeight = FontWeight.Medium)
                                    }
                                    val discount = order.discountAmount?.takeIf { it > 0.0 }
                                    if (discount != null) {
                                        Text("-Rp ${String.format("%,.0f", discount)}", color = Color(0xFFF59E0B), fontSize = 9.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                                Surface(shape = RoundedCornerShape(12.dp), color = badgeColor.copy(alpha = 0.1f)) {
                                    Text(
                                        order.paymentMethod?.uppercase() ?: "N/A",
                                        color = badgeColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Icon(
                                    if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = Color(0xFF9CA3AF),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        if (order.status.equals("cancelled", true) && order.cancellationUserName != null) {
                            Text(
                                "Dibatalkan oleh: ${order.cancellationUserName}",
                                color = Color(0xFFEF4444),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        } else if (!order.status.equals("cancelled", true) && order.cancellationStatus == "pending_approval") {
                            Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFFEF9C3), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A))) {
                                Text(
                                    "MENUNGGU PERSETUJUAN BATAL" + if (order.cancellationUserName != null) " (Oleh: ${order.cancellationUserName})" else "",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFA16207),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                } else {
                    // Landscape wide table-like row (original)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("#${order.orderNumber}", fontWeight = FontWeight.Bold, color = Color(0xFF6B7280), modifier = Modifier.width(40.dp))

                        Column(modifier = Modifier.weight(2.2f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(statusText, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(6.dp))
                                com.sukashawarma.pos.presentation.components.OrderSourceBadge(source = order.source, channel = order.channel)
                                if (order.isSyncedFromOffline == true) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF10B981).copy(alpha = 0.12f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
                                            Icon(Icons.Default.Refresh, contentDescription = "Sync", tint = Color(0xFF10B981), modifier = Modifier.size(10.dp))
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text(
                                                "SYNC",
                                                color = Color(0xFF10B981),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                            Text(order.customerName ?: "Pelanggan Walk-in", fontWeight = FontWeight.Bold)

                            if (order.status.equals("cancelled", true) && order.cancellationUserName != null) {
                                Text(
                                    "Dibatalkan oleh: ${order.cancellationUserName}",
                                    color = Color(0xFFEF4444),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            } else if (!order.status.equals("cancelled", true) && order.cancellationStatus == "pending_approval") {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFFEF9C3), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A))) {
                                        Text(
                                            "MENUNGGU PERSETUJUAN BATAL",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFA16207),
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                    if (order.cancellationUserName != null) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("(Oleh: ${order.cancellationUserName})", fontSize = 9.sp, color = Color(0xFFA16207))
                                    }
                                }
                            }
                        }

                        Column(modifier = Modifier.weight(2f)) {
                            Text(JakartaTime.dateStringOf(order.createdAt), color = Color(0xFF6B7280), fontSize = 12.sp)
                            Text(JakartaTime.timeStringOf(order.createdAt), color = Color(0xFF9CA3AF), fontSize = 12.sp)
                        }

                        Column(modifier = Modifier.weight(1.3f)) {
                            Text("${order.orderItems?.size ?: 0} item", color = Color(0xFF6B7280), fontSize = 13.sp)
                        }

                        Column(modifier = Modifier.weight(1.5f)) {
                            if (order.cashierName != null) {
                                Text("Kasir", color = Color(0xFF9CA3AF), fontSize = 12.sp)
                                Text(order.cashierName, color = Color(0xFF374151), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }

                        Row(modifier = Modifier.weight(2.5f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Rp ${String.format("%,.0f", order.totalAmount)}",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF111827)
                                )
                                val subsidy = order.promoSubsidy?.takeIf { it > 0.0 }
                                if (subsidy != null) {
                                    Text("-Rp ${String.format("%,.0f", subsidy)}", color = Color(0xFFEF4444), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                }
                                val discount = order.discountAmount?.takeIf { it > 0.0 }
                                if (discount != null) {
                                    Text("-Rp ${String.format("%,.0f", discount)}", color = Color(0xFFF59E0B), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
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
                }
            if (isExpanded && order.orderItems != null && order.orderItems.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF9FAFB))
                        .padding(16.dp)
                ) {
                    val rewardItem = order.orderItems.firstOrNull { it.isPromoReward }
                    if (rewardItem != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFDCFCE7),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF86EFAC)),
                            modifier = Modifier.padding(bottom = 10.dp)
                        ) {
                            Text(
                                "BUY ${rewardItem.promoBuyQuantity ?: 1} GET ${rewardItem.promoGetQuantity ?: 1}",
                                color = Color(0xFF166534),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    val groupedItems = remember(order.orderItems) { groupOrderItems(order.orderItems) }
                    groupedItems.forEach { (root, children) ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row {
                                    Text("${root.raw.quantity}x", fontWeight = FontWeight.Bold, color = Color(0xFF374151), modifier = Modifier.width(30.dp))
                                    Text(
                                        if (root.raw.isPromoReward) "Gratis · ${root.name}" else root.name,
                                        color = if (root.raw.isPromoReward) Color(0xFF15803D) else Color(0xFF4B5563),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Text(
                                    if (root.raw.isPromoReward) "Rp 0" else "Rp ${String.format("%,.0f", root.raw.subtotal)}",
                                    color = if (root.raw.isPromoReward) Color(0xFF15803D) else Color(0xFF374151),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            if (root.note.isNotBlank()) {
                                Surface(
                                    modifier = Modifier.padding(start = 30.dp, top = 4.dp, bottom = 2.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFFFFBEB),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A))
                                ) {
                                    Text(
                                        "\"${root.note}\"",
                                        color = Color(0xFFB45309),
                                        fontSize = 11.sp,
                                        fontStyle = FontStyle.Italic,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            children.forEach { child ->
                                Column(modifier = Modifier.padding(start = 30.dp, top = 4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("${child.raw.quantity}x", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B7280))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFFEF3C7)) {
                                                Text("EXTRA", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309), modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(child.name, color = Color(0xFF4B5563), fontSize = 13.sp)
                                        }
                                        Text("Rp ${String.format("%,.0f", child.raw.subtotal)}", color = Color(0xFF6B7280), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    }
                                    if (child.note.isNotBlank()) {
                                        Surface(
                                            modifier = Modifier.padding(start = 26.dp, top = 3.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFFFFBEB),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A))
                                        ) {
                                            Text(
                                                "\"${child.note}\"",
                                                color = Color(0xFFB45309),
                                                fontSize = 10.sp,
                                                fontStyle = FontStyle.Italic,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // "Potongan App" (promo_subsidy) SENGAJA tidak ditampilkan di sini — kasir
                    // hanya perlu tahu Total Akhir yang ditagih; rincian subsidi promo app
                    // cukup di layar Laporan (lihat ReportsScreen.kt).
                    val discount = order.discountAmount?.takeIf { it > 0.0 }
                    if (discount != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        androidx.compose.material3.HorizontalDivider(color = Color(0xFFE5E7EB), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Diskon", color = Color(0xFF6B7280), fontSize = 13.sp)
                            Text("- Rp ${String.format("%,.0f", discount)}", color = Color(0xFFF59E0B), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Akhir", color = Color(0xFF111827), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Rp ${String.format("%,.0f", order.totalAmount)}", color = Color(0xFF111827), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (!order.notes.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFFFBEB), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A))) {
                            Text(
                                buildString {
                                    append("Catatan: ")
                                    append(order.notes)
                                },
                                color = Color(0xFF92400E),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    val cancelReason = order.cancellationReason ?: order.voidReason
                    if (order.status.equals("cancelled", true) && !cancelReason.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFEF2F2), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFECACA))) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Alasan Batal: $cancelReason", color = Color(0xFF991B1B), fontSize = 13.sp)
                                if (order.cancellationUserName != null) {
                                    Text("Diajukan oleh: ${order.cancellationUserName}", color = Color(0xFFB91C1C), fontSize = 11.sp)
                                }
                            }
                        }
                    } else if (!order.status.equals("cancelled", true) && order.cancellationStatus == "pending_approval") {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFEFCE8), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A))) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Permintaan Batal (Menunggu Persetujuan)", color = Color(0xFF854D0E), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                if (!cancelReason.isNullOrBlank()) {
                                    Text("Alasan: $cancelReason", color = Color(0xFF854D0E), fontSize = 12.sp)
                                }
                                if (order.cancellationUserName != null) {
                                    Text("Diajukan oleh: ${order.cancellationUserName}", color = Color(0xFFA16207), fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    if (order.paymentMethod.equals("qris", true) && !order.paymentProofUrl.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFEFF6FF), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE))) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.QrCode, contentDescription = null, tint = Color(0xFF1D4ED8), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Bukti Transfer QRIS", color = Color(0xFF1D4ED8), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Button(
                                    onClick = { onPreviewImage(order.paymentProofUrl) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Lihat Foto", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (order.status.equals("pending", true)) {
                            Button(
                                onClick = onMarkCompleted,
                                colors = ButtonDefaults.buttonColors(containerColor = ShawarmaOrange),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Tandai Selesai", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

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

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isNarrow = maxWidth < 860.dp

        val filterSummaryBlock: @Composable (Modifier) -> Unit = { colModifier ->
            Column(
                modifier = colModifier,
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

                if (isNarrow) {
                    // Summary Cards horizontal row in narrow / portrait tablet
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Summary Card 1: Target Tercapai
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            color = ShawarmaOrange,
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = RoundedCornerShape(8.dp), color = Color.White.copy(alpha = 0.2f)) {
                                        Icon(Icons.Default.Print, contentDescription = null, tint = Color.White, modifier = Modifier.padding(6.dp).size(16.dp))
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Target Tercapai", color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(if (isLoading) "-" else "$totalDaysReached", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Text("Hari di bulan ini", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        // Summary Card 2: Estimasi Bonus Per Orang
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White,
                            border = androidx.compose.foundation.BorderStroke(1.dp, CreamBorder)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = RoundedCornerShape(8.dp), color = StatusCompleted.copy(alpha = 0.2f)) {
                                        Icon(Icons.Default.MonetizationOn, contentDescription = null, tint = StatusCompleted, modifier = Modifier.padding(6.dp).size(16.dp))
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Bonus / Orang", color = TextDarkSecondary, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(if (isLoading) "-" else "Rp ${String.format("%,.0f", bonusPerPerson)}", color = TextDarkPrimary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Dibagi $displayCrewCount orang", color = TextDarkMuted, style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        // Summary Card 3: Total Terkumpul
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White,
                            border = androidx.compose.foundation.BorderStroke(1.dp, CreamBorder)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFE0F2FE)) {
                                        Icon(Icons.Default.MonetizationOn, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.padding(6.dp).size(16.dp))
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Total Terkumpul", color = TextDarkSecondary, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(if (isLoading) "-" else "Rp ${String.format("%,.0f", totalBonusPool)}", color = TextDarkPrimary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Total sebelum dibagi", color = TextDarkMuted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                } else {
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
                }

                // Bonus Simulation Card
                if (dailyBreakdown.isNotEmpty()) {
                    val lastDay = dailyBreakdown.last()
                    BonusSimulationCard(
                        targetAmount = lastDay.targetAmount,
                        perItemBonus = lastDay.perItemBonus,
                        crewCount = displayCrewCount,
                        onCrewCountChange = { viewModel.simulatedCrewCount.value = it }
                    )
                }
            }
        }

        val tableBlock: @Composable (Modifier) -> Unit = { surfModifier ->
            Surface(
                modifier = surfModifier,
                shape = RoundedCornerShape(10.dp),
                color = CreamBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, CreamBorder)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Rincian Penjualan per Hari", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Lihat rekam jejak pencapaian target harian outletmu", style = MaterialTheme.typography.bodySmall, color = TextDarkMuted)
                    }

                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = ShawarmaOrange)
                        }
                    } else if (dailyBreakdown.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            com.sukashawarma.pos.presentation.components.EmptyState(
                                title = "Belum ada data untuk bulan ini",
                                icon = Icons.Default.CalendarMonth
                            )
                        }
                    } else {
                        // Table header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF3F4F6))
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text("Tanggal", modifier = Modifier.weight(1.2f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDarkMuted)
                            Text("Total Penjualan", modifier = Modifier.weight(1.3f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDarkMuted)
                            Text("Target Hari Ini", modifier = Modifier.weight(1.3f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDarkMuted)
                            Text("Bonus", modifier = Modifier.weight(1.2f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDarkMuted)
                            Text("Status", modifier = Modifier.weight(0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDarkMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }

                        LazyColumn(modifier = if (isNarrow) Modifier.heightIn(max = 420.dp) else Modifier.fillMaxSize()) {
                            items(dailyBreakdown.reversed()) { day ->
                                val dateStr = day.date ?: ""
                                val isToday = if (dateStr.isNotEmpty()) java.time.LocalDate.parse(dateStr.split("T")[0]) == java.time.LocalDate.now() else false

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (isToday) ShawarmaOrange.copy(alpha = 0.08f) else Color.White)
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(modifier = Modifier.weight(1.2f), verticalAlignment = Alignment.CenterVertically) {
                                        Text((day.date ?: "").split("T")[0], fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isToday) ShawarmaOrange else TextDarkPrimary)
                                        if (isToday) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Surface(color = ShawarmaOrange.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                                Text("HARI INI", fontSize = 8.sp, color = ShawarmaOrange, modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp), fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    Text("Rp ${String.format("%,.0f", day.dailySales)}", modifier = Modifier.weight(1.3f), fontSize = 12.sp, color = TextDarkPrimary, fontWeight = FontWeight.Medium)
                                    Text("Rp ${String.format("%,.0f", day.targetAmount)}", modifier = Modifier.weight(1.3f), fontSize = 12.sp, color = TextDarkSecondary)
                                    Text(
                                        if (day.isReached) "+ Rp ${String.format("%,.0f", day.bonusAmount)}" else "-",
                                        modifier = Modifier.weight(1.2f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (day.isReached) StatusCompleted else TextDarkMuted
                                    )
                                    Box(modifier = Modifier.weight(0.8f), contentAlignment = Alignment.Center) {
                                        if (day.isReached) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = "Tercapai", tint = StatusCompleted, modifier = Modifier.size(18.dp))
                                        } else {
                                            Icon(Icons.Default.Cancel, contentDescription = "Tidak Tercapai", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                                androidx.compose.material3.HorizontalDivider(color = CreamBorder, thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }
        }

        if (isNarrow) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { filterSummaryBlock(Modifier.fillMaxWidth()) }
                item { tableBlock(Modifier.fillMaxWidth()) }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                filterSummaryBlock(Modifier.weight(0.35f))
                tableBlock(Modifier.weight(0.65f).fillMaxHeight())
            }
        }
    }
}

@Composable
fun BonusSimulationCard(
    targetAmount: Double,
    perItemBonus: Double,
    crewCount: Int,
    onCrewCountChange: (Int) -> Unit
) {
    var isTargetReached by remember { mutableStateOf(true) }
    var additionalItems by remember { mutableStateOf(0f) }
    val maxItems = 50

    val additionalItemsInt = additionalItems.toInt()
    val totalBonus = if (isTargetReached) additionalItemsInt * perItemBonus else 0.0
    val bonusPerPerson = if (crewCount > 0) floor(totalBonus / crewCount) else 0.0

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = CreamBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, CreamBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Simulasi Bonus Tambahan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Hitung bonus ekstra per item terjual di atas target", style = MaterialTheme.typography.bodySmall, color = TextDarkMuted)
            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Jumlah Crew Hadir:", color = TextDarkSecondary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { if (crewCount > 1) onCrewCountChange(crewCount - 1) },
                        colors = ButtonDefaults.buttonColors(containerColor = CreamBorder),
                        modifier = Modifier.size(36.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("-", color = TextDarkPrimary) }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
                        Icon(Icons.Default.Groups, contentDescription = null, tint = Color(0xFF3B82F6), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("$crewCount", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { onCrewCountChange(crewCount + 1) },
                        colors = ButtonDefaults.buttonColors(containerColor = CreamBorder),
                        modifier = Modifier.size(36.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("+", color = TextDarkPrimary) }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Target Harian Tercapai?", color = TextDarkSecondary, fontWeight = FontWeight.Medium)
                Switch(checked = isTargetReached, onCheckedChange = { isTargetReached = it })
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Item Tambahan Terjual", color = TextDarkSecondary, fontSize = 13.sp)
                Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFDBEAFE)) {
                    Text("$additionalItemsInt Item", color = Color(0xFF1D4ED8), fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
            Slider(
                value = additionalItems,
                onValueChange = { additionalItems = it },
                valueRange = 0f..maxItems.toFloat(),
                steps = maxItems - 1,
                enabled = isTargetReached
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("0 Item", fontSize = 11.sp, color = TextDarkMuted)
                Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFFFFBEB)) {
                    Text("Target Rp ${String.format("%,.0f", targetAmount)}", fontSize = 11.sp, color = Color(0xFFB45309), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Text("$maxItems Item", fontSize = 11.sp, color = TextDarkMuted)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, CreamBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Perkiraan Bonus", color = TextDarkMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Rp ${String.format("%,.0f", bonusPerPerson)}",
                        color = if (isTargetReached) TextDarkPrimary else TextDarkMuted,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (isTargetReached) "Bonus Rp ${String.format("%,.0f", perItemBonus)} / item ekstra ($additionalItemsInt item) dibagi $crewCount orang"
                        else "Target harian belum tercapai",
                        color = TextDarkMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
