package com.sukashawarma.pos.data.local

import android.content.Context
import android.content.SharedPreferences

/** Persists which paired Bluetooth printer the cashier picked in Settings. */
object PrinterPrefs {
    private const val PREFS_NAME = "printer_prefs"
    private const val KEY_SELECTED_MAC = "selected_printer_mac"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun getSelectedMac(): String? = if (::prefs.isInitialized) prefs.getString(KEY_SELECTED_MAC, null) else null

    fun setSelectedMac(mac: String) {
        if (::prefs.isInitialized) prefs.edit().putString(KEY_SELECTED_MAC, mac).apply()
    }
}
