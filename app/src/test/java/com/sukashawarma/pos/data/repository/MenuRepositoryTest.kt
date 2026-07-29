package com.sukashawarma.pos.data.repository

import com.sukashawarma.pos.data.local.dao.KioskSettingDao
import com.sukashawarma.pos.data.local.dao.MenuItemDao
import com.sukashawarma.pos.data.local.entity.LocalKioskSettingEntity
import com.sukashawarma.pos.data.local.entity.LocalMenuItemEntity
import com.sukashawarma.pos.data.remote.SupabaseApi
import com.sukashawarma.pos.data.remote.dto.CategoryDto
import com.sukashawarma.pos.data.remote.dto.KioskSettingDto
import com.sukashawarma.pos.data.remote.dto.MenuItemDto
import com.sukashawarma.pos.data.remote.dto.UpsertKioskSettingPayload
import com.sukashawarma.pos.domain.model.MenuItem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class MenuRepositoryTest {

    private val outletId = "outlet-a"

    private fun menuItemDto(id: String, outletId: String? = null, description: String? = null) = MenuItemDto(
        id = id,
        categoryId = "cat-1",
        outletId = outletId,
        name = "Item $id",
        description = description,
        price = 10_000.0,
        strikePrice = null,
        channelPrices = null,
        imageUrl = null,
        isAvailable = true,
        isAvailableOnline = true,
        availableOnlineChannels = null,
        sortOrder = 1,
        prepTime = null,
        isPackage = null,
        packageItems = null,
        categories = null
    )

    private fun fakeMenuItemDao(initial: List<LocalMenuItemEntity> = emptyList()): MenuItemDao {
        val state = MutableStateFlow(initial)
        return object : MenuItemDao {
            override fun getAllMenuItems() = state
            override suspend fun insertMenuItems(items: List<LocalMenuItemEntity>) {
                state.value = items
            }
            override suspend fun clearMenuItems() {
                state.value = emptyList()
            }
        }
    }

    private fun fakeKioskSettingDao(): KioskSettingDao {
        val state = MutableStateFlow<List<LocalKioskSettingEntity>>(emptyList())
        return object : KioskSettingDao {
            override fun getAllSettings() = state
            override suspend fun upsertAll(settings: List<LocalKioskSettingEntity>) {
                state.value = settings
            }
        }
    }

    @Test
    fun `refresh returns filtered items and resolved settings on success`() = runTest {
        val api = mockk<SupabaseApi>()
        coEvery { api.getMenuItems() } returns Response.success(
            listOf(menuItemDto("item-own", outletId), menuItemDto("item-other", "outlet-b"))
        )
        coEvery { api.getCategories() } returns Response.success(listOf(CategoryDto("cat-1", "Utama", 1)))
        coEvery { api.getKioskSettings(orFilter = any(), keyFilter = any(), select = any()) } returns Response.success(
            listOf(KioskSettingDto("bestseller_ids", "[\"item-own\"]", outletId))
        )

        val repo = MenuRepository(api, fakeMenuItemDao(), fakeKioskSettingDao(), mockk<OkHttpClient>(relaxed = true))

        // Cache starts empty, so the first value the Flow emits is the network refresh,
        // not a cache hit — that's what lets this test assert fromCache == false.
        val snapshot = repo.snapshot(outletId).first()
        assertEquals(listOf("item-own"), snapshot.items.map { it.id })
        assertTrue(snapshot.settings.bestsellers.contains("item-own"))
        assertEquals(false, snapshot.fromCache)
    }

    @Test
    fun `snapshot falls back to cache with fromCache true when the network call fails`() = runTest {
        val api = mockk<SupabaseApi>()
        coEvery { api.getMenuItems() } throws RuntimeException("network down")
        coEvery { api.getCategories() } returns Response.success(emptyList())
        coEvery { api.getKioskSettings(orFilter = any(), keyFilter = any(), select = any()) } returns Response.success(emptyList())

        val cachedItem = LocalMenuItemEntity(
            id = "cached-item",
            categoryId = "cat-1",
            categoryName = "Utama",
            outletId = outletId,
            name = "Cached Item",
            description = "Cached description",
            price = 5_000.0,
            strikePrice = null,
            channelPricesJson = null,
            isAvailable = true,
            isAvailableOnline = true,
            availableOnlineChannelsJson = null,
            prepTimeMinutes = 10,
            imageUrl = null,
            isPackage = false,
            packageItemsJson = null
        )
        val dao = fakeMenuItemDao(listOf(cachedItem))
        val repo = MenuRepository(api, dao, fakeKioskSettingDao(), mockk<OkHttpClient>(relaxed = true))

        val snapshot = repo.snapshot(outletId).first()
        assertEquals(listOf("cached-item"), snapshot.items.map { it.id })
        assertTrue(snapshot.fromCache)
        // categoryName must survive the offline path since readCache() returns
        // categories = emptyList() and cannot re-resolve it from a lookup map.
        assertEquals("Utama", snapshot.items.first().categoryName)
        assertEquals("Cached description", snapshot.items.first().description)
    }

    @Test
    fun `description and categoryName survive a domain to entity to domain round trip`() {
        val item = MenuItemDto(
            id = "item-1",
            categoryId = "cat-1",
            outletId = null,
            name = "Item",
            description = "Rasa pedas",
            price = 10_000.0,
            strikePrice = null,
            channelPrices = null,
            imageUrl = null,
            isAvailable = true,
            isAvailableOnline = true,
            availableOnlineChannels = null,
            sortOrder = null,
            prepTime = null,
            isPackage = null,
            packageItems = null,
            categories = CategoryDto("cat-1", "Menu Utama", 1)
        ).toDomain()

        val roundTripped = item.toEntity().toDomain()

        assertEquals(item.description, roundTripped.description)
        assertEquals(item.categoryName, roundTripped.categoryName)
        assertEquals("Rasa pedas", roundTripped.description)
        assertEquals("Menu Utama", roundTripped.categoryName)
    }

    private fun menuItem(id: String, outletId: String? = null, isAvailable: Boolean = true) = MenuItem(
        id = id,
        categoryId = "cat-1",
        outletId = outletId,
        name = "Item $id",
        price = 10_000.0,
        isAvailable = isAvailable
    )

    @Test
    fun `toggleAvailability on own-outlet item patches menu_items and does not touch kiosk_settings`() = runTest {
        val api = mockk<SupabaseApi>()
        coEvery { api.updateMenuItemAvailability(any(), any()) } returns Response.success(null)

        val repo = MenuRepository(api, fakeMenuItemDao(), fakeKioskSettingDao(), mockk<OkHttpClient>(relaxed = true))
        val item = menuItem("item-own", outletId = outletId, isAvailable = true)

        val result = repo.toggleAvailability(item, outletId, unavailableIds = emptySet())

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { api.updateMenuItemAvailability("eq.item-own", mapOf("is_available" to false)) }
        coVerify(exactly = 0) { api.upsertKioskSetting(any(), any()) }
        coVerify(exactly = 0) { api.upsertKioskSettingOnPrimaryKey(any()) }
    }

    @Test
    fun `toggleAvailability on a global item not yet unavailable upserts via the untargeted endpoint`() = runTest {
        val api = mockk<SupabaseApi>()
        val payloadSlot = slot<UpsertKioskSettingPayload>()
        coEvery { api.upsertKioskSettingOnPrimaryKey(capture(payloadSlot)) } returns Response.success(null)

        val repo = MenuRepository(api, fakeMenuItemDao(), fakeKioskSettingDao(), mockk<OkHttpClient>(relaxed = true))
        val item = menuItem("item-global", outletId = null)

        val result = repo.toggleAvailability(item, outletId, unavailableIds = emptySet())

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { api.upsertKioskSetting(any(), any()) }
        coVerify(exactly = 0) { api.updateMenuItemAvailability(any(), any()) }
        assertEquals(outletId, payloadSlot.captured.outletId)
        assertEquals("unavailable_menu_ids", payloadSlot.captured.key)
        assertTrue(payloadSlot.captured.value.contains("item-global"))
    }

    @Test
    fun `toggleAvailability on an already-unavailable item removes it from the list`() = runTest {
        val api = mockk<SupabaseApi>()
        val payloadSlot = slot<UpsertKioskSettingPayload>()
        coEvery { api.upsertKioskSettingOnPrimaryKey(capture(payloadSlot)) } returns Response.success(null)

        val repo = MenuRepository(api, fakeMenuItemDao(), fakeKioskSettingDao(), mockk<OkHttpClient>(relaxed = true))
        val item = menuItem("item-global", outletId = null)

        val result = repo.toggleAvailability(item, outletId, unavailableIds = setOf("item-global", "item-other"))

        assertTrue(result.isSuccess)
        assertFalse(payloadSlot.captured.value.contains("item-global"))
        assertTrue(payloadSlot.captured.value.contains("item-other"))
    }

    @Test
    fun `toggleSettingMembership adds then removes via the on_conflict endpoint`() = runTest {
        val api = mockk<SupabaseApi>()
        val payloadSlot = slot<UpsertKioskSettingPayload>()
        coEvery { api.upsertKioskSetting(onConflict = any(), payload = capture(payloadSlot)) } returns Response.success(null)

        val repo = MenuRepository(api, fakeMenuItemDao(), fakeKioskSettingDao(), mockk<OkHttpClient>(relaxed = true))

        val added = repo.toggleSettingMembership("bestseller_ids", "item-1", outletId, currentIds = emptySet())
        assertTrue(added.isSuccess)
        assertEquals("bestseller_ids", payloadSlot.captured.key)
        assertEquals(outletId, payloadSlot.captured.outletId)
        assertTrue(payloadSlot.captured.value.contains("item-1"))

        val removed = repo.toggleSettingMembership("bestseller_ids", "item-1", outletId, currentIds = setOf("item-1"))
        assertTrue(removed.isSuccess)
        assertFalse(payloadSlot.captured.value.contains("item-1"))

        coVerify(exactly = 0) { api.upsertKioskSettingOnPrimaryKey(any()) }
        coVerify(exactly = 0) { api.updateMenuItemAvailability(any(), any()) }
    }

    @Test
    fun `toggleSettingMembership returns failure on a non-2xx response`() = runTest {
        val api = mockk<SupabaseApi>()
        coEvery { api.upsertKioskSetting(onConflict = any(), payload = any()) } returns
            Response.error(500, "error".toResponseBody(null))

        val repo = MenuRepository(api, fakeMenuItemDao(), fakeKioskSettingDao(), mockk<OkHttpClient>(relaxed = true))

        val result = repo.toggleSettingMembership("bestseller_ids", "item-1", outletId, currentIds = emptySet())

        assertTrue(result.isFailure)
    }
}
