package com.sukashawarma.pos.domain.usecase

import com.sukashawarma.pos.domain.model.Order
import com.sukashawarma.pos.domain.model.OrderStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PreparingOrderClassifierTest {
    @Test
    fun `release time server dipakai langsung seperti web`() {
        val release = "2026-08-13T09:10:00Z"
        val actual = PreparingOrderClassifier.effectiveReleaseTime(
            createdAt = Instant.parse("2026-08-13T08:00:00Z").toEpochMilli(),
            releaseTime = release,
            pickupTime = "16:30",
            notes = "AMBIL: 17:00"
        )
        assertEquals(Instant.parse(release).toEpochMilli(), actual)
    }

    @Test
    fun `catatan ambil menjadi dua puluh menit sebelum jam pickup`() {
        val actual = PreparingOrderClassifier.effectiveReleaseTime(
            createdAt = Instant.parse("2026-08-13T06:00:00Z").toEpochMilli(), // 13:00 WIB
            releaseTime = null,
            pickupTime = null,
            notes = "-- INFO PEMESAN ONLINE --\nAMBIL:\n16:30"
        )
        assertEquals(Instant.parse("2026-08-13T09:10:00Z").toEpochMilli(), actual) // 16:10 WIB
        assertTrue(actual > Instant.parse("2026-08-13T07:25:00Z").toEpochMilli()) // masih Terjadwal pada 14:25 WIB
    }

    @Test
    fun `pesanan terjadwal terpisah dari antrean dan diurutkan`() {
        val now = 1_000L
        val queue = order("queue", 0L)
        val later = order("later", 3_000L)
        val sooner = order("sooner", 2_000L)

        val (queued, scheduled) = PreparingOrderClassifier.split(listOf(later, queue, sooner), now)

        assertEquals(listOf("queue"), queued.map { it.id })
        assertEquals(listOf("sooner", "later"), scheduled.map { it.id })
        assertFalse(PreparingOrderClassifier.isScheduled(queue, now))
        assertTrue(PreparingOrderClassifier.isScheduled(sooner, now))
    }

    private fun order(id: String, releaseTime: Long) = Order(
        id = id,
        outletId = "outlet",
        orderNumber = 1,
        customerName = "Test",
        status = OrderStatus.PREPARING,
        effectiveReleaseTime = releaseTime
    )
}
