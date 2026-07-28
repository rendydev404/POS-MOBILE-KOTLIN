package com.sukashawarma.pos.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sukashawarma.pos.data.local.dao.KioskSettingDao
import com.sukashawarma.pos.data.local.dao.MenuItemDao
import com.sukashawarma.pos.data.local.entity.LocalKioskSettingEntity
import com.sukashawarma.pos.data.local.entity.LocalMenuItemEntity
import com.sukashawarma.pos.data.remote.SupabaseApi
import com.sukashawarma.pos.data.remote.dto.MenuItemDto
import com.sukashawarma.pos.data.remote.dto.PackageItemDto
import com.sukashawarma.pos.data.remote.realtime.MenuRealtimeManager
import com.sukashawarma.pos.domain.menu.KioskSettings
import com.sukashawarma.pos.domain.menu.PUSAT_OUTLET_ID
import com.sukashawarma.pos.domain.menu.filterByOutlet
import com.sukashawarma.pos.domain.menu.parseIdList
import com.sukashawarma.pos.domain.menu.resolveSetting
import com.sukashawarma.pos.domain.model.Category
import com.sukashawarma.pos.domain.model.MenuItem
import com.sukashawarma.pos.domain.model.PackageItem
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

data class MenuSnapshot(
    val items: List<MenuItem>,
    val categories: List<Category>,
    val settings: KioskSettings,
    val fromCache: Boolean
)

private val gson = Gson()
private val mapType = object : TypeToken<Map<String, Double>>() {}.type
private val stringListType = object : TypeToken<List<String>>() {}.type
private val packageListType = object : TypeToken<List<PackageItem>>() {}.type

