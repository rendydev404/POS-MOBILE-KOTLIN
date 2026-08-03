package com.sukashawarma.pos.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkStatusTest {

    @Test
    fun `internet plus validated dianggap online`() {
        assertTrue(NetworkStatus.isValidatedInternet(hasInternet = true, isValidated = true))
    }

    @Test
    fun `wifi outlet nyala tapi internet mati dianggap offline`() {
        // Kasus paling sering di outlet: router hidup, tagihan ISP telat.
        // NET_CAPABILITY_INTERNET tetap true, VALIDATED false.
        assertFalse(NetworkStatus.isValidatedInternet(hasInternet = true, isValidated = false))
    }

    @Test
    fun `tanpa kapabilitas internet dianggap offline`() {
        assertFalse(NetworkStatus.isValidatedInternet(hasInternet = false, isValidated = true))
        assertFalse(NetworkStatus.isValidatedInternet(hasInternet = false, isValidated = false))
    }
}
