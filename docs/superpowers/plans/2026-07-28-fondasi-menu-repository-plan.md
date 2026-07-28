# Fondasi Menu Repository Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build one shared `MenuRepository` (Supabase + Room cache + realtime) that both `MenuManagementViewModel` and `POSManualOrderViewModel` consume, replacing their duplicated, unfiltered, uncached `getCategories()`/`getMenuItems()` calls — with menu availability rules ported byte-for-byte from the web app (`apps/pos-kasir/app/kasir/menu/KasirMenuClient.tsx` and `app/kasir/order-manual/page.tsx`).

**Architecture:** `MenuRepository.snapshot(outletId)` returns a `Flow<MenuSnapshot>` that emits a Room-cached snapshot first (if any), then a fresh network snapshot, then re-emits on every Supabase realtime change to `menu_items`/`kiosk_settings`. Three pure functions in `domain/menu/MenuRules.kt` (`resolveSetting`, `filterByOutlet`, `isItemAvailable`) encode the precedence/filter/availability rules the web computes inline, so both `B` (Manajemen Menu) and `D` (Pesanan Baru) can reuse them later without re-deriving the logic.

**Tech Stack:** Kotlin, Retrofit/OkHttp (Supabase REST), Room (offline cache), hand-rolled Phoenix WebSocket client (existing `OrderRealtimeManager` pattern), Gson, JUnit4 + MockK for unit tests.

## Global Constraints

- **Supabase is the only source of truth.** Room is a read-only cache; nothing here writes menu data back except the cache-refresh path itself.
- **No offline queueing for menu changes.** Web blocks menu edits while offline; this plan does not add a sync queue for menu writes (there are no menu writes in this plan — see "Batas sub-project A" below).
- **Room migration must be explicit** (`Migration(2, 3)`), never `fallbackToDestructiveMigration()`, because the same database holds `sync_queue` (offline orders pending upload) that must survive an app update.
- **No UI changes in this plan.** `MenuManagementScreen.kt` and `POSManualOrderScreen.kt` are not touched. Only their ViewModels' data-loading internals change; every `StateFlow` name/type the screens already read (`categories`, `menuItems`, `selectedCategoryId`, `searchQuery`, `isLoading`, `statusMessage`, `canEditPrice`, `updatePrice`, `toggleAvailability`) stays as-is.
- **Business-rule constants:** `PUSAT_OUTLET_ID = "550e8400-e29b-41d4-a716-446655440001"`. Precedence for `kiosk_settings` rows: outlet's own row > PUSAT row > global (`outlet_id IS NULL`) row — ported from `getSetting()` in both web files (`KasirMenuClient.tsx:55-75`, `order-manual/page.tsx:215-235`).
- Reference spec: [`docs/superpowers/specs/2026-07-28-fondasi-menu-repository-design.md`](../specs/2026-07-28-fondasi-menu-repository-design.md).

## Deviations from the spec (decided during planning, with reasons)

The spec (§6.1) sketched `MenuRepository.setUnavailable(...)` and `setFlag(...)` as contract placeholders "for sub-project B". This plan does **not** add them: an empty/unimplemented method that nothing calls yet is exactly the kind of speculative scaffolding that rots before B starts. B's plan will add these methods together with their real dual-write logic (direct `menu_items` PATCH vs. `kiosk_settings` UPSERT depending on ownership) and their own tests. `MenuSnapshot`, `snapshot()`, and `refresh()` — the parts A actually needs — are built in full below.

The spec (§6.3) said to "generalize `OrderRealtimeManager`" to also watch `menu_items`/`kiosk_settings`. This plan instead adds a **separate** `MenuRealtimeManager` (same hand-rolled Phoenix-channel approach, new file). Reason: `OrderRealtimeManager` is live in production on the Dashboard (order alerts/sound) — changing its constructor or message shape risks that flow for no benefit, and the web app itself does the equivalent thing: `KasirOrderClient.tsx` and `KasirMenuClient.tsx` each open their **own** independent `supabase.channel(...)` subscription rather than sharing one. A second small manager class mirrors that reality and keeps this change's blast radius to files nothing else depends on yet.

