package com.sukashawarma.pos.presentation.gate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sukashawarma.pos.data.local.BypassStore
import com.sukashawarma.pos.data.local.SessionBypassStore
import com.sukashawarma.pos.data.remote.GlobalEventBus
import com.sukashawarma.pos.data.remote.SupabaseApi
import com.sukashawarma.pos.data.remote.SupabaseClient
import com.sukashawarma.pos.data.remote.dto.CreateBypassRequestPayload
import com.sukashawarma.pos.domain.gate.BlockType
import com.sukashawarma.pos.domain.gate.GateEvaluator
import com.sukashawarma.pos.domain.gate.GateInput
import com.sukashawarma.pos.domain.gate.GateState
import com.sukashawarma.pos.domain.gate.JakartaTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Mirror dari `GlobalBlockerMount.tsx`. Hanya mengambil data dan menyerahkan
 * keputusan ke [GateEvaluator]. Tidak ada polling — refresh dipicu
 * [GlobalEventBus.gateRefreshEvent] (event realtime, reconnect, dan resume).
 */
class PosGateViewModel(
    private val api: SupabaseApi = SupabaseClient.api,
    private val bypassStore: BypassStore = SessionBypassStore
) : ViewModel() {

    private var outletId = ""
    private var staffId = ""
    private var staffName = ""

    private val _state = MutableStateFlow(GateState())
    val state: StateFlow<GateState> = _state

    private val _spvPhone = MutableStateFlow("")
    val spvPhone: StateFlow<String> = _spvPhone

    init {
        viewModelScope.launch {
            GlobalEventBus.gateRefreshEvent.collect { refresh() }
        }
    }

    fun setSession(outletId: String, staffId: String) {
        this.outletId = outletId
        this.staffId = staffId
        refresh()
    }

    fun clearSession() {
        outletId = ""
        staffId = ""
        staffName = ""
        bypassStore.clear()
        _state.value = GateState()
        _spvPhone.value = ""
    }

    fun markBypassed() {
        bypassStore.markBypassed()
        refresh()
    }

    fun refresh() {
        if (outletId.isBlank() || staffId.isBlank()) return

        viewModelScope.launch {
            try {
                val today = JakartaTime.today()
                val todayStr = JakartaTime.dateString(today)
                val start = JakartaTime.startOfDayIso(today)
                val end = JakartaTime.endOfDayIso(today)

                val staff = api.getStaffById("eq.$staffId").body()?.firstOrNull()
                staffName = staff?.name ?: staff?.username ?: ""

                val outlet = api.getOutletById("eq.$outletId").body()?.firstOrNull()
                _spvPhone.value = normalisePhone(outlet?.phone)

                val bypasses = api
                    .getBypassRequestsForDay("eq.$outletId", "gte.$start")
                    .body()
                    .orEmpty()

                // Setara `markApprovedAndUnlock()` di web: begitu SPV menyetujui,
                // status dicatat lokal supaya gate tetap terbuka walau
                // pemanggilan berikutnya gagal.
                val hasApprovedBypass = bypasses.any {
                    it.status == "approved" && JakartaTime.isOnDay(it.createdAt, today)
                }
                if (hasApprovedBypass) bypassStore.markBypassed()

                val attendances = api
                    .getAttendanceForDay("eq.$outletId", "gte.$start", "lte.$end")
                    .body()
                    .orEmpty()

                val requiredItemIds = api
                    .getRequiredOpeningChecklist("eq.$outletId")
                    .body()
                    .orEmpty()
                    .flatMap { it.checklistItems.orEmpty() }
                    .filter { it.isRequired }
                    .map { it.id }

                val tickedItemIds: Set<String> = if (requiredItemIds.isEmpty()) {
                    emptySet()
                } else {
                    val record = api
                        .getChecklistRecordForDay("eq.$outletId", "eq.$todayStr")
                        .body()
                        ?.firstOrNull()
                    if (record == null) {
                        emptySet()
                    } else {
                        api.getTicksForRecord("eq.${record.id}")
                            .body()
                            .orEmpty()
                            .map { it.itemId }
                            .toSet()
                    }
                }

                _state.value = GateEvaluator.evaluate(
                    GateInput(
                        staff = staff,
                        outlet = outlet,
                        bypasses = bypasses,
                        attendances = attendances,
                        requiredItemIds = requiredItemIds,
                        tickedItemIds = tickedItemIds,
                        today = today,
                        bypassedAll = bypassStore.isBypassed()
                    )
                )
            } catch (e: Exception) {
                // Fail-open, sama seperti web: kegagalan jaringan tidak boleh
                // mengunci kasir.
                android.util.Log.e("PosGate", "Gagal evaluasi gate", e)
                _state.value = GateState()
            }
        }
    }

    fun requestBypass(reason: String, onWhatsAppText: (String) -> Unit) {
        if (outletId.isBlank() || reason.isBlank()) return

        viewModelScope.launch {
            try {
                val requestType = when (_state.value.type) {
                    BlockType.CHECKLIST -> "checklist"
                    else -> "attendance"
                }

                val response = api.createBypassRequest(
                    CreateBypassRequestPayload(
                        outletId = outletId,
                        staffId = null,
                        requestType = requestType,
                        requestedByName = staffName,
                        reason = reason.trim()
                    )
                )

                val inserted = response.body()?.firstOrNull()
                if (inserted == null) {
                    android.util.Log.e(
                        "PosGate",
                        "Bypass gagal: ${response.code()} ${response.errorBody()?.string()}"
                    )
                    return@launch
                }

                val approveLink =
                    "https://app.sukashawarma.com/api/bypass/approve?id=${inserted.id}"
                onWhatsAppText(
                    "Halo SPV, saya mengajukan *Bypass Darurat* untuk sistem POS.\n\n" +
                        "Kasir: $staffName\n" +
                        "Alasan: ${reason.trim()}\n\n" +
                        "Klik link berikut untuk menyetujui atau menolak:\n$approveLink"
                )

                refresh()
            } catch (e: Exception) {
                android.util.Log.e("PosGate", "Gagal kirim bypass", e)
            }
        }
    }

    private fun normalisePhone(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val digits = raw.replace(Regex("[^0-9]"), "")
        return if (digits.startsWith("0")) "62" + digits.substring(1) else digits
    }
}
