package com.sukashawarma.pos.presentation.shift

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sukashawarma.pos.POSApplication
import com.sukashawarma.pos.data.remote.SupabaseClient
import com.sukashawarma.pos.data.remote.dto.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.*

sealed class LedgerItem {
    abstract val date: Long

    data class Expense(val data: PettyCashExpenseDto) : LedgerItem() {
        override val date: Long = parseDate(data.createdAt ?: data.expenseDate)
    }

    data class Topup(val data: PettyCashTopupDto) : LedgerItem() {
        override val date: Long = parseDate(data.createdAt)
    }

    data class Sale(val data: OrderDto) : LedgerItem() {
        override val date: Long = parseDate(data.createdAt)
    }

    companion object {
        /**
         * Pola SimpleDateFormat yang lama diam-diam mengembalikan 0L untuk timestamp
         * ber-offset ("...+07:00") maupun berpecahan detik, sehingga transaksi bisa
         * lolos filter `>= startMillis` dan salah masuk perhitungan kas shift.
         * [JakartaTime.instantOrNull] menerima semua bentuk yang dikirim PostgREST.
         */
        fun parseDate(dateStr: String?): Long =
            com.sukashawarma.pos.domain.gate.JakartaTime.instantOrNull(dateStr)?.toEpochMilli() ?: 0L
    }
}

class ShiftViewModel(application: Application) : AndroidViewModel(application) {
    private val api = SupabaseClient.api
    private val database = (application as POSApplication).database
    private val orderDao = database.orderDao()

    // Status topup yang dianggap sudah masuk laci petty cash — mirror persis
    // SUDAH_DI_LACI di apps/pos-kasir/app/kasir/shift/page.tsx. Sebelumnya native
    // ikut menghitung "approved_by_finance" sebagai sudah masuk laci, padahal itu
    // baru tahap finance ACC/mencairkan ke Leader/Area Manager — dana belum benar-benar
    // sampai ke outlet sampai leader_forward_funds (status forwarded_by_leader).
    // Itu sebabnya saldo & "Uang Masuk" naik duluan sebelum statusnya benar-benar selesai.
    private val SUDAH_DI_LACI = listOf("completed", "approved", "forwarded_by_leader")

    val currentOutletId = MutableStateFlow("")
    val activeShift = MutableStateFlow<ShiftDto?>(null)
    
    val initialCash = MutableStateFlow(0.0)
    val expectedCash = MutableStateFlow(0.0)
    val shiftSalesTotal = MutableStateFlow(0.0)
    
    val initialPettyCash = MutableStateFlow(0.0)
    val pettyCashBalance = MutableStateFlow(0.0)
    val approvedTopupsTotal = MutableStateFlow(0.0)
    val expensesTotal = MutableStateFlow(0.0)

    val ledgerItems = MutableStateFlow<List<LedgerItem>>(emptyList())

    val openShiftInput = MutableStateFlow("")
    // Sama seperti web (page.tsx pettyCashLocked): true kalau saldo awal disodorkan
    // dari sisa petty cash shift sebelumnya, supaya kasir tidak sembarangan mengubahnya.
    val pettyCashLocked = MutableStateFlow(false)
    val actualCashInput = MutableStateFlow("")
    val actualPettyCashInput = MutableStateFlow("")
    
    val pettyCashCategory = MutableStateFlow("outlet")
    val pettyCashDescription = MutableStateFlow("")
    val pettyCashAmount = MutableStateFlow("")

    // Sama seperti web (router.push('/kasir/reports?shift=closed')): sinyal
    // sekali-pakai supaya layar pindah ke tab Laporan begitu tutup shift sukses.
    private val _navigateToReports = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val navigateToReports: SharedFlow<Unit> = _navigateToReports.asSharedFlow()

    val isShiftOpen = MutableStateFlow(false)
    /**
     * False until the server has successfully answered the first shift query for
     * the selected outlet. Keeping the POS usable on a transient fetch failure
     * mirrors the web blocker, which assumes an active shift until it knows
     * otherwise.
     */
    val isShiftStateReady = MutableStateFlow(false)
    val isLoading = MutableStateFlow(false)
    val errorMessage = MutableStateFlow<String?>(null)
    val successMessage = MutableStateFlow<String?>(null)

