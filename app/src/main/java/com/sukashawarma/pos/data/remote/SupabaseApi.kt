package com.sukashawarma.pos.data.remote

import com.sukashawarma.pos.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface SupabaseApi {

    // Fetch the logged-in staff's own row (RLS: outlet_staff_read_self allows id = auth.uid())
    @GET("rest/v1/outlet_staff")
    suspend fun getStaffById(
        @Query("id") idFilter: String,
        @Query("select") select: String = "*"
    ): Response<List<StaffProfileDto>>

    // Fetch Single Outlet by ID
    @GET("rest/v1/outlets")
    suspend fun getOutletById(
        @Query("id") idFilter: String,
        @Query("select") select: String = "*"
    ): Response<List<OutletDto>>

    // Fetch Outlets
    @GET("rest/v1/outlets")
    suspend fun getOutlets(
        @Query("select") select: String = "*",
        @Query("order") order: String = "name.asc"
    ): Response<List<OutletDto>>

    // Fetch Categories
    @GET("rest/v1/categories")
    suspend fun getCategories(
        @Query("select") select: String = "*",
        @Query("order") order: String = "sort_order.asc"
    ): Response<List<CategoryDto>>

    // Fetch Menu Items
    @GET("rest/v1/menu_items")
    suspend fun getMenuItems(
        @Query("select") select: String = "*",
        @Query("order") order: String = "sort_order.asc"
    ): Response<List<MenuItemDto>>

    // Update Menu Item Availability
    @PATCH("rest/v1/menu_items")
    suspend fun updateMenuItemAvailability(
        @Query("id") itemIdFilter: String,
        @Body patch: Map<String, Boolean>
    ): Response<Void>

    // Fetch Active Promos for Outlet
    @GET("rest/v1/outlet_promos")
    suspend fun getPromos(
        @Query("outlet_id") outletIdFilter: String,
        @Query("is_active") isActiveFilter: String = "eq.true",
        @Query("select") select: String = "*"
    ): Response<List<PromoDto>>

    // Fetch Orders with Items for Outlet
    @GET("rest/v1/orders")
    suspend fun getOrders(
        @Query("outlet_id") outletIdFilter: String,
        @Query("select") select: String = "*,order_items(*)",
        @Query("order") order: String = "created_at.desc"
    ): Response<List<OrderDto>>

    // Create New Order
    @POST("rest/v1/orders")
    @Headers("Prefer: return=representation")
    suspend fun createOrder(
        @Body payload: CreateOrderPayload
    ): Response<List<OrderDto>>

    // Create Order Items (cart lines) for an already-created order
    @POST("rest/v1/order_items")
    @Headers("Prefer: return=representation")
    suspend fun createOrderItems(
        @Body payload: List<CreateOrderItemPayload>
    ): Response<List<OrderItemDto>>

    // Update Order Status
    @PATCH("rest/v1/orders")
    suspend fun updateOrderStatus(
        @Query("id") orderIdFilter: String,
        @Body patch: Map<String, String>
    ): Response<Void>

    // Fetch Real Shifts from Supabase
    @GET("rest/v1/shifts")
    suspend fun getShifts(
        @Query("outlet_id") outletIdFilter: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = "start_time.desc"
    ): Response<List<ShiftDto>>

    // Fetch Real Petty Cash Expenses from Supabase
    @GET("rest/v1/petty_cash_expenses")
    suspend fun getPettyCashExpenses(
        @Query("outlet_id") outletIdFilter: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = "created_at.desc"
    ): Response<List<PettyCashExpenseDto>>

    // ── RPCs (server-side business logic — see supabase/migrations) ──────────

    @POST("rest/v1/rpc/open_shift")
    suspend fun openShift(@Body payload: OpenShiftPayload): Response<String>

    @POST("rest/v1/rpc/close_shift_blind")
    suspend fun closeShiftBlind(@Body payload: CloseShiftPayload): Response<Void>

    @POST("rest/v1/rpc/get_expected_shift_cash")
    suspend fun getExpectedShiftCash(@Body payload: ShiftIdPayload): Response<Double>

    @POST("rest/v1/rpc/add_petty_cash")
    suspend fun addPettyCash(@Body payload: AddPettyCashPayload): Response<PettyCashExpenseDto>

    @POST("rest/v1/rpc/get_petty_cash_balance")
    suspend fun getPettyCashBalance(@Body payload: OutletIdPayload): Response<Double>

    @POST("rest/v1/rpc/get_my_target_progress")
    suspend fun getMyTargetProgress(@Body payload: Map<String, String> = emptyMap()): Response<List<TargetProgressDto>>

    // Registers/refreshes this device's FCM token (see fcm_tokens migration).
    @Headers("Prefer: resolution=merge-duplicates")
    @POST("rest/v1/fcm_tokens")
    suspend fun upsertFcmToken(
        @Query("on_conflict") onConflict: String = "staff_id,token",
        @Body payload: UpsertFcmTokenPayload
    ): Response<Void>
}
