package com.sukashawarma.pos.domain.gate

import com.sukashawarma.pos.data.remote.dto.AttendanceDto
import com.sukashawarma.pos.data.remote.dto.BypassRequestDto
import com.sukashawarma.pos.data.remote.dto.OutletDto
import com.sukashawarma.pos.data.remote.dto.StaffProfileDto
import java.time.LocalDate

/** Lima tipe blokir, menyamai `BlockType` di BlockedOverlay.tsx. */
enum class BlockType { USER, OUTLET, ATTENDANCE, CHECKLIST, CLOSED }

enum class BypassStatus { PENDING, REJECTED }

data class ChecklistProgress(val total: Int, val done: Int)

data class GateState(
    val isBlocked: Boolean = false,
    val type: BlockType = BlockType.USER,
    val reason: String = "",
    val progress: ChecklistProgress? = null,
    val bypassStatus: BypassStatus? = null
)

/** Seluruh data mentah yang dibutuhkan untuk satu kali evaluasi gate. */
data class GateInput(
    val staff: StaffProfileDto?,
    val outlet: OutletDto?,
    val bypasses: List<BypassRequestDto>,
    val attendances: List<AttendanceDto>,
    val requiredItemIds: List<String>,
    val tickedItemIds: Set<String>,
    val today: LocalDate,
    val bypassedAll: Boolean
)
