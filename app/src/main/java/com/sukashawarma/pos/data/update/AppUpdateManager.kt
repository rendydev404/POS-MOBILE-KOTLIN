package com.sukashawarma.pos.data.update

import android.app.DownloadManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.sukashawarma.pos.BuildConfig
import com.sukashawarma.pos.data.remote.SupabaseClient
import com.sukashawarma.pos.data.remote.dto.AppUpdateDelta
import com.sukashawarma.pos.data.remote.dto.AppUpdateManifest
import com.sukashawarma.pos.data.remote.dto.deltaFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream

/**
 * Distribusi app ini lewat WhatsApp, bukan Play Store — jadi tidak ada update
 * otomatis bawaan. Ini pengganti minimalnya: manifest versi terbaru disimpan di
 * `global_settings` (key `app_update`, lihat AppUpdateManifest); begitu ada
 * versi baru, updater memilih patch delta bila basisnya cocok. Patch hanya berisi
 * byte yang berubah dan merekonstruksi APK signed dari APK terpasang; jika patch
 * tidak tersedia/valid, full APK tetap menjadi fallback aman.
 *
 * Deteksinya SENGAJA bukan polling. `global_settings` ada di publikasi
 * `supabase_realtime` (lihat migrasi 20300103000009), dan
 * [UpdateRealtimeManager] memiliki channel khusus sejak Application hidup.
 * Jadi begitu baris app_update di-update di server, [handleRealtimePayload]
 * menerima push langsung tanpa app perlu bertanya berkala. REST check hanya
 * menjadi fallback idempoten untuk event yang terbit saat device offline.
 *
 * Cara mem-publish update baru: build APK, upload ke storage manapun yang bisa
 * diakses lewat URL HTTPS langsung (mis. bucket Supabase Storage), lalu upsert
 * baris `global_settings` key='app_update' dengan version_code/apk_url yang baru.
 */
object AppUpdateManager {

    enum class DownloadState {
        IDLE,
        DOWNLOADING,
        READY_TO_INSTALL,
        INSTALLING,
        AWAITING_USER_ACTION,
        FAILED
    }

    enum class DownloadPayload {
        FULL_APK,
        DELTA_PATCH
    }

    private val _downloadState = MutableStateFlow(DownloadState.IDLE)
    val downloadState = _downloadState.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress = _downloadProgress.asStateFlow()

    private val _downloadPayload = MutableStateFlow(DownloadPayload.FULL_APK)
    val downloadPayload = _downloadPayload.asStateFlow()

    private val _downloadPayloadSizeBytes = MutableStateFlow<Long?>(null)
    val downloadPayloadSizeBytes = _downloadPayloadSizeBytes.asStateFlow()

    /** Sumber kebenaran tunggal: diisi dari [checkForUpdate] (sekali, saat login)
     *  ATAU dari [handleRealtimePayload] (push, tiap kali server berubah). */
    private val _availableUpdate = MutableStateFlow<AppUpdateManifest?>(null)
    val availableUpdate = _availableUpdate.asStateFlow()

    /** Versi yang baru selesai diterapkan, ditampilkan sebentar setelah relaunch. */
    private val _recentlyInstalledVersion = MutableStateFlow<String?>(null)
    val recentlyInstalledVersion = _recentlyInstalledVersion.asStateFlow()

    private var downloadId: Long = -1L
    private var pendingManifest: AppUpdateManifest? = null
    private var pendingUserAction: Intent? = null
    private var pendingDelta: AppUpdateDelta? = null
    private var forceFullForVersion: Int? = null
    private var receiverRegistered = false
    @Volatile private var processingDownloadedPayload = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var appContext: Context? = null

