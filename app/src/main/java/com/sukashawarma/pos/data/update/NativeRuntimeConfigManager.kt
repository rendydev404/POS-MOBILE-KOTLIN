package com.sukashawarma.pos.data.update

import com.sukashawarma.pos.data.remote.SupabaseClient
import com.sukashawarma.pos.data.remote.dto.NativeRuntimeConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Config UI native yang dapat berubah langsung lewat Supabase Realtime. */
object NativeRuntimeConfigManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _config = MutableStateFlow(NativeRuntimeConfig())
    val config = _config.asStateFlow()

    suspend fun checkForConfig() {
        try {
            val response = SupabaseClient.api.getNativeRuntimeConfig()
            response.body()?.firstOrNull()?.value?.let { _config.value = it }
        } catch (error: Exception) {
            error.printStackTrace()
        }
    }

    fun checkForConfigAsync() {
        scope.launch { checkForConfig() }
    }

    fun handleRealtimePayload(record: JSONObject) {
        if (record.optString("key") != "native_runtime_config") return
        val value = record.optJSONObject("value") ?: return
        val colorsByVersion = value.optJSONObject("new_order_button_colors_by_version")?.let { colors ->
            buildMap {
                val keys = colors.keys()
                while (keys.hasNext()) {
                    val version = keys.next()
                    colors.optString(version).takeIf { it.isNotBlank() }?.let { put(version, it) }
                }
            }
        } ?: _config.value.newOrderButtonColorsByVersion
        _config.value = NativeRuntimeConfig(
            newOrderButtonColor = value.optString(
                "new_order_button_color",
                _config.value.newOrderButtonColor
            ),
            newOrderButtonLabel = value.optString(
                "new_order_button_label",
                _config.value.newOrderButtonLabel
            ),
            newOrderButtonColorsByVersion = colorsByVersion
        )
    }
}
