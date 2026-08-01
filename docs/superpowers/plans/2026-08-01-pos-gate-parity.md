# POS Gate Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Menyamakan sistem blokir POS kasir native dengan versi web, sehingga POS terbuka otomatis secara realtime saat kru absen hadir lalu beralih ke state checklist, dengan lima tipe blokir dan UI yang identik.

**Architecture:** Logika gate diekstrak jadi satu fungsi murni `GateEvaluator.evaluate(GateInput): GateState` yang bisa diuji tanpa jaringan maupun Android. `PosGateViewModel` hanya bertugas mengambil data dari `SupabaseApi`, menyusun `GateInput`, dan menyimpan hasilnya. Sinyal perubahan datang dari WebSocket Supabase Realtime lewat satu event `GlobalEventBus.gateRefreshEvent`; tidak ada polling periodik. `BlockedOverlay` adalah composable murni yang hanya membaca `GateState`.

**Tech Stack:** Kotlin, Jetpack Compose (Material3, compose-bom 2024.02.00), Retrofit + PostgREST, OkHttp WebSocket, JUnit4 + MockK 1.13.9 + kotlinx-coroutines-test 1.7.3.

**Spec:** `docs/superpowers/specs/2026-08-01-pos-gate-parity-design.md`

**Referensi web (read-only, jangan diubah):**
`C:\Users\Creator MPB\OneDrive\Desktop\New folder\DIGITALISASI-SS-PROJECT\apps\pos-kasir\components\GlobalBlockerMount.tsx` dan `BlockedOverlay.tsx`

## Global Constraints

- Package root: `com.sukashawarma.pos`. Semua file baru berada di bawah `app/src/main/java/com/sukashawarma/pos/`.
- Timezone gate selalu `ZoneId.of("Asia/Jakarta")`. Dilarang memakai `ZoneId.systemDefault()`, `SimpleDateFormat`, atau `Date()` di jalur gate.
- Dilarang menambahkan timer/polling periodik (`delay()` berulang, `Timer`, `Handler.postDelayed` berulang) di jalur gate. Re-check hanya dipicu event realtime, reconnect WebSocket, dan `ON_RESUME`.
- Fail-open: exception apa pun saat evaluasi berarti `isBlocked = false`.
- Semua teks yang terlihat pengguna berbahasa Indonesia dan disalin verbatim dari versi web sesuai tabel di tiap task.
- Setiap `@QueryMap` yang dikirim ke Retrofit dilarang memuat key yang sudah dideklarasikan sebagai `@Query` bernama pada fungsi yang sama.
- Test unit dijalankan dengan `./gradlew testDebugUnitTest`. Di Windows PowerShell gunakan `.\gradlew.bat testDebugUnitTest`.
- Commit di akhir setiap task, dengan pesan persis seperti yang tertulis di step commit.

## File Structure

| File | Tanggung jawab | Task |
| --- | --- | --- |
| `data/remote/dto/SupabaseDtos.kt` | Tambah `inactive_reason` ke dua DTO | 1 |
| `data/remote/SupabaseApi.kt` | Endpoint gate tanpa parameter dobel | 1 |
| `domain/gate/GateModels.kt` | `BlockType`, `BypassStatus`, `ChecklistProgress`, `GateState`, `GateInput` | 2 |
| `domain/gate/JakartaTime.kt` | Perhitungan hari & rentang waktu Asia/Jakarta | 2 |
| `domain/gate/GateEvaluator.kt` | Fungsi murni evaluasi gate | 3 |
| `data/local/BypassStore.kt` | Persistensi tipe bypass (setara `sessionStorage`) | 4 |
| `presentation/gate/PosGateViewModel.kt` | Ambil data, susun input, simpan state, kirim bypass | 5 |
| `data/remote/GlobalEventBus.kt` | Event `gateRefreshEvent` | 6 |
| `data/remote/realtime/OrderRealtimeManager.kt` | Subscription 5 tabel gate | 6 |
| `data/remote/realtime/POSRealtimeService.kt` | Map event tabel gate ke `gateRefreshEvent` | 6 |
| `presentation/theme/Color.kt` | Warna Tailwind yang belum ada | 7 |
| `presentation/gate/BlockedOverlay.kt` | UI overlay lima tipe | 7 |
| `presentation/MainActivity.kt` | Wiring `staffId`, lifecycle resume, logout | 8 |
| `presentation/attendance/` (dihapus) | Digantikan `presentation/gate/` | 8 |

## Urutan Paralel

Task dapat dikerjakan bersamaan dalam gelombang berikut:

- **Gelombang A (paralel):** Task 1, Task 2, Task 4, Task 6
- **Gelombang B (paralel):** Task 3 (butuh 2), Task 7 (butuh 2)
- **Gelombang C:** Task 5 (butuh 1, 2, 3, 4)
- **Gelombang D:** Task 8 (butuh 5, 6, 7)

---

### Task 1: DTO dan endpoint gate

**Files:**
- Modify: `app/src/main/java/com/sukashawarma/pos/data/remote/dto/SupabaseDtos.kt:5-22`
- Modify: `app/src/main/java/com/sukashawarma/pos/data/remote/SupabaseApi.kt:17-30, 218-262`
- Test: `app/src/test/java/com/sukashawarma/pos/data/remote/dto/GateDtoTest.kt`

**Interfaces:**
- Consumes: tidak ada.
- Produces:
  - `StaffProfileDto(id: String, username: String?, name: String?, role: String?, outletId: String?, isActive: Boolean?, inactiveReason: String?)`
  - `OutletDto(id, slug, name, address, phone, type, is_active, inactiveReason: String?)`
  - `SupabaseApi.getAttendanceForDay(outletId: String, from: String, to: String): Response<List<AttendanceDto>>`
  - `SupabaseApi.getBypassRequestsForDay(outletId: String, from: String): Response<List<BypassRequestDto>>`
  - `SupabaseApi.getRequiredOpeningChecklist(outletId: String): Response<List<ChecklistCategoryDto>>`
  - `SupabaseApi.getChecklistRecordForDay(outletId: String, date: String): Response<List<DailyChecklistRecordDto>>`
  - `SupabaseApi.getTicksForRecord(recordId: String): Response<List<DailyChecklistTickDto>>`
  - `BypassRequestDto` mendapat field `reason: String?` (dipakai Task 5 saat membaca balik).

- [ ] **Step 1: Tulis test yang gagal untuk parsing DTO**

