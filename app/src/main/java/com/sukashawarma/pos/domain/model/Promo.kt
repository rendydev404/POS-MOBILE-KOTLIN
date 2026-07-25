package com.sukashawarma.pos.domain.model

enum class PromoScope {
    GLOBAL, // Diskon untuk total seluruh belanjaan
    ITEM    // Diskon khusus item tertentu (menuItemId)
}

enum class DiscountType {
    PERCENTAGE, // Persentase (e.g. 10%)
    NOMINAL     // Nominal Rupiah (e.g. Rp 5.000)
}

data class Promo(
    val id: String,
    val outletId: String,
    val name: String,
    val scope: PromoScope,
    val menuItemId: String? = null,
    val discountType: DiscountType,
    val discountValue: Double,
    val isActive: Boolean = true
)
