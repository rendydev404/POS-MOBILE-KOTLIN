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
import com.sukashawarma.pos.data.remote.realtime.OrderRealtimeManager
import com.sukashawarma.pos.data.sync.OrderSyncEngine
import com.sukashawarma.pos.domain.model.*
import com.sukashawarma.pos.domain.usecase.PrintReceiptUseCase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val database = (application as POSApplication).database
    private val orderDao = database.orderDao()
    private val api = SupabaseClient.api
    private val gson = Gson()
    private val printReceiptUseCase = PrintReceiptUseCase()
    val printerManager = BluetoothPrinterManager()
    private val alertPlayer = OrderAlertPlayer(application)
    private val realtimeManager = OrderRealtimeManager(SupabaseClient.okHttpClient, viewModelScope)
    private val syncEngine = OrderSyncEngine(orderDao, api)

    val currentOutletId = MutableStateFlow("")
    val currentOutletName = MutableStateFlow("")
    val currentCashierName = MutableStateFlow("Kasir")
    val outlets = MutableStateFlow<List<Pair<String, String>>>(emptyList())

    val totalLunasToday = MutableStateFlow(0.0)
    val criticalStockNames = MutableStateFlow("")
    val lowStockCount = MutableStateFlow(0)

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
        .map { list -> list.filter { it.status == OrderStatus.COMPLETED || it.status == OrderStatus.READY } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        realtimeManager.onConnectionState = { connected -> isRealtimeConnected.value = connected }
        realtimeManager.onChange = { eventType, record ->
            val recordOutletId = record.optString("outlet_id")
            val recordSource = record.optString("source", "pos")
            if (recordOutletId == currentOutletId.value) {
                viewModelScope.launch { syncOrdersFromServer(currentOutletId.value) }
                if (eventType == "INSERT" && recordSource.lowercase() != "pos") {
                    alertPlayer.playNewOrderAlert()
                }
            }
        }
        startPeriodicSyncLoop()
    }

    /** Call once right after login (and again on outlet switch) to scope everything to this outlet. */
    fun setSession(outletId: String, outletName: String, cashierName: String) {
        currentOutletId.value = outletId
        currentOutletName.value = outletName
        currentCashierName.value = cashierName
        fetchRealCriticalStockFromSupabase()
        viewModelScope.launch {
            trySyncPendingOrders(outletId)
            syncOrdersFromServer(outletId)
        }
        realtimeManager.connect(outletId)
    }

    /** Fase 3: drains any orders saved locally while offline, then refreshes from the server. */
    private suspend fun trySyncPendingOrders(outletId: String) {
        if (outletId.isBlank()) return
        val synced = syncEngine.syncPendingOrders(outletId)
        if (synced > 0) {
            syncOrdersFromServer(outletId)
        }
    }

    /** Retries the offline sync queue every 30s while an outlet session is active. */
    private fun startPeriodicSyncLoop() {
        viewModelScope.launch {
            while (isActive) {
                delay(30_000)
                val outletId = currentOutletId.value
                if (outletId.isNotBlank()) {
                    trySyncPendingOrders(outletId)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        realtimeManager.disconnect()
    }

    /** Pulls this outlet's orders (with line items) from Supabase and mirrors them into Room. */
    suspend fun syncOrdersFromServer(outletId: String) {
        if (outletId.isBlank()) return
        try {
            val res = api.getOrders("eq.$outletId")
            if (res.isSuccessful && res.body() != null) {
                val dtos = res.body()!!
                dtos.forEach { dto -> orderDao.insertOrder(dtoToEntity(dto)) }

                val completed = dtos.filter { it.status.equals("completed", ignoreCase = true) || it.status.equals("ready", ignoreCase = true) }
                totalLunasToday.value = completed.sumOf { it.totalAmount }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun fetchRealCriticalStockFromSupabase() {
        viewModelScope.launch {
            try {
                val menuRes = api.getMenuItems()
                if (menuRes.isSuccessful && menuRes.body() != null) {
                    val soldOutItems = menuRes.body()!!.filter { it.isAvailable == false }
                    lowStockCount.value = soldOutItems.size
                    criticalStockNames.value = if (soldOutItems.isNotEmpty()) {
                        soldOutItems.joinToString(" • ") { it.name.uppercase() }
                    } else {
                        ""
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateOrderStatus(order: Order, newStatus: OrderStatus) {
        viewModelScope.launch {
            orderDao.updateOrderStatus(order.id, newStatus.name)
            try {
                api.updateOrderStatus(
                    orderIdFilter = "eq.${order.id}",
                    patch = mapOf("status" to newStatus.name.lowercase())
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val printStatusMessage = MutableStateFlow<String?>(null)

    fun printReceipt(order: Order, isKitchen: Boolean = false) {
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
                printReceiptUseCase.generateKitchenReceiptBytes(order)
            } else {
                printReceiptUseCase.generateCustomerReceiptBytes(
                    order = order,
                    outletName = currentOutletName.value,
                    cashierName = currentCashierName.value
                )
            }
            val printed = printerManager.printBytesChunked(bytes)
            printStatusMessage.value = if (printed) null else "Gagal mencetak struk."
        }
    }

    private fun mapEntityToOrder(entity: LocalOrderEntity): Order {
        val itemType = object : TypeToken<List<OrderItem>>() {}.type
        val items: List<OrderItem> = gson.fromJson(entity.itemsJson, itemType) ?: emptyList()

        return Order(
            id = entity.id,
            outletId = entity.outletId,
            orderNumber = entity.orderNumber,
            customerName = entity.customerName,
            status = OrderStatus.valueOf(entity.status),
            source = OrderSource.valueOf(entity.source),
            paymentMethod = PaymentMethod.valueOf(entity.paymentMethod),
            items = items,
            subtotal = entity.subtotal,
            discountAmount = entity.discountAmount,
            totalAmount = entity.totalAmount,
            amountReceived = entity.amountReceived,
            changeAmount = entity.changeAmount,
            kitchenReceiptPrinted = entity.kitchenReceiptPrinted,
            createdAt = entity.createdAt,
            isOffline = entity.isPendingSync,
            channel = entity.channel
        )
    }

    private fun dtoToEntity(dto: OrderDto): LocalOrderEntity {
        val items = (dto.orderItems ?: emptyList()).map { item ->
            OrderItem(
                menuItemId = item.menuItemId,
                name = item.menuItemName,
                quantity = item.quantity,
                unitPrice = item.unitPrice,
                subtotal = item.subtotal
            )
        }
        return LocalOrderEntity(
            id = dto.id,
            outletId = dto.outletId,
            orderNumber = dto.orderNumber,
            customerName = dto.customerName ?: "Pelanggan",
            status = safeParseStatus(dto.status).name,
            source = safeParseSource(dto.source).name,
            paymentMethod = safeParsePaymentMethod(dto.paymentMethod).name,
            itemsJson = gson.toJson(items),
            subtotal = items.sumOf { it.subtotal },
            discountAmount = dto.discountAmount ?: 0.0,
            totalAmount = dto.totalAmount,
            amountReceived = dto.amountReceived ?: 0.0,
            changeAmount = dto.changeAmount ?: 0.0,
            kitchenReceiptPrinted = dto.kitchenReceiptPrinted ?: false,
            createdAt = parseIsoTimestamp(dto.createdAt),
            isPendingSync = false,
            channel = dto.channel
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
