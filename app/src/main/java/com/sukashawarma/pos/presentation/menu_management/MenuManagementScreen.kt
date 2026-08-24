package com.sukashawarma.pos.presentation.menu_management

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
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
    val kioskSettings by viewModel.kioskSettings.collectAsState()
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
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
        val isNarrow = maxWidth < 600.dp
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            val titleBlock: @Composable () -> Unit = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.RestaurantMenu, contentDescription = null, tint = AmberPrimary)
                        Text(
                            text = "Manajemen Menu Kasir",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Text(
                        text = "${filteredItems.size} menu tampil",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp, start = 32.dp)
                    )
                }
            }
            val searchField: @Composable (Modifier) -> Unit = { fieldModifier ->
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.searchQuery.value = it },
                    placeholder = { Text("Cari menu...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = fieldModifier,
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AmberPrimary,
                        unfocusedBorderColor = SlateBorder
                    )
                )
            }

            if (isNarrow) {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    titleBlock()
                    searchField(Modifier.fillMaxWidth())
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    titleBlock()
                    searchField(Modifier.widthIn(max = 300.dp).weight(1f, fill = false))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Categories Filter
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedCatId.isEmpty(),
                        onClick = { viewModel.selectedCategoryId.value = "" },
                        label = { Text("Semua Kategori", fontWeight = FontWeight.SemiBold) },
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
                        label = { Text(cat.name, fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AmberPrimary,
                            selectedLabelColor = SlateBackground
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Table Header
            Surface(
                color = SlateCard,
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Foto", modifier = Modifier.width(64.dp), style = MaterialTheme.typography.labelMedium, color = TextSecondary, fontWeight = FontWeight.Bold)
                    Text("Nama Menu", modifier = Modifier.weight(2f), style = MaterialTheme.typography.labelMedium, color = TextSecondary, fontWeight = FontWeight.Bold)
                    Text("Kategori", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = TextSecondary, fontWeight = FontWeight.Bold)
                    Text("Harga", modifier = Modifier.weight(1f).padding(end = 16.dp), style = MaterialTheme.typography.labelMedium, color = TextSecondary, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                    Text("Status", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = TextSecondary, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("Aksi", modifier = Modifier.width(100.dp), style = MaterialTheme.typography.labelMedium, color = TextSecondary, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AmberPrimary)
                }
            } else if (filteredItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    com.sukashawarma.pos.presentation.components.EmptyState(
                        title = "Menu tidak ditemukan",
                        icon = Icons.Default.Search
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        MenuTableRow(item = item, viewModel = viewModel, kioskSettings = kioskSettings)
                    }
                }
            }
        }
        }
    }
}

