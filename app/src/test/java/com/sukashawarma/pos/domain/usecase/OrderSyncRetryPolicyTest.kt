package com.sukashawarma.pos.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class OrderSyncRetryPolicyTest {

    @Test
    fun `kegagalan memperpanjang jeda sampai maksimum dua menit`() {
        assertEquals(20_000L, OrderSyncRetryPolicy.afterAttempt(10_000L, false))
        assertEquals(40_000L, OrderSyncRetryPolicy.afterAttempt(20_000L, false))
        assertEquals(120_000L, OrderSyncRetryPolicy.afterAttempt(80_000L, false))
        assertEquals(120_000L, OrderSyncRetryPolicy.afterAttempt(120_000L, false))
    }

    @Test
    fun `keberhasilan mengembalikan jeda ke awal`() {
        assertEquals(10_000L, OrderSyncRetryPolicy.afterAttempt(120_000L, true))
    }
}