Buat `app/src/test/java/com/sukashawarma/pos/data/remote/dto/GateDtoTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Jalankan test dan pastikan gagal**

```bash
./gradlew testDebugUnitTest --tests "com.sukashawarma.pos.data.remote.dto.GateDtoTest"
```

Expected: FAIL — kompilasi error "unresolved reference: inactiveReason".

- [ ] **Step 3: Tambahkan field ke DTO**

Di `SupabaseDtos.kt`, ganti `StaffProfileDto` dan `OutletDto`:

```kotlin
data class StaffProfileDto(
    val id: String,
    val username: String?,
    val name: String?,
    val role: String?,
    @SerializedName("outlet_id") val outletId: String?,
    @SerializedName("is_active") val isActive: Boolean?,
    @SerializedName("inactive_reason") val inactiveReason: String? = null
)

data class OutletDto(
    val id: String,
    val slug: String,
    val name: String,
    val address: String?,
    val phone: String?,
    val type: String?,
    val is_active: Boolean?,
    @SerializedName("inactive_reason") val inactiveReason: String? = null
)
```

Lalu tambahkan `reason` ke `BypassRequestDto`:

```kotlin
data class BypassRequestDto(
    val id: String,
    @SerializedName("outlet_id") val outletId: String,
    @SerializedName("staff_id") val staffId: String? = null,
    @SerializedName("request_type") val requestType: String? = null,
    val status: String,
    val reason: String? = null,
    @SerializedName("created_at") val createdAt: String
)
```

- [ ] **Step 4: Jalankan test dan pastikan lulus**

```bash
./gradlew testDebugUnitTest --tests "com.sukashawarma.pos.data.remote.dto.GateDtoTest"
```

Expected: PASS, 3 test.

- [ ] **Step 5: Ganti endpoint gate di SupabaseApi**

Di `SupabaseApi.kt`, hapus lima fungsi lama `getAttendance`, `getDailyChecklistRecords`, `getChecklistCategories`, `getDailyChecklistTicks`, dan `getBypassRequests`, lalu ganti dengan:

```kotlin
    // --- Gate kasir. Semua filter eksplisit; tidak ada QueryMap agar tidak
    // ada parameter select/order yang terkirim dua kali. ---

    @GET("rest/v1/attendance")
    suspend fun getAttendanceForDay(
        @Query("outlet_id") outletId: String,      // "eq.<uuid>"
        @Query("ts_server") from: String,          // "gte.<iso8601>"
        @Query("ts_server") to: String,            // "lte.<iso8601>"
        @Query("select") select: String = "outlet_staff_id,type,ts_server",
        @Query("order") order: String = "ts_server.asc"
    ): Response<List<AttendanceDto>>

    @GET("rest/v1/bypass_requests")
    suspend fun getBypassRequestsForDay(
        @Query("outlet_id") outletId: String,      // "eq.<uuid>"
        @Query("created_at") from: String,         // "gte.<iso8601>"
        @Query("select") select: String = "id,outlet_id,staff_id,request_type,status,reason,created_at",
        @Query("order") order: String = "created_at.desc"
    ): Response<List<BypassRequestDto>>

    @GET("rest/v1/checklist_categories")
    suspend fun getRequiredOpeningChecklist(
        @Query("outlet_id") outletId: String,      // "eq.<uuid>"
        @Query("phase") phase: String = "eq.buka",
        @Query("select") select: String = "id,checklist_items(id,is_required)"
    ): Response<List<ChecklistCategoryDto>>

    @GET("rest/v1/daily_checklist_records")
    suspend fun getChecklistRecordForDay(
        @Query("outlet_id") outletId: String,      // "eq.<uuid>"
        @Query("date") date: String,               // "eq.YYYY-MM-DD"
        @Query("select") select: String = "id,outlet_id,staff_id,date",
        @Query("limit") limit: String = "1"
    ): Response<List<DailyChecklistRecordDto>>

    @GET("rest/v1/daily_checklist_ticks")
    suspend fun getTicksForRecord(
        @Query("record_id") recordId: String,      // "eq.<uuid>"
        @Query("select") select: String = "item_id,record_id"
    ): Response<List<DailyChecklistTickDto>>
```

Biarkan `getStaffById`, `getOutletById`, dan `createBypassRequest` apa adanya.

- [ ] **Step 6: Pastikan tidak ada pemanggil lama yang tertinggal**

```bash
grep -rn "getAttendance(\|getBypassRequests(\|getChecklistCategories(\|getDailyChecklistRecords(\|getDailyChecklistTicks(" app/src/main/java app/src/test/java
```

Expected: hanya `AttendanceViewModel.kt` yang muncul.

- [ ] **Step 6b: Hapus pemanggil lama agar main source set tetap kompilasi**

Test unit ikut mengompilasi main source set, jadi pemanggil basi ini memblokir test **semua** task berikutnya, bukan hanya task ini. Karena itu penghapusannya dilakukan di sini, bukan di Task 8.

```bash
git rm app/src/main/java/com/sukashawarma/pos/presentation/attendance/AttendanceViewModel.kt app/src/main/java/com/sukashawarma/pos/presentation/attendance/AttendanceOverlay.kt
```

Lalu buang jejaknya dari `MainActivity.kt` — empat suntingan, tanpa mengganti apa pun dengan UI baru (overlay baru dipasang di Task 8):

1. Hapus dua baris import:
   ```kotlin
   import com.sukashawarma.pos.presentation.attendance.AttendanceViewModel
   import com.sukashawarma.pos.presentation.attendance.AttendanceOverlay
   ```
2. Hapus deklarasi properti:
   ```kotlin
   private val attendanceViewModel: AttendanceViewModel by viewModels()
   ```
3. Hapus baris di dalam `LaunchedEffect(activeSession)`:
   ```kotlin
   attendanceViewModel.setSession(session.outletId, session.username)
   ```
4. Hapus pemasangan overlay beserta komentar di atasnya:
   ```kotlin
   // Add overlay on top of EVERYTHING if locked
   AttendanceOverlay(viewModel = attendanceViewModel)
   ```

Untuk sementara aplikasi berjalan tanpa gate sama sekali. Itu disengaja; Task 8 memasang gate baru.

- [ ] **Step 6c: Jalankan test dan pastikan seluruh suite hijau**

```bash
./gradlew testDebugUnitTest
```

Expected: PASS, termasuk `GateDtoTest` dan suite lama (`MenuRepositoryTest`, `MenuRulesTest`, `MenuItemDtoTest`).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/sukashawarma/pos/data/remote/dto/SupabaseDtos.kt app/src/main/java/com/sukashawarma/pos/data/remote/SupabaseApi.kt app/src/main/java/com/sukashawarma/pos/presentation/MainActivity.kt app/src/test/java/com/sukashawarma/pos/data/remote/dto/GateDtoTest.kt
git commit -m "feat(gate): tambah inactive_reason dan endpoint gate tanpa parameter dobel"
```

---

### Task 2: Model gate dan perhitungan waktu Jakarta