@Composable
fun MenuTableRow(item: MenuItem, viewModel: MenuManagementViewModel, kioskSettings: com.sukashawarma.pos.domain.menu.KioskSettings) {
    val isGlobal = item.outletId == null
    val isManualUnav = kioskSettings.unavailableIds.contains(item.id)
    val isAutoUnav = kioskSettings.autoUnavailableIds.contains(item.id)
    val isForceAvail = kioskSettings.forceAvailableIds.contains(item.id)
    
    val isAvail = item.isAvailable && !(isManualUnav || (isAutoUnav && !isForceAvail))
    val autoDisabled = isAutoUnav && !isForceAvail

    Surface(
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = SlateBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Foto
            Box(modifier = Modifier.width(64.dp)) {
                if (isGlobal) {
                    Box(
                        modifier = Modifier
                            .offset(x = (-4).dp, y = (-4).dp)
                            .zIndex(1f)
                            .background(Color(0xFF3B82F6), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Public, contentDescription = "Global", tint = Color.White, modifier = Modifier.size(10.dp))
                    }
                }
                
                if (!item.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(com.sukashawarma.pos.data.local.MenuImageCache.resolve(item.imageUrl))
                            .crossfade(true)
                            .build(),
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SlateBorder)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(AmberPrimary.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.RestaurantMenu, contentDescription = null, tint = AmberPrimary, modifier = Modifier.size(24.dp))
                    }
                }
            }

            // Nama Menu
            Column(modifier = Modifier.weight(2f).padding(end = 8.dp)) {
                Text(text = item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                if (!item.description.isNullOrBlank()) {
                    Text(text = item.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            // Kategori
            Box(modifier = Modifier.weight(1f)) {
                if (item.categoryName.isNotBlank()) {
                    Surface(
                        color = AmberPrimary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = item.categoryName,
                            color = AmberPrimary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    Text("—", color = TextSecondary)
                }
            }

            // Harga
            Text(
                text = "Rp ${String.format("%,.0f", item.price)}",
                modifier = Modifier.weight(1f).padding(end = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.End
            )

            // Status (Switch)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    color = if (isAvail) StatusCompleted.copy(alpha = 0.1f) else StatusPending.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (isAvail) "Tersedia" else "Habis",
                        color = if (isAvail) StatusCompleted else StatusPending,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                
                Switch(
                    checked = isAvail,
                    onCheckedChange = { viewModel.toggleAvailability(item) },
                    modifier = Modifier.scale(0.8f),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = SlateBackground,
                        checkedTrackColor = StatusCompleted,
                        uncheckedThumbColor = SlateBackground,
                        uncheckedTrackColor = StatusPending
                    )
                )
                
                if (autoDisabled) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("(Habis di Sistem)", fontSize = 10.sp, color = TextSecondary)
                }
                if (isForceAvail) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("(Dipaksa Aktif)", fontSize = 10.sp, color = AmberPrimary)
                }
            }

            // Aksi
            Box(modifier = Modifier.width(100.dp), contentAlignment = Alignment.Center) {
                var expanded by remember { mutableStateOf(false) }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SlateCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                    modifier = Modifier.clickable { expanded = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Aksi", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(SlateCard)
                ) {
                    val isRec = kioskSettings.recommendations.contains(item.id)
                    val isUp = kioskSettings.upsells.contains(item.id)
                    val isBs = kioskSettings.bestsellers.contains(item.id)

                    DropdownMenuItem(
                        text = { Text(if (isRec) "Hapus Menu Rekomendasi" else "Jadikan Menu Rekomendasi", color = if (isRec) AmberPrimary else TextPrimary) },
                        trailingIcon = { if (isRec) Icon(Icons.Default.Check, null, tint = AmberPrimary, modifier = Modifier.size(16.dp)) },
                        onClick = { viewModel.toggleSettingMembership("recommendation_ids", item.id); expanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text(if (isUp) "Hapus Menu Ekstra" else "Jadikan Menu Ekstra", color = if (isUp) AmberPrimary else TextPrimary) },
                        trailingIcon = { if (isUp) Icon(Icons.Default.Check, null, tint = AmberPrimary, modifier = Modifier.size(16.dp)) },
                        onClick = { viewModel.toggleSettingMembership("upsell_ids", item.id); expanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text(if (isBs) "Hapus Best Seller" else "Tandai Best Seller", color = if (isBs) AmberPrimary else TextPrimary) },
                        trailingIcon = { if (isBs) Icon(Icons.Default.Check, null, tint = AmberPrimary, modifier = Modifier.size(16.dp)) },
                        onClick = { viewModel.toggleSettingMembership("bestseller_ids", item.id); expanded = false }
                    )
                    
                    if (isAutoUnav) {
                        HorizontalDivider(color = SlateBorder)
                        DropdownMenuItem(
                            text = { Text(if (isForceAvail) "Batal Paksa Aktif" else "Paksa Aktif (Abaikan Sistem)", color = AmberPrimary) },
                            trailingIcon = { if (isForceAvail) Icon(Icons.Default.Check, null, tint = AmberPrimary, modifier = Modifier.size(16.dp)) },
                            onClick = { viewModel.toggleSettingMembership("force_available_menu_ids", item.id); expanded = false }
                        )
                    }
                }
            }
        }
    }
}
