package com.sukashawarma.pos.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukashawarma.pos.data.remote.dto.AppUpdateManifest
import com.sukashawarma.pos.data.update.AppUpdateManager

/**
 * Status update berbentuk pill mengambang. Tidak modal, tidak meredupkan layar,
 * dan tidak mengambil fokus dari transaksi kasir.
 */
@Composable
fun AppUpdateIndicator(
    manifest: AppUpdateManifest,
    downloadState: AppUpdateManager.DownloadState,
    downloadPayload: AppUpdateManager.DownloadPayload,
    downloadPayloadSizeBytes: Long?,
    downloadProgress: Int,
    isSafeToApply: Boolean,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val actionable = downloadState == AppUpdateManager.DownloadState.AWAITING_USER_ACTION ||
        downloadState == AppUpdateManager.DownloadState.FAILED ||
        downloadState == AppUpdateManager.DownloadState.READY_TO_INSTALL

    val statusText = when (downloadState) {
        AppUpdateManager.DownloadState.IDLE -> "Update ditemukan"
        AppUpdateManager.DownloadState.DOWNLOADING -> {
            val payloadLabel = if (downloadPayload == AppUpdateManager.DownloadPayload.DELTA_PATCH) {
                "Patch"
            } else {
                "APK"
            }
            "Mengunduh $payloadLabel ${formatBytes(downloadPayloadSizeBytes)} \u2022 $downloadProgress%"
        }
        AppUpdateManager.DownloadState.READY_TO_INSTALL -> if (isSafeToApply) {
            "Siap diterapkan otomatis"
        } else {
            "Siap \u2022 menunggu layar Order"
        }
        AppUpdateManager.DownloadState.INSTALLING -> "Menerapkan update..."
        AppUpdateManager.DownloadState.AWAITING_USER_ACTION -> "Perlu izin \u2022 ketuk di sini"
        AppUpdateManager.DownloadState.FAILED -> "Update gagal \u2022 ketuk untuk ulang"
    }

    val accent = when (downloadState) {
        AppUpdateManager.DownloadState.READY_TO_INSTALL -> Color(0xFF059669)
        AppUpdateManager.DownloadState.FAILED -> Color(0xFFDC2626)
        AppUpdateManager.DownloadState.AWAITING_USER_ACTION -> Color(0xFFD97706)
        else -> Color(0xFF2563EB)
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f)),
        shadowElevation = 8.dp,
        modifier = modifier.then(
            if (actionable) Modifier.clickable(onClick = onAction) else Modifier
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Icon(
                Icons.Default.DragIndicator,
                contentDescription = "Geser indikator update",
                tint = Color(0xFF9CA3AF),
                modifier = Modifier.size(18.dp)
            )
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(34.dp)) {
                when (downloadState) {
                    AppUpdateManager.DownloadState.DOWNLOADING -> {
                        CircularProgressIndicator(
                            progress = { downloadProgress.coerceIn(0, 100) / 100f },
                            modifier = Modifier.size(30.dp),
                            strokeWidth = 3.dp,
                            color = accent,
                            trackColor = Color(0xFFE5E7EB)
                        )
                        Text("$downloadProgress", color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    AppUpdateManager.DownloadState.INSTALLING -> CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp,
                        color = accent
                    )
                    AppUpdateManager.DownloadState.FAILED -> Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = accent
                    )
                    AppUpdateManager.DownloadState.AWAITING_USER_ACTION -> Icon(
                        Icons.Default.InstallMobile,
                        contentDescription = null,
                        tint = accent
                    )
                    AppUpdateManager.DownloadState.READY_TO_INSTALL -> Icon(
                        Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = accent
                    )
                    AppUpdateManager.DownloadState.IDLE -> Icon(
                        Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = accent
                    )
                }
            }

            Column {
                Text(
                    text = "Update ${manifest.versionName}",
                    color = Color(0xFF111827),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(statusText, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

private fun formatBytes(bytes: Long?): String {
    if (bytes == null || bytes <= 0) return "ukuran belum diketahui"
    return when {
        bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}

/** Konfirmasi non-modal setelah APK baru berhasil diterapkan dan app relaunch. */
@Composable
fun AppUpdateSuccessIndicator(
    versionName: String,
    modifier: Modifier = Modifier
) {
    val green = Color(0xFF059669)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, green.copy(alpha = 0.24f)),
        shadowElevation = 8.dp,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Icon(
                Icons.Default.DragIndicator,
                contentDescription = "Geser indikator update",
                tint = Color(0xFF9CA3AF),
                modifier = Modifier.size(18.dp)
            )
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = green,
                modifier = Modifier.size(30.dp)
            )
            Column {
                Text(
                    text = "Update selesai",
                    color = Color(0xFF111827),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text("POS v$versionName sudah aktif", color = green, fontSize = 11.sp)
            }
        }
    }
}
