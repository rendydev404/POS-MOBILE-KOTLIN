package com.sukashawarma.pos

import android.app.Application
import com.sukashawarma.pos.data.local.AppDatabase
import com.sukashawarma.pos.data.local.AuthPrefs
import com.sukashawarma.pos.data.local.MenuImageCache
import com.sukashawarma.pos.data.local.PrinterPrefs
import com.sukashawarma.pos.data.local.SessionPrefs
import com.sukashawarma.pos.data.notification.NotificationChannels
import com.sukashawarma.pos.data.remote.NetworkMonitor

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
        MenuImageCache.init(this, database.imageCacheDao(), com.sukashawarma.pos.data.remote.SupabaseClient.okHttpClient)
        NetworkMonitor.init(this)
        NotificationChannels.createChannels(this)
        com.sukashawarma.pos.data.update.AppUpdateManager.initialize(this)
        com.sukashawarma.pos.data.update.UpdateRealtimeManager.start()
        // Menangkap update bahkan sebelum kasir login. Jalur realtime tetap yang
        // utama; cek satu kali saat proses lahir menjadi fallback ketika event
        // terbit saat device mati atau socket belum tersambung.
        com.sukashawarma.pos.data.update.AppUpdateManager.checkForUpdateAsync()
    }
}
