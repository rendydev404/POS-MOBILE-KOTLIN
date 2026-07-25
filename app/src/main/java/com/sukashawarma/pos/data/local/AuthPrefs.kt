package com.sukashawarma.pos.data.local

import android.content.Context
import android.content.SharedPreferences

/** Persists the Supabase Auth refresh_token so the app can re-authenticate silently
 *  after the OS kills the process (e.g. when woken up by an FCM push). */
object AuthPrefs {
    private const val PREFS_NAME = "auth_prefs"
    private const val KEY_REFRESH_TOKEN = "refresh_token"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun setRefreshToken(token: String) {
        if (::prefs.isInitialized) prefs.edit().putString(KEY_REFRESH_TOKEN, token).apply()
    }

    fun getRefreshToken(): String? = if (::prefs.isInitialized) prefs.getString(KEY_REFRESH_TOKEN, null) else null

    fun clear() {
        if (::prefs.isInitialized) prefs.edit().remove(KEY_REFRESH_TOKEN).apply()
    }
}
