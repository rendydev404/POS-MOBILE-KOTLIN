package com.sukashawarma.pos.domain.gate

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Semua perhitungan hari untuk gate kasir memakai Asia/Jakarta, menyamai
 * `Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Jakarta' })` di versi web.
 */
object JakartaTime {

    val ZONE: ZoneId = ZoneId.of("Asia/Jakarta")

    fun today(): LocalDate = LocalDate.now(ZONE)

    fun dateString(day: LocalDate): String = day.format(DateTimeFormatter.ISO_LOCAL_DATE)

    /**
     * Batas hari sebagai instant UTC (`...Z`), menunjuk titik waktu yang sama
     * dengan tengah malam di Jakarta.
     *
     * Sengaja BUKAN bentuk `+07:00`: string ini dipakai sebagai nilai query
     * PostgREST, dan OkHttp tidak mem-persen-encode `+` di query string. Server
     * menerimanya sebagai spasi lalu menolak seluruh request dengan
     * `22007 invalid input syntax for type timestamp with time zone`.
     */
    fun startOfDayIso(day: LocalDate): String =
        Instant.ofEpochMilli(startOfDayMillis(day)).toString()

    /** Batas atas inklusif, sampai milidetik terakhir hari itu. */
    fun endOfDayIso(day: LocalDate): String =
        Instant.ofEpochMilli(endOfDayMillis(day)).toString()

    fun startOfDayMillis(day: LocalDate): Long = day.atStartOfDay(ZONE).toInstant().toEpochMilli()

    fun endOfDayMillis(day: LocalDate): Long = day.plusDays(1).atStartOfDay(ZONE).toInstant().toEpochMilli() - 1

    /** Bahasa tampilan tanggal di seluruh app — "Agu", bukan "Aug". */
    private val LOCALE_ID: Locale = Locale("id", "ID")

    private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZONE)
    private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZONE)
    private val DATE_TIME_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", LOCALE_ID).withZone(ZONE)

    /**
     * Tanggal `yyyy-MM-dd` menurut Asia/Jakarta dari timestamp mentah PostgREST.
     *
     * Memotong 10 karakter pertama string mentah TIDAK sama dengan ini: baris yang
     * datang dari cache Room diformat sebagai instant UTC (`...Z`), sehingga pesanan
     * dini hari WIB (mis. 01:00) tampil sebagai tanggal KEMARIN.
     */
    fun dateStringOf(raw: String?): String =
        instantOrNull(raw)?.let { DATE_FORMAT.format(it) } ?: "-"

    /**
     * Jam `HH:mm` menurut Asia/Jakarta dari timestamp mentah PostgREST.
     *
     * Zona ditetapkan eksplisit, bukan mengikuti zona perangkat: tablet outlet yang
     * jam/zonanya meleset tidak boleh ikut menggeser jam pada struk dan riwayat.
     */
    fun timeStringOf(raw: String?): String =
        instantOrNull(raw)?.let { TIME_FORMAT.format(it) } ?: "-"

    /** Tanggal + jam (`dd MMM yyyy, HH:mm`) menurut Asia/Jakarta, untuk baris daftar transaksi. */
    fun dateTimeStringOf(raw: String?): String =
        instantOrNull(raw)?.let { DATE_TIME_FORMAT.format(it) } ?: "-"

    /** True bila [timestamp] jatuh pada [day] menurut Asia/Jakarta. */
    fun isOnDay(timestamp: String?, day: LocalDate): Boolean {
        val instant = instantOrNull(timestamp) ?: return false
        return instant.atZone(ZONE).toLocalDate() == day
    }

    fun instantOrNull(raw: String?): Instant? {
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
