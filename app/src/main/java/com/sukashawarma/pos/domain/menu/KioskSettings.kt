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
