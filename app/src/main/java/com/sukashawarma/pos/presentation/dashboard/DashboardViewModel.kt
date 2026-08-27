package com.sukashawarma.pos.presentation.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sukashawarma.pos.POSApplication
import com.sukashawarma.pos.data.bluetooth.BluetoothPrinterManager
import com.sukashawarma.pos.data.local.entity.LocalOrderEntity
import com.sukashawarma.pos.data.notification.OrderAlertPlayer
import com.sukashawarma.pos.data.remote.SupabaseClient
import com.sukashawarma.pos.data.remote.dto.OrderDto
import com.sukashawarma.pos.data.remote.dto.PrintLayoutDto
import com.sukashawarma.pos.data.remote.realtime.OrderRealtimeManager
import com.sukashawarma.pos.data.remote.realtime.StockRealtimeManager
import com.sukashawarma.pos.domain.model.StockAlert
import com.sukashawarma.pos.domain.model.StockAlertStatus
import com.sukashawarma.pos.domain.model.toStockAlerts
import kotlinx.coroutines.Job
import com.sukashawarma.pos.data.sync.OrderSyncEngine
import com.sukashawarma.pos.data.remote.NetworkMonitor
import com.sukashawarma.pos.domain.model.*
import com.sukashawarma.pos.domain.usecase.PrintReceiptUseCase
import com.sukashawarma.pos.domain.usecase.OrderStatusUpdateGuard
import com.sukashawarma.pos.domain.usecase.OrderStatusUpdatePolicy
import com.sukashawarma.pos.domain.usecase.OrderSyncRetryPolicy
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        /**
         * Cakupan cermin Room di [syncOrdersFromServer], dalam hari (termasuk hari ini).
         *
         * Yang benar-benar dilayani cache lokal adalah pesanan berjalan di dashboard
         * dan laci kasir shift — shift terpanjang pun hanya melewati satu tengah
         * malam, jadi seminggu sudah jauh lebih dari cukup. Diambil dari pengukuran
         * langsung ke outlet tersibuk: 7 hari ≈ 540 pesanan (±0,8 MB), sementara
         * tanpa batas ≈ 1.000 pesanan (±1,5 MB) pada tiap event realtime.
         */
        const val SYNC_WINDOW_DAYS = 7

        /** Jaring pengaman kalau satu outlet meledak transaksinya dalam rentang di atas. */
        const val SYNC_ROW_LIMIT = "2000"
    }

    private val database = (application as POSApplication).database
    private val orderDao = database.orderDao()
    private val api = SupabaseClient.api
    private val gson = Gson()
    private val printReceiptUseCase = PrintReceiptUseCase()
    val printerManager = BluetoothPrinterManager
    private val alertPlayer = OrderAlertPlayer(application)
    private val syncEngine = OrderSyncEngine(orderDao, api)
    private val statusUpdateGuard = OrderStatusUpdateGuard()
    val highlightedOrderId = MutableStateFlow<String?>(null)
    private var highlightClearJob: Job? = null

    private val _updatingOrderIds = MutableStateFlow<Set<String>>(emptySet())
    val updatingOrderIds: StateFlow<Set<String>> = _updatingOrderIds.asStateFlow()
    private val _statusUpdateErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val statusUpdateErrors: SharedFlow<String> = _statusUpdateErrors.asSharedFlow()

    val currentOutletId = MutableStateFlow("")
    val currentOutletName = MutableStateFlow("")
    val currentCashierName = MutableStateFlow("Kasir")
    val outlets = MutableStateFlow<List<Pair<String, String>>>(emptyList())

    /** Fokus singkat pada kartu order yang dibuka dari tap notifikasi push. */
    fun highlightOrder(orderId: String) {
        highlightedOrderId.value = orderId
        highlightClearJob?.cancel()
        highlightClearJob = viewModelScope.launch {
            delay(5_000)
            highlightedOrderId.value = null
        }
    }

    /**
     * Detak per menit supaya batas "hari ini" ikut dihitung ulang saat lewat
     * tengah malam, meski tidak ada pesanan baru yang masuk.
     *
     * `LocalDate.now()` di [omzetKotorHariIni] dan [completedOrders] dulu hanya
     * dievaluasi ulang saat Room memancarkan baris baru — dan `insertOrders`
     * sengaja tidak menulis baris yang isinya identik (lihat [syncOrdersFromServer]),
     * jadi tablet yang menyala semalaman tanpa transaksi baru tetap menampilkan
     * "hari ini" versi kemarin sampai ada perubahan data.
     */
    private val dayTicker: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(60_000)
        }
    }

    /** Angka RPC saat online; null berarti "belum ada / lagi offline", jatuh balik ke hitungan Room. */
    private val _omzetServerOverride = MutableStateFlow<Double?>(null)

    /**
     * Omzet kotor hari ini: jumlah subtotal item dari pesanan `COMPLETED`, zona Jakarta.
     *
     * Dulu ini menjumlahkan `totalAmount` (nilai setelah diskon) dan diberi label
     * "omzet", sehingga angkanya tidak pernah cocok dengan halaman Laporan.
     *
     * Selagi online, nilainya diambil dari RPC `pos_revenue_summary_guarded` lewat
     * [_omzetServerOverride] — sumber yang sama dipakai dashboard web. Perhitungan
     * dari Room dulu dianggap "aman karena hari ini pasti sudah ada di cache
     * lokal", tapi itu cuma benar kalau event realtime yang
     * menulis ke Room tidak pernah telat/kelewat — pada kenyataannya kadang telat,
     * dan itu sumber omzet native pernah beda dari web. Room tetap dipakai sebagai
     * fallback saat offline.
     */
    val omzetKotorHariIni: StateFlow<Double> = combine(
        currentOutletId.flatMapLatest { outletId ->
            combine(orderDao.getOrdersByOutlet(outletId), dayTicker) { entities, _ -> entities }
                .map { entities ->
                    val today = LocalDate.now(ZoneId.of("Asia/Jakarta"))
                    entities.filter {
                        com.sukashawarma.pos.domain.usecase.RevenueCalculator.isRevenue(it) &&
                            isToday(it.createdAt, today)
                    }.sumOf { com.sukashawarma.pos.domain.usecase.RevenueCalculator.grossOf(it) }
                }
        },
        _omzetServerOverride
    ) { localGross, serverGross -> serverGross ?: localGross }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0.0)

    /** Menarik ulang [_omzetServerOverride] tiap ganti outlet, event sync, atau balik online. */
    private suspend fun refreshOmzetFromServer() {
        val outletId = currentOutletId.value
        if (outletId.isBlank() || !NetworkMonitor.isOnline.value) {
            _omzetServerOverride.value = null
            return
        }
        try {
            val today = LocalDate.now(ZoneId.of("Asia/Jakarta"))
            val startIso = com.sukashawarma.pos.domain.gate.JakartaTime.startOfDayIso(today)
            val endIso = com.sukashawarma.pos.domain.gate.JakartaTime.endOfDayIso(today)
            
            val conditions = mutableListOf<String>()
            conditions.add("created_at.gte.$startIso")
            conditions.add("created_at.lte.$endIso")
            val filters = mapOf(
                "outlet_id" to "eq.$outletId",
                "limit" to "1000",
                "and" to "(${conditions.joinToString(",")})"
            )
            
            val dtos = api.getOrders(filters).body() ?: return
            val completed = dtos.filter { com.sukashawarma.pos.domain.usecase.RevenueCalculator.isRevenue(it) }
            val gross = completed.sumOf { com.sukashawarma.pos.domain.usecase.RevenueCalculator.grossOf(it) }
            _omzetServerOverride.value = gross
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    /** Bahan menipis + kritis di outlet ini, apa adanya dari `monitoring_view_crew`. */
    val stockAlerts = MutableStateFlow<List<StockAlert>>(emptyList())

    /** Hanya yang berstatus `below` — inilah isi marquee merah, sama seperti web. */
    val criticalStockAlerts: StateFlow<List<StockAlert>> = stockAlerts
        .map { list -> list.filter { it.status == StockAlertStatus.BELOW } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** Badge di menu "Stok Outlet": jumlah bahan kritis, bukan jumlah menu habis. */
    val lowStockCount: StateFlow<Int> = criticalStockAlerts
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    private var stockAlertJob: Job? = null
    private var stockRealtime: StockRealtimeManager? = null
    
    val printLayout = MutableStateFlow<PrintLayoutDto?>(null)

    val isRealtimeConnected = MutableStateFlow(false)
    val pendingSyncCount: StateFlow<Int> = currentOutletId
        .flatMapLatest { outletId -> orderDao.getPendingSyncCountFlow(outletId) }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val orders: StateFlow<List<Order>> = currentOutletId
        .flatMapLatest { outletId ->
            orderDao.getOrdersByOutlet(outletId).map { entities ->
                entities.map { mapEntityToOrder(it) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val pendingOrders: StateFlow<List<Order>> = orders
        .map { list -> list.filter { it.status == OrderStatus.PENDING } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val preparingOrders: StateFlow<List<Order>> = orders
        .map { list -> list.filter { it.status == OrderStatus.PREPARING } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val completedOrders: StateFlow<List<Order>> = combine(orders, dayTicker) { list, _ -> list }
        .map { list ->
            val today = LocalDate.now(ZoneId.of("Asia/Jakarta"))
            list.filter {
                (it.status == OrderStatus.COMPLETED || it.status == OrderStatus.READY) &&
                    isToday(it.createdAt, today)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        viewModelScope.launch {
            com.sukashawarma.pos.data.remote.GlobalEventBus.isRealtimeConnected.collect { connected ->
                isRealtimeConnected.value = connected
            }
        }
        viewModelScope.launch {
            com.sukashawarma.pos.data.remote.GlobalEventBus.orderSyncEvent.collect {
                syncOrdersFromServer(currentOutletId.value)
            }
        }
        viewModelScope.launch {
            NetworkMonitor.isOnline.collect { isOnline ->
                if (isOnline) {
                    val outletId = currentOutletId.value
                    if (outletId.isNotBlank()) {
                        trySyncPendingOrders(outletId)
                    }
                }
            }
        }
        viewModelScope.launch {
            pendingSyncCount.collect { count ->
                if (count > 0 && NetworkMonitor.isOnline.value) {
                    val outletId = currentOutletId.value
                    if (outletId.isNotBlank()) {
                        trySyncPendingOrders(outletId)
                    }
                }
            }
        }
        viewModelScope.launch {
            var retryDelayMs = OrderSyncRetryPolicy.INITIAL_DELAY_MS
            while (isActive) {
                delay(retryDelayMs)

                val outletId = currentOutletId.value
                if (!NetworkMonitor.isOnline.value || outletId.isBlank()) {
                    retryDelayMs = OrderSyncRetryPolicy.INITIAL_DELAY_MS
                    continue
                }
                if (pendingSyncCount.value <= 0) {
                    retryDelayMs = OrderSyncRetryPolicy.INITIAL_DELAY_MS
                    continue
                }

                val synced = trySyncPendingOrders(outletId)
                retryDelayMs = OrderSyncRetryPolicy.afterAttempt(
                    previousDelayMs = retryDelayMs,
                    syncedAnyOrder = synced > 0
                )
            }
        }
        viewModelScope.launch {
            merge(
                currentOutletId.map { },
                com.sukashawarma.pos.data.remote.GlobalEventBus.orderSyncEvent.map { },
                NetworkMonitor.isOnline.map { },
                dayTicker
            ).collectLatest { refreshOmzetFromServer() }
        }
    }

    // showLocalNotification removed, handled by POSRealtimeService in background

    /** Call once right after login (and again on outlet switch) to scope everything to this outlet. */
    fun setSession(outletId: String, outletName: String, cashierName: String) {
        currentOutletId.value = outletId
        currentOutletName.value = outletName
        currentCashierName.value = cashierName
        restartStockAlerts(outletId)
        fetchPrintLayout()
        viewModelScope.launch {
            trySyncPendingOrders(outletId)
            syncOrdersFromServer(outletId)
        }
        // Logout memanggil ini dengan outlet kosong. Dulu `start("")` diabaikan
        // diam-diam oleh service, sehingga channel outlet LAMA tetap hidup dan
        // HP terus berbunyi untuk pesanan outlet itu setelah kasir keluar.
        if (outletId.isBlank()) {
            com.sukashawarma.pos.data.remote.realtime.POSRealtimeService.stop(getApplication())
        } else {
            com.sukashawarma.pos.data.remote.realtime.POSRealtimeService.start(getApplication(), outletId)
        }
    }

    /** Fase 3: drains any orders saved locally while offline, then refreshes from the server. */
    private suspend fun trySyncPendingOrders(outletId: String): Int {
        if (outletId.isBlank()) return 0
        // OrderSyncEngine mengirim event refresh setelah berhasil. Tidak perlu
        // menarik server sekali lagi di sini karena itu menggandakan request.
        return syncEngine.syncPendingOrders(outletId)
    }

    override fun onCleared() {
        super.onCleared()
        // Service handles background connection independently now,
        // no need to disconnect when ViewModel clears.
        // Channel stok tidak punya service latar seperti pesanan, jadi harus
        // ditutup sendiri supaya socket-nya tidak menggantung.
        stockRealtime?.disconnect()
        stockRealtime = null
    }

    /**
     * True bila nilai `cancellation_status` berarti "tidak ada pembatalan".
     *
     * Server memakai tiga ejaan untuk hal yang sama: NULL (baris lama), string
     * kosong, dan 'none' (default kolom sekarang).
     */
    private fun isNoCancellation(value: String?): Boolean =
        value.isNullOrBlank() || value.equals("none", ignoreCase = true)

    /**
     * Pulls this outlet's recent orders (with line items) from Supabase and mirrors them into Room.
     *
     * Dibatasi [SYNC_WINDOW_DAYS] hari terakhir, bukan "semua pesanan outlet".
     * Versi lama mengirim query tanpa rentang tanggal maupun limit, sehingga:
     *   1. Tiap pemanggilan menarik ~1000 baris (±1,5 MB, sudah termasuk
     *      order_items dan menu_items bersarang) — dan fungsi ini dipanggil pada
     *      SETIAP event realtime pesanan, tiap flush pending-sync, dan tiap
     *      pergantian sesi. Di jam sibuk itu berulang terus-menerus.
     *   2. Yang membatasi hanya `max-rows` bawaan PostgREST, jadi cakupan Room
     *      menyusut diam-diam seiring bertambahnya transaksi outlet — outlet
     *      dengan 3.700 pesanan hanya menerima ±13 hari terakhir tanpa ada
     *      tandanya sama sekali.
     * Rentang tetap membuat beban sinkron konstan dan cakupannya bisa diprediksi.
     */
    suspend fun syncOrdersFromServer(outletId: String) {
        if (outletId.isBlank()) return
        try {
            val since = com.sukashawarma.pos.domain.gate.JakartaTime.startOfDayIso(
                com.sukashawarma.pos.domain.gate.JakartaTime.today().minusDays(SYNC_WINDOW_DAYS - 1L)
            )
            // `order` sengaja tidak dikirim di sini: getOrders() sudah memasang
            // default `created_at.desc`, dan mengirimnya lagi lewat QueryMap
            // membuat parameter itu muncul dua kali di URL.
            val res = api.getOrders(
                mapOf(
                    "outlet_id" to "eq.$outletId",
                    "created_at" to "gte.$since",
                    "limit" to SYNC_ROW_LIMIT
                )
            )
            if (!res.isSuccessful) {
                // Dulu kegagalan di sini tidak meninggalkan jejak apa pun: Room
                // tidak ter-update dan kasir tetap melihat data lama tanpa tahu
                // sinkronisasinya gagal (token kedaluwarsa, 5xx, timeout).
                android.util.Log.e(
                    "DashboardViewModel",
                    "Sync pesanan outlet $outletId gagal: HTTP ${res.code()} ${res.errorBody()?.string().orEmpty()}"
                )
                return
            }
            val dtos = res.body()
            if (dtos == null) {
                android.util.Log.e("DashboardViewModel", "Sync pesanan outlet $outletId sukses tapi body kosong")
                return
            }
            // Dikumpulkan dulu, lalu ditulis sekali. Versi lama menyisipkan tiap
            // pesanan satu per satu pada SETIAP sync (tiap 15 detik dan tiap event
            // realtime), termasuk pesanan yang isinya sama persis — setiap
            // penyisipan memicu Room mengirim sinyal dan seluruh daftar pesanan,
            // laporan, serta laci kasir dikomposisi ulang tanpa ada yang berubah.
            val toWrite = mutableListOf<LocalOrderEntity>()
            // Order yang device ini belum pernah punya salinannya SAMA SEKALI (kiosk/
            // device lain, bukan yang membuat order-nya) tidak punya `existing` untuk
            // dipertahankan kalau kena race yang sama (lihat komentar panjang di bawah).
            // Satu-satunya penyelamatan yang mungkin: fetch ulang sekali lagi setelah
            // jeda singkat, dengan asumsi order_items-nya sudah komit di server saat itu.
            val suspiciousNewOrderIds = mutableListOf<String>()
            val existingById = orderDao.getAllOrdersByOutlet(outletId).associateBy { it.id }
            dtos.forEach { dto ->
                val existing = existingById[dto.id]
                var newEntity = dtoToEntity(dto)
                if (existing == null && dto.orderItems.isNullOrEmpty() && dto.totalAmount > 0) {
                    suspiciousNewOrderIds += dto.id
                }
                if (existing != null) {
                    newEntity = newEntity.copy(isSyncedFromOffline = existing.isSyncedFromOffline)
                    // Prevent stale HTTP response from overwriting an eager real-time CANCELLED patch
                    if (existing.status == com.sukashawarma.pos.domain.model.OrderStatus.CANCELLED.name && newEntity.status != com.sukashawarma.pos.domain.model.OrderStatus.CANCELLED.name) {
                        newEntity = newEntity.copy(
                            status = com.sukashawarma.pos.domain.model.OrderStatus.CANCELLED.name,
                            cancellationStatus = "approved"
                        )
                    }
                    // Jangan biarkan "belum ada pembatalan" dari server menimpa
                    // status pending_approval yang baru ditulis lokal.
                    //
                    // Kolom `orders.cancellation_status` default-nya string 'none',
                    // BUKAN NULL. Penjaga lama cuma memeriksa null/kosong, jadi
                    // 'none' lolos dan menimpa status lokal — kartu "Menunggu
                    // Persetujuan" balik jadi tombol Batal/Selesai dalam hitungan
                    // detik, karena sync ini berjalan tiap 15 detik.
                    // Perlindungan ini berbatas waktu. Tanpa batas, keputusan
                    // TOLAK dari AM (server mengembalikan nilai ke 'none')
                    // tidak pernah bisa mendarat dan kartu terus berputar.
                    if (existing.cancellationStatus == "pending_approval" &&
                        isNoCancellation(newEntity.cancellationStatus) &&
                        com.sukashawarma.pos.data.sync.PendingCancellationGuard.isProtected(dto.id)
                    ) {
                        newEntity = newEntity.copy(
                            cancellationStatus = "pending_approval",
                            cancellationUserName = existing.cancellationUserName
                        )
                    }
                    // submitOrder() menyimpan order lokal dengan item LENGKAP, lalu di
                    // baris berikutnya langsung memancarkan orderSyncEvent yang memicu
                    // fetch ini — BERSAMAAN dengan pushOrderRemainder() yang baru mulai
                    // POST order_items ke server di coroutine terpisah (lihat
                    // POSManualOrderViewModel.submitOrder). Kalau fetch GET ini menang
                    // race dan sampai duluan sebelum POST order_items komit, server
                    // membalas order_items KOSONG padahal total_amount sudah terisi
                    // (kolom independen) — dan REPLACE di insertOrders() menimpa item
                    // lokal yang tadinya sudah benar dengan yang kosong itu. Order jadi
                    // tampil "harga ada, menu hilang" secara PERMANEN karena app hanya
                    // subscribe realtime ke tabel `orders`, bukan `order_items` — tidak
                    // ada event susulan yang memicu resync buat memperbaikinya sendiri.
                    // Jangan biarkan hasil fetch yang lebih miskin (item kosong) menimpa
                    // item lokal yang sudah ada.
                    if (dto.orderItems.isNullOrEmpty() &&
                        existing.itemsJson.isNotBlank() && existing.itemsJson != "[]" &&
                        dto.totalAmount > 0
                    ) {
                        newEntity = newEntity.copy(
                            itemsJson = existing.itemsJson,
                            subtotal = existing.subtotal
                        )
                    }
                }
                // Baris yang isinya identik tidak perlu ditulis ulang.
                if (newEntity != existing) toWrite += newEntity
            }

            if (toWrite.isNotEmpty()) orderDao.insertOrders(toWrite)

            if (suspiciousNewOrderIds.isNotEmpty()) {
                viewModelScope.launch { retrySuspiciousOrders(outletId, suspiciousNewOrderIds) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Fetch ulang SEKALI, khusus order yang pertama kali muncul di device ini
     * dengan order_items kosong tapi total_amount terisi — kemungkinan besar
     * order_items-nya baru komit di server sesaat setelah fetch pertama (lihat
     * komentar race condition di [syncOrdersFromServer]). Dibatasi satu kali
     * retry (tidak memanggil syncOrdersFromServer lagi secara rekursif) supaya
     * order yang order_items-nya memang betul-betul kosong di server tidak
     * memicu polling tanpa henti.
     */
    private suspend fun retrySuspiciousOrders(outletId: String, orderIds: List<String>) {
        kotlinx.coroutines.delay(4_000)
        try {
            val res = api.getOrders(
                mapOf("id" to "in.(${orderIds.joinToString(",")})")
            )
            val dtos = res.body() ?: return
            if (!res.isSuccessful || dtos.isEmpty()) return
            val existingById = orderDao.getAllOrdersByOutlet(outletId).associateBy { it.id }
            val toWrite = dtos.mapNotNull { dto ->
                if (dto.orderItems.isNullOrEmpty()) return@mapNotNull null
                val newEntity = dtoToEntity(dto)
                val existing = existingById[dto.id]
                if (newEntity == existing) null else newEntity
            }
            if (toWrite.isNotEmpty()) orderDao.insertOrders(toWrite)
        } catch (e: Exception) {
            android.util.Log.e("DashboardViewModel", "Retry order_items kosong gagal", e)
        }
    }

    /**
     * Ambil ulang daftar bahan menipis/kritis dari `monitoring_view_crew`.
     *
     * Versi sebelumnya membaca `menu_items.is_available` — menu yang dimatikan
     * kasir, bukan stok bahan baku — sehingga banner "STOK KRITIS/HABIS" tidak
     * pernah sinkron dengan papan stok dan umumnya tampil kosong.
     */
    fun refreshStockAlerts() {
        val outletId = currentOutletId.value
        if (outletId.isBlank()) return
        viewModelScope.launch {
            try {
                val res = api.getStockAlerts("eq.$outletId")
                if (res.isSuccessful) {
                    stockAlerts.value = (res.body() ?: emptyList()).toStockAlerts()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Pasang ulang pemantauan stok untuk satu outlet: channel realtime plus
     * polling 30 detik sebagai jaring pengaman — realtime bisa putus tanpa
     * suara, dan web pun tetap memasang `refetchInterval` di samping channel.
     *
     * Outlet kosong (setelah logout) menghentikan keduanya dan mengosongkan
     * daftar, supaya nama bahan outlet lama tidak tertinggal di layar.
     */
    private fun restartStockAlerts(outletId: String) {
        stockAlertJob?.cancel()

        if (stockRealtime == null) {
            stockRealtime = StockRealtimeManager(SupabaseClient.okHttpClient, viewModelScope).apply {
                onStockChanged = {
                    refreshStockAlerts()
                    com.sukashawarma.pos.data.remote.GlobalEventBus.stockEvent.tryEmit(Unit)
                }
            }
        }
        stockRealtime?.connect(outletId)

        if (outletId.isBlank()) {
            stockAlerts.value = emptyList()
            return
        }

        stockAlertJob = viewModelScope.launch {
            while (isActive) {
                refreshStockAlerts()
                delay(30_000)
            }
        }
    }

    private fun fetchPrintLayout() {
        viewModelScope.launch {
            try {
                val res = api.getGlobalSettings()
                if (res.isSuccessful && res.body()?.isNotEmpty() == true) {
                    val rawValue = res.body()!!.first().value
                    if (!rawValue.isNullOrBlank()) {
                        try {
                            val parsed = gson.fromJson(rawValue, PrintLayoutDto::class.java)
                            printLayout.value = parsed
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun updateOrderStatus(order: Order, newStatus: OrderStatus) {
        if (!statusUpdateGuard.tryBegin(order.id)) return
        _updatingOrderIds.update { it + order.id }
        viewModelScope.launch {
            try {
                val localOrder = orderDao.getOrderById(order.id)
                val needsSync = localOrder != null &&
                    localOrder.syncState != com.sukashawarma.pos.data.local.entity.SyncState.SYNCED.name
                if (needsSync) {
                    if (!NetworkMonitor.isOnline.value) {
                        _statusUpdateErrors.tryEmit(
                            "Order sementara #${order.orderNumber} masih aman di tablet. " +
                                "Hubungkan internet lalu coba lagi; aplikasi juga akan mengirimnya otomatis."
                        )
                        return@launch
                    }

                    _statusUpdateErrors.tryEmit(
                        "Order sementara #${order.orderNumber} sedang dikirim ke server…"
                    )
                    if (!syncEngine.syncOrder(order.id)) {
                        _statusUpdateErrors.tryEmit(
                            "Order sementara #${order.orderNumber} masih aman di tablet, " +
                                "tetapi server belum merespons. Aplikasi akan mencoba lagi otomatis."
                        )
                        return@launch
                    }
                }

                // Order Website tidak pernah dapat cashier_name saat dibuat (dibuat oleh
                // customer, bukan kasir) — dicatat saat kasir benar-benar menyelesaikannya.
                val cashierNameToRecord = currentCashierName.value.takeIf { it.isNotBlank() }
                val patch = mutableMapOf("status" to newStatus.name.lowercase())
                if (cashierNameToRecord != null) {
                    patch["cashier_name"] = cashierNameToRecord
                }
                // Server lebih dulu. Versi lama menghapus kartu dari kolom secara
                // optimistis, lalu menganggap HTTP 204 dengan 0 baris sebagai sukses.
                // Jika order belum pernah tersimpan di server, kartu tampak hilang
                // walaupun tidak ada transaksi yang bisa ditemukan di database.
                val res = api.updateOrderStatusReturning(
                    orderIdFilter = "eq.${order.id}",
                    patch = patch
                )
                val updatedRows = res.body()?.size ?: 0
                if (OrderStatusUpdatePolicy.canCommit(res.isSuccessful, updatedRows)) {
                    if (cashierNameToRecord != null) {
                        orderDao.updateOrderStatusAndCashier(order.id, newStatus.name, cashierNameToRecord)
                    } else {
                        orderDao.updateOrderStatus(order.id, newStatus.name)
                    }
                    com.sukashawarma.pos.data.remote.GlobalEventBus.orderSyncEvent.tryEmit(Unit)
                    com.sukashawarma.pos.data.remote.GlobalEventBus.targetRefreshEvent.tryEmit(Unit)

                    // Order website online yang ditandai Selesai di sini (papan order native)
                    // juga harus memicu WA "pesanan siap diambil" ke customer — sebelumnya
                    // hanya jalur web pos-kasir (markAsCompleted di KasirOrderClient.tsx) yang
                    // melakukan ini, jadi order yang diselesaikan lewat tablet native tidak
                    // pernah mengirim notifikasi WA sama sekali.
                    //
                    // PENTING (koreksi rilis sebelumnya): guard ini sempat dipersempit ke
                    // `order.channel == "website"`, dengan asumsi keliru bahwa order website
                    // asli punya channel itu. Dibuktikan lewat query production: dari 167 order
                    // `source='online'` yang benar-benar dari website customer, 132 di antaranya
                    // punya `channel = null` dan cuma 1 yang punya `channel = 'website'` — nilai
                    // channel="website" di DB justru dipakai tab "Order Website (Backup)" (order
                    // yang diketik manual kasir, source='manual', TANPA external_order_id), yang
                    // seharusnya memang tidak memicu WA ini. Guard channel=="website" itu malah
                    // mematikan notifikasi untuk order website asli. Edge Function
                    // kasir-order-done sudah menyaring dengan benar lewat `order.source==='online' &&
                    // order.external_order_id` — jadi cukup jaga di source==ONLINE saja di sini,
                    // biar tetap ikut GoFood/GrabFood/dst (server men-skip yang tidak relevan).
                    if (order.source == com.sukashawarma.pos.domain.model.OrderSource.ONLINE &&
                        newStatus == OrderStatus.COMPLETED
                    ) {
                        val token = com.sukashawarma.pos.data.remote.SessionTokenHolder.accessToken
                        if (token == null) {
                            android.util.Log.e(
                                "DashboardViewModel",
                                "Tidak bisa panggil kasir-order-done: sesi kasir tidak punya access token"
                            )
                        } else {
                            try {
                                val notifyRes = api.notifyOnlineOrderDone(
                                    url = "${com.sukashawarma.pos.data.remote.OrderOnlineEndpoints.FUNCTIONS_BASE}/kasir-order-done",
                                    authorization = "Bearer $token",
                                    payload = mapOf("pos_order_id" to order.id)
                                )
                                if (!notifyRes.isSuccessful) {
                                    android.util.Log.e(
                                        "DashboardViewModel",
                                        "kasir-order-done gagal untuk order ${order.id}: HTTP ${notifyRes.code()}"
                                    )
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("DashboardViewModel", "Gagal memanggil kasir-order-done", e)
                            }
                        }
                    }
                } else {
                    android.util.Log.e(
                        "DashboardViewModel",
                        "Status order ${order.id} tidak diubah: HTTP ${res.code()}, baris=$updatedRows"
                    )
                    _statusUpdateErrors.tryEmit(
                        "Server belum mengizinkan perubahan order ini. Status tetap aman dan tidak diubah; coba lagi."
                    )
                }
            } catch (e: Exception) {
                // Pertahankan kartu di status semula. Kasir dapat mencoba lagi dan
                // order tidak terlihat selesai sebelum server menyimpannya.
                android.util.Log.e("DashboardViewModel", "Update status order ${order.id} gagal", e)
                _statusUpdateErrors.tryEmit(
                    "Gagal mengubah order #${order.orderNumber}. Order tetap aman di daftar; cek koneksi lalu coba lagi."
                )
            } finally {
                statusUpdateGuard.finish(order.id)
                _updatingOrderIds.update { it - order.id }
            }
        }
    }

    /** Aksi kasir untuk memaksa satu order lokal dikirim tanpa mengubah statusnya. */
    fun retryOrderSync(order: Order) {
        if (!statusUpdateGuard.tryBegin(order.id)) return
        _updatingOrderIds.update { it + order.id }
        viewModelScope.launch {
            try {
                if (!NetworkMonitor.isOnline.value) {
                    _statusUpdateErrors.tryEmit(
                        "Order sementara #${order.orderNumber} masih aman di tablet. Internet belum siap."
                    )
                    return@launch
                }

                _statusUpdateErrors.tryEmit(
                    "Mengirim order sementara #${order.orderNumber} ke server…"
                )
                if (syncEngine.syncOrder(order.id)) {
                    val officialNumber = orderDao.getOrderById(order.id)?.orderNumber
                    _statusUpdateErrors.tryEmit(
                        if (officialNumber != null) {
                            "Order berhasil dikirim. Nomor resmi dari server: #$officialNumber."
                        } else {
                            "Order berhasil dikirim ke server."
                        }
                    )
                } else {
                    _statusUpdateErrors.tryEmit(
                        "Server belum merespons. Order tetap aman di tablet dan akan dicoba lagi otomatis."
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", "Retry order ${order.id} gagal", e)
                _statusUpdateErrors.tryEmit(
                    "Pengiriman belum berhasil. Order tetap aman di tablet dan akan dicoba lagi otomatis."
                )
            } finally {
                statusUpdateGuard.finish(order.id)
                _updatingOrderIds.update { it - order.id }
            }
        }
    }

    val printStatusMessage = MutableStateFlow<String?>(null)

    fun printReceipt(context: android.content.Context, order: Order, isKitchen: Boolean = false, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val printerMac = com.sukashawarma.pos.data.local.PrinterPrefs.getSelectedMac()
            if (printerMac.isNullOrBlank()) {
                printStatusMessage.value = "Belum ada printer dipilih — atur di menu Settings."
                return@launch
            }
            val connected = printerManager.ensureConnected(printerMac)
            if (!connected) {
                printStatusMessage.value = "Gagal terhubung ke printer. Cek Bluetooth & posisi printer."
                return@launch
            }

            val bytes = if (isKitchen) {
                printReceiptUseCase.generateKitchenReceiptBytes(
                    order = order,
                    outletName = currentOutletName.value,
                    cashierName = currentCashierName.value,
                    layout = printLayout.value?.strukDapur
                )
            } else {
                printReceiptUseCase.generateCustomerReceiptBytes(
                    context = context,
                    order = order,
                    outletName = currentOutletName.value,
                    cashierName = currentCashierName.value,
                    layout = printLayout.value?.strukCustomer
                )
            }
            val printed = printerManager.printBytesChunked(bytes)
            if (printed) {
                printStatusMessage.value = null
                onSuccess()
            } else {
                printStatusMessage.value = "Gagal mencetak struk."
            }
        }
    }
    
    fun markKitchenReceiptPrinted(order: Order) {
        viewModelScope.launch {
            orderDao.updateKitchenReceiptStatus(order.id, true)
            try {
                api.updateOrderStatus(
                    orderIdFilter = "eq.${order.id}",
                    patch = mapOf("kitchen_receipt_printed" to "true")
                )
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun markCustomerReceiptPrinted(order: Order) {
        viewModelScope.launch {
            orderDao.updateCustomerReceiptStatus(order.id, true)
            try {
                api.updateOrderStatus(
                    orderIdFilter = "eq.${order.id}",
                    patch = mapOf("customer_receipt_printed" to "true")
                )
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun requestCancellation(order: Order, reason: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val staffName = currentCashierName.value
            val previousCancellationStatus = order.cancellationStatus
            orderDao.updateCancellationStatus(order.id, "pending_approval", staffName)
            com.sukashawarma.pos.data.sync.PendingCancellationGuard.mark(order.id)
            try {
                // Hanya dua kolom ini yang ADA di tabel `orders` — sama persis dengan
                // web (KasirOrderClient.tsx). Dulu di sini ikut dikirim
                // `cancellation_user_name`, kolom yang tidak pernah ada, sehingga
                // PostgREST menolak SELURUH patch dan status "menunggu persetujuan"
                // tidak pernah tersimpan di server. Nama pemohon memang tidak
                // disimpan di `orders`; jejaknya ada di `cancellation_requests`.
                val patchRes = api.updateOrderStatus(
                    orderIdFilter = "eq.${order.id}",
                    patch = mapOf(
                        "cancellation_status" to "pending_approval",
                        "cancellation_reason" to reason
                    )
                )
                // Response<Void> tidak melempar exception untuk 4xx. Tanpa cek ini,
                // kegagalan tadi lolos tanpa suara dan kasir mengira sudah terkirim.
                if (!patchRes.isSuccessful) {
                    com.sukashawarma.pos.data.sync.PendingCancellationGuard.clear(order.id)
                    orderDao.updateCancellationStatus(order.id, previousCancellationStatus, null)
                    onError("Gagal menandai pesanan (${patchRes.code()}). Coba lagi.")
                    return@launch
                }

                // Generate expiresAt +24 hours
                val expiresAt = java.time.Instant.now().plus(24, java.time.temporal.ChronoUnit.HOURS).toString()
                val staffId = com.sukashawarma.pos.data.local.SessionPrefs.getStaffId() ?: ""
                
                val payload = com.sukashawarma.pos.data.remote.dto.CreateCancellationRequestPayload(
                    orderId = order.id,
                    reason = reason,
                    expiresAt = expiresAt,
                    previousOrderStatus = order.status.name.lowercase(),
                    requestedBy = staffId
                )

                val res = api.createCancellationRequest(payload)
                if (res.isSuccessful && res.body()?.isNotEmpty() == true) {
                    onSuccess()
                } else {
                    onError("Gagal mengirim permintaan pembatalan.")
                }
            } catch (e: Exception) { 
                e.printStackTrace()
                onError("Terjadi kesalahan jaringan.")
            }
        }
    }

    private fun mapEntityToOrder(entity: LocalOrderEntity): Order {
        val itemType = object : TypeToken<List<OrderItem>>() {}.type
        val items: List<OrderItem> = gson.fromJson(entity.itemsJson, itemType) ?: emptyList()

        val parsedStatus = try {
            OrderStatus.valueOf(entity.status)
        } catch (e: Exception) {
            OrderStatus.PENDING
        }

        return Order(
            id = entity.id,
            outletId = entity.outletId,
            orderNumber = entity.orderNumber,
            customerName = entity.customerName,
            status = parsedStatus,
            source = OrderSource.valueOf(entity.source),
            paymentMethod = PaymentMethod.valueOf(entity.paymentMethod),
            items = items,
            subtotal = entity.subtotal,
            discountAmount = entity.discountAmount,
            promoSubsidy = entity.promoSubsidy,
            totalAmount = entity.totalAmount,
            amountReceived = entity.amountReceived,
            changeAmount = entity.changeAmount,
            kitchenReceiptPrinted = entity.kitchenReceiptPrinted,
            customerReceiptPrinted = entity.customerReceiptPrinted,
            cancellationStatus = entity.cancellationStatus,
            cancellationUserName = entity.cancellationUserName,
            createdAt = entity.createdAt,
            // SENDING tidak dihitung "offline": itu pesanan yang baru saja dibuat dan
            // pengirimannya masih berjalan di latar belakang (umumnya < 1 detik).
            // Menandainya merah akan membuat setiap pesanan baru berkedip "OFFLINE".
            isOffline = entity.syncState != com.sukashawarma.pos.data.local.entity.SyncState.SYNCED.name &&
                entity.syncState != com.sukashawarma.pos.data.local.entity.SyncState.SENDING.name,
            isSyncedFromOffline = entity.isSyncedFromOffline,
            channel = entity.channel,
            notes = entity.notes,
            effectiveReleaseTime = entity.effectiveReleaseTime,
            cashierName = entity.cashierName
        )
    }

    private fun dtoToEntity(dto: OrderDto): LocalOrderEntity {
        val items = (dto.orderItems ?: emptyList()).map { item ->
            val safeName = item.resolvedName
            val nameUpper = safeName.uppercase()
            val isChild = nameUpper.startsWith("EXTRA ") || nameUpper.startsWith("? EXTRA") || nameUpper.startsWith(" EXTRA")
            
            val finalId = item.id ?: java.util.UUID.randomUUID().toString()
            android.util.Log.d("OrderSyncDebug", "dtoToEntity item: name=${safeName}, original_id=${item.id}, final_id=$finalId")
            
            OrderItem(
                id = finalId,
                menuItemId = item.menuItemId ?: "",
                name = safeName,
                quantity = item.quantity,
                unitPrice = item.unitPrice,
                subtotal = item.subtotal,
                isChild = isChild
            )
        }
        val mappedStatus = if (dto.cancellationStatus == "approved" || dto.status.equals("cancelled", ignoreCase = true)) {
            OrderStatus.CANCELLED.name
        } else {
            safeParseStatus(dto.status).name
        }

        return LocalOrderEntity(
            id = dto.id,
            outletId = dto.outletId,
            orderNumber = dto.orderNumber,
            customerName = dto.customerName ?: "Pelanggan",
            status = mappedStatus,
            source = safeParseSource(dto.source).name,
            paymentMethod = safeParsePaymentMethod(dto.paymentMethod).name,
            itemsJson = gson.toJson(items),
            subtotal = items.sumOf { it.subtotal },
            discountAmount = dto.discountAmount ?: 0.0,
            promoSubsidy = dto.promoSubsidy ?: 0.0,
            totalAmount = dto.totalAmount,
            amountReceived = dto.amountReceived ?: 0.0,
            changeAmount = dto.changeAmount ?: 0.0,
            kitchenReceiptPrinted = dto.kitchenReceiptPrinted ?: false,
            customerReceiptPrinted = dto.customerReceiptPrinted ?: false,
            cancellationStatus = dto.cancellationStatus,
            cancellationUserName = dto.cancellationUserName,
            notes = dto.notes,
            createdAt = parseIsoTimestamp(dto.createdAt),
            syncState = com.sukashawarma.pos.data.local.entity.SyncState.SYNCED.name,
            dirtyFields = "",
            isSyncedFromOffline = false,
            channel = dto.channel,
            effectiveReleaseTime = com.sukashawarma.pos.domain.usecase.PreparingOrderClassifier.effectiveReleaseTime(
                createdAt = parseIsoTimestamp(dto.createdAt),
                releaseTime = dto.releaseTime,
                pickupTime = dto.pickupTime,
                notes = dto.notes
            ),
            cashierName = dto.cashierName
        )
    }
}

private fun safeParseStatus(value: String): OrderStatus =
    try { OrderStatus.valueOf(value.uppercase()) } catch (e: Exception) { OrderStatus.PENDING }

private fun safeParseSource(value: String): OrderSource = when (value.lowercase()) {
    "kiosk" -> OrderSource.KIOSK
    "pos" -> OrderSource.POS
    else -> OrderSource.ONLINE // online, manual, gofood, grabfood, shopeefood, tiktok
}

private fun safeParsePaymentMethod(value: String?): PaymentMethod =
    try { PaymentMethod.valueOf((value ?: "cash").uppercase()) } catch (e: Exception) { PaymentMethod.CASH }

/**
 * `Instant.parse` melempar untuk timestamp ber-offset seperti `...+07:00` — bug yang
 * sama sudah diperbaiki di [OrderHistoryViewModel] dan [ShiftViewModel] dengan
 * [com.sukashawarma.pos.domain.gate.JakartaTime.instantOrNull]. Fallback ke "sekarang"
 * salah: pesanan lama yang gagal di-parse akan ikut ke-cap sebagai "hari ini" dan
 * membengkakkan omzet, bukan malah hilang.
 */
private fun parseIsoTimestamp(iso: String): Long =
    com.sukashawarma.pos.domain.gate.JakartaTime.instantOrNull(iso)?.toEpochMilli() ?: 0L

private fun isToday(timestamp: Long, today: LocalDate): Boolean {
    val orderDate = Instant.ofEpochMilli(timestamp).atZone(ZoneId.of("Asia/Jakarta")).toLocalDate()
    return orderDate == today
}
