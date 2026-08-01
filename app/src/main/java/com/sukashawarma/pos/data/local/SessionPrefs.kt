package com.sukashawarma.pos.data.local

import android.content.Context
import android.content.SharedPreferences

object SessionPrefs {
    private const val PREFS_NAME = "session_prefs"
    private const val KEY_STAFF_ID = "staff_id"
    private const val KEY_OUTLET_ID = "outlet_id"
    private const val KEY_OUTLET_NAME = "outlet_name"
    private const val KEY_USERNAME = "username"
    private const val KEY_ROLE = "role"
    private const val KEY_BYPASSED_DATE = "gate_bypassed_date"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun setSession(staffId: String, outletId: String, outletName: String = "Outlet", username: String = "Staff", role: String = "kasir") {
        if (::prefs.isInitialized) {
            prefs.edit()
                .putString(KEY_STAFF_ID, staffId)
                .putString(KEY_OUTLET_ID, outletId)
                .putString(KEY_OUTLET_NAME, outletName)
                .putString(KEY_USERNAME, username)
                .putString(KEY_ROLE, role)
                .apply()
        }
    }

    fun clear() {
        if (::prefs.isInitialized) prefs.edit().clear().apply()
    }

    fun getStaffId(): String? = if (::prefs.isInitialized) prefs.getString(KEY_STAFF_ID, null) else null
    fun getOutletId(): String? = if (::prefs.isInitialized) prefs.getString(KEY_OUTLET_ID, null) else null
    fun getOutletName(): String? = if (::prefs.isInitialized) prefs.getString(KEY_OUTLET_NAME, null) else null
    fun getUsername(): String? = if (::prefs.isInitialized) prefs.getString(KEY_USERNAME, null) else null
    fun getRole(): String? = if (::prefs.isInitialized) prefs.getString(KEY_ROLE, null) else null

    fun setBypassedDate(date: String?) {
        if (::prefs.isInitialized) {
            prefs.edit().putString(KEY_BYPASSED_DATE, date).apply()
        }
    }

    fun getBypassedDate(): String? =
        if (::prefs.isInitialized) prefs.getString(KEY_BYPASSED_DATE, null) else null
}
