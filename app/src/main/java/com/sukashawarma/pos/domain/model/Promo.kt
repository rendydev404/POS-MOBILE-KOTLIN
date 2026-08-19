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
    val minPurchase: Double? = null,
    val isActive: Boolean = true,
    val usageLimit: Int? = null,
    val currentUsage: Int = 0,
    /** Epoch millis, absolute instants — null means unbounded on that side. */
    val startDate: Long? = null,
    val endDate: Long? = null,
    val dailyStartTime: String? = null,
    val dailyEndTime: String? = null,
    val applyToFoodApps: Boolean = false
)
