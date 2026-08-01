package com.sukashawarma.pos.data.remote.dto

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GateDtoTest {

    private val gson = Gson()

    @Test
    fun `staff profile parses inactive reason`() {
        val json = """
            {
              "id": "staff-1",
              "username": "kasir1",
              "name": "Budi",
              "role": "crew",
              "outlet_id": "outlet-1",
              "is_active": false,
              "inactive_reason": "Melanggar SOP"
            }
        """.trimIndent()

        val dto = gson.fromJson(json, StaffProfileDto::class.java)

        assertEquals("Budi", dto.name)
        assertFalse(dto.isActive!!)
        assertEquals("Melanggar SOP", dto.inactiveReason)
    }

    @Test
    fun `outlet parses inactive reason`() {
        val json = """
            {
              "id": "outlet-1",
              "slug": "bnr",
              "name": "Outlet BNR",
              "address": null,
              "phone": "081234567890",
              "type": "outlet",
              "is_active": false,
              "inactive_reason": "Renovasi"
            }
        """.trimIndent()

        val dto = gson.fromJson(json, OutletDto::class.java)

        assertFalse(dto.is_active!!)
        assertEquals("Renovasi", dto.inactiveReason)
    }

    @Test
    fun `bypass request parses reason`() {
        val json = """
            {
              "id": "bp-1",
              "outlet_id": "outlet-1",
              "staff_id": null,
              "request_type": "attendance",
              "status": "pending",
              "reason": "Absensi error",
              "created_at": "2026-08-01T02:00:00+00:00"
            }
        """.trimIndent()

        val dto = gson.fromJson(json, BypassRequestDto::class.java)

        assertEquals("Absensi error", dto.reason)
        assertEquals("pending", dto.status)
    }
}
