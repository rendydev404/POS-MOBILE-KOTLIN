package com.sukashawarma.pos.data.notification

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.sukashawarma.pos.data.remote.SupabaseClient
import com.sukashawarma.pos.data.remote.dto.RegisterFcmTokenPayload

/** Shared by POSFirebaseMessagingService.onNewToken, post-login registration, and logout. */
object FcmTokenRegistrar {
    private const val TAG = "POS_DEBUG"

    /**
     * Ikat device ini ke akun yang sedang login.
     *
     * `staffId` hanya dipakai untuk log — server menentukan pemiliknya sendiri
     * dari `auth.uid()`. Pendaftaran ini juga yang MELEPAS ikatan akun
     * sebelumnya: satu device hanya boleh punya satu baris di `fcm_tokens`,
     * kalau tidak HP bekas akun lain terus dikirimi notifikasi outlet lamanya.
     */
    suspend fun registerCurrentToken(staffId: String, outletId: String?) {
        val token = try {
            fetchToken() ?: run {
                Log.e(TAG, "FCM token fetch returned null — device tidak dapat token dari Firebase")
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "FCM token fetch gagal", e)
            return
        }
        try {
            val res = SupabaseClient.api.registerFcmToken(
                payload = RegisterFcmTokenPayload(token = token, outletId = outletId)
            )
            // Retrofit tidak melempar exception untuk 4xx/5xx pada Response<T>, jadi
            // tanpa cek eksplisit penolakan RLS ditelan diam-diam.
            if (res.isSuccessful) {
                Log.d(TAG, "FCM token terdaftar untuk staff=$staffId outlet=$outletId")
            } else {
                Log.e(TAG, "FCM token register DITOLAK ${res.code()}: ${res.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "FCM token register error", e)
        }
    }

    /** Call BEFORE clearing the session token — deletion is RLS-scoped to auth.uid(). */
    suspend fun unregisterCurrentToken() {
        val token = try {
            fetchToken() ?: return
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }
        try {
            SupabaseClient.api.deleteFcmToken(tokenFilter = "eq.$token")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun fetchToken(): String? = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                cont.resume(task.result, onCancellation = null)
            } else {
                Log.e(TAG, "FirebaseMessaging.getToken gagal", task.exception)
                cont.resume(null, onCancellation = null)
            }
        }
    }
}
