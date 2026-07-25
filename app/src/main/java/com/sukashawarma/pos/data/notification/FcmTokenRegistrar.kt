package com.sukashawarma.pos.data.notification

import com.google.firebase.messaging.FirebaseMessaging
import com.sukashawarma.pos.data.remote.SupabaseClient
import com.sukashawarma.pos.data.remote.dto.UpsertFcmTokenPayload

/** Shared by POSFirebaseMessagingService.onNewToken and post-login registration. */
object FcmTokenRegistrar {
    suspend fun registerCurrentToken(staffId: String, outletId: String?) {
        val token = try {
            fetchToken() ?: return
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }
        try {
            SupabaseClient.api.upsertFcmToken(
                payload = UpsertFcmTokenPayload(staffId = staffId, outletId = outletId, token = token)
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun fetchToken(): String? = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                cont.resume(task.result, onCancellation = null)
            } else {
                cont.resume(null, onCancellation = null)
            }
        }
    }
}
