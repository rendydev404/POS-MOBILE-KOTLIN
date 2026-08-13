package com.sukashawarma.pos.data.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.sukashawarma.pos.BuildConfig
import com.sukashawarma.pos.data.remote.SupabaseClient
import com.sukashawarma.pos.data.remote.dto.AppUpdateManifest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Distribusi app ini lewat WhatsApp, bukan Play Store — jadi tidak ada update
 * otomatis bawaan. Ini pengganti minimalnya: cek manifest versi di
 * `global_settings` (key `app_update`, lihat AppUpdateManifest), lalu kalau ada
 * yang lebih baru, download APK-nya dan minta sistem memasangnya.
 *
 * Cara mem-publish update baru: build APK, upload ke storage manapun yang bisa
 * diakses lewat URL HTTPS langsung (mis. bucket Supabase Storage), lalu upsert
 * baris `global_settings` key='app_update' dengan version_code/apk_url yang baru.
 */
object AppUpdateManager {

    enum class DownloadState { IDLE, DOWNLOADING, READY_TO_INSTALL, FAILED }

    private val _downloadState = MutableStateFlow(DownloadState.IDLE)
    val downloadState = _downloadState.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress = _downloadProgress.asStateFlow()

    private var downloadId: Long = -1L
    private var pendingManifest: AppUpdateManifest? = null
    private var receiverRegistered = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun checkForUpdate(): AppUpdateManifest? {
        return try {
            val res = SupabaseClient.api.getAppUpdateInfo()
            val manifest = res.body()?.firstOrNull()?.value ?: return null
            if (manifest.versionCode > BuildConfig.VERSION_CODE) manifest else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun startDownload(context: Context, manifest: AppUpdateManifest) {
        if (_downloadState.value == DownloadState.DOWNLOADING) return
        pendingManifest = manifest
        _downloadState.value = DownloadState.DOWNLOADING
        _downloadProgress.value = 0

        registerReceiver(context)

        val updatesDir = File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }
        val destFile = File(updatesDir, "suka-shawarma-${manifest.versionCode}.apk")
        if (destFile.exists()) destFile.delete()

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(manifest.apkUrl))
            .setTitle("Update Suka Shawarma POS")
            .setDescription("Mengunduh versi ${manifest.versionName}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(destFile))
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        downloadId = downloadManager.enqueue(request)
        pollProgress(context, downloadManager)
    }

    /** Poll ringan tiap detik — DownloadManager tidak punya callback progress langsung. */
    private fun pollProgress(context: Context, downloadManager: DownloadManager) {
        scope.launch {
            var downloading = true
            while (downloading) {
                val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
                if (cursor != null && cursor.moveToFirst()) {
                    val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val bytesIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else -1
                    val bytes = if (bytesIdx >= 0) cursor.getLong(bytesIdx) else 0L
                    val total = if (totalIdx >= 0) cursor.getLong(totalIdx) else -1L

                    if (total > 0) {
                        _downloadProgress.value = ((bytes * 100) / total).toInt()
                    }

                    if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                        downloading = false
                        if (status == DownloadManager.STATUS_FAILED) {
                            _downloadState.value = DownloadState.FAILED
                        }
                    }
                }
                cursor?.close()
                if (downloading) delay(1000)
            }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id != downloadId) return
            _downloadProgress.value = 100
            _downloadState.value = DownloadState.READY_TO_INSTALL
        }
    }

    private fun registerReceiver(context: Context) {
        if (receiverRegistered) return
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.applicationContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.applicationContext.registerReceiver(receiver, filter)
        }
        receiverRegistered = true
    }

    /** True kalau app sudah diizinkan memasang APK dari sumber sendiri (Android 8+). */
    fun canRequestInstall(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true
    }

    fun installIntentSettings(context: Context): Intent {
        return Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    fun installDownloadedApk(context: Context) {
        val manifest = pendingManifest ?: return
        val updatesDir = File(context.getExternalFilesDir(null), "updates")
        val apkFile = File(updatesDir, "suka-shawarma-${manifest.versionCode}.apk")
        if (!apkFile.exists()) {
            _downloadState.value = DownloadState.FAILED
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun reset() {
        _downloadState.value = DownloadState.IDLE
        _downloadProgress.value = 0
    }
}
