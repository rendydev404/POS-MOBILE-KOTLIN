package com.sukashawarma.pos.domain.model

/** Port of the web's `Mode` type (order-manual/page.tsx:25) — which tab of "Pesanan Baru" is active. */
enum class OrderMode {
    WALKIN,
    ONLINE,
    ENDORSE,
    WEBSITE
}

/** Port of `CHANNELS` (apps/pos-kasir/lib/channels.ts:10-16) — Food Apps channel options
 *  shown only in [OrderMode.ONLINE]. `website` is not offered as a button; it's forced
 *  automatically when [OrderMode.WEBSITE] is active. */
data class Channel(val id: String, val label: String)

val FOOD_APP_CHANNELS = listOf(
    Channel("gofood", "GoFood"),
    Channel("shopeefood", "ShopeeFood"),
    Channel("grabfood", "GrabFood"),
    Channel("tiktokgo", "TikTok Go")
)

/** The 5 channels that require the mandatory "Promo Apps" subsidy input (web: order-manual/page.tsx,
 *  referenced at lines 456, 474, 490, 578, 601, 1601, 1776). `tiktok` is included because the web
 *  treats it as a synonym of `tiktokgo` (channels.ts:18-25). */
val PROMO_SUBSIDY_CHANNELS = setOf("gofood", "grabfood", "shopeefood", "tiktok", "tiktokgo")

/** Normalizes `tiktok`/`tiktok_go` to `tiktokgo` — port of `getChannel()` (channels.ts:18-25). */
fun normalizeChannel(channel: String?): String? = when (channel?.lowercase()) {
    "tiktok", "tiktok_go" -> "tiktokgo"
    else -> channel?.lowercase()
}
