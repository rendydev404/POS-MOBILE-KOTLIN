# Plan — Manajemen Menu Kasir: full parity with web `apps/pos-kasir`

## Context

The native screen `presentation/menu_management/` (MenuManagementScreen.kt +
MenuManagementViewModel.kt) is a stub: it lists items with a raw `is_available`
Switch and a category filter row. The web reference is:

- `C:\Users\Creator MPB\OneDrive\Desktop\New folder\DIGITALISASI-SS-PROJECT\apps\pos-kasir\app\kasir\menu\KasirMenuClient.tsx` (567 lines) — the whole page
- `…\app\kasir\menu\page.tsx` — SSR prefetch, same query + same `getSetting`/outlet-filter logic

The read side is already ported and shipped by the previous plan
(`2026-07-28-fondasi-menu-repository-plan.md`): `MenuRepository.snapshot()` does
the cache-first fetch, the `.or(outlet_id...)` kiosk_settings query, realtime on
`menu_items`/`kiosk_settings`, and `domain/menu/MenuRules.kt` holds
`resolveSetting`, `filterByOutlet`, `parseIdList`, `isItemAvailable`.

**What is missing is the write side and the whole UI/flow.** Gaps:

| Web behavior | Native today |
|---|---|
| Status shows `isAvail = is_available && !(manualUnav \|\| (autoUnav && !forceAvail))` | shows raw `item.isAvailable` |
| `toggleAvail` branches: own-outlet item → PATCH `menu_items`; otherwise → toggle id in `unavailable_menu_ids` | always PATCHes `menu_items` |
| Aksi dropdown: Rekomendasi / Ekstra / Best Seller / Paksa Aktif | absent |
| `guardOnline()` blocks all writes offline with a toast | absent |
| Toasts with exact Indonesian copy + `(Global)` suffix | absent |
| Search matches name **or category name** | name only |
| No category filter chips on web | native has chips |
| Table: Foto / Nama Menu (+ description) / Kategori / Harga / Status / Aksi | flat card rows |
| Globe badge on `outlet_id === null` items; `(Habis di Sistem)` / `(Dipaksa Aktif)` chips | absent |
| Empty states "Belum ada menu" vs "Menu tidak ditemukan" | none |

## Global Constraints

1. **The web file is the spec.** Flow, branch conditions, request payloads, and
   user-visible Indonesian copy must match `KasirMenuClient.tsx` exactly. When
   in doubt, cite the web line number in a comment.
2. **Reuse the existing domain layer.** `PUSAT_OUTLET_ID`, `resolveSetting`,
   `filterByOutlet`, `parseIdList`, `isItemAvailable` in
   `domain/menu/MenuRules.kt` already exist and are tested — call them, do not
   re-derive them. `MenuRepository.snapshot()` is the only read path.
3. **All writes go through `MenuRepository`.** The ViewModel must not touch
   `SupabaseApi` directly (today's ViewModel does — that is part of the fix).
4. **Mirror the web's conflict targets exactly** (human partner's ruling,
   2026-07-29). Web calls `supabase.from('kiosk_settings').upsert({...})`
   *without* `onConflict` for `unavailable_menu_ids` (KasirMenuClient.tsx:257)
   while the other four toggles pass `{ onConflict: 'outlet_id, key' }`
   (tsx:285, 310, 333, 356). Native reproduces both:
   `unavailable_menu_ids` posts with **no** `on_conflict` query param (PostgREST
   then resolves on the primary key, which is what supabase-js does by default),
   and the other four keep `on_conflict=outlet_id,key`. Comment the asymmetry
   with the web line numbers so nobody "fixes" it later.
5. **Every kiosk_settings write uses `outlet_id = outletId`** (the current
   outlet), never `null`, never PUSAT — same as web.
6. **After any successful write, refresh** — the port of web's
   `invalidateMenu()`.
7. **Colors:** the web page uses plain Tailwind utilities (gray/amber/emerald/
   red), so use the `Tw*` palette in `presentation/theme/Color.kt`, not the
   brand `Amber*`/`Slate*` aliases.
