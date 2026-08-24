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

    // Bahan baku menipis/habis di outlet ini. Filter status dilakukan di server,
    // sama persis dengan POS web (lib/useStockAlerts.ts) supaya kasir dan papan
    // stok tidak pernah menampilkan daftar yang berbeda.
    @GET("rest/v1/monitoring_view_crew")
    suspend fun getStockAlerts(
        @Query("outlet_id") outletIdFilter: String,
        @Query("status") statusFilter: String = "in.(below,warning)",
        @Query("select") select: String =
            "bahan_baku_id,item_name,satuan,kategori,current_qty,threshold,status,projection_text",
        @Query("order") order: String = "status,item_name"
    ): Response<List<MonitoringViewCrewDto>>

    // Fetch the logged-in staff's own row (RLS: outlet_staff_read_self allows id = auth.uid())
    @GET("rest/v1/outlet_staff")
    suspend fun getStaffById(
        @Query("id") idFilter: String,
        @Query("select") select: String =
            "id,username,name,role,outlet_id,is_active,inactive_reason"
    ): Response<List<StaffProfileDto>>

    // Fetch Single Outlet by ID
    @GET("rest/v1/outlets")
    suspend fun getOutletById(
        @Query("id") idFilter: String,
        @Query("select") select: String =
            "id,slug,name,address,phone,type,is_active,inactive_reason"
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

    @GET("rest/v1/kiosk_settings")
    suspend fun getOutletKioskSetting(
        @Query("outlet_id") outletIdFilter: String,
        @Query("key") keyFilter: String,
        @Query("select") select: String = "key,value,outlet_id"
    ): Response<List<KioskSettingDto>>

    @GET("rest/v1/global_settings")
    suspend fun getGlobalSettings(
        @Query("key") keyFilter: String = "eq.print_layout",
        @Query("select") select: String = "key,value"
    ): Response<List<GlobalSettingDto>>

    // Manifest versi APK terbaru — distribusi app ini lewat WhatsApp (bukan Play
    // Store), jadi update dicek & dipasang sendiri dari dalam app. Lihat AppUpdateManager.
    @GET("rest/v1/global_settings")
    suspend fun getAppUpdateInfo(
        @Query("key") keyFilter: String = "eq.app_update",
        @Query("select") select: String = "key,value"
    ): Response<List<AppUpdateSettingDto>>

    @GET("rest/v1/global_settings")
    suspend fun getNativeRuntimeConfig(
        @Query("key") keyFilter: String = "eq.native_runtime_config",
        @Query("select") select: String = "key,value"
    ): Response<List<NativeRuntimeSettingDto>>

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
        @Query("select") select: String = "*,order_items(*,menu_item:menu_items(id,name))",
        @Query("order") order: String = "created_at.desc"
    ): Response<List<OrderDto>>

    // Jalur pembuatan pesanan yang dipakai app: order + seluruh barisnya masuk
    // dalam SATU transaksi database, jadi pesanan tidak pernah bisa terbaca
    // "setengah jadi" (harga ada, menu kosong). Aman dikirim ulang: fungsi ini
    // idempoten terhadap id order yang sama. Mengembalikan baris order final,
    // termasuk order_number hasil trigger — nomor yang dicetak di struk.
    @POST("rest/v1/rpc/create_order_with_items")
    suspend fun createOrderWithItems(
        @Body payload: CreateOrderWithItemsPayload
    ): Response<OrderDto>

    // Create Order Items (cart lines) for an already-created order.
    // Hanya untuk menambal pesanan LAMA yang barisnya terlanjur kosong; jalur
    // pembuatan pesanan baru memakai createOrderWithItems di atas.
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

    // Khusus perpindahan status dari UI. `return=representation` membedakan
    // "PATCH berhasil dan satu baris berubah" dari HTTP 204 yang mengubah 0 baris
    // (misalnya order lokal belum pernah sampai ke server).
    @PATCH("rest/v1/orders")
    @Headers("Prefer: return=representation")
    suspend fun updateOrderStatusReturning(
        @Query("id") orderIdFilter: String,
        @Body patch: Map<String, String>
    ): Response<List<OrderDto>>

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
        @Body patch: @JvmSuppressWildcards Map<String, Any>
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

    // Omzet diagregasi di database, bukan dengan menarik semua baris `orders` lalu
    // menjumlahkannya di klien — cara lama selalu terpotong limit baris PostgREST.
    @POST("rest/v1/rpc/pos_revenue_summary_guarded")
    suspend fun getRevenueSummary(@Body payload: RevenueSummaryPayload): Response<RevenueSummaryDto>

    // Dipanggil sekali per promo yang benar-benar dipakai order (bukan per item) —
    // lihat POSManualOrderViewModel.submitOrder. Row-locked di server, aman dari race.
    @POST("rest/v1/rpc/increment_promo_usage")
    suspend fun incrementPromoUsage(@Body payload: IncrementPromoUsagePayload): Response<Void>

    @POST("rest/v1/rpc/get_my_target_progress")
    suspend fun getMyTargetProgress(@Body payload: Map<String, String> = emptyMap()): Response<List<TargetProgressDto>>

    // Mendaftarkan device ini ke akun yang sedang login. Lewat RPC, bukan upsert
    // langsung ke tabel: satu device = satu baris, dan login berikutnya mengambil
    // alih baris itu supaya notifikasi tidak lagi menyasar akun yang sudah logout
    // (migrasi 20260808_fcm_tokens_one_row_per_device.sql).
    @POST("rest/v1/rpc/register_fcm_token")
    suspend fun registerFcmToken(@Body payload: RegisterFcmTokenPayload): Response<Void>

    // Dipanggil sebelum token sesi dibersihkan saat logout. Policy DELETE
    // membatasi baris ke auth.uid() yang sedang aktif.
    @DELETE("rest/v1/fcm_tokens")
    suspend fun deleteFcmToken(@Query("token") tokenFilter: String): Response<Void>

    @GET("rest/v1/petty_cash_topups")
    suspend fun getPettyCashTopups(
        @QueryMap filters: Map<String, String>,
        @Query("select") select: String = "*",
        @Query("order") order: String = "created_at.desc"
    ): Response<List<PettyCashTopupDto>>

    @POST("rest/v1/rpc/void_petty_cash_expense")
    suspend fun voidPettyCashExpense(@Body payload: VoidPettyCashPayload): Response<Void>

    @POST("rest/v1/rpc/calculate_monthly_crew_bonus")
    suspend fun calculateMonthlyCrewBonus(@Body payload: MonthlyBonusPayload): Response<List<MonthlyBonusResultDto>>

    @POST("rest/v1/rpc/get_daily_bonus_breakdown")
    suspend fun getDailyBonusBreakdown(@Body payload: MonthlyBonusPayload): Response<List<DailyBonusBreakdownDto>>

    @POST("rest/v1/rpc/get_my_active_messages")
    suspend fun getMyActiveMessages(@Body payload: Map<String, String> = emptyMap()): Response<List<OwnerMessageDto>>

    // --- Gate kasir. Semua filter eksplisit; tidak ada QueryMap agar tidak
    // ada parameter select/order yang terkirim dua kali. ---

    @GET("rest/v1/attendance")
    suspend fun getAttendanceForDay(
        @Query("outlet_id") outletId: String,      // "eq.<uuid>"
        @Query("ts_server") from: String,          // "gte.<iso8601>"
        @Query("ts_server") to: String,            // "lte.<iso8601>"
        @Query("select") select: String = "outlet_staff_id,type,ts_server",
        @Query("order") order: String = "ts_server.asc"
    ): Response<List<AttendanceDto>>

    @GET("rest/v1/bypass_requests")
    suspend fun getBypassRequestsForDay(
        @Query("outlet_id") outletId: String,      // "eq.<uuid>"
        @Query("created_at") from: String,         // "gte.<iso8601>"
        @Query("select") select: String = "id,outlet_id,staff_id,request_type,status,reason,created_at",
        @Query("order") order: String = "created_at.desc"
    ): Response<List<BypassRequestDto>>

    @GET("rest/v1/checklist_categories")
    suspend fun getRequiredOpeningChecklist(
        @Query("outlet_id") outletId: String,      // "eq.<uuid>"
        @Query("phase") phase: String = "eq.buka",
        @Query("select") select: String = "id,checklist_items(id,is_required)"
    ): Response<List<ChecklistCategoryDto>>

    @GET("rest/v1/daily_checklist_records")
    suspend fun getChecklistRecordForDay(
        @Query("outlet_id") outletId: String,      // "eq.<uuid>"
        @Query("date") date: String,               // "eq.YYYY-MM-DD"
        @Query("select") select: String = "id,outlet_id,staff_id,date",
        @Query("limit") limit: String = "1"
    ): Response<List<DailyChecklistRecordDto>>

    @GET("rest/v1/daily_checklist_ticks")
    suspend fun getTicksForRecord(
        @Query("record_id") recordId: String,      // "eq.<uuid>"
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
    
    // system_guides — SELECT RLS sudah public (USING (true)), dikonfirmasi lewat
    // query langsung 2026-08-15; tidak perlu proxy lewat pos-kasir sama sekali.
    @GET("rest/v1/system_guides")
    suspend fun getSystemGuides(
        @Query("system_code") systemCode: String = "eq.pos",
        @Query("select") select: String = "id,category,title,content_html,image_url,sort_order",
        @Query("order") order: String = "category.asc,sort_order.asc"
    ): Response<List<SystemGuideDto>>

    @GET
    suspend fun getKioskAccounts(@Url url: String): Response<KioskAccountsResponse>

    @POST
    suspend fun generateKioskQr(
        @Url url: String,
        @Body payload: Map<String, String>
    ): Response<KioskQrResponse>

    @POST
    suspend fun logoutKiosk(
        @Url url: String,
        @Body payload: Map<String, String>
    ): Response<ResponseBody>

    @POST
    suspend fun pullOnlineOrder(
        @Url url: String,
        @Header("Authorization") auth: String,
        @Body payload: Map<String, String>
    ): Response<ResponseBody>

    @POST
    suspend fun syncActiveOrders(
        @Url url: String,
        @Header("Authorization") auth: String
    ): Response<ResponseBody>

    // Dipanggil langsung oleh native saat order website online ditandai Selesai,
    // dengan pos_order_id dan access token sesi kasir sendiri — Edge Function
    // kasir-order-done di project Order-Online yang me-resolve external_order_id
    // dan mengirim WA "pesanan siap diambil" ke customer.
    @POST
    suspend fun notifyOnlineOrderDone(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Body payload: Map<String, String>
    ): Response<ResponseBody>

    @POST
    suspend fun parseReceipt(
        @Url url: String,
        @Body payload: ParseReceiptPayload
    ): Response<ParseReceiptResponse>
}
