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
import com.sukashawarma.pos.data.sync.OrderSyncEngine
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

    val totalLunasToday = MutableStateFlow(0.0)
    val criticalStockNames = MutableStateFlow("")
    val lowStockCount = MutableStateFlow(0)
    
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
            val today = LocalDate.now(ZoneId.systemDefault())
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
        startPeriodicSyncLoop()
    }

    // showLocalNotification removed, handled by POSRealtimeService in background

    /** Call once right after login (and again on outlet switch) to scope everything to this outlet. */
    fun setSession(outletId: String, outletName: String, cashierName: String) {
        currentOutletId.value = outletId
        currentOutletName.value = outletName
        currentCashierName.value = cashierName
        fetchRealCriticalStockFromSupabase()
        fetchPrintLayout()
        viewModelScope.launch {
            trySyncPendingOrders(outletId)
            syncOrdersFromServer(outletId)
        }
        com.sukashawarma.pos.data.remote.realtime.POSRealtimeService.start(getApplication(), outletId)
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
        // Service handles background connection independently now,
        // no need to disconnect when ViewModel clears.
    }

    /** Pulls this outlet's orders (with line items) from Supabase and mirrors them into Room. */
    suspend fun syncOrdersFromServer(outletId: String) {
        if (outletId.isBlank()) return
        try {
            val res = api.getOrders(mapOf("outlet_id" to "eq.$outletId"))
            if (res.isSuccessful && res.body() != null) {
                val dtos = res.body()!!
                dtos.forEach { dto -> 
                    val existing = orderDao.getOrderById(dto.id)
                    val newEntity = dtoToEntity(dto)
                    if (existing != null) {
                        orderDao.insertOrder(newEntity) // Always use the updated data from server
                    } else {
                        orderDao.insertOrder(newEntity)
                    }
                }

                val today = LocalDate.now(ZoneId.systemDefault())
                val completedToday = dtos.filter { 
                    (it.status.equals("completed", ignoreCase = true) || it.status.equals("ready", ignoreCase = true)) &&
                    isToday(parseIsoTimestamp(it.createdAt), today)
                }
                totalLunasToday.value = completedToday.sumOf { it.totalAmount }
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

    fun requestCancellation(order: Order, reason: String, onWaUrlReady: (String) -> Unit) {
        viewModelScope.launch {
            val staffName = currentCashierName.value
            orderDao.updateCancellationStatus(order.id, "pending_approval", staffName)
            try {
                api.updateOrderStatus(
                    orderIdFilter = "eq.${order.id}",
                    patch = mapOf(
                        "cancellation_status" to "pending_approval",
                        "cancellation_reason" to reason,
                        "cancellation_user_name" to staffName
                    )
                )

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
                    val token = res.body()!!.first().token
                    val magicLink = "https://app.sukashawarma.com/cancellations/approve?token=$token"
                    
                    val currencyFormat = java.text.NumberFormat.getCurrencyInstance(java.util.Locale("id", "ID")).apply {
                        maximumFractionDigits = 0
                    }
                    val formattedPrice = currencyFormat.format(order.totalAmount).replace("Rp", "Rp ")
                    
                    val message = """
*PERMINTAAN PEMBATALAN PESANAN*

Outlet: ${currentOutletName.value}
No Order: ${order.orderNumber}
Pelanggan: ${order.customerName}
Total: $formattedPrice
Alasan: $reason
Diajukan Oleh: $staffName

Silakan klik link berikut untuk *MENYETUJUI* atau *MENOLAK* pembatalan ini (link hanya berlaku 1 kali):

$magicLink
                    """.trimIndent()

                    val encodedMessage = android.net.Uri.encode(message)
                    val phone = "6285218446637"
                    val waUrl = "whatsapp://send?phone=$phone&text=$encodedMessage"
                    onWaUrlReady(waUrl)
                }
            } catch (e: Exception) { e.printStackTrace() }
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
            customerReceiptPrinted = entity.customerReceiptPrinted,
            cancellationStatus = entity.cancellationStatus,
            cancellationUserName = entity.cancellationUserName,
            createdAt = entity.createdAt,
            isOffline = entity.isPendingSync,
            channel = entity.channel
        )
    }

    private fun dtoToEntity(dto: OrderDto): LocalOrderEntity {
        val items = (dto.orderItems ?: emptyList()).map { item ->
            val nameUpper = item.menuItemName.uppercase()
            val isChild = nameUpper.startsWith("EXTRA ") || nameUpper.startsWith("? EXTRA") || nameUpper.startsWith(" EXTRA")
            
            val finalId = item.id ?: java.util.UUID.randomUUID().toString()
            android.util.Log.d("OrderSyncDebug", "dtoToEntity item: name=${item.menuItemName}, original_id=${item.id}, final_id=$finalId")
            
            OrderItem(
                id = finalId,
                menuItemId = item.menuItemId,
                name = item.menuItemName,
                quantity = item.quantity,
                unitPrice = item.unitPrice,
                subtotal = item.subtotal,
                isChild = isChild
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
            customerReceiptPrinted = dto.customerReceiptPrinted ?: false,
            cancellationStatus = dto.cancellationStatus,
            cancellationUserName = dto.cancellationUserName,
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

private fun isToday(timestamp: Long, today: LocalDate): Boolean {
    val orderDate = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    return orderDate == today
}
