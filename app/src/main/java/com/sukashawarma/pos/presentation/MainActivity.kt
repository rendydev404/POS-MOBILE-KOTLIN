package com.sukashawarma.pos.presentation

import android.Manifest
import android.content.Intent
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
import com.sukashawarma.pos.presentation.shift.ShiftScreen
import com.sukashawarma.pos.presentation.shift.ShiftViewModel
import com.sukashawarma.pos.presentation.theme.CreamBackground
import com.sukashawarma.pos.presentation.theme.SukaShawarmaPOSTheme

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_ORDER_ID = "order_id"
    }

    private val loginViewModel: LoginViewModel by viewModels()
    private val dashboardViewModel: DashboardViewModel by viewModels()
    private val posManualOrderViewModel: POSManualOrderViewModel by viewModels()
    private val menuManagementViewModel: MenuManagementViewModel by viewModels()
    private val orderHistoryViewModel: OrderHistoryViewModel by viewModels()
    private val reportsViewModel: ReportsViewModel by viewModels()
    private val shiftViewModel: ShiftViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    // Bumped every time a notification tap should force the UI back to
    // Dashboard — a plain nullable order id wouldn't retrigger the
    // LaunchedEffect below if the same order is tapped twice in a row.
    private val pendingNotificationOrderId = mutableStateOf<String?>(null)

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op — alarm suara/realtime tetap jalan walau ditolak, hanya notifikasi sistem yang hilang */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        handleNotificationIntent(intent)
        setContent {
            SukaShawarmaPOSTheme {
                val activeSession by loginViewModel.activeSession.collectAsState()

                if (activeSession == null) {
                    // Show Cashier Login & Outlet Selection Screen
                    LoginScreen(
                        viewModel = loginViewModel,
                        onLoginSuccess = { session ->
                            dashboardViewModel.setSession(session.outletId, session.outletName, session.username)
                            posManualOrderViewModel.currentOutletId.value = session.outletId
                            orderHistoryViewModel.setOutlet(session.outletId)
                            shiftViewModel.setOutlet(session.outletId)
                            reportsViewModel.setOutlet(session.outletId)
                        }
                    )
                } else {
                    // Active Session -> Show Main POS Tablet Layout
                    val session = activeSession!!
                    var currentTab by remember { mutableStateOf(POSTab.DASHBOARD) }
                    val notifiedOrderId by pendingNotificationOrderId

                    LaunchedEffect(notifiedOrderId) {
                        val orderId = notifiedOrderId ?: return@LaunchedEffect
                        currentTab = POSTab.DASHBOARD
                        dashboardViewModel.highlightOrder(orderId)
                        pendingNotificationOrderId.value = null
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(CreamBackground)
                    ) {
                        // Persistent Side Navigation Bar (100% Matching Website Screenshot)
                        SideNavRail(
                            currentTab = currentTab,
                            onTabSelected = { currentTab = it },
                            outletName = session.outletName,
                            onLogoutClick = {
                                loginViewModel.logout()
                                dashboardViewModel.setSession("", "", "Kasir")
                                posManualOrderViewModel.currentOutletId.value = ""
                            }
                        )

                        // Main Content Area
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(1f)
                                .background(CreamBackground)
                        ) {
                            when (currentTab) {
                                POSTab.DASHBOARD -> DashboardScreen(
                                    viewModel = dashboardViewModel,
                                    onNewOrderClick = { currentTab = POSTab.INFO_PORSI }
                                )
                                POSTab.INFO_PORSI -> POSManualOrderScreen(viewModel = posManualOrderViewModel)
                                POSTab.MENU_MANAGEMENT -> MenuManagementScreen(viewModel = menuManagementViewModel)
                                POSTab.SHIFT_PETTY_CASH, POSTab.SHIFT_CLOSE -> ShiftScreen(viewModel = shiftViewModel)
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val orderId = intent?.getStringExtra(EXTRA_ORDER_ID) ?: return
        pendingNotificationOrderId.value = orderId
    }
}
