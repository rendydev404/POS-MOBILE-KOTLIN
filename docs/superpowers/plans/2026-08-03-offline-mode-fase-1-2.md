# Offline Mode POS Native — Fase 1 & 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Membuat POS kasir tetap melayani pelanggan penuh saat internet outlet mati seharian, dengan setiap mutasi kasir tersimpan di outbox lokal dan tersinkron rapi tanpa duplikat saat internet kembali.

**Architecture:** Layar tidak pernah memanggil jaringan. ViewModel menulis ke Repository; Repository menulis tabel data Room **dan** satu baris outbox `sync_queue` dalam satu transaksi Room yang sama. `SyncEngine` generik menguras outbox (fase PUSH) lalu menarik data master (fase PULL), dijalankan `SyncWorker` (WorkManager) dan dipicu `NetworkMonitor`. Konflik diselesaikan per-jenis data: data transaksi kasir (order, status, shift, petty cash) lokal menang lewat penanda `dirtyFields`; data master pusat (menu, harga, kategori, print layout) server menang lewat penimpaan cache.

**Tech Stack:** Kotlin, Jetpack Compose, Room 2.6.1, Retrofit 2.9 + OkHttp 4.12, Gson, Coroutines/StateFlow, WorkManager 2.9.0 (baru), Supabase PostgREST + RPC. Test: JUnit4, MockK 1.13.9, kotlinx-coroutines-test 1.7.3, Room `MigrationTestHelper` (androidTest).

## Global Constraints

- `minSdk = 26`, `compileSdk = 34`, `targetSdk = 34`, `jvmTarget = "17"`. Jangan pakai API di atas level 26 tanpa `Build.VERSION` guard.
- Package root: `com.sukashawarma.pos`. Ikuti struktur yang ada: `data/local`, `data/local/dao`, `data/local/entity`, `data/remote`, `data/repository`, `data/sync`, `domain/model`, `domain/usecase`, `presentation/<fitur>`.
- Semua DAO baru ditaruh di file DAO yang sudah relevan bila kecil; DAO baru yang berdiri sendiri dapat file sendiri. `KioskSettingDao` dan `SyncQueueDao` saat ini menumpang di `dao/MenuItemDao.kt` — **Task 3 memindahkan `SyncQueueDao` ke filenya sendiri**, sisanya dibiarkan.
- **DILARANG** memakai `fallbackToDestructiveMigration()`. Setiap kenaikan versi database wajib punya `Migration` eksplisit dan test di `app/src/androidTest/.../MigrationTest.kt`.
- **DILARANG** pola `try { api.x() } catch (e: Exception) { e.printStackTrace() }` untuk mutasi. Setiap mutasi wajib lewat outbox.
- Komentar kode dan pesan commit ditulis dalam Bahasa Indonesia, mengikuti commit terakhir (`feat(gate): ...`, `fix(gate): ...`). Format: Conventional Commits dengan scope.
- Teks yang dilihat kasir ditulis Bahasa Indonesia.
- Zona waktu bisnis: **Asia/Jakarta**. Sudah ada helper `com.sukashawarma.pos.domain.gate.JakartaTime` — pakai itu, jangan bikin baru.
- Unit test di `app/src/test` **tidak boleh** menyentuh kelas Android (`Context`, `ConnectivityManager`, Room asli). Logika yang perlu diuji harus dipisah ke kelas murni Kotlin.
- Perintah test: `./gradlew :app:testDebugUnitTest --tests "<pola>"`. Perintah migration test: `./gradlew :app:connectedDebugAndroidTest --tests "<pola>"` (butuh emulator/device).
- Commit setiap akhir task. Jangan pernah `git add -A` — sebut file secara eksplisit.

---

# FASE 1 — FONDASI

Fase 1 menghasilkan mesin sync yang bekerja tapi belum ada yang memakainya kecuali order create yang sudah ada. Setelah Fase 1 selesai aplikasi harus tetap berjalan normal.

---

### Task 1: NetworkMonitor — status jaringan reaktif se-aplikasi

Masalah yang diperbaiki: `POSManualOrderViewModel.isNetworkAvailable()` mengecek `NET_CAPABILITY_INTERNET` saja. Kapabilitas itu bernilai `true` bahkan ketika tablet tersambung WiFi outlet yang **routernya hidup tapi internetnya mati** — kasus paling umum di lapangan. Yang benar adalah `NET_CAPABILITY_VALIDATED`, yang berarti OS sudah membuktikan ada jalan keluar ke internet.