8. **Currency format** follows the codebase convention:
   `"Rp ${String.format("%,.0f", value)}"`.
9. Tests: JVM unit tests under `app/src/test/java/...`. Run
   `./gradlew testDebugUnitTest` and paste real output. Do not add a test that
   asserts nothing.
10. Do not change `POSTab` routing, `MainActivity`, or any other screen except
    where a signature you changed forces it.

---

## Task 1 — Carry `description` and `categoryName` into the domain model

The web row renders `item.description` under the name and
`item.categories.name` as the Kategori badge. Neither reaches the native UI
today: `MenuItem` has no `description`, and `MenuRepository.readCache()`
returns `categories = emptyList()`, so an offline snapshot cannot resolve a
category name at all.

**Files:** `domain/model/MenuItem.kt`, `data/remote/dto/SupabaseDtos.kt` (read
only — `description` and `categories` already exist on `MenuItemDto`),
`data/repository/MenuRepository.kt`, `data/local/entity/LocalMenuItemEntity.kt`,
`data/local/AppDatabase.kt`, `app/src/test/java/.../MenuItemDtoTest.kt`,
`app/src/androidTest/java/.../MigrationTest.kt`.

**Changes:**

1. `MenuItem`: add `val description: String? = null` and
   `val categoryName: String = ""`.
2. `MenuItemDto.toDomain()` in MenuRepository.kt: map
   `description = description`, `categoryName = categories?.name ?: ""`.
3. `LocalMenuItemEntity`: add `val description: String?`. It already has
   `categoryName`.
4. `MenuItem.toEntity(...)`: it currently takes a `categoryName: String`
   parameter resolved from a lookup map in `writeCache`. Drop the parameter —
   use `this.categoryName`. Delete the now-dead `categoryNameById` map in
   `writeCache`; `writeCache` keeps its `categories` parameter only if still
   used, otherwise remove it and update the call site.
5. `LocalMenuItemEntity.toDomain()`: map `description` and `categoryName`
   back.
6. `AppDatabase`: bump `version` to 4 and add
   ```kotlin
   val MIGRATION_3_4 = object : Migration(3, 4) {
       override fun migrate(db: SupportSQLiteDatabase) {
           db.execSQL("ALTER TABLE local_menu_items ADD COLUMN description TEXT")
       }
   }
   ```
   registered in `addMigrations(...)` alongside the existing two. Follow the
   existing comment style explaining why this is not a destructive migration
   (`sync_queue`/`local_orders` hold unsynced sales).

**Tests:**
- `MenuItemDtoTest`: extend the existing DTO→domain case so it asserts
  `description` and `categoryName` survive the mapping, including the
  `categories == null → ""` fallback.
- `MigrationTest`: add a 3→4 case in the same shape as the existing cases.
- A round-trip case (domain → entity → domain) asserting `description` and
  `categoryName` survive, wherever the existing repository/entity tests live.

---

## Task 2 — Write side: the five toggles on `MenuRepository`

Port `toggleAvail`, `toggleBestseller`, `toggleUpsell`, `toggleRecommendation`,
`toggleForceAvail` (KasirMenuClient.tsx:242-363). All five share one shape:
compute the new id set, upsert one `kiosk_settings` row, then refresh.

**Files:** `data/remote/SupabaseApi.kt`, `data/repository/MenuRepository.kt`,
`app/src/test/java/.../MenuRepositoryTest.kt`.

**`SupabaseApi` — add one endpoint** next to the existing
`upsertKioskSetting`, per Global Constraint 4:

```kotlin
// Same row, no `on_conflict` param — PostgREST resolves on the primary key.
// Mirrors the web's un-targeted `.upsert()` for unavailable_menu_ids only
// (KasirMenuClient.tsx:257); every other key uses upsertKioskSetting above.
@Headers("Prefer: resolution=merge-duplicates")
@POST("rest/v1/kiosk_settings")
suspend fun upsertKioskSettingOnPrimaryKey(
    @Body payload: UpsertKioskSettingPayload
): Response<Void>
```

