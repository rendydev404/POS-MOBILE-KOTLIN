package com.sukashawarma.pos.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReportDateRangeTest {

    private val today = LocalDate.of(2026, 8, 6)

    private fun resolve(
        range: ReportRange,
        start: LocalDate? = null,
        end: LocalDate? = null
    ) = ReportDateRangeResolver.resolve(range, start, end, today)

    @Test
    fun `hari ini dibatasi awal dan akhir hari Jakarta`() {
        val r = resolve(ReportRange.TODAY)
        assertEquals("2026-08-05T17:00:00Z", r.startIso)
        assertEquals("2026-08-06T16:59:59.999Z", r.endIso)
    }

    @Test
    fun `kemarin tidak menyentuh hari ini`() {
        val r = resolve(ReportRange.YESTERDAY)
        assertEquals("2026-08-04T17:00:00Z", r.startIso)
        assertEquals("2026-08-05T16:59:59.999Z", r.endIso)
    }

    @Test
    fun `tujuh hari terakhir benar-benar tujuh hari termasuk hari ini`() {
        val r = resolve(ReportRange.LAST_7_DAYS)
        // 31 Juli sampai 6 Agustus = 7 hari. Versi lama mundur 7 hari penuh
        // sehingga mencakup 8 hari.
        assertEquals("2026-07-30T17:00:00Z", r.startIso)
        assertEquals("2026-08-06T16:59:59.999Z", r.endIso)
    }

    @Test
    fun `tiga puluh hari terakhir mencakup tiga puluh hari`() {
        val r = resolve(ReportRange.LAST_30_DAYS)
        assertEquals("2026-07-07T17:00:00Z", r.startIso)
    }

    @Test
    fun `semua waktu tidak punya batas dan tetap aman untuk kueri Room`() {
        val r = resolve(ReportRange.ALL_TIME)
        assertNull(r.startIso)
        assertNull(r.endIso)
        assertTrue(r.isUnbounded)
        // Regresi: dulu batas lokalnya diambil dari `now`, sehingga kueri Room
        // menjadi (sekarang..MAX) dan omzet offline selalu Rp 0.
        assertEquals(0L, r.startMillis)
        assertEquals(Long.MAX_VALUE, r.endMillis)
    }

    @Test
    fun `kustom inklusif sampai akhir hari terakhir`() {
        val r = resolve(ReportRange.CUSTOM, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3))
        assertEquals("2026-07-31T17:00:00Z", r.startIso)
        assertEquals("2026-08-03T16:59:59.999Z", r.endIso)
    }

    @Test
    fun `kustom dengan tanggal terbalik tetap menghasilkan rentang yang benar`() {
        val r = resolve(ReportRange.CUSTOM, LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 1))
        assertEquals("2026-07-31T17:00:00Z", r.startIso)
        assertEquals("2026-08-03T16:59:59.999Z", r.endIso)
    }

    @Test
    fun `kustom tanpa tanggal jatuh ke semua waktu bukan rentang kosong`() {
        val r = resolve(ReportRange.CUSTOM, null, null)
        assertTrue(r.isUnbounded)
    }

    @Test
    fun `batas milidetik konsisten dengan batas ISO`() {
        val r = resolve(ReportRange.TODAY)
        // 2026-08-05T17:00:00Z = 2026-08-06T00:00:00+07:00
        assertEquals(1785949200000L, r.startMillis)
        assertEquals(1786035599999L, r.endMillis)
    }

    /**
     * Regresi. String ini masuk ke query PostgREST apa adanya, dan OkHttp
     * meneruskan `+` mentah — server membacanya sebagai spasi lalu menolak seluruh
     * request dengan `22007 invalid input syntax for type timestamp with time
     * zone`, sehingga daftar riwayat selalu kosong untuk semua rentang tanggal.
     */
    @Test
    fun `batas ISO tidak pernah memuat plus yang merusak query`() {
        ReportRange.values().forEach { range ->
            val r = resolve(range, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3))
            assertFalse("$range punya + di startIso", r.startIso?.contains('+') ?: false)
            assertFalse("$range punya + di endIso", r.endIso?.contains('+') ?: false)
        }
    }

    @Test
    fun `kunci filter halaman Riwayat dipetakan ke rentang yang sama`() {
        assertEquals(ReportRange.TODAY, ReportRange.fromHistoryKey("today"))
        assertEquals(ReportRange.YESTERDAY, ReportRange.fromHistoryKey("yesterday"))
        assertEquals(ReportRange.LAST_7_DAYS, ReportRange.fromHistoryKey("7d"))
        assertEquals(ReportRange.LAST_30_DAYS, ReportRange.fromHistoryKey("30d"))
        assertEquals(ReportRange.CUSTOM, ReportRange.fromHistoryKey("custom"))
        assertEquals(ReportRange.ALL_TIME, ReportRange.fromHistoryKey("all"))
    }
}