**Files:**
- Create: `app/src/main/java/com/sukashawarma/pos/domain/gate/GateModels.kt`
- Create: `app/src/main/java/com/sukashawarma/pos/domain/gate/JakartaTime.kt`
- Test: `app/src/test/java/com/sukashawarma/pos/domain/gate/JakartaTimeTest.kt`

**Interfaces:**
- Consumes: tidak ada.
- Produces:
  - `enum class BlockType { USER, OUTLET, ATTENDANCE, CHECKLIST, CLOSED }`
  - `enum class BypassStatus { PENDING, REJECTED }`
  - `data class ChecklistProgress(val total: Int, val done: Int)`
  - `data class GateState(isBlocked, type, reason, progress, bypassStatus)`
  - `data class GateInput(staff, outlet, bypasses, attendances, requiredItemIds, tickedItemIds, today, bypassedAll)`
  - `JakartaTime.ZONE: ZoneId`
  - `JakartaTime.today(): LocalDate`
  - `JakartaTime.dateString(day: LocalDate): String`
  - `JakartaTime.startOfDayIso(day: LocalDate): String`
  - `JakartaTime.endOfDayIso(day: LocalDate): String`
  - `JakartaTime.isOnDay(timestamp: String?, day: LocalDate): Boolean`

- [ ] **Step 1: Tulis test yang gagal untuk JakartaTime**

Buat `app/src/test/java/com/sukashawarma/pos/domain/gate/JakartaTimeTest.kt`:

```kotlin
package com.sukashawarma.pos.domain.gate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class JakartaTimeTest {

    private val day = LocalDate.of(2026, 8, 1)

    @Test
    fun `dateString formats as yyyy-MM-dd`() {
        assertEquals("2026-08-01", JakartaTime.dateString(day))
    }

    @Test
    fun `day bounds use plus seven offset`() {
        assertEquals("2026-08-01T00:00:00+07:00", JakartaTime.startOfDayIso(day))
        assertEquals("2026-08-01T23:59:59+07:00", JakartaTime.endOfDayIso(day))
    }

    @Test
    fun `utc timestamp inside jakarta day is matched`() {
        // 2026-07-31T17:00:00Z == 2026-08-01T00:00:00+07:00
        assertTrue(JakartaTime.isOnDay("2026-07-31T17:00:00Z", day))
    }

    @Test
    fun `utc timestamp before jakarta midnight belongs to previous day`() {
        // 2026-07-31T16:59:59Z == 2026-07-31T23:59:59+07:00
        assertFalse(JakartaTime.isOnDay("2026-07-31T16:59:59Z", day))
    }

    @Test
    fun `postgrest two digit offset is parsed`() {
        assertTrue(JakartaTime.isOnDay("2026-08-01T03:00:00+00", day))
    }

    @Test
    fun `postgrest space separated timestamp is parsed`() {
        assertTrue(JakartaTime.isOnDay("2026-08-01 03:00:00+00:00", day))
    }

    @Test
    fun `timestamp with microseconds is parsed`() {
        assertTrue(JakartaTime.isOnDay("2026-08-01T03:00:00.123456+00:00", day))
    }

    @Test
    fun `timestamp without offset is treated as utc`() {
        assertTrue(JakartaTime.isOnDay("2026-08-01T03:00:00", day))
    }

    @Test
    fun `null and garbage timestamps do not match`() {
        assertFalse(JakartaTime.isOnDay(null, day))
        assertFalse(JakartaTime.isOnDay("bukan tanggal", day))
    }
}
```

- [ ] **Step 2: Jalankan test dan pastikan gagal**

```bash
./gradlew testDebugUnitTest --tests "com.sukashawarma.pos.domain.gate.JakartaTimeTest"
```

Expected: FAIL — "unresolved reference: JakartaTime".

- [ ] **Step 3: Implementasikan JakartaTime**

Buat `app/src/main/java/com/sukashawarma/pos/domain/gate/JakartaTime.kt`:

```kotlin
package com.sukashawarma.pos.domain.gate

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Semua perhitungan hari untuk gate kasir memakai Asia/Jakarta, menyamai
 * `Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Jakarta' })` di versi web.
 */
object JakartaTime {

    val ZONE: ZoneId = ZoneId.of("Asia/Jakarta")

    fun today(): LocalDate = LocalDate.now(ZONE)

    fun dateString(day: LocalDate): String = day.format(DateTimeFormatter.ISO_LOCAL_DATE)

    fun startOfDayIso(day: LocalDate): String = "${dateString(day)}T00:00:00+07:00"

    fun endOfDayIso(day: LocalDate): String = "${dateString(day)}T23:59:59+07:00"

    /** True bila [timestamp] jatuh pada [day] menurut Asia/Jakarta. */
    fun isOnDay(timestamp: String?, day: LocalDate): Boolean {
        val instant = parseInstant(timestamp) ?: return false
        return instant.atZone(ZONE).toLocalDate() == day
    }

    private fun parseInstant(raw: String?): Instant? {
        if (raw.isNullOrBlank()) return null
        // PostgREST bisa mengembalikan "2026-08-01 03:00:00+00" — normalkan
        // pemisah tanggal/jam dan offset dua digit menjadi bentuk ISO penuh.
        var value = raw.trim().replace(' ', 'T')
        if (Regex("[+-]\\d{2}$").containsMatchIn(value)) {
            value += ":00"
        }
        return try {
            OffsetDateTime.parse(value).toInstant()
        } catch (e: Exception) {
            try {
                // Tanpa offset: perlakukan sebagai UTC, sama seperti web.
                LocalDateTime.parse(value).toInstant(ZoneOffset.UTC)
            } catch (e2: Exception) {
                null
            }
        }
    }
}
```

- [ ] **Step 4: Jalankan test dan pastikan lulus**

```bash
./gradlew testDebugUnitTest --tests "com.sukashawarma.pos.domain.gate.JakartaTimeTest"
```

Expected: PASS, 9 test.

- [ ] **Step 5: Buat model gate**

Buat `app/src/main/java/com/sukashawarma/pos/domain/gate/GateModels.kt`:

```kotlin
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
```

- [ ] **Step 6: Pastikan modul kompilasi**

```bash
./gradlew compileDebugKotlin
```

Expected: hanya error dari `AttendanceViewModel.kt` bila Task 1 sudah mendarat; tidak boleh ada error dari file `domain/gate/`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/sukashawarma/pos/domain/gate/ app/src/test/java/com/sukashawarma/pos/domain/gate/JakartaTimeTest.kt
git commit -m "feat(gate): model gate dan perhitungan hari Asia/Jakarta"
```

---

### Task 3: Evaluator gate

**Files:**
- Create: `app/src/main/java/com/sukashawarma/pos/domain/gate/GateEvaluator.kt`
- Test: `app/src/test/java/com/sukashawarma/pos/domain/gate/GateEvaluatorTest.kt`

