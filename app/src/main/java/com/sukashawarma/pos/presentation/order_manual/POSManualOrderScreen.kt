package com.sukashawarma.pos.presentation.order_manual

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.res.painterResource
import com.sukashawarma.pos.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sukashawarma.pos.domain.model.*
import com.sukashawarma.pos.presentation.theme.*

import com.sukashawarma.pos.presentation.printer.BluetoothPrinterDialog
import com.sukashawarma.pos.presentation.printer.BluetoothPrinterViewModel
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled

/**
 * Port of apps/pos-kasir/app/kasir/order-manual/page.tsx +
 * components/kasir/WalkInCartPanel.tsx + components/kasir/QrisPaymentModal.tsx.
 *
 * Styling here intentionally uses vanilla Tailwind colors (Tw* in theme/Color.kt) rather
 * than the app's custom brand palette (AmberPrimary etc.) — that's what the web source
 * literally uses on this page (`bg-amber-500`, `border-emerald-500`, ...), a different,
 * un-themed palette from the rest of this app's screens.
 */
@Composable
fun POSManualOrderScreen(
    viewModel: POSManualOrderViewModel,
    printerViewModel: BluetoothPrinterViewModel,
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {}
) {
    val mode by viewModel.mode.collectAsState()
    val channel by viewModel.channel.collectAsState()
    val payment by viewModel.payment.collectAsState()
    val customerName by viewModel.customerName.collectAsState()
    val pickupTime by viewModel.pickupTime.collectAsState()
    val promoSubsidy by viewModel.promoSubsidy.collectAsState()
    val cashInput by viewModel.cashInput.collectAsState()
    val showInfoBanner by viewModel.showInfoBanner.collectAsState()

    val categories by viewModel.categories.collectAsState()
    val selectedCatId by viewModel.selectedCategoryId.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val cartLines by viewModel.cartLines.collectAsState()

    val selectedMenu by viewModel.selectedMenu.collectAsState()
    val isQrisModalOpen by viewModel.isQrisModalOpen.collectAsState()
    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val orderErrorMessage by viewModel.orderErrorMessage.collectAsState()
    val orderSuccessInfo by viewModel.orderSuccessInfo.collectAsState()

    val menuItemsState by viewModel.menuItems.collectAsState()
    val visibleItems = remember(menuItemsState, selectedCatId, searchQuery, mode, channel) {
        viewModel.visibleItems()
    }
    val totals = remember(cartLines, promoSubsidy, channel, mode) { viewModel.cartTotals() }

    val context = LocalContext.current
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val bytes = inputStream?.readBytes()
                inputStream?.close()
                if (bytes != null) {
                    val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                    viewModel.handleScanImage("data:image/jpeg;base64,$base64")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    val isScanningReceipt by viewModel.isScanningReceipt.collectAsState()
    
    val connectionStatus by printerViewModel.connectionStatus.collectAsState()
    val isPrinterConnected = connectionStatus == com.sukashawarma.pos.presentation.printer.ConnectionStatus.CONNECTED
    var showPrinterDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TwGray50)
    ) {
        OrderManualHeader(
            mode = mode, 
            isPrinterConnected = isPrinterConnected,
            onPrinterClick = { showPrinterDialog = true },
            onBackClick = onBackClick
        )

        OrderModeTabRow(mode = mode, onModeSelected = { viewModel.switchMode(it) })

        // InfoBanner removed to save space

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (mode == OrderMode.ONLINE) {
                    ChannelSelector(selectedChannel = channel, onSelect = { viewModel.selectChannel(it) })
                }

                SearchAndCategoryCard(
                    searchQuery = searchQuery,
                    onSearchChange = { viewModel.searchQuery.value = it },
                    categories = categories,
                    selectedCatId = selectedCatId,
                    onCategorySelected = { viewModel.selectedCategoryId.value = it },
                    onScanReceiptClick = { imagePickerLauncher.launch("image/*") }
                )

                if (isLoading || isScanningReceipt) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = TwAmber500)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(visibleItems, key = { it.id }) { item ->
                            val disabled = viewModel.isDisabled(item)
                            MenuItemCard(
                                menuItem = item,
                                displayPrice = viewModel.priceFor(item),
                                cartQty = viewModel.cartQuantityFor(item.id),
                                disabled = disabled,
                                onClick = { if (!disabled) viewModel.onMenuItemClick(item) }
                            )
                        }
                    }
                }
            }

            // Kolomnya yang di-scroll, bukan isi panel: card keranjang memanjang ke bawah
            // mengikuti jumlah varian menu, lalu seluruh card digeser saat di-scroll.
            Column(
                modifier = Modifier
                    .width(360.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) {
                CartPanel(
                    viewModel = viewModel,
                    mode = mode,
                    channel = channel,
                    payment = payment,
                    customerName = customerName,
                    pickupTime = pickupTime,
                    promoSubsidy = promoSubsidy,
                    cashInput = cashInput,
                    cartLines = cartLines,
                    totals = totals,
                    isSubmitting = isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    selectedMenu?.let { menu ->
        ItemDetailModal(viewModel = viewModel, menu = menu)
    }

    if (isQrisModalOpen) {
        QrisModal(
            viewModel = viewModel,
            totalAmount = totals.total,
            canMarkPaidWithoutProof = viewModel.canMarkPaidWithoutProof(),
            onDismiss = { viewModel.isQrisModalOpen.value = false },
            onConfirmPaid = {
                viewModel.isQrisModalOpen.value = false
                viewModel.submitOrder()
            }
        )
    }

    val printerConnectionStatus by printerViewModel.connectionStatus.collectAsState()
    val currentUsername by viewModel.currentUsername.collectAsState()

    orderSuccessInfo?.let { success ->
        OrderSuccessOverlay(
            success = success, 
            printerConnectionStatus = printerConnectionStatus,
            onPrintKitchen = { printerViewModel.printReceiptAsync(context, success, currentUsername, isKitchen = true) },
            onPrintCustomer = { printerViewModel.printReceiptAsync(context, success, currentUsername, isKitchen = false) },
            onDismiss = { viewModel.dismissSuccess() }
        )
    }

    orderErrorMessage?.let { message ->
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
            Surface(shape = RoundedCornerShape(12.dp), color = TwRed600.copy(alpha = 0.96f)) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(message, color = Color.White, modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewModel.clearErrorMessage() }) {
                        Text("Tutup", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    
    if (showPrinterDialog) {
        BluetoothPrinterDialog(
            viewModel = printerViewModel,
            onDismiss = { showPrinterDialog = false }
        )
    }
}

@Composable
private fun OrderManualHeader(
    mode: OrderMode,
    isPrinterConnected: Boolean,
    onPrinterClick: () -> Unit,
    onBackClick: () -> Unit
) {
    val (title, subtitle) = when (mode) {
        OrderMode.WALKIN -> "Order Offline — Pesanan Baru" to "Catat pesanan pelanggan secara offline / langsung"
        OrderMode.ENDORSE -> "Order Endorse" to "Catat pesanan endorse dengan harga Rp 0"
        OrderMode.WEBSITE -> "Order Website — Backup Mandiri" to "Input cadangan pesanan via Website / WA"
        OrderMode.ONLINE -> "Input Food Apps" to "Input pesanan dari aplikasi makanan"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .background(Color.White, CircleShape)
                    .border(1.dp, TwGray200, CircleShape)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Kembali ke Dashboard",
                    tint = TwGray700
                )
            }
            Column {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = TwGray900)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TwGray500)
            }
        }
        
        IconButton(
            onClick = onPrinterClick,
            modifier = Modifier
                .background(if (isPrinterConnected) TwEmerald50 else TwRed50, CircleShape)
                .border(1.dp, if (isPrinterConnected) TwEmerald100 else TwRed100, CircleShape)
        ) {
            Icon(
                imageVector = if (isPrinterConnected) Icons.Default.BluetoothConnected else Icons.Default.BluetoothDisabled,
                contentDescription = "Printer Settings",
                tint = if (isPrinterConnected) TwEmerald600 else TwRed600
            )
        }
    }
}

