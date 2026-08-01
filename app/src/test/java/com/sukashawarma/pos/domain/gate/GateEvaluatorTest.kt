package com.sukashawarma.pos.domain.gate

import com.sukashawarma.pos.data.remote.dto.AttendanceDto
import com.sukashawarma.pos.data.remote.dto.BypassRequestDto
import com.sukashawarma.pos.data.remote.dto.OutletDto
import com.sukashawarma.pos.data.remote.dto.StaffProfileDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GateEvaluatorTest {

    private val today = LocalDate.of(2026, 8, 1)
    private val outletId = "outlet-1"

    private fun staff(
        role: String = "crew",
        isActive: Boolean? = true,
        inactiveReason: String? = null
    ) = StaffProfileDto(
        id = "staff-1",
        username = "kasir1",
        name = "Budi",
        role = role,
        outletId = outletId,
        isActive = isActive,
        inactiveReason = inactiveReason
    )

    private fun outlet(
        isActive: Boolean? = true,
        inactiveReason: String? = null
    ) = OutletDto(
        id = outletId,
        slug = "bnr",
        name = "Outlet BNR",
        address = null,
        phone = "081234567890",
        type = "outlet",
        is_active = isActive,
        inactiveReason = inactiveReason
    )

    private fun attendance(
        staffId: String,
        type: String,
        ts: String = "2026-08-01T03:00:00+00:00"
    ) = AttendanceDto(
        id = "att-$staffId-$type-$ts",
        outletStaffId = staffId,
        outletId = outletId,
        type = type,
        tsServer = ts
    )

    private fun bypass(
        status: String,
        ts: String = "2026-08-01T03:00:00+00:00"
    ) = BypassRequestDto(
        id = "bp-$status-$ts",
        outletId = outletId,
        staffId = null,
        requestType = "attendance",
        status = status,
        reason = "Absensi error",
        createdAt = ts
    )

    private fun input(
        staff: StaffProfileDto? = staff(),
        outlet: OutletDto? = outlet(),
        bypasses: List<BypassRequestDto> = emptyList(),
        attendances: List<AttendanceDto> = emptyList(),
        requiredItemIds: List<String> = emptyList(),
        tickedItemIds: Set<String> = emptySet(),
        bypassedAll: Boolean = false
    ) = GateInput(
        staff = staff,
        outlet = outlet,
        bypasses = bypasses,
        attendances = attendances,
        requiredItemIds = requiredItemIds,
        tickedItemIds = tickedItemIds,
        today = today,
        bypassedAll = bypassedAll
    )

    @Test
    fun `no attendance today blocks with attendance type`() {
        val state = GateEvaluator.evaluate(input())

        assertTrue(state.isBlocked)
        assertEquals(BlockType.ATTENDANCE, state.type)
        assertEquals("Menunggu kru absen hadir.", state.reason)
        assertNull(state.progress)
    }

    @Test
    fun `one crew checked in with no checklist items is not blocked`() {
        val state = GateEvaluator.evaluate(
            input(attendances = listOf(attendance("staff-1", "in")))
        )

        assertFalse(state.isBlocked)
    }

    @Test
    fun `all crew checked out blocks with closed type`() {
        val state = GateEvaluator.evaluate(
            input(
                attendances = listOf(
                    attendance("staff-1", "in", "2026-08-01T02:00:00+00:00"),
                    attendance("staff-1", "out", "2026-08-01T10:00:00+00:00")
                )
            )
        )

        assertTrue(state.isBlocked)
        assertEquals(BlockType.CLOSED, state.type)
        assertEquals(
            "Semua kru sudah absen pulang. Toko sudah tutup untuk hari ini.",
            state.reason
        )
    }

    @Test
    fun `one crew still in while another is out keeps store open`() {
        val state = GateEvaluator.evaluate(
            input(
                attendances = listOf(
                    attendance("staff-1", "in", "2026-08-01T02:00:00+00:00"),
                    attendance("staff-2", "in", "2026-08-01T02:10:00+00:00"),
                    attendance("staff-2", "out", "2026-08-01T10:00:00+00:00")
                )
            )
        )

        assertFalse(state.isBlocked)
    }

    @Test
    fun `partial checklist blocks with progress`() {
        val state = GateEvaluator.evaluate(
            input(
                attendances = listOf(attendance("staff-1", "in")),
                requiredItemIds = listOf("i1", "i2", "i3"),
                tickedItemIds = setOf("i1")
            )
        )

        assertTrue(state.isBlocked)
        assertEquals(BlockType.CHECKLIST, state.type)
        assertEquals("Checklist buka toko belum selesai.", state.reason)
        assertEquals(ChecklistProgress(total = 3, done = 1), state.progress)
    }

    @Test
    fun `complete checklist is not blocked`() {
        val state = GateEvaluator.evaluate(
            input(
                attendances = listOf(attendance("staff-1", "in")),
                requiredItemIds = listOf("i1", "i2"),
                tickedItemIds = setOf("i1", "i2", "i-lain")
            )
        )

        assertFalse(state.isBlocked)
        assertNull(state.progress)
    }

    @Test
    fun `approved bypass today unlocks even without attendance`() {
        val state = GateEvaluator.evaluate(
            input(bypasses = listOf(bypass("approved")))
        )

        assertFalse(state.isBlocked)
    }

    @Test
    fun `approved bypass from yesterday does not unlock`() {
        val state = GateEvaluator.evaluate(
            input(bypasses = listOf(bypass("approved", "2026-07-30T03:00:00+00:00")))
        )

        assertTrue(state.isBlocked)
        assertEquals(BlockType.ATTENDANCE, state.type)
    }

    @Test
    fun `pending bypass today is surfaced without unlocking`() {
        val state = GateEvaluator.evaluate(
            input(bypasses = listOf(bypass("pending")))
        )

        assertTrue(state.isBlocked)
        assertEquals(BypassStatus.PENDING, state.bypassStatus)
    }

    @Test
    fun `rejected bypass today is surfaced`() {
        val state = GateEvaluator.evaluate(
            input(bypasses = listOf(bypass("rejected")))
        )

        assertEquals(BypassStatus.REJECTED, state.bypassStatus)
    }

    @Test
    fun `inactive staff blocks with user type and reason`() {
        val state = GateEvaluator.evaluate(
            input(staff = staff(isActive = false, inactiveReason = "Melanggar SOP"))
        )

        assertTrue(state.isBlocked)
        assertEquals(BlockType.USER, state.type)
        assertEquals("Melanggar SOP", state.reason)
    }

    @Test
    fun `inactive staff without reason falls back to default copy`() {
        val state = GateEvaluator.evaluate(input(staff = staff(isActive = false)))

        assertEquals("Akun Anda dinonaktifkan oleh Admin.", state.reason)
    }

    @Test
    fun `inactive outlet blocks with outlet type and reason`() {
        val state = GateEvaluator.evaluate(
            input(outlet = outlet(isActive = false, inactiveReason = "Renovasi"))
        )

        assertTrue(state.isBlocked)
        assertEquals(BlockType.OUTLET, state.type)
        assertEquals("Renovasi", state.reason)
    }

    @Test
    fun `inactive outlet without reason falls back to default copy`() {
        val state = GateEvaluator.evaluate(input(outlet = outlet(isActive = false)))

        assertEquals(
            "Cabang tempat Anda bertugas sedang dinonaktifkan oleh Admin.",
            state.reason
        )
    }

    @Test
    fun `admin is never blocked`() {
        val state = GateEvaluator.evaluate(input(staff = staff(role = "admin")))

        assertFalse(state.isBlocked)
    }

    @Test
    fun `role outside crew and leader is not gated`() {
        val state = GateEvaluator.evaluate(input(staff = staff(role = "spv")))

        assertFalse(state.isBlocked)
    }

    @Test
    fun `leader is gated like crew`() {
        val state = GateEvaluator.evaluate(input(staff = staff(role = "leader")))

        assertTrue(state.isBlocked)
        assertEquals(BlockType.ATTENDANCE, state.type)
    }

    @Test
    fun `yesterday attendance is ignored`() {
        val state = GateEvaluator.evaluate(
            input(attendances = listOf(attendance("staff-1", "in", "2026-07-30T03:00:00+00:00")))
        )

        assertTrue(state.isBlocked)
        assertEquals(BlockType.ATTENDANCE, state.type)
    }

    @Test
    fun `local bypass all unlocks immediately`() {
        val state = GateEvaluator.evaluate(input(bypassedAll = true))

        assertFalse(state.isBlocked)
    }

    @Test
    fun `missing staff profile is not blocked`() {
        val state = GateEvaluator.evaluate(input(staff = null))

        assertFalse(state.isBlocked)
    }

    @Test
    fun `mixed timestamp formats still order chronologically`() {
        val state = GateEvaluator.evaluate(
            input(
                attendances = listOf(
                    attendance("staff-1", "in", "2026-08-01T05:00:00+00:00"),
                    attendance("staff-1", "out", "2026-08-01 10:00:00+00")
                )
            )
        )

        assertTrue(state.isBlocked)
        assertEquals(BlockType.CLOSED, state.type)
    }

    @Test
    fun `mixed format bypass picks the latest status`() {
        val state = GateEvaluator.evaluate(
            input(
                bypasses = listOf(
                    bypass("rejected", "2026-08-01T02:00:00+00:00"),
                    bypass("pending", "2026-08-01 09:00:00+00")
                )
            )
        )

        assertEquals(BypassStatus.PENDING, state.bypassStatus)
    }
}