**Interfaces:**
- Consumes: `GateInput`, `GateState`, `BlockType`, `BypassStatus`, `ChecklistProgress`, `JakartaTime.isOnDay` (Task 2); `StaffProfileDto`, `OutletDto`, `AttendanceDto`, `BypassRequestDto` (Task 1).
- Produces: `GateEvaluator.evaluate(input: GateInput): GateState`

**Catatan parity:** urutan cabang harus persis mengikuti `checkStatus()` lalu `checkKasirGate()` di `GlobalBlockerMount.tsx`. Web hanya menggerbangi role `crew` dan `leader`; role lain (termasuk `kasir` dan `spv`) tidak diblokir. Pertahankan aturan itu apa adanya — jangan "diperbaiki" jadi lebih luas.

- [ ] **Step 1: Tulis test yang gagal**

Buat `app/src/test/java/com/sukashawarma/pos/domain/gate/GateEvaluatorTest.kt`:

```kotlin
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
}
```

- [ ] **Step 2: Jalankan test dan pastikan gagal**

```bash
./gradlew testDebugUnitTest --tests "com.sukashawarma.pos.domain.gate.GateEvaluatorTest"
```

Expected: FAIL — "unresolved reference: GateEvaluator".

- [ ] **Step 3: Implementasikan evaluator**

Buat `app/src/main/java/com/sukashawarma/pos/domain/gate/GateEvaluator.kt`:

```kotlin
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
```

- [ ] **Step 4: Jalankan test dan pastikan lulus**

```bash
./gradlew testDebugUnitTest --tests "com.sukashawarma.pos.domain.gate.GateEvaluatorTest"
```

Expected: PASS, 20 test.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/sukashawarma/pos/domain/gate/GateEvaluator.kt app/src/test/java/com/sukashawarma/pos/domain/gate/GateEvaluatorTest.kt
git commit -m "feat(gate): evaluator gate kasir lima tipe blokir"
```

---

### Task 4: Penyimpanan bypass lokal

**Files:**
- Create: `app/src/main/java/com/sukashawarma/pos/data/local/BypassStore.kt`
- Modify: `app/src/main/java/com/sukashawarma/pos/data/local/SessionPrefs.kt:34-42`
- Test: `app/src/test/java/com/sukashawarma/pos/data/local/BypassStoreTest.kt`

**Interfaces:**
- Consumes: `JakartaTime.today()`, `JakartaTime.dateString()` (Task 2).
- Produces:
  - `interface BypassStore { fun isBypassed(): Boolean; fun markBypassed(); fun clear() }`
  - `class InMemoryBypassStore : BypassStore` (dipakai test)
  - `object SessionBypassStore : BypassStore` (didukung `SessionPrefs`)
  - `SessionPrefs.setBypassedDate(date: String?)` dan `SessionPrefs.getBypassedDate(): String?`

**Catatan:** web memakai `sessionStorage`, yang mati bersama tab. Proses aplikasi Android hidup jauh lebih lama, jadi analog yang setia adalah menyimpan **tanggal Jakarta** saat bypass diberikan dan menganggapnya kedaluwarsa begitu tanggalnya berganti. Tanpa ini, satu bypass bisa membuka POS selamanya.

- [ ] **Step 1: Tulis test yang gagal**

Buat `app/src/test/java/com/sukashawarma/pos/data/local/BypassStoreTest.kt`:

```kotlin
package com.sukashawarma.pos.data.local

import com.sukashawarma.pos.domain.gate.JakartaTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BypassStoreTest {

    @Test
    fun `fresh store is not bypassed`() {
        assertFalse(InMemoryBypassStore().isBypassed())
    }

    @Test
    fun `marking makes it bypassed`() {
        val store = InMemoryBypassStore()

        store.markBypassed()

        assertTrue(store.isBypassed())
    }

    @Test
    fun `clear resets the store`() {
        val store = InMemoryBypassStore()
        store.markBypassed()

        store.clear()

        assertFalse(store.isBypassed())
    }

    @Test
    fun `bypass from another day is ignored`() {
        val store = InMemoryBypassStore(storedDate = "2020-01-01")

        assertFalse(store.isBypassed())
    }

    @Test
    fun `marking stores today in jakarta`() {
        val store = InMemoryBypassStore()

        store.markBypassed()

        assertTrue(store.storedDate == JakartaTime.dateString(JakartaTime.today()))
    }
}
```

- [ ] **Step 2: Jalankan test dan pastikan gagal**

```bash
./gradlew testDebugUnitTest --tests "com.sukashawarma.pos.data.local.BypassStoreTest"
```

Expected: FAIL — "unresolved reference: InMemoryBypassStore".

- [ ] **Step 3: Implementasikan BypassStore**

Buat `app/src/main/java/com/sukashawarma/pos/data/local/BypassStore.kt`:

```kotlin
package com.sukashawarma.pos.data.local

import com.sukashawarma.pos.domain.gate.JakartaTime

/**
 * Menyimpan apakah gate sudah di-bypass. Setara `pos_gate_bypassed_types` di
 * `sessionStorage` versi web, tapi dikunci ke tanggal Jakarta agar bypass
 * tidak ikut terbawa ke hari berikutnya.
 */
interface BypassStore {
    fun isBypassed(): Boolean
    fun markBypassed()
    fun clear()
}

private fun isToday(storedDate: String?): Boolean =
    storedDate != null && storedDate == JakartaTime.dateString(JakartaTime.today())

class InMemoryBypassStore(var storedDate: String? = null) : BypassStore {
    override fun isBypassed(): Boolean = isToday(storedDate)

    override fun markBypassed() {
        storedDate = JakartaTime.dateString(JakartaTime.today())
    }

    override fun clear() {
        storedDate = null
    }
}

object SessionBypassStore : BypassStore {
    override fun isBypassed(): Boolean = isToday(SessionPrefs.getBypassedDate())

    override fun markBypassed() {
        SessionPrefs.setBypassedDate(JakartaTime.dateString(JakartaTime.today()))
    }

    override fun clear() {
        SessionPrefs.setBypassedDate(null)
    }
}
```

- [ ] **Step 4: Tambahkan penyimpanan di SessionPrefs**

Di `SessionPrefs.kt`, tambahkan konstanta di bawah `KEY_ROLE`:

```kotlin
    private const val KEY_BYPASSED_DATE = "gate_bypassed_date"
```

Lalu tambahkan dua fungsi setelah `getRole()`:

```kotlin
    fun setBypassedDate(date: String?) {
        if (::prefs.isInitialized) {
            prefs.edit().putString(KEY_BYPASSED_DATE, date).apply()
        }
    }

    fun getBypassedDate(): String? =
        if (::prefs.isInitialized) prefs.getString(KEY_BYPASSED_DATE, null) else null
