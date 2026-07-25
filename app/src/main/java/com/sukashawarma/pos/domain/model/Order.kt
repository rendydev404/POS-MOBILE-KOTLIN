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
    CARD
}

data class OrderItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val menuItemId: String,
    val name: String,
    val quantity: Int,
    val unitPrice: Double,
    val subtotal: Double = unitPrice * quantity,
    val note: String = "",
    val isChild: Boolean = false
)

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
    val totalAmount: Double = 0.0,
    val amountReceived: Double = 0.0,
    val changeAmount: Double = 0.0,
    val kitchenReceiptPrinted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val isOffline: Boolean = false
)
