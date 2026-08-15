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
    private val database = (application as POSApplication).database
    private val orderDao = database.orderDao()
    private val api = SupabaseClient.api
    private val gson = Gson()
    private val printReceiptUseCase = PrintReceiptUseCase()
    val printerManager = BluetoothPrinterManager
    private val alertPlayer = OrderAlertPlayer(application)
    private val syncEngine = OrderSyncEngine(orderDao, api)

    val currentOutletId = MutableStateFlow("")
    val currentOutletName = MutableStateFlow("")
    val currentCashierName = MutableStateFlow("Kasir")
    val outlets = MutableStateFlow<List<Pair<String, String>>>(emptyList())

    /**
     * Omzet kotor hari ini: jumlah subtotal item dari pesanan `COMPLETED`, zona Jakarta.
     *
     * Dulu ini menjumlahkan `totalAmount` (nilai setelah diskon) dan diberi label
     * "omzet", sehingga angkanya tidak pernah cocok dengan halaman Laporan.
     * Aman dihitung dari Room karena cakupannya hanya hari ini — semua pesanan hari
     * berjalan pasti ada di cache lokal.
     */
    val omzetKotorHariIni: StateFlow<Double> = currentOutletId
        .flatMapLatest { outletId ->
            orderDao.getOrdersByOutlet(outletId).map { entities ->
                val today = LocalDate.now(ZoneId.of("Asia/Jakarta"))
                entities.filter {
                    com.sukashawarma.pos.domain.usecase.RevenueCalculator.isRevenue(it) &&
                        isToday(it.createdAt, today)
                }.sumOf { com.sukashawarma.pos.domain.usecase.RevenueCalculator.grossOf(it) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0.0)
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

    val completedOrders: StateFlow<List<Order>> = orders
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
    private suspend fun trySyncPendingOrders(outletId: String) {
        if (outletId.isBlank()) return
        val synced = syncEngine.syncPendingOrders(outletId)
        if (synced > 0) {
            syncOrdersFromServer(outletId)
        }
    }

    /** Periodic sync loop removed in favor of NetworkMonitor trigger */

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

    /** Pulls this outlet's orders (with line items) from Supabase and mirrors them into Room. */
    suspend fun syncOrdersFromServer(outletId: String) {
        if (outletId.isBlank()) return
        try {
            val res = api.getOrders(mapOf("outlet_id" to "eq.$outletId", "order" to "created_at.desc"))
            if (res.isSuccessful && res.body() != null) {
                val dtos = res.body()!!
                // Dikumpulkan dulu, lalu ditulis sekali. Versi lama menyisipkan tiap
                // pesanan satu per satu pada SETIAP sync (tiap 15 detik dan tiap event
                // realtime), termasuk pesanan yang isinya sama persis — setiap
                // penyisipan memicu Room mengirim sinyal dan seluruh daftar pesanan,
                // laporan, serta laci kasir dikomposisi ulang tanpa ada yang berubah.
                val toWrite = mutableListOf<LocalOrderEntity>()
                val existingById = orderDao.getAllOrdersByOutlet(outletId).associateBy { it.id }
                dtos.forEach { dto ->
                    val existing = existingById[dto.id]
                    var newEntity = dtoToEntity(dto)
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
                    }
                    // Baris yang isinya identik tidak perlu ditulis ulang.
                    if (newEntity != existing) toWrite += newEntity
                }

                if (toWrite.isNotEmpty()) orderDao.insertOrders(toWrite)
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
        viewModelScope.launch {
            // Order Website tidak pernah dapat cashier_name saat dibuat (dibuat oleh
            // customer, bukan kasir) — dicatat di sini, saat kasir yang benar-benar
            // menekan Selesai, supaya laporan tahu siapa yang memproses pesanan itu.
            val cashierNameToRecord = currentCashierName.value.takeIf { it.isNotBlank() }
            if (cashierNameToRecord != null) {
                orderDao.updateOrderStatusAndCashier(order.id, newStatus.name, cashierNameToRecord)
            } else {
                orderDao.updateOrderStatus(order.id, newStatus.name)
            }
            try {
                val patch = mutableMapOf("status" to newStatus.name.lowercase())
                if (cashierNameToRecord != null) {
                    patch["cashier_name"] = cashierNameToRecord
                }
                val res = api.updateOrderStatus(
                    orderIdFilter = "eq.${order.id}",
                    patch = patch
                )
                // Emit HANYA setelah server benar-benar menerima perubahan.
                //
                // Sebelumnya event ini dikirim sebelum PATCH berangkat. Kolektornya
                // langsung menjalankan syncOrdersFromServer, yang menarik status
                // LAMA dari server dan menimpa tulisan lokal barusan — kartu balik
                // ke "Diproses" dan tombol Selesai baru berhasil di klik kedua.
                if (res.isSuccessful) {
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
                    // mematikan notifikasi untuk order website asli. Server (notify-online-done)
                    // sudah menyaring dengan benar lewat `order.source==='online' &&
                    // order.external_order_id` — jadi cukup jaga di source==ONLINE saja di sini,
                    // biar tetap ikut GoFood/GrabFood/dst (server men-skip yang tidak relevan).
                    if (order.source == com.sukashawarma.pos.domain.model.OrderSource.ONLINE &&
                        newStatus == OrderStatus.COMPLETED
                    ) {
                        try {
                            val notifyRes = api.notifyOnlineOrderDone(
                                url = "$WEB_POS_API_BASE/api/orders/notify-online-done",
                                payload = mapOf("order_id" to order.id)
                            )
                            if (!notifyRes.isSuccessful) {
                                android.util.Log.e(
                                    "DashboardViewModel",
                                    "notify-online-done gagal untuk order ${order.id}: HTTP ${notifyRes.code()}"
                                )
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("DashboardViewModel", "Gagal memanggil notify-online-done", e)
                        }
                    }
                }
            } catch (e: Exception) {
                // Offline: status lokal sudah tersimpan dan layar lain membaca Room
                // secara reaktif, jadi tidak ada yang perlu dipicu di sini.
                e.printStackTrace()
            }
        }
    }

    companion object {
        private const val WEB_POS_API_BASE = "https://pos.sukashawarma.com"
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
            totalAmount = entity.totalAmount,
            amountReceived = entity.amountReceived,
            changeAmount = entity.changeAmount,
            kitchenReceiptPrinted = entity.kitchenReceiptPrinted,
            customerReceiptPrinted = entity.customerReceiptPrinted,
            cancellationStatus = entity.cancellationStatus,
            cancellationUserName = entity.cancellationUserName,
            createdAt = entity.createdAt,
            isOffline = entity.syncState != com.sukashawarma.pos.data.local.entity.SyncState.SYNCED.name,
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

private fun parseIsoTimestamp(iso: String): Long = try {
    java.time.Instant.parse(if (iso.endsWith("Z") || iso.contains("+")) iso else "${iso}Z").toEpochMilli()
} catch (e: Exception) {
    System.currentTimeMillis()
}

private fun isToday(timestamp: Long, today: LocalDate): Boolean {
    val orderDate = Instant.ofEpochMilli(timestamp).atZone(ZoneId.of("Asia/Jakarta")).toLocalDate()
    return orderDate == today
}
