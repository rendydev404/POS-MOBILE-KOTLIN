package com.sukashawarma.pos.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukashawarma.pos.presentation.theme.*

enum class POSTab {
    DASHBOARD,
    ORDER_MANUAL,
    INFO_PORSI,
    MENU_MANAGEMENT,
    SHIFT_PETTY_CASH,
    SHIFT_CLOSE,
    HISTORI_BONUS,
    KIOSK_CONTROL,
    REPORTS,
    SETTINGS,
    PANDUAN,
    STOK_OUTLET
}

@Composable
fun SideNavRail(
    currentTab: POSTab,
    onTabSelected: (POSTab) -> Unit,
    outletName: String = "SUKA SHAWARMA BNR",
    lowStockCount: Int = 0,
    onLogoutClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isOrderExpanded by remember { mutableStateOf(true) }
    var isShiftExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(220.dp),
        color = CreamSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, CreamBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start
        ) {
            // Brand Logo Header (Matching Website Screenshot)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = com.sukashawarma.pos.R.mipmap.ic_launcher),
                    contentDescription = "Logo",
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "SHAWARMA",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextDarkPrimary,
                    letterSpacing = 1.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(StatusCompleted)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "ONLINE",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = StatusCompleted,
                        fontSize = 10.sp,
                        letterSpacing = 0.5.sp
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "CABANG $outletName",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDarkSecondary,
                    fontSize = 11.sp
                )
            }

            HorizontalDivider(color = CreamBorder)
            Spacer(modifier = Modifier.height(12.dp))

            // 1. Order Dropdown
            SidebarDropdownHeader(
                label = "Order",
                icon = Icons.Default.Notifications,
                isExpanded = isOrderExpanded,
                onClick = { isOrderExpanded = !isOrderExpanded }
            )

            if (isOrderExpanded) {
                SidebarSubItem(
                    label = "Pesanan Kasir",
                    isSelected = currentTab == POSTab.DASHBOARD,
                    onClick = { onTabSelected(POSTab.DASHBOARD) }
                )
                SidebarSubItem(
                    label = "Info Porsi & Stok",
                    isSelected = currentTab == POSTab.INFO_PORSI,
                    onClick = { onTabSelected(POSTab.INFO_PORSI) }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 2. Manajemen Menu
            SidebarItem(
                label = "Manajemen Menu",
                icon = Icons.Default.Fastfood,
                isSelected = currentTab == POSTab.MENU_MANAGEMENT,
                onClick = { onTabSelected(POSTab.MENU_MANAGEMENT) }
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 3. Shift Dropdown
            SidebarDropdownHeader(
                label = "Shift",
                icon = Icons.Default.AccountBalanceWallet,
                isExpanded = isShiftExpanded,
                onClick = { isShiftExpanded = !isShiftExpanded }
            )

            if (isShiftExpanded) {
                SidebarSubItem(
                    label = "Petty Cash",
                    isSelected = currentTab == POSTab.SHIFT_PETTY_CASH,
                    onClick = { onTabSelected(POSTab.SHIFT_PETTY_CASH) }
                )
                SidebarSubItem(
                    label = "Tutup Shift",
                    isSelected = currentTab == POSTab.SHIFT_CLOSE,
                    onClick = { onTabSelected(POSTab.SHIFT_CLOSE) }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 4. Histori & Bonus
            SidebarItem(
                label = "Histori & Bonus",
                icon = Icons.Default.Assignment,
                isSelected = currentTab == POSTab.HISTORI_BONUS,
                onClick = { onTabSelected(POSTab.HISTORI_BONUS) }
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 5. Kontrol Device Pelanggan
            SidebarItem(
                label = "Kontrol Device Pela...",
                icon = Icons.Default.Tv,
                isSelected = currentTab == POSTab.KIOSK_CONTROL,
                onClick = { onTabSelected(POSTab.KIOSK_CONTROL) }
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 6. Laporan
            SidebarItem(
                label = "Laporan",
                icon = Icons.Default.BarChart,
                isSelected = currentTab == POSTab.REPORTS,
                onClick = { onTabSelected(POSTab.REPORTS) }
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 7. Tampilan Layar
            SidebarItem(
                label = "Tampilan Layar",
                icon = Icons.Default.Image,
                isSelected = currentTab == POSTab.SETTINGS,
                onClick = { onTabSelected(POSTab.SETTINGS) }
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 8. Panduan
            SidebarItem(
                label = "Panduan",
                icon = Icons.Default.Book,
                isSelected = currentTab == POSTab.PANDUAN,
                onClick = { onTabSelected(POSTab.PANDUAN) }
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 9. Stok Outlet Badge
            SidebarItem(
                label = "Stok Outlet",
                icon = Icons.Default.Inventory2,
                isSelected = currentTab == POSTab.STOK_OUTLET,
                badgeCount = lowStockCount,
                onClick = { onTabSelected(POSTab.STOK_OUTLET) }
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = CreamBorder)
            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = TextDarkSecondary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Portal", style = MaterialTheme.typography.bodyMedium, color = TextDarkSecondary)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Print, contentDescription = null, tint = TextDarkSecondary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Koneksi Printer", style = MaterialTheme.typography.bodyMedium, color = TextDarkSecondary)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onLogoutClick)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ExitToApp, contentDescription = null, tint = MarqueeRed, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Keluar", style = MaterialTheme.typography.bodyMedium, color = MarqueeRed, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SidebarItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    badgeCount: Int = 0,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) ShawarmaOrangeLight else Color.Transparent
    val contentColor = if (isSelected) ShawarmaOrange else TextDarkSecondary

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = contentColor
                )
            }

            if (badgeCount > 0) {
                Surface(
                    shape = CircleShape,
                    color = MarqueeRed
                ) {
                    Text(
                        text = "$badgeCount",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SidebarDropdownHeader(
    label: String,
    icon: ImageVector,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = TextDarkSecondary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(text = label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = TextDarkPrimary)
        }
        Icon(
            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = TextDarkSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun SidebarSubItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) ShawarmaOrangeLight else Color.Transparent
    val contentColor = if (isSelected) ShawarmaOrange else TextDarkSecondary

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 28.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        color = bgColor
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = contentColor
        )
    }
}
