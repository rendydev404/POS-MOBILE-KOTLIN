package com.sukashawarma.pos.presentation.shift

import com.sukashawarma.pos.data.remote.dto.ShiftDto
import org.junit.Assert.assertEquals
import org.junit.Test

class PettyCashOpeningBalanceTest {

    @Test
    fun `next shift follows actual petty cash counted at latest closing`() {
        val lastClosedShift = closedShift(
            actualEndingPettyCash = 37_500.0,
            expectedEndingPettyCash = 42_000.0,
            startingPettyCash = 50_000.0
        )

        assertEquals(37_500.0, lastClosedShift.carryOverPettyCash(), 0.0)
    }

    @Test
    fun `actual closing balance of zero remains zero`() {
        val lastClosedShift = closedShift(
            actualEndingPettyCash = 0.0,
            expectedEndingPettyCash = 42_000.0,
            startingPettyCash = 50_000.0
        )

        assertEquals(0.0, lastClosedShift.carryOverPettyCash(), 0.0)
    }

    @Test
    fun `legacy closing without actual balance falls back to expected then starting`() {
        val withExpected = closedShift(
            actualEndingPettyCash = null,
            expectedEndingPettyCash = 42_000.0,
            startingPettyCash = 50_000.0
        )
        val withStartingOnly = closedShift(
            actualEndingPettyCash = null,
            expectedEndingPettyCash = null,
            startingPettyCash = 50_000.0
        )

        assertEquals(42_000.0, withExpected.carryOverPettyCash(), 0.0)
        assertEquals(50_000.0, withStartingOnly.carryOverPettyCash(), 0.0)
    }

    @Test
    fun `admin snapshot opening balance is used when no shift is active`() {
        val snapshot = PettyCashSnapshotDto(
            currentBalance = 125_000.0,
            openingBalance = 125_000.0,
            pendingAdjustmentId = "adjustment-1"
        )

        assertEquals(125_000.0, snapshot.openingPettyCash(), 0.0)
    }

    @Test
    fun `snapshot current balance is fallback when opening balance is absent`() {
        val snapshot = PettyCashSnapshotDto(currentBalance = 80_000.0)

        assertEquals(80_000.0, snapshot.openingPettyCash(), 0.0)
    }

    @Test
    fun `empty snapshot falls back to latest physical closing balance`() {
        val emptySnapshot = PettyCashSnapshotDto(currentBalance = 0.0)
        val lastClosedShift = closedShift(
            actualEndingPettyCash = 375_000.0,
            expectedEndingPettyCash = 375_000.0,
            startingPettyCash = 375_000.0
        )

        assertEquals(375_000.0, resolveOpeningPettyCash(emptySnapshot, lastClosedShift), 0.0)
    }

    @Test
    fun `explicit zero closing balance is not replaced by snapshot fallback`() {
        val emptySnapshot = PettyCashSnapshotDto(currentBalance = 0.0)
        val lastClosedShift = closedShift(
            actualEndingPettyCash = 0.0,
            expectedEndingPettyCash = 25_000.0,
            startingPettyCash = 25_000.0
        )

        assertEquals(0.0, resolveOpeningPettyCash(emptySnapshot, lastClosedShift), 0.0)
    }

    private fun closedShift(
        actualEndingPettyCash: Double?,
        expectedEndingPettyCash: Double?,
        startingPettyCash: Double?
    ) = ShiftDto(
        id = "shift-yesterday",
        outletId = "outlet-1",
        staffId = "staff-1",
        startTime = "2026-08-25T01:00:00Z",
        endTime = "2026-08-25T15:00:00Z",
        startingCash = 0.0,
        startingPettyCash = startingPettyCash,
        actualEndingCash = null,
        expectedEndingCash = null,
        actualEndingPettyCash = actualEndingPettyCash,
        expectedEndingPettyCash = expectedEndingPettyCash,
        variance = null,
        status = "closed"
    )
}
