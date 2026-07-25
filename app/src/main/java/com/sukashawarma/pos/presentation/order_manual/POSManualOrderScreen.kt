package com.sukashawarma.pos.presentation.order_manual

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sukashawarma.pos.domain.model.MenuItem
import com.sukashawarma.pos.domain.model.OrderItem
import com.sukashawarma.pos.domain.usecase.CalculateCartUseCase
import com.sukashawarma.pos.presentation.components.PaymentModal
import com.sukashawarma.pos.presentation.theme.*

@Composable
fun POSManualOrderScreen(
    viewModel: POSManualOrderViewModel,
    modifier: Modifier = Modifier
) {
    val categories by viewModel.categories.collectAsState()
    val menuItems by viewModel.menuItems.collectAsState()
    val selectedCatId by viewModel.selectedCategoryId.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val cartItems by viewModel.cartItems.collectAsState()
    val customerName by viewModel.customerName.collectAsState()
    val isPaymentModalOpen by viewModel.isPaymentModalOpen.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val filteredItems = menuItems.filter {
        (selectedCatId.isEmpty() || it.categoryId == selectedCatId) &&
                (searchQuery.isEmpty() || it.name.contains(searchQuery, ignoreCase = true))
    }

    val calc = remember(cartItems) {
        CalculateCartUseCase().execute(cartItems, viewModel.activePromos.value)
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // LEFT 65%: Catalog & Item Selector with Real Supabase Images
        Column(
            modifier = Modifier
                .weight(1.6f)
                .fillMaxHeight()
        ) {
            // Search Bar & Category Row
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.searchQuery.value = it },
                placeholder = { Text("Cari menu Supabase...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(10.dp),
                singleLine = true
            )

            // Category Chips
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

            // Menu Items Grid (3 Columns) with Real Coil Images
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
                    items(filteredItems, key = { it.id }) { item ->
                        MenuItemCard(
                            menuItem = item,
                            onAddToCart = { viewModel.addToCart(item) }
                        )
                    }
                }
            }
        }

        // RIGHT 35%: Persistent Real-Time Cart Panel
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
                Text(
                    text = "Keranjang Belanja",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = customerName,
                    onValueChange = { viewModel.customerName.value = it },
                    label = { Text("Nama Pelanggan (Opsional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = SlateBorder)
                Spacer(modifier = Modifier.height(12.dp))

                // Cart Items List
                if (cartItems.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Keranjang masih kosong.\nPilih menu di sebelah kiri.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        cartItems.forEach { item ->
                            CartRowItem(
                                item = item,
                                onQuantityChange = { delta -> viewModel.updateQuantity(item, delta) }
                            )
                        }
                    }
                }

                Divider(color = SlateBorder)
                Spacer(modifier = Modifier.height(12.dp))

                // Calculation Summary
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Subtotal", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Text("Rp ${String.format("%,.0f", calc.subtotal)}", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    }
                    if (calc.totalDiscount > 0) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Diskon Promo", style = MaterialTheme.typography.bodyMedium, color = StatusPending)
                            Text("-Rp ${String.format("%,.0f", calc.totalDiscount)}", style = MaterialTheme.typography.bodyMedium, color = StatusPending)
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("TOTAL BAYAR", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Rp ${String.format("%,.0f", calc.finalTotal)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AmberPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Checkout Button
                Button(
                    onClick = { viewModel.isPaymentModalOpen.value = true },
                    enabled = cartItems.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary)
                ) {
                    Text(
                        text = "BAYAR SEKARANG",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SlateBackground
                    )
                }
            }
        }
    }

    // Payment Modal Dialog
    if (isPaymentModalOpen) {
        PaymentModal(
            totalAmount = calc.finalTotal,
            onConfirmPayment = { method, amount ->
                viewModel.processOrder(method, amount)
            },
            onDismiss = { viewModel.isPaymentModalOpen.value = false }
        )
    }
}

@Composable
private fun MenuItemCard(
    menuItem: MenuItem,
    onAddToCart: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAddToCart),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Real Product Image loaded from Supabase Storage via Coil AsyncImage
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

            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = menuItem.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Rp ${String.format("%,.0f", menuItem.price)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = AmberPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = AmberPrimary
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Tambah",
                            tint = SlateBackground,
                            modifier = Modifier
                                .padding(4.dp)
                                .size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CartRowItem(
    item: OrderItem,
    onQuantityChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SlateCard, RoundedCornerShape(8.dp))
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(text = "Rp ${String.format("%,.0f", item.unitPrice)}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onQuantityChange(-1) }, modifier = Modifier.size(28.dp)) {
                Icon(imageVector = if (item.quantity == 1) Icons.Default.Delete else Icons.Default.Remove, contentDescription = null, tint = TextMuted)
            }
            Text(text = "${item.quantity}", style = MaterialTheme.typography.titleMedium, color = AmberPrimary, modifier = Modifier.padding(horizontal = 8.dp))
            IconButton(onClick = { onQuantityChange(1) }, modifier = Modifier.size(28.dp)) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = TextPrimary)
            }
        }
    }
}
