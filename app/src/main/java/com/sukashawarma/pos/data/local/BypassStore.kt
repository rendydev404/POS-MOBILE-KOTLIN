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
