package com.sukashawarma.pos.domain.model

data class Category(
    val id: String,
    val name: String,
    val isAvailable: Boolean = true
)

data class MenuItem(
    val id: String,
    val categoryId: String,
    val name: String,
    val price: Double,
    val isAvailable: Boolean = true,
    val prepTimeMinutes: Int = 10,
    val imageUrl: String? = null
)
