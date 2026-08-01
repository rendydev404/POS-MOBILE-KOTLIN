# Parity Gate Kasir Web → Native

Tanggal: 2026-08-01

Menyamakan sistem blokir POS kasir di aplikasi native dengan versi web
(`apps/pos-kasir`), sehingga POS terbuka otomatis secara realtime ketika ada kru
yang absen hadir, lalu beralih ke state checklist sampai checklist buka toko
selesai.

Sumber kebenaran versi web:

- `components/GlobalBlockerMount.tsx` — logika gate
- `components/BlockedOverlay.tsx` — UI overlay

## Masalah pada implementasi native saat ini

1. **Auto-buka realtime tidak jalan.** `OrderRealtimeManager` hanya subscribe ke
   `orders`, `owner_messages`, `daily_sales_targets`, dan `bypass_requests`.
   Tabel `attendance`, `daily_checklist_ticks`, `daily_checklist_records`,
   `outlet_staff`, dan `outlets` tidak di-subscribe, dan tidak ada mekanisme
   re-check lain. Satu-satunya cara overlay hilang setelah kru absen adalah
   menekan tombol "Refresh Status" secara manual.
2. **Dua tipe blokir hilang.** Web punya lima tipe (`user`, `outlet`,
   `attendance`, `checklist`, `closed`). Native hanya punya tiga, dan `closed`
   tidak dibedakan secara visual. Akun atau cabang yang dinonaktifkan
   Administrator tidak memblokir apa pun di native.
3. **Timezone salah.** Web menghitung hari dengan `Asia/Jakarta` eksplisit.
   Native memakai `ZoneId.systemDefault()` dan `SimpleDateFormat` default,
   sehingga tablet dengan timezone non-WIB menghitung hari yang berbeda.
4. **Bug session.** `MainActivity` mengoper `session.username` sebagai `staffId`
   ke `AttendanceViewModel`, padahal `SessionPrefs` menyimpan `staff_id` berupa
   UUID. Akibatnya `requested_by_name` berisi username dan pemeriksaan profil
   per-user tidak mungkin dilakukan.
5. **Query parameter dobel.** `getAttendance` mendeklarasikan default
   `@Query("select")` dan `@Query("order")`, sementara pemanggilnya juga
   mengirim `select` dan `order` lewat `@QueryMap`. URL yang dihasilkan memuat
   kedua parameter itu dua kali dan perilaku PostgREST menjadi tidak
   deterministik.
6. **UI jauh berbeda.** Native menampilkan satu ikon gembok dan teks polos.
   Web menampilkan ikon dan warna per tipe, progress bar checklist, indikator
   pulse, kotak alasan penonaktifan, tombol logout, dan form bypass dua tahap.
7. **Bypass tidak persist.** Web menyimpan `pos_gate_bypassed_types` di
   `sessionStorage`. Native tidak menyimpan apa pun, sehingga bypass yang sudah
   disetujui hilang begitu state di-refresh.

Filter `phase=buka` untuk checklist sudah benar — sudah ada sebagai default
`@Query` di `SupabaseApi.getChecklistCategories`.

## Arsitektur

`AttendanceViewModel` diganti menjadi `PosGateViewModel`, mirror satu-lawan-satu
dari `GlobalBlockerMount.tsx`. Tiga `StateFlow` terpisah yang ada sekarang
diganti satu state:

```kotlin
enum class BlockType { USER, OUTLET, ATTENDANCE, CHECKLIST, CLOSED }
enum class BypassStatus { PENDING, REJECTED }

data class ChecklistProgress(val total: Int, val done: Int)

data class GateState(
    val isBlocked: Boolean = false,
    val type: BlockType = BlockType.USER,
    val reason: String = "",
    val progress: ChecklistProgress? = null,
    val bypassStatus: BypassStatus? = null,
)
```

`AttendanceOverlay.kt` menjadi `BlockedOverlay.kt`: composable murni yang hanya
membaca `GateState` dan memanggil callback, setara props `reason` / `type` /
`progress` / `onBypass` di web. Pemisahan ini membuat overlay bisa di-preview
untuk setiap tipe tanpa jaringan.

Pembagian tanggung jawab:

- `PosGateViewModel` — evaluasi gate, request bypass, penyimpanan bypass lokal.
- `BlockedOverlay` — presentasi murni, tanpa akses jaringan.
- `OrderRealtimeManager` — sumber sinyal perubahan.
- `GlobalEventBus.gateRefreshEvent` — satu-satunya jalur antara sinyal dan
  view model.

