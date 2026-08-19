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
import com.sukashawarma.pos.data.remote.dto.ParseReceiptPayload
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
    val change: Double,
    val orderEntity: com.sukashawarma.pos.data.local.entity.LocalOrderEntity,
    val outletName: String
)

data class CartTotals(
    val subtotal: Double,
    val discount: Double,
    val promoSubsidyAmount: Double,
    val total: Double,
    val missingAmount: Double? = null,
    val appliedPromoNames: List<String> = emptyList()
)

enum class PromoStatus { ACTIVE, SCHEDULED, EXPIRED, QUOTA_EXCEEDED, INACTIVE }

data class PromoStatusEntry(
    val promo: Promo,
    val status: PromoStatus,
    val statusLabel: String
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
    /** Channel WebSocket `outlet_promos` — begitu admin ubah promo di website, ini
     *  memicu fetch ulang seketika. Tidak ada polling di jalur ini sama sekali. */
    private var promoRealtime: com.sukashawarma.pos.data.remote.realtime.PromoRealtimeManager? = null

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
    
    val isScanningReceipt = MutableStateFlow(false)

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
                    connectPromoRealtime(outletId)
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

    /** Begitu admin ubah promo di website, event WebSocket ini memicu fetch ulang
     *  seketika — bukan polling — supaya kasir tidak perlu logout/masuk ulang. */
    private fun connectPromoRealtime(outletId: String) {
        if (promoRealtime == null) {
            promoRealtime = com.sukashawarma.pos.data.remote.realtime.PromoRealtimeManager(
                SupabaseClient.okHttpClient,
                viewModelScope
            ).apply {
                onPromoChanged = {
                    fetchActivePromos(currentOutletId.value)
                    com.sukashawarma.pos.data.remote.GlobalEventBus.promoEvent.tryEmit(Unit)
                }
            }
        }
        promoRealtime?.connect(outletId)
    }

    override fun onCleared() {
        super.onCleared()
        promoRealtime?.disconnect()
        promoRealtime = null
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
                            name = dto.promoName ?: "Promo (ID: ${dto.id.take(4)})",
                            scope = scope,
                            menuItemId = dto.menuItemId,
                            discountType = type,
                            discountValue = dto.discountValue ?: 0.0,
                            minPurchase = dto.minPurchase,
                            isActive = dto.isActive ?: false,
                            usageLimit = dto.usageLimit,
                            currentUsage = dto.currentUsage ?: 0,
                            startDate = dto.startDate?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
                            endDate = dto.endDate?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
                            dailyStartTime = dto.dailyStartTime,
                            dailyEndTime = dto.dailyEndTime,
                            applyToFoodApps = dto.applyToFoodApps ?: false
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
    // AI Receipt Scanner
    // ---------------------------------------------------------------------

    fun handleScanImage(base64Image: String) {
        viewModelScope.launch {
            try {
                isScanningReceipt.value = true
                val menuText = menuItems.value.joinToString("\n") { "${it.id} - ${it.name} - Rp${it.price}" }
                
                val payload = ParseReceiptPayload(imageBase64 = base64Image, menuText = menuText)
                val response = api.parseReceipt("https://app.sukashawarma.com/api/parse-receipt", payload)
                
                if (response.isSuccessful) {
                    val data = response.body()
                    if (data?.error != null) {
                        orderErrorMessage.value = data.error
                    } else {
                        val newLines = mutableListOf<CartLine>()
                        data?.items?.forEach { parsed ->
                            val matchedMenu = menuItems.value.find { m -> m.name.equals(parsed.name, ignoreCase = true) }
                            if (matchedMenu != null) {
                                newLines.add(
                                    CartLine(
                                        cartItemId = UUID.randomUUID().toString(),
                                        menuItemId = matchedMenu.id,
                                        name = matchedMenu.name,
                                        unitPrice = priceFor(matchedMenu),
                                        quantity = parsed.qty,
                                        note = if (parsed.matched) "" else "UNMATCHED: PILIH MANUAL"
                                    )
                                )
                            } else {
                                newLines.add(
                                    CartLine(
                                        cartItemId = UUID.randomUUID().toString(),
                                        menuItemId = "unmatched",
                                        name = parsed.name,
                                        unitPrice = parsed.price ?: 0.0,
                                        quantity = parsed.qty,
                                        note = "UNMATCHED: PILIH MANUAL"
                                    )
                                )
                            }
                        }
                        data?.subsidies?.forEach { sub ->
                            newLines.add(
                                CartLine(
                                    cartItemId = UUID.randomUUID().toString(),
                                    menuItemId = "subsidy",
                                    name = sub.name,
                                    unitPrice = sub.amount,
                                    quantity = 1,
                                    note = ""
                                )
                            )
                        }
                        cartLines.value = cartLines.value + newLines
                    }
                } else {
                    orderErrorMessage.value = "Gagal memproses nota: ${response.message()}"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                orderErrorMessage.value = "Terjadi kesalahan saat memproses gambar."
            } finally {
                isScanningReceipt.value = false
            }
        }
    }

    // ---------------------------------------------------------------------
    // Item detail modal — port of §5 (page.tsx:78-82, 430-443, 1206-1338)
    // ---------------------------------------------------------------------

    /** Categories whose name suggests a single-tap item (drink/topping) skip the modal
     *  entirely (page.tsx:434-437). */
    private val directAddCategoryHints = listOf("drink", "minuman", "topping")

    /** Baris "polos" untuk menuItemId ini: tanpa catatan/extra/pilihan paket, dan tidak
     *  punya anak (extra) yang menempel — satu-satunya bentuk baris yang aman digabung,
     *  karena baris dengan catatan/extra berbeda bisa mewakili konfigurasi yang berbeda. */
    private fun findPlainLine(menuItemId: String): CartLine? =
        cartLines.value.firstOrNull { line ->
            line.menuItemId == menuItemId &&
                line.parentId == null &&
                line.note.isBlank() &&
                line.packageChoices.isEmpty() &&
                cartLines.value.none { it.parentId == line.cartItemId }
        }

    fun onMenuItemClick(item: MenuItem) {
        val categoryName = categories.value.find { it.id == item.categoryId }?.name.orEmpty().lowercase()
        if (directAddCategoryHints.any { categoryName.contains(it) }) {
            val existing = findPlainLine(item.id)
            if (existing != null) {
                setLineQuantity(existing.cartItemId, existing.quantity + 1)
            } else {
                cartLines.value = cartLines.value + CartLine(
                    menuItemId = item.id,
                    name = item.name,
                    unitPrice = priceFor(item),
                    quantity = 1
                )
            }
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

        // Tambahan polos (tanpa catatan/extra/paket) digabung ke baris yang sudah ada
        // di keranjang alih-alih bikin baris baru — quantity-nya saja yang naik.
        if (note.isBlank() && choices.isEmpty() && extraLines.isEmpty()) {
            val existing = findPlainLine(menu.id)
            if (existing != null) {
                setLineQuantity(existing.cartItemId, existing.quantity + qty)
                closeItemModal()
                return
            }
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
        val activeChannel = if (mode.value == OrderMode.WEBSITE) "website" else channel.value
        val calc = calculateCartUseCase.execute(items, activePromos.value, activeChannel)
        val subsidy = promoSubsidyAmount()

        // Hint: nearest global promo the cart hasn't reached min_purchase for yet.
        var missingAmount: Double? = null
        val nearGlobalPromo = activePromos.value
            .filter { it.isActive && it.scope == PromoScope.GLOBAL && it.minPurchase != null }
            .minByOrNull { it.minPurchase!! }
        if (nearGlobalPromo != null && calc.subtotal > 0 && calc.subtotal < nearGlobalPromo.minPurchase!!) {
            missingAmount = nearGlobalPromo.minPurchase - calc.subtotal
        }

        return CartTotals(
            subtotal = calc.subtotal,
            discount = calc.totalDiscount,
            promoSubsidyAmount = subsidy,
            total = maxOf(0.0, calc.finalTotal - subsidy),
            missingAmount = missingAmount,
            appliedPromoNames = calc.appliedPromoNames
        )
    }

    /** Status kasat mata per promo aktif-di-outlet — supaya kasir bisa lihat langsung kalau
     *  promo yang admin buat baru "terjadwal" (belum masuk window tanggalnya) alih-alih
     *  menduga-duga kenapa diskon tidak muncul. */
    fun promoStatusEntries(promos: List<Promo> = activePromos.value, now: Long = System.currentTimeMillis()): List<PromoStatusEntry> {
        val isFoodAppTab = mode.value == OrderMode.ONLINE
        return promos.map { promo ->
            var status = when {
                !promo.isActive -> PromoStatus.INACTIVE
                isFoodAppTab && !promo.applyToFoodApps -> PromoStatus.INACTIVE
                promo.usageLimit != null && promo.currentUsage >= promo.usageLimit -> PromoStatus.QUOTA_EXCEEDED
                promo.endDate != null && now >= promo.endDate -> PromoStatus.EXPIRED
                promo.startDate != null && now < promo.startDate -> PromoStatus.SCHEDULED
                else -> PromoStatus.ACTIVE
            }

            if (status == PromoStatus.ACTIVE && promo.dailyStartTime != null && promo.dailyEndTime != null) {
                fun parseTimeStr(timeStr: String?): Long? {
                    if (timeStr.isNullOrBlank()) return null
                    val parts = timeStr.split(":")
                    if (parts.size < 2) return null
                    val h = parts[0].toLongOrNull() ?: return null
                    val m = parts[1].toLongOrNull() ?: return null
                    val s = if (parts.size > 2) parts[2].toLongOrNull() ?: 0L else 0L
                    return (h * 60 * 60 + m * 60 + s) * 1000
                }
                
                val dailyStart = parseTimeStr(promo.dailyStartTime)
                val dailyEnd = parseTimeStr(promo.dailyEndTime)
                
                if (dailyStart != null && dailyEnd != null) {
                    val wibOffset = 7L * 60L * 60L * 1000L
                    val dayMs = 24L * 60L * 60L * 1000L
                    val nowTimeInDay = (now + wibOffset) % dayMs
                    
                    if (dailyStart <= dailyEnd) {
                        if (nowTimeInDay < dailyStart || nowTimeInDay >= dailyEnd) status = PromoStatus.SCHEDULED
                    } else {
                        if (nowTimeInDay < dailyStart && nowTimeInDay >= dailyEnd) status = PromoStatus.SCHEDULED
                    }
                }
            }

            val label = when {
                !promo.isActive -> "Nonaktif"
                isFoodAppTab && !promo.applyToFoodApps -> "Tidak berlaku untuk Food App"
                status == PromoStatus.ACTIVE -> "Aktif sekarang"
                status == PromoStatus.SCHEDULED -> "Terjadwal mulai ${formatPromoDateTime(promo.startDate)}"
                status == PromoStatus.EXPIRED -> "Sudah berakhir ${formatPromoDateTime(promo.endDate)}"
                status == PromoStatus.QUOTA_EXCEEDED -> "Kuota pemakaian habis"
                else -> "Nonaktif"
            }
            PromoStatusEntry(promo, status, label)
        }.sortedBy { it.status.ordinal }
    }

    /** Promo per-menu paling relevan untuk ditampilkan di card menu (badge + harga coret) —
     *  promo aktif diprioritaskan di atas yang cuma terjadwal, meniru tampilan situs web. */
    fun promoStatusForMenuItem(menuItemId: String, promos: List<Promo> = activePromos.value, now: Long = System.currentTimeMillis()): PromoStatusEntry? {
        return promoStatusEntries(promos, now)
            .filter { it.promo.scope == PromoScope.ITEM && it.promo.menuItemId == menuItemId }
            .minByOrNull { it.status.ordinal }
    }

    /** Harga per-unit setelah potongan promo aktif — dipakai buat tampilan harga-coret di card menu. */
    fun discountedPriceFor(item: MenuItem, promo: Promo): Double {
        val price = priceFor(item)
        return when (promo.discountType) {
            DiscountType.PERCENTAGE -> price * (1 - promo.discountValue / 100.0)
            DiscountType.NOMINAL -> maxOf(0.0, price - promo.discountValue)
        }
    }

    private fun formatPromoDateTime(millis: Long?): String {
        if (millis == null) return "-"
        return runCatching {
            val formatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")
                .withZone(java.time.ZoneId.of("Asia/Jakarta"))
            formatter.format(Instant.ofEpochMilli(millis))
        }.getOrDefault("-")
    }

    /** Label tanggal-jam singkat WIB buat badge "TERJADWAL" di card menu — meniru
     *  format situs web ("17 Agu, 17.00 WIB"). */
    fun scheduleLabelShort(promo: Promo): String? {
        val millis = promo.startDate ?: return null
        return runCatching {
            val formatter = DateTimeFormatter.ofPattern("d MMM, HH.mm", java.util.Locale("id", "ID"))
                .withZone(java.time.ZoneId.of("Asia/Jakarta"))
            "${formatter.format(Instant.ofEpochMilli(millis))} WIB"
        }.getOrNull()
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
        OrderMode.ONLINE -> listOf(PaymentMethod.QRIS, PaymentMethod.CARD)
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
        // NET_CAPABILITY_INTERNET = interface jaringan ada (WiFi/data aktif).
        // NET_CAPABILITY_VALIDATED = OS sudah membuktikan koneksi bisa mencapai
        // internet sungguhan (bukan captive portal / koneksi putus di tengah).
        // Keduanya harus terpenuhi agar request ke Supabase punya peluang berhasil.
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
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
            // Endorse ditandai lewat channel agar bisa dibedakan dari walk-in POS biasa
            // (source-nya sama-sama POS) — dipakai badge "Endorse" di kartu order/riwayat/laporan.
            val activeChannel = when (mode.value) {
                OrderMode.WEBSITE -> "website"
                OrderMode.ENDORSE -> "endorse"
                else -> channel.value
            }
            val isWalkInLike = mode.value == OrderMode.WALKIN || mode.value == OrderMode.ENDORSE
            val amountReceived = if (selectedPayment == PaymentMethod.CASH) cashAmount() else totals.total

            val online = isNetworkAvailable()
            val today = com.sukashawarma.pos.domain.gate.JakartaTime.today()
            val startOfDay = com.sukashawarma.pos.domain.gate.JakartaTime.startOfDayMillis(today)
            val endOfDay = com.sukashawarma.pos.domain.gate.JakartaTime.endOfDayMillis(today)
            val maxToday = orderDao.getMaxOrderNumberToday(outletId, startOfDay, endOfDay) ?: 0
            
            val maxServer = maxToday // We don't have separate maxServer anymore, just continue from maxToday

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
                lastOfflineOrderNumber = maxToday,
                channel = activeChannel,
                source = if (isWalkInLike) OrderSource.POS else OrderSource.ONLINE,
                additionalDiscount = totals.promoSubsidyAmount,
                cashierName = currentUsername.value
            )
            val effectiveReleaseTime = if (mode.value == OrderMode.WEBSITE) {
                com.sukashawarma.pos.domain.usecase.PreparingOrderClassifier.effectiveReleaseTime(
                    createdAt = order.createdAt,
                    releaseTime = null,
                    pickupTime = pickupTime.value,
                    notes = null
                )
            } else 0L
            val pickupTimeIso = effectiveReleaseTime.takeIf { it > 0L }?.let {
                DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(it + 20 * 60_000L))
            }
            val releaseTimeIso = effectiveReleaseTime.takeIf { it > 0L }?.let {
                DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(it))
            }

            var pendingSync = !online
            // Beda dari "device benar-benar tidak ada internet" — dipakai supaya pesan ke
            // kasir tidak salah menuduh koneksi padahal request ke server yang gagal/ditolak.
            var syncFailedWhileOnline = false
            var serverOrderNumber = order.orderNumber

            var localPaymentProofPath: String? = null

            if (online) {
                try {
                    // Pastikan token masih valid sebelum hit server — kalau sudah
                    // expired di-refresh di sini, bukan setelah request gagal 401.
                    com.sukashawarma.pos.data.remote.AuthSessionManager.ensureAuthenticated()
                    val createdAtIso = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(order.createdAt))
                    val payload = CreateOrderPayload(
                        id = order.id,
                        orderNumber = order.orderNumber,
                        outletId = order.outletId,
                        customerName = order.customerName,
                        status = order.status.name.lowercase(),
                        source = order.source.name.lowercase(),
                        paymentMethod = order.paymentMethod.name.lowercase(),
                        discountAmount = order.discountAmount.toLong(),
                        promoSubsidy = order.promoSubsidy.toLong(),
                        totalAmount = order.totalAmount.toLong(),
                        amountReceived = order.amountReceived.toLong(),
                        changeAmount = order.changeAmount.toLong(),
                        createdAt = createdAtIso,
                        channel = order.channel,
                        pickupTime = pickupTimeIso,
                        releaseTime = releaseTimeIso,
                        cashierName = order.cashierName
                    )
                    val orderRes = api.createOrder(payload)
                    if (orderRes.isSuccessful && !orderRes.body().isNullOrEmpty()) {
                        serverOrderNumber = orderRes.body()!!.first().orderNumber

                        val itemPayloads = order.items.map { item ->
                            CreateOrderItemPayload(
                                orderId = order.id,
                                menuItemId = item.menuItemId,
                                menuItemName = item.encodedMenuItemName(),
                                quantity = item.quantity,
                                unitPrice = item.unitPrice.toLong(),
                                subtotal = item.subtotal.toLong()
                            )
                        }
                        api.createOrderItems(itemPayloads)

                        order.appliedPromoIds.forEach { promoId ->
                            try {
                                api.incrementPromoUsage(
                                    com.sukashawarma.pos.data.remote.dto.IncrementPromoUsagePayload(promoId = promoId)
                                )
                            } catch (e: Exception) {
                                android.util.Log.e("POSManualOrderVM", "Gagal increment_promo_usage untuk promo $promoId", e)
                            }
                        }

                        qrisProofBitmap.value?.let { bitmap ->
                            uploadPaymentProof(outletId = order.outletId, orderId = order.id, orderNumber = serverOrderNumber, bitmap = bitmap)
                        }
                    } else {
                        // Device online tapi request-nya sendiri gagal (validasi, RLS, timeout, dll) —
                        // dicatat di logcat karena sebelumnya gagal total tanpa jejak, membuat order
                        // yang bermasalah tampak "OFFLINE" selamanya tanpa cara mendiagnosis kenapa.
                        android.util.Log.e(
                            "POSManualOrderVM",
                            "Submit order ${order.id} gagal walau online: HTTP ${orderRes.code()} ${orderRes.errorBody()?.string().orEmpty()}"
                        )
                        pendingSync = true
                        syncFailedWhileOnline = true
                    }
                } catch (e: Exception) {
                    android.util.Log.e("POSManualOrderVM", "Submit order ${order.id} melempar exception walau online", e)
                    pendingSync = true
                    syncFailedWhileOnline = true
                }
            }

            if (pendingSync) {
                // Save QRIS proof locally for later upload
                qrisProofBitmap.value?.let { bitmap ->
                    try {
                        val fileName = "${order.outletId}_${serverOrderNumber}_${LocalDate.now()}_offline.jpg"
                        val context = getApplication<Application>().applicationContext
                        val file = java.io.File(context.cacheDir, fileName)
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            java.io.FileOutputStream(file).use { out ->
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                            }
                        }
                        localPaymentProofPath = file.absolutePath
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
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
                promoSubsidy = order.promoSubsidy,
                totalAmount = order.totalAmount,
                amountReceived = order.amountReceived,
                changeAmount = order.changeAmount,
                kitchenReceiptPrinted = false,
                customerReceiptPrinted = false,
                cancellationStatus = null,
                cancellationUserName = null,
                createdAt = order.createdAt,
                syncState = if (pendingSync) com.sukashawarma.pos.data.local.entity.SyncState.PENDING.name else com.sukashawarma.pos.data.local.entity.SyncState.SYNCED.name,
                dirtyFields = "",
                channel = order.channel,
                isSyncedFromOffline = false,
                localPaymentProofPath = localPaymentProofPath,
                notes = order.notes,
                effectiveReleaseTime = effectiveReleaseTime,
                cashierName = order.cashierName
            )
            orderDao.insertOrder(entity)
            // Jangan tunggu echo realtime dari server: layar lain (target harian,
            // laporan, histori, laci kasir) harus ikut berubah begitu pesanan
            // tersimpan, termasuk saat pesanan masih tersimpan lokal/offline.
            com.sukashawarma.pos.data.remote.GlobalEventBus.orderSyncEvent.tryEmit(Unit)
            com.sukashawarma.pos.data.remote.GlobalEventBus.targetRefreshEvent.tryEmit(Unit)

            orderSuccessInfo.value = OrderSuccessInfo(
                orderNumber = serverOrderNumber,
                method = selectedPayment,
                change = order.changeAmount,
                orderEntity = entity,
                outletName = currentOutletName.value
            )



            if (pendingSync) {
                orderErrorMessage.value = if (syncFailedWhileOnline) {
                    "Order tersimpan lokal (offline) dengan nomor sementara #$serverOrderNumber. " +
                        "Koneksi internet ada, tapi server menolak/gagal merespons — akan dicoba " +
                        "sinkron ulang otomatis."
                } else {
                    "Order tersimpan lokal (offline) dengan nomor sementara #$serverOrderNumber. " +
                        "Akan di-sinkronkan otomatis saat koneksi internet tersedia."
                }
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