```

`clear()` yang sudah ada memakai `prefs.edit().clear()`, jadi logout otomatis menghapus tanggal bypass — tidak perlu perubahan lain.

- [ ] **Step 5: Jalankan test dan pastikan lulus**

```bash
./gradlew testDebugUnitTest --tests "com.sukashawarma.pos.data.local.BypassStoreTest"
```

Expected: PASS, 5 test.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/sukashawarma/pos/data/local/BypassStore.kt app/src/main/java/com/sukashawarma/pos/data/local/SessionPrefs.kt app/src/test/java/com/sukashawarma/pos/data/local/BypassStoreTest.kt
git commit -m "feat(gate): persistensi bypass harian pengganti sessionStorage"
```

---

### Task 5: PosGateViewModel

**Files:**
- Create: `app/src/main/java/com/sukashawarma/pos/presentation/gate/PosGateViewModel.kt`
- Test: `app/src/test/java/com/sukashawarma/pos/presentation/gate/PosGateViewModelTest.kt`

**Interfaces:**
- Consumes: endpoint Task 1, model Task 2, `GateEvaluator.evaluate` Task 3, `BypassStore` Task 4, `GlobalEventBus.gateRefreshEvent` Task 6.
- Produces:
  - `class PosGateViewModel(api: SupabaseApi = SupabaseClient.api, bypassStore: BypassStore = SessionBypassStore) : ViewModel()`
  - `val state: StateFlow<GateState>`
  - `val spvPhone: StateFlow<String>`
  - `fun setSession(outletId: String, staffId: String)`
  - `fun refresh()`
  - `fun requestBypass(reason: String, onWhatsAppText: (String) -> Unit)`
  - `fun markBypassed()`
  - `fun clearSession()`

**Ketergantungan pada Task 6:** view model meng-collect `GlobalEventBus.gateRefreshEvent`. Bila Task 6 belum mendarat, tambahkan field itu ke `GlobalEventBus.kt` sebagai bagian dari task ini; definisinya identik di kedua task sehingga aman:

```kotlin
    val gateRefreshEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
```

- [ ] **Step 1: Tulis test yang gagal**

Buat `app/src/test/java/com/sukashawarma/pos/presentation/gate/PosGateViewModelTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Jalankan test dan pastikan gagal**

```bash
./gradlew testDebugUnitTest --tests "com.sukashawarma.pos.presentation.gate.PosGateViewModelTest"
```

Expected: FAIL — "unresolved reference: PosGateViewModel".

- [ ] **Step 3: Implementasikan view model**

Buat `app/src/main/java/com/sukashawarma/pos/presentation/gate/PosGateViewModel.kt`:

```kotlin
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
```

- [ ] **Step 4: Tambahkan gateRefreshEvent bila belum ada**

Periksa `app/src/main/java/com/sukashawarma/pos/data/remote/GlobalEventBus.kt`. Bila `gateRefreshEvent` belum ada (Task 6 belum mendarat), tambahkan baris ini di dalam `object GlobalEventBus`:

```kotlin
    val gateRefreshEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
```

- [ ] **Step 5: Jalankan test dan pastikan lulus**

```bash
./gradlew testDebugUnitTest --tests "com.sukashawarma.pos.presentation.gate.PosGateViewModelTest"
```

Expected: PASS, 7 test.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/sukashawarma/pos/presentation/gate/PosGateViewModel.kt app/src/main/java/com/sukashawarma/pos/data/remote/GlobalEventBus.kt app/src/test/java/com/sukashawarma/pos/presentation/gate/PosGateViewModelTest.kt
git commit -m "feat(gate): PosGateViewModel tanpa polling"
```

---

### Task 6: Realtime lima tabel gate

**Files:**
- Modify: `app/src/main/java/com/sukashawarma/pos/data/remote/GlobalEventBus.kt:6-12`
- Modify: `app/src/main/java/com/sukashawarma/pos/data/remote/realtime/OrderRealtimeManager.kt:75-100`
- Modify: `app/src/main/java/com/sukashawarma/pos/data/remote/realtime/POSRealtimeService.kt:41-44, 81-86`

**Interfaces:**
- Consumes: tidak ada.
- Produces: `GlobalEventBus.gateRefreshEvent: MutableSharedFlow<Unit>`

**Catatan:** `daily_checklist_ticks` tidak difilter karena barisnya hanya memuat `record_id` dan `item_id`, tanpa `outlet_id`. `outlet_staff` dan `outlets` juga tidak difilter, menyamai web.

- [ ] **Step 1: Tambahkan event ke GlobalEventBus**

Di `GlobalEventBus.kt`, tambahkan di dalam object (bila Task 5 sudah menambahkannya, lewati langkah ini):

```kotlin
    val gateRefreshEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
```

Hapus `bypassRequestEvent` — semua konsumennya dipindah ke `gateRefreshEvent`:

```bash
grep -rn "bypassRequestEvent" app/src/main/java
```

Expected setelah task ini selesai: hanya `AttendanceViewModel.kt` (dihapus di Task 8).

- [ ] **Step 2: Tambahkan subscription tabel gate**

Di `OrderRealtimeManager.kt`, tepat setelah blok `bypass_requests` yang sudah ada (sekitar baris 96), tambahkan lima entri berikut ke dalam `JSONArray` yang sama. Ikuti bentuk entri yang sudah ada di file itu:

```kotlin
                        put(org.json.JSONObject().apply {
                            put("event", "*")
                            put("schema", "public")
                            put("table", "attendance")
                            put("filter", "outlet_id=eq.$outletId")
                        })
                        put(org.json.JSONObject().apply {
                            put("event", "*")
                            put("schema", "public")
                            put("table", "daily_checklist_records")
                            put("filter", "outlet_id=eq.$outletId")
                        })
                        put(org.json.JSONObject().apply {
                            // Baris tick tidak memuat outlet_id, jadi tidak difilter.
                            put("event", "*")
                            put("schema", "public")
                            put("table", "daily_checklist_ticks")
                        })
                        put(org.json.JSONObject().apply {
                            put("event", "UPDATE")
                            put("schema", "public")
                            put("table", "outlet_staff")
                        })
                        put(org.json.JSONObject().apply {
                            put("event", "UPDATE")
                            put("schema", "public")
                            put("table", "outlets")
                        })
```

Sesuaikan penulisan `put("event", ...)` dengan gaya entri `orders`/`owner_messages` yang sudah ada agar konsisten.

- [ ] **Step 3: Petakan tabel gate ke gateRefreshEvent**

Di `POSRealtimeService.kt`, ganti cabang `bypass_requests` (baris 83-85) menjadi:

```kotlin
            } else if (table in GATE_TABLES) {
                GlobalEventBus.gateRefreshEvent.tryEmit(Unit)
            }
```

Tambahkan konstanta di `companion object` milik `POSRealtimeService`:

