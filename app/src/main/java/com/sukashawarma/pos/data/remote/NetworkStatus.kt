package com.sukashawarma.pos.data.remote

/**
 * Aturan murni "apakah jaringan ini benar-benar bisa dipakai", dipisah dari
 * ConnectivityManager supaya bisa diuji tanpa perangkat Android.
 */
object NetworkStatus {

    /**
     * Online hanya jika OS sudah MEMBUKTIKAN ada jalan keluar ke internet.
     * `hasInternet` saja tidak cukup: WiFi outlet yang routernya hidup tapi
     * ISP-nya mati tetap melaporkan NET_CAPABILITY_INTERNET = true.
     */
    fun isValidatedInternet(hasInternet: Boolean, isValidated: Boolean): Boolean =
        hasInternet && isValidated
}
