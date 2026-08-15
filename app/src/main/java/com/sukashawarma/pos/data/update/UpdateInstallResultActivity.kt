package com.sukashawarma.pos.data.update

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Target PendingIntent dari PackageInstaller. Activity transparan dipakai agar
 * OS dapat menyalakan proses APK yang baru setelah self-update sukses, lalu
 * AppUpdateManager membuka kembali MainActivity secara otomatis.
 */
class UpdateInstallResultActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeStatus(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeStatus(intent)
    }

    private fun consumeStatus(statusIntent: Intent) {
        if (statusIntent.action == ACTION_INSTALL_STATUS) {
            AppUpdateManager.handleInstallStatus(applicationContext, statusIntent)
        }
        finish()
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "com.sukashawarma.pos.action.UPDATE_INSTALL_STATUS"
    }
}
