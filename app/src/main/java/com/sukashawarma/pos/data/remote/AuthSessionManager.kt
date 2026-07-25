package com.sukashawarma.pos.data.remote

import com.sukashawarma.pos.data.local.AuthPrefs

/** Ensures there's a valid access token before making an authenticated request,
 *  even in a fresh process (e.g. FirebaseMessagingService woken up after app-kill). */
object AuthSessionManager {
    suspend fun ensureAuthenticated(): Boolean {
        if (SessionTokenHolder.accessToken != null) return true

        val refreshToken = AuthPrefs.getRefreshToken() ?: return false
        return try {
            val res = SupabaseClient.authApi.refreshSession(payload = RefreshTokenPayload(refreshToken))
            val body = res.body()
            if (res.isSuccessful && body != null) {
                SessionTokenHolder.accessToken = body.access_token
                SessionTokenHolder.refreshToken = body.refresh_token
                AuthPrefs.setRefreshToken(body.refresh_token)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
