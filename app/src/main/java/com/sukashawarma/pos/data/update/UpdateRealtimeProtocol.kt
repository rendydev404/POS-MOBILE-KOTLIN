package com.sukashawarma.pos.data.update

import com.google.gson.JsonObject
import com.google.gson.JsonParser

internal sealed interface UpdateRealtimeEvent {
    data class JoinReply(
        val ref: String,
        val successful: Boolean,
        val hasGlobalSettingsSubscription: Boolean
    ) : UpdateRealtimeEvent

    data class GlobalSettingChanged(val record: JsonObject) : UpdateRealtimeEvent
    data object ChannelFailure : UpdateRealtimeEvent
    data object Ignore : UpdateRealtimeEvent
}

/** Pure protocol helpers so the Realtime handshake can be regression-tested on the JVM. */
internal object UpdateRealtimeProtocol {
    fun joinMessage(topic: String, ref: String, anonKey: String): String = JsonObject().apply {
        addProperty("topic", topic)
        addProperty("event", "phx_join")
        add("payload", JsonObject().apply {
            add("config", JsonObject().apply {
                add("postgres_changes", com.google.gson.JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("event", "*")
                        addProperty("schema", "public")
                        addProperty("table", "global_settings")
                    })
                })
            })
            addProperty("access_token", anonKey)
        })
        addProperty("ref", ref)
    }.toString()

    fun heartbeatMessage(ref: String): String = JsonObject().apply {
        addProperty("topic", "phoenix")
        addProperty("event", "heartbeat")
        add("payload", JsonObject())
        addProperty("ref", ref)
    }.toString()

    fun parse(text: String): UpdateRealtimeEvent {
        return try {
            @Suppress("DEPRECATION")
            val frame = JsonParser().parse(text).asJsonObject
            val event = frame.string("event")
            if (event == "phx_error" || event == "phx_close") {
                return UpdateRealtimeEvent.ChannelFailure
            }

            val payload = frame.obj("payload")
            if (event == "system" && payload?.string("status") == "error") {
                return UpdateRealtimeEvent.ChannelFailure
            }

            if (event == "phx_reply") {
                val response = payload?.obj("response")
                val subscriptions = response?.array("postgres_changes")
                var hasExpectedSubscription = false
                if (subscriptions != null) {
                    for (element in subscriptions) {
                        if (element.isJsonObject &&
                            element.asJsonObject.string("schema") == "public" &&
                            element.asJsonObject.string("table") == "global_settings"
                        ) {
                            hasExpectedSubscription = true
                            break
                        }
                    }
                }
                return UpdateRealtimeEvent.JoinReply(
                    ref = frame.string("ref").orEmpty(),
                    successful = payload?.string("status") == "ok",
                    hasGlobalSettingsSubscription = hasExpectedSubscription
                )
            }

            if (event != "postgres_changes") return UpdateRealtimeEvent.Ignore
            val data = payload?.obj("data") ?: payload ?: return UpdateRealtimeEvent.Ignore
            if (data.string("table") != "global_settings") return UpdateRealtimeEvent.Ignore
            val record = data.obj("record") ?: data.obj("old_record") ?: return UpdateRealtimeEvent.Ignore
            UpdateRealtimeEvent.GlobalSettingChanged(record)
        } catch (_: Exception) {
            UpdateRealtimeEvent.Ignore
        }
    }

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeUnless { it.isJsonNull }?.asString

    private fun JsonObject.obj(name: String): JsonObject? =
        get(name)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.array(name: String): com.google.gson.JsonArray? =
        get(name)?.takeIf { it.isJsonArray }?.asJsonArray
}
