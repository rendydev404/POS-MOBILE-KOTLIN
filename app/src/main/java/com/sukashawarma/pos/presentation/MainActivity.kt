package com.sukashawarma.pos.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import com.sukashawarma.pos.domain.usecase.StokOutletLauncher
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
import com.sukashawarma.pos.presentation.components.OfflineIndicator
import com.sukashawarma.pos.data.remote.NetworkMonitor
import androidx.compose.ui.Alignment

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
    private val printerViewModel: com.sukashawarma.pos.presentation.printer.BluetoothPrinterViewModel by viewModels()

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op — alarm suara/realtime tetap jalan walau ditolak, hanya notifikasi sistem yang hilang */ }

    /**
     * Socket realtime bisa mati diam-diam saat layar tidur / app di background.
     * Tanpa pemulihan di sini, layar menampilkan data basi sampai heartbeat
     * menyadarinya (~70 detik) atau poll berkala jalan.
     */
    override fun onResume() {
        super.onResume()
        val session = loginViewModel.activeSession.value ?: return
        com.sukashawarma.pos.data.remote.realtime.POSRealtimeService.resume(this, session.outletId)
        com.sukashawarma.pos.data.remote.GlobalEventBus.orderSyncEvent.tryEmit(Unit)
        com.sukashawarma.pos.data.remote.GlobalEventBus.targetRefreshEvent.tryEmit(Unit)
        com.sukashawarma.pos.data.remote.GlobalEventBus.ownerMessageRefreshEvent.tryEmit(Unit)
        com.sukashawarma.pos.data.remote.GlobalEventBus.pettyCashEvent.tryEmit(Unit)
        com.sukashawarma.pos.data.remote.GlobalEventBus.gateRefreshEvent.tryEmit(Unit)
    }

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

                        // Tanpa ini tabel fcm_tokens tetap kosong dan edge function
                        // send-fcm balas "No tokens found": onNewToken hanya menyala
                        // sekali, biasanya sebelum kasir sempat login.
                        com.sukashawarma.pos.data.notification.FcmTokenRegistrar
                            .registerCurrentToken(session.staffId, session.outletId)
                    }
                }

                // Distribusi app ini lewat WhatsApp (bukan Play Store) — cek versi
                // terbaru sendiri tiap kali kasir buka app. Lihat AppUpdateManager.
                var updateManifest by remember { mutableStateOf<com.sukashawarma.pos.data.remote.dto.AppUpdateManifest?>(null) }
                LaunchedEffect(activeSession) {
                    if (activeSession != null) {
                        updateManifest = com.sukashawarma.pos.data.update.AppUpdateManager.checkForUpdate()
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
                    val isOnline by NetworkMonitor.isOnline.collectAsState()

                    // Sama seperti web (router.push('/kasir/reports?shift=closed')):
                    // begitu tutup shift sukses, pindah otomatis ke tab Laporan.
                    LaunchedEffect(Unit) {
                        shiftViewModel.navigateToReports.collect {
                            currentTab = POSTab.REPORTS
                        }
                    }

                    // "Stok Outlet" bukan layar native — ia melempar ke web stok
                    // (Chrome diprioritaskan) sambil membawa sesi Supabase, jadi
                    // kasir tidak login ulang. Lihat StokOutletLauncher.
                    val context = LocalContext.current
                    val scope = rememberCoroutineScope()
                    var isOpeningStok by remember { mutableStateOf(false) }
                    val lowStockCount by dashboardViewModel.lowStockCount.collectAsState()
                    var showPrinterDialog by remember { mutableStateOf(false) }

                    if (showPrinterDialog) {
                        com.sukashawarma.pos.presentation.printer.BluetoothPrinterDialog(
                            viewModel = printerViewModel,
                            onDismiss = { showPrinterDialog = false }
                        )
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        POSAdaptiveScaffold(
                            windowSizeClass = windowSizeClass.widthSizeClass,
                            currentTab = currentTab,
                            onTabSelected = { tab ->
                                if (tab != POSTab.STOK_OUTLET) {
                                    currentTab = tab
                                } else if (!isOpeningStok) {
                                    isOpeningStok = true
                                    scope.launch {
                                        val message = when (StokOutletLauncher.open(context)) {
                                            StokOutletLauncher.Result.Success -> null
                                            StokOutletLauncher.Result.NoSession ->
                                                "Sesi tidak bisa disiapkan. Login ulang lalu coba lagi."
                                            StokOutletLauncher.Result.NoBrowser ->
                                                "Tidak ada browser terpasang di perangkat ini."
                                        }
                                        message?.let {
                                            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                                        }
                                        isOpeningStok = false
                                    }
                                }
                            },
                            outletName = session.outletName,
                            isOnline = isOnline,
                            // Badge di menu "Stok Outlet" — sebelumnya selalu 0 karena
                            // nilainya tidak pernah dialirkan ke scaffold.
                            lowStockCount = lowStockCount,
                            onLogoutClick = {
                                loginViewModel.logout()
                                dashboardViewModel.setSession("", "", "Kasir")
                                posManualOrderViewModel.currentOutletId.value = ""
                                menuManagementViewModel.setOutlet("")
                            },
                            onPrinterClick = { showPrinterDialog = true }
                        ) {
                            Column(modifier = Modifier.fillMaxSize().background(CreamBackground)) {
                                // Admin Rules & Target Banner
                                com.sukashawarma.pos.presentation.components.BriefingBanner(outletId = session.outletId)
                                
                                Box(modifier = Modifier.weight(1f)) {
                                    when (currentTab) {
                                        POSTab.DASHBOARD -> DashboardScreen(
                                            viewModel = dashboardViewModel,
                                            printerViewModel = printerViewModel,
                                            windowSizeClass = windowSizeClass.widthSizeClass,
                                            onNewOrderClick = { currentTab = POSTab.ORDER_MANUAL }
                                        )
                                        POSTab.ORDER_MANUAL -> POSManualOrderScreen(
                                            viewModel = posManualOrderViewModel, 
                                            printerViewModel = printerViewModel,
                                            onBackClick = { currentTab = POSTab.DASHBOARD }
                                        )
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
                                        // Ditangani di onTabSelected (buka web stok), currentTab tidak pernah bernilai ini.
                                        POSTab.STOK_OUTLET -> Unit
                                    }
                                }
                            }
                        }

                        OfflineIndicator(
                            isOffline = !isOnline,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }

                    updateManifest?.let { manifest ->
                        val downloadState by com.sukashawarma.pos.data.update.AppUpdateManager.downloadState.collectAsState()
                        val downloadProgress by com.sukashawarma.pos.data.update.AppUpdateManager.downloadProgress.collectAsState()

                        com.sukashawarma.pos.presentation.components.AppUpdateDialog(
                            manifest = manifest,
                            downloadState = downloadState,
                            downloadProgress = downloadProgress,
                            onDismiss = { updateManifest = null },
                            onStartDownload = {
                                if (com.sukashawarma.pos.data.update.AppUpdateManager.canRequestInstall(context)) {
                                    com.sukashawarma.pos.data.update.AppUpdateManager.startDownload(context, manifest)
                                } else {
                                    context.startActivity(
                                        com.sukashawarma.pos.data.update.AppUpdateManager.installIntentSettings(context)
                                    )
                                }
                            },
                            onInstall = {
                                com.sukashawarma.pos.data.update.AppUpdateManager.installDownloadedApk(context)
                            }
                        )
                    }
                }
            }
        }
    }
}
