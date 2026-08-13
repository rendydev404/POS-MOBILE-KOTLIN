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
    private var reconnectJob: Job? = null
    private val refCounter = AtomicInteger(1)
    private val topic get() = "realtime:public:orders"
    private var currentOutletId: String = ""

    @Volatile private var lastFrameAt = 0L
    /** Token yang sedang dipakai channel — dipakai untuk tahu kapan perlu dikirim ulang. */
    @Volatile private var channelToken: String? = null

    var onChange: ((table: String, eventType: String, record: JSONObject) -> Unit)? = null
    var onConnectionState: ((connected: Boolean) -> Unit)? = null

    fun connect(outletId: String) {
        disconnect()
        if (outletId.isBlank()) return
        currentOutletId = outletId
        lastFrameAt = System.currentTimeMillis()

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
                lastFrameAt = System.currentTimeMillis()
                handleMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: OkResponse?) {
                onConnectionState?.invoke(false)
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onConnectionState?.invoke(false)
                scheduleReconnect()
            }
        })
    }

    private fun joinChannel(ws: WebSocket, outletId: String) {
        val joinPayload = JSONObject().apply {
            put("topic", topic)
            put("event", "phx_join")
            put("payload", JSONObject().apply {
                put("config", JSONObject().apply {
                    put("postgres_changes", org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("event", "*")
                            put("schema", "public")
                            put("table", "orders")
                        })
                        put(JSONObject().apply {
                            put("event", "*")
                            put("schema", "public")
                            put("table", "owner_messages")
                            // No filter so we can get global messages or outlet specific
                        })
                        put(JSONObject().apply {
                            put("event", "*")
                            put("schema", "public")
                            put("table", "daily_sales_targets")
                        })
                        put(JSONObject().apply {
                            put("event", "*")
                            put("schema", "public")
                            put("table", "bypass_requests")
                        })
                        put(JSONObject().apply {
                            put("event", "*")
                            put("schema", "public")
                            put("table", "cancellation_requests")
                        })
                        put(JSONObject().apply {
                            put("event", "*")
                            put("schema", "public")
                            put("table", "petty_cash_topups")
                        })
                        put(JSONObject().apply {
                            put("event", "*")
                            put("schema", "public")
                            put("table", "petty_cash_expenses")
                        })
                        put(JSONObject().apply {
                            put("event", "*")
                            put("schema", "public")
                            put("table", "order_items")
                        })
                    })
                })
                channelToken = SessionTokenHolder.accessToken
                put("access_token", channelToken ?: BuildConfig.SUPABASE_ANON_KEY)
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
                // Di jaringan seluler socket bisa "menggantung": terbuka tapi tidak
                // ada frame masuk lagi. Tanpa cek ini realtime mati diam-diam.
                if (System.currentTimeMillis() - lastFrameAt > 70_000) {
                    onConnectionState?.invoke(false)
                    scheduleReconnect()
                    return@launch
                }
                // Supabase menutup channel begitu JWT-nya kedaluwarsa (~1 jam) tanpa
                // menutup socket-nya, jadi realtime mati diam-diam. Segarkan token
                // lebih dulu, lalu kirim yang baru ke channel.
                com.sukashawarma.pos.data.remote.AuthSessionManager.ensureAuthenticated()
                val token = SessionTokenHolder.accessToken
                if (token != null && token != channelToken) {
                    channelToken = token
                    ws.send(JSONObject().apply {
                        put("topic", topic)
                        put("event", "access_token")
                        put("payload", JSONObject().put("access_token", token))
                        put("ref", refCounter.getAndIncrement().toString())
                    }.toString())
                }

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

    /** Sambung ulang sekarang juga — dipakai saat jaringan kembali online. */
    fun reconnectNow() {
        if (currentOutletId.isNotBlank()) connect(currentOutletId)
    }

    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            delay(5_000)
            if (currentOutletId.isNotBlank()) connect(currentOutletId)
        }
    }

    private fun handleMessage(text: String) {
        try {
            val json = JSONObject(text)
            // JWT kedaluwarsa / RLS menolak -> channel ditutup server tanpa onFailure.
            when (json.optString("event")) {
                "phx_error", "phx_close" -> {
                    onConnectionState?.invoke(false)
                    scheduleReconnect()
                    return
                }
            }
            if (json.optString("event") != "postgres_changes") return
            val payload = json.optJSONObject("payload") ?: return
            val data = payload.optJSONObject("data") ?: payload
            val eventType = data.optString("type") // INSERT | UPDATE | DELETE
            val record = data.optJSONObject("record") ?: data.optJSONObject("old_record") ?: JSONObject()
            val table = data.optString("table")
            onChange?.invoke(table, eventType, record)
        } catch (_: Exception) {
            // Malformed/unrelated frame (e.g. phx_reply) — ignore.
        }
    }

    fun disconnect() {
        heartbeatJob?.cancel()
        reconnectJob?.cancel()
        webSocket?.close(1000, "bye")
        webSocket = null
    }
}
