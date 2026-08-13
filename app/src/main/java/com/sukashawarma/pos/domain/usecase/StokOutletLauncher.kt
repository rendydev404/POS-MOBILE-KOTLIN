package com.sukashawarma.pos.domain.usecase

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.sukashawarma.pos.BuildConfig
import com.sukashawarma.pos.data.local.AuthPrefs
import com.sukashawarma.pos.data.remote.AuthSessionManager
import com.sukashawarma.pos.data.remote.SessionTokenHolder
import com.sukashawarma.pos.data.remote.SignInPayload
import com.sukashawarma.pos.data.remote.SupabaseClient
import java.net.URLEncoder

/**
 * Membuka web Stok Outlet di browser dengan sesi Supabase yang sudah aktif di POS,
 * sehingga kasir tidak perlu login ulang.
 *
 * Token dikirim lewat *fragment* URL (`#access_token=...`), bukan query string:
 * fragment tidak pernah dikirim ke server, jadi tidak mendarat di access log Vercel
 * maupun header Referer. Halaman `/auth/sso` di app stok menukarnya jadi cookie sesi
 * (@supabase/ssr) yang dibaca middleware `enforceAppAccess`.
 *
 * Outlet tidak perlu dikirim: web stok menentukan scope outlet dari `auth.uid()`
 * lewat RPC `accessible_outlet_ids()` — identitas yang sama, outlet yang sama.
 */
object StokOutletLauncher {

    /** Chrome diprioritaskan sesuai permintaan ops; urut dari channel stabil. */
    private val CHROME_PACKAGES = listOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.chrome.canary"
    )

    private const val EMAIL_DOMAIN = "@outlet.local"

    sealed interface Result {
        data object Success : Result
        /** Sesi tidak bisa disiapkan (token mati & kredensial tersimpan tidak ada/ditolak). */
        data object NoSession : Result
        /** Tidak ada browser sama sekali di perangkat. */
        data object NoBrowser : Result
    }

    suspend fun open(context: Context, path: String = "/dashboard"): Result {
        val session = mintWebSession() ?: return Result.NoSession

        val url = buildString {
            append(BuildConfig.STOK_WEB_URL.trimEnd('/'))
            append("/auth/sso#access_token=")
            append(encode(session.first))
            append("&refresh_token=")
            append(encode(session.second))
            append("&next=")
            append(encode(path))
        }

        return if (launchUrl(context, url)) Result.Success else Result.NoBrowser
    }

    /**
     * Sesi terpisah untuk browser, bukan sesi milik POS.
     *
     * Supabase merotasi refresh_token: kalau browser dan POS berbagi satu sesi,
     * refresh di browser mencabut token POS dan kasir tiba-tiba ter-logout di
     * tengah transaksi. Sign-in ulang memakai kredensial tersimpan memberi baris
     * sesi sendiri, jadi keduanya hidup berdampingan. Kalau kredensial tidak ada
     * (mis. sesi lama sebelum fitur ini), pakai sesi POS sebagai fallback —
     * tetap bisa masuk, hanya berbagi rotasi.
     */
    private suspend fun mintWebSession(): Pair<String, String>? {
        val username = AuthPrefs.getLastUsername()
        val password = AuthPrefs.getLastPassword()
        if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
            val email = if (username.contains("@")) username else "$username$EMAIL_DOMAIN"
            try {
                val res = SupabaseClient.authApi.signInWithPassword(payload = SignInPayload(email, password))
                val body = res.body()
                if (res.isSuccessful && body != null) {
                    return body.access_token to body.refresh_token
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (!AuthSessionManager.ensureAuthenticated()) return null
        val access = SessionTokenHolder.accessToken ?: return null
        val refresh = SessionTokenHolder.refreshToken ?: AuthPrefs.getRefreshToken() ?: return null
        return access to refresh
    }

    /** Chrome dulu; kalau tidak terpasang, serahkan ke browser default. */
    private fun launchUrl(context: Context, url: String): Boolean {
        val uri = Uri.parse(url)
        for (pkg in CHROME_PACKAGES) {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
                return true
            } catch (e: ActivityNotFoundException) {
                // Channel Chrome ini tidak terpasang — coba berikutnya.
            }
        }

        return try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
