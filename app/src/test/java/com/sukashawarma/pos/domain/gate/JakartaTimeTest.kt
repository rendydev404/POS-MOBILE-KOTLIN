package com.sukashawarma.pos.domain.gate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class JakartaTimeTest {

    private val day = LocalDate.of(2026, 8, 1)

    @Test
    fun `dateString formats as yyyy-MM-dd`() {
        assertEquals("2026-08-01", JakartaTime.dateString(day))
    }

    @Test
    fun `day bounds are utc instants of the jakarta day`() {
        // 2026-08-01T00:00:00+07:00 == 2026-07-31T17:00:00Z
        assertEquals("2026-07-31T17:00:00Z", JakartaTime.startOfDayIso(day))
        assertEquals("2026-08-01T16:59:59.999Z", JakartaTime.endOfDayIso(day))
    }

    /**
     * Regresi: bentuk `+07:00` membuat SELURUH request PostgREST gagal, karena
     * OkHttp meneruskan `+` mentah di query string dan server membacanya sebagai
     * spasi (`22007 invalid input syntax for type timestamp with time zone`).
     */
    @Test
    fun `day bounds never contain a plus sign`() {
        assertFalse(JakartaTime.startOfDayIso(day).contains('+'))
        assertFalse(JakartaTime.endOfDayIso(day).contains('+'))
    }

    @Test
    fun `day bounds round trip back to the same jakarta day`() {
        assertTrue(JakartaTime.isOnDay(JakartaTime.startOfDayIso(day), day))
        assertTrue(JakartaTime.isOnDay(JakartaTime.endOfDayIso(day), day))
        assertFalse(JakartaTime.isOnDay(JakartaTime.startOfDayIso(day.plusDays(1)), day))
    }

    @Test
    fun `utc timestamp inside jakarta day is matched`() {
        // 2026-07-31T17:00:00Z == 2026-08-01T00:00:00+07:00
        assertTrue(JakartaTime.isOnDay("2026-07-31T17:00:00Z", day))
    }

    @Test
    fun `utc timestamp before jakarta midnight belongs to previous day`() {
        // 2026-07-31T16:59:59Z == 2026-07-31T23:59:59+07:00
        assertFalse(JakartaTime.isOnDay("2026-07-31T16:59:59Z", day))
    }

    @Test
    fun `postgrest two digit offset is parsed`() {
        assertTrue(JakartaTime.isOnDay("2026-08-01T03:00:00+00", day))
    }

    @Test
    fun `postgrest space separated timestamp is parsed`() {
        assertTrue(JakartaTime.isOnDay("2026-08-01 03:00:00+00:00", day))
    }

    @Test
    fun `timestamp with microseconds is parsed`() {
        assertTrue(JakartaTime.isOnDay("2026-08-01T03:00:00.123456+00:00", day))
    }

    @Test
    fun `timestamp without offset is treated as utc`() {
        assertTrue(JakartaTime.isOnDay("2026-08-01T03:00:00", day))
    }

    @Test
    fun `null and garbage timestamps do not match`() {
        assertFalse(JakartaTime.isOnDay(null, day))
        assertFalse(JakartaTime.isOnDay("bukan tanggal", day))
    }
}
