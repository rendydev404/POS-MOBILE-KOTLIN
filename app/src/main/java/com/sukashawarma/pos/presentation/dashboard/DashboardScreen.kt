package com.sukashawarma.pos.presentation.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukashawarma.pos.domain.model.Order
import com.sukashawarma.pos.domain.model.OrderStatus
import com.sukashawarma.pos.presentation.components.OrderCard
import com.sukashawarma.pos.presentation.theme.*

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.TabRow
import androidx.compose.material3.Tab
import kotlinx.coroutines.launch

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    printerViewModel: com.sukashawarma.pos.presentation.printer.BluetoothPrinterViewModel,
    windowSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Expanded,
    newOrderButtonColorHex: String = "#E67E22",
    newOrderButtonLabel: String = "Pesanan Baru",
    onNewOrderClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val pendingOrders by viewModel.pendingOrders.collectAsState()
    val preparingOrders by viewModel.preparingOrders.collectAsState()
    val updatingOrderIds by viewModel.updatingOrderIds.collectAsState()
    val highlightedOrderId by viewModel.highlightedOrderId.collectAsState()
    val completedOrders by viewModel.completedOrders.collectAsState()
    
    val printerConnectionStatus by printerViewModel.connectionStatus.collectAsState()
    val connectedDeviceName by printerViewModel.connectedDeviceName.collectAsState()
    var showPrinterDialog by remember { mutableStateOf(false) }

    if (showPrinterDialog) {
        com.sukashawarma.pos.presentation.printer.BluetoothPrinterDialog(
            viewModel = printerViewModel,
            onDismiss = { showPrinterDialog = false }
        )
    }

    val currentOutletName by viewModel.currentOutletName.collectAsState()
    val omzetKotorHariIni by viewModel.omzetKotorHariIni.collectAsState()
    val criticalStockAlerts by viewModel.criticalStockAlerts.collectAsState()

    var activeSourceFilter by remember { mutableStateOf("Semua") }
    var preparingSubTab by remember { mutableStateOf("Antrean") }
    var boardNow by remember { mutableStateOf(System.currentTimeMillis()) }
    var completedSearchQuery by remember { mutableStateOf("") }
    val printStatusMessage by viewModel.printStatusMessage.collectAsState()

    val pagerState = rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val newOrderButtonColor = remember(newOrderButtonColorHex) {
        runCatching { Color(android.graphics.Color.parseColor(newOrderButtonColorHex)) }
            .getOrDefault(TwViolet600)
    }

    LaunchedEffect(printStatusMessage) {
        printStatusMessage?.let { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.statusUpdateErrors.collect { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    val currentOutletId by viewModel.currentOutletId.collectAsState()

    // 15s polling has been removed in favor of Supabase Realtime via GlobalEventBus

    // Sama seperti web: waktu berjalan harus memindahkan pesanan Terjadwal ke
    // Antrean secara otomatis saat release_time tercapai, tanpa menunggu refetch.
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            boardNow = System.currentTimeMillis()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CreamBackground)
    ) {

        // 2. Pita stok kritis. Menghilang sendiri kalau tidak ada bahan kritis —
        // dulu selalu tampil walau kosong. Ditekan → buka papan stok outlet.
        com.sukashawarma.pos.presentation.components.StockMarquee(
            alerts = criticalStockAlerts
        )

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
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
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
                                text = "Anda berada di outlet: $currentOutletName",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = ShawarmaOrangeDark,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Warna/label berasal dari runtime config dan dapat berubah
                    // realtime tanpa mengunduh atau memasang APK baru.
                    Button(
                        onClick = onNewOrderClick,
                        colors = ButtonDefaults.buttonColors(containerColor = newOrderButtonColor),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Icon(Icons.Default.AddCircle, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(newOrderButtonLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
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
                                Text("OMZET KOTOR HARI INI", style = MaterialTheme.typography.bodySmall, fontSize = 9.sp, color = TextDarkMuted, fontWeight = FontWeight.Bold)
                                Text("Rp ${String.format("%,.0f", omzetKotorHariIni)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextDarkPrimary)
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
                    color = if (printerConnectionStatus == com.sukashawarma.pos.presentation.printer.ConnectionStatus.CONNECTED) TwEmerald50 else CreamSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (printerConnectionStatus == com.sukashawarma.pos.presentation.printer.ConnectionStatus.CONNECTED) TwEmerald100 else CreamBorder),
                    modifier = Modifier.clickable { showPrinterDialog = true }.wrapContentWidth().padding(end = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val icon = Icons.Default.Print
                        val tint = if (printerConnectionStatus == com.sukashawarma.pos.presentation.printer.ConnectionStatus.CONNECTED) TwEmerald600 else TextDarkMuted
                        val text = if (printerConnectionStatus == com.sukashawarma.pos.presentation.printer.ConnectionStatus.CONNECTED) "Terhubung" else "Belum Terhubung"
                        
                        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = text, 
                            style = MaterialTheme.typography.bodySmall, 
                            fontSize = 11.sp, 
                            color = tint, 
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
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

            // 5. Adaptive Order Board (Row for Expanded, Pager for Compact/Medium)
            
            val filteredPending = remember(pendingOrders, activeSourceFilter) {
                pendingOrders.filter { order ->
                    when (activeSourceFilter) {
                        "🌐 Online" -> order.source == com.sukashawarma.pos.domain.model.OrderSource.ONLINE
                        "📱 Offline" -> order.source == com.sukashawarma.pos.domain.model.OrderSource.POS || order.source == com.sukashawarma.pos.domain.model.OrderSource.KIOSK
                        else -> true
                    }
                }
            }
            
            val filteredPreparing = remember(preparingOrders, activeSourceFilter) {
                preparingOrders.filter { order ->
                    when (activeSourceFilter) {
                        "🌐 Online" -> order.source == com.sukashawarma.pos.domain.model.OrderSource.ONLINE
                        "📱 Offline" -> order.source == com.sukashawarma.pos.domain.model.OrderSource.POS || order.source == com.sukashawarma.pos.domain.model.OrderSource.KIOSK
                        else -> true
                    }
                }
            }

            val (queuedPreparing, scheduledPreparing) = remember(filteredPreparing, boardNow) {
                com.sukashawarma.pos.domain.usecase.PreparingOrderClassifier.split(filteredPreparing, boardNow)
            }
            val visiblePreparing = if (preparingSubTab == "Terjadwal") scheduledPreparing else queuedPreparing
            
            val filteredCompleted = remember(completedOrders, activeSourceFilter) {
                completedOrders.filter { order ->
                    when (activeSourceFilter) {
                        "🌐 Online" -> order.source == com.sukashawarma.pos.domain.model.OrderSource.ONLINE
                        "📱 Offline" -> order.source == com.sukashawarma.pos.domain.model.OrderSource.POS || order.source == com.sukashawarma.pos.domain.model.OrderSource.KIOSK
                        else -> true
                    }
                }
            }

            val column1: @Composable () -> Unit = {
                BoardColumn(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    title = "Menunggu Bayar",
                    icon = Icons.Default.Schedule,
                    badgeCount = filteredPending.size,
                    emptyMessage = "Tidak ada pesanan tertunda\nPesanan baru akan muncul otomatis di sini."
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        filteredPending.forEach { order ->
                            key(order.id) { OrderCard(
                                order = order,
                                isStatusUpdating = order.id in updatingOrderIds,
                                isHighlighted = order.id == highlightedOrderId,
                                onStatusChange = { o, newStatus -> viewModel.updateOrderStatus(o, newStatus) },
                                onCancelOrder = { o, reason -> 
                                    viewModel.requestCancellation(
                                        order = o, 
                                        reason = reason, 
                                        onSuccess = {
                                            android.widget.Toast.makeText(context, "Permintaan pembatalan berhasil dikirim ke Area Manager.", android.widget.Toast.LENGTH_SHORT).show()
                                        },
                                        onError = { errorMsg ->
                                            android.widget.Toast.makeText(context, errorMsg, android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                },
                                onPrintKitchen = { o -> 
                                    viewModel.printReceipt(context, o, isKitchen = true) {
                                        viewModel.markKitchenReceiptPrinted(o)
                                    }
                                },
                                onPrintCustomer = { o -> 
                                    viewModel.printReceipt(context, o, isKitchen = false) {
                                        viewModel.markCustomerReceiptPrinted(o)
                                    }
                                },
                                onReprint = { o -> viewModel.printReceipt(context, o, isKitchen = false) },
                                onAcceptOrder = { o ->
                                    viewModel.updateOrderStatus(o, com.sukashawarma.pos.domain.model.OrderStatus.PREPARING)
                                    viewModel.printReceipt(context, o, isKitchen = true) {
                                        viewModel.markKitchenReceiptPrinted(o)
                                    }
                                }
                            ) }
                        }
                    }
                }
            }

            val column2: @Composable () -> Unit = {
                BoardColumn(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    title = "Sedang Diproses",
                    icon = Icons.Default.Restaurant,
                    badgeCount = filteredPreparing.size,
                    hasSubTabs = true,
                    subTabState = preparingSubTab,
                    onSubTabChange = { preparingSubTab = it },
                    subTabCounts = mapOf(
                        "Antrean" to queuedPreparing.size,
                        "Terjadwal" to scheduledPreparing.size
                    ),
                    contentCount = visiblePreparing.size,
                    emptyMessage = if (preparingSubTab == "Terjadwal") {
                        "Tidak ada pesanan terjadwal\nPesanan pre-order akan ditahan di sini sebelum masuk antrean."
                    } else {
                        "Tidak ada antrean masak\nDapur sedang santai, pesanan aktif akan muncul di sini."
                    }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        visiblePreparing.forEach { order ->
                            key(order.id) { OrderCard(
                                order = order,
                                isStatusUpdating = order.id in updatingOrderIds,
                                isHighlighted = order.id == highlightedOrderId,
                                onStatusChange = { o, newStatus -> viewModel.updateOrderStatus(o, newStatus) },
                                onCancelOrder = { o, reason -> 
                                    viewModel.requestCancellation(
                                        order = o, 
                                        reason = reason, 
                                        onSuccess = {
                                            android.widget.Toast.makeText(context, "Permintaan pembatalan berhasil dikirim ke Area Manager.", android.widget.Toast.LENGTH_SHORT).show()
                                        },
                                        onError = { errorMsg ->
                                            android.widget.Toast.makeText(context, errorMsg, android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                },
                                onPrintKitchen = { o -> 
                                    viewModel.printReceipt(context, o, isKitchen = true) {
                                        viewModel.markKitchenReceiptPrinted(o)
                                    }
                                },
                                onPrintCustomer = { o -> 
                                    viewModel.printReceipt(context, o, isKitchen = false) {
                                        viewModel.markCustomerReceiptPrinted(o)
                                    }
                                },
                                onReprint = { o -> viewModel.printReceipt(context, o, isKitchen = false) }
                            ) }
                        }
                    }
                }
            }

            val column3: @Composable () -> Unit = {
                BoardColumn(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    title = "Selesai / Lunas",
                    icon = Icons.Default.CheckCircle,
                    badgeCount = filteredCompleted.size,
                    hasSearchBar = true,
                    searchQuery = completedSearchQuery,
                    onSearchQueryChange = { completedSearchQuery = it },
                    emptyMessage = "Belum ada pesanan selesai hari ini"
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val itemsToShow = filteredCompleted.filter { completedSearchQuery.isEmpty() || it.orderNumber.toString().contains(completedSearchQuery) }
                        itemsToShow.forEach { order ->
                            key(order.id) { OrderCard(
                                order = order,
                                isStatusUpdating = order.id in updatingOrderIds,
                                isHighlighted = order.id == highlightedOrderId,
                                onStatusChange = { o, newStatus -> viewModel.updateOrderStatus(o, newStatus) },
                                onCancelOrder = { o, reason -> 
                                    viewModel.requestCancellation(
                                        order = o, 
                                        reason = reason, 
                                        onSuccess = {
                                            android.widget.Toast.makeText(context, "Permintaan pembatalan berhasil dikirim ke Area Manager.", android.widget.Toast.LENGTH_SHORT).show()
                                        },
                                        onError = { errorMsg ->
                                            android.widget.Toast.makeText(context, errorMsg, android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                },
                                onPrintKitchen = { o -> 
                                    viewModel.printReceipt(context, o, isKitchen = true) {
                                        viewModel.markKitchenReceiptPrinted(o)
                                    }
                                },
                                onPrintCustomer = { o -> 
                                    viewModel.printReceipt(context, o, isKitchen = false) {
                                        viewModel.markCustomerReceiptPrinted(o)
                                    }
                                },
                                onReprint = { o -> viewModel.printReceipt(context, o, isKitchen = false) }
                            ) }
                        }
                    }
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val availableWidth = maxWidth

                if (availableWidth >= 480.dp) {
                    // 3 Kolom Penuh untuk Tablet. Papan digulung bersama (bukan per kolom)
                    // supaya tiap kartu bisa memanjang ke bawah sesuai banyaknya orderan.
                    EqualHeightBoardRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 12.dp)
                    ) {
                        column1()
                        column2()
                        column3()
                    }
                } else {
                    // Mobile / Layar Sempit: Pager dengan Swipeable Tabs
                    Column(modifier = Modifier.fillMaxSize()) {
                        val tabs = listOf("Menunggu (${pendingOrders.size})", "Diproses (${preparingOrders.size})", "Selesai (${completedOrders.size})")
                        TabRow(
                            selectedTabIndex = pagerState.currentPage,
                            containerColor = CreamBackground,
                            contentColor = ShawarmaOrange,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            tabs.forEachIndexed { index, title ->
                                Tab(
                                    selected = pagerState.currentPage == index,
                                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                                    text = { Text(title, fontWeight = FontWeight.Bold, maxLines = 1, fontSize = 12.sp) }
                                )
                            }
                        }
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 4.dp)
                            ) {
                                when (page) {
                                    0 -> column1()
                                    1 -> column2()
                                    2 -> column3()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Measures all board columns once to find the tallest content, then measures
 * every column again at that exact height. This works with the board's shared
 * vertical scroll, where intrinsic row sizing alone does not propagate height
 * to sibling cards.
 */
@Composable
private fun EqualHeightBoardRow(
    modifier: Modifier = Modifier,
    spacing: androidx.compose.ui.unit.Dp = 12.dp,
    content: @Composable () -> Unit
) {
    Layout(modifier = modifier, content = content) { measurables, constraints ->
        if (measurables.isEmpty()) {
            layout(0, 0) {}
        } else {
            val spacingPx = spacing.roundToPx()
            val totalSpacing = spacingPx * (measurables.size - 1)
            val rowWidth = constraints.maxWidth
            val columnWidth = ((rowWidth - totalSpacing) / measurables.size).coerceAtLeast(0)
            // A child may only be measured once per layout pass. Query intrinsic
            // height first, then do the single real measurement at the shared size.
            val tallestHeight = measurables.maxOf { it.maxIntrinsicHeight(columnWidth) }
            val equalHeightConstraints = Constraints(
                minWidth = columnWidth,
                maxWidth = columnWidth,
                minHeight = tallestHeight,
                maxHeight = tallestHeight
            )
            val placeables = measurables.map { it.measure(equalHeightConstraints) }

            layout(rowWidth, tallestHeight) {
                var x = 0
                placeables.forEach { placeable ->
                    placeable.placeRelative(x = x, y = 0)
                    x += columnWidth + spacingPx
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
    subTabCounts: Map<String, Int> = emptyMap(),
    contentCount: Int = badgeCount,
    hasSearchBar: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    emptyMessage: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = TwGray50,
        border = androidx.compose.foundation.BorderStroke(1.dp, CreamBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(icon, contentDescription = null, tint = ShawarmaOrange, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDarkPrimary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ShawarmaOrangeLight
                ) {
                    Text(
                        text = "$badgeCount",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ShawarmaOrangeDark,
                        maxLines = 1
                    )
                }
            }

            if (hasSubTabs) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .background(CreamBorder, RoundedCornerShape(8.dp))
                        .padding(4.dp)
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = tab,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) ShawarmaOrange else TextDarkSecondary,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${subTabCounts[tab] ?: 0}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TwRed600,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            if (hasSearchBar) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Cari antrian...", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        unfocusedBorderColor = CreamBorder
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (contentCount == 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 200.dp)
                        .border(1.dp, CreamBorder, RoundedCornerShape(10.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Icon(icon, contentDescription = null, tint = TextDarkMuted, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = emptyMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextDarkMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            maxLines = 4,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    content()
                }
            }
        }
    }
}


