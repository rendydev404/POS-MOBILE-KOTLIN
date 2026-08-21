package com.sukashawarma.pos.domain.usecase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderStatusUpdatePolicyTest {

    @Test
    fun `tap ganda order yang sama hanya memulai satu update`() {
        val guard = OrderStatusUpdateGuard()

        assertTrue(guard.tryBegin("order-11"))
        assertFalse(guard.tryBegin("order-11"))

        guard.finish("order-11")
        assertTrue(guard.tryBegin("order-11"))
    }

    @Test
    fun `status lokal hanya boleh berpindah bila server benar benar mengubah satu baris`() {
        assertFalse(OrderStatusUpdatePolicy.canCommit(httpSuccessful = false, updatedRowCount = 0))
        assertFalse(OrderStatusUpdatePolicy.canCommit(httpSuccessful = true, updatedRowCount = 0))
        assertTrue(OrderStatusUpdatePolicy.canCommit(httpSuccessful = true, updatedRowCount = 1))
    }
}
