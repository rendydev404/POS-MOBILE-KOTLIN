package com.sukashawarma.pos

import android.app.Application
import com.sukashawarma.pos.data.local.AppDatabase
import com.sukashawarma.pos.data.local.AuthPrefs
import com.sukashawarma.pos.data.local.PrinterPrefs
import com.sukashawarma.pos.data.local.SessionPrefs
import com.sukashawarma.pos.data.notification.NotificationChannels

class POSApplication : Application() {
    val database: AppDatabase by lazy {
        AppDatabase.getInstance(this)
    }

    val menuRepository: com.sukashawarma.pos.data.repository.MenuRepository by lazy {
        com.sukashawarma.pos.data.repository.MenuRepository(
            api = com.sukashawarma.pos.data.remote.SupabaseClient.api,
            menuItemDao = database.menuItemDao(),
            kioskSettingDao = database.kioskSettingDao(),
            okHttpClient = com.sukashawarma.pos.data.remote.SupabaseClient.okHttpClient
        )
    }

    override fun onCreate() {
        super.onCreate()
        PrinterPrefs.init(this)
        AuthPrefs.init(this)
        SessionPrefs.init(this)
        NotificationChannels.createChannels(this)
    }
}
