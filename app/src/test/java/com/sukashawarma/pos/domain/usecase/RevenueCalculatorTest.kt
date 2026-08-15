package com.sukashawarma.pos.domain.usecase

import com.sukashawarma.pos.data.local.entity.LocalOrderEntity
import com.sukashawarma.pos.data.remote.dto.OrderDto
import com.sukashawarma.pos.data.remote.dto.OrderItemDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RevenueCalculatorTest {

    private fun item(name: String = "Shawarma", qty: Int = 1, price: Double = 25_000.0) =
        OrderItemDto(
            id = null,
            orderId = null,
            menuItemId = "m1",
            menuItemName = name,
            quantity = qty,
            unitPrice = price,
            subtotal = price * qty
        )

    private fun order(
        status: String = "completed",
        total: Double = 25_000.0,
        discount: Double? = 0.0,
        promo: Double? = 0.0,
        items: List<OrderItemDto>? = listOf(item())
    ) = OrderDto(
        id = "o1",
        outletId = "outlet",
        orderNumber = 1,
        customerName = "Pelanggan",
        status = status,
        source = "pos",
        paymentMethod = "cash",
        discountAmount = discount,
        promoSubsidy = promo,
        totalAmount = total,
        amountReceived = total,
        changeAmount = 0.0,
        kitchenReceiptPrinted = false,
        customerReceiptPrinted = false,
        cancellationStatus = null,
        cancellationUserName = null,
        createdAt = "2026-08-06T10:00:00+07:00",
        channel = null,
        orderItems = items
    )

    @Test
    fun `omzet kotor memakai subtotal item bukan total_amount`() {
        // Menu Rp 30.000, diskon Rp 5.000, pelanggan bayar Rp 25.000.
        val o = order(
            total = 25_000.0,
            discount = 5_000.0,
            items = listOf(item(price = 30_000.0))
        )
        assertEquals(30_000.0, RevenueCalculator.grossOf(o), 0.01)
        assertEquals(25_000.0, RevenueCalculator.netOf(o), 0.01)
    }

    @Test
    fun `beberapa baris item dijumlahkan`() {
        val o = order(items = listOf(item(qty = 2, price = 25_000.0), item("Extra Keju", 1, 5_000.0)))
        assertEquals(55_000.0, RevenueCalculator.grossOf(o), 0.01)
    }

    @Test
    fun `tanpa order_items nilainya direkonstruksi dari potongan yang tercatat`() {
        val o = order(total = 25_000.0, discount = 4_000.0, promo = 1_000.0, items = null)
        assertEquals(30_000.0, RevenueCalculator.grossOf(o), 0.01)
    }

    @Test
    fun `hanya status completed yang dihitung`() {
        assertTrue(RevenueCalculator.isRevenue(order(status = "completed")))
        assertTrue(RevenueCalculator.isRevenue(order(status = "COMPLETED")))
        assertFalse(RevenueCalculator.isRevenue(order(status = "ready")))
        assertFalse(RevenueCalculator.isRevenue(order(status = "pending")))
        assertFalse(RevenueCalculator.isRevenue(order(status = "cancelled")))
        assertFalse(RevenueCalculator.isRevenue(order(status = "preparing")))
    }

    @Test
    fun `summarize mengabaikan pesanan batal dan pending`() {
        val orders = listOf(
            order(status = "completed", total = 25_000.0, items = listOf(item(price = 30_000.0))),
            order(status = "cancelled", total = 99_000.0, items = listOf(item(price = 99_000.0))),
            order(status = "pending", total = 50_000.0, items = listOf(item(price = 50_000.0)))
        )
        val summary = RevenueCalculator.summarize(orders)

        assertEquals(30_000.0, summary.gross, 0.01)
        assertEquals(25_000.0, summary.net, 0.01)
        assertEquals(5_000.0, summary.deductions, 0.01)
        assertEquals(1, summary.orderCount)
        assertEquals(1, summary.itemsSold)
    }

    @Test
    fun `summarize daftar kosong menghasilkan nol bukan pengecualian`() {
        val summary = RevenueCalculator.summarize(emptyList<OrderDto>())
        assertEquals(0.0, summary.gross, 0.01)
        assertEquals(0, summary.orderCount)
    }

    @Test
    fun `entity Room memakai kolom subtotal sebagai omzet kotor`() {
        val e = entity(status = "COMPLETED", subtotal = 30_000.0, total = 25_000.0, discount = 5_000.0)
        assertEquals(30_000.0, RevenueCalculator.grossOf(e), 0.01)
    }

    @Test
    fun `entity tanpa subtotal jatuh ke total ditambah diskon`() {
        val e = entity(status = "COMPLETED", subtotal = 0.0, total = 25_000.0, discount = 5_000.0)
        assertEquals(30_000.0, RevenueCalculator.grossOf(e), 0.01)
    }

    private fun entity(
        status: String,
        subtotal: Double,
        total: Double,
        discount: Double
    ) = LocalOrderEntity(
        id = "o1",
        outletId = "outlet",
        orderNumber = 1,
        customerName = "Pelanggan",
        status = status,
        source = "POS",
        paymentMethod = "CASH",
        itemsJson = "[]",
        subtotal = subtotal,
        discountAmount = discount,
        totalAmount = total,
        amountReceived = total,
        changeAmount = 0.0,
        kitchenReceiptPrinted = false,
        customerReceiptPrinted = false,
        cancellationStatus = null,
        cancellationUserName = null,
        createdAt = 0L,
        cashierName = null
    )
}
