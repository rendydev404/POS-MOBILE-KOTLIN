package com.sukashawarma.pos.presentation.order_manual

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable
fun POSManualOrderScreen(
    viewModel: POSManualOrderViewModel,
    modifier: Modifier = Modifier
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

    // menuItems/kioskSettings changes should re-derive the visible grid too.
    val menuItemsState by viewModel.menuItems.collectAsState()
    val visibleItems = remember(menuItemsState, selectedCatId, searchQuery, mode, channel) {
        viewModel.visibleItems()
    }
    val totals = remember(cartLines, promoSubsidy, channel, mode) { viewModel.cartTotals() }

    Column(modifier = modifier.fillMaxSize()) {
        OrderManualHeader(mode = mode)

        OrderModeTabRow(mode = mode, onModeSelected = { viewModel.switchMode(it) })

        if (showInfoBanner) {
            InfoBanner(mode = mode, onDismiss = { viewModel.showInfoBanner.value = false })
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // LEFT: channel selector (online only) + search/category + menu grid
            Column(
                modifier = Modifier
                    .weight(1.6f)
                    .fillMaxHeight()
            ) {
                if (mode == OrderMode.ONLINE) {
                    ChannelSelector(selectedChannel = channel, onSelect = { viewModel.selectChannel(it) })
                    Spacer(modifier = Modifier.height(12.dp))
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.searchQuery.value = it },
                    placeholder = { Text("Cari menu...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedCatId.isEmpty(),
                            onClick = { viewModel.selectedCategoryId.value = "" },
                            label = { Text("Semua Menu", fontWeight = if (selectedCatId.isEmpty()) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AmberPrimary,
                                selectedLabelColor = SlateBackground
                            )
                        )
                    }
                    items(categories) { cat ->
                        val selected = cat.id == selectedCatId
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.selectedCategoryId.value = cat.id },
                            label = { Text(cat.name, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AmberPrimary,
                                selectedLabelColor = SlateBackground
                            )
                        )
                    }
                }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AmberPrimary)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
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

            // RIGHT: persistent cart panel (Nama Customer, Metode Pembayaran, Uang Diterima, cart, totals)
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
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
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

    orderSuccessInfo?.let { success ->
        OrderSuccessOverlay(success = success, onDismiss = { viewModel.dismissSuccess() })
    }

    orderErrorMessage?.let { message ->
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = StatusPending.copy(alpha = 0.95f)
            ) {
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
}

@Composable
private fun OrderManualHeader(mode: OrderMode) {
    val (title, subtitle) = when (mode) {
        OrderMode.WALKIN -> "Order Offline — Pesanan Baru" to "Catat pesanan pelanggan secara offline / langsung"
        OrderMode.ENDORSE -> "Order Endorse" to "Catat pesanan endorse dengan harga Rp 0"
        OrderMode.WEBSITE -> "Order Website — Backup Mandiri" to "Input cadangan pesanan via Website / WA"
        OrderMode.ONLINE -> "Input Food Apps" to "Input pesanan dari aplikasi makanan"
    }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}

@Composable
private fun OrderModeTabRow(mode: OrderMode, onModeSelected: (OrderMode) -> Unit) {
    val tabs = listOf(
        Triple(OrderMode.WALKIN, "Order Offline", Icons.Default.RestaurantMenu),
        Triple(OrderMode.ONLINE, "Food Apps", Icons.Default.Search),
        Triple(OrderMode.WEBSITE, "Order Website", Icons.Default.Search),
        Triple(OrderMode.ENDORSE, "Endorse", Icons.Default.Search)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tabs.forEach { (m, label, icon) ->
            val selected = m == mode
            Surface(
                modifier = Modifier.clickable { onModeSelected(m) },
                shape = RoundedCornerShape(8.dp),
                color = if (selected) AmberLight else Color.Transparent,
                border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, AmberPrimary) else null
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(icon, contentDescription = null, tint = if (selected) AmberDark else TextMuted, modifier = Modifier.size(18.dp))
                    Text(label, color = if (selected) AmberDark else TextSecondary, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
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
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFEAF3FB)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text, style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Tutup", tint = TextMuted)
            }
        }
    }
}

@Composable
private fun ChannelSelector(selectedChannel: String?, onSelect: (String) -> Unit) {
    Column {
        Text("1. Pilih Channel", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(modifier = Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(FOOD_APP_CHANNELS) { ch ->
                val selected = normalizeChannel(selectedChannel) == ch.id
                FilterChip(
                    selected = selected,
                    onClick = { onSelect(ch.id) },
                    label = { Text(ch.label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AmberPrimary,
                        selectedLabelColor = SlateBackground
                    )
                )
            }
        }
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
    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !disabled, onClick = onClick),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            border = if (cartQty > 0) androidx.compose.foundation.BorderStroke(2.dp, AmberPrimary) else null
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(105.dp)
                                .then(if (disabled) Modifier.background(Color.Black.copy(alpha = 0.35f)) else Modifier)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .background(SlateBorder),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestaurantMenu,
                                contentDescription = null,
                                tint = AmberPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    if (disabled) {
                        Surface(
                            modifier = Modifier.align(Alignment.Center),
                            color = StatusPending,
                            shape = RoundedCornerShape(6.dp)
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
                    if (cartQty > 0) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp),
                            color = AmberPrimary,
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(
                                "$cartQty",
                                color = SlateBackground,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = menuItem.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (disabled) TextMuted else TextPrimary,
                        maxLines = 2
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Rp ${String.format("%,.0f", displayPrice)}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (disabled) TextMuted else AmberPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        if (!disabled) {
                            Surface(shape = RoundedCornerShape(6.dp), color = AmberPrimary) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Tambah",
                                    tint = SlateBackground,
                                    modifier = Modifier.padding(4.dp).size(16.dp)
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
        shape = RoundedCornerShape(12.dp),
        color = SlateSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text("Keranjang", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(modifier = Modifier.height(12.dp))

            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                if (cartLines.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "Belum ada menu dipilih",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    cartLines.filter { it.parentId == null }.forEach { parent ->
                        CartRowItem(line = parent, isChild = false, onQuantityChange = { qty -> viewModel.setLineQuantity(parent.cartItemId, qty) })
                        cartLines.filter { it.parentId == parent.cartItemId }.forEach { child ->
                            CartRowItem(line = child, isChild = true, onQuantityChange = { qty -> viewModel.setLineQuantity(child.cartItemId, qty) })
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = customerName,
                    onValueChange = { viewModel.customerName.value = it },
                    label = { Text("Nama Customer *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (mode == OrderMode.WEBSITE) {
                    OutlinedTextField(
                        value = pickupTime,
                        onValueChange = { viewModel.pickupTime.value = it },
                        label = { Text("Jam Ambil (HH:MM) *") },
                        placeholder = { Text("14:30") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (needsPromoSubsidy) {
                    OutlinedTextField(
                        value = promoSubsidy,
                        onValueChange = { v -> viewModel.promoSubsidy.value = v.filter { it.isDigit() } },
                        label = { Text("Promo Apps (subsidi) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                val paymentOptions = remember(mode) { viewModel.availablePaymentMethods() }
                if (paymentOptions.isNotEmpty()) {
                    Text("Metode Pembayaran", style = MaterialTheme.typography.titleSmall, color = TextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        paymentOptions.forEach { method ->
                            PaymentChoiceButton(
                                label = paymentLabel(method),
                                selected = payment == method,
                                onClick = { viewModel.payment.value = method },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (payment == PaymentMethod.CASH) {
                    OutlinedTextField(
                        value = cashInput,
                        onValueChange = { v -> viewModel.cashInput.value = v.filter { it.isDigit() } },
                        label = { Text("Uang Diterima") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { viewModel.setExactCash(totals.total) }, modifier = Modifier.weight(1f)) {
                            Text("Uang Pas", style = MaterialTheme.typography.labelMedium)
                        }
                        viewModel.quickCashAmounts(totals.total).forEach { amount ->
                            OutlinedButton(
                                onClick = { viewModel.cashInput.value = amount.toLong().toString() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Rp ${String.format("%,.0f", amount)}", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    val received = cashInput.toDoubleOrNull() ?: 0.0
                    if (received > 0) {
                        val enough = received >= totals.total
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = (if (enough) StatusCompleted else StatusPending).copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(if (enough) "Kembalian" else "Kurang", color = TextPrimary)
                                Text(
                                    "Rp ${String.format("%,.0f", kotlin.math.abs(received - totals.total))}",
                                    color = if (enough) StatusCompleted else StatusPending,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Divider(color = SlateBorder, modifier = Modifier.padding(vertical = 12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Subtotal", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Text("Rp ${String.format("%,.0f", totals.subtotal)}", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                }
                if (totals.discount > 0) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Diskon Promo", style = MaterialTheme.typography.bodyMedium, color = StatusPending)
                        Text("-Rp ${String.format("%,.0f", totals.discount)}", style = MaterialTheme.typography.bodyMedium, color = StatusPending)
                    }
                }
                if (totals.promoSubsidyAmount > 0) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Subsidi Promo Apps", style = MaterialTheme.typography.bodyMedium, color = StatusPending)
                        Text("-Rp ${String.format("%,.0f", totals.promoSubsidyAmount)}", style = MaterialTheme.typography.bodyMedium, color = StatusPending)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("TOTAL BAYAR", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Rp ${String.format("%,.0f", totals.total)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AmberPrimary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (payment == PaymentMethod.QRIS) {
                        viewModel.isQrisModalOpen.value = true
                    } else {
                        viewModel.submitOrder()
                    }
                },
                enabled = canSubmit && !isSubmitting,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary)
            ) {
                Text(
                    text = if (isSubmitting) "MEMPROSES..." else if (payment == PaymentMethod.QRIS) "TAMPILKAN QRIS" else "BAYAR SEKARANG",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SlateBackground
                )
            }
        }
    }
}

@Composable
private fun PaymentChoiceButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val borderColor = if (selected) AmberPrimary else SlateBorder
    val bgColor = if (selected) AmberPrimary.copy(alpha = 0.15f) else SlateCard
    Box(
        modifier = modifier
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .background(bgColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) AmberDark else TextSecondary, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

private fun paymentLabel(method: PaymentMethod): String = when (method) {
    PaymentMethod.CASH -> "Tunai"
    PaymentMethod.QRIS -> "QRIS"
    PaymentMethod.CARD -> "Debit"
    PaymentMethod.VA -> "Virtual Account"
}

@Composable
private fun CartRowItem(line: CartLine, isChild: Boolean, onQuantityChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (isChild) 16.dp else 0.dp, top = 4.dp)
            .background(if (isChild) SlateBackground else SlateCard, RoundedCornerShape(8.dp))
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = (if (isChild) "↳ " else "") + line.name,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary
            )
            Text("Rp ${String.format("%,.0f", line.subtotal)}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            if (!isChild && line.note.isNotBlank()) {
                Text(line.note, style = MaterialTheme.typography.bodySmall, color = TextMuted, maxLines = 2)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onQuantityChange(line.quantity - 1) }, modifier = Modifier.size(28.dp)) {
                Icon(imageVector = if (line.quantity == 1) Icons.Default.Delete else Icons.Default.Remove, contentDescription = null, tint = TextMuted)
            }
            Text("${line.quantity}", style = MaterialTheme.typography.titleSmall, color = AmberPrimary, modifier = Modifier.padding(horizontal = 8.dp))
            IconButton(onClick = { onQuantityChange(line.quantity + 1) }, modifier = Modifier.size(28.dp)) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = TextPrimary)
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
    val upsellItems = remember(menuItems) { viewModel.upsellItems() }

    Dialog(onDismissRequest = { viewModel.closeItemModal() }) {
        Surface(shape = RoundedCornerShape(16.dp), color = SlateSurface) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(menu.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
                    IconButton(onClick = { viewModel.closeItemModal() }) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup", tint = TextMuted)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                Text("Catatan Khusus", style = MaterialTheme.typography.titleSmall, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { viewModel.selectedMenuNote.value = it },
                    placeholder = { Text("Contoh: Pedas, tanpa bawang...") },
                    modifier = Modifier.fillMaxWidth().height(90.dp)
                )

                if (menu.isPackage && menu.packageItems.any { it.orMenuItemId != null }) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Pilihan Paket", style = MaterialTheme.typography.titleSmall, color = TextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    menu.packageItems.filter { it.orMenuItemId != null }.forEach { pi ->
                        val mainName = menuItems.find { it.id == pi.menuItemId }?.name ?: "Menu"
                        val altName = menuItems.find { it.id == pi.orMenuItemId }?.name ?: "Menu"
                        val chosen = packageChoices[pi.id] ?: pi.menuItemId
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                            PaymentChoiceButton(
                                label = mainName,
                                selected = chosen == pi.menuItemId,
                                onClick = { viewModel.selectPackageChoice(pi.id, pi.menuItemId) },
                                modifier = Modifier.weight(1f)
                            )
                            PaymentChoiceButton(
                                label = altName,
                                selected = chosen == pi.orMenuItemId,
                                onClick = { pi.orMenuItemId?.let { viewModel.selectPackageChoice(pi.id, it) } },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                if (upsellItems.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Menu Ekstra", style = MaterialTheme.typography.titleSmall, color = TextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    upsellItems.forEach { extra ->
                        val selected = (extras[extra.id] ?: 0) > 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, if (selected) AmberPrimary else SlateBorder, RoundedCornerShape(8.dp))
                                .clickable { viewModel.toggleExtra(extra.id) }
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${extra.name}  +Rp ${String.format("%,.0f", viewModel.priceFor(extra))}", color = TextPrimary)
                            Icon(
                                imageVector = if (selected) Icons.Default.Close else Icons.Default.Add,
                                contentDescription = null,
                                tint = if (selected) AmberPrimary else TextMuted
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Jumlah Pesanan", style = MaterialTheme.typography.titleSmall, color = TextSecondary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.decrementModalQty() }) {
                            Icon(Icons.Default.Remove, contentDescription = null)
                        }
                        Text("$qty", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 8.dp))
                        IconButton(onClick = { viewModel.incrementModalQty() }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.confirmAddToCart() },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary)
                ) {
                    Text("Tambah ke Keranjang", color = SlateBackground, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Port of QrisPaymentModal.tsx — shows a live QR (qrserver.com, same source the web uses
 * when online) and lets the cashier attach a transfer-proof photo (camera or gallery),
 * uploaded to Supabase Storage only after the order is created (see
 * POSManualOrderViewModel.uploadPaymentProof). Food Apps channels can skip the proof
 * entirely via "Tandai Sudah Bayar" (port of QrisPaymentModal.tsx's isFoodApp shortcut).
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

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SlateSurface,
        title = { Text("Pembayaran QRIS", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text("Rp ${String.format("%,.0f", totalAmount)}", style = MaterialTheme.typography.headlineMedium, color = AmberPrimary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                AsyncImage(
                    model = "https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=shawarma-kasir://pay?amount=${totalAmount.toLong()}",
                    contentDescription = "QRIS",
                    modifier = Modifier.size(200.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Bukti Transfer", style = MaterialTheme.typography.titleSmall, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))

                if (proofBitmap != null) {
                    Image(
                        bitmap = proofBitmap!!.asImageBitmap(),
                        contentDescription = "Bukti transfer",
                        modifier = Modifier
                            .size(160.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.setQrisProofBitmap(null) }) {
                        Text("Hapus foto", color = StatusPending)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Ambil Foto")
                    }
                    OutlinedButton(onClick = { galleryLauncher.launch("image/*") }) {
                        Text("Pilih dari Galeri")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Minta pelanggan scan QR di atas, lalu unggah bukti transfer sebelum memproses pembayaran.",
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                Button(
                    onClick = onConfirmPaid,
                    enabled = proofBitmap != null,
                    colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary)
                ) {
                    Text("Proses Pembayaran", color = SlateBackground, fontWeight = FontWeight.Bold)
                }
                if (canMarkPaidWithoutProof) {
                    TextButton(onClick = onConfirmPaid) {
                        Text("Tandai Sudah Bayar (tanpa bukti)", color = TextSecondary)
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal", color = TextMuted) }
        }
    )
}

@Composable
private fun OrderSuccessOverlay(success: OrderSuccessInfo, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = SlateSurface) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Order Berhasil!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = StatusCompleted)
                Spacer(modifier = Modifier.height(8.dp))
                Text("No. Antrean", color = TextSecondary)
                Text("${success.orderNumber}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                if (success.method == PaymentMethod.CASH && success.change > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(color = StatusCompleted.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Kembalian", color = TextPrimary)
                            Text("Rp ${String.format("%,.0f", success.change)}", color = StatusCompleted, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary)
                ) {
                    Text("Transaksi Baru", color = SlateBackground, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
