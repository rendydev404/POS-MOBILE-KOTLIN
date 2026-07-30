package com.sukashawarma.pos.presentation.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sukashawarma.pos.data.remote.GlobalEventBus
import com.sukashawarma.pos.data.remote.SupabaseClient
import com.sukashawarma.pos.data.remote.dto.CreateBypassRequestPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AttendanceViewModel : ViewModel() {
    private val api = SupabaseClient.api
    private var outletId = ""
    private var staffId = ""

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked

    private val _lockReason = MutableStateFlow("")
    val lockReason: StateFlow<String> = _lockReason

    private val _bypassStatus = MutableStateFlow<String?>(null)
    val bypassStatus: StateFlow<String?> = _bypassStatus

    init {
        viewModelScope.launch {
            GlobalEventBus.bypassRequestEvent.collect {
                checkLockStatus()
            }
        }
    }

    fun setSession(outletId: String, staffId: String) {
        this.outletId = outletId
        this.staffId = staffId
        checkLockStatus()
    }

    fun checkLockStatus() {
        if (outletId.isBlank() || staffId.isBlank()) return
        
        viewModelScope.launch {
            try {
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val today = java.time.LocalDate.now(java.time.ZoneId.systemDefault())

                // Check active approved bypass
                val bypassRes = api.getBypassRequests(mapOf(
                    "outlet_id" to "eq.$outletId",
                    "order" to "created_at.desc",
                    "limit" to "20"
                ))
                
                val bypasses = (bypassRes.body() ?: emptyList()).filter { 
                    val ts = it.createdAt
                    if (ts != null) {
                        try {
                            val cleanTs = ts.replace(" ", "T")
                            val instant = java.time.Instant.parse(if (cleanTs.endsWith("Z") || cleanTs.contains("+")) cleanTs else "${cleanTs}Z")
                            instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate() == today
                        } catch(e: Exception) { 
                            // fallback
                            ts.startsWith(todayStr)
                        }
                    } else false
                }
                
                val approvedBypass = bypasses.find { it.status == "approved" }
                val pendingBypass = bypasses.find { it.status == "pending" }
                
                if (approvedBypass != null) {
                    _isLocked.value = false
                    _bypassStatus.value = "approved"
                    return@launch
                }
                
                if (pendingBypass != null) {
                    _bypassStatus.value = "pending"
                } else {
                    _bypassStatus.value = null
                }

                // Check attendance 'in' for anyone in this outlet
                val attRes = api.getAttendance(mapOf(
                    "outlet_id" to "eq.$outletId",
                    "select" to "outlet_staff_id,type,ts_server",
                    "order" to "ts_server.desc",
                    "limit" to "100"
                ))
                
                val attendances = (attRes.body() ?: emptyList()).filter {
                    val ts = it.tsServer
                    if (ts != null) {
                        try {
                            val cleanTs = ts.replace(" ", "T")
                            val instant = java.time.Instant.parse(if (cleanTs.endsWith("Z") || cleanTs.contains("+")) cleanTs else "${cleanTs}Z")
                            instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate() == today
                        } catch(e: Exception) { 
                            // fallback
                            ts.startsWith(todayStr)
                        }
                    } else false
                }
                
                // Reverse to process chronologically
                val staffStatus = mutableMapOf<String, String>()
                for (att in attendances.reversed()) {
                    val sid = att.outletStaffId ?: continue
                    staffStatus[sid] = att.type
                }
                
                val hasAnyoneIn = staffStatus.values.any { it == "in" }

                // Check daily checklist
                val chkRes = api.getDailyChecklistRecords(mapOf(
                    "outlet_id" to "eq.$outletId",
                    "order" to "record_date.desc",
                    "limit" to "10"
                ))
                val hasChecklist = (chkRes.body() ?: emptyList()).any { it.recordDate.startsWith(todayStr) }

                if (!hasAnyoneIn) {
                    _isLocked.value = true
                    _lockReason.value = "Menunggu kru absen hadir."
                } else if (!hasChecklist) {
                    _isLocked.value = true
                    _lockReason.value = "Checklist Harian belum diisi hari ini."
                } else {
                    _isLocked.value = false
                    if (_bypassStatus.value != "pending") {
                        _bypassStatus.value = null
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun requestBypass() {
        if (outletId.isBlank() || staffId.isBlank()) return
        viewModelScope.launch {
            try {
                _bypassStatus.value = "pending"
                api.createBypassRequest(CreateBypassRequestPayload(
                    outletId = outletId,
                    staffId = staffId,
                    requestType = if (_lockReason.value.contains("absen")) "attendance" else "checklist"
                ))
                checkLockStatus()
            } catch (e: Exception) {
                e.printStackTrace()
                _bypassStatus.value = null
            }
        }
    }
}