    /**
     * Bedakan first install dengan package update memakai timestamp dari Android.
     * Status sukses tidak bergantung pada callback proses lama: APK baru dapat
     * mengenalinya sendiri saat pertama kali dibuka setelah package replacement.
     */
    fun initialize(context: Context) {
        val appContext = context.applicationContext
        this.appContext = appContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_ACKNOWLEDGED_VERSION, 0) == BuildConfig.VERSION_CODE) return

        val packageInfo = runCatching {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        }.getOrNull()
        val isPackageUpdate = packageInfo != null &&
            packageInfo.lastUpdateTime - packageInfo.firstInstallTime > 1_000L

        if (isPackageUpdate) {
            _recentlyInstalledVersion.value = BuildConfig.VERSION_NAME
        } else {
            prefs.edit().putInt(KEY_ACKNOWLEDGED_VERSION, BuildConfig.VERSION_CODE).apply()
        }
    }

    fun acknowledgeRecentInstall(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_ACKNOWLEDGED_VERSION, BuildConfig.VERSION_CODE).apply()
        _recentlyInstalledVersion.value = null
    }

    /** Satu-satunya tempat REST dipanggil — sekali per login, bukan loop berkala. */
    suspend fun checkForUpdate() {
        try {
            val res = SupabaseClient.api.getAppUpdateInfo()
            val manifest = res.body()?.firstOrNull()?.value ?: return
            applyIfNewer(manifest)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Aman dipanggil Application, service, atau Activity tanpa menahan main thread. */
    fun checkForUpdateAsync() {
        scope.launch { checkForUpdate() }
    }

    /** Dipanggil POSRealtimeService saat menerima event postgres_changes untuk
     *  tabel global_settings — payload sudah berisi baris penuh, jadi tidak
     *  perlu REST call tambahan sama sekali. */
    fun handleRealtimePayload(record: JSONObject) {
        if (record.optString("key") != "app_update") return
        val v = record.optJSONObject("value") ?: return
        val manifest = AppUpdateManifest(
            versionCode = v.optInt("version_code"),
            versionName = v.optString("version_name"),
            apkUrl = v.optString("apk_url"),
            apkSha256 = v.optString("apk_sha256").ifBlank { null },
            apkSizeBytes = v.optLong("apk_size_bytes").takeIf { it > 0 },
            delta = v.optJSONObject("delta")?.toAppUpdateDelta(),
            deltas = v.optJSONArray("deltas")?.let { array ->
                buildList {
                    for (index in 0 until array.length()) {
                        array.optJSONObject(index)?.toAppUpdateDelta()?.let(::add)
                    }
                }
            }.orEmpty(),
            notes = if (v.isNull("notes")) null else v.optString("notes").ifBlank { null },
            mandatory = v.optBoolean("mandatory", false)
        )
        applyIfNewer(manifest)
    }

    private fun applyIfNewer(manifest: AppUpdateManifest) {
        if (manifest.versionCode <= BuildConfig.VERSION_CODE) return

        val context = appContext
        val isDifferentRelease = pendingManifest?.versionCode != manifest.versionCode
        if (isDifferentRelease) {
            if (downloadId != -1L && context != null) {
                val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                runCatching { manager.remove(downloadId) }
            }
            downloadId = -1L
            pendingUserAction = null
            pendingDelta = null
            forceFullForVersion = null
            processingDownloadedPayload = false
            pendingManifest = manifest
            _downloadProgress.value = 0
            _downloadPayloadSizeBytes.value = null
            _downloadState.value = DownloadState.IDLE
        }

        _availableUpdate.value = manifest

        // Download dimiliki manager, bukan UI. Dengan begitu push yang diterima
        // foreground service tetap mulai mengunduh walau Activity sedang tidak ada.
        if (context != null && _downloadState.value == DownloadState.IDLE) {
            startDownload(context, manifest)
        }
    }

    fun startDownload(context: Context, manifest: AppUpdateManifest) {
        if (_downloadState.value == DownloadState.DOWNLOADING || processingDownloadedPayload) return
        pendingManifest = manifest

        val updatesDir = File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }
        val destFile = File(updatesDir, "suka-shawarma-${manifest.versionCode}.apk")

        // App sempat restart setelah download-nya kelar sebelumnya — file APK ini
        // sudah lengkap di disk, tidak perlu unduh ulang. Ditandai lewat prefs
        // (bukan sekadar file ada) supaya download yang putus di tengah jalan
        // tidak dikira sudah selesai lalu gagal saat dipasang.
        if (destFile.exists() && isMarkedComplete(context, manifest.versionCode)) {
            _downloadProgress.value = 100
            _downloadState.value = DownloadState.READY_TO_INSTALL
            return
        }

        val delta = manifest.deltaFor(BuildConfig.VERSION_CODE)?.takeIf {
            it.patchUrl.startsWith("https://") &&
                it.patchSha256.length == 64 &&
                it.patchSizeBytes > 0 &&
                (manifest.apkSizeBytes == null || it.patchSizeBytes < manifest.apkSizeBytes) &&
                forceFullForVersion != manifest.versionCode &&
                !manifest.apkSha256.isNullOrBlank()
        }
        pendingDelta = delta
        val payloadFile = if (delta != null) {
            File(updatesDir, "suka-shawarma-${delta.baseVersionCode}-to-${manifest.versionCode}.fbf")
        } else {
            destFile
        }
        val payloadUrl = delta?.patchUrl ?: manifest.apkUrl
        _downloadPayload.value = if (delta != null) DownloadPayload.DELTA_PATCH else DownloadPayload.FULL_APK
        _downloadPayloadSizeBytes.value = delta?.patchSizeBytes ?: manifest.apkSizeBytes

        if (destFile.exists()) destFile.delete()
        if (payloadFile.exists()) payloadFile.delete()

        _downloadState.value = DownloadState.DOWNLOADING
        _downloadProgress.value = 0
        registerReceiver(context)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(payloadUrl))
            .setTitle("Update Suka Shawarma POS")
            .setDescription(
                if (delta != null) "Mengunduh patch hemat ${manifest.versionName}"
                else "Mengunduh versi ${manifest.versionName}"
            )
            .addRequestHeader(
                "User-Agent",
                "SukaShawarmaPOS-Updater/${BuildConfig.VERSION_NAME}"
            )
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(payloadFile))
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
                        } else {
                            // Jangan hanya mengandalkan ACTION_DOWNLOAD_COMPLETE.
                            // Broadcast dapat terlambat/terlewat walau bytes sudah 100%,
                            // yang sebelumnya membuat dialog macet di "Mengunduh...".
                            markDownloadReady(context)
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

            // ACTION_DOWNLOAD_COMPLETE juga dikirim untuk download yang gagal.
            // Query status final agar file parsial tidak pernah ditandai siap pasang.
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val cursor = downloadManager.query(DownloadManager.Query().setFilterById(id))
            val status = cursor?.use {
                if (!it.moveToFirst()) return@use null
                val statusIdx = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
                if (statusIdx >= 0) it.getInt(statusIdx) else null
            }

            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                markDownloadReady(context)
            } else if (status == DownloadManager.STATUS_FAILED) {
                _downloadState.value = DownloadState.FAILED
            }
        }
    }

    private fun markDownloadReady(context: Context) {
        if (processingDownloadedPayload ||
            _downloadState.value == DownloadState.READY_TO_INSTALL ||
            _downloadState.value == DownloadState.INSTALLING ||
            _downloadState.value == DownloadState.AWAITING_USER_ACTION
        ) return
        val manifest = pendingManifest ?: return
        processingDownloadedPayload = true
        scope.launch {
            var shouldFallbackToFull = false
            try {
                val updatesDir = File(context.getExternalFilesDir(null), "updates")
                val targetApk = File(updatesDir, "suka-shawarma-${manifest.versionCode}.apk")
                if (_downloadPayload.value == DownloadPayload.DELTA_PATCH) {
                    val delta = requireNotNull(pendingDelta)
                    val patchFile = File(
                        updatesDir,
                        "suka-shawarma-${delta.baseVersionCode}-to-${manifest.versionCode}.fbf"
                    )
                    require(patchFile.length() == delta.patchSizeBytes) {
                        "Downloaded delta size mismatch"
                    }
                    val actualPatchHash = ApkDeltaApplier.sha256(patchFile)
                    require(actualPatchHash.equals(delta.patchSha256, ignoreCase = true)) {
                        "Downloaded delta SHA-256 mismatch"
                    }
                    ApkDeltaApplier.apply(
                        installedApk = File(context.applicationInfo.sourceDir),
                        patchFile = patchFile,
                        outputApk = targetApk,
                        expectedBaseVersion = BuildConfig.VERSION_CODE,
                        expectedTargetVersion = manifest.versionCode,
                        expectedTargetSha256 = requireNotNull(manifest.apkSha256)
                    )
                    patchFile.delete()
                } else if (!manifest.apkSha256.isNullOrBlank()) {
                    val actualApkHash = ApkDeltaApplier.sha256(targetApk)
                    require(actualApkHash.equals(manifest.apkSha256, ignoreCase = true)) {
                        "Downloaded APK SHA-256 mismatch"
                    }
                }

                markComplete(context, manifest.versionCode)
                downloadId = -1L
                _downloadProgress.value = 100
                _downloadState.value = DownloadState.READY_TO_INSTALL
            } catch (error: Exception) {
                android.util.Log.e("AppUpdateManager", "Downloaded update validation failed", error)
                shouldFallbackToFull = _downloadPayload.value == DownloadPayload.DELTA_PATCH
                if (shouldFallbackToFull) {
                    val delta = pendingDelta
                    if (delta != null) {
                        File(
                            File(context.getExternalFilesDir(null), "updates"),
                            "suka-shawarma-${delta.baseVersionCode}-to-${manifest.versionCode}.fbf"
                        ).delete()
                    }
                }
                if (!shouldFallbackToFull) _downloadState.value = DownloadState.FAILED
            } finally {
                processingDownloadedPayload = false
            }

            if (shouldFallbackToFull) {
                forceFullForVersion = manifest.versionCode
                pendingDelta = null
                _downloadState.value = DownloadState.IDLE
                _downloadProgress.value = 0
                startDownload(context.applicationContext, manifest)
            }
        }
    }

    private const val PREFS_NAME = "app_update_prefs"
    private const val KEY_ACKNOWLEDGED_VERSION = "acknowledged_app_version"

    private fun markComplete(context: Context, versionCode: Int) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean("downloaded_$versionCode", true).apply()
    }

    private fun isMarkedComplete(context: Context, versionCode: Int): Boolean {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("downloaded_$versionCode", false)
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

    /**
     * Terapkan APK yang sudah selesai diunduh.
     *
     * Android 12+ mendukung self-update tanpa dialog melalui PackageInstaller
     * selama app mendeklarasikan UPDATE_PACKAGES_WITHOUT_USER_ACTION, target API
     * memenuhi syarat, dan izin sumber instalasi sudah diberikan. OS tetap boleh
     * mengembalikan STATUS_PENDING_USER_ACTION; kasus itu ditampilkan sebagai
     * indikator kecil, bukan dialog modal aplikasi.
     */
    fun installDownloadedApk(context: Context) {
        val manifest = pendingManifest ?: return
        val updatesDir = File(context.getExternalFilesDir(null), "updates")
        val apkFile = File(updatesDir, "suka-shawarma-${manifest.versionCode}.apk")
        if (!apkFile.exists()) {
            _downloadState.value = DownloadState.FAILED
            return
        }

        if (!canRequestInstall(context)) {
            pendingUserAction = null
            _downloadState.value = DownloadState.AWAITING_USER_ACTION
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            installSilently(context.applicationContext, apkFile, manifest.versionCode)
        } else {
            // API 26-30 belum memiliki USER_ACTION_NOT_REQUIRED untuk installer
            // biasa. Tetap gunakan installer sistem sebagai fallback satu-tap.
            _downloadState.value = DownloadState.AWAITING_USER_ACTION
        }
    }

    private fun installSilently(context: Context, apkFile: File, versionCode: Int) {
        if (_downloadState.value == DownloadState.INSTALLING) return
        _downloadState.value = DownloadState.INSTALLING
        pendingUserAction = null

        scope.launch {
            val packageInstaller = context.packageManager.packageInstaller
            var sessionId: Int? = null
            try {
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(context.packageName)
                    setSize(apkFile.length())
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                    }
                }

                sessionId = packageInstaller.createSession(params)
                packageInstaller.openSession(sessionId).use { session ->
                    FileInputStream(apkFile).use { input ->
                        session.openWrite("base.apk", 0, apkFile.length()).use { output ->
                            input.copyTo(output)
                            session.fsync(output)
                        }
                    }

                    val callbackIntent = Intent(context, UpdateInstallResultActivity::class.java).apply {
                        action = UpdateInstallResultActivity.ACTION_INSTALL_STATUS
                    }
                    val callback = PendingIntent.getActivity(
                        context,
                        versionCode,
                        callbackIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    )
                    session.commit(callback.intentSender)
                }

                // Jangan biarkan indikator INSTALLING macet tanpa batas bila
                // vendor ROM tidak mengirim callback PackageInstaller.
                scope.launch {
                    delay(120_000)
                    if (_downloadState.value == DownloadState.INSTALLING) {
                        _downloadState.value = DownloadState.FAILED
                    }
                }
            } catch (error: Exception) {
                sessionId?.let { id -> runCatching { packageInstaller.abandonSession(id) } }
                error.printStackTrace()
                _downloadState.value = DownloadState.FAILED
            }
        }
    }

    /** Dipanggil indikator kecil bila platform membutuhkan tindakan user. */
    fun continueInstallWithUserAction(context: Context) {
        val confirmation = pendingUserAction
        if (confirmation != null) {
            confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(confirmation)
            return
        }

        if (!canRequestInstall(context)) {
            context.startActivity(installIntentSettings(context))
            return
        }

        val manifest = pendingManifest ?: return
        val apkFile = File(File(context.getExternalFilesDir(null), "updates"), "suka-shawarma-${manifest.versionCode}.apk")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            installSilently(context.applicationContext, apkFile, manifest.versionCode)
        } else {
            launchLegacyInstaller(context, apkFile)
        }
    }

    /** Lanjut setelah kembali dari halaman izin, tetapi jangan membuka ulang
     *  dialog konfirmasi sistem bila user baru saja membatalkannya. */
    fun resumeAfterInstallPermission(context: Context) {
        if (_downloadState.value == DownloadState.AWAITING_USER_ACTION &&
            pendingUserAction == null &&
            canRequestInstall(context)
        ) {
            installDownloadedApk(context)
        }
    }

    internal fun handleInstallStatus(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_SUCCESS -> relaunchUpdatedApp(context)
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                pendingUserAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                _downloadState.value = DownloadState.AWAITING_USER_ACTION
            }
            else -> {
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)?.let {
                    android.util.Log.e("AppUpdateManager", "Install failed: $it")
                }
                _downloadState.value = DownloadState.FAILED
            }
        }
    }

    private fun launchLegacyInstaller(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun relaunchUpdatedApp(context: Context) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(launchIntent)
    }

    fun reset() {
        _downloadState.value = DownloadState.IDLE
        _downloadProgress.value = 0
        pendingUserAction = null
        pendingDelta = null
        _downloadPayloadSizeBytes.value = null
    }

    private fun JSONObject.toAppUpdateDelta(): AppUpdateDelta? {
        val baseVersionCode = optInt("base_version_code")
        val patchUrl = optString("patch_url")
        val patchSha256 = optString("patch_sha256")
        val patchSizeBytes = optLong("patch_size_bytes")
        if (baseVersionCode <= 0 || patchUrl.isBlank() || patchSha256.isBlank() || patchSizeBytes <= 0) {
            return null
        }
        return AppUpdateDelta(baseVersionCode, patchUrl, patchSha256, patchSizeBytes)
    }
}
