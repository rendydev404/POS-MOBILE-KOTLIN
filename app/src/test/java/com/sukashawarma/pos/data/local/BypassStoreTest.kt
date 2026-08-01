package com.sukashawarma.pos.data.local

import com.sukashawarma.pos.domain.gate.JakartaTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BypassStoreTest {

    @Test
    fun `fresh store is not bypassed`() {
        assertFalse(InMemoryBypassStore().isBypassed())
    }

    @Test
    fun `marking makes it bypassed`() {
        val store = InMemoryBypassStore()

        store.markBypassed()

        assertTrue(store.isBypassed())
    }

    @Test
    fun `clear resets the store`() {
        val store = InMemoryBypassStore()
        store.markBypassed()

        store.clear()

        assertFalse(store.isBypassed())
    }

    @Test
    fun `bypass from another day is ignored`() {
        val store = InMemoryBypassStore(storedDate = "2020-01-01")

        assertFalse(store.isBypassed())
    }

    @Test
    fun `marking stores today in jakarta`() {
        val store = InMemoryBypassStore()

        store.markBypassed()

        assertTrue(store.storedDate == JakartaTime.dateString(JakartaTime.today()))
    }
}