**Files:**
- Create: `app/src/main/java/com/sukashawarma/pos/data/remote/NetworkMonitor.kt`
- Create: `app/src/main/java/com/sukashawarma/pos/data/remote/NetworkStatus.kt`
- Create: `app/src/test/java/com/sukashawarma/pos/data/remote/NetworkStatusTest.kt`
- Modify: `app/src/main/java/com/sukashawarma/pos/POSApplication.kt`

**Interfaces:**
- Consumes: tidak ada (task pertama).
- Produces:
  - `object NetworkMonitor { fun init(context: Context); val isOnline: StateFlow<Boolean> }`
  - `object NetworkStatus { fun isValidatedInternet(hasInternet: Boolean, isValidated: Boolean): Boolean }`

- [ ] **Step 1: Tulis test yang gagal**

File: `app/src/test/java/com/sukashawarma/pos/data/remote/NetworkStatusTest.kt`

```kotlin
package com.sukashawarma.pos.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkStatusTest {

    @Test
    fun `internet plus validated dianggap online`() {
        assertTrue(NetworkStatus.isValidatedInternet(hasInternet = true, isValidated = true))
    }

    @Test
    fun `wifi outlet nyala tapi internet mati dianggap offline`() {
        // Kasus paling sering di outlet: router hidup, tagihan ISP telat.
        // NET_CAPABILITY_INTERNET tetap true, VALIDATED false.
        assertFalse(NetworkStatus.isValidatedInternet(hasInternet = true, isValidated = false))
    }

    @Test
    fun `tanpa kapabilitas internet dianggap offline`() {
        assertFalse(NetworkStatus.isValidatedInternet(hasInternet = false, isValidated = true))
        assertFalse(NetworkStatus.isValidatedInternet(hasInternet = false, isValidated = false))
    }
}
```

- [ ] **Step 2: Jalankan test, pastikan GAGAL**

```bash
./gradlew :app:testDebugUnitTest --tests "com.sukashawarma.pos.data.remote.NetworkStatusTest"
```

Expected: FAIL — `Unresolved reference: NetworkStatus`.

- [ ] **Step 3: Tulis implementasi minimal**

File: `app/src/main/java/com/sukashawarma/pos/data/remote/NetworkStatus.kt`

```kotlin
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
```

- [ ] **Step 4: Jalankan test, pastikan LULUS**

```bash
./gradlew :app:testDebugUnitTest --tests "com.sukashawarma.pos.data.remote.NetworkStatusTest"
```

Expected: PASS, 3 test.

- [ ] **Step 5: Tulis NetworkMonitor**

File: `app/src/main/java/com/sukashawarma/pos/data/remote/NetworkMonitor.kt`

```kotlin
package com.sukashawarma.pos.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sumber kebenaran tunggal status jaringan untuk seluruh aplikasi.
 *
 * Memakai callback OS (bukan polling) sehingga perubahan jaringan terdeteksi
 * seketika, dan memakai NET_CAPABILITY_VALIDATED sehingga "WiFi nyala tapi
 * internet mati" dihitung sebagai OFFLINE — lihat [NetworkStatus].
 */
object NetworkMonitor {

    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private var connectivityManager: ConnectivityManager? = null
    private var registered = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            _isOnline.value = NetworkStatus.isValidatedInternet(
                hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            )
        }

        override fun onLost(network: Network) {
            _isOnline.value = false
        }

        override fun onUnavailable() {
            _isOnline.value = false
        }
    }

    /** Dipanggil sekali dari POSApplication.onCreate(). Aman dipanggil berulang. */
    fun init(context: Context) {
        if (registered) return
        val cm = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        connectivityManager = cm

        // Nilai awal sebelum callback pertama datang, supaya UI tidak sempat
        // menampilkan OFFLINE palsu saat aplikasi baru dibuka dalam keadaan online.
        val active = cm.activeNetwork
        val caps = active?.let { cm.getNetworkCapabilities(it) }
        _isOnline.value = caps != null && NetworkStatus.isValidatedInternet(
            hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        )

        cm.registerDefaultNetworkCallback(callback)
        registered = true
    }
}
```

- [ ] **Step 6: Daftarkan di POSApplication**

File: `app/src/main/java/com/sukashawarma/pos/POSApplication.kt` — tambahkan import dan satu baris di `onCreate()`:

