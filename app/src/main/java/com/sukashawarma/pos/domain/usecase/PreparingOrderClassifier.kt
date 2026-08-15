package com.sukashawarma.pos.domain.usecase

import com.sukashawarma.pos.domain.model.Order
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Mirrors the web cashier's `_effectiveReleaseTime` calculation. */
object PreparingOrderClassifier {
    private val jakartaZone: ZoneId = ZoneId.of("Asia/Jakarta")
    private val pickupPattern = Regex("(?:AMBIL|JAM\\s+AMBIL)\\s*[:\\n]\\s*(\\d{1,2}:\\d{2})", RegexOption.IGNORE_CASE)
    private const val DEFAULT_LEAD_MINUTES = 20L

    fun effectiveReleaseTime(
        createdAt: Long,
        releaseTime: String?,
        pickupTime: String?,
        notes: String?
    ): Long {
        parseInstant(releaseTime)?.let { return it }

        val rawPickup = pickupTime
            ?.takeIf { it.isNotBlank() && it != "-" }
            ?: notes?.let { pickupPattern.find(it)?.groupValues?.getOrNull(1) }
            ?: return 0L

        parseInstant(rawPickup)?.let { return it - DEFAULT_LEAD_MINUTES * 60_000L }

        val time = runCatching { LocalTime.parse(rawPickup.trim().padStart(5, '0')) }.getOrNull()
            ?: return 0L
        val created = Instant.ofEpochMilli(createdAt).atZone(jakartaZone)
        var pickup = ZonedDateTime.of(created.toLocalDate(), time, jakartaZone)
        if (pickup.toInstant().toEpochMilli() < createdAt) pickup = pickup.plusDays(1)
        return pickup.toInstant().toEpochMilli() - DEFAULT_LEAD_MINUTES * 60_000L
    }

    fun isScheduled(order: Order, now: Long): Boolean = order.effectiveReleaseTime > now

    fun split(orders: List<Order>, now: Long): Pair<List<Order>, List<Order>> {
        val (scheduled, queue) = orders.partition { isScheduled(it, now) }
        return queue to scheduled.sortedBy { it.effectiveReleaseTime }
    }

    private fun parseInstant(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
    }
}
