package com.sukashawarma.pos.data.remote.realtime

import com.sukashawarma.pos.BuildConfig
import com.sukashawarma.pos.data.remote.SessionTokenHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.Response as OkResponse
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

/**
 * Minimal Supabase Realtime (Phoenix channel over WebSocket) client for the
 * `orders` table, scoped to one outlet — mirrors what the web app gets from
 * `supabase.channel(...).on('postgres_changes', ...)`.
 *
 * We hand-roll this instead of pulling the full Supabase Kotlin SDK to keep the
 * dependency footprint small; the wire protocol is a handful of JSON messages.
 */
class OrderRealtimeManager(
    private val client: OkHttpClient,
    private val scope: CoroutineScope
) {
    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private val refCounter = AtomicInteger(1)
    private val topic get() = "realtime:public:orders"

    var onChange: ((eventType: String, orderJson: JSONObject) -> Unit)? = null
    var onConnectionState: ((connected: Boolean) -> Unit)? = null

    fun connect(outletId: String) {
        disconnect()
        if (outletId.isBlank()) return

        val anonKey = BuildConfig.SUPABASE_ANON_KEY
        val wsUrl = com.sukashawarma.pos.data.remote.SupabaseClient.BASE_URL
            .replace("https://", "wss://") + "realtime/v1/websocket?apikey=$anonKey&vsn=1.0.0"

        val request = Request.Builder().url(wsUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: OkResponse) {
                joinChannel(webSocket, outletId)
                startHeartbeat(webSocket)
                onConnectionState?.invoke(true)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: OkResponse?) {
                onConnectionState?.invoke(false)
                scheduleReconnect(outletId)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onConnectionState?.invoke(false)
            }
        })
    }

    private fun joinChannel(ws: WebSocket, outletId: String) {
        val joinPayload = JSONObject().apply {
            put("topic", topic)
            put("event", "phx_join")
            put("payload", JSONObject().apply {
                put("config", JSONObject().apply {
                    put("postgres_changes", org.json.JSONArray().put(
                        JSONObject().apply {
                            put("event", "*")
                            put("schema", "public")
                            put("table", "orders")
                            put("filter", "outlet_id=eq.$outletId")
                        }
                    ))
                })
                put("access_token", SessionTokenHolder.accessToken ?: BuildConfig.SUPABASE_ANON_KEY)
            })
            put("ref", refCounter.getAndIncrement().toString())
        }
        ws.send(joinPayload.toString())
    }

    private fun startHeartbeat(ws: WebSocket) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(25_000)
                val hb = JSONObject().apply {
                    put("topic", "phoenix")
                    put("event", "heartbeat")
                    put("payload", JSONObject())
                    put("ref", refCounter.getAndIncrement().toString())
                }
                ws.send(hb.toString())
            }
        }
    }

    private fun scheduleReconnect(outletId: String) {
        scope.launch {
            delay(5_000)
            connect(outletId)
        }
    }

    private fun handleMessage(text: String) {
        try {
            val json = JSONObject(text)
            if (json.optString("event") != "postgres_changes") return
            val payload = json.optJSONObject("payload") ?: return
            val data = payload.optJSONObject("data") ?: return
            val eventType = data.optString("type") // INSERT | UPDATE | DELETE
            val record = data.optJSONObject("record") ?: return
            onChange?.invoke(eventType, record)
        } catch (_: Exception) {
            // Malformed/unrelated frame (e.g. phx_reply) — ignore.
        }
    }

    fun disconnect() {
        heartbeatJob?.cancel()
        webSocket?.close(1000, "bye")
        webSocket = null
    }
}
