package com.sukashawarma.pos.domain.usecase

import com.sukashawarma.pos.domain.model.DiscountType
import com.sukashawarma.pos.domain.model.OrderItem
import com.sukashawarma.pos.domain.model.Promo
import com.sukashawarma.pos.domain.model.PromoScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuyOneGetOneCartCalculationTest {
    private val calculator = CalculateCartUseCase()

    @Test
    fun `buy one get one keeps paid total and blocks other discounts`() {
        val result = calculator.execute(
            items = listOf(
                OrderItem(menuItemId = "shawarma", name = "Shawarma", quantity = 1, unitPrice = 25_000.0),
                OrderItem(
                    menuItemId = "shawarma",
                    name = "Shawarma",
                    quantity = 1,
                    unitPrice = 0.0,
                    isPromoReward = true,
                    promoId = "b1g1"
                )
            ),
            promos = listOf(
                promo(id = "b1g1", type = DiscountType.BUY_ONE_GET_ONE, menuItemId = "shawarma"),
                promo(id = "discount", type = DiscountType.PERCENTAGE, menuItemId = "shawarma", value = 50.0)
            )
        )

        assertEquals(25_000.0, result.finalTotal, 0.0)
        assertEquals(0.0, result.totalDiscount, 0.0)
        assertEquals(setOf("b1g1"), result.appliedPromoIds)
    }

    @Test
    fun `buy one get one does not apply to food app order`() {
        val result = calculator.execute(
            items = listOf(OrderItem(menuItemId = "shawarma", name = "Shawarma", quantity = 1, unitPrice = 25_000.0)),
            promos = listOf(promo(id = "b1g1", type = DiscountType.BUY_ONE_GET_ONE, menuItemId = "shawarma")),
            channel = "gofood"
        )

        assertTrue(result.appliedPromoIds.isEmpty())
        assertEquals(25_000.0, result.finalTotal, 0.0)
    }

    @Test
    fun `buy x get y only applies after configured buy quantity`() {
        val promo = promo(
            id = "buy3get2",
            type = DiscountType.BUY_ONE_GET_ONE,
            menuItemId = "shawarma",
            buyQuantity = 3,
            getQuantity = 2
        )

        val belowThreshold = calculator.execute(
            items = listOf(OrderItem(menuItemId = "shawarma", name = "Shawarma", quantity = 2, unitPrice = 25_000.0)),
            promos = listOf(promo)
        )
        val eligible = calculator.execute(
            items = listOf(
                OrderItem(menuItemId = "shawarma", name = "Shawarma", quantity = 3, unitPrice = 25_000.0),
                OrderItem(menuItemId = "shawarma", name = "Shawarma", quantity = 2, unitPrice = 0.0, isPromoReward = true)
            ),
            promos = listOf(promo)
        )

        assertTrue(belowThreshold.appliedPromoIds.isEmpty())
        assertEquals(setOf("buy3get2"), eligible.appliedPromoIds)
        assertEquals(75_000.0, eligible.finalTotal, 0.0)
    }

    private fun promo(
        id: String,
        type: DiscountType,
        menuItemId: String,
        value: Double = 0.01,
        buyQuantity: Int = 1,
        getQuantity: Int = 1
    ) = Promo(
        id = id,
        outletId = "outlet-1",
        name = id,
        scope = PromoScope.ITEM,
        menuItemId = menuItemId,
        discountType = type,
        discountValue = value,
        buyQuantity = buyQuantity,
        getQuantity = getQuantity
    )
}
