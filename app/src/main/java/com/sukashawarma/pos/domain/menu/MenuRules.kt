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
        if (raw.contains(",")) {
            raw.split(",").map { it.trim().replace("\"", "").replace("[", "").replace("]", "") }
        } else {
            listOf(raw.trim().replace("\"", "").replace("[", "").replace("]", ""))
        }
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
