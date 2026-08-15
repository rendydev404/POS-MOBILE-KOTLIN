package com.sukashawarma.pos.data.remote.dto

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeRuntimeConfigTest {
    @Test
    fun `warna khusus versi hanya aktif setelah APK target terpasang`() {
        val config = NativeRuntimeConfig(
            newOrderButtonColor = "#7C3AED",
            newOrderButtonColorsByVersion = mapOf(
                "5" to "#E67E22",
                "6" to "#DC2626"
            )
        )

        assertEquals("#7C3AED", config.newOrderButtonColorFor(4))
        assertEquals("#E67E22", config.newOrderButtonColorFor(5))
        assertEquals("#DC2626", config.newOrderButtonColorFor(6))
    }

    @Test
    fun `warna dasar dipakai jika tidak ada override versi`() {
        val config = NativeRuntimeConfig(newOrderButtonColor = "#E67E22")

        assertEquals("#E67E22", config.newOrderButtonColorFor(99))
    }
}
