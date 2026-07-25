package com.sukashawarma.pos.data.notification

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.sukashawarma.pos.R
import com.sukashawarma.pos.data.local.SessionPrefs
import com.sukashawarma.pos.data.remote.AuthSessionManager
import com.sukashawarma.pos.presentation.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles FCM pushes for "pesanan masuk"/"pesanan dibatalkan". The payload
 * sent by the notify-order edge function is data-only (no top-level
 * `notification` key) on purpose: a `notification` payload makes Android
 * show its own tray notification without calling onMessageReceived() while
 * the app is backgrounded, which would skip the alarm tone/vibration below.
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

        val type = message.data["type"] ?: "new_order"
        val orderId = message.data["order_id"]
        val title = message.data["title"]
            ?: if (type == "order_cancelled") "Pesanan Dibatalkan" else "Pesanan Baru Masuk"
        val body = message.data["body"] ?: "Ada pesanan baru menunggu diproses."
        showSystemNotification(title, body, orderId)
    }

    private fun showSystemNotification(title: String, body: String, orderId: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            orderId?.let { putExtra(MainActivity.EXTRA_ORDER_ID, it) }
        }
        val requestCode = orderId?.hashCode() ?: System.currentTimeMillis().toInt()
        val pendingIntent = PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NotificationChannels.NEW_ORDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(requestCode, notification)
    }
}
