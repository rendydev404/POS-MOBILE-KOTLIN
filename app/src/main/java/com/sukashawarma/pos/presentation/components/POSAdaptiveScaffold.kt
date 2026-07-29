package com.sukashawarma.pos.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.sukashawarma.pos.presentation.theme.*

@Composable
fun POSAdaptiveScaffold(
    windowSizeClass: WindowWidthSizeClass,
    currentTab: POSTab,
    onTabSelected: (POSTab) -> Unit,
    outletName: String,
    lowStockCount: Int = 0,
    onLogoutClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    when (windowSizeClass) {
        WindowWidthSizeClass.Compact -> {
            // HP: Bottom Bar dengan 4 Menu + Tombol "Lainnya" untuk buka Sidebar (Drawer)
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        modifier = Modifier.width(260.dp),
                        drawerContainerColor = CreamSurface
                    ) {
                        SideNavRail(
                            currentTab = currentTab,
                            onTabSelected = {
                                onTabSelected(it)
                                scope.launch { drawerState.close() }
                            },
                            outletName = outletName,
                            lowStockCount = lowStockCount,
                            onLogoutClick = onLogoutClick,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            ) {
                Scaffold(
                    bottomBar = {
                        POSBottomBar(
                            currentTab = currentTab,
                            onTabSelected = onTabSelected,
                            onMoreClick = { scope.launch { drawerState.open() } }
                        )
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        content()
                    }
                }
            }
        }
        WindowWidthSizeClass.Medium -> {
            // Tablet Portrait: Navigation Rail (Ikon Saja)
            Row(modifier = Modifier.fillMaxSize()) {
                POSNavigationRail(
                    currentTab = currentTab,
                    onTabSelected = onTabSelected,
                    lowStockCount = lowStockCount,
                    onLogoutClick = onLogoutClick
                )
                Box(modifier = Modifier.weight(1f)) {
                    content()
                }
            }
        }
        else -> {
            // Tablet Landscape / Layar Lebar: Sidebar Penuh
            Row(modifier = Modifier.fillMaxSize()) {
                SideNavRail(
                    currentTab = currentTab,
                    onTabSelected = onTabSelected,
                    outletName = outletName,
                    lowStockCount = lowStockCount,
                    onLogoutClick = onLogoutClick
                )
                Box(modifier = Modifier.weight(1f)) {
                    content()
                }
            }
        }
    }
}

@Composable
fun POSBottomBar(
    currentTab: POSTab,
    onTabSelected: (POSTab) -> Unit,
    onMoreClick: () -> Unit
) {
    NavigationBar(
        containerColor = CreamSurface,
        contentColor = TextDarkPrimary,
        tonalElevation = 8.dp
    ) {
        val bottomTabs = listOf(
            Triple(POSTab.DASHBOARD, "Order", Icons.Default.Dashboard),
            Triple(POSTab.ORDER_MANUAL, "Manual", Icons.Default.AddCircle),
            Triple(POSTab.INFO_PORSI, "Porsi", Icons.Default.Inventory),
            Triple(POSTab.REPORTS, "Laporan", Icons.Default.BarChart)
        )

        bottomTabs.forEach { (tab, label, icon) ->
            NavigationBarItem(
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label, fontSize = 10.sp) },
                selected = currentTab == tab,
                onClick = { onTabSelected(tab) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = ShawarmaOrange,
                    selectedTextColor = ShawarmaOrange,
                    indicatorColor = ShawarmaOrangeLight,
                    unselectedIconColor = TextDarkSecondary,
                    unselectedTextColor = TextDarkSecondary
                )
            )
        }

        NavigationBarItem(
            icon = { Icon(Icons.Default.Menu, contentDescription = "Lainnya") },
            label = { Text("Lainnya", fontSize = 10.sp) },
            selected = false,
            onClick = onMoreClick,
            colors = NavigationBarItemDefaults.colors(
                unselectedIconColor = TextDarkSecondary,
                unselectedTextColor = TextDarkSecondary
            )
        )
    }
}

@Composable
fun POSNavigationRail(
    currentTab: POSTab,
    onTabSelected: (POSTab) -> Unit,
    lowStockCount: Int,
    onLogoutClick: () -> Unit
) {
    NavigationRail(
        containerColor = CreamSurface,
        header = {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = com.sukashawarma.pos.R.mipmap.ic_launcher),
                contentDescription = "Logo",
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .padding(top = 12.dp, bottom = 8.dp)
            )
        },
        modifier = Modifier.width(80.dp)
    ) {
        val railTabs = listOf(
            Triple(POSTab.DASHBOARD, "Order", Icons.Default.Dashboard),
            Triple(POSTab.ORDER_MANUAL, "Manual", Icons.Default.AddCircle),
            Triple(POSTab.MENU_MANAGEMENT, "Menu", Icons.Default.Fastfood),
            Triple(POSTab.SHIFT_PETTY_CASH, "Shift", Icons.Default.AccountBalanceWallet),
            Triple(POSTab.REPORTS, "Laporan", Icons.Default.BarChart),
            Triple(POSTab.STOK_OUTLET, "Stok", Icons.Default.Inventory2)
        )

        railTabs.forEach { (tab, label, icon) ->
            NavigationRailItem(
                icon = {
                    if (tab == POSTab.STOK_OUTLET && lowStockCount > 0) {
                        BadgedBox(badge = { Badge(containerColor = MarqueeRed) { Text("$lowStockCount", color = Color.White) } }) {
                            Icon(icon, contentDescription = label)
                        }
                    } else {
                        Icon(icon, contentDescription = label)
                    }
                },
                label = { Text(label, fontSize = 10.sp, maxLines = 1) },
                selected = currentTab == tab,
                onClick = { onTabSelected(tab) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = ShawarmaOrange,
                    selectedTextColor = ShawarmaOrange,
                    indicatorColor = ShawarmaOrangeLight,
                    unselectedIconColor = TextDarkSecondary,
                    unselectedTextColor = TextDarkSecondary
                )
            )
        }

        Spacer(modifier = Modifier.weight(1f))
        
        NavigationRailItem(
            icon = { Icon(Icons.Default.ExitToApp, contentDescription = "Keluar") },
            label = { Text("Keluar", fontSize = 10.sp) },
            selected = false,
            onClick = onLogoutClick,
            colors = NavigationRailItemDefaults.colors(
                unselectedIconColor = MarqueeRed,
                unselectedTextColor = MarqueeRed
            )
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}
