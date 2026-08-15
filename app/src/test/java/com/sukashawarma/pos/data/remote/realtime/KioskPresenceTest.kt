package com.sukashawarma.pos.data.remote.realtime

import org.junit.Assert.assertEquals
import org.junit.Test

class KioskPresenceTest {
    @Test
    fun `device label follows web presence metadata with safe fallbacks`() {
        assertEquals(
            "Device-45",
            normalizedKioskPresence("user-1", "kiosk_pekayon", "Device-45", null).deviceLabel
        )
        assertEquals(
            "kiosk_pekayon",
            normalizedKioskPresence("user-1", "kiosk_pekayon", "", null).deviceLabel
        )
        assertEquals(
            "user-1",
            normalizedKioskPresence("user-1", null, null, null).deviceLabel
        )
    }
}
