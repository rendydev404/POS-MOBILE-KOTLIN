package com.sukashawarma.pos.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sukashawarma.pos.data.remote.dto.AppUpdateManifest
import com.sukashawarma.pos.data.update.AppUpdateManager

/**
 * Ditampilkan MainActivity begitu AppUpdateManager.checkForUpdate() menemukan
 * versi baru. Distribusi app ini lewat WhatsApp, bukan Play Store, jadi alur
 * download+install dilakukan manual dari sini.
 */
@Composable
fun AppUpdateDialog(
    manifest: AppUpdateManifest,
    downloadState: AppUpdateManager.DownloadState,
    downloadProgress: Int,
    onDismiss: () -> Unit,
    onStartDownload: () -> Unit,
    onInstall: () -> Unit
) {
    val dismissible = manifest.mandatory.not() && downloadState != AppUpdateManager.DownloadState.DOWNLOADING

    Dialog(onDismissRequest = { if (dismissible) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFDBEAFE)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Update Tersedia", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF111827))
                        Text("Versi ${manifest.versionName}", fontSize = 12.sp, color = Color(0xFF6B7280))
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (!manifest.notes.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF9FAFB)
                    ) {
                        Text(
                            manifest.notes,
                            fontSize = 13.sp,
                            color = Color(0xFF4B5563),
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }

                when (downloadState) {
                    AppUpdateManager.DownloadState.DOWNLOADING -> {
                        Text("Mengunduh update... $downloadProgress%", fontSize = 13.sp, color = Color(0xFF6B7280))
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = Color(0xFF2563EB),
                            trackColor = Color(0xFFE5E7EB)
                        )
                    }
                    AppUpdateManager.DownloadState.READY_TO_INSTALL -> {
                        Text("Update siap dipasang.", fontSize = 13.sp, color = Color(0xFF059669), fontWeight = FontWeight.SemiBold)
                    }
                    AppUpdateManager.DownloadState.FAILED -> {
                        Text("Gagal mengunduh update. Coba lagi.", fontSize = 13.sp, color = Color(0xFFDC2626))
                    }
                    AppUpdateManager.DownloadState.IDLE -> {
                        Text("Update baru siap diunduh dan dipasang.", fontSize = 13.sp, color = Color(0xFF6B7280))
                    }
                }

                Spacer(Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (dismissible) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).height(52.dp)
                        ) {
                            Text("Nanti", fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = {
                            when (downloadState) {
                                AppUpdateManager.DownloadState.READY_TO_INSTALL -> onInstall()
                                AppUpdateManager.DownloadState.IDLE, AppUpdateManager.DownloadState.FAILED -> onStartDownload()
                                AppUpdateManager.DownloadState.DOWNLOADING -> {}
                            }
                        },
                        enabled = downloadState != AppUpdateManager.DownloadState.DOWNLOADING,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) {
                        Text(
                            when (downloadState) {
                                AppUpdateManager.DownloadState.READY_TO_INSTALL -> "Pasang Sekarang"
                                AppUpdateManager.DownloadState.DOWNLOADING -> "Mengunduh..."
                                AppUpdateManager.DownloadState.FAILED -> "Coba Lagi"
                                AppUpdateManager.DownloadState.IDLE -> "Update Sekarang"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
