package com.sukashawarma.pos.data.remote.realtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sukashawarma.pos.R
import com.sukashawarma.pos.data.notification.NotificationChannels
import com.sukashawarma.pos.data.notification.OrderAlertPlayer
import com.sukashawarma.pos.data.remote.GlobalEventBus
import com.sukashawarma.pos.data.remote.SupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class POSRealtimeService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var realtimeManager: OrderRealtimeManager
    private lateinit var onlineSyncManager: OrderOnlineSyncManager
    private lateinit var alertPlayer: OrderAlertPlayer
    
    private var currentOutletId: String = ""

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundNotification())
        
        alertPlayer = OrderAlertPlayer(this)
        realtimeManager = OrderRealtimeManager(SupabaseClient.okHttpClient, serviceScope)
        onlineSyncManager = OrderOnlineSyncManager(SupabaseClient.okHttpClient, serviceScope)
        
        realtimeManager.onConnectionState = { connected ->
            GlobalEventBus.isRealtimeConnected.value = connected
        }
        
        onlineSyncManager.connect()
        
        realtimeManager.onChange = { table, eventType, record ->
            if (table == "orders") {
                val recordOutletId = record.optString("outlet_id")
                val recordSource = record.optString("source", "pos")
                if (recordOutletId == currentOutletId) {
                    GlobalEventBus.orderSyncEvent.tryEmit(Unit)
                    
                    if (eventType == "INSERT" && recordSource.lowercase() != "pos") {
                        alertPlayer.playNewOrderAlert()
                        showPushNotification(
                            id = record.optString("id").hashCode(),
                            title = "Pesanan Baru Masuk",
                            body = "Ada pesanan baru menunggu diproses."
                        )
                    }
                }
            } else if (table == "owner_messages") {
                val msgId = record.optString("id").hashCode()
                android.util.Log.d("POS_DEBUG", "Realtime event for owner_messages: $eventType, record: $record")
                if (eventType == "DELETE") {
                    val manager = getSystemService(NotificationManager::class.java)
                    manager?.cancel(msgId)
                } else if (eventType == "INSERT" || eventType == "UPDATE") {
                    var title = record.optString("title", "Pesan dari Owner")
                    val body = record.optString("body", "Ada pesan baru untuk Anda.")
                    
                    // Tambahkan prefix "PESAN DARI OWNER: " agar konsisten dengan permintaan user
                    title = "PESAN DARI OWNER: $title"
                    
                    android.util.Log.d("POS_DEBUG", "Showing push notification for owner message: $title")
                    alertPlayer.playOwnerMessageAlert()
                    showPushNotification(msgId, title, body)
                }
                GlobalEventBus.ownerMessageRefreshEvent.tryEmit(Unit)
            } else if (table == "daily_sales_targets") {
                GlobalEventBus.targetRefreshEvent.tryEmit(Unit)
            } else if (table == "bypass_requests") {
                GlobalEventBus.bypassRequestEvent.tryEmit(Unit)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val outletId = intent?.getStringExtra(EXTRA_OUTLET_ID)
        if (!outletId.isNullOrBlank() && outletId != currentOutletId) {
            currentOutletId = outletId
            realtimeManager.connect(currentOutletId)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        realtimeManager.disconnect()
        onlineSyncManager.disconnect()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "POS Realtime Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createForegroundNotification(): Notification {
        val intent = Intent(this, com.sukashawarma.pos.presentation.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Suka Shawarma POS")
            .setContentText("Koneksi realtime aktif di latar belakang")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun showPushNotification(id: Int, title: String, body: String) {
        val intent = Intent(this, com.sukashawarma.pos.presentation.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, NotificationChannels.NEW_ORDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
            
        android.util.Log.d("POS_DEBUG", "Notification builder created, checking permission...")
            
        if (androidx.core.app.ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            val manager = getSystemService(NotificationManager::class.java)
            manager?.notify(id, notification)
            android.util.Log.d("POS_DEBUG", "Notification Manager notify called with id $id")
        } else {
            android.util.Log.d("POS_DEBUG", "Permission POST_NOTIFICATIONS is NOT granted!")
        }
    }

    companion object {
        const val EXTRA_OUTLET_ID = "outlet_id"
        private const val CHANNEL_ID = "pos_service_channel"
        private const val FOREGROUND_NOTIFICATION_ID = 1001

        fun start(context: Context, outletId: String) {
            val intent = Intent(context, POSRealtimeService::class.java).apply {
                putExtra(EXTRA_OUTLET_ID, outletId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stop(context: Context) {
            context.stopService(Intent(context, POSRealtimeService::class.java))
        }
    }
}
