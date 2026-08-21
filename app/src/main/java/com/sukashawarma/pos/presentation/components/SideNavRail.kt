package com.sukashawarma.pos.presentation.components

import androidx.compose.animation.AnimatedVisibility

import androidx.compose.animation.core.animateFloatAsState

import androidx.compose.animation.core.animateDpAsState

import androidx.compose.animation.core.tween

import androidx.compose.animation.core.spring

import androidx.compose.animation.core.Spring

import androidx.compose.foundation.Image

import androidx.compose.foundation.background

import androidx.compose.foundation.border

import androidx.compose.foundation.clickable

import androidx.compose.foundation.layout.*

import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.foundation.verticalScroll

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.material.icons.automirrored.filled.ExitToApp

import androidx.compose.material.icons.filled.*

import androidx.compose.material3.*

import androidx.compose.runtime.*

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.draw.clip

import androidx.compose.ui.graphics.graphicsLayer

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.graphics.vector.ImageVector

import androidx.compose.ui.layout.layout

import androidx.compose.ui.res.painterResource

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.text.style.TextAlign

import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.ui.unit.dp

import androidx.compose.ui.unit.sp

import com.sukashawarma.pos.R

import com.sukashawarma.pos.BuildConfig

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

// Data structure mimicking the links array in KasirNav.tsx

data class NavMenuItem(

    val label: String,

    val icon: ImageVector,

    val tab: POSTab? = null,

    val subItems: List<NavMenuSubItem>? = null,

    val badgeCount: Int = 0

)

data class NavMenuSubItem(

    val label: String,

    val tab: POSTab

)

val POS_MENU_LINKS = listOf(

    NavMenuItem(

        label = "Order",

        icon = Icons.Default.Notifications,

        subItems = listOf(

            NavMenuSubItem("Pesanan Kasir", POSTab.DASHBOARD),

            NavMenuSubItem("Info Porsi & Stok", POSTab.INFO_PORSI)

        )

    ),

    NavMenuItem(label = "Manajemen Menu", icon = Icons.Default.Fastfood, tab = POSTab.MENU_MANAGEMENT),

    NavMenuItem(

        label = "Shift",

        icon = Icons.Default.AccountBalanceWallet,

        subItems = listOf(

            NavMenuSubItem("Petty Cash", POSTab.SHIFT_PETTY_CASH),

            NavMenuSubItem("Tutup Shift", POSTab.SHIFT_CLOSE)

        )

    ),

    NavMenuItem(label = "Histori & Bonus", icon = Icons.Default.Assignment, tab = POSTab.HISTORI_BONUS),

    NavMenuItem(label = "Control Dev..", icon = Icons.Default.Tv, tab = POSTab.KIOSK_CONTROL),

    NavMenuItem(label = "Laporan", icon = Icons.Default.BarChart, tab = POSTab.REPORTS),

    NavMenuItem(label = "Screen Settings", icon = Icons.Default.Image, tab = POSTab.SETTINGS),

    NavMenuItem(label = "Panduan", icon = Icons.Default.MenuBook, tab = POSTab.PANDUAN)

)

val ActiveBgColor = Color(0xFFF29744)

val ActiveTextColor = Color(0xFF643400)

val ActiveBorderColor = Color(0xFF904D00)

val InactiveTextColor = Color(0xFF544437)

@Composable