```kotlin
import com.sukashawarma.pos.data.remote.NetworkMonitor
```

```kotlin
    override fun onCreate() {
        super.onCreate()
        PrinterPrefs.init(this)
        AuthPrefs.init(this)
        SessionPrefs.init(this)
        NetworkMonitor.init(this)
        NotificationChannels.createChannels(this)
    }
```

- [ ] **Step 7: Pastikan aplikasi tetap ter-compile**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/sukashawarma/pos/data/remote/NetworkMonitor.kt app/src/main/java/com/sukashawarma/pos/data/remote/NetworkStatus.kt app/src/test/java/com/sukashawarma/pos/data/remote/NetworkStatusTest.kt app/src/main/java/com/sukashawarma/pos/POSApplication.kt
git commit -m "feat(offline): NetworkMonitor berbasis NET_CAPABILITY_VALIDATED"
```

---

### Task 2: Buang `fallbackToDestructiveMigration()`

Masalah yang diperbaiki: [AppDatabase.kt](app/src/main/java/com/sukashawarma/pos/data/local/AppDatabase.kt) menulis tiga komentar panjang yang menjelaskan kenapa migrasi harus manual agar order belum tersync tidak hilang, lalu memanggil `fallbackToDestructiveMigration()` tepat di bawahnya. Satu mismatch skema = seluruh penjualan offline terhapus tanpa jejak.

**Files:**
- Modify: `app/src/main/java/com/sukashawarma/pos/data/local/AppDatabase.kt:95-105`
- Modify: `app/src/androidTest/java/com/sukashawarma/pos/data/local/MigrationTest.kt`

**Interfaces:**
- Consumes: tidak ada.
- Produces: `AppDatabase.getInstance(context)` yang melempar `IllegalStateException` alih-alih menghapus data bila ada versi tanpa migrasi.

- [ ] **Step 1: Tulis test yang gagal**

Tambahkan test ini ke `app/src/androidTest/java/com/sukashawarma/pos/data/local/MigrationTest.kt` (di dalam class `MigrationTest`, setelah test terakhir):

```kotlin
    @Test(expected = IllegalStateException::class)
    fun databaseTanpaMigrasiHarusGagalBukanMenghapusData() {
        // Database versi 4 dibuka sebagai versi 5 TANPA mendaftarkan MIGRATION_4_5.
        // Perilaku yang benar: melempar IllegalStateException.
        // Perilaku yang SALAH (fallbackToDestructiveMigration): menghapus semua tabel diam-diam.
        var db = helper.createDatabase(dbName, 4)
        db.execSQL(
            """
            INSERT INTO local_orders
                (id, outletId, orderNumber, customerName, status, source, paymentMethod,
                 itemsJson, subtotal, discountAmount, totalAmount, amountReceived,
                 changeAmount, kitchenReceiptPrinted, createdAt, isPendingSync, channel)
            VALUES
                ('order-penting', 'outlet-a', 12, 'Budi', 'PREPARING', 'POS', 'CASH',
                 '[]', 25000.0, 0.0, 25000.0, 25000.0, 0.0, 0, 1000, 1, NULL)
            """.trimIndent()
        )
        db.close()

        // Tanpa migrasi apa pun: harus melempar, bukan menghapus.
        helper.runMigrationsAndValidate(dbName, 5, true)
    }
```

- [ ] **Step 2: Jalankan test, pastikan GAGAL**

```bash
./gradlew :app:connectedDebugAndroidTest --tests "com.sukashawarma.pos.data.local.MigrationTest"
```

Expected: test baru FAIL — `runMigrationsAndValidate` melapor missing migration dengan tipe exception yang berbeda, atau test lain ikut terpengaruh. Catat pesannya.

Catatan: `MigrationTestHelper.runMigrationsAndValidate` memang melempar `IllegalStateException` untuk migrasi yang hilang; test ini terutama mengunci **perilaku produksi** di Step 3. Bila test langsung lulus di sini, tetap lanjutkan — nilai test ini adalah mencegah `fallbackToDestructiveMigration()` dikembalikan orang lain di kemudian hari.

- [ ] **Step 3: Hapus baris berbahaya**

File: `app/src/main/java/com/sukashawarma/pos/data/local/AppDatabase.kt` — pada `getInstance`, hapus `.fallbackToDestructiveMigration()`:

```kotlin
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pos_sukashawarma.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                 // TIDAK ADA fallbackToDestructiveMigration di sini, dan jangan pernah
                 // ditambahkan: local_orders + sync_queue menyimpan penjualan yang belum
                 // naik ke server. Lebih baik aplikasi gagal terbuka dan kita perbaiki
                 // migrasinya daripada omzet satu hari hilang tanpa jejak.
                 .build()
                INSTANCE = instance
                instance
            }
        }
