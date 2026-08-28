package com.sukashawarma.pos.data.camera

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.sukashawarma.pos.R
import com.sukashawarma.pos.data.remote.AuthSessionManager
import com.sukashawarma.pos.data.remote.SupabaseClient
import io.livekit.android.LiveKit
import io.livekit.android.RoomOptions
import io.livekit.android.room.Room
import io.livekit.android.room.track.CameraPosition
import io.livekit.android.room.track.LocalVideoTrackOptions
import io.livekit.android.room.track.VideoPreset169
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Publishes exactly one front-camera track for the currently logged-in outlet.
 *
 * This service intentionally has no recording path: frames go directly to the
 * configured LiveKit room and are discarded after transport. It is separate
 * from POSRealtimeService so order synchronization keeps working if a camera
 * session fails or is stopped.
 */
class LiveCameraService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var outletId: String = ""
    private var room: Room? = null
    @Volatile private var isPublishing = false
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0

    override fun onCreate() {
        super.onCreate()
        if (!ENABLED) {
            stopSelf()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return
        }
        createNotificationChannel()
        ServiceCompat.startForeground(
            this,
            FOREGROUND_NOTIFICATION_ID,
            createForegroundNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            } else {
                0
            },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!ENABLED) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val requestedOutletId = intent?.getStringExtra(EXTRA_OUTLET_ID)
        if (requestedOutletId.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        val outletChanged = requestedOutletId != outletId
        if (outletChanged || !isPublishing) {
            outletId = requestedOutletId
            if (outletChanged) {
                reconnectJob?.cancel()
                reconnectJob = null
                reconnectAttempt = 0
                heartbeatJob?.cancel()
                room?.disconnect()
                room = null
                isPublishing = false
            }
            if (!isPublishing) serviceScope.launch { connectAndPublish() }
        }
        return START_NOT_STICKY
    }

    private suspend fun connectAndPublish() {
        if (isPublishing) return
        isPublishing = true
        try {
            if (!AuthSessionManager.ensureAuthenticated()) {
                throw IllegalStateException("Sesi POS tidak tersedia")
            }
            val session = requestSession(action = "start")
            val nextRoom = LiveKit.create(
                appContext = applicationContext,
                options = RoomOptions(
                    dynacast = true,
                    // Explicitly request the front camera at the configured 720p profile.
                    videoTrackCaptureDefaults = LocalVideoTrackOptions(
                        position = CameraPosition.FRONT,
                        captureParams = VideoPreset169.H720.capture,
                    ),
                ),
            )
            room?.disconnect()
            room = nextRoom
            nextRoom.connect(url = session.serverUrl, token = session.token)
            // No microphone track is enabled or published.
            val cameraEnabled = nextRoom.localParticipant.setCameraEnabled(true)
            if (!cameraEnabled || nextRoom.localParticipant.getTrackPublication(io.livekit.android.room.track.Track.Source.CAMERA) == null) {
                throw IllegalStateException("Kamera gagal dipublikasikan ke server")
            }
            reconnectAttempt = 0

            heartbeatJob?.cancel()
            heartbeatJob = serviceScope.launch {
                while (isActive && room === nextRoom) {
                    delay(HEARTBEAT_INTERVAL_MS)
                    if (nextRoom.state == Room.State.DISCONNECTED) {
                        runCatching { requestSession(action = "error", errorMessage = "Koneksi kamera terputus") }
                        nextRoom.disconnect()
                        if (room === nextRoom) {
                            room = null
                            isPublishing = false
                        }
                        scheduleReconnect()
                        break
                    }
                    // Keep the SDK's built-in reconnect flow alive during
                    // transient CONNECTING/RECONNECTING states. The room is
                    // only torn down after it reaches DISCONNECTED.
                    if (nextRoom.state == Room.State.CONNECTED) {
                        runCatching { requestSession(action = "heartbeat") }
                            .onFailure { android.util.Log.w(TAG, "Live camera heartbeat failed", it) }
                    }
                }
            }
        } catch (error: Throwable) {
            android.util.Log.e(TAG, "Live camera failed", error)
            heartbeatJob?.cancel()
            heartbeatJob = null
            runCatching { requestSession(action = "error", errorMessage = error.message) }
            room?.disconnect()
            room = null
            scheduleReconnect()
        } finally {
            isPublishing = room != null
        }
    }

    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true || outletId.isBlank()) return
        val targetOutlet = outletId
        val backoff = (INITIAL_RECONNECT_DELAY_MS shl reconnectAttempt.coerceAtMost(5)).coerceAtMost(MAX_RECONNECT_DELAY_MS)
        reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(6)
        reconnectJob = serviceScope.launch {
            delay(backoff)
            reconnectJob = null
            if (isActive && outletId == targetOutlet && !isPublishing) connectAndPublish()
        }
    }

    private suspend fun requestSession(action: String, errorMessage: String? = null): CameraSession {
        val payload = JSONObject()
            .put("outlet_id", outletId)
            .put("mode", "publisher")
            .put("action", action)
        if (!errorMessage.isNullOrBlank()) payload.put("error_message", errorMessage.take(MAX_ERROR_LENGTH))

        val request = Request.Builder()
            .url("${SupabaseClient.BASE_URL}functions/v1/livekit-token")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        SupabaseClient.okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException("Live camera token gagal (${response.code})")
            val json = JSONObject(body)
            return CameraSession(
                serverUrl = json.getString("server_url"),
                token = json.getString("participant_token"),
            )
        }
    }

    override fun onDestroy() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        room?.disconnect()
        room = null
        isPublishing = false
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "POS aktif", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun createForegroundNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("Suka Shawarma POS")
        .setContentText("Layanan POS aktif")
        .setOngoing(true)
        .build()

    private data class CameraSession(val serverUrl: String, val token: String)

    companion object {
        private const val TAG = "LiveCameraService"
        // Feature flag: keep the implementation in place while the rollout is paused.
        const val ENABLED = false
        private const val CHANNEL_ID = "pos_live_camera"
        private const val FOREGROUND_NOTIFICATION_ID = 1002
        private const val EXTRA_OUTLET_ID = "outlet_id"
        private const val HEARTBEAT_INTERVAL_MS = 30_000L
        private const val INITIAL_RECONNECT_DELAY_MS = 1_000L
        private const val MAX_RECONNECT_DELAY_MS = 30_000L
        private const val MAX_ERROR_LENGTH = 300
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun start(context: Context, outletId: String) {
            if (!ENABLED) return
            val intent = Intent(context, LiveCameraService::class.java).putExtra(EXTRA_OUTLET_ID, outletId)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LiveCameraService::class.java))
        }
    }
}
