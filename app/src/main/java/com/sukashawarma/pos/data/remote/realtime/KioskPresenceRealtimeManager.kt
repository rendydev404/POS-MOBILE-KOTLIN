package com.sukashawarma.pos.data.remote.realtime

import com.sukashawarma.pos.BuildConfig
import com.sukashawarma.pos.data.remote.SessionTokenHolder
import com.sukashawarma.pos.data.remote.SupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response as OkResponse
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

data class KioskPresence(
    val userId: String,
    val username: String,
    val deviceLabel: String,
    val onlineAt: String
)

internal fun normalizedKioskPresence(
    userId: String,
    username: String?,
    deviceLabel: String?,
    onlineAt: String?
): KioskPresence {
    val resolvedUsername = username?.takeIf(String::isNotBlank) ?: userId
    return KioskPresence(
        userId = userId,
        username = resolvedUsername,
        deviceLabel = deviceLabel?.takeIf(String::isNotBlank) ?: resolvedUsername.ifBlank { "Kiosk" },
        onlineAt = onlineAt.orEmpty()
    )
}

/** Presence-only channel matching web `kiosk-control:<outletId>`. */
class KioskPresenceRealtimeManager(private val scope: CoroutineScope) {
    private val refs = AtomicInteger(1)
    private var socket: WebSocket? = null
    private var heartbeat: Job? = null
    private var outletId = ""
    private val members = linkedMapOf<String, KioskPresence>()
    var onPresence: ((List<KioskPresence>) -> Unit)? = null
    var onConnection: ((Boolean) -> Unit)? = null

    fun connect(outlet: String) {
        disconnect()
        if (outlet.isBlank()) return
        outletId = outlet
        val url = SupabaseClient.BASE_URL.replace("https://", "wss://") +
            "realtime/v1/websocket?apikey=${BuildConfig.SUPABASE_ANON_KEY}&vsn=1.0.0"
        socket = SupabaseClient.okHttpClient.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: OkResponse) {
                    val token = SessionTokenHolder.accessToken ?: BuildConfig.SUPABASE_ANON_KEY
                    webSocket.send(JSONObject().apply {
                        put("topic", topic())
                        put("event", "phx_join")
                        put("payload", JSONObject().apply {
                            put("config", JSONObject().apply {
                                put("broadcast", JSONObject().put("self", false))
                                put("presence", JSONObject().put("key", "cashier-control"))
                            })
                            put("access_token", token)
                        })
                        put("ref", refs.getAndIncrement().toString())
                    }.toString())
                    heartbeat = scope.launch {
                        while (isActive && socket === webSocket) {
                            delay(25_000)
                            webSocket.send(JSONObject().apply {
                                put("topic", "phoenix")
                                put("event", "heartbeat")
                                put("payload", JSONObject())
                                put("ref", refs.getAndIncrement().toString())
                            }.toString())
                        }
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) = handle(text)
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: OkResponse?) = disconnected()
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = disconnected()
            }
        )
    }

    /** Sama seperti tombol Refresh web: buat ulang channel lalu minta kiosk re-track. */
    fun refresh() {
        val currentOutlet = outletId
        if (currentOutlet.isNotBlank()) connect(currentOutlet)
    }

    private fun handle(text: String) {
        try {
            val frame = JSONObject(text)
            when (frame.optString("event")) {
                "phx_reply" -> if (frame.optJSONObject("payload")?.optString("status") == "ok" &&
                    frame.optString("topic") == topic()
                ) {
                    onConnection?.invoke(true)
                    requestPresence()
                }
                "presence_state" -> {
                    members.clear()
                    val payload = frame.optJSONObject("payload") ?: JSONObject()
                    payload.keys().forEach { userId -> members[userId] = payload.presence(userId) }
                    emitPresence()
                }
                "presence_diff" -> {
                    val payload = frame.optJSONObject("payload") ?: JSONObject()
                    payload.optJSONObject("joins")?.let { joins ->
                        joins.keys().forEach { userId -> members[userId] = joins.presence(userId) }
                    }
                    payload.optJSONObject("leaves")?.keys()?.forEach { members.remove(it) }
                    emitPresence()
                }
                "phx_error", "phx_close" -> disconnected()
            }
        } catch (_: Exception) {
            // Frame realtime asing/malformed tidak boleh menjatuhkan layar kasir.
        }
    }

    private fun requestPresence() {
        socket?.send(JSONObject().apply {
            put("topic", topic())
            put("event", "broadcast")
            put("payload", JSONObject().apply {
                put("type", "broadcast")
                put("event", "request_presence")
                put("payload", JSONObject())
            })
            put("ref", refs.getAndIncrement().toString())
        }.toString())
    }

    private fun JSONObject.presence(userId: String): KioskPresence {
        val meta = optJSONObject(userId)?.optJSONArray("metas")?.optJSONObject(0) ?: JSONObject()
        return normalizedKioskPresence(
            userId = userId,
            username = meta.optString("username"),
            deviceLabel = meta.optString("device_label"),
            onlineAt = meta.optString("online_at")
        )
    }

    private fun emitPresence() {
        onPresence?.invoke(members.values.sortedBy { it.deviceLabel.lowercase() })
    }

    private fun topic() = "realtime:kiosk-control:$outletId"

    private fun disconnected() {
        onConnection?.invoke(false)
    }

    fun disconnect() {
        heartbeat?.cancel()
        heartbeat = null
        socket?.close(1000, "bye")
        socket = null
        members.clear()
        onPresence?.invoke(emptyList())
        onConnection?.invoke(false)
    }
}
