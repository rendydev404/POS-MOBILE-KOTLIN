package com.sukashawarma.pos.data.update

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateRealtimeProtocolTest {
    @Test
    fun `join subscribes only to global settings with anon access token`() {
        @Suppress("DEPRECATION")
        val frame = JsonParser().parse(
            UpdateRealtimeProtocol.joinMessage("realtime:test", "12", "anon-token")
        ).asJsonObject

        assertEquals("phx_join", frame.get("event").asString)
        assertEquals("12", frame.get("ref").asString)
        val payload = frame.getAsJsonObject("payload")
        assertEquals("anon-token", payload.get("access_token").asString)
        val changes = payload.getAsJsonObject("config").getAsJsonArray("postgres_changes")
        assertEquals(1, changes.size())
        assertEquals("global_settings", changes[0].asJsonObject.get("table").asString)
    }

    @Test
    fun `join is accepted only when server confirms global settings subscription`() {
        val accepted = UpdateRealtimeProtocol.parse(
            """{"ref":"7","event":"phx_reply","payload":{"status":"ok","response":{"postgres_changes":[{"id":1,"schema":"public","table":"global_settings"}]}}}"""
        ) as UpdateRealtimeEvent.JoinReply
        assertTrue(accepted.successful)
        assertTrue(accepted.hasGlobalSettingsSubscription)

        val missing = UpdateRealtimeProtocol.parse(
            """{"ref":"8","event":"phx_reply","payload":{"status":"ok","response":{"postgres_changes":[]}}}"""
        ) as UpdateRealtimeEvent.JoinReply
        assertTrue(missing.successful)
        assertFalse(missing.hasGlobalSettingsSubscription)
    }

    @Test
    fun `postgres change returns the complete global setting record`() {
        val event = UpdateRealtimeProtocol.parse(
            """{"event":"postgres_changes","payload":{"data":{"table":"global_settings","type":"UPDATE","record":{"key":"app_update","value":{"version_code":7}}}}}"""
        ) as UpdateRealtimeEvent.GlobalSettingChanged

        assertEquals("app_update", event.record.get("key").asString)
        assertEquals(7, event.record.getAsJsonObject("value").get("version_code").asInt)
    }

    @Test
    fun `channel error requests reconnect`() {
        assertTrue(
            UpdateRealtimeProtocol.parse("""{"event":"phx_error","payload":{}}""") ===
                UpdateRealtimeEvent.ChannelFailure
        )
    }
}
