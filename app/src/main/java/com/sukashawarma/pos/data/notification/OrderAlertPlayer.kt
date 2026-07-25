package com.sukashawarma.pos.data.notification

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Plays the "pesanan masuk" alert (tone + haptic) per spec section 2.B.
 * Uses ToneGenerator instead of a bundled audio asset so it works with zero setup.
 */
class OrderAlertPlayer(context: Context) {
    private val appContext = context.applicationContext

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    fun playNewOrderAlert() {
        try {
            ToneGenerator(AudioManager.STREAM_ALARM, 90).startTone(ToneGenerator.TONE_PROP_BEEP2, 700)
        } catch (_: Exception) {
            // Non-critical — printer/kitchen ticket still shows the order either way.
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 200), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 200, 100, 200), -1)
            }
        } catch (_: Exception) {
        }
    }
}