**Add to `MenuRepository`:**

```kotlin
/** Port of toggleAvail (KasirMenuClient.tsx:242). Items owned by THIS outlet
 *  flip their own menu_items.is_available column; every other item (global or
 *  PUSAT) is toggled by adding/removing its id from this outlet's
 *  unavailable_menu_ids list. */
suspend fun toggleAvailability(
    item: MenuItem,
    outletId: String,
    unavailableIds: Set<String>
): Result<Unit>

/** Port of toggleBestseller/toggleUpsell/toggleRecommendation/toggleForceAvail
 *  (KasirMenuClient.tsx:271-363) — identical bodies, different key. */
suspend fun toggleSettingMembership(
    key: String,
    itemId: String,
    outletId: String,
    currentIds: Set<String>
): Result<Unit>
```

Behavior:
- `toggleAvailability`: if `item.outletId == outletId`, call
  `api.updateMenuItemAvailability("eq.${item.id}", mapOf("is_available" to !item.isAvailable))`.
  Otherwise build `newUnav = if (item.id in unavailableIds) unavailableIds - item.id else unavailableIds + item.id`
  and call **`api.upsertKioskSettingOnPrimaryKey(UpsertKioskSettingPayload(outletId, "unavailable_menu_ids", gson.toJson(newUnav.toList())))`**
  — the no-`on_conflict` variant, per Global Constraint 4.
- `toggleSettingMembership`: same add/remove, upsert `key` via
  `api.upsertKioskSetting` (which carries `on_conflict=outlet_id,key`).
- Both: non-2xx response or thrown exception → `Result.failure`. On success,
  call `refresh(outletId)` (port of `invalidateMenu()`) **before** returning
  `Result.success(Unit)`.
- Serialize the id list as a JSON string array with the file's existing `gson`.
- Valid keys are only `bestseller_ids`, `upsell_ids`, `recommendation_ids`,
  `force_available_menu_ids`, `unavailable_menu_ids`.

**Tests** (`MenuRepositoryTest` already has a fake `SupabaseApi` — extend it):
- own-outlet item → `updateMenuItemAvailability` called with the negated flag,
  and `upsertKioskSetting` **not** called
- global item (`outletId == null`) not currently unavailable → the
  **`upsertKioskSettingOnPrimaryKey`** variant is called (not
  `upsertKioskSetting`) with `key = "unavailable_menu_ids"`,
  `outlet_id = <this outlet>`, value JSON containing the id
- same item already in `unavailableIds` → upsert value JSON **without** the id
- `toggleSettingMembership("bestseller_ids", …)` adds then removes correctly,
  and routes to `upsertKioskSetting` (the `on_conflict=outlet_id,key` variant)
- API failure (non-2xx) → `Result.isFailure`, and no refresh side effect claimed

---

## Task 3 — ViewModel: state, guards, toasts

Rewrite `MenuManagementViewModel` to expose everything the screen needs and to
own the web's guard/toast behavior. Pure string/filter logic goes in
`domain/menu/` so it is unit-testable without Robolectric.

**Files:** `presentation/menu_management/MenuManagementViewModel.kt`,
new `domain/menu/MenuManagementCopy.kt`, new
`app/src/test/java/.../domain/menu/MenuManagementCopyTest.kt`.

**`domain/menu/MenuManagementCopy.kt` — pure functions, no Android imports:**

```kotlin
/** Port of the search predicate (KasirMenuClient.tsx:367-374): blank query
 *  matches everything; otherwise case-insensitive substring on the item name
 *  OR its category name. */
fun filterMenuForSearch(items: List<MenuItem>, query: String): List<MenuItem>
```

Plus one builder per toast, each returning the web's exact string. `isGlobal`
below means `outletId == PUSAT_OUTLET_ID` — the web appends `" (Global)"` only
then (KasirMenuClient.tsx:288, 313, 336):

