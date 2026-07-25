package com.sukashawarma.pos.data.local

import android.content.Context
import android.content.SharedPreferences

/**
 * Small persisted mirror of the logged-in staff/outlet, readable outside the
 * Compose/ViewModel graph — needed by POSFirebaseMessagingService, which Android
 * can instantiate in a fresh process (app killed) with no ViewModels alive.
 */
object SessionPrefs {
    private const val PREFS_NAME = "session_prefs"
    private const val KEY_STAFF_ID = "staff_id"
    private const val KEY_OUTLET_ID = "outlet_id"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun setSession(staffId: String, outletId: String) {
        if (::prefs.isInitialized) {
            prefs.edit().putString(KEY_STAFF_ID, staffId).putString(KEY_OUTLET_ID, outletId).apply()
        }
    }

    fun clear() {
        if (::prefs.isInitialized) prefs.edit().clear().apply()
    }

    fun getStaffId(): String? = if (::prefs.isInitialized) prefs.getString(KEY_STAFF_ID, null) else null
    fun getOutletId(): String? = if (::prefs.isInitialized) prefs.getString(KEY_OUTLET_ID, null) else null
}
