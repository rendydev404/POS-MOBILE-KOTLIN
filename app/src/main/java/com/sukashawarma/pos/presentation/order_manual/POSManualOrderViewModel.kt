package com.sukashawarma.pos.presentation.order_manual

import android.app.Application
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sukashawarma.pos.POSApplication
import com.sukashawarma.pos.data.local.entity.LocalOrderEntity
import com.sukashawarma.pos.data.remote.SupabaseClient
import com.sukashawarma.pos.data.remote.dto.CreateOrderItemPayload
import com.sukashawarma.pos.data.remote.dto.CreateOrderPayload
import com.sukashawarma.pos.domain.menu.KioskSettings
import com.sukashawarma.pos.domain.menu.isItemAvailable
import com.sukashawarma.pos.domain.model.*
import com.sukashawarma.pos.domain.usecase.CalculateCartUseCase
import com.sukashawarma.pos.domain.usecase.CreateOrderUseCase
import com.google.gson.Gson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Result shown on the post-submit success overlay (order-manual's `success`/`walkInSuccess`). */
data class OrderSuccessInfo(
    val orderNumber: Int,
    val method: PaymentMethod,
    val change: Double
)

data class CartTotals(
    val subtotal: Double,
    val discount: Double,
    val promoSubsidyAmount: Double,
    val total: Double
)

/**
 * Port of apps/pos-kasir/app/kasir/order-manual/page.tsx.
 *
 * Deliberately NOT ported here (see commit messages): the AI receipt-scan feature —
 * it needs a Next.js API route (`/api/parse-receipt`) this app has no auth-compatible
 * way to call (that route likely expects the web's NextAuth session, not the Supabase
 * JWT this app authenticates with).
 *
 * QRIS payment-proof capture *is* ported (camera/gallery -> Supabase Storage bucket
 * `payment_proofs` -> orders.payment_proof_url), see qrisProofBitmap/uploadPaymentProof.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class POSManualOrderViewModel(application: Application) : AndroidViewModel(application) {
    private val database = (application as POSApplication).database
    private val orderDao = database.orderDao()
    private val calculateCartUseCase = CalculateCartUseCase()
    private val createOrderUseCase = CreateOrderUseCase(calculateCartUseCase)
    private val api = SupabaseClient.api
    private val repository = (application as POSApplication).menuRepository
    private val gson = Gson()

    val currentOutletId = MutableStateFlow("")
    val currentOutletName = MutableStateFlow("")
    val currentUsername = MutableStateFlow("")
    val categories = MutableStateFlow<List<Category>>(emptyList())
    val menuItems = MutableStateFlow<List<MenuItem>>(emptyList())
    val isLoading = MutableStateFlow(false)
    private val kioskSettings = MutableStateFlow(KioskSettings.EMPTY)

    val selectedCategoryId = MutableStateFlow("")
    val searchQuery = MutableStateFlow("")

    // --- order-manual page state (mirrors page.tsx's mode/channel/payment/etc state) ---
    val mode = MutableStateFlow(OrderMode.WALKIN)
    val channel = MutableStateFlow<String?>(null)
    val payment = MutableStateFlow<PaymentMethod?>(null)
    val customerName = MutableStateFlow("")
    val pickupTime = MutableStateFlow("")
    val promoSubsidy = MutableStateFlow("")
    val cashInput = MutableStateFlow("")
    val showInfoBanner = MutableStateFlow(true)

    val cartLines = MutableStateFlow<List<CartLine>>(emptyList())
    val activePromos = MutableStateFlow<List<Promo>>(emptyList())

    // --- item detail modal state ---
    val selectedMenu = MutableStateFlow<MenuItem?>(null)
    val selectedMenuQty = MutableStateFlow(1)
    val selectedMenuNote = MutableStateFlow("")
    val selectedExtras = MutableStateFlow<Map<String, Int>>(emptyMap())
    val selectedPackageChoices = MutableStateFlow<Map<String, String>>(emptyMap())

    val isQrisModalOpen = MutableStateFlow(false)
    /** Captured/picked photo of the transfer receipt — port of the web's QrisPaymentModal
     *  "Transfer (Bukti)" tab file selection (QrisPaymentModal.tsx). Uploaded to Supabase
     *  Storage only after the order itself is created (see submitOrder/uploadPaymentProof),
     *  same order as the web's handleWalkInPay (page.tsx:684-710). */
    val qrisProofBitmap = MutableStateFlow<Bitmap?>(null)
    val isSubmitting = MutableStateFlow(false)
    val orderErrorMessage = MutableStateFlow<String?>(null)
    val orderSuccessInfo = MutableStateFlow<OrderSuccessInfo?>(null)

    init {
        collectMenuSnapshot()
    }

    private fun collectMenuSnapshot() {
        viewModelScope.launch {
            currentOutletId.filter { it.isNotBlank() }
                .flatMapLatest { outletId ->
                    // Also fetch promos for this outlet asynchronously
                    fetchActivePromos(outletId)
                    repository.snapshot(outletId)
                }
                .collect { snapshot ->
                    categories.value = snapshot.categories
                    if (snapshot.categories.isNotEmpty() && selectedCategoryId.value.isEmpty()) {
                        selectedCategoryId.value = snapshot.categories.first().id
                    }
                    menuItems.value = snapshot.items
                    kioskSettings.value = snapshot.settings
                    isLoading.value = false
                }
        }
    }

    private fun fetchActivePromos(outletId: String) {
        viewModelScope.launch {
            try {
                val response = api.getPromos("eq.$outletId", "eq.true")
                if (response.isSuccessful) {
                    val promoDtos = response.body() ?: emptyList()
                    val mappedPromos = promoDtos.mapNotNull { dto ->
                        val scope = when(dto.scope?.lowercase()) {
                            "global" -> com.sukashawarma.pos.domain.model.PromoScope.GLOBAL
                            "item" -> com.sukashawarma.pos.domain.model.PromoScope.ITEM
                            else -> return@mapNotNull null
                        }
                        val type = when(dto.discountType?.lowercase()) {
                            "percentage" -> com.sukashawarma.pos.domain.model.DiscountType.PERCENTAGE
                            "nominal" -> com.sukashawarma.pos.domain.model.DiscountType.NOMINAL
                            else -> return@mapNotNull null
                        }
                        com.sukashawarma.pos.domain.model.Promo(
                            id = dto.id,
                            outletId = dto.outletId,
                            name = "Promo (ID: ${dto.id.take(4)})", // Placeholder since Dto lacks name
                            scope = scope,
                            menuItemId = null, // Backend handles item mapping differently if needed
                            discountType = type,
                            discountValue = dto.discountValue ?: 0.0,
                            isActive = dto.isActive ?: false
                        )
                    }
                    activePromos.value = mappedPromos
                }
            } catch (e: Exception) {
                // Ignore for offline mode, wait for retry mechanism if needed
            }
        }
    }

    // ---------------------------------------------------------------------
    // Mode / channel switching — port of handleSwitchMode (page.tsx:149-166)
    // ---------------------------------------------------------------------

    fun switchMode(newMode: OrderMode) {
        mode.value = newMode
        channel.value = if (newMode == OrderMode.WEBSITE) "website" else null
        payment.value = if (newMode == OrderMode.ENDORSE) PaymentMethod.CASH else null
        customerName.value = ""
        pickupTime.value = ""
        promoSubsidy.value = ""
        cashInput.value = ""
        cartLines.value = emptyList()
        searchQuery.value = ""
        orderErrorMessage.value = null
        qrisProofBitmap.value = null
    }

    fun selectChannel(channelId: String) {
        channel.value = channelId
        promoSubsidy.value = ""
    }

    // ---------------------------------------------------------------------
    // Menu grid — port of `visibleItems` (page.tsx:340-388)
    // ---------------------------------------------------------------------

    fun visibleItems(): List<MenuItem> {
        val cat = selectedCategoryId.value
        val query = searchQuery.value
        val currentMode = mode.value
        val activeChannel = normalizeChannel(if (currentMode == OrderMode.WEBSITE) "website" else channel.value)

        return menuItems.value.filter { item ->
            if (cat.isNotBlank() && item.categoryId != cat) return@filter false
            if (query.isNotBlank() && !item.name.contains(query, ignoreCase = true)) return@filter false

            when (currentMode) {
                OrderMode.WALKIN, OrderMode.ENDORSE ->
                    item.availableOnlineChannels.isEmpty() ||
                        item.availableOnlineChannels.any { normalizeChannel(it) == "pos_kasir" }
                OrderMode.ONLINE ->
                    if (activeChannel == null) {
                        true
                    } else if (!item.isAvailableOnline) {
                        false
                    } else {
                        item.availableOnlineChannels.isEmpty() ||
                            item.availableOnlineChannels.any { normalizeChannel(it) == activeChannel }
                    }
                OrderMode.WEBSITE -> true
            }
        }
    }

    fun isDisabled(item: MenuItem): Boolean = !isItemAvailable(item, kioskSettings.value)

    fun cartQuantityFor(menuItemId: String): Int =
        cartLines.value.filter { it.menuItemId == menuItemId }.sumOf { it.quantity }

    /** Port of `wrappedCalculateItemPrice` (page.tsx:449-452) — channel_prices override,
     *  forced to 0 for endorse. */
    fun priceFor(item: MenuItem): Double {
        if (mode.value == OrderMode.ENDORSE) return 0.0
        val activeChannel = normalizeChannel(if (mode.value == OrderMode.WEBSITE) "website" else channel.value)
        if (activeChannel != null) {
            item.channelPrices[activeChannel]?.let { return it }
        }
        return item.price
    }

    val upsellItems: StateFlow<List<MenuItem>> = combine(menuItems, kioskSettings) { items, settings ->
        android.util.Log.d("UPSELL_TEST", "menuItems count: ${items.size}, upsells count in settings: ${settings.upsells.size}, upsells: ${settings.upsells}")
        val filtered = items.filter { it.id in settings.upsells && it.isAvailable }
        android.util.Log.d("UPSELL_TEST", "Filtered upsells count: ${filtered.size}")
        filtered
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ---------------------------------------------------------------------
    // Item detail modal — port of §5 (page.tsx:78-82, 430-443, 1206-1338)
    // ---------------------------------------------------------------------

    /** Categories whose name suggests a single-tap item (drink/topping) skip the modal
     *  entirely (page.tsx:434-437). */
    private val directAddCategoryHints = listOf("drink", "minuman", "topping")

    fun onMenuItemClick(item: MenuItem) {
        val categoryName = categories.value.find { it.id == item.categoryId }?.name.orEmpty().lowercase()
        if (directAddCategoryHints.any { categoryName.contains(it) }) {
            cartLines.value = cartLines.value + CartLine(
                menuItemId = item.id,
                name = item.name,
                unitPrice = priceFor(item),
                quantity = 1
            )
            return
        }
        selectedMenu.value = item
        selectedMenuQty.value = 1
        selectedMenuNote.value = ""
        selectedExtras.value = emptyMap()
        selectedPackageChoices.value = emptyMap()
    }

    fun closeItemModal() {
        selectedMenu.value = null
    }

    fun incrementModalQty() {
        selectedMenuQty.value += 1
    }

    fun decrementModalQty() {
        selectedMenuQty.value = maxOf(1, selectedMenuQty.value - 1)
    }

    /** Toggle 0/1 — extras are include/exclude only, not a full quantity stepper
     *  (page.tsx:1255-1260). */
    fun toggleExtra(extraId: String) {
        val current = selectedExtras.value[extraId] ?: 0
        selectedExtras.value = selectedExtras.value + (extraId to if (current > 0) 0 else 1)
    }

    fun selectPackageChoice(packageItemId: String, chosenMenuItemId: String) {
        selectedPackageChoices.value = selectedPackageChoices.value + (packageItemId to chosenMenuItemId)
    }

    /** Port of the modal's "Tambah ke Keranjang" submit (page.tsx:1307-1338). */
    fun confirmAddToCart() {
        val menu = selectedMenu.value ?: return
        val qty = selectedMenuQty.value
        var note = selectedMenuNote.value
        val choices = selectedPackageChoices.value

        if (menu.isPackage && choices.isNotEmpty()) {
            val names = choices.values.mapNotNull { id -> menuItems.value.find { it.id == id }?.name }
            if (names.isNotEmpty()) {
                val label = "Paket: ${names.joinToString(", ")}"
                note = if (note.isBlank()) label else "$note ($label)"
            }
        }

        val parentId = UUID.randomUUID().toString()
        val parentLine = CartLine(
            cartItemId = parentId,
            menuItemId = menu.id,
            name = menu.name,
            unitPrice = priceFor(menu),
            quantity = qty,
            note = note,
            packageChoices = choices
        )
        val extraLines = selectedExtras.value.filter { it.value > 0 }.mapNotNull { (extraId, extraQty) ->
            val extraItem = menuItems.value.find { it.id == extraId } ?: return@mapNotNull null
            CartLine(
                menuItemId = extraItem.id,
                name = extraItem.name,
                unitPrice = priceFor(extraItem),
                quantity = extraQty * qty,
                parentId = parentId
            )
        }

        cartLines.value = cartLines.value + parentLine + extraLines
        closeItemModal()
    }

    // ---------------------------------------------------------------------
    // Cart line editing — port of `setQty` (page.tsx:409-424)
    // ---------------------------------------------------------------------

    fun setLineQuantity(cartItemId: String, qty: Int) {
        if (qty <= 0) {
            val toRemove = cartLines.value
                .filter { it.cartItemId == cartItemId || it.parentId == cartItemId }
                .map { it.cartItemId }
                .toSet()
            cartLines.value = cartLines.value.filterNot { it.cartItemId in toRemove }
            return
        }
        val newQty = minOf(qty, 10)
        cartLines.value = cartLines.value.map { line ->
            when {
                line.cartItemId == cartItemId -> line.copy(quantity = newQty)
                line.parentId == cartItemId -> line.copy(quantity = minOf(line.quantity, newQty))
                else -> line
            }
        }
    }

    // ---------------------------------------------------------------------
    // Totals & validation — port of §4/§6/§7
    // ---------------------------------------------------------------------

    fun cartTotals(): CartTotals {
        val items = cartLines.value.map {
            OrderItem(menuItemId = it.menuItemId, name = it.name, quantity = it.quantity, unitPrice = it.unitPrice, subtotal = it.subtotal)
        }
        val calc = calculateCartUseCase.execute(items, activePromos.value)
        val subsidy = promoSubsidyAmount()
        return CartTotals(
            subtotal = calc.subtotal,
            discount = calc.totalDiscount,
            promoSubsidyAmount = subsidy,
            total = maxOf(0.0, calc.finalTotal - subsidy)
        )
    }

    private fun promoSubsidyAmount(): Double {
        val activeChannel = normalizeChannel(if (mode.value == OrderMode.WEBSITE) "website" else channel.value)
        return if (activeChannel != null && activeChannel in PROMO_SUBSIDY_CHANNELS) {
            promoSubsidy.value.toDoubleOrNull() ?: 0.0
        } else {
            0.0
        }
    }

    private fun cashAmount(): Double = cashInput.value.toDoubleOrNull() ?: 0.0

    /** Port of the dynamic quick-cash amounts (page.tsx:1668-1681 / WalkInCartPanel.tsx:228-241) —
     *  round-ups to the nearest 10k/20k/50k/100k plus fixed denominations, not a static list. */
    fun quickCashAmounts(total: Double): List<Double> {
        if (total <= 0.0) return listOf(20_000.0, 50_000.0, 100_000.0, 150_000.0)
        val options = sortedSetOf<Double>()
        listOf(10_000.0, 20_000.0, 50_000.0, 100_000.0).forEach { step ->
            val rounded = kotlin.math.ceil(total / step) * step
            if (rounded > total) options.add(rounded)
            if (rounded + step > total) options.add(rounded + step)
        }
        listOf(50_000.0, 100_000.0, 150_000.0, 200_000.0, 300_000.0, 500_000.0).forEach { fixed ->
            if (fixed > total) options.add(fixed)
        }
        return options.toList().sorted().take(4)
    }

    fun setExactCash(total: Double) {
        cashInput.value = kotlin.math.ceil(total).toLong().toString()
    }

    /** Port of `canSubmit` (page.tsx:463-464) + WalkInCartPanel.tsx:60-64. */
    fun canSubmit(): Boolean {
        if (cartLines.value.isEmpty()) return false
        if (customerName.value.isBlank()) return false
        val total = cartTotals().total
        return when (mode.value) {
            OrderMode.ENDORSE -> true
            OrderMode.WALKIN -> {
                val p = payment.value ?: return false
                p != PaymentMethod.CASH || (cashAmount() >= total && total > 0)
            }
            OrderMode.ONLINE -> {
                val p = payment.value ?: return false
                if (channel.value == null) return false
                val activeChannel = normalizeChannel(channel.value) ?: ""
                if (activeChannel in PROMO_SUBSIDY_CHANNELS && promoSubsidy.value.isBlank()) return false
                p != PaymentMethod.CASH || (cashAmount() >= total && total > 0)
            }
            OrderMode.WEBSITE -> {
                if (pickupTime.value.isBlank()) return false
                payment.value != null
            }
        }
    }

    /** Payment methods offered per mode — port of §3: walkin=Tunai/QRIS/Debit,
     *  online=QRIS/Tunai, website=QRIS/VA, endorse=none (forced cash/0). */
    fun availablePaymentMethods(): List<PaymentMethod> = when (mode.value) {
        OrderMode.WALKIN -> listOf(PaymentMethod.CASH, PaymentMethod.QRIS, PaymentMethod.CARD)
        OrderMode.ONLINE -> listOf(PaymentMethod.QRIS, PaymentMethod.CASH)
        OrderMode.WEBSITE -> listOf(PaymentMethod.QRIS, PaymentMethod.VA)
        OrderMode.ENDORSE -> emptyList()
    }

    fun clearErrorMessage() {
        orderErrorMessage.value = null
    }

    fun dismissSuccess() {
        orderSuccessInfo.value = null
    }

    fun setQrisProofBitmap(bitmap: Bitmap?) {
        qrisProofBitmap.value = bitmap
    }

    /** Port of the isFoodApp "Tandai Sudah Bayar" shortcut (QrisPaymentModal.tsx:203-266) —
     *  only Food Apps channels may skip the proof photo and mark-as-paid directly. */
    fun canMarkPaidWithoutProof(): Boolean {
        val activeChannel = normalizeChannel(channel.value) ?: return false
        return mode.value == OrderMode.ONLINE && activeChannel in PROMO_SUBSIDY_CHANNELS
    }

    // ---------------------------------------------------------------------
    // Submit — port of handleSubmit (page.tsx:467-627) / handleWalkInPay (page.tsx:630-810)
    // ---------------------------------------------------------------------

    private fun isNetworkAvailable(): Boolean {
        val app = getApplication<Application>()
        val cm = app.getSystemService(Application.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun submitOrder() {
        if (!canSubmit() || isSubmitting.value) return
        val outletId = currentOutletId.value
        if (outletId.isBlank()) {
            orderErrorMessage.value = "Outlet belum siap, silakan login ulang."
            return
        }

        viewModelScope.launch {
            isSubmitting.value = true
            val totals = cartTotals()
            val finalCustomerName = if (mode.value == OrderMode.WEBSITE && pickupTime.value.isNotBlank()) {
                "${customerName.value} [Jam Ambil: ${pickupTime.value}]"
            } else {
                customerName.value
            }
            val selectedPayment = payment.value ?: PaymentMethod.CASH
            val activeChannel = if (mode.value == OrderMode.WEBSITE) "website" else channel.value
            val isWalkInLike = mode.value == OrderMode.WALKIN || mode.value == OrderMode.ENDORSE
            val amountReceived = if (selectedPayment == PaymentMethod.CASH) cashAmount() else totals.total

            val online = isNetworkAvailable()
            val maxOffline = orderDao.getMaxOfflineOrderNumber(outletId) ?: 9000
            val maxServer = orderDao.getMaxServerOrderNumber(outletId) ?: 0

            val orderItems = cartLines.value.map { line ->
                OrderItem(
                    menuItemId = line.menuItemId,
                    name = line.name,
                    quantity = line.quantity,
                    unitPrice = line.unitPrice,
                    subtotal = line.subtotal,
                    note = line.note,
                    isChild = line.parentId != null
                )
            }

            val order = createOrderUseCase.execute(
                outletId = outletId,
                customerName = finalCustomerName,
                items = orderItems,
                paymentMethod = selectedPayment,
                amountReceived = amountReceived,
                activePromos = activePromos.value,
                isOnline = online,
                lastServerOrderNumber = maxServer,
                lastOfflineOrderNumber = maxOffline,
                channel = activeChannel,
                source = if (isWalkInLike) OrderSource.POS else OrderSource.ONLINE,
                additionalDiscount = totals.promoSubsidyAmount
            )

            var pendingSync = !online
            var serverOrderNumber = order.orderNumber

            if (online) {
                try {
                    val createdAtIso = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(order.createdAt))
                    val payload = CreateOrderPayload(
                        id = order.id,
                        outletId = order.outletId,
                        customerName = order.customerName,
                        status = order.status.name.lowercase(),
                        source = order.source.name.lowercase(),
                        paymentMethod = order.paymentMethod.name.lowercase(),
                        discountAmount = order.discountAmount,
                        totalAmount = order.totalAmount,
                        amountReceived = order.amountReceived,
                        changeAmount = order.changeAmount,
                        createdAt = createdAtIso,
                        channel = order.channel
                    )
                    val orderRes = api.createOrder(payload)
                    if (orderRes.isSuccessful && !orderRes.body().isNullOrEmpty()) {
                        serverOrderNumber = orderRes.body()!!.first().orderNumber

                        val itemPayloads = order.items.map { item ->
                            CreateOrderItemPayload(
                                orderId = order.id,
                                menuItemId = item.menuItemId,
                                menuItemName = item.name,
                                quantity = item.quantity,
                                unitPrice = item.unitPrice,
                                subtotal = item.subtotal
                            )
                        }
                        api.createOrderItems(itemPayloads)

                        qrisProofBitmap.value?.let { bitmap ->
                            uploadPaymentProof(outletId = order.outletId, orderId = order.id, orderNumber = serverOrderNumber, bitmap = bitmap)
                        }
                    } else {
                        pendingSync = true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    pendingSync = true
                }
            }

            val entity = LocalOrderEntity(
                id = order.id,
                outletId = order.outletId,
                orderNumber = serverOrderNumber,
                customerName = order.customerName,
                status = order.status.name,
                source = order.source.name,
                paymentMethod = order.paymentMethod.name,
                itemsJson = gson.toJson(order.items),
                subtotal = order.subtotal,
                discountAmount = order.discountAmount,
                totalAmount = order.totalAmount,
                amountReceived = order.amountReceived,
                changeAmount = order.changeAmount,
                kitchenReceiptPrinted = false,
                customerReceiptPrinted = false,
                cancellationStatus = null,
                cancellationUserName = null,
                createdAt = order.createdAt,
                isPendingSync = pendingSync,
                channel = order.channel
            )
            orderDao.insertOrder(entity)

            orderSuccessInfo.value = OrderSuccessInfo(
                orderNumber = serverOrderNumber,
                method = selectedPayment,
                change = order.changeAmount
            )

            // Auto-print receipt if a printer is configured
            val printerMac = com.sukashawarma.pos.data.local.PrinterPrefs.getSelectedMac()
            if (!printerMac.isNullOrBlank()) {
                try {
                    val printerManager = com.sukashawarma.pos.data.bluetooth.BluetoothPrinterManager()
                    val connected = printerManager.ensureConnected(printerMac)
                    if (connected) {
                        // Customer Receipt Auto Print removed to match Web POS and save paper
                        // Cashier can print manually via "Cetak Ulang Struk"
                        
                        // Print Kitchen Ticket
                        val kitchenBytes = com.sukashawarma.pos.domain.printer.ReceiptPrinter.generateReceiptBytes(
                            order = order.copy(orderNumber = serverOrderNumber),
                            isKitchen = true,
                            cashierName = currentUsername.value,
                            outletName = currentOutletName.value
                        )
                        printerManager.printBytesChunked(kitchenBytes)
                        
                        // Mark as printed locally
                        val updatedEntity = entity.copy(kitchenReceiptPrinted = true)
                        orderDao.insertOrder(updatedEntity) // Update entity
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (pendingSync) {
                orderErrorMessage.value = "Order tersimpan lokal (offline) dengan nomor sementara #$serverOrderNumber. " +
                    "Auto-sync ke server belum aktif di versi ini — sinkronkan manual saat online."
            }

            // Reset cart for the next order, keep the active mode (page.tsx resets
            // lines/channel/payment/customerName/promoSubsidy but stays on the same tab).
            cartLines.value = emptyList()
            channel.value = if (mode.value == OrderMode.WEBSITE) "website" else null
            payment.value = if (mode.value == OrderMode.ENDORSE) PaymentMethod.CASH else null
            customerName.value = ""
            pickupTime.value = ""
            promoSubsidy.value = ""
            cashInput.value = ""
            qrisProofBitmap.value = null
            isSubmitting.value = false
        }
    }

    /** Port of the web's post-order-creation proof upload (page.tsx:684-710): upload to
     *  Supabase Storage bucket `payment_proofs`, then PATCH the order's payment_proof_url
     *  with the resulting public URL. Filename mirrors the web's
     *  `${OUTLET}_{order_number}_{yyyy-mm-dd}.{ext}` pattern, using outletId in place of
     *  the web's outlet slug (this app doesn't have the slug on hand here, and either
     *  string is just a unique storage key). Non-fatal on failure — the order itself is
     *  already created; only the proof photo attachment is lost. */
    private suspend fun uploadPaymentProof(outletId: String, orderId: String, orderNumber: Int, bitmap: Bitmap) {
        try {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            val fileName = "${outletId}_${orderNumber}_${LocalDate.now()}.jpg"
            val objectPath = "payment_proofs/$fileName"
            val body = stream.toByteArray().toRequestBody("image/jpeg".toMediaTypeOrNull())

            val uploadRes = api.uploadPaymentProof(objectPath = objectPath, contentType = "image/jpeg", file = body)
            if (uploadRes.isSuccessful) {
                val publicUrl = "${SupabaseClient.BASE_URL}storage/v1/object/public/$objectPath"
                api.updateOrderStatus(orderIdFilter = "eq.$orderId", patch = mapOf("payment_proof_url" to publicUrl))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