`available_outlets` (mentioned in the spec's §8 "asumsi belum diverifikasi") is now confirmed dead: it exists in neither `supabase-schema.sql`, any migration, nor `apps/pos-kasir/types/index.ts`'s `MenuItem` interface (checked directly — see `types/index.ts:43-62`). `filterByOutlet` below implements only the `outlet_id` branch of the web's filter; the `available_outlets` branch is omitted because there is no field to feed it, on either the Kotlin or the (properly typed) web side.

---

### Task 1: Domain rules — `MenuRules`, `KioskSettings`, extended `MenuItem`

**Files:**
- Modify: `app/build.gradle.kts` (test dependencies)
- Modify: `app/src/main/java/com/sukashawarma/pos/domain/model/MenuItem.kt`
- Create: `app/src/main/java/com/sukashawarma/pos/domain/menu/KioskSettings.kt`
- Create: `app/src/main/java/com/sukashawarma/pos/domain/menu/MenuRules.kt`
- Test: `app/src/test/java/com/sukashawarma/pos/domain/menu/MenuRulesTest.kt`

**Interfaces:**
- Produces: `const val PUSAT_OUTLET_ID: String`; `fun parseIdList(raw: String?): List<String>`; `fun resolveSetting(rows: List<KioskSettingDto>, key: String, outletId: String): List<String>`; `fun filterByOutlet(items: List<MenuItem>, outletId: String): List<MenuItem>`; `fun isItemAvailable(item: MenuItem, settings: KioskSettings): Boolean`; `data class KioskSettings(bestsellers, upsells, recommendations, unavailableIds, autoUnavailableIds, forceAvailableIds: Set<String>)` with `KioskSettings.EMPTY`; extended `MenuItem` (see below) and new `data class PackageItem(id, menuItemId, orMenuItemId, quantity)`.
- Consumes: nothing yet (this task has no dependencies on later tasks). `resolveSetting` takes `KioskSettingDto` by name only — Task 2 defines that class; this task's test file constructs it directly by fully-qualified constructor, which works fine even though Task 2 hasn't run yet **only if** Task 2 runs first. Run Task 2 before Task 1 is not required structurally, but this plan executes tasks in numeric order and Task 2 depends on nothing in Task 1, so to avoid a forward reference, **do Task 2's DTO changes first if executing out of order; otherwise follow the plan's numeric order and this compiles fine because both tasks are applied to the same module before either is built standalone.**

To keep this task buildable on its own (bite-sized, independently testable), `KioskSettingDto` is defined as a minimal local shape check: the test file uses the real `com.sukashawarma.pos.data.remote.dto.KioskSettingDto`, so **Task 1 must be implemented after Task 2's DTO addition exists**, OR Task 2 is done first. Reorder execution so **Task 2 runs before Task 1** if doing this task-by-task in a fresh subagent per task with no cross-task coordination. (If using inline/sequential execution in one session, order doesn't matter — just do Task 2 before running Task 1's test.)

- [ ] **Step 1: Add test dependencies**

In `app/build.gradle.kts`, inside the existing `// Testing` block (currently just `testImplementation("junit:junit:4.13.2")` plus the `androidTest*` lines), add two lines right after the `junit:junit` line:

```kotlin
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.9")
```

- [ ] **Step 2: Extend `MenuItem` domain model**

Replace the full contents of `app/src/main/java/com/sukashawarma/pos/domain/model/MenuItem.kt`:

```kotlin
package com.sukashawarma.pos.domain.model

data class Category(
    val id: String,
    val name: String,
    val isAvailable: Boolean = true
)

data class PackageItem(
    val id: String,
    val menuItemId: String,
    val orMenuItemId: String?,
    val quantity: Int
)

data class MenuItem(
    val id: String,
    val categoryId: String,
    val outletId: String? = null,
    val name: String,
    val price: Double,
    val strikePrice: Double? = null,
    val channelPrices: Map<String, Double> = emptyMap(),
    val isAvailable: Boolean = true,
    val isAvailableOnline: Boolean = true,
    val availableOnlineChannels: List<String> = emptyList(),
    val prepTimeMinutes: Int = 10,
    val imageUrl: String? = null,
    val isPackage: Boolean = false,
    val packageItems: List<PackageItem> = emptyList()
)
```

- [ ] **Step 3: Write `KioskSettings.kt`**

```kotlin
package com.sukashawarma.pos.domain.menu

data class KioskSettings(
    val bestsellers: Set<String> = emptySet(),
    val upsells: Set<String> = emptySet(),
    val recommendations: Set<String> = emptySet(),
    val unavailableIds: Set<String> = emptySet(),
    val autoUnavailableIds: Set<String> = emptySet(),
    val forceAvailableIds: Set<String> = emptySet()
) {
    companion object {
        val EMPTY = KioskSettings()
    }
}
```

- [ ] **Step 4: Write the failing test `MenuRulesTest.kt`**

```kotlin
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
```

- [ ] **Step 5: Run test to verify it fails (compile error — `MenuRules.kt` doesn't exist yet)**

Run: `./gradlew testDebugUnitTest --tests "com.sukashawarma.pos.domain.menu.MenuRulesTest"`
Expected: FAIL — unresolved reference `resolveSetting`/`filterByOutlet`/`isItemAvailable`/`PUSAT_OUTLET_ID`.

- [ ] **Step 6: Write `MenuRules.kt`**

```kotlin
package com.sukashawarma.pos.domain.menu

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sukashawarma.pos.data.remote.dto.KioskSettingDto
import com.sukashawarma.pos.domain.model.MenuItem

const val PUSAT_OUTLET_ID = "550e8400-e29b-41d4-a716-446655440001"

private val gson = Gson()
private val stringListType = object : TypeToken<List<String>>() {}.type

/** Mirrors the web's `parseIds()` — malformed JSON becomes an empty list, never a crash. */
fun parseIdList(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        gson.fromJson<List<String>>(raw, stringListType) ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * Port of `getSetting()` from KasirMenuClient.tsx:55 and order-manual/page.tsx:215.
 * Precedence: this outlet's row > PUSAT's row > the global (`outlet_id IS NULL`) row.
 * `on_conflict=outlet_id,key` means at most one row per weight bucket in practice.
 */
fun resolveSetting(rows: List<KioskSettingDto>, key: String, outletId: String): List<String> {
    val matching = rows.filter { it.key == key }
    fun weight(rowOutletId: String?): Int = when (rowOutletId) {
        outletId -> 3
        PUSAT_OUTLET_ID -> 2
        null -> 1
        else -> 0
    }
    val best = matching.maxByOrNull { weight(it.outletId) } ?: return emptyList()
    return parseIdList(best.value)
}

/** Port of the outlet filter in both web files — the `available_outlets` branch is
 *  omitted because that column exists in neither the schema nor `types/index.ts`. */
fun filterByOutlet(items: List<MenuItem>, outletId: String): List<MenuItem> {
    return items.filter { item ->
        item.outletId == null || item.outletId == outletId || item.outletId == PUSAT_OUTLET_ID
    }
}

/** Port of `isAvail` — KasirMenuClient.tsx:443 and order-manual/page.tsx:381 (written twice on web). */
fun isItemAvailable(item: MenuItem, settings: KioskSettings): Boolean {
    val manualUnav = item.id in settings.unavailableIds
    val autoUnav = item.id in settings.autoUnavailableIds
    val forceAvail = item.id in settings.forceAvailableIds
    return item.isAvailable && !(manualUnav || (autoUnav && !forceAvail))
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.sukashawarma.pos.domain.menu.MenuRulesTest"`
Expected: PASS, 13 tests green. (This step requires Task 2's `KioskSettingDto` to already exist — see the note in Interfaces above. If it doesn't exist yet, do Task 2's Step 2 first, then return here.)

- [ ] **Step 8: Commit**

```bash
git add app/build.gradle.kts app/src/main/java/com/sukashawarma/pos/domain/model/MenuItem.kt app/src/main/java/com/sukashawarma/pos/domain/menu/ app/src/test/java/com/sukashawarma/pos/domain/menu/
git commit -m "feat: port menu availability/precedence rules from web as pure functions"
```

---

### Task 2: Remote layer — extended `MenuItemDto`, `KioskSettingDto`, `SupabaseApi` endpoints

**Files:**
- Modify: `app/src/main/java/com/sukashawarma/pos/data/remote/dto/SupabaseDtos.kt`
- Modify: `app/src/main/java/com/sukashawarma/pos/data/remote/SupabaseApi.kt`
- Test: `app/src/test/java/com/sukashawarma/pos/data/remote/dto/MenuItemDtoTest.kt`

**Interfaces:**
- Consumes: nothing from Task 1.
- Produces: `MenuItemDto` with `outletId, strikePrice, channelPrices, isAvailableOnline, availableOnlineChannels, isPackage, packageItems: List<PackageItemDto>?, categories: CategoryDto?`; `data class PackageItemDto(id, menuItemId, orMenuItemId, quantity)`; `data class KioskSettingDto(key: String, value: String?, outletId: String?)`; `data class UpsertKioskSettingPayload(outletId: String?, key: String, value: String)`; `SupabaseApi.getMenuItems()` with an expanded default `select`; `SupabaseApi.getKioskSettings(orFilter: String, keyFilter: String = ..., select: String = ...): Response<List<KioskSettingDto>>`; `SupabaseApi.upsertKioskSetting(onConflict: String = "outlet_id,key", payload: UpsertKioskSettingPayload): Response<Void>`.

- [ ] **Step 1: Write the failing test — confirms Gson maps the new DTO fields correctly**

```kotlin
package com.sukashawarma.pos.data.remote.dto

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MenuItemDtoTest {

    private val gson = Gson()

    @Test
    fun `deserializes full menu item payload including package and channel fields`() {
        val json = """
            {
              "id": "item-1",
              "category_id": "cat-1",
              "outlet_id": "outlet-a",
              "name": "Shawarma Ayam",
              "description": null,
              "price": 25000.0,
              "strike_price": 30000.0,
              "channel_prices": {"gofood": 27000.0, "grabfood": 28000.0},
              "image_url": null,
              "is_available": true,
              "is_available_online": false,
              "available_online_channels": ["gofood"],
              "sort_order": 1,
              "prep_time": 12,
              "is_package": true,
              "package_items": [
                {"id": "pk-1", "menu_item_id": "item-2", "or_menu_item_id": null, "quantity": 2}
              ],
              "categories": {"id": "cat-1", "name": "Menu Utama", "sort_order": 1}
            }
        """.trimIndent()

        val dto = gson.fromJson(json, MenuItemDto::class.java)

        assertEquals("outlet-a", dto.outletId)
        assertEquals(30000.0, dto.strikePrice)
        assertEquals(27000.0, dto.channelPrices?.get("gofood"))
        assertEquals(false, dto.isAvailableOnline)
        assertEquals(listOf("gofood"), dto.availableOnlineChannels)
        assertEquals(true, dto.isPackage)
        assertEquals(1, dto.packageItems?.size)
        assertEquals("item-2", dto.packageItems?.first()?.menuItemId)
        assertEquals("Menu Utama", dto.categories?.name)
    }

    @Test
    fun `missing optional fields deserialize to null rather than throwing`() {
        val json = """{"id":"item-1","category_id":"cat-1","name":"Item","description":null,"price":1000.0,"image_url":null}"""
        val dto = gson.fromJson(json, MenuItemDto::class.java)
        assertNull(dto.outletId)
        assertNull(dto.channelPrices)
        assertNull(dto.packageItems)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.sukashawarma.pos.data.remote.dto.MenuItemDtoTest"`
Expected: FAIL — compile error, `dto.outletId`/`dto.strikePrice`/etc. don't exist on the current `MenuItemDto`.

- [ ] **Step 3: Extend `SupabaseDtos.kt`**

Replace the existing `data class MenuItemDto(...)` block (lines 30-41) with:

```kotlin
data class MenuItemDto(
    val id: String,
    @SerializedName("category_id") val categoryId: String?,
    @SerializedName("outlet_id") val outletId: String?,
    val name: String,
    val description: String?,
    val price: Double,
    @SerializedName("strike_price") val strikePrice: Double?,
    @SerializedName("channel_prices") val channelPrices: Map<String, Double>?,
    @SerializedName("image_url") val imageUrl: String?,
    @SerializedName("is_available") val isAvailable: Boolean?,
    @SerializedName("is_available_online") val isAvailableOnline: Boolean?,
    @SerializedName("available_online_channels") val availableOnlineChannels: List<String>?,
    @SerializedName("sort_order") val sortOrder: Int?,
    // Optional in the response — older rows may not have it, so callers fall back to 10.
    @SerializedName("prep_time") val prepTime: Int?,
    @SerializedName("is_package") val isPackage: Boolean?,
    @SerializedName("package_items") val packageItems: List<PackageItemDto>?,
    val categories: CategoryDto?
)

data class PackageItemDto(
    val id: String,
    @SerializedName("menu_item_id") val menuItemId: String,
    @SerializedName("or_menu_item_id") val orMenuItemId: String?,
    val quantity: Int
)

/** Row of `kiosk_settings` — see MenuRules.resolveSetting() for the precedence this feeds. */
data class KioskSettingDto(
    val key: String,
    val value: String?,
    @SerializedName("outlet_id") val outletId: String?
)

/** Body for upserting one `kiosk_settings` row — same shape web sends (KasirMenuClient.tsx:257). */
data class UpsertKioskSettingPayload(
    @SerializedName("outlet_id") val outletId: String?,
    val key: String,
    val value: String
)
```

Add the import at the top of the file if not already present: `import com.google.gson.annotations.SerializedName` (already there).

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.sukashawarma.pos.data.remote.dto.MenuItemDtoTest"`
Expected: PASS, 2 tests green.

- [ ] **Step 5: Extend `SupabaseApi.kt`**

Replace the existing `getMenuItems` method (lines 37-42) with a version that joins categories and package items, matching the superset of what both web pages select:

```kotlin
    // Fetch Menu Items — select mirrors the union of what KasirMenuClient.tsx and
    // order-manual/page.tsx request, so one call serves both Manajemen Menu and Pesanan Baru.
    @GET("rest/v1/menu_items")
    suspend fun getMenuItems(
        @Query("select") select: String =
            "*, categories(id,name,sort_order), package_items:menu_packages!package_id(id, menu_item_id, or_menu_item_id, quantity)",
        @Query("order") order: String = "sort_order.asc"
    ): Response<List<MenuItemDto>>
```

Then add two new endpoints right after `updateMenuItem` (after line 57), before the `// Fetch Active Promos for Outlet` comment:

```kotlin
    // kiosk_settings rows relevant to one outlet: its own, PUSAT's, and the global
    // (outlet_id IS NULL) row — mirrors the `.or(...)` filter both web pages use.
    @GET("rest/v1/kiosk_settings")
    suspend fun getKioskSettings(
        @Query("or") orFilter: String,
        @Query("key") keyFilter: String =
            "in.(bestseller_ids,upsell_ids,unavailable_menu_ids,auto_unavailable_menu_ids,force_available_menu_ids,recommendation_ids)",
        @Query("select") select: String = "key,value,outlet_id"
    ): Response<List<KioskSettingDto>>

    // Upsert one kiosk_settings row (bestseller/upsell/recommendation/unavailable/etc. list for
    // one outlet+key). Contract only in sub-project A — B is the first caller.
    @Headers("Prefer: resolution=merge-duplicates")
    @POST("rest/v1/kiosk_settings")
    suspend fun upsertKioskSetting(
        @Query("on_conflict") onConflict: String = "outlet_id,key",
        @Body payload: UpsertKioskSettingPayload
    ): Response<Void>
```

- [ ] **Step 6: Verify the module still compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (`MenuManagementViewModel.kt` and `POSManualOrderViewModel.kt` still construct `MenuItem(...)` using only the original fields by name, which remain valid with the new defaulted fields, so nothing else breaks yet.)

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/sukashawarma/pos/data/remote/dto/SupabaseDtos.kt app/src/main/java/com/sukashawarma/pos/data/remote/SupabaseApi.kt app/src/test/java/com/sukashawarma/pos/data/remote/dto/
git commit -m "feat: extend MenuItemDto and SupabaseApi for channel prices, packages, kiosk_settings"
```

---

### Task 3: Local persistence — extended `LocalMenuItemEntity`, `LocalKioskSettingEntity`, Room migration 2→3

**Files:**
- Modify: `app/src/main/java/com/sukashawarma/pos/data/local/entity/LocalMenuItemEntity.kt`
- Modify: `app/src/main/java/com/sukashawarma/pos/data/local/dao/MenuItemDao.kt`
- Modify: `app/src/main/java/com/sukashawarma/pos/data/local/AppDatabase.kt`
- Create: `app/src/androidTest/java/com/sukashawarma/pos/data/local/MigrationTest.kt`
- Modify: `app/build.gradle.kts` (androidTest dependency for `room-testing`)

**Interfaces:**
- Consumes: nothing from Tasks 1-2.
- Produces: `LocalMenuItemEntity` with new columns (`outletId, strikePrice, channelPricesJson, isAvailableOnline, availableOnlineChannelsJson, isPackage, packageItemsJson`); `data class LocalKioskSettingEntity(settingKey: String, jsonValue: String, syncedAt: Long)` (primary key `settingKey`, not `key` — SQLite reserves `KEY` as a keyword, so the field is named to avoid quoting pitfalls; functionally it is still "one row per setting key"); `KioskSettingDao` with `getAllSettings(): Flow<List<LocalKioskSettingEntity>>` and `upsertAll(settings: List<LocalKioskSettingEntity>)`; `AppDatabase.kioskSettingDao(): KioskSettingDao`; `AppDatabase` version bumped to 3 with `MIGRATION_2_3` applied via `addMigrations`.

- [ ] **Step 1: Add `room-testing` for the migration test**

In `app/build.gradle.kts`, in the `dependencies` block, add next to the other `androidTestImplementation` lines:

```kotlin
    androidTestImplementation("androidx.room:room-testing:2.6.1")
```

- [ ] **Step 2: Extend `LocalMenuItemEntity.kt` and add `LocalKioskSettingEntity`**

Replace the `LocalMenuItemEntity` class (lines 6-16) and add the new entity after `SyncQueueEntity`:

```kotlin
@Entity(tableName = "local_menu_items")
data class LocalMenuItemEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val categoryName: String,
    val outletId: String?,
    val name: String,
    val price: Double,
    val strikePrice: Double?,
    val channelPricesJson: String?,
    val isAvailable: Boolean,
    val isAvailableOnline: Boolean,
    val availableOnlineChannelsJson: String?,
    val prepTimeMinutes: Int,
    val imageUrl: String?,
    val isPackage: Boolean,
    val packageItemsJson: String?
)

/** One row per resolved (already precedence-applied) kiosk_settings key — mirrors the
 *  web's Dexie cache shape (`db.kiosk_settings.put({ id: key, settings_data, synced_at })`). */
@Entity(tableName = "local_kiosk_settings")
data class LocalKioskSettingEntity(
    @PrimaryKey val settingKey: String,
    val jsonValue: String,
    val syncedAt: Long
)
```

(Leave `SyncQueueEntity` untouched below it.)

- [ ] **Step 3: Add `KioskSettingDao`**

In `MenuItemDao.kt`, add a new DAO interface after `MenuItemDao` and before `SyncQueueDao`, and add the new entity import:

```kotlin
import com.sukashawarma.pos.data.local.entity.LocalKioskSettingEntity
```

```kotlin
@Dao
interface KioskSettingDao {
    @Query("SELECT * FROM local_kiosk_settings")
    fun getAllSettings(): Flow<List<LocalKioskSettingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(settings: List<LocalKioskSettingEntity>)
}
```

- [ ] **Step 4: Bump `AppDatabase` to version 3 with an explicit migration**

Replace the full contents of `AppDatabase.kt`:

```kotlin
package com.sukashawarma.pos.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sukashawarma.pos.data.local.dao.KioskSettingDao
import com.sukashawarma.pos.data.local.dao.MenuItemDao
import com.sukashawarma.pos.data.local.dao.OrderDao
import com.sukashawarma.pos.data.local.dao.SyncQueueDao
import com.sukashawarma.pos.data.local.entity.LocalKioskSettingEntity
import com.sukashawarma.pos.data.local.entity.LocalMenuItemEntity
import com.sukashawarma.pos.data.local.entity.LocalOrderEntity
import com.sukashawarma.pos.data.local.entity.SyncQueueEntity

@Database(
    entities = [
        LocalOrderEntity::class,
        LocalMenuItemEntity::class,
        SyncQueueEntity::class,
        LocalKioskSettingEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun orderDao(): OrderDao
    abstract fun menuItemDao(): MenuItemDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun kioskSettingDao(): KioskSettingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // sync_queue holds offline orders not yet uploaded — a destructive migration
        // here would silently delete unsynced sales, so 2->3 is handled explicitly.
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE local_menu_items ADD COLUMN outletId TEXT")
                db.execSQL("ALTER TABLE local_menu_items ADD COLUMN strikePrice REAL")
                db.execSQL("ALTER TABLE local_menu_items ADD COLUMN channelPricesJson TEXT")
                db.execSQL("ALTER TABLE local_menu_items ADD COLUMN isAvailableOnline INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE local_menu_items ADD COLUMN availableOnlineChannelsJson TEXT")
                db.execSQL("ALTER TABLE local_menu_items ADD COLUMN isPackage INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE local_menu_items ADD COLUMN packageItemsJson TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS local_kiosk_settings (
                        settingKey TEXT NOT NULL PRIMARY KEY,
                        jsonValue TEXT NOT NULL,
                        syncedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pos_sukashawarma.db"
                ).addMigrations(MIGRATION_2_3)
                 .fallbackToDestructiveMigration()
                 .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
```

`fallbackToDestructiveMigration()` stays as a last-resort safety net for version jumps this app never actually produces (e.g. someone downgrading), but `addMigrations(MIGRATION_2_3)` means the real, tested 2→3 path Room will actually take never touches it.

- [ ] **Step 5: Write the migration instrumented test**

```kotlin
package com.sukashawarma.pos.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class MigrationTest {

    private val dbName = "migration-test.db"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate2To3_preservesExistingSyncQueueRows() {
        var db = helper.createDatabase(dbName, 2)
        db.execSQL(
            "INSERT INTO sync_queue (queueId, actionType, payloadJson, createdAt) VALUES (1, 'CREATE_ORDER', '{}', 1000)"
        )
        db.close()

        db = helper.runMigrationsAndValidate(dbName, 3, true, AppDatabase.MIGRATION_2_3)

        val cursor = db.query("SELECT COUNT(*) FROM sync_queue")
        cursor.moveToFirst()
        assert(cursor.getInt(0) == 1) { "sync_queue row lost during migration" }
        cursor.close()

        val settingsCursor = db.query("SELECT COUNT(*) FROM local_kiosk_settings")
        settingsCursor.moveToFirst()
        assert(settingsCursor.getInt(0) == 0) { "local_kiosk_settings should exist and be empty" }
        settingsCursor.close()
        db.close()
    }
}
```

- [ ] **Step 6: Run the migration test on a connected device or emulator**

Run: `./gradlew connectedDebugAndroidTest --tests "com.sukashawarma.pos.data.local.MigrationTest"`
Expected: PASS. (Requires a running emulator/device — this is the one step in this plan that cannot be verified headlessly. If no device is available at implementation time, note that explicitly rather than claiming it passed.)

- [ ] **Step 7: Commit**

```bash
git add app/build.gradle.kts app/src/main/java/com/sukashawarma/pos/data/local/ app/src/androidTest/java/com/sukashawarma/pos/data/local/
git commit -m "feat: extend Room schema for menu cache + kiosk_settings, add explicit migration 2->3"
```

---

### Task 4: `MenuRealtimeManager` — realtime invalidation for `menu_items` / `kiosk_settings`

**Files:**
- Create: `app/src/main/java/com/sukashawarma/pos/data/remote/realtime/MenuRealtimeManager.kt`

**Interfaces:**
- Consumes: `SupabaseClient.BASE_URL`, `SessionTokenHolder.accessToken`, `BuildConfig.SUPABASE_ANON_KEY` (all already exist, same as `OrderRealtimeManager` uses them).
- Produces: `class MenuRealtimeManager(client: OkHttpClient, scope: CoroutineScope)` with `var onChange: ((table: String, eventType: String, record: JSONObject) -> Unit)?`, `fun connect()`, `fun disconnect()`.

- [ ] **Step 1: Write `MenuRealtimeManager.kt`**

Structurally mirrors `OrderRealtimeManager.kt`, subscribing to two tables on one channel instead of one table with an outlet filter (menu data isn't outlet-scoped at the row-visibility level — RLS/select already limits what's readable):

```kotlin
package com.sukashawarma.pos.data.remote.realtime

import com.sukashawarma.pos.BuildConfig
import com.sukashawarma.pos.data.remote.SessionTokenHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.Response as OkResponse
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

/**
 * Realtime channel for `menu_items` + `kiosk_settings`, independent from
 * `OrderRealtimeManager` — mirrors how the web app opens a separate
 * `supabase.channel(...)` per page (KasirMenuClient.tsx vs KasirOrderClient.tsx)
 * rather than sharing one subscription across screens.
 */
class MenuRealtimeManager(
    private val client: OkHttpClient,
    private val scope: CoroutineScope
) {
    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private val refCounter = AtomicInteger(1)
    private val topic = "realtime:public:menu"

    var onChange: ((table: String, eventType: String, record: JSONObject) -> Unit)? = null

    fun connect() {
        disconnect()
        val anonKey = BuildConfig.SUPABASE_ANON_KEY
        val wsUrl = com.sukashawarma.pos.data.remote.SupabaseClient.BASE_URL
            .replace("https://", "wss://") + "realtime/v1/websocket?apikey=$anonKey&vsn=1.0.0"

        val request = Request.Builder().url(wsUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: OkResponse) {
                joinChannel(webSocket)
                startHeartbeat(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: OkResponse?) {
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {}
        })
    }

    private fun joinChannel(ws: WebSocket) {
        val joinPayload = JSONObject().apply {
            put("topic", topic)
            put("event", "phx_join")
            put("payload", JSONObject().apply {
                put("config", JSONObject().apply {
                    put(
                        "postgres_changes",
                        JSONArray().apply {
                            put(JSONObject().apply {
                                put("event", "*"); put("schema", "public"); put("table", "menu_items")
                            })
                            put(JSONObject().apply {
                                put("event", "*"); put("schema", "public"); put("table", "kiosk_settings")
                            })
                        }
                    )
                })
                put("access_token", SessionTokenHolder.accessToken ?: BuildConfig.SUPABASE_ANON_KEY)
            })
            put("ref", refCounter.getAndIncrement().toString())
        }
        ws.send(joinPayload.toString())
    }

    private fun startHeartbeat(ws: WebSocket) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(25_000)
                val hb = JSONObject().apply {
                    put("topic", "phoenix")
                    put("event", "heartbeat")
                    put("payload", JSONObject())
                    put("ref", refCounter.getAndIncrement().toString())
                }
                ws.send(hb.toString())
            }
        }
    }

    private fun scheduleReconnect() {
        scope.launch {
            delay(5_000)
            connect()
        }
    }

    private fun handleMessage(text: String) {
        try {
            val json = JSONObject(text)
            if (json.optString("event") != "postgres_changes") return
            val payload = json.optJSONObject("payload") ?: return
            val data = payload.optJSONObject("data") ?: return
            val table = data.optString("table")
            val eventType = data.optString("type")
            val record = data.optJSONObject("record") ?: return
            onChange?.invoke(table, eventType, record)
        } catch (_: Exception) {
            // Malformed/unrelated frame (e.g. phx_reply) — ignore.
        }
    }

    fun disconnect() {
        heartbeatJob?.cancel()
        webSocket?.close(1000, "bye")
        webSocket = null
    }
}
```

- [ ] **Step 2: Verify the module compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (Nothing references this class yet — that's Task 5.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/sukashawarma/pos/data/remote/realtime/MenuRealtimeManager.kt
git commit -m "feat: add MenuRealtimeManager for menu_items/kiosk_settings postgres_changes"
```

---

### Task 5: `MenuRepository` — cache-first snapshot with network refresh and realtime invalidation

**Files:**
- Create: `app/src/main/java/com/sukashawarma/pos/data/repository/MenuRepository.kt`
- Test: `app/src/test/java/com/sukashawarma/pos/data/repository/MenuRepositoryTest.kt`

**Interfaces:**
- Consumes: `SupabaseApi` (Task 2: `getMenuItems`, `getCategories`, `getKioskSettings`), `MenuItemDao`/`KioskSettingDao` (Task 3), `MenuRealtimeManager` (Task 4), `MenuRules.{resolveSetting,filterByOutlet,PUSAT_OUTLET_ID}` (Task 1), `KioskSettings` (Task 1), `MenuItem`/`PackageItem`/`Category` (Task 1/existing).
- Produces: `data class MenuSnapshot(items: List<MenuItem>, categories: List<Category>, settings: KioskSettings, fromCache: Boolean)`; `class MenuRepository(api: SupabaseApi, menuItemDao: MenuItemDao, kioskSettingDao: KioskSettingDao, okHttpClient: OkHttpClient)` with `fun snapshot(outletId: String): Flow<MenuSnapshot>` and `suspend fun refresh(outletId: String)`. Task 6 (ViewModels) is the consumer of both.

- [ ] **Step 1: Write the failing test**

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.sukashawarma.pos.data.repository.MenuRepositoryTest"`
Expected: FAIL — `MenuRepository` doesn't exist yet.

- [ ] **Step 3: Write `MenuRepository.kt`**

```kotlin
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

private val SETTING_KEYS = listOf(
    "bestseller_ids", "upsell_ids", "recommendation_ids",
    "unavailable_menu_ids", "auto_unavailable_menu_ids", "force_available_menu_ids"
)

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
            val orFilter = "outlet_id.is.null,outlet_id.eq.$PUSAT_OUTLET_ID,outlet_id.eq.$outletId"
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.sukashawarma.pos.data.repository.MenuRepositoryTest"`
Expected: PASS, 2 tests green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/sukashawarma/pos/data/repository/ app/src/test/java/com/sukashawarma/pos/data/repository/
git commit -m "feat: add MenuRepository with cache-first snapshot and realtime invalidation"
```

---

### Task 6: Wire `MenuRepository` into both ViewModels

**Files:**
- Modify: `app/src/main/java/com/sukashawarma/pos/POSApplication.kt`
- Modify: `app/src/main/java/com/sukashawarma/pos/presentation/menu_management/MenuManagementViewModel.kt`
- Modify: `app/src/main/java/com/sukashawarma/pos/presentation/order_manual/POSManualOrderViewModel.kt`
- Modify: `app/src/main/java/com/sukashawarma/pos/presentation/MainActivity.kt`

**Interfaces:**
- Consumes: `MenuRepository` (Task 5), `MenuSnapshot` (Task 5).
- Produces: `POSApplication.menuRepository: MenuRepository`; `MenuManagementViewModel.setOutlet(outletId: String)` (new); every `StateFlow` both screens already read is preserved unchanged in name and type.

This task has no unit test of its own — it is glue code between already-tested layers (Task 1 rules, Task 5 repository) and the untouched screens. Verification is the manual QA in Step 5.

- [ ] **Step 1: Expose `menuRepository` from `POSApplication`**

Add to `POSApplication.kt`, after the `database` property:

```kotlin
    val menuRepository: com.sukashawarma.pos.data.repository.MenuRepository by lazy {
        com.sukashawarma.pos.data.repository.MenuRepository(
            api = com.sukashawarma.pos.data.remote.SupabaseClient.api,
            menuItemDao = database.menuItemDao(),
            kioskSettingDao = database.kioskSettingDao(),
            okHttpClient = com.sukashawarma.pos.data.remote.SupabaseClient.okHttpClient
        )
    }
```

(Fully-qualified names avoid adding four new imports to a very small file; feel free to add proper `import` lines instead if that matches the file's existing style better — check the top of `POSApplication.kt` and use plain imports there if it already imports similarly-deep packages.)

- [ ] **Step 2: Rewrite `MenuManagementViewModel.kt` to consume the repository**

Replace the full file:

```kotlin
package com.sukashawarma.pos.presentation.menu_management

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sukashawarma.pos.POSApplication
import com.sukashawarma.pos.data.remote.SupabaseClient
import com.sukashawarma.pos.domain.model.Category
import com.sukashawarma.pos.domain.model.MenuItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

/** Roles allowed to change master product data (harga/nama) — spec §3.A. */
private val PRICE_EDIT_ROLES = setOf("admin", "leader", "kepala_outlet", "spv", "spvkitchen")

@OptIn(ExperimentalCoroutinesApi::class)
class MenuManagementViewModel(application: Application) : AndroidViewModel(application) {
    private val api = SupabaseClient.api
    private val repository = (application as POSApplication).menuRepository

    private val currentOutletId = MutableStateFlow("")

    val categories = MutableStateFlow<List<Category>>(emptyList())
    val menuItems = MutableStateFlow<List<MenuItem>>(emptyList())
    val selectedCategoryId = MutableStateFlow("")
    val searchQuery = MutableStateFlow("")
    val isLoading = MutableStateFlow(false)
    val statusMessage = MutableStateFlow<String?>(null)
    val currentRole = MutableStateFlow("crew")

    val canEditPrice: Boolean
        get() = currentRole.value.lowercase() in PRICE_EDIT_ROLES

    init {
        viewModelScope.launch {
            currentOutletId.filter { it.isNotBlank() }
                .flatMapLatest { outletId -> repository.snapshot(outletId) }
                .collect { snapshot ->
                    categories.value = snapshot.categories
                    menuItems.value = snapshot.items
                    isLoading.value = false
                }
        }
    }

    fun setOutlet(outletId: String) {
        isLoading.value = outletId.isNotBlank() && categories.value.isEmpty()
        currentOutletId.value = outletId
    }

    fun setRole(role: String) {
        currentRole.value = role.lowercase()
    }

    fun clearStatusMessage() {
        statusMessage.value = null
    }

    fun toggleAvailability(item: MenuItem) {
        viewModelScope.launch {
            val newStatus = !item.isAvailable
            replaceLocally(item.id) { it.copy(isAvailable = newStatus) }

            try {
                val res = api.updateMenuItemAvailability("eq.${item.id}", mapOf("is_available" to newStatus))
                if (!res.isSuccessful) {
                    replaceLocally(item.id) { it.copy(isAvailable = item.isAvailable) }
                    statusMessage.value = "Gagal mengubah status stok di server (kode ${res.code()})."
                }
            } catch (e: Exception) {
                e.printStackTrace()
                replaceLocally(item.id) { it.copy(isAvailable = item.isAvailable) }
                statusMessage.value = "Gagal mengubah status stok: ${e.localizedMessage}"
            }
        }
    }

    fun updatePrice(item: MenuItem, newPrice: Double) {
        if (!canEditPrice) {
            statusMessage.value = "Hanya Admin/Leader yang boleh mengubah harga menu."
            return
        }
        if (newPrice < 0) {
            statusMessage.value = "Harga tidak valid."
            return
        }
        viewModelScope.launch {
            replaceLocally(item.id) { it.copy(price = newPrice) }
            try {
                val res = api.updateMenuItem("eq.${item.id}", mapOf("price" to newPrice))
                if (res.isSuccessful) {
                    statusMessage.value = "Harga ${item.name} diperbarui."
                } else {
                    replaceLocally(item.id) { it.copy(price = item.price) }
                    statusMessage.value = "Gagal menyimpan harga di server (kode ${res.code()})."
                }
            } catch (e: Exception) {
                e.printStackTrace()
                replaceLocally(item.id) { it.copy(price = item.price) }
                statusMessage.value = "Gagal menyimpan harga: ${e.localizedMessage}"
            }
        }
    }

    private fun replaceLocally(itemId: String, transform: (MenuItem) -> MenuItem) {
        val currentList = menuItems.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == itemId }
        if (index >= 0) {
            currentList[index] = transform(currentList[index])
            menuItems.value = currentList
        }
    }
}
```

`updatePrice`, `canEditPrice`, and `toggleAvailability` are untouched on purpose — spec §7 decision 3 removes the price-edit feature entirely in sub-project B, not here; touching it now would be scope creep into B's work.

- [ ] **Step 3: Rewrite the menu-loading part of `POSManualOrderViewModel.kt`**

Replace the `fetchRealDataFromSupabase()` function and its call site. First, in the `init` block, replace:

```kotlin
    init {
        fetchRealDataFromSupabase()
```

with:

```kotlin
    init {
        collectMenuSnapshot()
```

Then replace the entire `fetchRealDataFromSupabase()` function with a function of the new name, mirroring the collection pattern used in Task 6 Step 2's `MenuManagementViewModel`:

```kotlin
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun collectMenuSnapshot() {
        viewModelScope.launch {
            currentOutletId.filter { it.isNotBlank() }
                .flatMapLatest { outletId -> repository.snapshot(outletId) }
                .collect { snapshot ->
                    categories.value = snapshot.categories
                    if (snapshot.categories.isNotEmpty() && selectedCategoryId.value.isEmpty()) {
                        selectedCategoryId.value = snapshot.categories.first().id
                    }
                    menuItems.value = snapshot.items
                    isLoading.value = false
                }
        }
    }
```

Add the repository property near the top of the class, next to `private val api = SupabaseClient.api`:

```kotlin
    private val repository = (application as POSApplication).menuRepository
```

Add the two missing imports at the top of the file (the `@OptIn` above uses the fully-qualified annotation name, so `ExperimentalCoroutinesApi` itself doesn't need an import):

```kotlin
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
```

(`kotlinx.coroutines.flow.combine` and `stateIn` are already imported; `MutableStateFlow`/`StateFlow`/`launch` too.)

- [ ] **Step 4: Wire outlet ID into `MenuManagementViewModel` from `MainActivity`**

In `MainActivity.kt`, in the `onLoginSuccess` callback, add one line next to the existing `menuManagementViewModel.setRole(session.role)` (around line 90):

```kotlin
                            menuManagementViewModel.setRole(session.role)
                            menuManagementViewModel.setOutlet(session.outletId)
```

And in the logout handler, next to `posManualOrderViewModel.setOutlet("")` (around line 151):

```kotlin
                            posManualOrderViewModel.setOutlet("")
                            menuManagementViewModel.setOutlet("")
```

- [ ] **Step 5: Manual verification**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

Then, per the spec's Verifikasi section, install on a device/emulator and check by hand:
1. Log in with an outlet that has at least one outlet-owned menu item and confirm both **Pesanan Baru** and the Menu tabs (**Manajemen Menu**, **Info Porsi**, **Stok Outlet** — all three still render via `MenuManagementScreen`, unchanged UI) show the same item set, and that items belonging to a *different* outlet do not appear.
2. Turn off network after one successful load, relaunch/reopen the screen — the previously loaded items should still show (from Room), no crash, no blank screen.
3. In the Supabase dashboard, edit a `kiosk_settings` row (e.g. add an item to `unavailable_menu_ids` for the logged-in outlet) — confirm the app's `menuItems`/`categories` StateFlow updates without restarting the app (log via `Log.d` or a temporary breakpoint if there's no visible UI change yet — the UI doesn't consume `KioskSettings` until sub-project B, so this step only confirms the repository layer received the realtime event, not a visible effect).
4. Confirm existing behavior still works: toggling availability in Manajemen Menu, editing price (where role permits), placing a manual order end-to-end, offline order queueing.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/sukashawarma/pos/POSApplication.kt app/src/main/java/com/sukashawarma/pos/presentation/menu_management/MenuManagementViewModel.kt app/src/main/java/com/sukashawarma/pos/presentation/order_manual/POSManualOrderViewModel.kt app/src/main/java/com/sukashawarma/pos/presentation/MainActivity.kt
git commit -m "feat: wire MenuManagementViewModel and POSManualOrderViewModel to MenuRepository"
```