class MenuRepository(
    private val api: SupabaseApi,
    private val menuItemDao: MenuItemDao,
    private val kioskSettingDao: KioskSettingDao,
    private val okHttpClient: OkHttpClient
) {

    /** Cache-first: emits the Room snapshot immediately (if any), then a network
     *  snapshot, then re-emits on every menu_items/kiosk_settings realtime change. */
    fun snapshot(outletId: String): Flow<MenuSnapshot> = channelFlow {
        readCache(outletId)?.let { send(it) }
        refreshInternal(outletId)?.let { send(it) }

        val realtimeManager = MenuRealtimeManager(okHttpClient, this)
        realtimeManager.onChange = { table, _, _ ->
            if (table == "menu_items" || table == "kiosk_settings") {
                launch { refreshInternal(outletId)?.let { send(it) } }
            }
        }
        realtimeManager.connect()

        awaitClose { realtimeManager.disconnect() }
    }

    suspend fun refresh(outletId: String) {
        refreshInternal(outletId)
    }

    private suspend fun refreshInternal(outletId: String): MenuSnapshot? {
        return try {
            val orFilter = "(outlet_id.is.null,outlet_id.eq.$PUSAT_OUTLET_ID,outlet_id.eq.$outletId)"
            val itemsRes = api.getMenuItems()
            val catRes = api.getCategories()
            if (!itemsRes.isSuccessful || !catRes.isSuccessful) return null

            val settingsRes = api.getKioskSettings(orFilter = orFilter)
            val settingDtos = if (settingsRes.isSuccessful) settingsRes.body().orEmpty() else emptyList()

            val items = itemsRes.body().orEmpty().map { it.toDomain() }
            val filtered = filterByOutlet(items, outletId)
            val categories = catRes.body().orEmpty().map { Category(it.id, it.name) }
            val settings = KioskSettings(
                bestsellers = resolveSetting(settingDtos, "bestseller_ids", outletId).toSet(),
                upsells = resolveSetting(settingDtos, "upsell_ids", outletId).toSet(),
                recommendations = resolveSetting(settingDtos, "recommendation_ids", outletId).toSet(),
                unavailableIds = resolveSetting(settingDtos, "unavailable_menu_ids", outletId).toSet(),
                autoUnavailableIds = resolveSetting(settingDtos, "auto_unavailable_menu_ids", outletId).toSet(),
                forceAvailableIds = resolveSetting(settingDtos, "force_available_menu_ids", outletId).toSet()
            )

            writeCache(filtered, categories, settings)
            MenuSnapshot(items = filtered, categories = categories, settings = settings, fromCache = false)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun readCache(outletId: String): MenuSnapshot? {
        val cachedEntities = menuItemDao.getAllMenuItems().first()
        if (cachedEntities.isEmpty()) return null
        val items = filterByOutlet(cachedEntities.map { it.toDomain() }, outletId)
        val settings = kioskSettingDao.getAllSettings().first().toKioskSettings()
        return MenuSnapshot(items = items, categories = emptyList(), settings = settings, fromCache = true)
    }

    private suspend fun writeCache(items: List<MenuItem>, categories: List<Category>, settings: KioskSettings) {
        val categoryNameById = categories.associate { it.id to it.name }
        val now = System.currentTimeMillis()
        menuItemDao.clearMenuItems()
        menuItemDao.insertMenuItems(items.map { it.toEntity(categoryNameById[it.categoryId] ?: "") })
        kioskSettingDao.upsertAll(settings.toEntities(now))
    }
}

private fun PackageItemDto.toDomain() = PackageItem(
    id = id,
    menuItemId = menuItemId,
    orMenuItemId = orMenuItemId,
    quantity = quantity
)

private fun MenuItemDto.toDomain(): MenuItem = MenuItem(
    id = id,
    categoryId = categoryId ?: "",
    outletId = outletId,
    name = name,
    price = price,
    strikePrice = strikePrice,
    channelPrices = channelPrices ?: emptyMap(),
    isAvailable = isAvailable ?: true,
    isAvailableOnline = isAvailableOnline ?: true,
    availableOnlineChannels = availableOnlineChannels ?: emptyList(),
    prepTimeMinutes = prepTime ?: 10,
    imageUrl = imageUrl,
    isPackage = isPackage ?: false,
    packageItems = packageItems?.map { it.toDomain() } ?: emptyList()
)

private fun MenuItem.toEntity(categoryName: String): LocalMenuItemEntity = LocalMenuItemEntity(
    id = id,
    categoryId = categoryId,
    categoryName = categoryName,
    outletId = outletId,
    name = name,
    price = price,
    strikePrice = strikePrice,
    channelPricesJson = if (channelPrices.isEmpty()) null else gson.toJson(channelPrices),
    isAvailable = isAvailable,
    isAvailableOnline = isAvailableOnline,
    availableOnlineChannelsJson = if (availableOnlineChannels.isEmpty()) null else gson.toJson(availableOnlineChannels),
    prepTimeMinutes = prepTimeMinutes,
    imageUrl = imageUrl,
    isPackage = isPackage,
    packageItemsJson = if (packageItems.isEmpty()) null else gson.toJson(packageItems)
)

private fun LocalMenuItemEntity.toDomain(): MenuItem = MenuItem(
    id = id,
    categoryId = categoryId,
    outletId = outletId,
    name = name,
    price = price,
    strikePrice = strikePrice,
    channelPrices = channelPricesJson?.let { gson.fromJson<Map<String, Double>>(it, mapType) } ?: emptyMap(),
    isAvailable = isAvailable,
    isAvailableOnline = isAvailableOnline,
    availableOnlineChannels = availableOnlineChannelsJson?.let { gson.fromJson<List<String>>(it, stringListType) } ?: emptyList(),
    prepTimeMinutes = prepTimeMinutes,
    imageUrl = imageUrl,
    isPackage = isPackage,
    packageItems = packageItemsJson?.let { gson.fromJson<List<PackageItem>>(it, packageListType) } ?: emptyList()
)

private fun KioskSettings.toEntities(syncedAt: Long): List<LocalKioskSettingEntity> = listOf(
    LocalKioskSettingEntity("bestseller_ids", gson.toJson(bestsellers.toList()), syncedAt),
    LocalKioskSettingEntity("upsell_ids", gson.toJson(upsells.toList()), syncedAt),
    LocalKioskSettingEntity("recommendation_ids", gson.toJson(recommendations.toList()), syncedAt),
    LocalKioskSettingEntity("unavailable_menu_ids", gson.toJson(unavailableIds.toList()), syncedAt),
    LocalKioskSettingEntity("auto_unavailable_menu_ids", gson.toJson(autoUnavailableIds.toList()), syncedAt),
    LocalKioskSettingEntity("force_available_menu_ids", gson.toJson(forceAvailableIds.toList()), syncedAt)
)

private fun List<LocalKioskSettingEntity>.toKioskSettings(): KioskSettings {
    val byKey = associateBy { it.settingKey }
    fun idsFor(key: String): Set<String> = byKey[key]?.jsonValue?.let { parseIdList(it).toSet() } ?: emptySet()
    return KioskSettings(
        bestsellers = idsFor("bestseller_ids"),
        upsells = idsFor("upsell_ids"),
        recommendations = idsFor("recommendation_ids"),
        unavailableIds = idsFor("unavailable_menu_ids"),
        autoUnavailableIds = idsFor("auto_unavailable_menu_ids"),
        forceAvailableIds = idsFor("force_available_menu_ids")
    )
}
