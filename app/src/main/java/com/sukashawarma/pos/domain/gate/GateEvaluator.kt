package com.sukashawarma.pos.domain.gate

/**
 * Evaluasi gate kasir. Fungsi murni — tidak menyentuh jaringan, Android, atau
 * jam sistem. Urutan cabang mengikuti `checkStatus()` lalu `checkKasirGate()`
 * di `GlobalBlockerMount.tsx` versi web.
 */
object GateEvaluator {

    private val GATED_ROLES = setOf("crew", "leader")

    private const val REASON_ATTENDANCE = "Menunggu kru absen hadir."
    private const val REASON_CLOSED =
        "Semua kru sudah absen pulang. Toko sudah tutup untuk hari ini."
    private const val REASON_CHECKLIST = "Checklist buka toko belum selesai."
    private const val REASON_USER_DEFAULT = "Akun Anda dinonaktifkan oleh Admin."
    private const val REASON_OUTLET_DEFAULT =
        "Cabang tempat Anda bertugas sedang dinonaktifkan oleh Admin."

    fun evaluate(input: GateInput): GateState {
        val bypassStatus = bypassStatusToday(input)

        if (input.bypassedAll) return GateState(bypassStatus = bypassStatus)

        val staff = input.staff ?: return GateState(bypassStatus = bypassStatus)
        if (staff.role == "admin") return GateState(bypassStatus = bypassStatus)

        if (staff.isActive == false) {
            return GateState(
                isBlocked = true,
                type = BlockType.USER,
                reason = staff.inactiveReason ?: REASON_USER_DEFAULT,
                bypassStatus = bypassStatus
            )
        }

        val outlet = input.outlet
        if (outlet != null && outlet.is_active == false) {
            return GateState(
                isBlocked = true,
                type = BlockType.OUTLET,
                reason = outlet.inactiveReason ?: REASON_OUTLET_DEFAULT,
                bypassStatus = bypassStatus
            )
        }

        if (staff.role !in GATED_ROLES || staff.outletId.isNullOrBlank()) {
            return GateState(bypassStatus = bypassStatus)
        }

        val hasApprovedBypass = input.bypasses.any {
            it.status == "approved" && JakartaTime.isOnDay(it.createdAt, input.today)
        }
        if (hasApprovedBypass) return GateState(bypassStatus = bypassStatus)

        // Status terakhir tiap staf hari ini, diproses kronologis.
        val todayAttendance = input.attendances
            .filter { JakartaTime.isOnDay(it.tsServer, input.today) }
            .sortedBy { it.tsServer }

        val lastTypePerStaff = LinkedHashMap<String, String>()
        for (att in todayAttendance) {
            val staffId = att.outletStaffId ?: continue
            lastTypePerStaff[staffId] = att.type
        }

        if (lastTypePerStaff.isEmpty()) {
            return GateState(
                isBlocked = true,
                type = BlockType.ATTENDANCE,
                reason = REASON_ATTENDANCE,
                bypassStatus = bypassStatus
            )
        }

        if (lastTypePerStaff.values.none { it == "in" }) {
            return GateState(
                isBlocked = true,
                type = BlockType.CLOSED,
                reason = REASON_CLOSED,
                bypassStatus = bypassStatus
            )
        }

        val total = input.requiredItemIds.size
        val done = input.requiredItemIds.count { input.tickedItemIds.contains(it) }

        if (total > 0 && done < total) {
            return GateState(
                isBlocked = true,
                type = BlockType.CHECKLIST,
                reason = REASON_CHECKLIST,
                progress = ChecklistProgress(total = total, done = done),
                bypassStatus = bypassStatus
            )
        }

        return GateState(bypassStatus = bypassStatus)
    }

    private fun bypassStatusToday(input: GateInput): BypassStatus? {
        val todayBypasses = input.bypasses
            .filter { JakartaTime.isOnDay(it.createdAt, input.today) }
            .sortedByDescending { it.createdAt }

        return when (todayBypasses.firstOrNull()?.status) {
            "pending" -> BypassStatus.PENDING
            "rejected" -> BypassStatus.REJECTED
            else -> null
        }
    }
}
