package com.sukashawarma.pos.data.remote.dto

import com.google.gson.annotations.SerializedName

data class StaffProfileDto(
    val id: String,
    val username: String?,
    val name: String?,
    val role: String?,
    @SerializedName("outlet_id") val outletId: String?,
    @SerializedName("is_active") val isActive: Boolean?
)

data class OutletDto(
    val id: String,
    val slug: String,
    val name: String,
    val address: String?,
    val phone: String?,
    val type: String?,
    val is_active: Boolean?
)

data class CategoryDto(
    val id: String,
    val name: String,
    val sort_order: Int?
)

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

data class PromoDto(
    val id: String,
    @SerializedName("outlet_id") val outletId: String,
    val scope: String?,
    @SerializedName("discount_type") val discountType: String?,
    @SerializedName("discount_value") val discountValue: Double?,
    @SerializedName("is_active") val isActive: Boolean?
)

// Mirrors the real `orders` table columns (see supabase/migrations) — it has no
// `subtotal` column; subtotal is derived client-side from order_items when needed.
data class OrderDto(
    val id: String,
    @SerializedName("outlet_id") val outletId: String,
    @SerializedName("order_number") val orderNumber: Int,
    @SerializedName("customer_name") val customerName: String?,
    val status: String,
    val source: String,
    @SerializedName("payment_method") val paymentMethod: String?,
    @SerializedName("discount_amount") val discountAmount: Double?,
    @SerializedName("total_amount") val totalAmount: Double,
    @SerializedName("amount_received") val amountReceived: Double?,
    @SerializedName("change_amount") val changeAmount: Double?,
    @SerializedName("kitchen_receipt_printed") val kitchenReceiptPrinted: Boolean?,
    @SerializedName("created_at") val createdAt: String,
    val channel: String?,
    @SerializedName("order_items") val orderItems: List<OrderItemDto>?
)

data class OrderItemDto(
    val id: String?,
    @SerializedName("order_id") val orderId: String?,
    @SerializedName("menu_item_id") val menuItemId: String,
    @SerializedName("menu_item_name") val menuItemName: String,
    val quantity: Int,
    @SerializedName("unit_price") val unitPrice: Double,
    val subtotal: Double
)

data class ShiftDto(
    val id: String,
    @SerializedName("outlet_id") val outletId: String,
    @SerializedName("staff_id") val staffId: String?,
    @SerializedName("start_time") val startTime: String?,
    @SerializedName("end_time") val endTime: String?,
    @SerializedName("starting_cash") val startingCash: Double,
    @SerializedName("actual_ending_cash") val actualEndingCash: Double?,
    @SerializedName("expected_ending_cash") val expectedEndingCash: Double?,
    val variance: Double?,
    val status: String
)

data class PettyCashExpenseDto(
    val id: String,
    @SerializedName("outlet_id") val outletId: String,
    val category: String?,
    val amount: Double,
    val description: String?,
    @SerializedName("expense_date") val expenseDate: String?,
    @SerializedName("receipt_url") val receiptUrl: String?
)

// Mirrors the real `orders` table columns. order_number is assigned server-side by
// a BEFORE INSERT trigger (per-outlet, per-day counter) — never sent by the client,
// which avoids two devices computing a colliding "next number" themselves.
// There is no `subtotal` column on `orders` (only on `order_items`).
data class CreateOrderPayload(
    val id: String,
    @SerializedName("outlet_id") val outletId: String,
    @SerializedName("customer_name") val customerName: String,
    val status: String,
    val source: String,
    @SerializedName("payment_method") val paymentMethod: String,
    @SerializedName("discount_amount") val discountAmount: Double,
    @SerializedName("total_amount") val totalAmount: Double,
    @SerializedName("amount_received") val amountReceived: Double,
    @SerializedName("change_amount") val changeAmount: Double,
    @SerializedName("created_at") val createdAt: String,
    // Food Apps/Website channel this order came in on (null for walk-in/endorse) —
    // column already read back via OrderDto.channel, so writing it is safe.
    val channel: String? = null
)

// ── RPC request/response payloads (rest/v1/rpc/*) ──────────────────────────

data class OpenShiftPayload(
    @SerializedName("p_outlet_id") val outletId: String,
    @SerializedName("p_starting_cash") val startingCash: Double
)

data class CloseShiftPayload(
    @SerializedName("p_shift_id") val shiftId: String,
    @SerializedName("p_actual_cash") val actualCash: Double
)

data class ShiftIdPayload(
    @SerializedName("p_shift_id") val shiftId: String
)

data class OutletIdPayload(
    @SerializedName("p_outlet_id") val outletId: String
)

data class AddPettyCashPayload(
    @SerializedName("p_category") val category: String,
    @SerializedName("p_amount") val amount: Double,
    @SerializedName("p_description") val description: String,
    @SerializedName("p_receipt_url") val receiptUrl: String? = null
)

data class UpsertFcmTokenPayload(
    @SerializedName("staff_id") val staffId: String,
    @SerializedName("outlet_id") val outletId: String?,
    val token: String,
    val platform: String = "android"
)

data class TargetProgressDto(
    @SerializedName("outlet_id") val outletId: String,
    @SerializedName("outlet_name") val outletName: String,
    @SerializedName("target_amount") val targetAmount: Double,
    @SerializedName("omzet_today") val omzetToday: Double
)

data class CreateOrderItemPayload(
    @SerializedName("order_id") val orderId: String,
    @SerializedName("menu_item_id") val menuItemId: String,
    @SerializedName("menu_item_name") val menuItemName: String,
    val quantity: Int,
    @SerializedName("unit_price") val unitPrice: Double,
    val subtotal: Double
)
