package com.sukashawarma.pos.data.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class UpdateInstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_INSTALL_STATUS) {
            AppUpdateManager.handleInstallStatus(context.applicationContext, intent)
        }
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "com.sukashawarma.pos.action.UPDATE_INSTALL_STATUS"
    }
}

