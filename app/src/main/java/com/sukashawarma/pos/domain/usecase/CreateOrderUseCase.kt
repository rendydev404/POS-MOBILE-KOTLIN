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
        lastOfflineOrderNumber: Int
    ): Order {
        val calc = calculateCartUseCase.execute(items, activePromos)
        val changeAmount = if (paymentMethod == PaymentMethod.CASH) {
            maxOf(0.0, amountReceived - calc.finalTotal)
        } else {
            0.0
        }

        // Penomoran Antrean: Offline range dimulai dari 9001
        val orderNumber = if (isOnline) {
            lastServerOrderNumber + 1
        } else {
            if (lastOfflineOrderNumber < 9001) 9001 else lastOfflineOrderNumber + 1
        }

        return Order(
            outletId = outletId,
            orderNumber = orderNumber,
            customerName = if (customerName.isBlank()) "Pelanggan" else customerName,
            source = OrderSource.POS,
            paymentMethod = paymentMethod,
            items = items,
            subtotal = calc.subtotal,
            discountAmount = calc.totalDiscount,
            totalAmount = calc.finalTotal,
            amountReceived = amountReceived,
            changeAmount = changeAmount,
            isOffline = !isOnline
        )
    }
}
