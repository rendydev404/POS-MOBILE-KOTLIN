package com.sukashawarma.pos.data.remote

import com.sukashawarma.pos.data.remote.dto.*
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface SupabaseApi {

    @GET("rest/v1/monitoring_view_crew")
    suspend fun getMonitoringViewCrew(
        @Query("outlet_id") outletIdFilter: String,
        @Query("select") select: String = "item_name,projection_text"
    ): Response<List<MonitoringViewCrewDto>>

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

    // Fetch Menu Items — select mirrors the union of what KasirMenuClient.tsx and
    // order-manual/page.tsx request, so one call serves both Manajemen Menu and Pesanan Baru.
    @GET("rest/v1/menu_items")
    suspend fun getMenuItems(
        @Query("select") select: String =
            "*, categories(id,name,sort_order), package_items:menu_packages!package_id(id, menu_item_id, or_menu_item_id, quantity)",
        @Query("order") order: String = "sort_order.asc"
    ): Response<List<MenuItemDto>>

    // Update Menu Item Availability
    @PATCH("rest/v1/menu_items")
    suspend fun updateMenuItemAvailability(
        @Query("id") itemIdFilter: String,
        @Body patch: Map<String, Boolean>
    ): Response<Void>

    // kiosk_settings rows relevant to one outlet: its own, PUSAT's, and the global
    // (outlet_id IS NULL) row — mirrors the `.or(...)` filter both web pages use.
    @GET("rest/v1/kiosk_settings")
    suspend fun getKioskSettings(
        @Query("or") orFilter: String,
        @Query("key") keyFilter: String =
            "in.(bestseller_ids,upsell_ids,unavailable_menu_ids,auto_unavailable_menu_ids,force_available_menu_ids,recommendation_ids)",
        @Query("select") select: String = "key,value,outlet_id"
    ): Response<List<KioskSettingDto>>

    @GET("rest/v1/global_settings")
    suspend fun getGlobalSettings(
        @Query("key") keyFilter: String = "eq.print_layout",
        @Query("select") select: String = "key,value"
    ): Response<List<GlobalSettingDto>>

    // Upsert one kiosk_settings row (bestseller/upsell/recommendation/unavailable/etc. list for
    // one outlet+key). Contract only in sub-project A — B is the first caller.
    @Headers("Prefer: resolution=merge-duplicates")
    @POST("rest/v1/kiosk_settings")
    suspend fun upsertKioskSetting(
        @Query("on_conflict") onConflict: String = "outlet_id,key",
        @Body payload: UpsertKioskSettingPayload
    ): Response<Void>

    // Same row, no `on_conflict` param — PostgREST resolves on the primary key.
    // Mirrors the web's un-targeted `.upsert()` for unavailable_menu_ids only
    // (KasirMenuClient.tsx:257); every other key uses upsertKioskSetting above.
    @Headers("Prefer: resolution=merge-duplicates")
    @POST("rest/v1/kiosk_settings")
    suspend fun upsertKioskSettingOnPrimaryKey(
        @Body payload: UpsertKioskSettingPayload
    ): Response<Void>

    // Fetch Active Promos for Outlet
    @GET("rest/v1/outlet_promos")
    suspend fun getPromos(
        @Query("outlet_id") outletIdFilter: String,
        @Query("is_active") isActiveFilter: String = "eq.true",
        @Query("select") select: String = "*"
    ): Response<List<PromoDto>>

    // Fetch Orders with Items for Outlet (with flexible filters like date ranges)
    @GET("rest/v1/orders")
    suspend fun getOrders(
        @QueryMap filters: Map<String, String>,
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

    // Update Order Status (also reused to PATCH payment_proof_url — see
    // uploadPaymentProof/updateOrderPaymentProof below — both are the same generic
    // "patch a couple of orders columns by id" shape, so one endpoint covers both).
    @PATCH("rest/v1/orders")
    suspend fun updateOrderStatus(
        @Query("id") orderIdFilter: String,
        @Body patch: Map<String, String>
    ): Response<Void>

    // Upload a QRIS payment-proof photo to Supabase Storage — port of the web's upload
    // step in handleWalkInPay (order-manual/page.tsx:684-710). `objectPath` is
    // "<bucket>/<filename>"; @Path(encoded=true) so the "/" between them isn't escaped.
    @PUT("storage/v1/object/{objectPath}")
    suspend fun uploadPaymentProof(
        @Path(value = "objectPath", encoded = true) objectPath: String,
        @Header("Content-Type") contentType: String,
        @Header("x-upsert") upsert: String = "true",
        @Body file: RequestBody
    ): Response<ResponseBody>

    // Fetch Real Shifts from Supabase (with flexible filters)
    @GET("rest/v1/shifts")
    suspend fun getShifts(
        @QueryMap filters: Map<String, String>,
        @Query("select") select: String = "*",
        @Query("order") order: String = "start_time.desc"
    ): Response<List<ShiftDto>>

    // Update Shift (e.g. for closing shift natively via PATCH)
    @PATCH("rest/v1/shifts")
    suspend fun updateShift(
        @Query("id") shiftIdFilter: String,
        @Body patch: Map<String, Any>
    ): Response<Void>

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

    @GET("rest/v1/petty_cash_topups")
    suspend fun getPettyCashTopups(
        @QueryMap filters: Map<String, String>,
        @Query("select") select: String = "*",
        @Query("order") order: String = "created_at.desc"
    ): Response<List<PettyCashTopupDto>>

    @POST("rest/v1/rpc/void_petty_cash_expense")
    suspend fun voidPettyCashExpense(@Body payload: VoidPettyCashPayload): Response<Void>

    @POST("rest/v1/rpc/crew_receive_funds")
    suspend fun crewReceiveFunds(@Body payload: ReceiveFundsPayload): Response<Void>

    @POST("rest/v1/rpc/calculate_monthly_crew_bonus")
    suspend fun calculateMonthlyCrewBonus(@Body payload: MonthlyBonusPayload): Response<List<MonthlyBonusResultDto>>

    @POST("rest/v1/rpc/get_daily_bonus_breakdown")
    suspend fun getDailyBonusBreakdown(@Body payload: MonthlyBonusPayload): Response<List<DailyBonusBreakdownDto>>

    @POST("rest/v1/rpc/get_my_active_messages")
    suspend fun getMyActiveMessages(@Body payload: Map<String, String> = emptyMap()): Response<List<OwnerMessageDto>>

    @GET("rest/v1/attendance")
    suspend fun getAttendance(
        @QueryMap filters: Map<String, String>,
        @Query("select") select: String = "*",
        @Query("order") order: String = "created_at.desc"
    ): Response<List<AttendanceDto>>

    @GET("rest/v1/daily_checklist_records")
    suspend fun getDailyChecklistRecords(
        @QueryMap filters: Map<String, String>,
        @Query("select") select: String = "*"
    ): Response<List<DailyChecklistRecordDto>>

    @GET("rest/v1/checklist_categories")
    suspend fun getChecklistCategories(
        @QueryMap filters: Map<String, String>,
        @Query("select") select: String = "id,checklist_items(id,is_required)",
        @Query("phase") phase: String = "eq.buka"
    ): Response<List<ChecklistCategoryDto>>

    @GET("rest/v1/daily_checklist_ticks")
    suspend fun getDailyChecklistTicks(
        @QueryMap filters: Map<String, String>,
        @Query("select") select: String = "item_id,record_id"
    ): Response<List<DailyChecklistTickDto>>

    @POST("rest/v1/bypass_requests")
    @Headers("Prefer: return=representation")
    suspend fun createBypassRequest(
        @Body payload: CreateBypassRequestPayload
    ): Response<List<BypassRequestDto>>
    
    @POST("rest/v1/cancellation_requests")
    @Headers("Prefer: return=representation")
    suspend fun createCancellationRequest(
        @Body payload: CreateCancellationRequestPayload
    ): Response<List<CancellationRequestResponse>>
    
    @GET("rest/v1/bypass_requests")
    suspend fun getBypassRequests(
        @QueryMap filters: Map<String, String>,
        @Query("select") select: String = "*",
        @Query("order") order: String = "created_at.desc"
    ): Response<List<BypassRequestDto>>

    // Web POS API Endpoints (https://pos.sukashawarma.com)
    @POST
    suspend fun pullOnlineOrder(
        @Url url: String,
        @Body payload: Map<String, String>
    ): Response<ResponseBody>
    
    @POST
    suspend fun syncActiveOrders(
        @Url url: String
    ): Response<ResponseBody>

    @POST
    suspend fun parseReceipt(
        @Url url: String,
        @Body payload: ParseReceiptPayload
    ): Response<ParseReceiptResponse>
}