```kotlin
        private val GATE_TABLES = setOf(
            "bypass_requests",
            "attendance",
            "daily_checklist_records",
            "daily_checklist_ticks",
            "outlet_staff",
            "outlets"
        )
```

- [ ] **Step 4: Picu re-check saat WebSocket tersambung ulang**

Di `POSRealtimeService.kt`, ganti handler `onConnectionState` (baris 41-43) menjadi:

```kotlin
        realtimeManager.onConnectionState = { connected ->
            GlobalEventBus.isRealtimeConnected.value = connected
            if (connected) {
                // Pengganti polling 5 detik versi web: setiap kali socket
                // tersambung ulang, status gate dievaluasi ulang sekali.
                GlobalEventBus.gateRefreshEvent.tryEmit(Unit)
            }
        }
```

- [ ] **Step 5: Pastikan tidak ada polling yang tersisip**

```bash
grep -rn "delay(\|postDelayed\|Timer(" app/src/main/java/com/sukashawarma/pos/data/remote/realtime/ app/src/main/java/com/sukashawarma/pos/presentation/gate/
```

Expected: hanya `delay(25_000)` (heartbeat) dan `delay(5_000)` (reconnect backoff) di `OrderRealtimeManager.kt`. Tidak boleh ada `delay` di `presentation/gate/`.

- [ ] **Step 6: Kompilasi**

```bash
./gradlew compileDebugKotlin
```

Expected: hanya error dari `AttendanceViewModel.kt` (dihapus di Task 8); tidak ada error dari file realtime.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/sukashawarma/pos/data/remote/GlobalEventBus.kt app/src/main/java/com/sukashawarma/pos/data/remote/realtime/OrderRealtimeManager.kt app/src/main/java/com/sukashawarma/pos/data/remote/realtime/POSRealtimeService.kt
git commit -m "feat(gate): realtime attendance, checklist, staff, dan outlet"
```

---

### Task 7: UI BlockedOverlay

**Files:**
- Modify: `app/src/main/java/com/sukashawarma/pos/presentation/theme/Color.kt:64-80`
- Create: `app/src/main/java/com/sukashawarma/pos/presentation/gate/BlockedOverlay.kt`

**Interfaces:**
- Consumes: `GateState`, `BlockType`, `BypassStatus`, `ChecklistProgress` (Task 2).
- Produces:
  ```kotlin
  @Composable
  fun BlockedOverlay(
      state: GateState,
      spvPhone: String,
      onSubmitBypass: (reason: String, onWhatsAppText: (String) -> Unit) -> Unit,
      onLogout: () -> Unit
  )
  ```
  Composable ini tidak menyentuh jaringan sama sekali.

**Referensi teks:** semua salinan diambil dari map `TITLE`, `MESSAGE`, dan `PULSE_LABEL` di `BlockedOverlay.tsx`.

- [ ] **Step 1: Tambahkan warna yang belum ada**

Di `Color.kt`, tambahkan di akhir file:

```kotlin
val TwRed400 = Color(0xFFF87171)
val TwRed900 = Color(0xFF7F1D1D)

val TwIndigo100 = Color(0xFFE0E7FF)
val TwIndigo600 = Color(0xFF4F46E5)

val WhatsAppGreen = Color(0xFF25D366)
val WhatsAppGreenDark = Color(0xFF1DA851)
```

- [ ] **Step 2: Tulis composable overlay**

Buat `app/src/main/java/com/sukashawarma/pos/presentation/gate/BlockedOverlay.kt`:

```kotlin
package com.sukashawarma.pos.presentation.gate

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukashawarma.pos.domain.gate.BlockType
import com.sukashawarma.pos.domain.gate.BypassStatus
import com.sukashawarma.pos.domain.gate.GateState
import com.sukashawarma.pos.presentation.theme.*

private const val FALLBACK_SPV_PHONE = "6285218446637"

private fun titleFor(type: BlockType) = when (type) {
    BlockType.ATTENDANCE -> "Menunggu Kehadiran Kru"
    BlockType.CHECKLIST -> "Checklist Buka Toko Belum Selesai"
    BlockType.CLOSED -> "Toko Sudah Tutup"
    BlockType.USER -> "Akun Dinonaktifkan"
    BlockType.OUTLET -> "Cabang Dinonaktifkan"
}

private fun messageFor(type: BlockType) = when (type) {
    BlockType.ATTENDANCE ->
        "Sistem POS akan otomatis terbuka ketika ada minimal 1 kru yang melakukan absen hadir hari ini."
    BlockType.CHECKLIST ->
        "Dashboard kasir akan otomatis terbuka setelah seluruh tugas checklist buka toko diselesaikan oleh kru."
    BlockType.CLOSED -> "Semua kru sudah absen pulang. Sampai jumpa besok!"
    BlockType.USER -> "Akun Anda saat ini sedang dinonaktifkan oleh Administrator."
    BlockType.OUTLET ->
        "Cabang tempat Anda bertugas saat ini sedang dinonaktifkan oleh Administrator."
}

private fun pulseLabelFor(type: BlockType) = when (type) {
    BlockType.ATTENDANCE -> "Menunggu Sinyal Absensi..."
    BlockType.CHECKLIST -> "Menunggu Checklist Diselesaikan..."
    BlockType.CLOSED -> "Menunggu Sesi Baru..."
    else -> ""
}

private fun iconFor(type: BlockType): ImageVector = when (type) {
    BlockType.ATTENDANCE -> Icons.Default.Schedule
    BlockType.CHECKLIST -> Icons.Default.AssignmentTurnedIn
    BlockType.CLOSED -> Icons.Default.DarkMode
    BlockType.USER, BlockType.OUTLET -> Icons.Default.Block
}

private fun iconTintFor(type: BlockType): Color = when (type) {
    BlockType.ATTENDANCE, BlockType.CHECKLIST -> TwAmber600
    BlockType.CLOSED -> TwIndigo600
    BlockType.USER, BlockType.OUTLET -> TwRed600
}

private fun iconBackgroundFor(type: BlockType): Color = when (type) {
    BlockType.ATTENDANCE, BlockType.CHECKLIST -> TwAmber100
    BlockType.CLOSED -> TwIndigo100
    BlockType.USER, BlockType.OUTLET -> TwRed100
}

