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

    private val _spvPhone = MutableStateFlow("")
    val spvPhone: StateFlow<String> = _spvPhone

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

                // Fetch outlet phone for WhatsApp if not yet fetched
                if (_spvPhone.value.isBlank()) {
                    try {
                        val outletRes = api.getOutletById(outletId)
                        val outlet = outletRes.body()?.firstOrNull()
                        if (!outlet?.phone.isNullOrBlank()) {
                            var phone = outlet!!.phone!!.replace(Regex("[^0-9]"), "")
                            if (phone.startsWith("0")) {
                                phone = "62" + phone.substring(1)
                            }
                            android.util.Log.d("AttendanceViewModel", "Fetched SPV phone from outlet: $phone")
                            _spvPhone.value = phone
                        } else {
                            android.util.Log.d("AttendanceViewModel", "Outlet phone is null or blank in database")
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

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
                val hasAnyoneOut = staffStatus.values.all { it != "in" } && staffStatus.isNotEmpty()

                if (!hasAnyoneIn) {
                    _isLocked.value = true
                    if (hasAnyoneOut) {
                        _lockReason.value = "Semua kru sudah absen pulang. Toko sudah tutup untuk hari ini."
                    } else {
                        _lockReason.value = "Menunggu kru absen hadir."
                    }
                    return@launch
                }

                // Check daily checklist progress
                val catRes = api.getChecklistCategories(mapOf(
                    "outlet_id" to "eq.$outletId"
                ))
                
                val categories = catRes.body() ?: emptyList()
                val requiredIds = categories.flatMap { it.checklistItems ?: emptyList() }
                    .filter { it.isRequired }
                    .map { it.id }
                    
                val total = requiredIds.size
                var done = 0
                
                if (total > 0) {
                    val recRes = api.getDailyChecklistRecords(mapOf(
                        "outlet_id" to "eq.$outletId",
                        "date" to "eq.$todayStr",
                        "limit" to "1"
                    ))
                    
                    val record = recRes.body()?.firstOrNull()
                    if (record != null) {
                        val ticksRes = api.getDailyChecklistTicks(mapOf(
                            "record_id" to "eq.${record.id}"
                        ))
                        val ticks = ticksRes.body() ?: emptyList()
                        val tickedIds = ticks.map { it.itemId }.toSet()
                        done = requiredIds.count { tickedIds.contains(it) }
                    }
                }
                
                if (total > 0 && done < total) {
                    _isLocked.value = true
                    _lockReason.value = "Checklist buka toko belum selesai. ($done/$total)"
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

    fun requestBypass(reason: String, onLinkGenerated: (String) -> Unit) {
        if (outletId.isBlank() || staffId.isBlank()) return
        viewModelScope.launch {
            try {
                _bypassStatus.value = "pending"
                val requestType = if (_lockReason.value.contains("absen")) "attendance" else "checklist"
                val response = api.createBypassRequest(CreateBypassRequestPayload(
                    outletId = outletId,
                    staffId = null, // Don't send username as staff_id (UUID mismatch)
                    requestType = requestType,
                    requestedByName = staffId,
                    reason = reason
                ))
                
                if (!response.isSuccessful) {
                    android.util.Log.e("BypassError", "Error from Supabase: ${response.code()} ${response.errorBody()?.string()}")
                } else {
                    android.util.Log.d("BypassSuccess", "Response body: ${response.body()}")
                }
                
                val inserted = response.body()?.firstOrNull()
                if (inserted == null) {
                    android.util.Log.e("BypassError", "Inserted is null! Is body empty? ${response.body()?.isEmpty()}")
                } else {
                    val approveLink = "https://app.sukashawarma.com/api/bypass/approve?id=${inserted.id}"
                    val waText = "Halo Regional Manager, saya mengajukan *Bypass Darurat* untuk sistem POS.\n\nKasir: $staffId\nAlasan: ${reason.trim()}\n\nKlik link berikut untuk menyetujui atau menolak:\n$approveLink"
                    onLinkGenerated(waText)
                }
                
                checkLockStatus()
            } catch (e: Exception) {
                e.printStackTrace()
                _bypassStatus.value = null
            }
        }
    }
}
