package com.sukashawarma.pos.domain.model

enum class OrderStatus {
    PENDING,    // Menunggu Pembayaran / Masuk
    PREPARING,  // Sedang Dimasak di Dapur
    READY,      // Siap Diambil
    COMPLETED,  // Selesai / Lunas
    CANCELLED   // Dibatalkan
}

enum class OrderSource {
    POS,        // Kasir Manual
    KIOSK,      // Self-order Tablet Kiosk
    ONLINE      // Grab/Go/Shopee Food
}

enum class PaymentMethod {
    CASH,
    QRIS,
    CARD,
    VA
}

data class OrderItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val menuItemId: String,
    val name: String,
    val quantity: Int,
    val unitPrice: Double,
    val subtotal: Double = unitPrice * quantity,
    val note: String = "",
    val isChild: Boolean = false,
    val isPromoReward: Boolean = false,
    val promoId: String? = null,
    val promoName: String? = null,
    val promoBuyQuantity: Int? = null,
    val promoGetQuantity: Int? = null,
    val originalUnitPrice: Double? = null
) {
    /**
     * `order_items` (Supabase) tidak punya kolom note sendiri — [note] harus
     * disisipkan ke `menu_item_name` pakai delimiter `|NOTE|`, konvensi yang
     * sudah dipakai OrderCard/OrderHistoryScreen saat parsing nama item balik.
     * Selalu pakai ini saat membangun payload create-order-item ke server.
     */
    fun encodedMenuItemName(): String =
        if (note.isBlank()) name else "$name|NOTE|$note"
}

data class Order(
    val id: String = java.util.UUID.randomUUID().toString(),
    val outletId: String,
    val orderNumber: Int,
    val customerName: String,
    val status: OrderStatus = OrderStatus.PENDING,
    val source: OrderSource = OrderSource.POS,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val items: List<OrderItem> = emptyList(),
    val subtotal: Double = 0.0,
    val discountAmount: Double = 0.0,
    val promoSubsidy: Double = 0.0,
    val totalAmount: Double = 0.0,
    val amountReceived: Double = 0.0,
    val changeAmount: Double = 0.0,
    val kitchenReceiptPrinted: Boolean = false,
    val customerReceiptPrinted: Boolean = false,
    val cancellationStatus: String? = null,
    val cancellationUserName: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isOffline: Boolean = false,
    val isSyncedFromOffline: Boolean = false,
    val channel: String? = null,
    val notes: String? = null,
    /** Waktu order dilepas ke antrean dapur; 0 berarti order langsung masuk antrean. */
    val effectiveReleaseTime: Long = 0L,
    /** Tidak dikirim ke server — hanya dipakai lokal untuk memanggil RPC
     *  `increment_promo_usage` per promo yang benar-benar dipakai order ini. */
    val appliedPromoIds: Set<String> = emptySet(),
    val cashierName: String? = null
)