```

- [ ] **Step 4: Jalankan test, pastikan LULUS**

```bash
./gradlew :app:connectedDebugAndroidTest --tests "com.sukashawarma.pos.data.local.MigrationTest"
```

Expected: PASS, semua test migrasi.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/sukashawarma/pos/data/local/AppDatabase.kt app/src/androidTest/java/com/sukashawarma/pos/data/local/MigrationTest.kt
git commit -m "fix(offline): buang fallbackToDestructiveMigration agar order belum sync tidak terhapus"
```

---

### Task 3: Skema outbox & syncState (migration 5 → 6)

Tabel `sync_queue` sudah ada sejak awal tapi **tidak pernah ditulis maupun dibaca**. Task ini memberinya kolom yang dibutuhkan mesin sync, dan mengganti `local_orders.isPendingSync` (boolean) dengan `syncState` (enum string 3 nilai) + `dirtyFields`.

Penggantian kolom dilakukan dengan **membuat ulang tabel** (create baru → salin → drop lama → rename), pola standar Room untuk SQLite yang `DROP COLUMN`-nya tidak tersedia di semua level API yang kita dukung.

**Files:**
- Modify: `app/src/main/java/com/sukashawarma/pos/data/local/entity/LocalMenuItemEntity.kt` (definisi `SyncQueueEntity` ada di file ini)
- Modify: `app/src/main/java/com/sukashawarma/pos/data/local/entity/LocalOrderEntity.kt`
- Create: `app/src/main/java/com/sukashawarma/pos/data/local/dao/SyncQueueDao.kt`
- Modify: `app/src/main/java/com/sukashawarma/pos/data/local/dao/MenuItemDao.kt` (hapus `SyncQueueDao` dari sini)
- Modify: `app/src/main/java/com/sukashawarma/pos/data/local/dao/OrderDao.kt`
- Modify: `app/src/main/java/com/sukashawarma/pos/data/local/AppDatabase.kt`
- Modify: `app/src/androidTest/java/com/sukashawarma/pos/data/local/MigrationTest.kt`

**Interfaces:**
- Consumes: tidak ada.
- Produces:
  - `enum class SyncState { SYNCED, PENDING, FAILED }`
  - `SyncQueueEntity(queueId: Long, actionType: String, entityId: String, idempotencyKey: String, payloadJson: String, status: String, attemptCount: Int, nextAttemptAt: Long, lastError: String?, createdAt: Long)`
  - `LocalOrderEntity` dengan `syncState: String` dan `dirtyFields: String` menggantikan `isPendingSync: Boolean`
  - `SyncQueueDao`: `getReady(now: Long): List<SyncQueueEntity>`, `getAllOrdered(): List<SyncQueueEntity>`, `insert(item): Long`, `delete(queueId: Long)`, `markFailed(queueId, error, now)`, `markRetry(queueId, attemptCount, nextAttemptAt, error)`, `countFailedFlow(): Flow<Int>`, `deleteByIdempotencyKey(key: String)`
  - `AppDatabase.MIGRATION_5_6`

- [ ] **Step 1: Tulis test migrasi yang gagal**

Tambahkan ke `MigrationTest`:

