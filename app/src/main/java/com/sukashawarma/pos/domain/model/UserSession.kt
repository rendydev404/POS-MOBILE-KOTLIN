package com.sukashawarma.pos.domain.model

data class UserSession(
    val staffId: String,
    val username: String,
    val role: String, // crew, kasir, spv, kepala_outlet, admin, kiosk
    val outletId: String,
    val outletName: String,
    val loggedInAt: Long = System.currentTimeMillis()
)
