package com.sukashawarma.pos.data.remote

/**
 * Holds the current Supabase Auth user access token in memory.
 * Requests carry this as Bearer token so PostgREST RLS (auth.uid()) applies;
 * without it, requests fall back to the anon key (same as an anonymous web visitor).
 */
object SessionTokenHolder {
    @Volatile
    var accessToken: String? = null

    @Volatile
    var refreshToken: String? = null

    fun clear() {
        accessToken = null
        refreshToken = null
    }
}