/** Port of the segmented pill tab group (page.tsx:840-866): a gray-100 rounded "track"
 *  with the active tab floating as a white rounded chip with a shadow — not colored tabs. */
@Composable
private fun OrderModeTabRow(mode: OrderMode, onModeSelected: (OrderMode) -> Unit) {
    val tabs = listOf(
        Triple(OrderMode.WALKIN, "Order Offline", Icons.Filled.Store),
        Triple(OrderMode.ONLINE, "Food Apps", Icons.Filled.Public),
        Triple(OrderMode.WEBSITE, "Order Website", Icons.Filled.Public),
        Triple(OrderMode.ENDORSE, "Endorse", Icons.Filled.ThumbUp)
    )
    Surface(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = TwGray100
    ) {
        Row(modifier = Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            tabs.forEach { (m, label, icon) ->
                val selected = m == mode
                val activeColor = if (m == OrderMode.WEBSITE) TwBlue600 else TwAmber600
                Surface(
                    modifier = Modifier.clickable { onModeSelected(m) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) Color.White else Color.Transparent,
                    shadowElevation = if (selected) 2.dp else 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(icon, contentDescription = null, tint = if (selected) activeColor else TwGray500, modifier = Modifier.size(16.dp))
                        Text(
                            label,
                            color = if (selected) activeColor else TwGray500,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun InfoBanner(mode: OrderMode, onDismiss: () -> Unit) {
    val text = if (mode == OrderMode.WEBSITE) {
        "Panduan Order Website (Backup): Tab ini khusus dipakai jika pelanggan memesan lewat WhatsApp (WA) atau saat Website Order Online sedang ada kendala/gangguan. Kasir dapat menginput pesanan manual, mengisi Waktu Jam Ambil, dan memilih pembayaran (QRIS atau Virtual Account / VA)."
    } else {
        "Order Offline digunakan untuk pelanggan yang datang langsung atau memesan offline di kasir toko tanpa perantara aplikasi.\n" +
            "Food Apps digunakan untuk mencatat pesanan yang masuk dari aplikasi pihak ketiga seperti GrabFood, GoFood, dll.\n" +
            "Order Website digunakan sebagai cadangan jika pemesanan via WA/Website pelanggan berkendala.\n" +
            "Endorse digunakan untuk mencatat pesanan endorsement atau gratis (Rp 0). Stok menu tetap berkurang normal."
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = TwBlue50,
        border = androidx.compose.foundation.BorderStroke(1.dp, TwBlue100)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(TwBlue500),
                contentAlignment = Alignment.Center
            ) {
                Text("i", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
            }
            Text(text, style = MaterialTheme.typography.bodySmall, color = TwBlue900, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Tutup", tint = TwBlue400, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun ChannelSelector(selectedChannel: String?, onSelect: (String) -> Unit) {
    val logos = mapOf(
        "gofood" to R.drawable.ic_gofood,
        "shopeefood" to R.drawable.ic_shopeefood,
        "grabfood" to R.drawable.ic_grabfood,
        "tiktokgo" to R.drawable.ic_tiktokgo
    )

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
    ) {
        items(FOOD_APP_CHANNELS) { ch ->
            val selected = normalizeChannel(selectedChannel) == ch.id
            Row(
                modifier = Modifier
                    .border(1.dp, if (selected) TwAmber400 else TwGray200, RoundedCornerShape(12.dp))
                    .background(if (selected) TwAmber50 else Color.White, RoundedCornerShape(12.dp))
                    .clickable { onSelect(ch.id) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                logos[ch.id]?.let { resId ->
                    Image(
                        painter = painterResource(id = resId),
                        contentDescription = ch.label,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = ch.label, 
                    fontWeight = FontWeight.Bold, 
                    color = if (selected) TwAmber600 else TwGray700, 
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun SearchAndCategoryCard(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    categories: List<Category>,
    selectedCatId: String,
    onCategorySelected: (String) -> Unit,
    onScanReceiptClick: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                CategoryChip(label = "Semua", selected = selectedCatId.isEmpty(), onClick = { onCategorySelected("") })
            }
            items(categories) { cat ->
                CategoryChip(label = cat.name, selected = cat.id == selectedCatId, onClick = { onCategorySelected(cat.id) })
            }
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        IconButton(
            onClick = onScanReceiptClick,
            modifier = Modifier
                .size(40.dp)
                .background(TwAmber50, RoundedCornerShape(10.dp))
                .border(1.dp, TwAmber400, RoundedCornerShape(10.dp))
        ) {
            Icon(Icons.Default.PhotoCamera, contentDescription = "Scan Nota AI", tint = TwAmber600, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) TwGray900 else Color.White,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, TwGray200),
        shadowElevation = if (selected) 2.dp else 0.dp
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            color = if (selected) Color.White else TwGray600,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun MenuItemCard(
    menuItem: MenuItem,
    displayPrice: Double,
    cartQty: Int,
    disabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !disabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(
            if (cartQty > 0) 1.dp else 1.dp,
            if (cartQty > 0) TwAmber400 else TwGray200
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box {
                if (!menuItem.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(menuItem.imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = menuItem.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(80.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(80.dp).background(TwGray50),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.RestaurantMenu, contentDescription = null, tint = TwGray300, modifier = Modifier.size(32.dp))
                    }
                }
                if (disabled) {
                    Box(modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                        Surface(
                            color = TwRed500,
                            shape = RoundedCornerShape(20.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, TwRed200)
                        ) {
                            Text(
                                "HABIS",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                if (cartQty > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(TwAmber500)
                            .border(2.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("$cartQty", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = menuItem.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (disabled) TwGray400 else TwGray800,
                    minLines = 2,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Rp ${String.format("%,.0f", displayPrice)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (disabled) TwGray400 else TwAmber600,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun CartPanel(
    viewModel: POSManualOrderViewModel,
    mode: OrderMode,
    channel: String?,
    payment: PaymentMethod?,
    customerName: String,
    pickupTime: String,
    promoSubsidy: String,
    cashInput: String,
    cartLines: List<CartLine>,
    totals: CartTotals,
    isSubmitting: Boolean,
    modifier: Modifier = Modifier
) {
    val canSubmit = remember(cartLines, customerName, payment, channel, promoSubsidy, cashInput, mode, totals) {
        viewModel.canSubmit()
    }
    val activeChannel = normalizeChannel(if (mode == OrderMode.WEBSITE) "website" else channel)
    val needsPromoSubsidy = activeChannel != null && activeChannel in PROMO_SUBSIDY_CHANNELS

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, TwGray200)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Keranjang", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TwGray900)
                if (cartLines.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(color = TwAmber100, shape = RoundedCornerShape(20.dp)) {
                        Text(
                            "${cartLines.sumOf { it.quantity }}",
                            color = TwAmber600,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Divider(color = TwGray100, modifier = Modifier.padding(vertical = 12.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                if (cartLines.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TwGray50, RoundedCornerShape(16.dp))
                            .border(1.dp, TwGray200, RoundedCornerShape(16.dp))
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        com.sukashawarma.pos.presentation.components.EmptyState(
                            title = "Belum ada menu dipilih",
                            icon = Icons.Default.ShoppingCart,
                            minHeight = 0.dp
                        )
                    }
                } else {
                    cartLines.filter { it.parentId == null }.forEach { parent ->
                        CartRowItem(line = parent, isChild = false, onQuantityChange = { qty -> viewModel.setLineQuantity(parent.cartItemId, qty) })
                        cartLines.filter { it.parentId == parent.cartItemId }.forEach { child ->
                            CartRowItem(line = child, isChild = true, onQuantityChange = { qty -> viewModel.setLineQuantity(child.cartItemId, qty) })
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                FieldLabel("NAMA CUSTOMER")
                OutlinedTextField(
                    value = customerName,
                    onValueChange = { viewModel.customerName.value = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, unfocusedBorderColor = TwGray200)
                )

                val paymentOptions = remember(mode) { viewModel.availablePaymentMethods() }
                if (paymentOptions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    FieldLabel("METODE PEMBAYARAN")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        paymentOptions.forEach { method ->
                            PaymentChoiceButton(
                                method = method,
                                selected = payment == method,
                                onClick = { viewModel.payment.value = method },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                if (mode == OrderMode.WEBSITE) {
                    Spacer(modifier = Modifier.height(10.dp))
                    FieldLabel("JAM AMBIL (HH:MM)")
                    OutlinedTextField(
                        value = pickupTime,
                        onValueChange = { viewModel.pickupTime.value = it },
                        placeholder = { Text("14:30") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                }
                if (needsPromoSubsidy) {
                    Spacer(modifier = Modifier.height(10.dp))
                    FieldLabel("PROMO APPS (SUBSIDI)")
                    OutlinedTextField(
                        value = promoSubsidy,
                        onValueChange = { v -> viewModel.promoSubsidy.value = v.filter { it.isDigit() } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                if (payment == PaymentMethod.CASH) {
                    Spacer(modifier = Modifier.height(10.dp))
                    FieldLabel("UANG DITERIMA")
                    OutlinedTextField(
                        value = cashInput,
                        onValueChange = { v -> viewModel.cashInput.value = v.filter { it.isDigit() } },
                        leadingIcon = { Text("Rp", color = TwGray500, fontWeight = FontWeight.Bold) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    FieldLabel("PILIHAN CEPAT")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        QuickCashChip(
                            label = "Pas",
                            highlight = true,
                            onClick = { viewModel.setExactCash(totals.total) },
                            modifier = Modifier.weight(1f)
                        )
                        viewModel.quickCashAmounts(totals.total).take(2).forEach { amount ->
                            QuickCashChip(
                                label = "Rp ${String.format("%,.0f", amount)}",
                                highlight = false,
                                onClick = { viewModel.cashInput.value = amount.toLong().toString() },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    val received = cashInput.toDoubleOrNull() ?: 0.0
                    if (received > 0) {
                        val enough = received >= totals.total
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = if (enough) TwEmerald50 else TwRed50,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (enough) TwEmerald100 else TwRed100),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (enough) "Kembalian" else "Kurang", color = TwGray700, fontWeight = FontWeight.Medium)
                                Text(
                                    "Rp ${String.format("%,.0f", kotlin.math.abs(received - totals.total))}",
                                    color = if (enough) TwEmerald700 else TwRed600,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }

            }

            Divider(color = TwGray100, modifier = Modifier.padding(vertical = 12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                totals.missingAmount?.let { amount ->
                    Surface(
                        color = TwRed50,
                        border = androidx.compose.foundation.BorderStroke(1.dp, TwRed200),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Text(
                            "Tambah Rp ${String.format("%,.0f", amount)} lagi untuk dapat diskon promo!",
                            color = TwRed600,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(8.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Subtotal", style = MaterialTheme.typography.bodyMedium, color = TwGray500)
                    Text("Rp ${String.format("%,.0f", totals.subtotal)}", style = MaterialTheme.typography.bodyMedium, color = TwGray700, fontWeight = FontWeight.Bold)
                }
                if (totals.discount > 0) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Diskon", style = MaterialTheme.typography.bodyMedium, color = TwRed600)
                        Text("-Rp ${String.format("%,.0f", totals.discount)}", style = MaterialTheme.typography.bodyMedium, color = TwRed600)
                    }
                }
                if (totals.promoSubsidyAmount > 0) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Subsidi Promo Apps", style = MaterialTheme.typography.bodyMedium, color = TwRed600)
                        Text("-Rp ${String.format("%,.0f", totals.promoSubsidyAmount)}", style = MaterialTheme.typography.bodyMedium, color = TwRed600)
                    }
                }
                Divider(color = TwGray100, modifier = Modifier.padding(vertical = 6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Total Pembayaran", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = TwGray700)
                    Text("Rp ${String.format("%,.0f", totals.total)}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = TwGray900)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Modal "Tampilkan QRIS" (kode ini, tepatnya di bawah) hanya milik mode
            // Order Offline di web — lihat WalkInCartPanel.tsx: gate-nya persis
            // `payment === 'qris' && !isEndorse`. Untuk Food Apps/Website
            // (order-manual/page.tsx baris ~1780), QRIS cuma tag metode bayar biasa;
            // tombolnya selalu "Konfirmasi (Diproses)" dan langsung submit, tidak
            // pernah menampilkan gambar QR. Sebelum ini modal QRIS menyala untuk
            // SEMUA mode, jadi Food Apps pun ikut memunculkan QR yang tak seharusnya.
            val showsQrisModal = payment == PaymentMethod.QRIS && mode == OrderMode.WALKIN
            val buttonColor = when {
                showsQrisModal -> TwBlue500
                payment == PaymentMethod.CARD -> TwPurple500
                else -> TwEmerald500
            }
            val buttonLabel = when {
                isSubmitting -> "MEMPROSES..."
                showsQrisModal -> "Tampilkan QRIS"
                mode == OrderMode.ENDORSE -> "Simpan Pesanan Endorse"
                mode == OrderMode.ONLINE || mode == OrderMode.WEBSITE -> "Konfirmasi (Diproses)"
                else -> "Bayar & Cetak Struk"
            }
            Button(
                onClick = {
                    if (showsQrisModal) {
                        viewModel.isQrisModalOpen.value = true
                    } else {
                        viewModel.submitOrder()
                    }
                },
                enabled = canSubmit && !isSubmitting,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
            ) {
                Icon(
                    if (showsQrisModal) Icons.Filled.QrCode else Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(buttonLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TwGray500)
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun QuickCashChip(label: String, highlight: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (highlight) TwEmerald100 else Color.White,
        border = if (highlight) null else androidx.compose.foundation.BorderStroke(1.dp, TwGray200)
    ) {
        Text(
            label,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp),
            color = if (highlight) TwEmerald700 else TwGray700,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Visible
        )
    }
}

private fun paymentAccentColor(method: PaymentMethod): Color = when (method) {
    PaymentMethod.CASH -> TwEmerald500
    PaymentMethod.QRIS -> TwBlue500
    PaymentMethod.CARD -> TwPurple500
    PaymentMethod.VA -> TwBlue500
}

private fun paymentAccentSoft(method: PaymentMethod): Color = when (method) {
    PaymentMethod.CASH -> TwEmerald50
    PaymentMethod.QRIS -> TwBlue50
    PaymentMethod.CARD -> TwPurple50
    PaymentMethod.VA -> TwBlue50
}

private fun paymentAccentText(method: PaymentMethod): Color = when (method) {
    PaymentMethod.CASH -> TwEmerald700
    PaymentMethod.QRIS -> TwBlue600
    PaymentMethod.CARD -> TwPurple700
    PaymentMethod.VA -> TwBlue600
}

private fun paymentLabel(method: PaymentMethod): String = when (method) {
    PaymentMethod.CASH -> "Tunai"
    PaymentMethod.QRIS -> "QRIS"
    PaymentMethod.CARD -> "Debit"
    PaymentMethod.VA -> "Virtual Account"
}

/** Port of WalkInCartPanel's payment-method buttons (border-2, color-coded per method). */
@Composable
private fun PaymentChoiceButton(method: PaymentMethod, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = paymentAccentColor(method)
    Row(
        modifier = modifier
            .border(2.dp, if (selected) accent else TwGray200, RoundedCornerShape(12.dp))
            .background(if (selected) paymentAccentSoft(method) else Color.White, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = when (method) {
            PaymentMethod.CASH -> Icons.Filled.Payments
            PaymentMethod.QRIS -> Icons.Filled.QrCode
            PaymentMethod.CARD -> Icons.Filled.CreditCard
            PaymentMethod.VA -> Icons.Filled.CreditCard
        }
        Icon(icon, contentDescription = null, tint = if (selected) paymentAccentText(method) else TwGray500, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            paymentLabel(method),
            color = if (selected) paymentAccentText(method) else TwGray600,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun CartRowItem(line: CartLine, isChild: Boolean, onQuantityChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (isChild) 20.dp else 0.dp, top = 4.dp)
            .background(if (isChild) TwAmber50 else Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, if (isChild) TwAmber100 else TwGray200, RoundedCornerShape(12.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = (if (isChild) "↳ Extra: " else "") + line.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = TwGray800
            )
            Text("Rp ${String.format("%,.0f", line.subtotal)}", style = MaterialTheme.typography.bodySmall, color = TwAmber600, fontWeight = FontWeight.Bold)
            if (!isChild && line.note.isNotBlank()) {
                Text(line.note, style = MaterialTheme.typography.bodySmall, color = TwGray400, maxLines = 2)
            }
        }
        Row(
            modifier = Modifier
                .background(TwGray50, RoundedCornerShape(8.dp))
                .border(1.dp, TwGray100, RoundedCornerShape(8.dp))
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onQuantityChange(line.quantity - 1) }, modifier = Modifier.size(26.dp)) {
                Icon(
                    imageVector = if (line.quantity == 1) Icons.Default.Delete else Icons.Default.Remove,
                    contentDescription = null,
                    tint = if (line.quantity == 1) TwRed500 else TwGray600,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text("${line.quantity}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = TwGray800, modifier = Modifier.padding(horizontal = 6.dp))
            IconButton(onClick = { onQuantityChange(line.quantity + 1) }, modifier = Modifier.size(26.dp)) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = TwAmber600, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun ItemDetailModal(viewModel: POSManualOrderViewModel, menu: MenuItem) {
    val qty by viewModel.selectedMenuQty.collectAsState()
    val note by viewModel.selectedMenuNote.collectAsState()
    val extras by viewModel.selectedExtras.collectAsState()
    val packageChoices by viewModel.selectedPackageChoices.collectAsState()
    val menuItems by viewModel.menuItems.collectAsState()
    val upsellItems by viewModel.upsellItems.collectAsState()

    Dialog(onDismissRequest = { viewModel.closeItemModal() }) {
        Surface(shape = RoundedCornerShape(20.dp), color = Color.White) {
            Column(modifier = Modifier.width(480.dp)) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TwGray50.copy(alpha = 0.5f))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(menu.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TwGray800)
                    Surface(
                        modifier = Modifier.clickable { viewModel.closeItemModal() },
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, TwGray200),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup", tint = TwGray500, modifier = Modifier.padding(8.dp).size(18.dp))
                    }
                }
                Divider(color = TwGray100)

                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.StickyNote2, contentDescription = null, tint = TwAmber500, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Catatan Khusus", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = TwGray700)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = note,
                        onValueChange = { viewModel.selectedMenuNote.value = it },
                        placeholder = { Text("Contoh: Pedas, tanpa bawang...") },
                        modifier = Modifier.fillMaxWidth().height(90.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = TwGray50, unfocusedBorderColor = TwGray200)
                    )

                    if (menu.isPackage && menu.packageItems.any { it.orMenuItemId != null }) {
                        Spacer(modifier = Modifier.height(20.dp))
                        Text("Pilihan Paket", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = TwGray700)
                        Spacer(modifier = Modifier.height(8.dp))
                        menu.packageItems.filter { it.orMenuItemId != null }.forEach { pi ->
                            val mainName = menuItems.find { it.id == pi.menuItemId }?.name ?: "Menu"
                            val altName = menuItems.find { it.id == pi.orMenuItemId }?.name ?: "Menu"
                            val chosen = packageChoices[pi.id] ?: pi.menuItemId
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                                PackageChoiceButton(label = mainName, selected = chosen == pi.menuItemId, onClick = { viewModel.selectPackageChoice(pi.id, pi.menuItemId) }, modifier = Modifier.weight(1f))
                                PackageChoiceButton(label = altName, selected = chosen == pi.orMenuItemId, onClick = { pi.orMenuItemId?.let { viewModel.selectPackageChoice(pi.id, it) } }, modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    if (upsellItems.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = TwAmber500, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Menu Ekstra", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = TwGray700)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        // Port of the 2-column extras grid (order-manual/page.tsx:1244-1290) —
                        // thumbnail-left, text-middle, toggle-button-right mini cards.
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            upsellItems.chunked(2).forEach { rowItems ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    rowItems.forEach { extra ->
                                        val selected = (extras[extra.id] ?: 0) > 0
                                        Box(modifier = Modifier.weight(1f)) {
                                            ExtraItemCard(
                                                extra = extra,
                                                price = viewModel.priceFor(extra),
                                                selected = selected,
                                                onClick = { viewModel.toggleExtra(extra.id) }
                                            )
                                        }
                                    }
                                    if (rowItems.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Jumlah Pesanan", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = TwGray700)
                        Row(
                            modifier = Modifier
                                .background(TwGray50, RoundedCornerShape(12.dp))
                                .border(1.dp, TwGray200, RoundedCornerShape(12.dp))
                                .padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                modifier = Modifier.clickable { viewModel.decrementModalQty() },
                                color = Color.White,
                                shape = RoundedCornerShape(8.dp),
                                shadowElevation = 1.dp
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = null, tint = TwGray600, modifier = Modifier.padding(10.dp).size(18.dp))
                            }
                            Text("$qty", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TwGray800, modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
                            Surface(
                                modifier = Modifier.clickable { viewModel.incrementModalQty() },
                                color = TwAmber500,
                                shape = RoundedCornerShape(8.dp),
                                shadowElevation = 1.dp
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.padding(10.dp).size(18.dp))
                            }
                        }
                    }
                }

                Divider(color = TwGray100)
                Box(modifier = Modifier.padding(16.dp)) {
                    Button(
                        onClick = { viewModel.confirmAddToCart() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TwAmber500)
                    ) {
                        Text("Tambah ke Keranjang", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtraItemCard(extra: MenuItem, price: Double, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (selected) TwAmber400 else TwGray200, RoundedCornerShape(12.dp))
            .background(if (selected) TwAmber50.copy(alpha = 0.4f) else Color.White, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!extra.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(extra.imageUrl).crossfade(true).build(),
                contentDescription = extra.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
            )
        } else {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(TwGray100).border(1.dp, TwGray200, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.RestaurantMenu, contentDescription = null, tint = TwGray400, modifier = Modifier.size(18.dp))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(extra.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = TwGray800, maxLines = 1)
            Text("+Rp ${String.format("%,.0f", price)}", style = MaterialTheme.typography.labelSmall, color = TwAmber600, fontWeight = FontWeight.Bold)
        }
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (selected) TwAmber500 else TwGray50)
                .then(if (selected) Modifier else Modifier.border(1.dp, TwGray200, RoundedCornerShape(8.dp))),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Default.Add,
                contentDescription = null,
                tint = if (selected) Color.White else TwGray600,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun PackageChoiceButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .border(1.dp, if (selected) TwAmber400 else TwGray200, RoundedCornerShape(10.dp))
            .background(if (selected) TwAmber50 else Color.White, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) TwAmber600 else TwGray600, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * Port of QrisPaymentModal.tsx — top blue accent strip, live QR (qrserver.com, same source
 * the web uses when online), and a transfer-proof photo (camera/gallery). Food Apps channels
 * can skip the proof entirely via "Tandai Sudah Bayar" (web's isFoodApp shortcut).
 */
@Composable
private fun QrisModal(
    viewModel: POSManualOrderViewModel,
    totalAmount: Double,
    canMarkPaidWithoutProof: Boolean,
    onDismiss: () -> Unit,
    onConfirmPaid: () -> Unit
) {
    val proofBitmap by viewModel.qrisProofBitmap.collectAsState()
    val context = LocalContext.current

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) viewModel.setQrisProofBitmap(bitmap)
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraLauncher.launch(null)
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val bitmap = try {
                context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) }
            } catch (e: Exception) {
                null
            }
            if (bitmap != null) viewModel.setQrisProofBitmap(bitmap)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = Color.White) {
            Column(modifier = Modifier.width(480.dp)) {
                Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(TwBlue500))

                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Pembayaran QRIS", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TwGray900)
                        Surface(
                            modifier = Modifier.clickable(onClick = onDismiss),
                            color = TwGray100,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup", tint = TwGray500, modifier = Modifier.padding(8.dp).size(18.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, TwGray100),
                        shape = RoundedCornerShape(16.dp),
                        shadowElevation = 2.dp
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.qris_static),
                            contentDescription = "QRIS",
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth()
                                .aspectRatio(1f),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("TOTAL BAYAR", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = TwGray600)
                    Text("Rp ${String.format("%,.0f", totalAmount)}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = TwAmber500)

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = TwGray100)
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Bukti Transfer", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = TwGray700)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (proofBitmap != null) {
                        Image(
                            bitmap = proofBitmap!!.asImageBitmap(),
                            contentDescription = "Bukti transfer",
                            modifier = Modifier.size(160.dp).clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { viewModel.setQrisProofBitmap(null) }) {
                            Text("Hapus foto", color = TwRed600)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            modifier = Modifier.clickable { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                            color = TwBlue50,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = TwBlue600, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Ambil Foto", color = TwBlue600, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Surface(
                            modifier = Modifier.clickable { galleryLauncher.launch("image/*") },
                            color = TwGray100,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                "Pilih dari Galeri",
                                color = TwGray700,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onConfirmPaid,
                        enabled = proofBitmap != null,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TwBlue500)
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Proses Pembayaran", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    if (canMarkPaidWithoutProof) {
                        TextButton(onClick = onConfirmPaid) {
                            Text("Tandai Sudah Bayar (tanpa bukti)", color = TwGray500)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderSuccessOverlay(
    success: OrderSuccessInfo, 
    printerConnectionStatus: com.sukashawarma.pos.presentation.printer.ConnectionStatus,
    onPrintKitchen: suspend () -> Boolean,
    onPrintCustomer: suspend () -> Boolean,
    onDismiss: () -> Unit
) {
    var printStep by remember { androidx.compose.runtime.mutableStateOf(0) } // 0: Kitchen, 1: Customer, 2: Done
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isPrinting by remember { androidx.compose.runtime.mutableStateOf(false) }

    // Dialog is unclosable manually until finished, so no effect on dismissRequest unless done.
    Dialog(
        onDismissRequest = { 
            if (printStep == 2) onDismiss() 
        },
        properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(shape = RoundedCornerShape(20.dp), color = Color.White) {
            Column(
                modifier = Modifier.padding(28.dp).width(320.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = TwEmerald500, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Order Berhasil!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = TwEmerald600)
                Spacer(modifier = Modifier.height(8.dp))
                Text("No. Antrean", color = TwGray500)
                Text("${success.orderNumber}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = TwGray900)
                if (success.method == PaymentMethod.CASH && success.change > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(color = TwEmerald50, border = androidx.compose.foundation.BorderStroke(1.dp, TwEmerald100), shape = RoundedCornerShape(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Kembalian", color = TwGray700)
                            Text("Rp ${String.format("%,.0f", success.change)}", color = TwEmerald700, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))

                // Step indicators
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Text(
                        text = "1. Dapur", 
                        color = if (printStep >= 0) TwBlue600 else TwGray400,
                        fontWeight = if (printStep == 0) FontWeight.Bold else FontWeight.Normal
                    )
                    Text(text = "-->", color = TwGray400)
                    Text(
                        text = "2. Customer", 
                        color = if (printStep >= 1) TwBlue600 else TwGray400,
                        fontWeight = if (printStep == 1) FontWeight.Bold else FontWeight.Normal
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                
                when (printStep) {
                    0 -> {
                        Button(
                            onClick = {
                                if (printerConnectionStatus != com.sukashawarma.pos.presentation.printer.ConnectionStatus.CONNECTED) {
                                    android.widget.Toast.makeText(context, "Bluetooth Printer belum terkoneksi!", android.widget.Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                scope.launch {
                                    isPrinting = true
                                    val successPrint = onPrintKitchen()
                                    isPrinting = false
                                    if (successPrint) {
                                        printStep = 1
                                    } else {
                                        android.widget.Toast.makeText(context, "Gagal mencetak struk dapur. Periksa printer.", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isPrinting,
                            colors = ButtonDefaults.buttonColors(containerColor = TwBlue500)
                        ) {
                            Icon(Icons.Filled.Print, contentDescription = "Print Kitchen", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Cetak Struk Dapur", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    1 -> {
                        Button(
                            onClick = {
                                if (printerConnectionStatus != com.sukashawarma.pos.presentation.printer.ConnectionStatus.CONNECTED) {
                                    android.widget.Toast.makeText(context, "Bluetooth Printer belum terkoneksi!", android.widget.Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                scope.launch {
                                    isPrinting = true
                                    val successPrint = onPrintCustomer()
                                    isPrinting = false
                                    if (successPrint) {
                                        printStep = 2
                                    } else {
                                        android.widget.Toast.makeText(context, "Gagal mencetak struk customer. Periksa printer.", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isPrinting,
                            colors = ButtonDefaults.buttonColors(containerColor = TwBlue500)
                        ) {
                            Icon(Icons.Filled.Print, contentDescription = "Print Customer", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Cetak Struk Customer", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    2 -> {
                        Text("Semua struk berhasil dicetak!", color = TwEmerald600, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = TwEmerald500)
                        ) {
                            Text("Selesai & Transaksi Baru", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Jalan keluar wajib. Tombol cetak hanya menaikkan `printStep` bila
                // pencetakan BERHASIL, sementara dialog ini menolak tombol back dan
                // ketukan di luar — jadi tanpa printer (mode offline / printer belum
                // terhubung) kasir terkurung dan harus mematikan aplikasi.
                if (printStep != 2) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isPrinting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Lewati & Tutup",
                            color = TwGray500,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        "Pesanan tetap tersimpan. Struk bisa dicetak ulang dari kartu pesanan.",
                        color = TwGray400,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}