## Realtime tanpa polling

`OrderRealtimeManager` menambah lima subscription menyamai web:

| Tabel                     | Filter                      |
| ------------------------- | --------------------------- |
| `attendance`              | `outlet_id=eq.<outletId>`   |
| `daily_checklist_ticks`   | —                           |
| `daily_checklist_records` | `outlet_id=eq.<outletId>`   |
| `outlet_staff`            | —                           |
| `outlets`                 | —                           |

`daily_checklist_ticks` tidak difilter karena baris tick hanya memuat
`record_id` dan `item_id`, tidak memuat `outlet_id`; ini sama dengan web.

Semua event tersebut dipetakan ke satu event baru
`GlobalEventBus.gateRefreshEvent`. Event `bypass_requests` yang sudah ada ikut
dialihkan ke event yang sama. `PosGateViewModel` meng-collect event itu dan
memanggil `checkGate()`.

Polling 5 detik milik web **tidak** diport. Sebagai gantinya:

- **on-reconnect** — `OrderRealtimeManager.onConnectionState(connected = true)`
  meng-emit `gateRefreshEvent`, sehingga setiap kali WebSocket berhasil
  re-subscribe status langsung dievaluasi ulang.
- **on-resume** — `LifecycleEventObserver` untuk `ON_RESUME` di `MainActivity`
  meng-emit event yang sama.

Tidak ada `delay()` berulang atau timer periodik di jalur gate.

## Logika gate

Urutan evaluasi mengikuti `checkStatus()` lalu `checkKasirGate()` di web:

1. Cek daftar bypass lokal. Jika memuat `all`, tidak diblokir dan evaluasi
   berhenti. Hanya `all` yang diperiksa di titik ini, karena tipe blokir belum
   diketahui sebelum evaluasi berjalan; `handleBypass` di web memang selalu
   menambahkan `all` bersama tipe spesifiknya.
2. Ambil profil lewat `getStaffById(staffId)`. Jika `is_active == false`, blokir
   tipe `USER` dengan alasan `inactive_reason`. Jika `role == "admin"`, tidak
   pernah diblokir.
3. Jika `outlets.is_active == false`, blokir tipe `OUTLET` dengan alasan
   `inactive_reason` milik outlet.
4. Jika `role` termasuk `crew` atau `leader` dan `outlet_id` ada, lanjut ke gate
   kasir. Selain itu tidak diblokir.
5. Jika ada `bypass_requests` berstatus `approved` untuk outlet ini hari ini,
   tidak diblokir.
6. Ambil `attendance` hari ini untuk outlet, susun map `outlet_staff_id → type`
   secara kronologis sehingga tiap staf menyisakan tipe terakhirnya.
   - Map kosong → blokir `ATTENDANCE`, alasan "Menunggu kru absen hadir."
   - Map terisi tapi tidak ada yang bertipe `in` → blokir `CLOSED`, alasan
     "Semua kru sudah absen pulang. Toko sudah tutup untuk hari ini."
   - Ada yang bertipe `in` → lanjut.
7. Hitung item checklist `phase=buka` yang `is_required` sebagai `total`. Ambil
   `daily_checklist_records` untuk outlet ini dengan `date` sama dengan tanggal
   hari ini di `Asia/Jakarta`; bila record tidak ada, `done` bernilai nol. Bila
   ada, `done` adalah jumlah item required yang `id`-nya muncul di
   `daily_checklist_ticks` dengan `record_id` tersebut. Jika
   `total > 0 && done < total`, blokir `CHECKLIST` dengan progress `done/total`.
   Selain itu tidak diblokir.
8. Exception apa pun pada langkah mana pun berarti **fail-open**: state
   di-reset ke tidak diblokir. Ini menyamai web, yang sengaja tidak memblokir
   saat gagal agar operasional tidak terganggu.

### Perbaikan yang menyertai

- Hari dihitung dengan `ZoneId.of("Asia/Jakarta")`; batas rentang query memakai
  offset `+07:00` menyamai `start`/`end` di web.
- `MainActivity` mengoper `SessionPrefs.getStaffId()` (UUID), bukan
  `session.username`.
- `@QueryMap` tidak lagi mengirim key `select` atau `order` yang sudah
  dideklarasikan sebagai default `@Query`, menghilangkan parameter dobel.
