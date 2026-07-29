package com.sukashawarma.pos.presentation.shift

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sukashawarma.pos.data.remote.SupabaseClient
import com.sukashawarma.pos.data.remote.dto.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
        fun parseDate(dateStr: String?): Long {
            if (dateStr == null) return 0L
            return try {
                val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                format.timeZone = TimeZone.getTimeZone("UTC")
                format.parse(dateStr)?.time ?: 0L
            } catch (e: Exception) {
                0L
            }
        }
    }
}

class ShiftViewModel(application: Application) : AndroidViewModel(application) {
    private val api = SupabaseClient.api

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
    val actualCashInput = MutableStateFlow("")
    val actualPettyCashInput = MutableStateFlow("")
    
    val pettyCashCategory = MutableStateFlow("operasional")
    val pettyCashDescription = MutableStateFlow("")
    val pettyCashAmount = MutableStateFlow("")

    val isShiftOpen = MutableStateFlow(false)
    val isLoading = MutableStateFlow(false)
    val errorMessage = MutableStateFlow<String?>(null)
    val successMessage = MutableStateFlow<String?>(null)

    fun setOutlet(outletId: String) {
        currentOutletId.value = outletId
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
                    
                    if (openShift != null) {
                        initialCash.value = openShift.startingCash
                        
                        // Use start time to filter items
                        val startTime = openShift.startTime ?: ""
                        
                        // Fetch Expenses
                        val pettyRes = api.getPettyCashExpenses(outletId)
                        val expenses = pettyRes.body()?.filter { 
                            (it.createdAt ?: it.expenseDate ?: "") >= startTime 
                        } ?: emptyList()

                        // Fetch Topups
                        val topupsRes = api.getPettyCashTopups(mapOf("outlet_id" to "eq.$outletId"))
                        val topups = topupsRes.body()?.filter { 
                            it.createdAt >= startTime 
                        } ?: emptyList()

                        // Fetch Cash Orders
                        val ordersRes = api.getOrders(mapOf(
                            "outlet_id" to "eq.$outletId",
                            "status" to "eq.completed"
                        ))
                        val cashOrders = ordersRes.body()?.filter { 
                            it.createdAt >= startTime && it.paymentMethod == "cash"
                        } ?: emptyList()

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
                        
                        val startPetty = openShift.startingCash // Wait, ShiftDto doesn't have startingPettyCash. We might need to assume 0 or check if it's there. 
                        // In page.tsx: const startPetty = Number(shiftData.starting_petty_cash) || 0
                        // Since startingPettyCash is missing in ShiftDto, we'll try fetching it or default to 0. 
                        // Actually, the API getPettyCashBalance calculates it. Let's rely on the RPC or calculate locally.
                        
                        val topupsSum = topups
                            .filter { it.status in listOf("completed", "approved", "approved_by_finance", "forwarded_by_leader") }
                            .sumOf { it.amount }
                        approvedTopupsTotal.value = topupsSum
                        
                        val expensesSum = expenses
                            .filter { it.status != "voided" } // Also check if deleted_at exists, but DTO might just have status
                            .sumOf { it.amount }
                        expensesTotal.value = expensesSum

                        // Fetch actual petty cash balance via RPC (more reliable)
                        val balanceRes = api.getPettyCashBalance(OutletIdPayload(outletId))
                        if (balanceRes.isSuccessful) {
                            pettyCashBalance.value = balanceRes.body() ?: 0.0
                        }
                    } else {
                        // Shift is closed
                        ledgerItems.value = emptyList()
                        pettyCashBalance.value = 0.0
                        initialCash.value = 0.0
                        expectedCash.value = 0.0
                        
                        // Fetch last closed shift for starting petty cash prep if needed
                        // Not strictly required since openShift API takes startingCash
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

    fun openShift() {
        val outletId = currentOutletId.value
        val starting = openShiftInput.value.toDoubleOrNull()
        if (outletId.isBlank() || starting == null) {
            errorMessage.value = "Modal awal tidak valid."
            return
        }
        viewModelScope.launch {
            isLoading.value = true
            clearMessages()
            try {
                // Notice the API payload expects p_starting_cash, but the Web uses p_starting_petty_cash
                // The DTO OpenShiftPayload has p_starting_cash.
                val res = api.openShift(OpenShiftPayload(outletId, starting))
                if (res.isSuccessful) {
                    successMessage.value = "Shift berhasil dibuka."
                    openShiftInput.value = ""
                    loadRealShiftData()
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
    
    fun checkTimeRestriction() {
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
                    pettyCashCategory.value = "operasional"
                    loadRealShiftData()
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
            val objectPath = "$outletId/$fileName" // Matching web path structure
            val body = stream.toByteArray().toRequestBody("image/jpeg".toMediaTypeOrNull())

            val uploadRes = api.uploadPaymentProof(objectPath = objectPath, contentType = "image/jpeg", file = body)
            if (uploadRes.isSuccessful) {
                "${SupabaseClient.BASE_URL}storage/v1/object/public/petty-cash-receipts/$objectPath"
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

    fun receiveTopup(topupId: String) {
        viewModelScope.launch {
            isLoading.value = true
            clearMessages()
            try {
                val res = api.crewReceiveFunds(ReceiveFundsPayload(topupId))
                if (res.isSuccessful) {
                    successMessage.value = "Uang berhasil diterima."
                    loadRealShiftData()
                } else {
                    errorMessage.value = "Gagal menerima dana: ${res.errorBody()?.string()}"
                }
            } catch (e: Exception) {
                errorMessage.value = "Gagal menerima dana: ${e.localizedMessage}"
            } finally {
                isLoading.value = false
            }
        }
    }
}
