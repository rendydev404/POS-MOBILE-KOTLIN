package com.sukashawarma.pos.data.remote

import com.sukashawarma.pos.data.local.AuthPrefs
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Menjaga access token tetap hidup. Token Supabase kedaluwarsa ~1 jam; tanpa
 *  refresh, REST jadi 401 diam-diam dan channel realtime ditutup server. */
object AuthSessionManager {
    private val mutex = Mutex()
    private var lastRefreshAt = 0L

    /** Refresh kalau token hilang atau tinggal <5 menit lagi. */
    suspend fun ensureAuthenticated(): Boolean {
        if (isUsable(SessionTokenHolder.accessToken)) return true
        return refresh()
    }

    /** Tukar refresh token jadi access token baru. Aman dipanggil bersamaan. */
    suspend fun refresh(): Boolean = mutex.withLock {
        // Coroutine lain mungkin baru saja menyegarkan sementara kita menunggu lock.
        if (isUsable(SessionTokenHolder.accessToken)) return@withLock true
        // Rem darurat: kalau `exp` gagal diparse, jangan memukul GoTrue tiap 25 detik.
        if (System.currentTimeMillis() - lastRefreshAt < 60_000) {
            return@withLock SessionTokenHolder.accessToken != null
        }

        val refreshToken = SessionTokenHolder.refreshToken
            ?: AuthPrefs.getRefreshToken()
            ?: return@withLock false

        try {
            lastRefreshAt = System.currentTimeMillis()
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

    private fun isUsable(token: String?): Boolean =
        token != null && expiryMillis(token) - System.currentTimeMillis() > 5 * 60_000L

    /** `exp` dari payload JWT (ms). 0 kalau tidak terbaca — dianggap perlu refresh. */
    private fun expiryMillis(token: String): Long = try {
        val payload = token.split(".")[1]
        val flags = android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
        org.json.JSONObject(String(android.util.Base64.decode(payload, flags))).getLong("exp") * 1000L
    } catch (e: Exception) {
        0L
    }
}
