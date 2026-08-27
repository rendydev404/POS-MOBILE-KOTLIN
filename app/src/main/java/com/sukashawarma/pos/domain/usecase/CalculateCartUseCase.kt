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
    val promoSubsidy: Double,
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
            if (promo.endDate != null && now >= promo.endDate) return false
            
            if (promo.dailyStartTime != null && promo.dailyEndTime != null) {
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
                    val wibOffset = 7L * 60 * 60L * 1000L
                    val dayMs = 24L * 60L * 60L * 1000L
                    val nowTimeInDay = (now + wibOffset) % dayMs
                    
                    if (dailyStart <= dailyEnd) {
                        if (nowTimeInDay < dailyStart || nowTimeInDay >= dailyEnd) return false
                    } else {
                        if (nowTimeInDay < dailyStart && nowTimeInDay >= dailyEnd) return false
                    }
                }
            }

            if (isFoodAppChannel && !promo.applyToFoodApps) return false
            return true
        }

        val eligiblePromos = promos.filter { isEligible(it) }
        val appliedPromoIds = mutableSetOf<String>()
        val appliedPromoNames = mutableSetOf<String>()

        // B1G1 berlaku hanya pada transaksi POS offline. Baris hadiah Rp0 dibuat
        // oleh keranjang; kalkulator hanya menetapkan eksklusivitas dan audit promo.
        // Tidak ada pemeriksaan stok di sini karena stok aplikasi bisa tertinggal dari
        // kondisi fisik outlet.
        val buyOneGetOnePromo = if (!isFoodAppChannel &&
            (channel.isNullOrBlank() || channel.equals("endorse", ignoreCase = true))) {
            eligiblePromos.firstOrNull { promo ->
                promo.discountType == DiscountType.BUY_ONE_GET_ONE &&
                    promo.scope == PromoScope.ITEM &&
                    promo.menuItemId != null &&
                    items.any {
                        it.menuItemId == promo.menuItemId &&
                            !it.isPromoReward &&
                            it.quantity >= promo.buyQuantity
                    }
            }
        } else null

        if (buyOneGetOnePromo != null) {
            appliedPromoIds += buyOneGetOnePromo.id
            appliedPromoNames += buyOneGetOnePromo.name
            return CartCalculationResult(
                subtotal = subtotal,
                totalDiscount = 0.0,
                promoSubsidy = 0.0,
                finalTotal = subtotal,
                appliedPromoIds = appliedPromoIds,
                appliedPromoNames = appliedPromoNames.toList()
            )
        }

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
                    DiscountType.BUY_ONE_GET_ONE -> 0.0
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
                DiscountType.BUY_ONE_GET_ONE -> 0.0
            }
        } else 0.0

        val totalCalculatedDiscount = itemDiscountTotal + globalDiscountTotal
        
        val totalDiscount: Double
        val promoSubsidy: Double
        val finalTotal: Double
        
        if (isFoodAppChannel) {
            totalDiscount = 0.0
            promoSubsidy = totalCalculatedDiscount
            finalTotal = subtotal
        } else {
            totalDiscount = totalCalculatedDiscount
            promoSubsidy = 0.0
            finalTotal = max(0.0, subtotal - totalDiscount)
        }

        return CartCalculationResult(
            subtotal = subtotal,
            totalDiscount = totalDiscount,
            promoSubsidy = promoSubsidy,
            finalTotal = finalTotal,
            appliedPromoIds = appliedPromoIds,
            appliedPromoNames = appliedPromoNames.toList()
        )
    }
}
