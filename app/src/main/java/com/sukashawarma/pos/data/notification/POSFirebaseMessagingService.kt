package com.sukashawarma.pos.data.notification

import android.Manifest
import android.app.NotificationManager
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.text.HtmlCompat
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
        android.util.Log.d("POS_DEBUG", "FCM Message received: data=${message.data}, notification=${message.notification}")

        val isOwnerMessage = message.data["type"] == "broadcast" || message.data["type"] == "owner_message"
        val isPettyCash = message.data["type"] == "petty_cash"

        // Hanya bunyikan alert jika bukan pesan dari owner dan bukan petty cash
        if (isOwnerMessage) {
            OrderAlertPlayer(applicationContext).playOwnerMessageAlert()
        } else if (!isPettyCash) {
            OrderAlertPlayer(applicationContext).playNewOrderAlert()
        }

        var title: CharSequence = message.notification?.title ?: message.data["title"] ?: "Pesanan Baru Masuk"
        val body = message.notification?.body ?: message.data["body"] ?: "Ada pesanan baru menunggu diproses."

        if (isOwnerMessage) {
            title = "PESAN DARI OWNER: $title"
        }

        // Id record, bukan timestamp: kalau POSRealtimeService sudah menampilkan
        // notifikasi yang sama, keduanya menimpa satu sama lain, bukan menumpuk.
        val notificationId = message.data["id"]?.takeIf { it.isNotBlank() }?.hashCode()
            ?: System.currentTimeMillis().toInt()
            
        val channelId = if (isPettyCash) NotificationChannels.SYSTEM_ALERTS_CHANNEL_ID else NotificationChannels.NEW_ORDER_CHANNEL_ID
        showSystemNotification(notificationId, title, body, channelId)

        // Layar yang sedang terbuka ikut menyegarkan diri saat push masuk.
        if (isOwnerMessage) {
            com.sukashawarma.pos.data.remote.GlobalEventBus.ownerMessageRefreshEvent.tryEmit(Unit)
        } else if (isPettyCash) {
            com.sukashawarma.pos.data.remote.GlobalEventBus.pettyCashEvent.tryEmit(Unit)
        } else {
            com.sukashawarma.pos.data.remote.GlobalEventBus.orderSyncEvent.tryEmit(Unit)
        }
    }

    private fun showSystemNotification(id: Int, title: CharSequence, body: String, channelId: String = NotificationChannels.NEW_ORDER_CHANNEL_ID) {
        val intent = android.content.Intent(this, com.sukashawarma.pos.presentation.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            if (channelId == NotificationChannels.SYSTEM_ALERTS_CHANNEL_ID) {
                putExtra("destination", "petty_cash")
            }
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        android.util.Log.d("POS_DEBUG", "FCM Notification builder created, checking permission...")

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            android.util.Log.d("POS_DEBUG", "FCM Permission POST_NOTIFICATIONS is NOT granted!")
            return
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(id, notification)
        android.util.Log.d("POS_DEBUG", "FCM Notification Manager notify called with id $id")
    }
}