| Function | Result |
|---|---|
| `availabilityToggleSuccess(name)` | `"Status menu $name berhasil diubah"` |
| `availabilityToggleError(name)` | `"Gagal mengubah status menu $name"` |
| `bestsellerToast(name, wasActive, isGlobal)` | active→`"$name dihapus dari Best Seller"` / inactive→`"$name ditandai sebagai Best Seller"`, each `+ " (Global)"` when `isGlobal` |
| `upsellToast(name, wasActive, isGlobal)` | `"$name dihapus dari Menu Ekstra"` / `"$name dijadikan Menu Ekstra"` (+ Global) |
| `recommendationToast(name, wasActive, isGlobal)` | `"$name dihapus dari Menu Rekomendasi"` / `"$name dijadikan Menu Rekomendasi"` (+ Global) |
| `forceAvailToast(name, wasActive)` | `"Batal Paksa Aktif untuk $name"` / `"$name Dipaksa Aktif"` — **no** Global suffix (web has none) |

Error copy, verbatim from the web: `"Gagal mengubah Best Seller"`,
`"Gagal mengubah Menu Ekstra"`, `"Gagal mengubah Menu Rekomendasi"`,
`"Gagal mengubah status Paksa Aktif"`, and the offline guard
`"Perubahan menu butuh internet"`.

**ViewModel:**

- Keep `setOutlet(outletId)` and the existing `snapshot()` collection, but store
  the whole `MenuSnapshot`: expose `menuItems`, `categories` (still collected;
  the UI no longer filters by it), `settings: StateFlow<KioskSettings>`,
  `isLoading`, `searchQuery`.
- `filteredItems: StateFlow<List<MenuItem>>` derived from `menuItems` +
  `searchQuery` via `filterMenuForSearch`.
- `isOnline: StateFlow<Boolean>` — register a
  `ConnectivityManager.registerDefaultNetworkCallback` in `init`, seed it with
  the same `NET_CAPABILITY_INTERNET` check `POSManualOrderViewModel.isNetworkAvailable()`
  uses (POSManualOrderViewModel.kt:434), and unregister in `onCleared()`.
- `toast: StateFlow<MenuToast?>` where
  `data class MenuToast(val isError: Boolean, val message: String)`. Setting a
  toast auto-clears it after **3500 ms** (web: KasirMenuClient.tsx:169), with
  the pending clear cancelled if a newer toast arrives.
- `openDropdownItemId: StateFlow<String?>` + `setOpenDropdown(id: String?)`.
- `guardOnline(): Boolean` — port of KasirMenuClient.tsx:175. When offline,
  emit the offline error toast and return false.
- Five handlers — `toggleAvailability(item)`, `toggleBestseller(item)`,
  `toggleUpsell(item)`, `toggleRecommendation(item)`, `toggleForceAvail(item)`.
  Each: return early if `outletId` is blank (web: `if (!outletId) return`),
  return early if `!guardOnline()`, call the matching repository method with the
  current `settings` set, then emit the success or error toast from the copy
  builders. Delete the old optimistic local mutation of `menuItems` — the
  repository refresh + realtime is the single source of truth, as on web.
- Remove `selectedCategoryId` entirely (web has no category filter).
- The ViewModel must no longer hold a `SupabaseClient.api` reference.

**Tests:** `MenuManagementCopyTest` covering `filterMenuForSearch` (blank query,
name match, category-name match, case-insensitivity, no match) and every toast
builder in both states, plus the Global-suffix on/off cases.

---

## Task 4 — Screen: rebuild to the web layout and flow

Rewrite `MenuManagementScreen.kt` as a port of the JSX in
KasirMenuClient.tsx:376-563. Compose equivalents, same information and same
interactions — not a pixel-identical HTML table, but the same columns, the same
labels, the same order, the same conditional chips.

**File:** `presentation/menu_management/MenuManagementScreen.kt`.

**Structure:**

1. **Header row** (tsx:380-401): title `"Manajemen Menu Kasir"` bold; subtitle
   `"${filteredItems.size} menu tampil"` in `TwGray400`; a search
   `OutlinedTextField` on the trailing edge with a leading `Icons.Default.Search`
   and placeholder `"Cari menu..."`, bound to `viewModel.searchQuery`. Remove
   the category `FilterChip` row.
