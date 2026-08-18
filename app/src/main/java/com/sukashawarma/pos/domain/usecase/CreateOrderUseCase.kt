package com.sukashawarma.pos.domain.usecase

import com.sukashawarma.pos.domain.model.Order
import com.sukashawarma.pos.domain.model.OrderItem
import com.sukashawarma.pos.domain.model.OrderSource
import com.sukashawarma.pos.domain.model.PaymentMethod
import com.sukashawarma.pos.domain.model.Promo

class CreateOrderUseCase(
    private val calculateCartUseCase: CalculateCartUseCase = CalculateCartUseCase()
) {
    fun execute(
        outletId: String,
        customerName: String,
        items: List<OrderItem>,
        paymentMethod: PaymentMethod,
        amountReceived: Double,
        activePromos: List<Promo>,
        isOnline: Boolean,
        lastServerOrderNumber: Int,
        lastOfflineOrderNumber: Int,
        channel: String? = null,
        source: OrderSource = OrderSource.POS,
        // Order-manual's "Promo Apps" subsidy input (Food Apps channels only) — reduces
        // the payable total same as a discount, but isn't itself a Promo row.
        additionalDiscount: Double = 0.0,
        cashierName: String? = null
    ): Order {
        val calc = calculateCartUseCase.execute(items, activePromos, channel)
        
        val totalDiscount: Double
        val promoSubsidy: Double
        val finalTotal: Double
        
        val isOnlineOrderType = source == OrderSource.ONLINE || channel != null
        
        if (isOnlineOrderType) {
            totalDiscount = calc.totalDiscount
            promoSubsidy = calc.promoSubsidy + additionalDiscount
            finalTotal = calc.finalTotal // For online, finalTotal is already subtotal (discount doesn't reduce it)
        } else {
            totalDiscount = calc.totalDiscount + additionalDiscount
            promoSubsidy = calc.promoSubsidy
            finalTotal = maxOf(0.0, calc.finalTotal - additionalDiscount)
        }
        
        val changeAmount = if (paymentMethod == PaymentMethod.CASH) {
            maxOf(0.0, amountReceived - finalTotal)
        } else {
            0.0
        }

        // Penomoran Antrean: Offline range dimulai dari 9001
        val orderNumber = if (isOnline) {
            lastServerOrderNumber + 1
        } else {
            lastOfflineOrderNumber + 1
        }

        return Order(
            outletId = outletId,
            orderNumber = orderNumber,
            customerName = if (customerName.isBlank()) "Pelanggan" else customerName,
            status = com.sukashawarma.pos.domain.model.OrderStatus.PREPARING,
            source = source,
            paymentMethod = paymentMethod,
            items = items,
            subtotal = calc.subtotal,
            discountAmount = totalDiscount,
            promoSubsidy = promoSubsidy,
            totalAmount = finalTotal,
            amountReceived = amountReceived,
            changeAmount = changeAmount,
            isOffline = !isOnline,
            channel = channel,
            appliedPromoIds = calc.appliedPromoIds,
            cashierName = cashierName
        )
    }
}