```kotlin
    @Test
    fun migrate5To6_orderPendingJadiSyncStatePendingDanOutboxDapatKolomBaru() {
        var db = helper.createDatabase(dbName, 5)
        db.execSQL(
            """
            INSERT INTO local_orders
                (id, outletId, orderNumber, customerName, status, source, paymentMethod,
                 itemsJson, subtotal, discountAmount, totalAmount, amountReceived,
                 changeAmount, kitchenReceiptPrinted, customerReceiptPrinted,
                 cancellationStatus, cancellationUserName, createdAt, isPendingSync, channel)
            VALUES
                ('order-offline', 'outlet-a', 9001, 'Budi', 'PREPARING', 'POS', 'CASH',
                 '[]', 25000.0, 0.0, 25000.0, 25000.0, 0.0, 0, 0, NULL, NULL, 1000, 1, NULL),
                ('order-sudah-sync', 'outlet-a', 12, 'Sari', 'COMPLETED', 'POS', 'QRIS',
                 '[]', 30000.0, 0.0, 30000.0, 30000.0, 0.0, 1, 1, NULL, NULL, 2000, 0, NULL)
            """.trimIndent()
        )
        db.execSQL(
            "INSERT INTO sync_queue (queueId, actionType, payloadJson, createdAt) " +
                "VALUES (7, 'CREATE_ORDER', '{\"a\":1}', 500)"
        )
        db.close()

        db = helper.runMigrationsAndValidate(
            dbName, 6, true,
            AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6
        )

        val orders = db.query("SELECT id, syncState, dirtyFields FROM local_orders ORDER BY id")
        orders.moveToFirst()
        assert(orders.getString(0) == "order-offline")
        assert(orders.getString(1) == "PENDING") { "isPendingSync=1 harus jadi syncState PENDING" }
        assert(orders.getString(2) == "") { "dirtyFields harus kosong untuk baris lama" }
        orders.moveToNext()
        assert(orders.getString(0) == "order-sudah-sync")
        assert(orders.getString(1) == "SYNCED") { "isPendingSync=0 harus jadi syncState SYNCED" }
        orders.close()

        val queue = db.query(
            "SELECT idempotencyKey, entityId, status, attemptCount, nextAttemptAt, lastError " +
                "FROM sync_queue WHERE queueId = 7"
        )
        queue.moveToFirst()
        assert(queue.getString(0) == "legacy-7") { "baris outbox lama harus dapat idempotencyKey" }
        assert(queue.getString(1) == "") { "entityId default kosong" }
        assert(queue.getString(2) == "PENDING")
        assert(queue.getInt(3) == 0)
        assert(queue.getLong(4) == 0L)
        assert(queue.isNull(5))
        queue.close()
        db.close()
    }
```

- [ ] **Step 2: Jalankan test, pastikan GAGAL**

```bash
./gradlew :app:connectedDebugAndroidTest --tests "com.sukashawarma.pos.data.local.MigrationTest"
```

Expected: FAIL — `Unresolved reference: MIGRATION_5_6`.

- [ ] **Step 3: Ubah entity**

File: `app/src/main/java/com/sukashawarma/pos/data/local/entity/LocalOrderEntity.kt` — ganti seluruh isi file:

```kotlin
package com.sukashawarma.pos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Status sinkronisasi satu baris data lokal terhadap server. */
enum class SyncState {
    /** Sudah sama dengan server. */
    SYNCED,

    /** Ada perubahan lokal yang belum naik; masih punya baris di sync_queue. */
    PENDING,

    /** Server MENOLAK perubahan ini (bukan gangguan jaringan). Butuh perhatian kasir. */
    FAILED
}

@Entity(tableName = "local_orders")
data class LocalOrderEntity(
    @PrimaryKey val id: String,
    val outletId: String,
    val orderNumber: Int,
    val customerName: String,
    val status: String, // PENDING, PREPARING, READY, COMPLETED, CANCELLED
    val source: String, // POS, KIOSK, ONLINE
    val paymentMethod: String, // CASH, QRIS, CARD
    val itemsJson: String, // JSON String array of items
    val subtotal: Double,
    val discountAmount: Double,
    val totalAmount: Double,
    val amountReceived: Double,
    val changeAmount: Double,
    val kitchenReceiptPrinted: Boolean,
    val customerReceiptPrinted: Boolean,
    val cancellationStatus: String?,
    val cancellationUserName: String?,
    val createdAt: Long,
    /** Nama [SyncState]. Menggantikan kolom isPendingSync yang lama. */
    val syncState: String = SyncState.SYNCED.name,
    /**
     * Daftar kolom yang diubah kasir dan BELUM naik ke server, dipisah koma
     * (mis. "status,customerReceiptPrinted"). Selama tidak kosong, baris ini
     * tidak boleh ditimpa oleh data tarikan server — inilah bentuk konkret
     * aturan "data transaksi kasir lokal menang".
     */
    val dirtyFields: String = "",
    val channel: String? = null
)
```

- [ ] **Step 4: Ubah SyncQueueEntity**