2. **Loading** (tsx:405-410): four placeholder cards in a 2-column grid, each a
   `TwGray100` rounded box.
3. **Empty — no items at all** (tsx:411-417): centered circle with a
   `RestaurantMenu` icon and `"Belum ada menu"`.
4. **Empty — search matched nothing** (tsx:418-422): centered `Search` icon and
   `"Menu tidak ditemukan"`.
5. **Table**: a header `Row` on `TwGray50` with
   `Foto | Nama Menu | Kategori | Harga | Status | Aksi`, then a `LazyColumn`
   over `filteredItems` keyed by `item.id`, each row separated by a
   `TwGray100` divider.

**Per-row content**, computed exactly as tsx:439-445:

```kotlin
val isGlobal = item.outletId == null
val isManualUnav = item.id in settings.unavailableIds
val isAutoUnav = item.id in settings.autoUnavailableIds
val isForceAvail = item.id in settings.forceAvailableIds
val isAvail = isItemAvailable(item, settings)   // domain/menu/MenuRules.kt
val autoDisabled = isAutoUnav && !isForceAvail
```

- **Foto:** 48dp `RoundedCornerShape(12.dp)` `AsyncImage` on a `TwAmber50`
  background; fall back to a `RestaurantMenu` icon in `TwAmber100` when
  `imageUrl` is null/blank. When `isGlobal`, overlay a small `TwBlue500` badge
  with `Icons.Default.Public` at the top-start (web: Globe, tsx:455-459).
- **Nama Menu:** name bold `TwGray900`; `description` below in `TwGray400`,
  single line, ellipsized, only when non-blank.
- **Kategori:** `item.categoryName` in an amber badge
  (`TwAmber50` background, `TwAmber600` text, rounded), or `"—"` in `TwGray300`
  when blank.
- **Harga:** `"Rp ${String.format("%,.0f", item.price)}"`, bold, end-aligned.
- **Status:** a pill button — `isAvail` → `TwEmerald50`/`TwEmerald700` reading
  `"Tersedia"`; else `TwRed50`/`TwRed500` reading `"Habis"`. `onClick =
  viewModel::toggleAvailability`. Below it, when `autoDisabled`, the chip
  `"(Habis di Sistem)"` (`TwGray100`/`TwGray400`); when `isForceAvail`, the chip
  `"(Dipaksa Aktif)"` (`TwAmber50`/`TwAmber600`, `TwAmber100` border). Both can
  show, in that order, matching tsx:502-507.
- **Aksi:** an outlined `"Aksi"` button with a trailing `ArrowDropDown`, opening
  a `DropdownMenu` anchored to it, controlled by
  `viewModel.openDropdownItemId == item.id`. Items in this exact order
  (tsx:523-541):
  1. `"Jadikan Menu Rekomendasi"` → `toggleRecommendation`
  2. `"Jadikan Menu Ekstra"` → `toggleUpsell`
  3. `"Tandai Best Seller"` → `toggleBestseller`
  4. **only when `isAutoUnav`**, after a divider:
     `if (isForceAvail) "Batal Paksa Aktif" else "Paksa Aktif (Abaikan Sistem)"`
     → `toggleForceAvail`

  Each of the first three shows a trailing `Icons.Default.Check` in `TwAmber600`
  and bold `TwAmber600` label when the item is in that set; otherwise a medium
  `TwGray700` label. The force-active entry's label is always `TwAmber600`, bold
  only when `isForceAvail`. Selecting any entry closes the dropdown
  (`setOpenDropdown(null)`), as on web.

6. **Toast** (tsx:556-563): when `viewModel.toast` is non-null, a bottom-centre
   `Box`-aligned pill — `TwEmerald600` background for success, `TwRed600` for
   error, white bold text, with a leading `CheckCircle` / `Error` icon.

No new business logic in this file: availability, membership, and gating all
come from the ViewModel and `MenuRules`.

**Verification:** `./gradlew assembleDebug` and `./gradlew testDebugUnitTest`
must both pass; paste the real output.