    init {
        // Serah-terima dana oleh leader terjadi di perangkat lain; tanpa listener
        // ini layar kasir baru berubah kalau kasir sendiri melakukan aksi.
        viewModelScope.launch {
            com.sukashawarma.pos.data.remote.GlobalEventBus.pettyCashEvent.collect {
                loadRealShiftData()
            }
        }
        viewModelScope.launch {
            com.sukashawarma.pos.data.remote.GlobalEventBus.orderSyncEvent.collect {
                loadRealShiftData()
            }
        }
    }

    fun setOutlet(outletId: String) {
        if (currentOutletId.value == outletId && isShiftStateReady.value) return
        currentOutletId.value = outletId
        activeShift.value = null
        isShiftOpen.value = true
        isShiftStateReady.value = false
        loadRealShiftData()
    }
    
    fun clearMessages() {
        errorMessage.value = null
        successMessage.value = null
    }

    fun loadRealShiftData() {
        val outletId = currentOutletId.value
        if (outletId.isBlank()) return
        viewModelScope.launch {
            isLoading.value = true
            clearMessages()
            try {
                val shiftsRes = api.getShifts(mapOf("outlet_id" to "eq.$outletId"))
                if (shiftsRes.isSuccessful && shiftsRes.body() != null) {
                    val list = shiftsRes.body()!!
                    val openShift = list.find { it.status.equals("open", ignoreCase = true) }
                    
                    activeShift.value = openShift
                    isShiftOpen.value = openShift != null
                    isShiftStateReady.value = true

                    if (openShift != null) {
                        pettyCashLocked.value = false
                        initialCash.value = openShift.startingCash
                        
                        // Use start time to filter items
                        val startMillis = LedgerItem.parseDate(openShift.startTime)
                        
                        // Fetch Expenses
                        val pettyRes = api.getPettyCashExpenses("eq.$outletId")
                        val expenses = pettyRes.body()?.filter { 
                            LedgerItem.parseDate(it.createdAt ?: it.expenseDate) >= startMillis 
                        } ?: emptyList()

                        // Fetch Topups
                        val topupsRes = api.getPettyCashTopups(mapOf("outlet_id" to "eq.$outletId"))
                        val topups = topupsRes.body()?.filter { 
                            LedgerItem.parseDate(it.createdAt) >= startMillis 
                        } ?: emptyList()

                        // Ambil pesanan kasir dari local database (agar transaksi offline/belum sync juga langsung masuk laci kasir)
                        val cashOrders = orderDao.getOrdersByDateRange(outletId, startMillis, Long.MAX_VALUE)
                            .map { it.toOrderDto() }
                            .filter { 
                                (it.status.equals("completed", true) || it.status.equals("ready", true)) &&
                                it.paymentMethod.equals("cash", true)
                            }

                        // Build Ledger
                        val items = mutableListOf<LedgerItem>()
                        items.addAll(expenses.map { LedgerItem.Expense(it) })
                        items.addAll(topups.map { LedgerItem.Topup(it) })
                        items.addAll(cashOrders.map { LedgerItem.Sale(it) })
                        
                        // Sort descending by date
                        items.sortByDescending { it.date }
                        ledgerItems.value = items

                        // Calculations
                        val salesTotal = cashOrders.sumOf { it.totalAmount }
                        shiftSalesTotal.value = salesTotal
                        expectedCash.value = initialCash.value + salesTotal
                        
                        val startPetty = openShift.startingPettyCash ?: 0.0
                        initialPettyCash.value = startPetty
                        
                        val topupsSum = topups
                            .filter { it.status in SUDAH_DI_LACI }
                            .sumOf { it.amount }
                        approvedTopupsTotal.value = topupsSum
                        
                        val expensesSum = expenses
                            .filter { it.deletedAt.isNullOrBlank() } // Only sum non-voided expenses
                            .sumOf { it.amount }
                        expensesTotal.value = expensesSum

                        // Calculate actual petty cash balance locally to ensure voided expenses are ignored
                        pettyCashBalance.value = initialPettyCash.value + approvedTopupsTotal.value - expensesTotal.value
                    } else {
                        // Shift is closed
                        ledgerItems.value = emptyList()
                        pettyCashBalance.value = 0.0
                        initialCash.value = 0.0
                        initialPettyCash.value = 0.0
                        expectedCash.value = 0.0

                        // Sama seperti web (page.tsx fetchCurrentState): kunci nominal setoran
                        // awal Dana Operasional ke sisa petty cash shift terakhir yang closed.
                        try {
                            val lastShiftRes = api.getShifts(
                                mapOf(
                                    "outlet_id" to "eq.$outletId",
                                    "status" to "eq.closed",
                                    "order" to "start_time.desc",
                                    "limit" to "1"
                                )
                            )
                            val lastShift = lastShiftRes.body()?.firstOrNull()
                            if (lastShiftRes.isSuccessful && lastShift != null) {
                                var endingBalance = lastShift.actualEndingPettyCash
                                    ?: lastShift.expectedEndingPettyCash
                                    ?: lastShift.startingPettyCash ?: 0.0

                                // Tambahkan topup & pengeluaran yang terjadi SETELAH shift itu
                                // ditutup (mis. leader menyerahkan dana semalam sebelum shift
                                // berikutnya dibuka), persis logika interim di web.
                                val refMillis = LedgerItem.parseDate(lastShift.endTime)
                                if (refMillis > 0) {
                                    val interimTopupsRes = api.getPettyCashTopups(mapOf("outlet_id" to "eq.$outletId"))
                                    val interimTopups = interimTopupsRes.body()
                                        ?.filter { it.status in SUDAH_DI_LACI && LedgerItem.parseDate(it.createdAt) > refMillis }
                                        ?.sumOf { it.amount } ?: 0.0

                                    val interimExpensesRes = api.getPettyCashExpenses("eq.$outletId")
                                    val interimExpenses = interimExpensesRes.body()
                                        ?.filter { it.deletedAt.isNullOrBlank() && LedgerItem.parseDate(it.createdAt ?: it.expenseDate) > refMillis }
                                        ?.sumOf { it.amount } ?: 0.0

                                    endingBalance += interimTopups - interimExpenses
                                }

                                openShiftInput.value = endingBalance.toInt().toString()
                                pettyCashLocked.value = true
                            } else {
                                // Belum pernah ada shift closed sama sekali (outlet baru) —
                                // fallback ke shift manapun yang starting_petty_cash-nya > 0.
                                val fallbackRes = api.getShifts(
                                    mapOf(
                                        "outlet_id" to "eq.$outletId",
                                        "starting_petty_cash" to "gt.0",
                                        "order" to "start_time.desc",
                                        "limit" to "1"
                                    )
                                )
                                val fallbackShift = fallbackRes.body()?.firstOrNull()
                                if (fallbackRes.isSuccessful && fallbackShift?.startingPettyCash != null) {
                                    openShiftInput.value = fallbackShift.startingPettyCash!!.toInt().toString()
                                    pettyCashLocked.value = true
                                } else {
                                    openShiftInput.value = "0"
                                    pettyCashLocked.value = false
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage.value = "Gagal memuat data shift: ${e.localizedMessage}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun openShift(isOnline: Boolean = true) {
        if (!isOnline) {
            clearMessages()
            errorMessage.value = "Anda harus online untuk membuka shift."
            return
        }
        val outletId = currentOutletId.value
        val starting = openShiftInput.value.toDoubleOrNull()
        if (outletId.isBlank() || starting == null || starting < 0) {
            errorMessage.value = "Saldo awal Petty Cash tidak valid."
            return
        }
        viewModelScope.launch {
            isLoading.value = true
            clearMessages()
            try {
                val res = api.openShift(OpenShiftPayload(outletId, starting))
                if (res.isSuccessful) {
                    successMessage.value = "Shift berhasil dibuka."
                    openShiftInput.value = ""
                    loadRealShiftData()
                    com.sukashawarma.pos.data.remote.GlobalEventBus.pettyCashEvent.tryEmit(Unit)
                } else {
                    errorMessage.value = "Gagal membuka shift: ${res.errorBody()?.string()}"
                }
            } catch (e: Exception) {
                errorMessage.value = "Gagal membuka shift: ${e.localizedMessage}"
            } finally {
                isLoading.value = false
            }
        }
    }

    val isClosingAllowed = MutableStateFlow(true)
    
    // Outlet tes ini juga dikecualikan dari trigger check_shift_closing_time di DB
    // (lihat supabase/migrations/20260813_shift_closing_time_test_outlet_bypass.sql),
    // jadi bisa tutup shift kapan saja tanpa perlu diutak-atik per tanggal lagi.
    private val TEST_OUTLET_ID = "eb174b2b-ff69-47eb-97af-b6c824d3ce4a"

    fun checkTimeRestriction() {
        val isTestOutlet = currentOutletId.value == TEST_OUTLET_ID
        if (isTestOutlet) {
            isClosingAllowed.value = true
            return
        }
        try {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta"))
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            isClosingAllowed.value = hour >= 22 || hour < 6
        } catch (e: Exception) {
            val cal = Calendar.getInstance()
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            isClosingAllowed.value = hour >= 22 || hour < 6
        }
    }

    // This mimics the web's /api/kasir/close-shift
    fun closeShift() {
        val shiftId = activeShift.value?.id
        val actual = actualCashInput.value.toDoubleOrNull()
        val actualPettyCash = actualPettyCashInput.value.toDoubleOrNull()
        
        if (shiftId == null || actual == null || actualPettyCash == null) {
            errorMessage.value = "Input hitungan uang fisik atau petty cash tidak valid."
            return
        }
        
        if (!isClosingAllowed.value) {
            errorMessage.value = "Penutupan shift hanya dapat dilakukan antara jam 22:00 hingga 06:00."
            return
        }

        viewModelScope.launch {
            isLoading.value = true
            clearMessages()
            try {
                val expectedCashVal = expectedCash.value
                val expectedPettyCashVal = pettyCashBalance.value
                
                // Format end time correctly
                val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                format.timeZone = TimeZone.getTimeZone("UTC")
                val endTimeStr = format.format(Date())

                val patch = mapOf(
                    "status" to "closed",
                    "end_time" to endTimeStr,
                    // Note: RLS might block updating closed_by or we can let backend default it
                    "actual_ending_cash" to actual,
                    "expected_ending_cash" to expectedCashVal,
                    "variance" to (actual - expectedCashVal),
                    "actual_ending_petty_cash" to actualPettyCash,
                    "expected_ending_petty_cash" to expectedPettyCashVal,
                    "petty_cash_variance" to (actualPettyCash - expectedPettyCashVal)
                )

                val res = api.updateShift("eq.$shiftId", patch)
                if (res.isSuccessful) {
                    actualCashInput.value = ""
                    actualPettyCashInput.value = ""
                    successMessage.value = "Shift berhasil ditutup."
                    loadRealShiftData()
                    com.sukashawarma.pos.data.remote.GlobalEventBus.pettyCashEvent.tryEmit(Unit)
                    _navigateToReports.tryEmit(Unit)
                } else {
                    errorMessage.value = "Gagal menutup shift: ${res.errorBody()?.string()}"
                }
            } catch (e: Exception) {
                errorMessage.value = "Gagal menutup shift: ${e.localizedMessage}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun addPettyCashExpense(bitmap: Bitmap? = null) {
        val amount = pettyCashAmount.value.toDoubleOrNull()
        val description = pettyCashDescription.value.trim()
        val outletId = currentOutletId.value
        if (amount == null || amount <= 0 || description.isBlank()) {
            errorMessage.value = "Nominal dan keterangan pengeluaran wajib diisi."
            return
        }
        if (bitmap == null) {
            errorMessage.value = "Foto struk/bukti wajib dilampirkan."
            return
        }
        viewModelScope.launch {
            isLoading.value = true
            clearMessages()
            try {
                var receiptUrl: String? = null
                if (outletId.isNotBlank()) {
                    receiptUrl = uploadPettyCashReceipt(outletId, bitmap)
                }

                val res = api.addPettyCash(
                    AddPettyCashPayload(
                        category = pettyCashCategory.value,
                        amount = amount,
                        description = description,
                        receiptUrl = receiptUrl
                    )
                )
                if (res.isSuccessful) {
                    successMessage.value = "Pengeluaran berhasil dicatat."
                    pettyCashAmount.value = ""
                    pettyCashDescription.value = ""
                    pettyCashCategory.value = "outlet"
                    loadRealShiftData()
                    com.sukashawarma.pos.data.remote.GlobalEventBus.pettyCashEvent.tryEmit(Unit)
                } else {
                    errorMessage.value = "Gagal mencatat petty cash: ${res.errorBody()?.string()}"
                }
            } catch (e: Exception) {
                errorMessage.value = "Gagal mencatat petty cash: ${e.localizedMessage}"
            } finally {
                isLoading.value = false
            }
        }
    }

    private suspend fun uploadPettyCashReceipt(outletId: String, bitmap: Bitmap): String? {
        return try {
            val stream = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            val fileName = "${outletId}_pettycash_${System.currentTimeMillis()}.jpg"
            val objectPath = "petty-cash-receipts/$outletId/$fileName" // Include bucket name
            val body = stream.toByteArray().toRequestBody("image/jpeg".toMediaTypeOrNull())

            val uploadRes = api.uploadPaymentProof(objectPath = objectPath, contentType = "image/jpeg", file = body)
            if (uploadRes.isSuccessful) {
                "${SupabaseClient.BASE_URL}storage/v1/object/public/$objectPath"
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun voidPettyCash(expenseId: String, reason: String) {
        viewModelScope.launch {
            isLoading.value = true
            clearMessages()
            try {
                val res = api.voidPettyCashExpense(VoidPettyCashPayload(expenseId, reason))
                if (res.isSuccessful) {
                    successMessage.value = "Pengeluaran berhasil dibatalkan."
                    loadRealShiftData()
                    com.sukashawarma.pos.data.remote.GlobalEventBus.pettyCashEvent.tryEmit(Unit)
                } else {
                    errorMessage.value = "Gagal membatalkan petty cash: ${res.errorBody()?.string()}"
                }
            } catch (e: Exception) {
                errorMessage.value = "Gagal membatalkan petty cash: ${e.localizedMessage}"
            } finally {
                isLoading.value = false
            }
        }
    }

    private fun com.sukashawarma.pos.data.local.entity.LocalOrderEntity.toOrderDto(): OrderDto {
        val itemsType = object : com.google.gson.reflect.TypeToken<List<com.sukashawarma.pos.domain.model.OrderItem>>() {}.type
        val itemsList: List<com.sukashawarma.pos.domain.model.OrderItem> =
            com.google.gson.Gson().fromJson(itemsJson, itemsType) ?: emptyList()
        val orderItems = itemsList.map {
            com.sukashawarma.pos.data.remote.dto.OrderItemDto(
                id = it.id,
                orderId = id,
                menuItemId = it.menuItemId,
                menuItemName = it.name,
                quantity = it.quantity,
                unitPrice = it.unitPrice,
                subtotal = it.subtotal
            )
        }

        val dtString = java.time.format.DateTimeFormatter.ISO_INSTANT.format(java.time.Instant.ofEpochMilli(createdAt))
        return OrderDto(
            id = id,
            outletId = outletId,
            orderNumber = orderNumber,
            customerName = customerName,
            status = status.lowercase(),
            source = source.lowercase(),
            paymentMethod = paymentMethod.lowercase(),
            discountAmount = discountAmount,
            promoSubsidy = 0.0,
            totalAmount = totalAmount,
            amountReceived = amountReceived,
            changeAmount = changeAmount,
            kitchenReceiptPrinted = kitchenReceiptPrinted,
            customerReceiptPrinted = customerReceiptPrinted,
            cancellationStatus = null,
            cancellationUserName = null,
            channel = channel,
            createdAt = dtString,
            orderItems = orderItems
        )
    }
}
