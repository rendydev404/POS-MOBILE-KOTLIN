package com.sukashawarma.pos.data.local

import android.content.Context
import android.content.SharedPreferences

/** Persists which paired Bluetooth printer the cashier picked in Settings. */
object PrinterPrefs {
    private const val PREFS_NAME = "printer_prefs"
    private const val KEY_SELECTED_MAC = "selected_printer_mac"
    private const val KEY_NAME_FILTER = "bluetooth_name_filter"
    private const val KEY_TEXT_LOGO = "disable_raster_logo"

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

    fun getNameFilter(): String = if (::prefs.isInitialized) prefs.getString(KEY_NAME_FILTER, "").orEmpty() else ""

    fun setNameFilter(filter: String) {
        if (::prefs.isInitialized) prefs.edit().putString(KEY_NAME_FILTER, filter.trim()).apply()
    }

    fun isTextLogoEnabled(): Boolean = if (::prefs.isInitialized) prefs.getBoolean(KEY_TEXT_LOGO, true) else true

    fun setTextLogoEnabled(enabled: Boolean) {
        if (::prefs.isInitialized) prefs.edit().putBoolean(KEY_TEXT_LOGO, enabled).apply()
    }
}
