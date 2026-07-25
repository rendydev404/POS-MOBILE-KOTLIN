package com.sukashawarma.pos.presentation.menu_management

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.sukashawarma.pos.presentation.theme.*

@Composable
fun MenuManagementScreen(
    viewModel: MenuManagementViewModel,
    modifier: Modifier = Modifier
) {
    val categories by viewModel.categories.collectAsState()
    val menuItems by viewModel.menuItems.collectAsState()
    val selectedCatId by viewModel.selectedCategoryId.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val filteredItems = menuItems.filter {
        (selectedCatId.isEmpty() || it.categoryId == selectedCatId) &&
                (searchQuery.isEmpty() || it.name.contains(searchQuery, ignoreCase = true))
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
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
                Icon(Icons.Default.RestaurantMenu, contentDescription = null, tint = AmberPrimary)
                Text(
                    text = "Manajemen Stok & Ketersediaan Menu Supabase",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = SlateBorder)
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
                    placeholder = { Text("Cari produk Supabase...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedCatId.isEmpty(),
                            onClick = { viewModel.selectedCategoryId.value = "" },
                            label = { Text("Semua Category") },
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
                            label = { Text(cat.name) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AmberPrimary,
                                selectedLabelColor = SlateBackground
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Menu Items List with Real Supabase Thumbnails & Availability Toggle
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AmberPrimary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = SlateCard
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Real Product Thumbnail Image from Supabase Storage
                                    if (!item.imageUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(item.imageUrl)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = item.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(56.dp)
                                                .background(SlateBorder, RoundedCornerShape(8.dp))
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(56.dp)
                                                .background(SlateBorder, RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.RestaurantMenu,
                                                contentDescription = null,
                                                tint = AmberPrimary
                                            )
                                        }
                                    }

                                    Column {
                                        Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "Harga: Rp ${String.format("%,.0f", item.price)}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = AmberPrimary
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    val statusText = if (item.isAvailable) "TERSEDIA" else "HABIS (SOLD OUT)"
                                    val statusColor = if (item.isAvailable) StatusCompleted else StatusPending

                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = statusColor
                                    )

                                    Switch(
                                        checked = item.isAvailable,
                                        onCheckedChange = { viewModel.toggleAvailability(item) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = SlateBackground,
                                            checkedTrackColor = StatusCompleted,
                                            uncheckedThumbColor = SlateBackground,
                                            uncheckedTrackColor = StatusPending
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
