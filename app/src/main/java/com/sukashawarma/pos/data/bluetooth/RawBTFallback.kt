package com.sukashawarma.pos.data.bluetooth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64

object RawBTFallback {
    fun printViaRawBT(context: Context, bytes: ByteArray) {
        try {
            val base64Data = Base64.encodeToString(bytes, Base64.DEFAULT)
            val intentUri = Uri.parse("rawbt:base64,$base64Data")
            val intent = Intent(Intent.ACTION_VIEW, intentUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
