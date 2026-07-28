# Fondasi Menu Repository — Sumber Data Bersama untuk Manajemen Menu & Pesanan Baru

**Status:** Approved
**Tanggal:** 2026-07-28

## Latar Belakang

App Kotlin adalah hasil porting dari web Next.js `apps/pos-kasir`, tapi hasilnya
belum sesuai. Analisis gap lengkap ada di `lanjutan.md` (root repo). Salah satu
akar masalahnya di lapisan data: `MenuManagementViewModel` dan
`POSManualOrderViewModel` masing-masing memanggil `api.getCategories()` +
`api.getMenuItems()` sendiri-sendiri, tanpa filter per outlet, dan tanpa
menyentuh `kiosk_settings` sama sekali. Web memakai satu fungsi `fetchMenuData`
yang sama untuk kedua halaman, dengan aturan filter outlet dan precedence
`kiosk_settings` yang konsisten.

Ini sub-project **A** dari lima (**A → B → C → D → E**, lihat `lanjutan.md` §5).
A adalah fondasi — dibutuhkan oleh sub-project B (Manajemen Menu) dan D
(Pesanan Baru) sebelum keduanya bisa disamakan dengan web.

**Tidak ada perubahan UI di spec ini.** Dua layar yang ada sekarang hanya
diganti sumber datanya; tampilannya belum disentuh.

## Tujuan

1. Satu `MenuRepository` bersama, dipakai `MenuManagementViewModel` dan
   `POSManualOrderViewModel`, supaya logika precedence/filter/availability
   tidak ditulis dua kali (persis penyebab masalah yang sedang diperbaiki).
2. Menu terfilter per outlet, meniru aturan filter web persis.
3. `kiosk_settings` (bestseller, upsell, rekomendasi, unavailable manual/auto,
   force-available) terbaca dan diterapkan ke ketersediaan item, meniru
   precedence `getSetting()` di web (outlet sendiri > PUSAT > global).
4. Cache offline Room benar-benar terisi (saat ini `MenuItemDao` dan
   `LocalMenuItemEntity` ada tapi tidak pernah ditulis).
5. Realtime pada `menu_items` dan `kiosk_settings` meng-invalidate cache,
   meniru `supabase.channel(...).on('postgres_changes', ...)` di web.

## Opsi yang ditolak (jangan diulang tanpa alasan baru)

- **Perluas masing-masing ViewModel tanpa repository** — logika precedence,
  filter outlet, dan aturan ketersediaan jadi ditulis dua kali. Itu persis
  penyebab masalah yang sedang diperbaiki.
- **Port penuh + antrean sync offline untuk perubahan menu** — over-engineering,
  dan justru membuat Kotlin **berbeda** dari web (web memblokir perubahan menu
  saat offline, lihat `KasirMenuClient.tsx` guard `guardOnline`). Kotlin harus
  ikut memblokir, bukan mengantrekan.

## Arsitektur

```
MenuManagementViewModel ─┐
                         ├─► MenuRepository ─┬─► SupabaseApi   (menu_items, categories, kiosk_settings)
POSManualOrderViewModel ─┘                   ├─► Room          (cache offline)
                                             └─► RealtimeManager (invalidasi)
```

Kontrak repository sengaja dibuat sempit — mengeluarkan satu objek utuh:

```kotlin
data class MenuSnapshot(
    val items: List<MenuItem>,        // sudah difilter per outlet
    val categories: List<Category>,
    val settings: KioskSettings,
    val fromCache: Boolean            // true = jaringan gagal, data dari Room
)

class MenuRepository {
    fun snapshot(outletId: String): Flow<MenuSnapshot>
    suspend fun refresh(outletId: String)
    suspend fun setUnavailable(...)   // dipakai sub-project B, bukan di sini
    suspend fun setFlag(...)          // bestseller/upsell/rekomendasi/paksa-aktif — B
}
```

`setUnavailable` dan `setFlag` didefinisikan sebagai bagian kontrak sekarang
supaya sub-project B tidak perlu mengubah bentuk repository, tapi
implementasinya (dual-write ke `menu_items` vs `kiosk_settings`, toast,
guard offline UI) adalah scope B, bukan A.

### Alur data

