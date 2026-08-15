package com.sukashawarma.pos.presentation.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sukashawarma.pos.data.remote.SupabaseClient
import com.sukashawarma.pos.data.remote.dto.UpsertKioskSettingPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.RequestBody.Companion.toRequestBody

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    val currentOutletName = MutableStateFlow("")
    val coverUrl = MutableStateFlow<String?>(null)
    val isUploadingCover = MutableStateFlow(false)
    val coverMessage = MutableStateFlow<String?>(null)

    fun setOutlet(outletId: String) {
        if (currentOutletName.value == outletId) return
        currentOutletName.value = outletId
        loadCover()
    }

    private fun loadCover() = viewModelScope.launch {
        val outletId = currentOutletName.value
        if (outletId.isBlank()) return@launch
        try {
            coverUrl.value = SupabaseClient.api.getOutletKioskSetting(
                outletIdFilter = "eq.$outletId",
                keyFilter = "eq.cover_image_url"
            ).body()?.firstOrNull()?.value
        } catch (_: Exception) {
            coverMessage.value = "Gagal memuat cover kiosk."
        }
    }

    fun uploadCover(uri: Uri) = viewModelScope.launch {
        val outletId = currentOutletName.value
        if (outletId.isBlank()) return@launch
        isUploadingCover.value = true
        coverMessage.value = null
        try {
            val resolver = getApplication<Application>().contentResolver
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("File tidak dapat dibaca")
            require(bytes.size <= 5 * 1024 * 1024) { "Ukuran gambar maksimal 5 MB." }
            val contentType = resolver.getType(uri)
                ?.takeIf { it in setOf("image/jpeg", "image/png", "image/webp") }
                ?: error("Gunakan gambar JPG, PNG, atau WebP.")
            val extension = when (contentType) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }
            val path = "kiosk-assets/cover_${outletId}_${System.currentTimeMillis()}.$extension"
            check(
                SupabaseClient.api.uploadPaymentProof(
                    path,
                    contentType,
                    file = bytes.toRequestBody()
                ).isSuccessful
            ) { "Upload cover gagal." }
            val url = "${SupabaseClient.BASE_URL}storage/v1/object/public/$path"
            check(
                SupabaseClient.api.upsertKioskSetting(
                    payload = UpsertKioskSettingPayload(outletId, "cover_image_url", url)
                ).isSuccessful
            ) { "Gagal menyimpan cover." }
            coverUrl.value = url
            coverMessage.value = "Cover kiosk berhasil diperbarui."
        } catch (error: Exception) {
            coverMessage.value = error.message ?: "Gagal mengunggah cover kiosk."
        } finally {
            isUploadingCover.value = false
        }
    }
}
