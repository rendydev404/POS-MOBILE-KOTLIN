package com.sukashawarma.pos.presentation.shift

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sukashawarma.pos.data.remote.SupabaseClient
import com.sukashawarma.pos.data.remote.dto.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

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

    fun loadRealShiftData() {
        val outletId = currentOutletId.value
        if (outletId.isBlank()) return
        viewModelScope.launch {
            isLoading.value = true
            try {
                val shiftsRes = api.getShifts("eq.$outletId")
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

                val pettyRes = api.getPettyCashExpenses("eq.$outletId")
                if (pettyRes.isSuccessful && pettyRes.body() != null) {
                    val list = pettyRes.body()!!
                    pettyCashHistory.value = list
                    totalPettyCashOut.value = list.sumOf { it.amount }
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

    fun addPettyCashExpense() {
        val amount = pettyCashAmount.value.toDoubleOrNull()
        val description = pettyCashDescription.value.trim()
        if (amount == null || amount <= 0 || description.isBlank()) {
            errorMessage.value = "Nominal dan keterangan pengeluaran wajib diisi."
            return
        }
        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null
            try {
                val res = api.addPettyCash(
                    AddPettyCashPayload(
                        category = pettyCashCategory.value,
                        amount = amount,
                        description = description
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
}
