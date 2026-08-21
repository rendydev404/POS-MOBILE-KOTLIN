package com.sukashawarma.pos.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.sukashawarma.pos.data.local.dao.ImageCacheDao
import com.sukashawarma.pos.data.local.entity.LocalImageCacheEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Cache foto PERMANEN (menu, dst) — sengaja BUKAN cacheDir bawaan Coil.
 *
 * Sebelum ini, foto menu murni mengandalkan disk cache default Coil di
 * `context.cacheDir`. Dua masalah:
 *  1. cacheDir cuma dijamin OS SEMENTARA — Android boleh menghapus isinya
 *     kapan saja saat storage device menipis, tanpa app diberi tahu. Itulah
 *     kenapa foto yang "kemarin sudah kebuka" bisa hilang lagi keesokan
 *     harinya walau kasir tidak pernah logout maupun clear data.
 *  2. Foto cuma didownload kalau item-nya PERNAH tampil di layar (lazy) —
 *     item yang belum pernah dibuka kasir sama sekali belum ada fotonya
 *     sama sekali walau online.
 *
 * Cache ini menulis ke `context.filesDir/menu_images/` (permanen sampai app
 * di-uninstall/data dibersihkan manual), dikompres ke WebP kecil supaya
 * ratusan foto tetap ringan, dan di-sync PROAKTIF: dipanggil dari
 * [com.sukashawarma.pos.data.repository.MenuRepository] setiap kali daftar
 * menu berhasil di-refresh (login, reconnect, maupun realtime saat admin
 * ubah/tambah menu) — bukan menunggu item-nya dibuka kasir dulu.
 *
 * "Pintar" terhadap perubahan: kunci cache adalah image_url itu sendiri.
 * Upload foto baru di sisi admin selalu menghasilkan path storage baru
 * (lihat pola nama file `<timestamp>-<hash>.jpeg` di bucket menu-images),
 * jadi menu_items.image_url otomatis berubah begitu fotonya diganti —
 * cache lama untuk URL lama otomatis jadi yatim (tidak direferensikan item
 * mana pun lagi) dan disapu di [syncAll]. Tidak perlu tabel versi terpisah.
 *
 * Object (bukan class) mengikuti pola [AuthPrefs]/[PrinterPrefs] di file ini:
 * satu instance untuk seluruh app, diinisialisasi sekali di POSApplication.
 */
object MenuImageCache {
    private const val DIR_NAME = "menu_images"
    private const val MAX_TOTAL_BYTES = 500L * 1024 * 1024 // 500MB, sesuai keputusan
    private const val MAX_DIMENSION_PX = 640 // cukup buat thumbnail menu di tablet
    private const val WEBP_QUALITY = 82

    private lateinit var appContext: Context
    private lateinit var dao: ImageCacheDao
    private lateinit var httpClient: OkHttpClient
    private lateinit var imagesDir: File

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    /** Snapshot in-memory dari tabel local_image_cache, supaya [resolve] bisa dipanggil
     *  langsung dari Compose (sebagai `model` AsyncImage) tanpa query Room di jalur UI. */
    private val index = ConcurrentHashMap<String, String>() // remoteUrl -> localPath

    fun init(context: Context, imageCacheDao: ImageCacheDao, client: OkHttpClient) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        dao = imageCacheDao
        httpClient = client
        imagesDir = File(appContext.filesDir, DIR_NAME).apply { mkdirs() }

