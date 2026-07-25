package com.sukashawarma.pos.domain.usecase

import com.sukashawarma.pos.domain.model.DiscountType
import com.sukashawarma.pos.domain.model.OrderItem
import com.sukashawarma.pos.domain.model.Promo
import com.sukashawarma.pos.domain.model.PromoScope
import kotlin.math.max

data class CartCalculationResult(
    val subtotal: Double,
    val totalDiscount: Double,
    val finalTotal: Double
)

class CalculateCartUseCase {
    fun execute(items: List<OrderItem>, promos: List<Promo>): CartCalculationResult {
        val subtotal = items.sumOf { it.subtotal }
        var itemDiscountTotal = 0.0
        var globalDiscountTotal = 0.0

        val activePromos = promos.filter { it.isActive }

        // 1. Hitung Promo Per Item
        val itemPromos = activePromos.filter { it.scope == PromoScope.ITEM && it.menuItemId != null }
        for (item in items) {
            val matchingPromo = itemPromos.find { it.menuItemId == item.menuItemId }
            if (matchingPromo != null) {
                val itemDiscount = when (matchingPromo.discountType) {
                    DiscountType.PERCENTAGE -> item.subtotal * (matchingPromo.discountValue / 100.0)
                    DiscountType.NOMINAL -> matchingPromo.discountValue * item.quantity
                }
                itemDiscountTotal += itemDiscount
            }
        }

        // 2. Hitung Promo Global
        val globalPromos = activePromos.filter { it.scope == PromoScope.GLOBAL }
        for (globalPromo in globalPromos) {
            val discount = when (globalPromo.discountType) {
                DiscountType.PERCENTAGE -> subtotal * (globalPromo.discountValue / 100.0)
                DiscountType.NOMINAL -> globalPromo.discountValue
            }
            globalDiscountTotal += discount
        }

        val totalDiscount = itemDiscountTotal + globalDiscountTotal
        val finalTotal = max(0.0, subtotal - totalDiscount)

        return CartCalculationResult(
            subtotal = subtotal,
            totalDiscount = totalDiscount,
            finalTotal = finalTotal
        )
    }
}
