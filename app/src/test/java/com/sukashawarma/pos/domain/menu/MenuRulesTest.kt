package com.sukashawarma.pos.domain.menu

import com.sukashawarma.pos.data.remote.dto.KioskSettingDto
import com.sukashawarma.pos.domain.model.MenuItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuRulesTest {

    private val outletId = "outlet-a"
    private val otherOutletId = "outlet-b"

    @Test
    fun `resolveSetting prefers own outlet row over PUSAT and global`() {
        val rows = listOf(
            KioskSettingDto(key = "bestseller_ids", value = "[\"global-item\"]", outletId = null),
            KioskSettingDto(key = "bestseller_ids", value = "[\"pusat-item\"]", outletId = PUSAT_OUTLET_ID),
            KioskSettingDto(key = "bestseller_ids", value = "[\"outlet-item\"]", outletId = outletId)
        )
        assertEquals(listOf("outlet-item"), resolveSetting(rows, "bestseller_ids", outletId))
    }

    @Test
    fun `resolveSetting falls back to PUSAT when own outlet row is absent`() {
        val rows = listOf(
            KioskSettingDto(key = "bestseller_ids", value = "[\"global-item\"]", outletId = null),
            KioskSettingDto(key = "bestseller_ids", value = "[\"pusat-item\"]", outletId = PUSAT_OUTLET_ID)
        )
        assertEquals(listOf("pusat-item"), resolveSetting(rows, "bestseller_ids", outletId))
    }

    @Test
    fun `resolveSetting falls back to global row when no outlet or PUSAT row exists`() {
        val rows = listOf(KioskSettingDto(key = "bestseller_ids", value = "[\"global-item\"]", outletId = null))
        assertEquals(listOf("global-item"), resolveSetting(rows, "bestseller_ids", outletId))
    }

    @Test
    fun `resolveSetting returns empty list when no rows match the key`() {
        assertEquals(emptyList<String>(), resolveSetting(emptyList(), "bestseller_ids", outletId))
    }

    @Test
    fun `resolveSetting returns empty list for malformed JSON`() {
        val rows = listOf(KioskSettingDto(key = "bestseller_ids", value = "not-json", outletId = outletId))
        assertEquals(emptyList<String>(), resolveSetting(rows, "bestseller_ids", outletId))
    }

    @Test
    fun `filterByOutlet keeps items with no outlet_id`() {
        val item = menuItem(outletId = null)
        assertEquals(listOf(item), filterByOutlet(listOf(item), outletId))
    }

    @Test
    fun `filterByOutlet keeps items owned by PUSAT`() {
        val item = menuItem(outletId = PUSAT_OUTLET_ID)
        assertEquals(listOf(item), filterByOutlet(listOf(item), outletId))
    }

    @Test
    fun `filterByOutlet keeps items owned by the current outlet`() {
        val item = menuItem(outletId = outletId)
        assertEquals(listOf(item), filterByOutlet(listOf(item), outletId))
    }

    @Test
    fun `filterByOutlet drops items owned by a different outlet`() {
        val item = menuItem(outletId = otherOutletId)
        assertTrue(filterByOutlet(listOf(item), outletId).isEmpty())
    }

    @Test
    fun `isItemAvailable is false when is_available is false regardless of settings`() {
        val item = menuItem(isAvailable = false)
        assertEquals(false, isItemAvailable(item, KioskSettings.EMPTY))
    }

    @Test
    fun `isItemAvailable is false when manually marked unavailable`() {
        val item = menuItem()
        val settings = KioskSettings(unavailableIds = setOf(item.id))
        assertEquals(false, isItemAvailable(item, settings))
    }

    @Test
    fun `isItemAvailable is false when auto-unavailable and not forced`() {
        val item = menuItem()
        val settings = KioskSettings(autoUnavailableIds = setOf(item.id))
        assertEquals(false, isItemAvailable(item, settings))
    }

    @Test
    fun `isItemAvailable is true when auto-unavailable but force-available`() {
        val item = menuItem()
        val settings = KioskSettings(
            autoUnavailableIds = setOf(item.id),
            forceAvailableIds = setOf(item.id)
        )
        assertEquals(true, isItemAvailable(item, settings))
    }

    @Test
    fun `isItemAvailable is true when no flags apply`() {
        assertEquals(true, isItemAvailable(menuItem(), KioskSettings.EMPTY))
    }

    private fun menuItem(
        id: String = "item-1",
        outletId: String? = null,
        isAvailable: Boolean = true
    ) = MenuItem(
        id = id,
        categoryId = "cat-1",
        outletId = outletId,
        name = "Test Item",
        price = 10_000.0,
        isAvailable = isAvailable
    )
}