- `StaffProfileDto` dan `OutletDto` mendapat field `inactive_reason`.
- Bypass mengirim `requested_by_name` dari `staff.name`, bukan username.
- Daftar tipe yang sudah di-bypass dipersist ke `SessionPrefs` (setara
  `sessionStorage` di web) dan dibersihkan saat logout.
- Status bypass `rejected` menampilkan "Pengajuan bypass ditolak oleh SPV."

## UI overlay

`BlockedOverlay` disusun ulang mengikuti `BlockedOverlay.tsx`: latar gelap
`gray-900/95` dengan blur, kartu putih sudut membulat besar, konten tengah.

| Tipe         | Ikon            | Warna ikon | Judul                             |
| ------------ | --------------- | ---------- | --------------------------------- |
| `ATTENDANCE` | Clock           | amber      | Menunggu Kehadiran Kru            |
| `CHECKLIST`  | ClipboardCheck  | amber      | Checklist Buka Toko Belum Selesai |
| `CLOSED`     | Moon            | indigo     | Toko Sudah Tutup                  |
| `USER`       | Ban             | merah      | Akun Dinonaktifkan                |
| `OUTLET`     | Ban             | merah      | Cabang Dinonaktifkan              |

Teks pesan panjang untuk tiap tipe disalin verbatim dari map `MESSAGE` di web.

Elemen tambahan:

- Progress bar amber dengan label "Progress Checklist Buka Toko" dan
  "`done`/`total` tugas", hanya untuk `CHECKLIST` dengan `total > 0`.
- Indikator pulse untuk `ATTENDANCE`, `CHECKLIST`, dan `CLOSED`, dengan teks
  "Menunggu Sinyal Absensi...", "Menunggu Checklist Diselesaikan...", dan
  "Menunggu Sesi Baru..." sesuai map `PULSE_LABEL` di web.
- Kotak alasan berlatar merah untuk `USER` dan `OUTLET`.
- Tombol Logout untuk `USER`, `OUTLET`, dan `CLOSED`.
- Link teks "Gunakan Bypass Darurat" untuk `ATTENDANCE`, `CHECKLIST`, dan
  `CLOSED`.

Form bypass dua tahap:

1. Tahap input — ikon AlertTriangle, judul "Pengajuan Bypass", textarea alasan
   dengan placeholder web, tombol Batal dan "Kirim ke SPV".
2. Tahap menunggu — ikon Clock berdenyut, judul "Menunggu Persetujuan", tombol
   hijau "Buka WhatsApp SPV" yang membuka intent WhatsApp, dan tombol "Kembali".

Tombol "Refresh Status" yang ada sekarang dihapus; web tidak memilikinya dan
re-check sudah otomatis lewat realtime, reconnect, dan resume.

Nomor WhatsApp diambil dari `outlets.phone` (dinormalkan ke format `62…`), dan
jatuh ke `6285218446637` bila kosong — nomor fallback yang sama dengan web.

## Testing

Unit test `PosGateViewModel` dengan fake `SupabaseApi`, satu test per skenario:

1. Belum ada absen hari ini → `ATTENDANCE`.
2. Satu kru bertipe `in` dan checklist selesai → tidak diblokir.
3. Semua kru sudah `out` → `CLOSED`.
4. Kru `in` dan checklist parsial → `CHECKLIST` dengan progress benar.
5. Bypass `approved` hari ini → tidak diblokir walau absen kosong.
6. `is_active == false` pada staf → `USER` dengan alasan dari `inactive_reason`.
7. `is_active == false` pada outlet → `OUTLET`.
8. API melempar exception → tidak diblokir (fail-open).

Ditambah dua test batas: absensi milik hari kemarin diabaikan menurut
`Asia/Jakarta`, dan `role == "admin"` tidak pernah diblokir.

Verifikasi manual di perangkat: overlay `ATTENDANCE` tampil, seorang kru absen
hadir dari aplikasi lain, dan overlay berpindah ke state `CHECKLIST` lalu hilang
tanpa interaksi apa pun di tablet POS.

## Di luar cakupan

Native tidak mendapat layar checklist. Web `pos-kasir` juga tidak memilikinya —
checklist dikerjakan kru di aplikasi terpisah, dan POS hanya menunggu. "Beralih
ke checklist" berarti overlay berganti ke state `CHECKLIST` beserta progress
bar, persis seperti web.