File: `app/src/main/java/com/sukashawarma/pos/data/local/entity/LocalMenuItemEntity.kt` — ganti definisi `SyncQueueEntity` (biarkan `LocalMenuItemEntity` dan `LocalKioskSettingEntity` apa adanya), dan tambahkan import `androidx.room.Index`:

```kotlin
import androidx.room.Index
```

```kotlin
/**
 * Outbox: satu baris per mutasi kasir yang belum sampai ke server.
 *
 * Ditulis dalam transaksi Room yang SAMA dengan perubahan tabel datanya,
 * sehingga tidak mungkin UI berubah tanpa antrian sync ikut terisi.
 */
@Entity(
    tableName = "sync_queue",
    indices = [Index(value = ["idempotencyKey"], unique = true)]
)
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val queueId: Long = 0,

    /** CREATE_ORDER, UPDATE_ORDER_STATUS, MARK_RECEIPT_PRINTED, REQUEST_CANCELLATION, UPLOAD_PAYMENT_PROOF */
    val actionType: String,

    /** id baris data yang diubah (mis. orders.id). Dipakai untuk menjaga urutan per-entitas. */
    val entityId: String,

    /**
     * Kunci anti-kirim-ganda. Untuk mutasi yang idempoten secara alami
     * (create order, set status) berbentuk "$actionType:$entityId" sehingga
     * mutasi berulang menimpa baris antrian lama alih-alih menumpuk.
     */
    val idempotencyKey: String,

    val payloadJson: String,

    /** Nama [SyncState]: PENDING (menunggu/berulang) atau FAILED (ditolak server). */
    val status: String = SyncState.PENDING.name,

    val attemptCount: Int = 0,

    /** Epoch millis paling awal boleh dicoba lagi (backoff). 0 = boleh sekarang. */
    val nextAttemptAt: Long = 0,

    val lastError: String? = null,

    val createdAt: Long = System.currentTimeMillis()
)
```

- [ ] **Step 5: Pindahkan SyncQueueDao ke file sendiri**

File: `app/src/main/java/com/sukashawarma/pos/data/local/dao/MenuItemDao.kt` — **hapus** blok `@Dao interface SyncQueueDao { ... }` beserta import `SyncQueueEntity` yang jadi tidak terpakai.

File baru: `app/src/main/java/com/sukashawarma/pos/data/local/dao/SyncQueueDao.kt`

```kotlin
package com.sukashawarma.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sukashawarma.pos.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncQueueDao {

    /** Antrian yang boleh dicoba sekarang, FIFO. FAILED tidak ikut — butuh aksi kasir. */
    @Query(
        "SELECT * FROM sync_queue WHERE status = 'PENDING' AND nextAttemptAt <= :now " +
            "ORDER BY queueId ASC"
    )
    suspend fun getReady(now: Long): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue ORDER BY queueId ASC")
    suspend fun getAllOrdered(): List<SyncQueueEntity>

    /** REPLACE + unique index idempotencyKey: mutasi berulang menimpa, tidak menumpuk. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: SyncQueueEntity): Long

    @Query("DELETE FROM sync_queue WHERE queueId = :queueId")
    suspend fun delete(queueId: Long)

    @Query("DELETE FROM sync_queue WHERE idempotencyKey = :key")
    suspend fun deleteByIdempotencyKey(key: String)

    @Query(
        "UPDATE sync_queue SET status = 'FAILED', lastError = :error, attemptCount = attemptCount + 1 " +
            "WHERE queueId = :queueId"
    )
    suspend fun markFailed(queueId: Long, error: String)

    @Query(
        "UPDATE sync_queue SET attemptCount = :attemptCount, nextAttemptAt = :nextAttemptAt, " +
            "lastError = :error WHERE queueId = :queueId"
    )
    suspend fun markRetry(queueId: Long, attemptCount: Int, nextAttemptAt: Long, error: String?)

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'PENDING'")
    fun countPendingFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'FAILED'")
    fun countFailedFlow(): Flow<Int>
}
```

- [ ] **Step 6: Sesuaikan OrderDao ke syncState**

File: `app/src/main/java/com/sukashawarma/pos/data/local/dao/OrderDao.kt` — ganti empat query terakhir yang memakai `isPendingSync`, dan tambahkan query untuk `dirtyFields`. Ganti bagian dari `getMaxOfflineOrderNumber` sampai akhir interface dengan:

