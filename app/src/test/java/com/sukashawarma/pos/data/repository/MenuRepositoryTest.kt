package com.sukashawarma.pos.data.repository

import com.sukashawarma.pos.data.local.dao.KioskSettingDao
import com.sukashawarma.pos.data.local.dao.MenuItemDao
import com.sukashawarma.pos.data.local.entity.LocalKioskSettingEntity
import com.sukashawarma.pos.data.local.entity.LocalMenuItemEntity
import com.sukashawarma.pos.data.remote.SupabaseApi
import com.sukashawarma.pos.data.remote.dto.CategoryDto
import com.sukashawarma.pos.data.remote.dto.KioskSettingDto
import com.sukashawarma.pos.data.remote.dto.MenuItemDto
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class MenuRepositoryTest {

    private val outletId = "outlet-a"

    private fun menuItemDto(id: String, outletId: String? = null) = MenuItemDto(
        id = id,
        categoryId = "cat-1",
        outletId = outletId,
        name = "Item $id",
        description = null,
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
    }
}
