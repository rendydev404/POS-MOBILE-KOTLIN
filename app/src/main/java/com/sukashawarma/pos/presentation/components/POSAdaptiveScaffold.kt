package com.sukashawarma.pos.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import androidx.compose.foundation.border
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
    isOnline: Boolean = true,
    onLogoutClick: () -> Unit,
    onPrinterClick: () -> Unit = {},
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
                            isOnline = isOnline,
                            onLogoutClick = onLogoutClick,
                            onPrinterClick = {
                                onPrinterClick()
                                scope.launch { drawerState.close() }
                            },
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
        WindowWidthSizeClass.Medium, WindowWidthSizeClass.Expanded -> {
            // Tablet: Collapsible Sidebar
            //
            // `isSidebarCollapsed` disimpan sebagai MutableState dan TIDAK dibaca di
            // sini. Dulu dibaca langsung di badan komposabel ini — dan karena
            // `content()` juga dipanggil di sini, setiap kali sidebar dibuka/tutup
            // SELURUH isi halaman (Dashboard beserta semua kartu pesanan) ikut
            // dikomposisi ulang. Pembacaannya sekarang terkurung di [SidebarSection],
            // sehingga toggle hanya membatalkan sidebar saja.
            val isSidebarCollapsed = remember {
                mutableStateOf(windowSizeClass == WindowWidthSizeClass.Medium)
            }

            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.zIndex(1f)) {
                    SidebarSection(
                        isCollapsed = isSidebarCollapsed,
                        currentTab = currentTab,
                        onTabSelected = onTabSelected,
                        outletName = outletName,
                        lowStockCount = lowStockCount,
                        isOnline = isOnline,
                        onLogoutClick = onLogoutClick,
                        onPrinterClick = onPrinterClick
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    content()
                }
            }
        }
    }
}

/**
 * Sidebar beserta tombol toggle-nya.
 *
 * Ini satu-satunya tempat `isCollapsed.value` dibaca, jadi menekan toggle hanya
 * membatalkan komposisi bagian ini — bukan seluruh halaman di sebelahnya.
 * Tampilannya sama persis dengan sebelumnya.
 */
@Composable
private fun BoxScope.SidebarSection(
    isCollapsed: MutableState<Boolean>,
    currentTab: POSTab,
    onTabSelected: (POSTab) -> Unit,
    outletName: String,
    lowStockCount: Int,
    isOnline: Boolean,
    onLogoutClick: () -> Unit,
    onPrinterClick: () -> Unit
) {
    val collapsed = isCollapsed.value

    SideNavRail(
        currentTab = currentTab,
        onTabSelected = onTabSelected,
        outletName = outletName,
        lowStockCount = lowStockCount,
        isOnline = isOnline,
        onLogoutClick = onLogoutClick,
        onPrinterClick = onPrinterClick,
        isCollapsed = collapsed
    )

    Box(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .offset(x = 12.dp)
            .size(24.dp)
            .background(ShawarmaOrange, CircleShape)
            .border(1.dp, CreamBorder, CircleShape)
            .clickable { isCollapsed.value = !isCollapsed.value },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (collapsed) Icons.Default.ChevronRight else Icons.Default.ChevronLeft,
            contentDescription = "Toggle Sidebar",
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
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