```kotlin
    @Query("DELETE FROM local_orders WHERE id = :orderId")
    suspend fun deleteOrder(orderId: String)

    @Query(
        "SELECT * FROM local_orders WHERE outletId = :outletId AND syncState != 'SYNCED' " +
            "ORDER BY createdAt ASC"
    )
    suspend fun getUnsyncedOrders(outletId: String): List<LocalOrderEntity>

    @Query(
        "SELECT COUNT(*) FROM local_orders WHERE outletId = :outletId AND syncState = 'PENDING'"
    )
    fun getPendingSyncCountFlow(outletId: String): Flow<Int>

    @Query("UPDATE local_orders SET syncState = :syncState WHERE id = :orderId")
    suspend fun setSyncState(orderId: String, syncState: String)

    @Query("UPDATE local_orders SET dirtyFields = :dirtyFields WHERE id = :orderId")
    suspend fun setDirtyFields(orderId: String, dirtyFields: String)

    @Query("SELECT dirtyFields FROM local_orders WHERE id = :orderId")
    suspend fun getDirtyFields(orderId: String): String?

    @Query(
        "UPDATE local_orders SET orderNumber = :serverOrderNumber, syncState = 'SYNCED', " +
            "dirtyFields = '' WHERE id = :orderId"
    )
    suspend fun markSynced(orderId: String, serverOrderNumber: Int)
```

**Hapus** `getMaxOfflineOrderNumber` dan `getMaxServerOrderNumber` — keduanya digantikan di Task 7. Sementara itu `POSManualOrderViewModel` akan gagal compile; Step 8 menanganinya.

- [ ] **Step 7: Tulis MIGRATION_5_6**

File: `app/src/main/java/com/sukashawarma/pos/data/local/AppDatabase.kt` — naikkan `version = 6`, tambahkan migrasi ke `addMigrations`, dan tambahkan konstanta berikut di dalam `companion object`:

```kotlin
        /**
         * v6: local_orders.isPendingSync (boolean) -> syncState (enum string) + dirtyFields,
         * dan sync_queue mendapat kolom yang dibutuhkan SyncEngine.
         *
         * local_orders dibuat ulang karena SQLite di minSdk 26 tidak punya DROP COLUMN.
         * Datanya DISALIN, bukan dibuang — tabel ini menyimpan penjualan yang belum naik.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE local_orders_baru (
                        id TEXT NOT NULL PRIMARY KEY,
                        outletId TEXT NOT NULL,
                        orderNumber INTEGER NOT NULL,
                        customerName TEXT NOT NULL,
                        status TEXT NOT NULL,
                        source TEXT NOT NULL,
                        paymentMethod TEXT NOT NULL,
                        itemsJson TEXT NOT NULL,
                        subtotal REAL NOT NULL,
                        discountAmount REAL NOT NULL,
                        totalAmount REAL NOT NULL,
                        amountReceived REAL NOT NULL,
                        changeAmount REAL NOT NULL,
                        kitchenReceiptPrinted INTEGER NOT NULL,
                        customerReceiptPrinted INTEGER NOT NULL,
                        cancellationStatus TEXT,
                        cancellationUserName TEXT,
                        createdAt INTEGER NOT NULL,
                        syncState TEXT NOT NULL DEFAULT 'SYNCED',
                        dirtyFields TEXT NOT NULL DEFAULT '',
                        channel TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO local_orders_baru (
                        id, outletId, orderNumber, customerName, status, source, paymentMethod,
                        itemsJson, subtotal, discountAmount, totalAmount, amountReceived,
                        changeAmount, kitchenReceiptPrinted, customerReceiptPrinted,
                        cancellationStatus, cancellationUserName, createdAt,
                        syncState, dirtyFields, channel
                    )
                    SELECT
                        id, outletId, orderNumber, customerName, status, source, paymentMethod,
                        itemsJson, subtotal, discountAmount, totalAmount, amountReceived,
                        changeAmount, kitchenReceiptPrinted, customerReceiptPrinted,
                        cancellationStatus, cancellationUserName, createdAt,
                        CASE WHEN isPendingSync = 1 THEN 'PENDING' ELSE 'SYNCED' END,
                        '', channel
                    FROM local_orders
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE local_orders")
                db.execSQL("ALTER TABLE local_orders_baru RENAME TO local_orders")

                db.execSQL("ALTER TABLE sync_queue ADD COLUMN entityId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE sync_queue ADD COLUMN idempotencyKey TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE sync_queue ADD COLUMN status TEXT NOT NULL DEFAULT 'PENDING'")
                db.execSQL("ALTER TABLE sync_queue ADD COLUMN attemptCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sync_queue ADD COLUMN nextAttemptAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sync_queue ADD COLUMN lastError TEXT")
                // Baris outbox lama (kalau ada) belum punya kunci; beri kunci unik dari queueId
                // supaya unique index di bawah tidak menolaknya.
                db.execSQL("UPDATE sync_queue SET idempotencyKey = 'legacy-' || queueId")
                db.execSQL(
                    "CREATE UNIQUE INDEX index_sync_queue_idempotencyKey ON sync_queue (idempotencyKey)"
                )
            }
        }
```

