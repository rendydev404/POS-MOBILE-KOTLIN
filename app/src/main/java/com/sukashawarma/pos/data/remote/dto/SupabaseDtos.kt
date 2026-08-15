package com.sukashawarma.pos.data.remote.dto

import com.google.gson.annotations.SerializedName

data class StaffProfileDto(
    val id: String,
    val username: String?,
    val name: String?,
    val role: String?,
    @SerializedName("outlet_id") val outletId: String?,
    @SerializedName("is_active") val isActive: Boolean?,
    @SerializedName("inactive_reason") val inactiveReason: String? = null
)

data class OutletDto(
    val id: String,
    val slug: String,
    val name: String,
    val address: String?,
    val phone: String?,
    val type: String?,
    val is_active: Boolean?,
    @SerializedName("inactive_reason") val inactiveReason: String? = null
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

/** Konten `system_guides` yang disajikan oleh API POS web untuk semua klien. */
data class SystemGuideDto(
    val id: String,
    val category: String,
    val title: String,
    @SerializedName("content_html") val contentHtml: String,
    @SerializedName("image_url") val imageUrl: String?,
    @SerializedName("sort_order") val sortOrder: Int?
)

data class KioskAccountDto(val id: String, val username: String?)
data class KioskAccountsResponse(val accounts: List<KioskAccountDto>?)
data class KioskQrResponse(@SerializedName("action_link") val actionLink: String?)

data class PromoDto(
    val id: String,
    @SerializedName("outlet_id") val outletId: String,
    @SerializedName("promo_name") val promoName: String?,
    val scope: String?,
    @SerializedName("menu_item_id") val menuItemId: String?,
    @SerializedName("discount_type") val discountType: String?,
    @SerializedName("discount_value") val discountValue: Double?,
    @SerializedName("min_purchase") val minPurchase: Double?,
    @SerializedName("is_active") val isActive: Boolean?,
    @SerializedName("usage_limit") val usageLimit: Int?,
    @SerializedName("current_usage") val currentUsage: Int?,
    @SerializedName("start_date") val startDate: String?,
    @SerializedName("end_date") val endDate: String?,
    @SerializedName("apply_to_food_apps") val applyToFoodApps: Boolean?
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
    @SerializedName("promo_subsidy") val promoSubsidy: Double?,
    @SerializedName("total_amount") val totalAmount: Double,
    @SerializedName("amount_received") val amountReceived: Double?,
    @SerializedName("change_amount") val changeAmount: Double?,
    @SerializedName("kitchen_receipt_printed") val kitchenReceiptPrinted: Boolean?,
    @SerializedName("customer_receipt_printed") val customerReceiptPrinted: Boolean?,
    @SerializedName("cancellation_status") val cancellationStatus: String?,
    @SerializedName("cancellation_user_name") val cancellationUserName: String?,
    @SerializedName("created_at") val createdAt: String,
    val channel: String?,
    @SerializedName("order_items") val orderItems: List<OrderItemDto>?,
    val notes: String? = null,
    @SerializedName("pickup_time") val pickupTime: String? = null,
    @SerializedName("release_time") val releaseTime: String? = null,
    @SerializedName("payment_proof_url") val paymentProofUrl: String? = null,
    @SerializedName("cashier_name") val cashierName: String? = null,
    @SerializedName("cancellation_reason") val cancellationReason: String? = null,
    @SerializedName("void_reason") val voidReason: String? = null,
    val isSyncedFromOffline: Boolean? = false
)

data class MenuItemRefDto(
    val id: String,
    val name: String
)

data class OrderItemDto(
    val id: String?,
    @SerializedName("order_id") val orderId: String?,
    @SerializedName("menu_item_id") val menuItemId: String?,
    @SerializedName("menu_item_name") val menuItemName: String?,
    @SerializedName("menu_item") val menuItem: MenuItemRefDto? = null,
    @SerializedName("or_menu_item") val orMenuItem: MenuItemRefDto? = null,
    val quantity: Int,
    @SerializedName("unit_price") val unitPrice: Double,
    val subtotal: Double
) {
    val resolvedName: String
        get() = menuItemName ?: menuItem?.name ?: orMenuItem?.name ?: "Unknown Item"

    /**
     * [resolvedName] tanpa delimiter penyisipan `|NOTE|`/`|PARENT|`/`|ID|` (lihat
     * OrderItem.encodedMenuItemName) — dipakai di mana pun nama item ditampilkan
     * atau diagregasi apa adanya (laporan produk terlaris, tabel histori transaksi),
     * supaya catatan/parent id tidak ikut nyampur jadi nama produk yang beda-beda.
     */
    val displayName: String
        get() = resolvedName.substringBefore("|NOTE|").substringBefore("|PARENT|").substringBefore("|ID|")
}

data class ShiftDto(
    val id: String,
    @SerializedName("outlet_id") val outletId: String,
    @SerializedName("staff_id") val staffId: String?,
    @SerializedName("start_time") val startTime: String?,
    @SerializedName("end_time") val endTime: String?,
    @SerializedName("starting_cash") val startingCash: Double,
    @SerializedName("starting_petty_cash") val startingPettyCash: Double?,
    @SerializedName("actual_ending_cash") val actualEndingCash: Double?,
    @SerializedName("expected_ending_cash") val expectedEndingCash: Double?,
    @SerializedName("actual_ending_petty_cash") val actualEndingPettyCash: Double?,
    @SerializedName("expected_ending_petty_cash") val expectedEndingPettyCash: Double?,
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
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("receipt_url") val receiptUrl: String?,
    @SerializedName("deleted_at") val deletedAt: String? = null,
    @SerializedName("delete_reason") val deleteReason: String? = null
)

// Mirrors the real `orders` table columns. order_number is assigned server-side by
// a BEFORE INSERT trigger (per-outlet, per-day counter) — never sent by the client,
// which avoids two devices computing a colliding "next number" themselves.
// There is no `subtotal` column on `orders` (only on `order_items`).
data class CreateOrderPayload(
    val id: String,
    @SerializedName("order_number") val orderNumber: Int? = null,
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
    val channel: String? = null,
    @SerializedName("pickup_time") val pickupTime: String? = null,
    @SerializedName("release_time") val releaseTime: String? = null,
    @SerializedName("cashier_name") val cashierName: String? = null
)

// ── RPC request/response payloads (rest/v1/rpc/*) ──────────────────────────

data class OpenShiftPayload(
    @SerializedName("p_outlet_id") val outletId: String,
    @SerializedName("p_starting_petty_cash") val startingPettyCash: Double
)

data class CloseShiftPayload(
    @SerializedName("p_shift_id") val shiftId: String,
    @SerializedName("p_actual_cash") val actualCash: Double,
    @SerializedName("p_actual_petty_cash") val actualPettyCash: Double?
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

/**
 * Argumen RPC `register_fcm_token`. `staff_id` sengaja tidak dikirim: fungsinya
 * mengambil paksa dari `auth.uid()`, supaya device tidak bisa mendaftarkan diri
 * atas nama staff lain.
 */
data class RegisterFcmTokenPayload(
    @SerializedName("p_token") val token: String,
    @SerializedName("p_outlet_id") val outletId: String?
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

data class IncrementPromoUsagePayload(
    @SerializedName("p_promo_id") val promoId: String,
    @SerializedName("p_increment_amount") val incrementAmount: Int = 1
)

data class VoidPettyCashPayload(
    @SerializedName("p_expense_id") val expenseId: String,
    @SerializedName("p_reason") val reason: String
)

data class PettyCashTopupDto(
    val id: String,
    @SerializedName("outlet_id") val outletId: String,
    val amount: Double,
    val status: String,
    @SerializedName("approval_token") val approvalToken: String?,
    @SerializedName("created_at") val createdAt: String
)

data class MonthlyBonusPayload(
    @SerializedName("p_month") val month: Int,
    @SerializedName("p_year") val year: Int,
    @SerializedName("p_outlet_id") val outletId: String
)

data class MonthlyBonusResultDto(
    @SerializedName("crew_name") val crewName: String,
    @SerializedName("role") val role: String,
    @SerializedName("outlet_name") val outletName: String,
    @SerializedName("days_target_reached") val daysTargetReached: Int,
    @SerializedName("total_bonus_received") val totalBonusReceived: Double
)

data class DailyBonusBreakdownDto(
    @SerializedName("order_date") val date: String? = "",
    @SerializedName("daily_sales") val dailySales: Double = 0.0,
    @SerializedName("target_amount") val targetAmount: Double = 0.0,
    @SerializedName("is_reached") val isReached: Boolean = false,
    @SerializedName("additional_items") val additionalItems: Int = 0,
    @SerializedName("per_item_bonus") val perItemBonus: Double = 0.0
) {
    val bonusAmount: Double
        get() = if (isReached) (additionalItems * perItemBonus) else 0.0
}

data class ReportOrderDto(
    val id: String,
    @SerializedName("outlet_id") val outletId: String,
    @SerializedName("order_number") val orderNumber: Int,
    @SerializedName("customer_name") val customerName: String?,
    val status: String,
    val source: String,
    @SerializedName("payment_method") val paymentMethod: String?,
    @SerializedName("discount_amount") val discountAmount: Double?,
    @SerializedName("promo_subsidy") val promoSubsidy: Double?,
    @SerializedName("total_amount") val totalAmount: Double,
    @SerializedName("amount_received") val amountReceived: Double?,
    @SerializedName("change_amount") val changeAmount: Double?,
    @SerializedName("kitchen_receipt_printed") val kitchenReceiptPrinted: Boolean?,
    @SerializedName("created_at") val createdAt: String,
    val channel: String?,
    @SerializedName("sales_source") val salesSource: String?,
    @SerializedName("order_items") val orderItems: List<OrderItemDto>?
)

data class OwnerMessageDto(
    val id: String,
    val kind: String,
    val title: String?,
    val body: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("expires_at") val expiresAt: String?
)

data class AttendanceDto(
    val id: String,
    @SerializedName("outlet_staff_id") val outletStaffId: String?,
    @SerializedName("outlet_id") val outletId: String,
    val type: String,
    @SerializedName("ts_server") val tsServer: String?
)

data class ChecklistItemDto(
    val id: String,
    @SerializedName("is_required") val isRequired: Boolean
)

data class ChecklistCategoryDto(
    val id: String,
    @SerializedName("checklist_items") val checklistItems: List<ChecklistItemDto>? = null
)

data class DailyChecklistTickDto(
    @SerializedName("item_id") val itemId: String,
    @SerializedName("record_id") val recordId: String
)

data class DailyChecklistRecordDto(
    val id: String,
    @SerializedName("outlet_id") val outletId: String,
    @SerializedName("staff_id") val staffId: String,
    @SerializedName("date") val date: String
)

data class BypassRequestDto(
    val id: String,
    @SerializedName("outlet_id") val outletId: String,
    @SerializedName("staff_id") val staffId: String? = null,
    @SerializedName("request_type") val requestType: String? = null,
    val status: String,
    val reason: String? = null,
    @SerializedName("created_at") val createdAt: String
)

data class CreateBypassRequestPayload(
    @SerializedName("outlet_id") val outletId: String,
    @SerializedName("staff_id") val staffId: String? = null,
    @SerializedName("request_type") val requestType: String? = null,
    @SerializedName("requested_by_name") val requestedByName: String? = null,
    val reason: String? = null,
    val status: String = "pending"
)

data class CreateCancellationRequestPayload(
    @SerializedName("order_id") val orderId: String,
    val reason: String,
    @SerializedName("expires_at") val expiresAt: String,
    @SerializedName("previous_order_status") val previousOrderStatus: String,
    @SerializedName("requested_by") val requestedBy: String,
    val token: String? = null
)

data class CancellationRequestResponse(
    val token: String
)

data class GlobalSettingDto(
    val key: String,
    val value: String?
)

/**
 * Row `global_settings` key='app_update' — dipakai AppUpdateManager untuk cek
 * versi APK terbaru. Distribusi app ini lewat WhatsApp (bukan Play Store),
 * jadi update dicek & dipasang sendiri dari dalam aplikasi. `value` di kolom
 * JSONB, jadi Gson bisa langsung memetakannya ke AppUpdateManifest.
 */
data class AppUpdateSettingDto(
    val key: String,
    val value: AppUpdateManifest?
)

data class AppUpdateManifest(
    @SerializedName("version_code") val versionCode: Int,
    @SerializedName("version_name") val versionName: String,
    @SerializedName("apk_url") val apkUrl: String,
    @SerializedName("apk_sha256") val apkSha256: String? = null,
    @SerializedName("apk_size_bytes") val apkSizeBytes: Long? = null,
    val delta: AppUpdateDelta? = null,
    val deltas: List<AppUpdateDelta> = emptyList(),
    val notes: String? = null,
    val mandatory: Boolean = false
)

fun AppUpdateManifest.deltaFor(baseVersionCode: Int): AppUpdateDelta? =
    deltas.firstOrNull { it.baseVersionCode == baseVersionCode }
        ?: delta?.takeIf { it.baseVersionCode == baseVersionCode }

data class AppUpdateDelta(
    @SerializedName("base_version_code") val baseVersionCode: Int,
    @SerializedName("patch_url") val patchUrl: String,
    @SerializedName("patch_sha256") val patchSha256: String,
    @SerializedName("patch_size_bytes") val patchSizeBytes: Long
)

data class NativeRuntimeSettingDto(
    val key: String,
    val value: NativeRuntimeConfig?
)

/**
 * Bagian UI/config yang boleh berubah realtime tanpa mengganti APK. Field baru
 * dapat ditambahkan bertahap; default menjaga kompatibilitas versi lama.
 */
data class NativeRuntimeConfig(
    @SerializedName("new_order_button_color") val newOrderButtonColor: String = "#E67E22",
    @SerializedName("new_order_button_label") val newOrderButtonLabel: String = "Pesanan Baru",
    @SerializedName("new_order_button_colors_by_version")
    val newOrderButtonColorsByVersion: Map<String, String> = mapOf(
        "6" to "#DC2626",
        "7" to "#16A34A",
        "8" to "#16A34A",
        "9" to "#16A34A",
        "10" to "#E67E22",
        "11" to "#E67E22"
    )
) {
    fun newOrderButtonColorFor(versionCode: Int): String =
        newOrderButtonColorsByVersion[versionCode.toString()] ?: newOrderButtonColor
}

data class CustomerLayoutDto(
    val paperWidth: Int = 58,
    val showLogo: Boolean = true,
    val headerText: String = "",
    val footerText: String = "Terima kasih & selamat menikmati!",
    val fontScale: String = "normal",
    val showCashier: Boolean = true,
    val showCustomer: Boolean = true,
    val showItemNotes: Boolean = true
)

data class KitchenLayoutDto(
    val paperWidth: Int = 58,
    val showLogo: Boolean = false,
    val headerText: String = "STRUK DAPUR",
    val fontScale: String = "normal",
    val showCustomer: Boolean = true
)

data class PrintLayoutDto(
    @SerializedName("struk_customer") val strukCustomer: CustomerLayoutDto? = null,
    @SerializedName("struk_dapur") val strukDapur: KitchenLayoutDto? = null
)

