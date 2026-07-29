package com.sukashawarma.pos.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.sukashawarma.pos.presentation.components.POSTab
import com.sukashawarma.pos.presentation.components.SideNavRail
import com.sukashawarma.pos.presentation.dashboard.DashboardScreen
import com.sukashawarma.pos.presentation.dashboard.DashboardViewModel
import com.sukashawarma.pos.presentation.history.OrderHistoryScreen
import com.sukashawarma.pos.presentation.info_porsi.InfoPorsiScreen
import com.sukashawarma.pos.presentation.info_porsi.InfoPorsiViewModel
import com.sukashawarma.pos.presentation.history.OrderHistoryViewModel
import com.sukashawarma.pos.presentation.login.LoginScreen
import com.sukashawarma.pos.presentation.login.LoginViewModel
import com.sukashawarma.pos.presentation.menu_management.MenuManagementScreen
import com.sukashawarma.pos.presentation.menu_management.MenuManagementViewModel
import com.sukashawarma.pos.presentation.order_manual.POSManualOrderScreen
import com.sukashawarma.pos.presentation.order_manual.POSManualOrderViewModel
import com.sukashawarma.pos.presentation.reports.ReportsScreen
import com.sukashawarma.pos.presentation.reports.ReportsViewModel
import com.sukashawarma.pos.presentation.settings.SettingsScreen
import com.sukashawarma.pos.presentation.settings.SettingsViewModel
import com.sukashawarma.pos.presentation.shift.CloseShiftScreen
import com.sukashawarma.pos.presentation.shift.ShiftScreen
import com.sukashawarma.pos.presentation.shift.ShiftViewModel
import com.sukashawarma.pos.presentation.theme.CreamBackground
import com.sukashawarma.pos.presentation.theme.SukaShawarmaPOSTheme

import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import com.sukashawarma.pos.presentation.components.POSAdaptiveScaffold

class MainActivity : ComponentActivity() {
    private val loginViewModel: LoginViewModel by viewModels()
    private val dashboardViewModel: DashboardViewModel by viewModels()
    private val posManualOrderViewModel: POSManualOrderViewModel by viewModels()
    private val menuManagementViewModel: MenuManagementViewModel by viewModels()
    private val orderHistoryViewModel: OrderHistoryViewModel by viewModels()
    private val reportsViewModel: ReportsViewModel by viewModels()
    private val shiftViewModel: ShiftViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val infoPorsiViewModel: InfoPorsiViewModel by viewModels()

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op — alarm suara/realtime tetap jalan walau ditolak, hanya notifikasi sistem yang hilang */ }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            
            SukaShawarmaPOSTheme {
                val activeSession by loginViewModel.activeSession.collectAsState()

                LaunchedEffect(activeSession) {
                    activeSession?.let { session ->
                        dashboardViewModel.setSession(session.outletId, session.outletName, session.username)
                        posManualOrderViewModel.currentOutletId.value = session.outletId
                        posManualOrderViewModel.currentOutletName.value = session.outletName
                        posManualOrderViewModel.currentUsername.value = session.username
                        menuManagementViewModel.setOutlet(session.outletId)
                        orderHistoryViewModel.setOutlet(session.outletId)
                        shiftViewModel.setOutlet(session.outletId)
                        reportsViewModel.setOutlet(session.outletId)
                        infoPorsiViewModel.currentOutletId.value = session.outletId
                    }
                }

                if (activeSession == null) {
                    // Show Cashier Login & Outlet Selection Screen
                    LoginScreen(
                        viewModel = loginViewModel
                    )
                } else {
                    // Active Session -> Show Main POS Tablet Layout
                    val session = activeSession!!
                    var currentTab by remember { mutableStateOf(POSTab.DASHBOARD) }

                    POSAdaptiveScaffold(
                        windowSizeClass = windowSizeClass.widthSizeClass,
                        currentTab = currentTab,
                        onTabSelected = { currentTab = it },
                        outletName = session.outletName,
                        onLogoutClick = {
                            loginViewModel.logout()
                            dashboardViewModel.setSession("", "", "Kasir")
                            posManualOrderViewModel.currentOutletId.value = ""
                            menuManagementViewModel.setOutlet("")
                        }
                    ) {
                        Column(modifier = Modifier.fillMaxSize().background(CreamBackground)) {
                            // Admin Rules & Target Banner
                            com.sukashawarma.pos.presentation.components.BriefingBanner(outletId = session.outletId)
                            
                            Box(modifier = Modifier.weight(1f)) {
                                when (currentTab) {
                                    POSTab.DASHBOARD -> DashboardScreen(
                                        viewModel = dashboardViewModel,
                                        windowSizeClass = windowSizeClass.widthSizeClass,
                                        onNewOrderClick = { currentTab = POSTab.ORDER_MANUAL }
                                    )
                                    POSTab.ORDER_MANUAL -> POSManualOrderScreen(viewModel = posManualOrderViewModel)
                                    POSTab.INFO_PORSI -> InfoPorsiScreen(viewModel = infoPorsiViewModel)
                                    POSTab.MENU_MANAGEMENT -> MenuManagementScreen(viewModel = menuManagementViewModel)
                                    POSTab.SHIFT_PETTY_CASH -> ShiftScreen(
                                        viewModel = shiftViewModel,
                                        onNavigateToCloseShift = { currentTab = POSTab.SHIFT_CLOSE }
                                    )
                                    POSTab.SHIFT_CLOSE -> CloseShiftScreen(
                                        viewModel = shiftViewModel,
                                        onBack = { currentTab = POSTab.SHIFT_PETTY_CASH }
                                    )
                                    POSTab.HISTORI_BONUS -> OrderHistoryScreen(viewModel = orderHistoryViewModel)
                                    POSTab.KIOSK_CONTROL -> SettingsScreen(viewModel = settingsViewModel)
                                    POSTab.REPORTS -> ReportsScreen(viewModel = reportsViewModel)
                                    POSTab.SETTINGS -> SettingsScreen(viewModel = settingsViewModel)
                                    POSTab.PANDUAN -> SettingsScreen(viewModel = settingsViewModel)
                                    POSTab.STOK_OUTLET -> MenuManagementScreen(viewModel = menuManagementViewModel)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
