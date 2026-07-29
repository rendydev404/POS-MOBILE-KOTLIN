package com.sukashawarma.pos.presentation.shift

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sukashawarma.pos.data.remote.SupabaseClient
import com.sukashawarma.pos.data.remote.dto.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import android.graphics.Bitmap
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class ShiftViewModel(application: Application) : AndroidViewModel(application) {
    private val api = SupabaseClient.api

    val currentOutletId = MutableStateFlow("")
    val currentShiftId = MutableStateFlow<String?>(null)
    val initialCash = MutableStateFlow(0.0)
    val expectedCash = MutableStateFlow(0.0)
    val totalPettyCashOut = MutableStateFlow(0.0)
    val pettyCashBalance = MutableStateFlow(0.0)

    val openShiftInput = MutableStateFlow("")
    val actualCashInput = MutableStateFlow("")
    val pettyCashCategory = MutableStateFlow("operasional")
    val pettyCashDescription = MutableStateFlow("")
    val pettyCashAmount = MutableStateFlow("")

    val shiftsHistory = MutableStateFlow<List<ShiftDto>>(emptyList())
    val pettyCashHistory = MutableStateFlow<List<PettyCashExpenseDto>>(emptyList())

    val isShiftOpen = MutableStateFlow(false)
    val isLoading = MutableStateFlow(false)
    val errorMessage = MutableStateFlow<String?>(null)
    val lastCloseVariance = MutableStateFlow<Double?>(null)

    fun setOutlet(outletId: String) {
        currentOutletId.value = outletId
        loadRealShiftData()
    }

    val topupsHistory = MutableStateFlow<List<PettyCashTopupDto>>(emptyList())

    fun loadRealShiftData() {
        val outletId = currentOutletId.value
        if (outletId.isBlank()) return
        viewModelScope.launch {
            isLoading.value = true
            try {
                val shiftsRes = api.getShifts(mapOf("outlet_id" to "eq.$outletId"))
                if (shiftsRes.isSuccessful && shiftsRes.body() != null) {
                    val list = shiftsRes.body()!!
                    shiftsHistory.value = list
                    val openShift = list.find { it.status.equals("open", ignoreCase = true) }
                    currentShiftId.value = openShift?.id
                    isShiftOpen.value = openShift != null
                    initialCash.value = openShift?.startingCash ?: 0.0
                    if (openShift != null) {
                        val expectedRes = api.getExpectedShiftCash(ShiftIdPayload(openShift.id))
                        if (expectedRes.isSuccessful) {
                            expectedCash.value = expectedRes.body() ?: 0.0
                        }
                    }
                }

                val pettyRes = api.getPettyCashExpenses(outletId)
                if (pettyRes.isSuccessful && pettyRes.body() != null) {
                    val list = pettyRes.body()!!
                    pettyCashHistory.value = list
                    totalPettyCashOut.value = list.filter { it.status == "approved" }.sumOf { it.amount }
                }

                val topupsRes = api.getPettyCashTopups(mapOf("outlet_id" to "eq.$outletId"))
                if (topupsRes.isSuccessful && topupsRes.body() != null) {
                    topupsHistory.value = topupsRes.body()!!
                }

                val balanceRes = api.getPettyCashBalance(OutletIdPayload(outletId))
                if (balanceRes.isSuccessful) {
                    pettyCashBalance.value = balanceRes.body() ?: 0.0
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
            errorMessage.value = null
            try {
                val res = api.openShift(OpenShiftPayload(outletId, starting))
                if (res.isSuccessful) {
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

    fun closeShift() {
        val shiftId = currentShiftId.value
        val actual = actualCashInput.value.toDoubleOrNull()
        if (shiftId == null || actual == null) {
            errorMessage.value = "Uang fisik laci tidak valid."
            return
        }
        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null
            try {
                val res = api.closeShiftBlind(CloseShiftPayload(shiftId, actual))
                if (res.isSuccessful) {
                    lastCloseVariance.value = actual - expectedCash.value
                    actualCashInput.value = ""
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
        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null
            try {
                var receiptUrl: String? = null
                if (bitmap != null && outletId.isNotBlank()) {
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
                    pettyCashAmount.value = ""
                    pettyCashDescription.value = ""
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
            val objectPath = "receipts/$fileName"
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
            errorMessage.value = null
            try {
                val res = api.voidPettyCashExpense(VoidPettyCashPayload(expenseId, reason))
                if (res.isSuccessful) {
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
            errorMessage.value = null
            try {
                val res = api.crewReceiveFunds(ReceiveFundsPayload(topupId))
                if (res.isSuccessful) {
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