```
ViewModel.snapshot(outletId)  ──►  MenuRepository
                                        │
                    ┌───────────────────┴───────────────────┐
                    │ 1. emit cache Room dulu (kalau ada)    │  ← layar langsung terisi
                    │ 2. fetch paralel: menu_items,          │
                    │    categories, kiosk_settings          │
                    │ 3. resolveSetting → filterByOutlet     │
                    │ 4. emit snapshot(fromCache = false)    │
                    │ 5. tulis Room di background            │
                    └────────────────────────────────────────┘
                                        ▲
                        realtime menu_items / kiosk_settings ──┘
```

Langkah 1 meniru web: halaman menu web memakai data SSR sebagai `initialData`
lalu React Query merefresh (`app/kasir/menu/page.tsx:108`). Padanan terdekat
di Android adalah cache Room.

`outletId` diambil dari `SessionPrefs.getOutletId()` yang sudah ada — tidak
perlu mekanisme baru.

## Tiga aturan bisnis jadi fungsi murni

Ditempatkan di `domain/menu/MenuRules.kt`, terpisah dari networking supaya
bisa dites tanpa Android:

1. **`resolveSetting(rows, key, outletId)`** — precedence baris
   `kiosk_settings`: outlet sendiri > PUSAT > global (`outlet_id` null).
   Port persis dari fungsi `getSetting()` di web.
   `PUSAT_OUTLET_ID = "550e8400-e29b-41d4-a716-446655440001"`.
2. **`filterByOutlet(items, outletId)`** — port persis dari filter di web:
   kalau `available_outlets` non-kosong → harus memuat `outletId`;
   kalau `outlet_id` ada dan bukan `outletId` dan bukan PUSAT → disaring
   keluar; selain itu → lolos.
3. **`isItemAvailable(item, settings)`** —
   `is_available && !(manualUnav || (autoUnav && !forceAvail))`.

Poin 3 penting: di web aturan itu ditulis **dua kali**
(`KasirMenuClient.tsx:443` dan `order-manual/page.tsx:381`). Di Kotlin cukup
satu, lalu dipakai bersama oleh B dan D.

## Komponen per lapisan

**Remote**
- `MenuItemDto` ditambah: `outlet_id`, `strike_price`,
  `channel_prices` (`Map<String, Double>?`), `is_available_online`,
  `available_online_channels` (`List<String>?`), `is_package`,
  `package_items`, relasi `categories`.
- DTO baru `KioskSettingDto(outletId, key, value)`.
- Endpoint baru `getKioskSettings()` dengan filter
  `or=(outlet_id.is.null,outlet_id.eq.<PUSAT>,outlet_id.eq.<outlet>)` dan
  `key=in.(bestseller_ids,upsell_ids,unavailable_menu_ids,auto_unavailable_menu_ids,force_available_menu_ids,recommendation_ids)`.
- Endpoint baru `upsertKioskSetting()` dengan `on_conflict=outlet_id,key`
  — parameter persis seperti yang dikirim web. (Dipakai B, ditulis sekarang
  supaya kontraknya siap.)

**Domain**
- `MenuItem` diperluas mengikuti DTO.
- `KioskSettings` sebagai satu objek berisi enam himpunan: `bestsellers`,
  `upsells`, `recommendations`, `unavailableIds`, `autoUnavailableIds`,
  `forceAvailableIds`.
- `MenuRules.kt` berisi tiga fungsi di atas.

**Lokal (Room)**
- `LocalMenuItemEntity` ditambah kolom baru mengikuti `MenuItem`.
- Entity baru `LocalKioskSettingEntity(key, jsonValue, syncedAt)`.
- Versi database naik dari 2 ke 3.
- **Wajib menulis migrasi Room eksplisit** (`Migration(2, 3) { ... }`), bukan
  mengandalkan `fallbackToDestructiveMigration()` yang sekarang dipakai di
  `AppDatabase.kt:38`. Alasan: database yang sama juga menyimpan
  `sync_queue` berisi order offline yang belum terkirim. Destructive
  migration akan menghapusnya saat aplikasi di-update.

