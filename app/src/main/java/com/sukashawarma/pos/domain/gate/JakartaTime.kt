package com.sukashawarma.pos.domain.gate

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Semua perhitungan hari untuk gate kasir memakai Asia/Jakarta, menyamai
 * `Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Jakarta' })` di versi web.
 */
object JakartaTime {

    val ZONE: ZoneId = ZoneId.of("Asia/Jakarta")

    fun today(): LocalDate = LocalDate.now(ZONE)

    fun dateString(day: LocalDate): String = day.format(DateTimeFormatter.ISO_LOCAL_DATE)

    fun startOfDayIso(day: LocalDate): String = "${dateString(day)}T00:00:00+07:00"

    fun endOfDayIso(day: LocalDate): String = "${dateString(day)}T23:59:59+07:00"

    /** True bila [timestamp] jatuh pada [day] menurut Asia/Jakarta. */
    fun isOnDay(timestamp: String?, day: LocalDate): Boolean {
        val instant = instantOrNull(timestamp) ?: return false
        return instant.atZone(ZONE).toLocalDate() == day
    }

    fun instantOrNull(raw: String?): Instant? {
        if (raw.isNullOrBlank()) return null
        // PostgREST bisa mengembalikan "2026-08-01 03:00:00+00" — normalkan
        // pemisah tanggal/jam dan offset dua digit menjadi bentuk ISO penuh.
        var value = raw.trim().replace(' ', 'T')
        if (Regex("[+-]\\d{2}$").containsMatchIn(value)) {
            value += ":00"
        }
        return try {
            OffsetDateTime.parse(value).toInstant()
        } catch (e: Exception) {
            try {
                // Tanpa offset: perlakukan sebagai UTC, sama seperti web.
                LocalDateTime.parse(value).toInstant(ZoneOffset.UTC)
            } catch (e2: Exception) {
                null
            }
        }
    }
}