        scope.launch {
            dao.getAll().forEach { row ->
                if (File(row.localPath).exists()) index[row.remoteUrl] = row.localPath
            }
        }
    }

    /** Dipakai sebagai `model` AsyncImage: file lokal kalau sudah ter-cache, kalau
     *  belum jatuh balik ke URL remote (Coil tetap bisa menampilkannya saat online,
     *  sambil [syncAll] mengunduhnya untuk kunjungan berikutnya).
     *
     *  SENGAJA tidak meng-update lastAccessedAt di sini walau ini "dipakai": fungsi
     *  ini terpanggil di setiap recomposition Compose (bisa puluhan kali/detik saat
     *  scroll), dan menulis Room di jalur sesering itu cuma buang-buang untuk manfaat
     *  yang nyaris tidak pernah kepakai — WebP 640px muat ribuan foto sebelum
     *  MAX_TOTAL_BYTES kepenuhan, jadi eviction LRU nyaris tidak pernah tereksekusi
     *  untuk satu outlet. lastAccessedAt cukup diisi sekali saat file didownload. */
    fun resolve(remoteUrl: String?): Any? {
        if (remoteUrl.isNullOrBlank()) return null
        val local = index[remoteUrl] ?: return remoteUrl
        val file = File(local)
        if (!file.exists()) {
            index.remove(remoteUrl)
            return remoteUrl
        }
        return file
    }

    /**
     * Dipanggil setelah setiap refresh menu berhasil (lihat MenuRepository).
     * Diam-diam di latar belakang: (1) download foto yang belum ter-cache,
     * (2) buang foto yang tidak direferensikan menu mana pun lagi (menu
     * dihapus, atau fotonya diganti), (3) jaga total ukuran di bawah
     * [MAX_TOTAL_BYTES] dengan membuang yang paling lama tidak dipakai.
     */
    fun syncAll(currentImageUrls: Collection<String?>) {
        if (!::appContext.isInitialized) return
        val urls = currentImageUrls.filterNotNull().filter { it.isNotBlank() }.toSet()
        scope.launch {
            // Satu sync jalan sekaligus — dipanggil dari login, reconnect, DAN tiap
            // event realtime menu_items; tanpa ini beberapa sync bisa tumpang tindih
            // saling mendownload/evict file yang sama.
            if (syncMutex.isLocked) return@launch
            syncMutex.withLock { runSync(urls) }
        }
    }

    private suspend fun runSync(urls: Set<String>) {
        // 1. Buang cache yang sudah tidak direferensikan menu mana pun.
        val stale = index.keys - urls
        stale.forEach { url -> evict(url) }

        // 2. Download yang belum ada. Sekuensial: jumlah menu per outlet biasanya
        //    puluhan-ratusan, dan ini jalan santai di latar belakang — paralel besar
        //    cuma menambah beban tanpa manfaat nyata di sini.
        urls.filter { !index.containsKey(it) }.forEach { url ->
            downloadAndStore(url)
        }

        // 3. Jaga kapasitas: evict LRU sampai di bawah batas.
        enforceCap()
    }

    private suspend fun downloadAndStore(url: String) {
        try {
            val bytes = withContext(Dispatchers.IO) {
                httpClient.newCall(Request.Builder().url(url).build()).execute().use { res ->
                    if (!res.isSuccessful) null else res.body?.bytes()
                }
            } ?: return

            val bitmap = decodeSampled(bytes, MAX_DIMENSION_PX) ?: return
            val fileName = sha256(url) + ".webp"
            val file = File(imagesDir, fileName)
            withContext(Dispatchers.IO) {
                FileOutputStream(file).use { out ->
                    bitmap.compress(webpFormat(), WEBP_QUALITY, out)
                }
            }
            bitmap.recycle()

            val entity = LocalImageCacheEntity(
                remoteUrl = url,
                localPath = file.absolutePath,
                sizeBytes = file.length(),
                lastAccessedAt = System.currentTimeMillis()
            )
            dao.upsert(entity)
            index[url] = file.absolutePath
        } catch (e: Exception) {
            android.util.Log.e("MenuImageCache", "Gagal download/simpan $url", e)
        }
    }

    private suspend fun evict(url: String) {
        val local = index.remove(url) ?: return
        runCatching { File(local).delete() }
        runCatching { dao.delete(url) }
    }

    private suspend fun enforceCap() {
        val rows = dao.allByLeastRecentlyUsed()
        var total = rows.sumOf { it.sizeBytes }
        if (total <= MAX_TOTAL_BYTES) return
        for (row in rows) {
            if (total <= MAX_TOTAL_BYTES) break
            evict(row.remoteUrl)
            total -= row.sizeBytes
        }
    }

    /** BitmapFactory.Options.inSampleSize: downscale SAAT decode, bukan sesudahnya —
     *  foto kamera 4000x3000 kalau di-decode penuh dulu baru di-resize bisa OOM di
     *  tablet low-end. Pola yang sama seperti kompresi bukti QRIS di POSManualOrderViewModel. */
    private fun decodeSampled(bytes: ByteArray, maxDimension: Int): Bitmap? {
        val boundsOnly = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOnly)
        if (boundsOnly.outWidth <= 0 || boundsOnly.outHeight <= 0) return null

        var sampleSize = 1
        while (boundsOnly.outWidth / (sampleSize * 2) >= maxDimension &&
            boundsOnly.outHeight / (sampleSize * 2) >= maxDimension
        ) {
            sampleSize *= 2
        }

        val decoded = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize }
        ) ?: return null

        if (decoded.width <= maxDimension && decoded.height <= maxDimension) return decoded

        val scale = maxDimension.toFloat() / maxOf(decoded.width, decoded.height)
        val scaled = Bitmap.createScaledBitmap(
            decoded, (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1), true
        )
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    @Suppress("DEPRECATION")
    private fun webpFormat(): Bitmap.CompressFormat =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