**Realtime**
- `OrderRealtimeManager` (`data/remote/realtime/OrderRealtimeManager.kt`)
  digeneralisasi supaya bisa berlangganan tabel lain (`menu_items`,
  `kiosk_settings`), meniru `supabase.channel(...).on('postgres_changes', ...)`
  di web. Implementasinya WebSocket Phoenix hand-rolled, bukan SDK Supabase.

## Penanganan error

Mengikuti web persis: kalau fetch gagal (jaringan mati atau timeout),
**jangan tampilkan error** — jatuh ke cache Room dan set `fromCache = true`.
Web melakukan hal yang sama di `KasirMenuClient.tsx:127`. Kalau cache juga
kosong, keluarkan snapshot kosong, bukan crash.

Repository **tidak** menampilkan pesan apa pun sendiri; dia hanya
mengeluarkan `fromCache`. Keputusan mau bilang apa ke kasir ada di layar
(sub-project B/D). Ini yang menjaga repository tetap bisa dites tanpa UI.

## Yang tidak dikerjakan (YAGNI / di luar scope A)

- Tidak ada perubahan UI di `MenuManagementScreen` / `POSManualOrderScreen` —
  itu sub-project B dan D.
- Tidak ada antrean sync offline untuk perubahan menu — web memblokir
  perubahan menu saat offline, Kotlin ikut memblokir (lihat "Opsi yang
  ditolak" di atas).
- Implementasi nyata `setUnavailable`/`setFlag` (dual-write, toast, guard UI)
  — kontraknya didefinisikan di A, isinya ditulis di B.
- Halaman Info Porsi (`monitoring_view_crew`) — sub-project C, sumber
  datanya beda (view, bukan `menu_items`).
- Penyeragaman warna — palet sudah sesuai web, tidak perlu disentuh.

## Asumsi yang belum diverifikasi (cek saat implementasi)

1. **`available_outlets` tidak ditemukan di skema mana pun** yang sudah
   diperiksa (`supabase-schema.sql`, migrasi, `types/index.ts`), padahal web
   memfilternya (`KasirMenuClient.tsx:87`). Kemungkinan kolom ini tidak ada
   di database produksi dan cabang kode itu mati. **Tetap port apa adanya**
   — kalau kolomnya tidak ada, DTO-nya null dan hasilnya sama (lolos filter).
2. **`kiosk_settings.outlet_id` di `supabase-schema.sql` bertipe `NOT NULL`**,
   tapi web membaca baris dengan `outlet_id.is.null`. Berarti skema produksi
   kemungkinan sudah berubah (kolom dibuat nullable). Verifikasi ke database
   sebelum mengandalkan cabang "global setting" — kalau ternyata tidak ada
   baris `outlet_id IS NULL` di produksi, precedence PUSAT/outlet tetap harus
   jalan benar untuk dua tingkat yang tersisa.

## Pengujian

Unit test JVM biasa, tanpa emulator:

- `resolveSetting` — baris outlet menang atas PUSAT, PUSAT menang atas
  global; JSON rusak → himpunan kosong (web memakai `try/catch` yang
  mengembalikan `[]`).
- `filterByOutlet` — item dengan `available_outlets` memuat outlet kita →
  lolos; item milik outlet lain → tersaring; item PUSAT → lolos; item tanpa
  `outlet_id` → lolos.
- `isItemAvailable` — matriks lengkap `is_available` × manual × auto ×
  force. Kasus kunci: auto-habis **tapi** dipaksa aktif → tersedia.
- Repository dengan API palsu: fetch gagal → keluar data cache dengan
  `fromCache = true`.

## Verifikasi

- Build sukses, migrasi Room 2→3 jalan tanpa menghapus `sync_queue` yang
  sudah ada (test manual: isi `sync_queue`, jalankan migrasi, cek masih ada).
- Buka Manajemen Menu / Pesanan Baru dengan dua outlet berbeda yang punya
  `menu_items.outlet_id` berbeda → daftar menu berbeda sesuai outlet.
  Item PUSAT tetap muncul di kedua outlet.
- Matikan jaringan setelah satu kali load sukses → buka layar lagi → data
  lama tetap tampil (dari Room), tidak ada crash atau layar kosong.
- Ubah baris `kiosk_settings` langsung di Supabase (mis. tambah item ke
  `unavailable_menu_ids`) → realtime menandai item itu tidak tersedia tanpa
  restart app.
