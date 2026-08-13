package com.sukashawarma.pos.domain.usecase

import com.sukashawarma.pos.data.remote.dto.OrderDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Nilai-nilai kanal dan status di sini diambil dari isi tabel `orders` yang
 * sebenarnya, bukan dari asumsi: `pos` (10.065 baris), `food_apps` (6.236),
 * `tiktok` (1.915) vs `tiktokgo` (1.170), dan tidak ada satu pun baris `pending`.
 */
class OrderFilterTest {

    private fun order(
        status: String = "completed",
        cancellationStatus: String? = "none",
        channel: String? = null,
        paymentMethod: String? = "cash"
    ) = OrderDto(
        id = "o1",
        outletId = "outlet",
        orderNumber = 1,
        customerName = null,
        status = status,
        source = "pos",
        paymentMethod = paymentMethod,
        discountAmount = 0.0,
        promoSubsidy = 0.0,
        totalAmount = 10_000.0,
        amountReceived = 10_000.0,
        changeAmount = 0.0,
        kitchenReceiptPrinted = false,
        customerReceiptPrinted = false,
        cancellationStatus = cancellationStatus,
        cancellationUserName = null,
        createdAt = "2026-08-06T10:00:00+07:00",
        channel = channel,
        orderItems = null
    )

    // --- Kanal ---

    @Test
    fun `offline mencakup channel null dan pos`() {
        assertTrue(OrderChannel.matches(OrderChannel.OFFLINE, null))
        assertTrue(OrderChannel.matches(OrderChannel.OFFLINE, "pos"))
        assertFalse(OrderChannel.matches(OrderChannel.OFFLINE, "gofood"))
        assertTrue(OrderChannel.includesNull(OrderChannel.OFFLINE))
    }

    @Test
    fun `food apps mencakup nilai generik food_apps`() {
        assertTrue(OrderChannel.matches(OrderChannel.FOOD_APPS, "food_apps"))
        assertTrue(OrderChannel.matches(OrderChannel.FOOD_APPS, "gofood"))
        assertTrue(OrderChannel.matches(OrderChannel.FOOD_APPS, "tiktok"))
        assertFalse(OrderChannel.matches(OrderChannel.FOOD_APPS, "pos"))
        assertFalse(OrderChannel.matches(OrderChannel.FOOD_APPS, null))
    }

    @Test
    fun `tiktokgo mencakup semua ejaan tiktok`() {
        listOf("tiktokgo", "tiktok", "tiktok_go").forEach {
            assertTrue("harus cocok: $it", OrderChannel.matches("tiktokgo", it))
        }
        assertFalse(OrderChannel.matches("tiktokgo", "gofood"))
    }

    @Test
    fun `semua kanal tidak menyaring apa pun`() {
        assertNull(OrderChannel.valuesFor(OrderChannel.ALL))
        assertNull(OrderChannel.postgrestCondition(OrderChannel.ALL))
        assertTrue(OrderChannel.matches(OrderChannel.ALL, null))
        assertTrue(OrderChannel.matches(OrderChannel.ALL, "apa pun"))
    }

    @Test
    fun `offline memakai or bersarang supaya null ikut terjaring`() {
        assertEquals("or(channel.is.null,channel.in.(pos))", OrderChannel.postgrestCondition(OrderChannel.OFFLINE))
        assertEquals("channel.in.(gofood)", OrderChannel.postgrestCondition("gofood"))
    }

    @Test
    fun `offline dan food apps tidak pernah tumpang tindih`() {
        val kanalNyata = listOf(null, "pos", "food_apps", "gofood", "grabfood", "shopeefood", "tiktok", "tiktokgo")
        kanalNyata.forEach { ch ->
            val offline = OrderChannel.matches(OrderChannel.OFFLINE, ch)
            val ojol = OrderChannel.matches(OrderChannel.FOOD_APPS, ch)
            assertFalse("kanal $ch masuk dua grup sekaligus", offline && ojol)
        }
    }

    // --- Status ---

    @Test
    fun `diproses mencakup preparing bukan cuma pending`() {
        assertTrue(OrderStatusFilter.matches(OrderStatusFilter.WAITING, order(status = "preparing")))
        assertTrue(OrderStatusFilter.matches(OrderStatusFilter.WAITING, order(status = "ready")))
        assertTrue(OrderStatusFilter.matches(OrderStatusFilter.WAITING, order(status = "pending")))
        assertFalse(OrderStatusFilter.matches(OrderStatusFilter.WAITING, order(status = "completed")))
    }

    @Test
    fun `pembatalan yang disetujui dihitung batal walau status masih completed`() {
        val disetujuiBatal = order(status = "completed", cancellationStatus = "approved")

        assertTrue(OrderStatusFilter.isCancelled(disetujuiBatal))
        assertTrue(OrderStatusFilter.matches(OrderStatusFilter.CANCELLED, disetujuiBatal))
        assertFalse(OrderStatusFilter.matches(OrderStatusFilter.DONE, disetujuiBatal))
    }

    @Test
    fun `pembatalan yang masih menunggu persetujuan belum dihitung batal`() {
        val menungguPersetujuan = order(status = "preparing", cancellationStatus = "pending_approval")

        assertFalse(OrderStatusFilter.isCancelled(menungguPersetujuan))
        assertTrue(OrderStatusFilter.matches(OrderStatusFilter.WAITING, menungguPersetujuan))
    }

    @Test
    fun `pesanan batal yang disetujui tidak masuk omzet`() {
        assertFalse(RevenueCalculator.isRevenue(order(status = "completed", cancellationStatus = "approved")))
        assertTrue(RevenueCalculator.isRevenue(order(status = "completed", cancellationStatus = "none")))
        assertTrue(RevenueCalculator.isRevenue(order(status = "completed", cancellationStatus = null)))
    }

    @Test
    fun `setiap pesanan jatuh tepat di satu pill`() {
        val pills = listOf(OrderStatusFilter.WAITING, OrderStatusFilter.DONE, OrderStatusFilter.CANCELLED)
        val contoh = listOf(
            order(status = "completed"),
            order(status = "cancelled"),
            order(status = "preparing"),
            order(status = "completed", cancellationStatus = "approved"),
            order(status = "preparing", cancellationStatus = "pending_approval")
        )
        contoh.forEach { dto ->
            val cocok = pills.count { OrderStatusFilter.matches(it, dto) }
            assertEquals("status=${dto.status} canc=${dto.cancellationStatus}", 1, cocok)
        }
    }

    @Test
    fun `pill semua meloloskan apa pun`() {
        assertTrue(OrderStatusFilter.matches(OrderStatusFilter.ALL, order(status = "cancelled")))
        assertNull(OrderStatusFilter.postgrestCondition(OrderStatusFilter.ALL))
    }
}
