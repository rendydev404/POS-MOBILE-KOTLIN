package com.sukashawarma.pos.data.remote.realtime

import com.sukashawarma.pos.BuildConfig
import com.sukashawarma.pos.data.remote.AuthSessionManager
import com.sukashawarma.pos.data.remote.SessionTokenHolder
import com.sukashawarma.pos.data.remote.SupabaseClient
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
 * Channel realtime `stok_balance` untuk satu outlet. Setiap perubahan saldo
 * memicu [onStockChanged] supaya marquee stok kritis ikut berubah saat itu juga,
 * meniru POS web yang mem-invalidate query `stock-alerts` dari channel yang sama
 * (lib/useStockAlerts.ts).
 *
 * Dipisah dari [MenuRealtimeManager]/[OrderRealtimeManager] mengikuti pola yang
 * sudah ada di app ini: satu channel per kebutuhan layar, bukan satu channel
 * raksasa yang dibagi-bagi.
 */
class StockRealtimeManager(
    private val client: OkHttpClient,
    private val scope: CoroutineScope
) {
    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private val refCounter = AtomicInteger(1)
    private var outletId: String = ""
    private val topic get() = "realtime:public:stok_balance:$outletId"
    @Volatile private var channelToken: String? = null

    var onStockChanged: (() -> Unit)? = null

    /** Outlet kosong (mis. setelah logout) berarti cukup tutup channel lama. */
    fun connect(outletId: String) {
        disconnect()
        if (outletId.isBlank()) return
        this.outletId = outletId

        val wsUrl = SupabaseClient.BASE_URL.replace("https://", "wss://") +
            "realtime/v1/websocket?apikey=${BuildConfig.SUPABASE_ANON_KEY}&vsn=1.0.0"

        webSocket = client.newWebSocket(Request.Builder().url(wsUrl).build(), object : WebSocketListener() {
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
        val payload = JSONObject().apply {
            put("topic", topic)
            put("event", "phx_join")
            put("payload", JSONObject().apply {
                put("config", JSONObject().apply {
                    put("postgres_changes", JSONArray().apply {
                        put(JSONObject().apply {
                            put("event", "*")
                            put("schema", "public")
                            put("table", "stok_balance")
                            // Filter di server: outlet lain tidak perlu membangunkan kasir ini.
                            put("filter", "outlet_id=eq.$outletId")
                        })
                    })
                })
                channelToken = SessionTokenHolder.accessToken
                put("access_token", channelToken ?: BuildConfig.SUPABASE_ANON_KEY)
            })
            put("ref", refCounter.getAndIncrement().toString())
        }
        ws.send(payload.toString())
    }

    private fun startHeartbeat(ws: WebSocket) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(25_000)
                // JWT kedaluwarsa membuat server menutup channel tanpa menutup socket —
                // sama seperti manager realtime lain di app ini.
                AuthSessionManager.ensureAuthenticated()
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

                ws.send(JSONObject().apply {
                    put("topic", "phoenix")
                    put("event", "heartbeat")
                    put("payload", JSONObject())
                    put("ref", refCounter.getAndIncrement().toString())
                }.toString())
            }
        }
    }

    private fun scheduleReconnect() {
        val id = outletId
        scope.launch {
            delay(5_000)
            connect(id)
        }
    }

    private fun handleMessage(text: String) {
        try {
            val json = JSONObject(text)
            if (json.optString("event") != "postgres_changes") return
            // Isi baris tidak dipakai: status kritis dihitung view di server, jadi
            // perubahan apa pun cukup jadi aba-aba untuk mengambil ulang daftarnya.
            onStockChanged?.invoke()
        } catch (_: Exception) {
            // Frame lain (phx_reply, dsb.) — abaikan.
        }
    }

    fun disconnect() {
        heartbeatJob?.cancel()
        webSocket?.close(1000, "bye")
        webSocket = null
    }
}
