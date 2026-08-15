package com.sukashawarma.pos.domain.usecase

import com.sukashawarma.pos.domain.model.DiscountType
import com.sukashawarma.pos.domain.model.OrderItem
import com.sukashawarma.pos.domain.model.PROMO_SUBSIDY_CHANNELS
import com.sukashawarma.pos.domain.model.Promo
import com.sukashawarma.pos.domain.model.PromoScope
import com.sukashawarma.pos.domain.model.normalizeChannel
import kotlin.math.max

data class CartCalculationResult(
    val subtotal: Double,
    val totalDiscount: Double,
    val finalTotal: Double,
    /** Ids of promos actually applied — for `increment_promo_usage` bookkeeping. */
    val appliedPromoIds: Set<String> = emptySet(),
    /** Names of promos actually applied — for showing the cashier what got discounted. */
    val appliedPromoNames: List<String> = emptyList()
)

/**
 * Port of apps/pos-kasir/lib/promo-calculator.ts (calculateItemPrice/isPromoEligible).
 * A promo is eligible when: active, quota not exhausted, within its schedule window,
 * and — for food-app order channels — only if `applyToFoodApps` is set. Only ONE
 * global promo applies per cart (the first eligible one), matching the web's semantics.
 */
class CalculateCartUseCase {
    fun execute(
        items: List<OrderItem>,
        promos: List<Promo>,
        channel: String? = null,
        now: Long = System.currentTimeMillis()
    ): CartCalculationResult {
        val subtotal = items.sumOf { it.subtotal }
        val isFoodAppChannel = normalizeChannel(channel) in PROMO_SUBSIDY_CHANNELS

        fun isEligible(promo: Promo): Boolean {
            if (!promo.isActive) return false
            if (promo.usageLimit != null && promo.currentUsage >= promo.usageLimit) return false
            if (promo.startDate != null && now < promo.startDate) return false
            if (promo.endDate != null && now > promo.endDate) return false
            if (isFoodAppChannel && !promo.applyToFoodApps) return false
            return true
        }

        val eligiblePromos = promos.filter { isEligible(it) }
        val appliedPromoIds = mutableSetOf<String>()
        val appliedPromoNames = mutableSetOf<String>()

        var itemDiscountTotal = 0.0
        val itemPromos = eligiblePromos.filter { it.scope == PromoScope.ITEM && it.menuItemId != null }
        val globalPromo = eligiblePromos.firstOrNull {
            it.scope == PromoScope.GLOBAL && (it.minPurchase == null || subtotal >= it.minPurchase)
        }

        for (item in items) {
            val matchingPromo = itemPromos.find { it.menuItemId == item.menuItemId }
            if (matchingPromo != null) {
                val itemDiscount = when (matchingPromo.discountType) {
                    DiscountType.PERCENTAGE -> item.subtotal * (matchingPromo.discountValue / 100.0)
                    DiscountType.NOMINAL -> matchingPromo.discountValue * item.quantity
                }
                itemDiscountTotal += itemDiscount
                appliedPromoIds += matchingPromo.id
                appliedPromoNames += matchingPromo.name
            }
        }

        val globalDiscountTotal = if (globalPromo != null) {
            appliedPromoIds += globalPromo.id
            appliedPromoNames += globalPromo.name
            when (globalPromo.discountType) {
                DiscountType.PERCENTAGE -> subtotal * (globalPromo.discountValue / 100.0)
                DiscountType.NOMINAL -> globalPromo.discountValue
            }
        } else 0.0

        val totalDiscount = itemDiscountTotal + globalDiscountTotal
        val finalTotal = max(0.0, subtotal - totalDiscount)

        return CartCalculationResult(
            subtotal = subtotal,
            totalDiscount = totalDiscount,
            finalTotal = finalTotal,
            appliedPromoIds = appliedPromoIds,
            appliedPromoNames = appliedPromoNames.toList()
        )
    }
}