fun SideNavRail(

    currentTab: POSTab,

    onTabSelected: (POSTab) -> Unit,

    outletName: String = "SUKA SHAWARMA BNR",

    lowStockCount: Int = 0,

    isOnline: Boolean = true,

    onLogoutClick: () -> Unit = {},

    onPrinterClick: () -> Unit = {},

    isCollapsed: Boolean = false,

    modifier: Modifier = Modifier

) {

    val scrollState = rememberScrollState()

    // Add badge count to specific items (like Stok Outlet if we added it, but here Stok is outside)

    // Sengaja TIDAK memakai `by`: nilainya dibaca di dalam lambda `layout` di bawah.
    //
    // Membaca State animasi saat komposisi membuat SELURUH sidebar ini (semua item
    // nav, dropdown, footer) dikomposisi ulang di setiap frame animasi — itulah
    // sebab show/hide terasa berat. Dengan pembacaan ditunda ke fase layout, yang
    // berjalan tiap frame hanyalah pengukuran, komposisinya dilewati.
    val widthAnim = animateDpAsState(
        targetValue = if (isCollapsed) 80.dp else 256.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "width"
    )

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .layout { measurable, constraints ->
                val w = widthAnim.value.roundToPx()
                val placeable = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            },
        color = CreamSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, CreamBorder)
    ) {

        Column(

            modifier = Modifier.fillMaxSize()

        ) {

            // Header (Logo & Name)

            Column(

                modifier = Modifier

                    .fillMaxWidth()

                    .padding(if (isCollapsed) 8.dp else 24.dp),

                horizontalAlignment = Alignment.CenterHorizontally

            ) {

                Box(contentAlignment = Alignment.TopEnd) {

                    Image(

                        painter = painterResource(id = R.mipmap.ic_launcher),

                        contentDescription = "Logo",

                        modifier = Modifier

                            .size(if (isCollapsed) 40.dp else 52.dp)

                            .clip(RoundedCornerShape(12.dp))

                    )

                    if (isCollapsed) {

                        Box(

                            modifier = Modifier

                                .offset(x = 2.dp, y = (-2).dp)

                                .size(10.dp)

                                .clip(CircleShape)

                                .background(if (isOnline) Color(0xFF22C55E) else Color(0xFFEF4444))

                                .border(1.5.dp, CreamSurface, CircleShape)

                        )

                    }

                }

                if (!isCollapsed) {

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(

                        text = "Hai, Kasir",

                        style = MaterialTheme.typography.titleMedium,

                        fontWeight = FontWeight.Bold,

                        color = TextDarkPrimary,

                        textAlign = TextAlign.Center

                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Column(

                        horizontalAlignment = Alignment.CenterHorizontally,

                        modifier = Modifier

                            .background(Color(0xFFE9E1D8), RoundedCornerShape(16.dp))

                            .padding(horizontal = 10.dp, vertical = 6.dp)

                    ) {

                        Row(verticalAlignment = Alignment.CenterVertically) {

                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(if (isOnline) Color(0xFF22C55E) else Color(0xFFEF4444)))

                            Spacer(modifier = Modifier.width(6.dp))

                            Text(

                                text = if (isOnline) "ONLINE" else "OFFLINE",

                                fontSize = 9.sp,

                                fontWeight = FontWeight.Bold,

                                color = ActiveBorderColor

                            )

                        }

                        Text(

                            text = outletName.uppercase(),

                            fontSize = 10.sp,

                            fontWeight = FontWeight.Bold,

                            color = ActiveBorderColor,

                            textAlign = TextAlign.Center

                        )

                    }

                }

            }

            HorizontalDivider(color = CreamBorder)

            // Menu Items

            Column(

                modifier = Modifier

                    .weight(1f)

                    .verticalScroll(scrollState)

                    .padding(if (isCollapsed) 8.dp else 16.dp),

                verticalArrangement = Arrangement.spacedBy(8.dp)

            ) {

                // Hanya satu popover/accordion boleh aktif pada satu waktu,
                // sama seperti `openDropdown` di KasirNav web.
                var openDropdownLabel by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(isCollapsed) {
                    if (!isCollapsed) openDropdownLabel = null
                }

                POS_MENU_LINKS.forEach { menuItem ->

                    val hasSubItems = !menuItem.subItems.isNullOrEmpty()

                    val isAnySubActive = hasSubItems && menuItem.subItems!!.any { it.tab == currentTab }

                    val isActive = (!hasSubItems && menuItem.tab == currentTab) || isAnySubActive

                    val isDropdownExpanded = openDropdownLabel == menuItem.label

                    Box {

                        // Main Item Button

                        Row(

                            modifier = Modifier

                                .fillMaxWidth()

                                .clip(RoundedCornerShape(12.dp))

                                .background(if (isActive && (!hasSubItems || isCollapsed || !isDropdownExpanded)) ActiveBgColor else Color.Transparent)

                                .clickable {

                                    if (hasSubItems) {

                                        openDropdownLabel = if (isDropdownExpanded) null else menuItem.label

                                    } else {

                                        menuItem.tab?.let { onTabSelected(it) }

                                    }

                                }

                                .padding(if (isCollapsed) 12.dp else 10.dp),

                            verticalAlignment = Alignment.CenterVertically,

                            horizontalArrangement = if (isCollapsed) Arrangement.Center else Arrangement.SpaceBetween

                        ) {

                            Row(verticalAlignment = Alignment.CenterVertically) {

                                Icon(

                                    imageVector = menuItem.icon,

                                    contentDescription = menuItem.label,

                                    tint = if (isActive && (!hasSubItems || isCollapsed || !isDropdownExpanded)) ActiveTextColor else InactiveTextColor,

                                    modifier = Modifier.size(18.dp)

                                )

                                if (!isCollapsed) {

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Text(

                                        text = menuItem.label,

                                        color = if (isActive && (!hasSubItems || !isDropdownExpanded)) ActiveTextColor else InactiveTextColor,

                                        fontWeight = FontWeight.Bold,

                                        fontSize = 15.sp,

                                        maxLines = 1,

                                        overflow = TextOverflow.Ellipsis

                                    )

                                }

                            }

                            if (!isCollapsed && hasSubItems) {

                                val rotation = animateFloatAsState(if (isDropdownExpanded) 180f else 0f, label = "rotate")

                                Icon(

                                    Icons.Default.KeyboardArrowDown,

                                    contentDescription = null,

                                    tint = InactiveTextColor,

                                    // graphicsLayer membaca nilai di fase menggambar,
                                    // jadi rotasi ini tidak lagi mengomposisi ulang
                                    // baris menu tiap frame seperti Modifier.rotate().
                                    modifier = Modifier.size(14.dp).graphicsLayer { rotationZ = rotation.value }

                                )

                            }

                        }

                        // Left border indicator if active

                        if (isActive && (!hasSubItems || isCollapsed || !isDropdownExpanded)) {

                            Box(

                                modifier = Modifier

                                    .align(Alignment.CenterStart)

                                    .width(4.dp)

                                    .height(48.dp)

                                    .background(ActiveBorderColor, RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))

                            )

                        }

                        // Dropdown for Collapsed Mode

                        if (isCollapsed && hasSubItems) {
                            DropdownMenu(
                                expanded = isDropdownExpanded,
                                onDismissRequest = { openDropdownLabel = null },
                                offset = androidx.compose.ui.unit.DpOffset(8.dp, 0.dp),
                                modifier = Modifier
                                    .width(192.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFFFFF8F1))
                                    .border(1.dp, Color(0xFFD9C2B2), RoundedCornerShape(16.dp))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = menuItem.label.uppercase(),
                                    color = Color(0xFFA48E7F),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                menuItem.subItems!!.forEach { subItem ->
                                    val isSubActive = currentTab == subItem.tab
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isSubActive) ActiveBgColor else Color.Transparent)
                                            .clickable {
                                                onTabSelected(subItem.tab)
                                                openDropdownLabel = null
                                            }
                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = subItem.label,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (isSubActive) Color.White else InactiveTextColor
                                        )
                                    }
                                }
                            }
                        }

                    }

                    // Expanded Mode Accordion

                    if (!isCollapsed && hasSubItems) {

                        AnimatedVisibility(visible = isDropdownExpanded) {

                            Column(modifier = Modifier.padding(start = 44.dp, end = 8.dp)) {

                                menuItem.subItems!!.forEach { subItem ->

                                    val subActive = currentTab == subItem.tab

                                    Row(

                                        modifier = Modifier

                                            .fillMaxWidth()

                                            .clip(RoundedCornerShape(8.dp))

                                            .background(if (subActive) ActiveBgColor else Color.Transparent)

                                            .clickable { onTabSelected(subItem.tab) }

                                            .padding(horizontal = 12.dp, vertical = 8.dp)

                                    ) {

                                        Text(

                                            text = subItem.label,

                                            fontWeight = FontWeight.SemiBold,

                                            fontSize = 14.sp,

                                            color = if (subActive) Color.White else InactiveTextColor

                                        )

                                    }

                                }

                            }

                        }

                    }

                }

                // Stok Outlet Button

                val stokActive = currentTab == POSTab.STOK_OUTLET

                Box(

                    modifier = Modifier

                        .fillMaxWidth()

                        .clip(RoundedCornerShape(12.dp))

                        .background(if (stokActive) ActiveBgColor else Color.Transparent)

                        .clickable { onTabSelected(POSTab.STOK_OUTLET) }

                        .padding(if (isCollapsed) 12.dp else 10.dp)

                ) {

                    Row(

                        modifier = Modifier.align(if (isCollapsed) Alignment.Center else Alignment.CenterStart),

                        verticalAlignment = Alignment.CenterVertically

                    ) {

                        Icon(

                            imageVector = Icons.Default.Inventory2,

                            contentDescription = "Stok Outlet",

                            tint = if (stokActive) ActiveTextColor else InactiveTextColor,

                            modifier = Modifier.size(20.dp)

                        )

                        if (!isCollapsed) {

                            Spacer(modifier = Modifier.width(12.dp))

                            Text(

                                text = "Stok Outlet",

                                color = if (stokActive) ActiveTextColor else InactiveTextColor,

                                fontWeight = FontWeight.Bold,

                                fontSize = 15.sp,

                                maxLines = 1,

                                overflow = TextOverflow.Ellipsis

                            )

                        }

                    }

                    if (lowStockCount > 0) {

                        Box(

                            modifier = Modifier

                                .align(if (isCollapsed) Alignment.TopEnd else Alignment.CenterEnd)

                                .offset(x = if (isCollapsed) 4.dp else 0.dp, y = if (isCollapsed) (-4).dp else 0.dp)

                                .size(20.dp)

                                .background(Color.Red, CircleShape),

                            contentAlignment = Alignment.Center

                        ) {

                            Text(

                                text = lowStockCount.toString(),

                                color = Color.White,

                                fontSize = 10.sp,

                                fontWeight = FontWeight.Bold

                            )

                        }

                    }

                }

            }

            HorizontalDivider(color = CreamBorder)

            // Footer Actions

            Column(

                modifier = Modifier

                    .fillMaxWidth()

                    .padding(if (isCollapsed) 8.dp else 16.dp),

                verticalArrangement = Arrangement.spacedBy(8.dp)

            ) {

                // Portal

                Row(

                    modifier = Modifier

                        .fillMaxWidth()

                        .clip(RoundedCornerShape(12.dp))

                        .clickable { }

                        .padding(if (isCollapsed) 12.dp else 10.dp),

                    horizontalArrangement = if (isCollapsed) Arrangement.Center else Arrangement.Start,

                    verticalAlignment = Alignment.CenterVertically

                ) {

                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Portal", tint = InactiveTextColor, modifier = Modifier.size(18.dp))

                    if (!isCollapsed) {

                        Spacer(modifier = Modifier.width(12.dp))

                        Text("Portal", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = InactiveTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)

                    }

                }

                // Printer

                Row(

                    modifier = Modifier

                        .fillMaxWidth()

                        .clip(RoundedCornerShape(12.dp))

                        .clickable(onClick = onPrinterClick)

                        .padding(if (isCollapsed) 12.dp else 10.dp),

                    horizontalArrangement = if (isCollapsed) Arrangement.Center else Arrangement.Start,

                    verticalAlignment = Alignment.CenterVertically

                ) {

                    Icon(Icons.Default.Print, contentDescription = "Printer", tint = InactiveTextColor, modifier = Modifier.size(18.dp))

                    if (!isCollapsed) {

                        Spacer(modifier = Modifier.width(12.dp))

                        Text("Koneksi Printer", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = InactiveTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)

                    }

                }

                // Logout

                Row(

                    modifier = Modifier

                        .fillMaxWidth()

                        .clip(RoundedCornerShape(12.dp))

                        .clickable(onClick = onLogoutClick)

                        .padding(if (isCollapsed) 12.dp else 10.dp),

                    horizontalArrangement = if (isCollapsed) Arrangement.Center else Arrangement.Start,

                    verticalAlignment = Alignment.CenterVertically

                ) {

                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Keluar", tint = Color(0xFFA43C26), modifier = Modifier.size(18.dp))

                    if (!isCollapsed) {

                        Spacer(modifier = Modifier.width(12.dp))

                        Text("Keluar", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFA43C26), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)

                    }

                }

                // Bukti versi aktif setelah updater menerapkan APK dan membuka
                // kembali aplikasi. Label ini sengaja kecil agar tidak mengganggu
                // operasional, tetapi memudahkan verifikasi update otomatis.
                Text(

                        text = if (isCollapsed) "v${BuildConfig.VERSION_NAME}" else "POS v${BuildConfig.VERSION_NAME}",

                        color = InactiveTextColor.copy(alpha = 0.65f),

                        fontSize = 10.sp,

                        fontWeight = FontWeight.Medium,

                        modifier = Modifier

                            .fillMaxWidth()

                            .padding(horizontal = 10.dp),

                        textAlign = TextAlign.Center

                    )

            }

        }

    }

}
