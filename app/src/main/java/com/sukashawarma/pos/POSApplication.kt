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

    override fun onCreate() {
        super.onCreate()
        PrinterPrefs.init(this)
        AuthPrefs.init(this)
        SessionPrefs.init(this)
        NotificationChannels.createChannels(this)
    }
}
