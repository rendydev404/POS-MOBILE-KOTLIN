package com.sukashawarma.pos.data.notification

import android.Manifest
import android.app.NotificationManager
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.sukashawarma.pos.R
import com.sukashawarma.pos.data.local.SessionPrefs
import com.sukashawarma.pos.data.remote.AuthSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles FCM pushes for "pesanan masuk" while the app is backgrounded or the
 * process was killed. Foreground alerts are already covered by
 * OrderRealtimeManager (Fase 2) — this is the fallback for when that WebSocket
 * isn't alive because Android suspended/killed the app.
 */
class POSFirebaseMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        scope.launch {
            val staffId = SessionPrefs.getStaffId() ?: return@launch
            if (!AuthSessionManager.ensureAuthenticated()) return@launch
            FcmTokenRegistrar.registerCurrentToken(staffId, SessionPrefs.getOutletId())
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        OrderAlertPlayer(applicationContext).playNewOrderAlert()

        val title = message.notification?.title ?: message.data["title"] ?: "Pesanan Baru Masuk"
        val body = message.notification?.body ?: message.data["body"] ?: "Ada pesanan baru menunggu diproses."
        showSystemNotification(title, body)
    }

    private fun showSystemNotification(title: String, body: String) {
        val notification = NotificationCompat.Builder(this, NotificationChannels.NEW_ORDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(System.currentTimeMillis().toInt(), notification)
    }
}