@Composable
fun BlockedOverlay(
    state: GateState,
    spvPhone: String,
    onSubmitBypass: (reason: String, onWhatsAppText: (String) -> Unit) -> Unit,
    onLogout: () -> Unit
) {
    if (!state.isBlocked) return

    val context = LocalContext.current
    var showBypassForm by remember { mutableStateOf(false) }
    var bypassReason by remember { mutableStateOf("") }
    var waText by remember { mutableStateOf<String?>(null) }

    val isPendingApproval = state.bypassStatus == BypassStatus.PENDING

    // SPV menyetujui lewat WhatsApp; realtime bypass_requests yang membuka gate.
    LaunchedEffect(state.bypassStatus) {
        if (state.bypassStatus == null && waText != null) {
            waText = null
            showBypassForm = false
        }
    }

    fun openWhatsApp(text: String) {
        val phone = spvPhone.ifBlank { FALLBACK_SPV_PHONE }
        val uri = Uri.parse("whatsapp://send?phone=$phone&text=${Uri.encode(text)}")
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "WhatsApp tidak terpasang di perangkat ini",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TwGray900.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 24.dp,
            modifier = Modifier.padding(24.dp).widthIn(max = 448.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when {
                    showBypassForm && isPendingApproval -> PendingApprovalContent(
                        onOpenWhatsApp = { waText?.let(::openWhatsApp) },
                        canOpenWhatsApp = waText != null,
                        onBack = { showBypassForm = false }
                    )

                    showBypassForm -> BypassFormContent(
                        reason = bypassReason,
                        onReasonChange = { bypassReason = it },
                        isRejected = state.bypassStatus == BypassStatus.REJECTED,
                        onCancel = {
                            showBypassForm = false
                            bypassReason = ""
                        },
                        onSubmit = {
                            onSubmitBypass(bypassReason) { text ->
                                waText = text
                                openWhatsApp(text)
                            }
                        }
                    )

                    else -> BlockedContent(
                        state = state,
                        onShowBypassForm = { showBypassForm = true },
                        onLogout = onLogout
                    )
                }
            }
        }
    }
}

@Composable
private fun BlockedContent(
    state: GateState,
    onShowBypassForm: () -> Unit,
    onLogout: () -> Unit
) {
    val type = state.type
    val isWaiting = type == BlockType.ATTENDANCE ||
        type == BlockType.CHECKLIST ||
        type == BlockType.CLOSED
    val showReasonBox = type == BlockType.USER || type == BlockType.OUTLET

    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(iconBackgroundFor(type)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = iconFor(type),
            contentDescription = null,
            tint = iconTintFor(type),
            modifier = Modifier.size(40.dp)
        )
    }

    Spacer(Modifier.height(24.dp))

    Text(
        text = titleFor(type),
        fontSize = 24.sp,
        fontWeight = FontWeight.Black,
        color = TwGray900,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(8.dp))

    Text(
        text = messageFor(type),
        fontWeight = FontWeight.Medium,
        color = TwGray500,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(24.dp))

    val progress = state.progress
    if (type == BlockType.CHECKLIST && progress != null && progress.total > 0) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Progress Checklist Buka Toko",
                fontWeight = FontWeight.Bold,
                color = TwGray700,
                fontSize = 14.sp
            )
            Text(
                "${progress.done}/${progress.total} tugas",
                fontWeight = FontWeight.Bold,
                color = TwGray700,
                fontSize = 14.sp
            )
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress.done.toFloat() / progress.total.toFloat() },
            color = TwAmber500,
            trackColor = TwGray100,
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(999.dp))
        )
        Spacer(Modifier.height(24.dp))
    }

    if (showReasonBox) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = TwRed50,
            border = androidx.compose.foundation.BorderStroke(1.dp, TwRed100),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "ALASAN PENONAKTIFAN:",
                    color = TwRed400,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "\"${state.reason}\"",
                    color = TwRed900,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.height(32.dp))
    } else if (isWaiting) {
        PulseIndicator(pulseLabelFor(type))
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onShowBypassForm) {
            Text(
                "Gunakan Bypass Darurat",
                color = TwGray400,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
            )
        }
        Spacer(Modifier.height(16.dp))
    }

    if (showReasonBox || type == BlockType.CLOSED) {
        Button(
            onClick = onLogout,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TwGray900),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Block,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("Keluar / Logout", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PulseIndicator(label: String) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = TwAmber50,
        border = androidx.compose.foundation.BorderStroke(1.dp, TwAmber100),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp).alpha(alpha),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(TwAmber500)
            )
            Spacer(Modifier.width(8.dp))
            Text(label, color = TwAmber600, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun BypassFormContent(
    reason: String,
    onReasonChange: (String) -> Unit,
    isRejected: Boolean,
    onCancel: () -> Unit,
    onSubmit: () -> Unit
) {
    Box(
        modifier = Modifier.size(80.dp).clip(CircleShape).background(TwAmber100),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.WarningAmber,
            contentDescription = null,
            tint = TwAmber600,
            modifier = Modifier.size(40.dp)
        )
    }

    Spacer(Modifier.height(24.dp))
    Text("Pengajuan Bypass", fontSize = 24.sp, fontWeight = FontWeight.Black, color = TwGray900)
    Spacer(Modifier.height(8.dp))
    Text(
        "Sistem absensi bermasalah? Ajukan bypass darurat ke SPV Anda.",
        color = TwGray500,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(24.dp))

    OutlinedTextField(
        value = reason,
        onValueChange = onReasonChange,
        label = { Text("Alasan Bypass") },
        placeholder = { Text("Contoh: Sistem absensi error, foto tidak bisa terkirim...") },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().height(120.dp)
    )

    if (isRejected) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Pengajuan bypass ditolak oleh SPV.",
            color = TwRed500,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }

    Spacer(Modifier.height(16.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = onCancel,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TwGray100,
                contentColor = TwGray700
            ),
            modifier = Modifier.weight(1f).height(52.dp)
        ) {
            Text("Batal", fontWeight = FontWeight.Bold)
        }
        Button(
            onClick = onSubmit,
            enabled = reason.isNotBlank(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TwAmber600),
            modifier = Modifier.weight(2f).height(52.dp)
        ) {
            Text("Kirim ke SPV", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PendingApprovalContent(
    onOpenWhatsApp: () -> Unit,
    canOpenWhatsApp: Boolean,
    onBack: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "pendingPulse")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "pendingAlpha"
    )

    Box(
        modifier = Modifier.size(80.dp).clip(CircleShape).background(TwAmber100),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Schedule,
            contentDescription = null,
            tint = TwAmber600,
            modifier = Modifier.size(40.dp).alpha(alpha)
        )
    }

    Spacer(Modifier.height(24.dp))
    Text("Menunggu Persetujuan", fontSize = 24.sp, fontWeight = FontWeight.Black, color = TwGray900)
    Spacer(Modifier.height(8.dp))
    Text(
        "Pengajuan bypass telah dikirim ke SPV. Sistem akan otomatis terbuka setelah disetujui.",
        color = TwGray500,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(24.dp))

    if (canOpenWhatsApp) {
        Button(
            onClick = onOpenWhatsApp,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("Buka WhatsApp SPV", fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(Modifier.height(12.dp))
    }

    Button(
        onClick = onBack,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = TwGray100,
            contentColor = TwGray700
        ),
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) {
        Text("Kembali", fontWeight = FontWeight.Bold)
    }
}
```

- [ ] **Step 3: Kompilasi**

```bash
./gradlew compileDebugKotlin
```

Expected: hanya error dari `AttendanceViewModel.kt` / `AttendanceOverlay.kt` (dihapus di Task 8); tidak ada error dari `presentation/gate/BlockedOverlay.kt`.

**Catatan:** `WhatsAppGreenDark` sengaja belum dipakai (hanya hover state di web). Bila lint memperingatkan unused, hapus konstanta itu dari `Color.kt`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/sukashawarma/pos/presentation/theme/Color.kt app/src/main/java/com/sukashawarma/pos/presentation/gate/BlockedOverlay.kt
git commit -m "feat(gate): BlockedOverlay lima tipe blokir menyamai web"
```

