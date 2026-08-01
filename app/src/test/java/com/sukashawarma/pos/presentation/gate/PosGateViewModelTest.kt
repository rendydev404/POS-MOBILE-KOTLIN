package com.sukashawarma.pos.presentation.gate

import com.sukashawarma.pos.data.local.InMemoryBypassStore
import com.sukashawarma.pos.data.remote.SupabaseApi
import com.sukashawarma.pos.data.remote.dto.AttendanceDto
import com.sukashawarma.pos.data.remote.dto.BypassRequestDto
import com.sukashawarma.pos.data.remote.dto.ChecklistCategoryDto
import com.sukashawarma.pos.data.remote.dto.ChecklistItemDto
import com.sukashawarma.pos.data.remote.dto.CreateBypassRequestPayload
import com.sukashawarma.pos.data.remote.dto.DailyChecklistRecordDto
import com.sukashawarma.pos.data.remote.dto.DailyChecklistTickDto
import com.sukashawarma.pos.data.remote.dto.OutletDto
import com.sukashawarma.pos.data.remote.dto.StaffProfileDto
import com.sukashawarma.pos.domain.gate.BlockType
import com.sukashawarma.pos.domain.gate.JakartaTime
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class PosGateViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val outletId = "outlet-1"
    private val staffId = "staff-1"
    private val nowIso = "${JakartaTime.dateString(JakartaTime.today())}T03:00:00+00:00"

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun api(
        staffRole: String = "crew",
        staffActive: Boolean = true,
        outletActive: Boolean = true,
        attendances: List<AttendanceDto> = emptyList(),
        bypasses: List<BypassRequestDto> = emptyList(),
        requiredItems: List<String> = emptyList(),
        ticked: List<String> = emptyList()
    ): SupabaseApi {
        val api = mockk<SupabaseApi>()

        coEvery { api.getStaffById(any(), any()) } returns Response.success(
            listOf(
                StaffProfileDto(
                    id = staffId,
                    username = "kasir1",
                    name = "Budi",
                    role = staffRole,
                    outletId = outletId,
                    isActive = staffActive,
                    inactiveReason = null
                )
            )
        )
        coEvery { api.getOutletById(any(), any()) } returns Response.success(
            listOf(
                OutletDto(
                    id = outletId,
                    slug = "bnr",
                    name = "Outlet BNR",
                    address = null,
                    phone = "081234567890",
                    type = "outlet",
                    is_active = outletActive,
                    inactiveReason = null
                )
            )
        )
        coEvery { api.getAttendanceForDay(any(), any(), any(), any(), any()) } returns
            Response.success(attendances)
        coEvery { api.getBypassRequestsForDay(any(), any(), any(), any()) } returns
            Response.success(bypasses)
        coEvery { api.getRequiredOpeningChecklist(any(), any(), any()) } returns
            Response.success(
                listOf(
                    ChecklistCategoryDto(
                        id = "cat-1",
                        checklistItems = requiredItems.map { ChecklistItemDto(it, true) }
                    )
                )
            )
        coEvery { api.getChecklistRecordForDay(any(), any(), any(), any()) } returns
            Response.success(
                listOf(
                    DailyChecklistRecordDto(
                        id = "rec-1",
                        outletId = outletId,
                        staffId = staffId,
                        date = JakartaTime.dateString(JakartaTime.today())
                    )
                )
            )
        coEvery { api.getTicksForRecord(any(), any()) } returns Response.success(
            ticked.map { DailyChecklistTickDto(itemId = it, recordId = "rec-1") }
        )
        return api
    }

    @Test
    fun `blocks on attendance when nobody checked in`() = runTest(dispatcher) {
        val vm = PosGateViewModel(api(), InMemoryBypassStore())

        vm.setSession(outletId, staffId)
        advanceUntilIdle()

        assertTrue(vm.state.value.isBlocked)
        assertEquals(BlockType.ATTENDANCE, vm.state.value.type)
    }

    @Test
    fun `unblocks when a crew is checked in and checklist is done`() = runTest(dispatcher) {
        val vm = PosGateViewModel(
            api(
                attendances = listOf(
                    AttendanceDto("a1", staffId, outletId, "in", nowIso)
                ),
                requiredItems = listOf("i1"),
                ticked = listOf("i1")
            ),
            InMemoryBypassStore()
        )

        vm.setSession(outletId, staffId)
        advanceUntilIdle()

        assertFalse(vm.state.value.isBlocked)
    }

    @Test
    fun `blocks on checklist with progress`() = runTest(dispatcher) {
        val vm = PosGateViewModel(
            api(
                attendances = listOf(
                    AttendanceDto("a1", staffId, outletId, "in", nowIso)
                ),
                requiredItems = listOf("i1", "i2"),
                ticked = listOf("i1")
            ),
            InMemoryBypassStore()
        )

        vm.setSession(outletId, staffId)
        advanceUntilIdle()

        assertEquals(BlockType.CHECKLIST, vm.state.value.type)
        assertEquals(2, vm.state.value.progress!!.total)
        assertEquals(1, vm.state.value.progress!!.done)
    }

    @Test
    fun `api failure fails open`() = runTest(dispatcher) {
        val api = mockk<SupabaseApi>()
        coEvery { api.getStaffById(any(), any()) } throws RuntimeException("network down")
        val vm = PosGateViewModel(api, InMemoryBypassStore())

        vm.setSession(outletId, staffId)
        advanceUntilIdle()

        assertFalse(vm.state.value.isBlocked)
    }

    @Test
    fun `local bypass unlocks without network change`() = runTest(dispatcher) {
        val store = InMemoryBypassStore()
        val vm = PosGateViewModel(api(), store)
        vm.setSession(outletId, staffId)
        advanceUntilIdle()
        assertTrue(vm.state.value.isBlocked)

        vm.markBypassed()
        advanceUntilIdle()

        assertFalse(vm.state.value.isBlocked)
        assertTrue(store.isBypassed())
    }

    @Test
    fun `spv phone is normalised to country code`() = runTest(dispatcher) {
        val vm = PosGateViewModel(api(), InMemoryBypassStore())

        vm.setSession(outletId, staffId)
        advanceUntilIdle()

        assertEquals("6281234567890", vm.spvPhone.value)
    }

    @Test
    fun `request bypass sends staff name and builds whatsapp text`() = runTest(dispatcher) {
        val api = api()
        val payload = slot<CreateBypassRequestPayload>()
        coEvery { api.createBypassRequest(capture(payload)) } returns Response.success(
            listOf(
                BypassRequestDto(
                    id = "bp-1",
                    outletId = outletId,
                    staffId = null,
                    requestType = "attendance",
                    status = "pending",
                    reason = "Absensi error",
                    createdAt = nowIso
                )
            )
        )
        val vm = PosGateViewModel(api, InMemoryBypassStore())
        vm.setSession(outletId, staffId)
        advanceUntilIdle()

        var waText = ""
        vm.requestBypass("Absensi error") { waText = it }
        advanceUntilIdle()

        assertEquals("Budi", payload.captured.requestedByName)
        assertEquals("attendance", payload.captured.requestType)
        assertEquals(outletId, payload.captured.outletId)
        assertTrue(waText.contains("Kasir: Budi"))
        assertTrue(waText.contains("Alasan: Absensi error"))
        assertTrue(
            waText.contains("https://app.sukashawarma.com/api/bypass/approve?id=bp-1")
        )
        coVerify(exactly = 1) { api.createBypassRequest(any()) }
    }
}
