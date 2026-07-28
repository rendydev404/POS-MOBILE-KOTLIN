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
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

/**
 * Realtime channel for `menu_items` + `kiosk_settings`, independent from
 * `OrderRealtimeManager` — mirrors how the web app opens a separate
 * `supabase.channel(...)` per page (KasirMenuClient.tsx vs KasirOrderClient.tsx)
 * rather than sharing one subscription across screens.
 */
class MenuRealtimeManager(
    private val client: OkHttpClient,
    private val scope: CoroutineScope
) {
    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private val refCounter = AtomicInteger(1)
    private val topic = "realtime:public:menu"

    var onChange: ((table: String, eventType: String, record: JSONObject) -> Unit)? = null

    fun connect() {
        disconnect()
        val anonKey = BuildConfig.SUPABASE_ANON_KEY
        val wsUrl = com.sukashawarma.pos.data.remote.SupabaseClient.BASE_URL
            .replace("https://", "wss://") + "realtime/v1/websocket?apikey=$anonKey&vsn=1.0.0"

        val request = Request.Builder().url(wsUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: OkResponse) {
                joinChannel(webSocket)
                startHeartbeat(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: OkResponse?) {
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {}
        })
    }

    private fun joinChannel(ws: WebSocket) {
        val joinPayload = JSONObject().apply {
            put("topic", topic)
            put("event", "phx_join")
            put("payload", JSONObject().apply {
                put("config", JSONObject().apply {
                    put(
                        "postgres_changes",
                        JSONArray().apply {
                            put(JSONObject().apply {
                                put("event", "*"); put("schema", "public"); put("table", "menu_items")
                            })
                            put(JSONObject().apply {
                                put("event", "*"); put("schema", "public"); put("table", "kiosk_settings")
                            })
                        }
                    )
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

    private fun scheduleReconnect() {
        scope.launch {
            delay(5_000)
            connect()
        }
    }

    private fun handleMessage(text: String) {
        try {
            val json = JSONObject(text)
            if (json.optString("event") != "postgres_changes") return
            val payload = json.optJSONObject("payload") ?: return
            val data = payload.optJSONObject("data") ?: return
            val table = data.optString("table")
            val eventType = data.optString("type")
            val record = data.optJSONObject("record") ?: return
            onChange?.invoke(table, eventType, record)
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
