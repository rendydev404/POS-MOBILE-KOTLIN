package com.sukashawarma.pos.data.remote.realtime

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
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

/**
 * Syncs online orders from the separate Online Order Supabase project.
 * Equivalent to OnlineOrderSync.tsx in Web POS.
 */
class OrderOnlineSyncManager(
    private val client: OkHttpClient,
    private val scope: CoroutineScope
) {
    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private val refCounter = AtomicInteger(1)
    
    private val SS_ORDER_URL = "https://qntuhtkujpwudcpudwbj.supabase.co"
    private val SS_ORDER_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InFudHVodGt1anB3dWRjcHVkd2JqIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzkyNTMyNjcsImV4cCI6MjA5NDgyOTI2N30.X2pjS2ont0ekVVc71HLacM2I49aLeypLRRgoPQV6OTw"
    // Dulu lewat pos-kasir (proxy ke DB via Next.js API route) — sekarang langsung
    // ke Edge Function di project Order-Online sendiri, supaya native tidak
    // berhenti menerima order website kalau pos-kasir sedang down.
    private val ORDER_ONLINE_FUNCTIONS_BASE = com.sukashawarma.pos.data.remote.OrderOnlineEndpoints.FUNCTIONS_BASE
    
    private val knownOrders = mutableSetOf<String>()
    private val inFlightPulls = mutableSetOf<String>()

    fun connect() {
        disconnect()

        val wsUrl = SS_ORDER_URL.replace("https://", "wss://") + "/realtime/v1/websocket?apikey=$SS_ORDER_KEY&vsn=1.0.0"

        val request = Request.Builder().url(wsUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: OkResponse) {
                joinChannel(webSocket)
                startHeartbeat(webSocket)
                // Satu sinkronisasi awal untuk menangkap status yang berubah selama
                // socket putus; sisanya murni event-driven lewat postgres_changes,
                // bukan polling berkala.
                syncActiveOrderStatuses()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: OkResponse?) {
                scheduleReconnect()
            }
        })
    }

    private fun joinChannel(ws: WebSocket) {
        val joinPayload = JSONObject().apply {
            put("topic", "realtime:public:orders:paid") // Match web pos behavior or just listen to all and filter
            put("event", "phx_join")
            put("payload", JSONObject().apply {
                put("config", JSONObject().apply {
                    put("postgres_changes", org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("event", "*")
                            put("schema", "public")
                            put("table", "orders")
                        })
                    })
                })
                put("access_token", SS_ORDER_KEY)
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
            
            val record = data.optJSONObject("record") ?: return
            val status = record.optString("status")
            val id = record.optString("id")
            
            if (status == "paid") {
                if (!knownOrders.contains(id)) {
                    knownOrders.add(id)
                    pullOrder(id)
                }
            } else if (status == "done" || status == "ready" || status == "cancelled") {
                syncActiveOrderStatuses()
            }
        } catch (_: Exception) {}
    }
    
    private fun pullOrder(externalOrderId: String) {
        if (inFlightPulls.contains(externalOrderId)) return
        inFlightPulls.add(externalOrderId)
        
        scope.launch {
            try {
                val res = SupabaseClient.api.pullOnlineOrder(
                    url = "$ORDER_ONLINE_FUNCTIONS_BASE/pull-online-order",
                    auth = "Bearer $SS_ORDER_KEY",
                    payload = mapOf("external_order_id" to externalOrderId)
                )
                if (res.isSuccessful) {
                    // Success, the POS realtime will trigger an update automatically
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                inFlightPulls.remove(externalOrderId)
            }
        }
    }
    
    private fun syncActiveOrderStatuses() {
        scope.launch {
            try {
                SupabaseClient.api.syncActiveOrders(
                    url = "$ORDER_ONLINE_FUNCTIONS_BASE/sync-active-orders",
                    auth = "Bearer $SS_ORDER_KEY"
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun disconnect() {
        heartbeatJob?.cancel()
        webSocket?.close(1000, "bye")
        webSocket = null
    }
}