Dan pada deklarasi database:

```kotlin
@Database(
    entities = [
        LocalOrderEntity::class,
        LocalMenuItemEntity::class,
        SyncQueueEntity::class,
        LocalKioskSettingEntity::class
    ],
    version = 6,
    exportSchema = false
)
```

```kotlin
                ).addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6
                )
```

- [ ] **Step 8: Perbaiki pemanggil yang rusak agar tetap compile**

File: `app/src/main/java/com/sukashawarma/pos/presentation/order_manual/POSManualOrderViewModel.kt` — di `submitOrder()`, ganti dua baris pembacaan nomor lama dan field entity. Perubahan ini sementara; Task 7 dan 8 menggantinya sepenuhnya.

Ganti:

```kotlin
            val maxOffline = orderDao.getMaxOfflineOrderNumber(outletId) ?: 9000
            val maxServer = orderDao.getMaxServerOrderNumber(outletId) ?: 0
```

menjadi:

```kotlin
            // Sementara sampai Task 7 memasang penomoran harian yang benar.
            val maxOffline = 9000
            val maxServer = 0
```

Dan pada pembuatan `LocalOrderEntity`, ganti `isPendingSync = pendingSync,` menjadi:

```kotlin
                syncState = if (pendingSync) SyncState.PENDING.name else SyncState.SYNCED.name,
                dirtyFields = "",
```

dengan import `com.sukashawarma.pos.data.local.entity.SyncState`.

File: `app/src/main/java/com/sukashawarma/pos/presentation/dashboard/DashboardViewModel.kt` — pada `mapEntityToOrder`, ganti `isOffline = entity.isPendingSync,` menjadi:

```kotlin
            isOffline = entity.syncState != SyncState.SYNCED.name,
```

Pada `dtoToEntity`, ganti `isPendingSync = false,` menjadi:

```kotlin
            syncState = SyncState.SYNCED.name,
            dirtyFields = "",
```

Tambahkan import `com.sukashawarma.pos.data.local.entity.SyncState`.

File: `app/src/main/java/com/sukashawarma/pos/data/sync/OrderSyncEngine.kt` — ganti `orderDao.getPendingSyncOrders(outletId)` menjadi `orderDao.getUnsyncedOrders(outletId)`.

- [ ] **Step 9: Jalankan test migrasi, pastikan LULUS**

```bash
./gradlew :app:connectedDebugAndroidTest --tests "com.sukashawarma.pos.data.local.MigrationTest"
```

Expected: PASS, semua test termasuk `migrate5To6_...`.

- [ ] **Step 10: Pastikan compile & unit test lama tetap hijau**

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 11: Commit**

```bash
git add app/src/main/java/com/sukashawarma/pos/data/local/entity/LocalOrderEntity.kt app/src/main/java/com/sukashawarma/pos/data/local/entity/LocalMenuItemEntity.kt app/src/main/java/com/sukashawarma/pos/data/local/dao/SyncQueueDao.kt app/src/main/java/com/sukashawarma/pos/data/local/dao/MenuItemDao.kt app/src/main/java/com/sukashawarma/pos/data/local/dao/OrderDao.kt app/src/main/java/com/sukashawarma/pos/data/local/AppDatabase.kt app/src/androidTest/java/com/sukashawarma/pos/data/local/MigrationTest.kt app/src/main/java/com/sukashawarma/pos/presentation/order_manual/POSManualOrderViewModel.kt app/src/main/java/com/sukashawarma/pos/presentation/dashboard/DashboardViewModel.kt app/src/main/java/com/sukashawarma/pos/data/sync/OrderSyncEngine.kt
git commit -m "feat(offline): skema outbox sync_queue dan syncState pada local_orders (migrasi 5-6)"
```

---
