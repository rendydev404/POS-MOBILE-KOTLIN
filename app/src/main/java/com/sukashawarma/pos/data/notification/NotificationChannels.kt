package com.sukashawarma.pos.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val NEW_ORDER_CHANNEL_ID = "new_order_alerts"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            NEW_ORDER_CHANNEL_ID,
            "Pesanan Masuk",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifikasi pesanan baru dari Kiosk/Online saat aplikasi di-background"
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }
}