---

### Task 8: Wiring MainActivity dan pembersihan

**Files:**
- Modify: `app/src/main/java/com/sukashawarma/pos/presentation/MainActivity.kt:39-40, 55, 87, 107-112, 149`
- Delete: `app/src/main/java/com/sukashawarma/pos/presentation/attendance/AttendanceViewModel.kt`
- Delete: `app/src/main/java/com/sukashawarma/pos/presentation/attendance/AttendanceOverlay.kt`

**Interfaces:**
- Consumes: `PosGateViewModel` (Task 5), `BlockedOverlay` (Task 7), `GlobalEventBus.gateRefreshEvent` (Task 6).
- Produces: aplikasi yang terkompilasi dan berjalan.

- [ ] **Step 1: Konfirmasi implementasi lama sudah hilang**

Task 1 sudah menghapus `AttendanceViewModel.kt` dan `AttendanceOverlay.kt` beserta seluruh rujukannya di `MainActivity.kt`.

```bash
grep -rn "AttendanceViewModel\|AttendanceOverlay" app/src/main/java
```

Expected: tidak ada hasil. Bila masih ada, hapus sisanya sebelum lanjut.

- [ ] **Step 2: Tambahkan import baru di MainActivity**

Tambahkan import untuk gate, lifecycle, dan event bus:

```kotlin
import com.sukashawarma.pos.presentation.gate.PosGateViewModel
import com.sukashawarma.pos.presentation.gate.BlockedOverlay
```

```kotlin
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.sukashawarma.pos.data.remote.GlobalEventBus
```

- [ ] **Step 3: Tambahkan deklarasi view model**

Di antara properti `by viewModels()` lain di `MainActivity` (Task 1 sudah menghapus deklarasi `attendanceViewModel` yang lama), tambahkan:

```kotlin
    private val gateViewModel: PosGateViewModel by viewModels()
```

- [ ] **Step 4: Oper session ke gate**

Di dalam `LaunchedEffect(activeSession)`, sebagai baris terakhir blok `activeSession?.let { session -> ... }`, tambahkan — perhatikan `session.staffId` (UUID), **bukan** `session.username`:

```kotlin
                        gateViewModel.setSession(session.outletId, session.staffId)
```

- [ ] **Step 5: Picu re-check saat aplikasi kembali ke foreground**

Tepat di bawah `LaunchedEffect(activeSession) { ... }`, tambahkan:

```kotlin
                // Pengganti polling versi web: evaluasi ulang sekali setiap
                // aplikasi kembali ke foreground.
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            GlobalEventBus.gateRefreshEvent.tryEmit(Unit)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
```

- [ ] **Step 6: Bersihkan state gate saat logout**

Di dalam lambda `onLogoutClick` (baris 107-112), tambahkan satu baris setelah `loginViewModel.logout()`:

```kotlin
                                gateViewModel.clearSession()
```

- [ ] **Step 7: Pasang overlay baru**

Di dalam `Box(modifier = Modifier.fillMaxSize())` yang membungkus `POSAdaptiveScaffold`, sebagai anak terakhir (setelah scaffold, supaya overlay menutupi segalanya), tambahkan:

```kotlin
                        val gateState by gateViewModel.state.collectAsState()
                        val spvPhone by gateViewModel.spvPhone.collectAsState()
                        BlockedOverlay(
                            state = gateState,
                            spvPhone = spvPhone,
                            onSubmitBypass = { reason, onText ->
                                gateViewModel.requestBypass(reason, onText)
                            },
                            onLogout = {
                                gateViewModel.clearSession()
                                loginViewModel.logout()
                                dashboardViewModel.setSession("", "", "Kasir")
                                posManualOrderViewModel.currentOutletId.value = ""
                                menuManagementViewModel.setOutlet("")
                            }
                        )
```

- [ ] **Step 8: Jalankan seluruh test unit**

```bash
./gradlew testDebugUnitTest
```

Expected: PASS, termasuk seluruh test dari Task 1 sampai 5, dan test lama (`MenuRepositoryTest`, `MenuRulesTest`, `MenuItemDtoTest`) tetap hijau.

- [ ] **Step 9: Build APK debug**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL, tanpa referensi tersisa ke `AttendanceViewModel` atau `AttendanceOverlay`.

```bash
grep -rn "AttendanceViewModel\|AttendanceOverlay\|bypassRequestEvent" app/src/main/java
```

Expected: tidak ada hasil.

- [ ] **Step 10: Verifikasi manual di perangkat**

Jalankan aplikasi di tablet POS, login sebagai kru di outlet uji yang belum ada absensi hari ini, lalu catat hasil tiap langkah:

1. Overlay tampil dengan judul "Menunggu Kehadiran Kru", ikon jam amber, dan indikator "Menunggu Sinyal Absensi...".
2. Dari aplikasi lain, seorang kru melakukan absen hadir di outlet yang sama. **Tanpa menyentuh tablet**, overlay berpindah ke "Checklist Buka Toko Belum Selesai" dengan progress bar.
3. Selesaikan checklist buka toko dari aplikasi lain. Overlay hilang sendiri dan dashboard kasir muncul.
4. Matikan Wi-Fi tablet selama 30 detik, absenkan kru lain, nyalakan lagi Wi-Fi. Setelah WebSocket tersambung ulang, status gate ikut ter-update tanpa interaksi.

Bila ada langkah yang gagal, catat langkah dan gejalanya sebelum melanjutkan.

- [ ] **Step 11: Commit**

Working tree memuat pekerjaan lain yang belum di-commit (order history). **Jangan** memakai `git add -A` atau `git add .` — stage hanya path berikut:

```bash
git add app/src/main/java/com/sukashawarma/pos/presentation/MainActivity.kt
git commit -m "feat(gate): wiring gate baru di MainActivity dan hapus overlay lama"
```

Penghapusan dua file di Step 1 sudah ter-stage oleh `git rm`, jadi ikut terbawa commit ini.
