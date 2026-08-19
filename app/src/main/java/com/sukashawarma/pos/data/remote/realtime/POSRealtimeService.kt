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
import kotlinx.coroutines.launch

class POSRealtimeService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var realtimeManager: OrderRealtimeManager
    private lateinit var onlineSyncManager: OrderOnlineSyncManager
    private lateinit var alertPlayer: OrderAlertPlayer
    
    private var currentOutletId: String = ""

    private lateinit var orderDao: com.sukashawarma.pos.data.local.dao.OrderDao

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundNotification())
        
        orderDao = (application as com.sukashawarma.pos.POSApplication).database.orderDao()
        
        alertPlayer = OrderAlertPlayer(this)
        realtimeManager = OrderRealtimeManager(SupabaseClient.okHttpClient, serviceScope)
        onlineSyncManager = OrderOnlineSyncManager(SupabaseClient.okHttpClient, serviceScope)
        
        realtimeManager.onConnectionState = { connected ->
            GlobalEventBus.isRealtimeConnected.value = connected
            if (connected) {
                // Event yang terjadi selama socket putus tidak dikirim ulang oleh
                // Supabase, jadi tarik ulang state begitu tersambung lagi.
                GlobalEventBus.orderSyncEvent.tryEmit(Unit)
                GlobalEventBus.ownerMessageRefreshEvent.tryEmit(Unit)
                GlobalEventBus.targetRefreshEvent.tryEmit(Unit)
                GlobalEventBus.pettyCashEvent.tryEmit(Unit)
            }
        }

        serviceScope.launch {
            com.sukashawarma.pos.data.remote.NetworkMonitor.isOnline.collect { online ->
                if (online) realtimeManager.reconnectNow()
            }
        }

        onlineSyncManager.connect()
        
        realtimeManager.onChange = { table, eventType, record ->
            if (table == "orders") {
                // Fallback to currentOutletId if it's missing in the payload (common in UPDATE events without FULL replica identity)
                val recordOutletId = record.optString("outlet_id", currentOutletId)
                val recordSource = record.optString("source", "pos")
                
                // Because we filter by outlet_id=eq.$outletId at the Supabase channel level,
                // any event received here is inherently for our outlet.
                if (recordOutletId == currentOutletId || recordOutletId.isEmpty()) {
                    // ANTI-DELAY: update local db immediately from realtime payload!
                    val orderId = record.optString("id")
                    val status = record.optString("status")
                    val cancellationStatus: String? = if (record.isNull("cancellation_status")) null else record.optString("cancellation_status")
                    val cancellationUserName: String? = if (record.isNull("cancellation_user_name")) null else record.optString("cancellation_user_name")
                    
                    serviceScope.launch {
                        if (orderId.isNotBlank()) {
                            val existing = orderDao.getOrderById(orderId)
                            val isCurrentlyCancelled = existing?.status == com.sukashawarma.pos.domain.model.OrderStatus.CANCELLED.name
                            
                            val isIncomingCancelled = cancellationStatus == "approved" || status.equals("cancelled", ignoreCase = true)
                            
                            val mappedStatus = try {
                                com.sukashawarma.pos.domain.model.OrderStatus.valueOf(status.uppercase()).name
                            } catch (e: Exception) {
                                com.sukashawarma.pos.domain.model.OrderStatus.PENDING.name
                            }

                            val finalStatus = if (isIncomingCancelled || isCurrentlyCancelled) {
                                com.sukashawarma.pos.domain.model.OrderStatus.CANCELLED.name
                            } else {
                                mappedStatus
                            }
                            
                            orderDao.updateOrderStatus(orderId, finalStatus)
                            
                            // 'none' adalah nilai default kolom `cancellation_status`,
                            // artinya "belum ada pembatalan" — sama saja dengan NULL.
                            val incomingKosong = cancellationStatus.isNullOrBlank() ||
                                cancellationStatus.equals("none", ignoreCase = true)
                            val menungguPersetujuan = existing?.cancellationStatus == "pending_approval"

                            if (isCurrentlyCancelled && !isIncomingCancelled) {
                                // Do not downgrade cancellation_status if the incoming event is a stale order record
                            } else {
                                if (isIncomingCancelled) {
                                    orderDao.updateCancellationStatus(orderId, "approved", cancellationUserName)
                                } else if (menungguPersetujuan && incomingKosong &&
                                    com.sukashawarma.pos.data.sync.PendingCancellationGuard.isProtected(orderId)
                                ) {
                                    // Event lain (mis. perubahan status pesanan) tidak boleh
                                    // membatalkan pengajuan yang baru saja diajukan. Setelah
                                    // jendela perlindungan lewat, 'none' dari server berarti
                                    // AM menolak — dan itu harus mendarat.
                                } else if (menungguPersetujuan && incomingKosong) {
                                    orderDao.updateCancellationStatus(orderId, null, null)
                                } else if (!incomingKosong) {
                                    orderDao.updateCancellationStatus(orderId, cancellationStatus, cancellationUserName)
                                }
                            }
                        }
                    }

                    GlobalEventBus.orderSyncEvent.tryEmit(Unit)
                    // Omzet berubah -> progress target harian harus ikut, tanpa
                    // menunggu poll berkala di banner.
                    GlobalEventBus.targetRefreshEvent.tryEmit(Unit)

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
            } else if (table == "petty_cash_topups" || table == "petty_cash_expenses" || table == "shifts") {
                val recordOutletId = record.optString("outlet_id", "")
                
                // Hanya memproses (dan refresh UI) jika outlet sesuai
                if (recordOutletId.isEmpty() || recordOutletId == currentOutletId) {
                    GlobalEventBus.pettyCashEvent.tryEmit(Unit)

                    // Jika table petty_cash_topups, dan eventType INSERT/UPDATE (hanya jika ada id dan bukan POS sendiri)
                    // Cek jika FCM belum mengirim, kita kirim local notif
                    if (table == "petty_cash_topups" && (eventType == "INSERT" || eventType == "UPDATE")) {
                        val amount = record.optDouble("amount", 0.0)
                        val status = record.optString("status", "")
                        
                        val amountStr = if (amount > 0) "Rp ${java.text.NumberFormat.getInstance(java.util.Locale("id", "ID")).format(amount)}" else ""
                        var title = "Update Petty Cash"
                        var body = "Data petty cash Anda telah diperbarui."
                        var shouldShow = false
                        
                        if (status == "forwarded_to_area_manager") {
                            title = "Status Petty Cash"
                            body = "Menunggu review AM."
                            shouldShow = true
                        } else if (status == "forwarded_to_finance") {
                            title = "Status Petty Cash"
                            body = "Sedang menunggu pencairan Finance."
                            shouldShow = true
                        } else if (status == "approved_by_finance" || status == "forwarded_by_finance") {
                            title = "Status Petty Cash"
                            body = "Dana ada di AM."
                            shouldShow = true
                        } else if (status == "forwarded_by_area_manager") {
                            title = "Status Petty Cash"
                            body = "Dana sudah diserahkan oleh AM ke Leader."
                            shouldShow = true
                        } else if (status == "completed" || status == "forwarded_by_leader" || status == "approved") {
                            title = "Petty Cash Cair"
                            body = "Saldo petty cash sudah masuk, Gunakan dengan sebaik-baiknya yaa😇😇"
                            shouldShow = true
                        } else if (status == "pending" && eventType == "INSERT") {
                            title = "Top Up Baru"
                            body = "Pengajuan top up petty cash menunggu persetujuan."
                            shouldShow = true
                        } else if (status == "rejected" || status == "cancelled") {
                            title = "Top Up Dibatalkan"
                            body = "Pengajuan top up petty cash ditolak/dibatalkan."
                            shouldShow = true
                        }
                        
                        if (shouldShow) {
                            val msgId = record.optString("id").hashCode()
                            showSystemPushNotification(msgId, title, body)
                        }
                    }
                }
            } else if (table == "cancellation_requests") {
                val orderId = record.optString("order_id")
                val status = record.optString("status")
                
                serviceScope.launch {
                    if (orderId.isNotBlank()) {
                        // Keputusan AM selalu menang atas status lokal yang optimistis.
                        com.sukashawarma.pos.data.sync.PendingCancellationGuard.clear(orderId)
                        if (status == "approved") {
                            orderDao.updateOrderStatus(orderId, com.sukashawarma.pos.domain.model.OrderStatus.CANCELLED.name)
                            orderDao.updateCancellationStatus(orderId, "approved", null)
                        } else if (status == "rejected") {
                            // Dulu cabang ini tidak ada sama sekali: penolakan tidak
                            // pernah diterapkan, jadi kartu terus menampilkan
                            // "Menunggu Persetujuan Batal" tanpa akhir. Pesanan tidak
                            // jadi dibatalkan, jadi status aslinya dibiarkan utuh.
                            orderDao.updateCancellationStatus(orderId, null, null)
                        }
                    }
                }
                GlobalEventBus.orderSyncEvent.tryEmit(Unit)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Service dapat hidup saat Activity tidak ada; cek ini juga memicu
        // download melalui AppUpdateManager tanpa menunggu UI dikomposisi.
        com.sukashawarma.pos.data.update.AppUpdateManager.checkForUpdateAsync()
        val outletId = intent?.getStringExtra(EXTRA_OUTLET_ID)
        if (!outletId.isNullOrBlank() && outletId != currentOutletId) {
            currentOutletId = outletId
            realtimeManager.connect(currentOutletId)
        } else if (intent?.getBooleanExtra(EXTRA_RESUME, false) == true) {
            // Layar tidur/app di background membuat socket mati diam-diam; tanpa ini
            // layar baru menyusul setelah heartbeat timeout (~70 detik).
            realtimeManager.reconnectNow()
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

    private fun showSystemPushNotification(id: Int, title: String, body: String) {
        val intent = Intent(this, com.sukashawarma.pos.presentation.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("destination", "petty_cash")
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, NotificationChannels.SYSTEM_ALERTS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
            
        if (androidx.core.app.ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            val manager = getSystemService(NotificationManager::class.java)
            manager?.notify(id, notification)
        }
    }

    companion object {
        const val EXTRA_OUTLET_ID = "outlet_id"
        const val EXTRA_RESUME = "resume"
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

        /**
         * Sambung ulang begitu app kembali ke depan. `outletId` tetap dikirim agar
         * channel tetap pulih walau service sempat dimatikan sistem.
         */
        fun resume(context: Context, outletId: String) {
            val intent = Intent(context, POSRealtimeService::class.java).apply {
                putExtra(EXTRA_OUTLET_ID, outletId)
                putExtra(EXTRA_RESUME, true)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
