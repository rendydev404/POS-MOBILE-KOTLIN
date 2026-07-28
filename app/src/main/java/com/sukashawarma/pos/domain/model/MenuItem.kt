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
