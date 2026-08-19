# SUKA Superapp Native — Review Menyeluruh, Inventaris Fitur, & Rencana Eksekusi

**Tanggal:** 2026-08-18
**Cakupan review:** `DIGITALISASI-SS-PROJECT` (monorepo web, 11 app + 4 package, 397 migration) dan `D:\PROJECT-APPS-NATIVE\POS` (APK POS native yang sudah live)
**Target:** **SATU aplikasi Android**. User login sekali (SSO), lalu memilih modul — Absensi, Stok, Distribusi — dari layar Home. POS dikecualikan karena sudah jadi APK terpisah dan sudah live di 19 outlet.

---

## Keputusan yang sudah diambil

| # | Keputusan | Pilihan | Tanggal |
|---|---|---|---|
| 0 | **Repo** | **Repo baru:** https://github.com/rendydev404/SUPER-APPS-SS-MOBILE (public, kosong, branch `main`). Repo POS tidak disentuh. | 2026-08-18 |
| 1 | **Bentuk aplikasi** | **SATU APK** `com.sukashawarma.superapp`. Portal jadi layar Home di dalamnya, bukan app terpisah. Setelah login, user memilih modul dari grid. | 2026-08-18 |
| 2 | **Model wajah** | **W4** — web pindah ke model native. Satu kolom `face_descriptor`, satu model, satu threshold. | 2026-08-18 |
| 3 | **Nasib app web** | Dipertahankan sebagai jalur desktop. Semua perubahan backend **wajib aditif**. | 2026-08-18 |
| 4 | **POS** | Tetap APK sendiri (`com.sukashawarma.pos`), tidak digabung. Superapp cukup deep-link ke sana. | 2026-08-18 |

> **Catatan revisi:** revisi sebelumnya sempat memutuskan 4 APK terpisah. Itu dibatalkan. Konsekuensi langsungnya: **seluruh pekerjaan SSO antar-proses (`ContentProvider` `signature`-protected) hilang** — tidak dibutuhkan lagi karena semua modul berada dalam satu proses, berbagi satu `AuthSessionManager`. Ini memangkas 3–4 hari kerja dan menghapus satu kelas risiko (ROM yang mematikan provider, force-stop, izin lintas-app).

---

## CAKUPAN — apa yang dibuat dan apa yang TIDAK

### ✅ Dibuat native (4 modul)

| Modul web asal | Jadi | Fitur |
|---|---|---|
| `apps/portal` | Layar **Home / Launcher** di dalam superapp | 17 |
| `apps/absensi` | Modul **Absensi** | 44 |
| `apps/stok` | Modul **Stok** | 52 |
| `apps/distribusi` | Modul **Distribusi** | 23 |

### ❌ TIDAK dibuat native — tetap web saja

`apps/admin-dashboard` · `apps/owner-dashboard` · `apps/finance` · `apps/manager` · `apps/pos-kasir`

Kelima ini **tidak boleh masuk backlog native dalam bentuk apa pun** — bukan sebagai modul, bukan sebagai layar, bukan sebagai "versi ringkas". Superapp hanya boleh **menautkan keluar** ke versi web-nya lewat Custom Tab (H-07), dan itu pun hanya tautan, bukan implementasi ulang.

`apps/pos-kasir` dikecualikan dengan alasan berbeda: sudah punya APK native sendiri (`com.sukashawarma.pos`), jadi superapp cukup deep-link ke sana (H-08).

### ⚠️ Konsekuensi: 4 role tidak punya modul apa pun di superapp

Matriks `ROLE_APP_ACCESS` diproyeksikan ke cakupan native:

| Role | Modul di superapp | Di luar cakupan |
|---|---|---|
| `spv`, `regional_manager`, `leader`, `crew`, `area_manager` | absensi, stok, distribusi | pos-kasir, admin-dashboard, finance, manager |
| `kitchen` | stok, distribusi | — *(cakupannya pas)* |
| `admin` | stok, distribusi | admin-dashboard, finance |
| `admin_hr`, `staff_pusat` | absensi | admin-dashboard |
| `admin_finance`, `purchasing` | stok | finance, admin-dashboard |
| **`owner`** | **— kosong —** | owner-dashboard, admin-dashboard, finance |
| **`mitra`** | **— kosong —** | admin-dashboard |
| **`developer`** | **— kosong —** | admin-dashboard |
| **`kiosk`** | **— kosong —** | pos-kasir *(punya APK sendiri)* |

`kiosk` tidak masalah — POS memang APK terpisah dan itu memang satu-satunya app-nya.

`owner`, `mitra`, `developer` bisa login tapi Home-nya kosong. **✅ Diputuskan 2026-08-18:** tampilkan Home kosong + tombol **"Buka di Web"** yang membawa sesi — user tidak login ulang. Login **tidak** diblokir. Lihat §3.6 untuk mekanismenya.

## 3.6 Serah-terima sesi native → web ("Buka di Web" tanpa login ulang)

**Kabar baik: mekanismenya sudah ada dan sudah jalan di produksi.** POS native sudah melakukan ini untuk web stok.

| Sisi | Berkas | Isi |
|---|---|---|
| Native | `domain/usecase/StokOutletLauncher.kt` (POS) | Bangun URL `<web>/auth/sso#access_token=…&refresh_token=…&next=…`, buka di Chrome (fallback browser default) |
| Web | `apps/stok/src/app/auth/sso/page.tsx` | Baca token dari **fragment**, `setSession()` lewat `@supabase/ssr` → jadi cookie yang dibaca middleware `enforceAppAccess`, lalu `location.replace(next)` |

Desainnya sudah benar dan tidak perlu diubah:
- Token dikirim di **fragment URL**, bukan query string — fragment tidak pernah dikirim ke server, jadi tidak mendarat di access log CDN maupun header `Referer`.
- `next` divalidasi harus path internal (`/` dan bukan `//`) — mencegah open redirect.
- Token dibuang dari history lewat `location.replace`.
- Outlet tidak perlu dikirim: web menentukan scope dari `auth.uid()` lewat `accessible_outlet_ids()`.

### Yang harus dikerjakan

| # | Task | Catatan |
|---|---|---|
| SSO-1 | Salin rute `/auth/sso` ke 4 app web di luar cakupan: `admin-dashboard`, `owner-dashboard`, `finance`, `manager` | Halaman identik; kecualikan dari matcher middleware seperti di stok |
| SSO-2 | Generalisasi `StokOutletLauncher` jadi `WebHandoffLauncher(appUrl, path)` di `core/auth` | Satu launcher untuk semua tujuan web |
| SSO-3 | **Ganti cara sesi web dibuat** — lihat konflik di bawah | Wajib, bukan opsional |

### 🔴 Konflik yang baru ketahuan: SSO handoff bergantung pada password plaintext

`StokOutletLauncher.mintWebSession()` sengaja **sign-in ulang memakai `AuthPrefs.getLastUsername()` + `getLastPassword()`** untuk mendapat baris sesi terpisah. Alasannya valid dan penting: Supabase merotasi `refresh_token`, jadi kalau browser dan app berbagi satu sesi, refresh di browser **mencabut token app** dan user tiba-tiba ter-logout di tengah kerja.

Tapi ini persis temuan **X3** — password plaintext di SharedPreferences — yang task 1.2 rencanakan untuk dibuang. Kedua task itu bertabrakan: buang password, SSO handoff kehilangan sifat sesi-terpisahnya dan jatuh ke fallback berbagi sesi (yang komentarnya sendiri akui berisiko).

**Solusi: Edge Function `mint-web-session`.**
1. Native memanggilnya dengan JWT-nya sendiri.
2. Edge Function memverifikasi JWT → dapat `user.id` → pakai service-role `auth.admin.generateLink({ type: 'magiclink', email })` → tukar hashed token lewat `/auth/v1/verify` → dapat **sesi baru yang independen**.
3. Kembalikan `access_token` + `refresh_token` sesi baru itu ke native, yang langsung memakainya untuk URL handoff.

Hasilnya: sesi terpisah tetap didapat, password tidak perlu disimpan sama sekali, dan X3 benar-benar tertutup. Ditambahkan sebagai task 2.16.

### Dampak ke backend

View `my_apps` (task 2.1) mengembalikan **semua** app yang boleh diakses role, termasuk yang di luar cakupan. Client native **wajib memfilternya** ke `{absensi, stok, distribusi}` untuk grid modul, dan memperlakukan sisanya sebagai kartu tautan-keluar. Jangan filter di sisi DB — web masih butuh daftar penuh.

---

# DAFTAR ISI

- **Bagian 1** — Peta sistem saat ini
- **Bagian 2** — Review per aplikasi (temuan)
- **Bagian 3** — Arsitektur superapp
- **Bagian 4** — **INVENTARIS FITUR LENGKAP** (yang harus dibuat di native)
- **Bagian 5** — Pekerjaan backend prasyarat
- **Bagian 6** — Rencana eksekusi bertahap
- **Bagian 7** — Risiko & keputusan terbuka
- **Lampiran A** — Daftar temuan review terurut prioritas
- **Lampiran B** — Inventaris kode POS yang dipakai ulang
- **Lampiran C** — Peta tabel, RPC, dan Edge Function

---

# BAGIAN 1 — PETA SISTEM SAAT INI

## 1.1 Inventaris repo

| Workspace | Peran | File TS/TSX | LOC | Port |
|---|---|---|---|---|
| `apps/absensi` | Presensi + face recognition + checklist + cuti + kasbon | 88 | 10.722 | 3001 |
| `apps/stok` | Ledger, opname, permintaan, mutasi, waste, monitoring | 123 | 15.847 | 3001 |
| `apps/distribusi` | Surat jalan, pengiriman, terima bahan, PO, cetak | 64 | 6.655 | 3002 |
| `apps/portal` | SSO launcher role-based | 12 | — | 3010 |
| `apps/pos-kasir` | POS web (disalip versi native) | — | — | 3004 |
| `apps/admin-dashboard` | Administrasi staff/akun, laporan POS, marketplace | — | — | 3005 |
| `apps/owner-dashboard` | Omzet & analisis keuangan | — | — | 3003 |
| `apps/finance` | Petty cash, payroll, PO settlement | — | — | 3020 |
| `apps/manager` | Approval operasional area | — | — | 3000 |
| `packages/auth` | SSO, matriks role→app, client, middleware, JWT | 11 | 666 | — |
| `packages/design-system` | Token brand, komponen, bridge WebView | 21 | — | — |
| `packages/realtime` | Hook channel + invalidasi React Query (ADR-0014) | 5 | — | — |
| `packages/offline-queue` | Antrean offline `idb-keyval` | 5 | — | — |
| `mobile/superapp` | Shell Expo RN + WebView — **mangkrak** | — | — | — |
| `mobile/native-superapp` | Kotlin+Compose, absensi fase 1 — **mangkrak** | ~30 | — | — |
| `mobile/native-pos` | Kotlin+Compose POS — **mangkrak** | ~17 | — | — |
| `supabase/` | 397 migration + 13 Edge Function | — | — | — |

**Repo POS native** (`D:\PROJECT-APPS-NATIVE\POS`) — repo git terpisah. `com.sukashawarma.pos`, versionCode 54 / v1.0.53, minSdk 26, targetSdk 34, Compose BOM 2024.02, Room 2.6.1, Retrofit + OkHttp, FCM, ZXing, Konfetti, archive-patcher. **115 file Kotlin / 23.105 LOC.**

## 1.2 Model data & otorisasi

- **Identitas kanonik:** `outlet_staff`, `id` = `auth.users.id`. `profiles` = view kompatibilitas lama.
- **15 role:** `admin, admin_hr, owner, spv, regional_manager, leader, crew, kiosk, kitchen, mitra, staff_pusat, admin_finance, area_manager, purchasing, developer`. (`korlap` masih dipakai di beberapa query tapi **tidak ada di union `Role`** — sumber bug diam di `MonitoringPage.tsx`.)
- **Matriks role→app:** `packages/auth/src/access.ts`.
- **Scope outlet:** `accessible_outlet_ids()` — satu-satunya sumber scope RLS. Privileged = semua outlet; leader/korlap = `staff_outlets`; crew/kiosk/mitra = `outlet_staff.outlet_id`.
- **Login:** username tanpa `@` → pseudo-email `<username>@outlet.local` (ADR-008).
- **Override outlet harian:** `getOutletStaff()` menimpa `outlet_id` dengan lokasi absen terakhir hari ini — mekanisme BKO/mutasi harian. **Wajib direplikasi di native**, kalau tidak crew BKO akan melihat data outlet yang salah.

## 1.3 Infrastruktur yang sudah lunas di POS

| Kapabilitas | Implementasi | Pakai ulang |
|---|---|---|
| Auth GoTrue | `AuthApi` (password + refresh grant), `AuthSessionManager` (mutex, refresh saat `exp` < 5 mnt, rem 60 dtk), `SessionTokenHolder`, `AuthPrefs` | ✅ 1:1 |
| Interceptor REST | `SupabaseClient` — `apikey` + `Authorization` khusus host sendiri, `Authenticator` refresh-on-401 + guard `X-Token-Retry` | ✅ 1:1 |
| Realtime | Phoenix WebSocket di atas OkHttp: `OrderRealtimeManager`, `UpdateRealtimeManager`, `UpdateRealtimeProtocol`, heartbeat, reconnect, resubscribe on network-up | ✅ setelah digeneralisasi |
| Offline-first | Room v9 + `SyncQueueEntity` (idempotencyKey unik, PENDING/FAILED, backoff) + `OrderSyncEngine` (409 = sukses) | ✅ pola generik |
| Foreground service | `POSRealtimeService` + notification channel | ✅ |
| Push | FCM + Edge Function `send-fcm` (service-account JWT via `jose`, data-only, dedup by id) | ✅ |
| Auto-update non-Play-Store | `global_settings.app_update` → push realtime → download → `PackageInstaller.Session` + `USER_ACTION_NOT_REQUIRED`, delta APK `archive-patcher`, pill draggable | ✅ **namespace per app** |
| Cetak thermal | `BluetoothPrinterManager`, `ESCPosEncoder`, `RawBTFallback`, `EscPosBuilder` | ✅ dibutuhkan distribusi |
| Keystore | `suka-shawarma-release.jks`, kredensial dari `local.properties`, release diblokir bila signing tak lengkap | ✅ **wajib** |
| Adaptive UI | `POSAdaptiveScaffold` + `material3-window-size-class`, strong skipping | ✅ |

**≈3.000 LOC siap pakai ulang.** Pekerjaan terberat sudah selesai; sisanya UI + logika domain + membereskan backend.

---

# BAGIAN 2 — REVIEW PER APLIKASI

## 2.1 `apps/absensi`

### Alur clock-in (`useClockKiosk.ts`, 785 LOC) — inti yang harus direplikasi persis

1. **`checkLocation()`** — ambil `outlets.lat/lng` fresh. `is_active=false` → layar terkunci (Emergency Lock pusat). `lat/lng` NULL → bypass geofence (HQ/Kantor Pusat).
2. **Dua probe GPS** — probe cepat (`enableHighAccuracy:false`, maxAge 30 dtk) untuk UX instan, `watchPosition` presisi di belakang.
3. **Anti-fake-GPS lapis 1** — tolak bila `isMock` true atau `accuracy` persis `1.0`/`0.0`.
4. **Gate akurasi** — `accuracy > 150 m` ditolak.
5. **Geofence** — `max(0, haversine − accuracy) <= 100 m`.
6. **Deteksi wajah** throttle 250 ms (~4 FPS) via `@vladmandic/human`; embedding → `/api/face-match` (mode server, default) atau cocok lokal (mode client legacy).
7. **`decideAction()`** — baca `attendance` hari ini (WIB) → `in` / `out` / `done`.
8. **Gate absen pulang, 3 lapis** — checklist fase `tutup` semua item wajib tercentang **DAN** tidak ada `shifts.status='open'` **DAN** (server) tidak ada `orders` berstatus `pending`/`preparing`/`ready`.
9. **Liveness** — challenge acak menoleh kiri/kanan, 2 fase (gerak → kembali frontal), throttle 200 ms, batal bila wajah hilang / >1 wajah / identitas berubah >3 detik.
10. **Submit** — upload selfie `selfies/{outlet_id}/{uuid}.jpg`, POST `/api/submit-attendance`.

### Logika server (`/api/submit-attendance`, 325 LOC)
Status staff aktif → cross-outlet (kecuali role global atau terdaftar di `staff_outlets`) → sudah enroll → anti-fake-GPS (dicatat sebagai baris `attendance` `status='fake_gps_blocked'`) → akurasi → **anti-teleportasi** (>160 km/j dibanding absen terakhir <2 jam, dicatat `teleportation_blocked`) → geofence → `selfie_path` wajib berprefiks `outlet_id` → gate shift & order → baca `outlet_attendance_config` (fallback `global_settings.global_attendance_config`) → hitung status (`tepat` / `telat_toleransi` / `telat` / `lebih_awal` / `pulang_telat`) → upsert idempoten (`onConflict:id, ignoreDuplicates`) → bila tipe `in` dan beda outlet, `outlet_staff.outlet_id` di-update ke lokasi absen.

### Temuan

| # | Tingkat | Temuan |
|---|---|---|
| A1 | **Kritis** | **`/api/submit-attendance` tidak memverifikasi pemanggil.** Route tidak pernah membaca header `Authorization`. Client mengirim token dari `localStorage['supabase-auth-token']` — kunci yang **tidak pernah ditulis** `@supabase/ssr` (sesi ada di cookie), jadi selalu `null` dan selalu jatuh ke anon key. Efeknya: endpoint service-role terbuka. Siapa pun yang tahu `outlet_staff_id` + koordinat outlet bisa mencatat absensi orang lain. |
| A2 | **Kritis** | **`/api/face-match` juga tanpa auth**, dan pada match sukses **mengembalikan `face_descriptor` mentah**. Kebocoran biometrik — descriptor bisa dipanen per-outlet lalu dipakai memalsukan match di jalur client-mode. |
| A3 | Tinggi | Mode client legacy masih bisa diaktifkan (`matchMode`, `setMatchMode` diekspor ke UI, env `NEXT_PUBLIC_FACE_MATCH_MODE`) — seluruh descriptor outlet diunduh ke browser. |
| A4 | Tinggi | Deteksi mock GPS `accuracy === 1.0 \|\| 0.0` mudah dielakkan dan rawan false-positive; `is_mock` dikirim client dan bisa dipalsukan. Android punya API yang benar: `Location.isFromMockProvider()`. |
| A5 | Sedang | `checkLocation` di-`useCallback` dengan dependensi `outletCoords` yang di-`setState` di dalamnya sendiri → identitas fungsi berubah tiap koordinat masuk. |
| A6 | Sedang | `scheduleReset` memakai `setTimeout` telanjang tanpa disimpan/di-clear; reset bisa menumpuk dan saling menimpa fase. |
| A7 | Sedang | Model Human ~20 MB diunduh dari CDN jsdelivr tiap perangkat baru — penyebab utama lambat/gagal di outlet berkoneksi buruk. |
| A8 | Rendah | Komentar `decideAction` tidak sesuai kodenya. |

## 2.2 `apps/stok`

### Sistem satuan — bagian paling berbahaya
`compositeUnit.ts` (436 LOC) menangani satuan tiga tingkat (besar → tengah → kecil) dengan `faktor_konversi`, `faktor_tengah`, `faktor_tampilan`, plus kolom terhitung **`stok_balance.saldo_is_gram`**. Sesi 2026-08-04 menemukan **11 fungsi penulis `ledger_stok` tidak scale-aware**; 141 dari 1517 baris saldo berisiko korup. 8 dari 11 sudah diperbaiki (`20300105000017`).

### Temuan

| # | Tingkat | Temuan |
|---|---|---|
| S1 | **Kritis** | **Semua mutasi stok lewat Server Action Next.js** (8 file, 29 export). Server Action **tidak bisa dipanggil aplikasi native** — Action ID di-hash per build dan berubah tiap deploy. Blocker #1. |
| S2 | **Kritis** | RPC `*_svc` **SECURITY DEFINER tanpa cek role**; gerbang hanya di TypeScript (`requirePermintaanApprover`). Native memanggil langsung = gerbang hilang total. Sama untuk `set_opname_pending` (nol guard). |
| S3 | Tinggi | `makeServiceClient()` punya **fallback hardcode ke anon key** bila env kosong → turun senyap ke anon, query balik kosong tanpa error jelas. |
| S4 | Tinggi | `kitchen` bisa opname outlet lain (draft → `set_opname_pending` → approve sendiri lewat service-role). Didokumentasikan 2026-08-13, **belum diperbaiki**. |
| S5 | Sedang | Outlet virtual `type='marketplace'` baru difilter di admin-dashboard; stok/absensi/distribusi belum. |
| S6 | Sedang | Ranjau 8 migration bertimestamp 2030 selalu jalan terakhir dan bisa menimpa perbaikan bertanggal wajar. |
| S7 | Sedang | `korlap` tidak ada di union `Role` → jatuh ke `CrewDashboard` walau seharusnya multi-outlet (komentar eksplisit di `MonitoringPage.tsx`). |

## 2.3 `apps/distribusi`

**Nol Server Action.** Semua mutasi lewat RPC PostgREST dengan JWT user — **bisa dipanggil apa adanya dari Retrofit**. Ini yang membuatnya kandidat pertama.

| # | Tingkat | Temuan |
|---|---|---|
| D1 | Tinggi | Trigger `sj_on_dikirim_kurangi_kitchen` menulis delta `ledger_stok` **selalu satuan besar**, tanpa cek `saldo_is_gram` outlet tujuan. Kirim 1 Pack ke baris gram-scale bisa tercatat −0,2. **Belum diperbaiki**, akan ikut terbawa ke native. |
| D2 | Sedang | `generatePDF.ts` 706 LOC berbasis DOM/canvas browser — tidak bisa dipindah apa adanya. |
| D3 | Sedang | Realtime `surat_jalan` butuh filter berbeda per peran (pusat tanpa filter, outlet dengan `outlet_id`). |

## 2.4 `apps/portal`

Hanya 12 file. Logika nyatanya tiga keputusan: `purchasing` → finance; `owner`/`mitra`/`korlap` → admin-dashboard; sisanya grid app dari `ROLE_APP_ACCESS` + strip status absensi hari ini.

| # | Tingkat | Temuan |
|---|---|---|
| P1 | Sedang | `ROLE_APP_ACCESS` hanya ada di TypeScript. Salinan Kotlin sudah pernah menyimpang (`native-superapp/Roles.kt`). |
| P2 | Rendah | Pengecualian hardcode `username === 'adminkitchen'`. |
| P3 | Rendah | Versi footer `v2.8.0` hardcode. |

## 2.5 Lintas-app

| # | Tingkat | Temuan |
|---|---|---|
| X1 | **Kritis** | Tiga percobaan mobile mangkrak berdampingan, masih terdaftar di `workspaces` root. POS asli hidup di repo lain. |
| X2 | **Kritis** | Embedding wajah web (Human 1024-d, threshold 0.65) vs native (FaceNet 192-d, threshold 0.80 belum dikalibrasi) tidak kompatibel. Database yang sama tidak menyelesaikan ini — lihat §5.4. |
| X3 | Tinggi | `AuthPrefs.setLastCredentials()` menyimpan **username + password plaintext** di SharedPreferences. |
| X4 | Tinggi | Auto-update memakai satu key `global_settings.app_update` untuk semua APK. |
| X5 | Sedang | `SUPABASE_ANON_KEY` hardcode di `build.gradle.kts` → rotasi kunci = rilis APK baru. |
| X6 | Sedang | 200+ script `.js` ad-hoc di root monorepo menembak DB produksi, banyak pakai service key. |
| X7 | Sedang | Cookie sesi `.sukashawarma.com` non-httpOnly, umur 1 tahun. Native tidak terdampak (pakai refresh token), tapi web masih. |

---

# BAGIAN 3 — ARSITEKTUR SUPERAPP

## 3.1 Bentuk aplikasi

```
┌──────────────────────────────────────────────┐
│  com.sukashawarma.superapp   (SATU APK)      │
│                                              │
│  Splash → Login (SSO GoTrue)                 │
│              ↓                               │
│  ┌────────── HOME / LAUNCHER ──────────┐     │
│  │  Profil + salam + status absen       │     │
│  │  ┌────────┐ ┌────────┐ ┌────────┐   │     │
│  │  │Absensi │ │  Stok  │ │Distrib.│   │     │  ← grid dari my_apps
│  │  └────────┘ └────────┘ └────────┘   │     │     (role-driven)
│  │  ┌────────┐                          │     │
│  │  │  POS ↗ │  ← deep-link APK terpisah│     │
│  │  └────────┘                          │     │
│  └──────────────────────────────────────┘     │
│         ↓ pilih modul                         │
│  NavHost per modul, back ke Home              │
└──────────────────────────────────────────────┘
                    ↕ deep-link
┌──────────────────────────────────────────────┐
│  com.sukashawarma.pos  (sudah live, terpisah) │
└──────────────────────────────────────────────┘
```

**Kenapa POS tidak ikut masuk:** sudah live di 19 tablet dengan `applicationId`, keystore, dan auto-update yang jalan. Menggabungkannya = migrasi paksa 19 perangkat demi keuntungan kosmetik, sekaligus membuat bug modul stok bisa menjatuhkan kasir saat melayani pelanggan.

## 3.2 Struktur modul Gradle

Satu APK, tapi tetap modular di dalam — supaya `feature/*` tidak saling mengimpor dan build incremental cepat.

```
suka-superapp/
├── core/
│   ├── network        SupabaseClient, AuthApi, interceptor, Authenticator, NetworkMonitor
│   ├── auth           AuthSessionManager, SessionTokenHolder, AuthPrefs (terenkripsi), Login UI
│   ├── realtime       RealtimeClient generik (Phoenix di atas OkHttp)
│   ├── database       Room base, konverter, SyncQueue, SyncEngine generik
│   ├── update         AppUpdateManager (key per applicationId)
│   ├── printer        ESC/POS + Bluetooth (dari POS)
│   ├── camera         CameraX + ML Kit face detection (dipakai absensi & bukti foto stok)
│   ├── location       FusedLocation + haversine + anti-mock
│   ├── storage        Upload/download Supabase Storage + kompresi gambar
│   ├── ui             Tema, token brand, komponen bersama, AdaptiveScaffold
│   └── roles          Client my_apps + cache
├── feature/
│   ├── home           Launcher role-based (menggantikan apps/portal)
│   ├── absensi
│   ├── stok
│   └── distribusi
└── app/               Satu modul aplikasi: MainActivity + NavHost root + DI wiring
```

Kalau nanti ternyata perlu memecah jadi beberapa APK, struktur ini memungkinkannya **tanpa menulis ulang satu layar pun** — cukup menambah modul `app/*` yang menarik `feature/*` berbeda.

## 3.3 Keputusan teknis

| Keputusan | Pilihan | Alasan |
|---|---|---|
| Bahasa & UI | Kotlin + Compose + Material 3 | Sama dengan POS; satu-satunya stack yang terbukti di sini |
| Akses data | **Retrofit + PostgREST/RPC langsung** | POS sudah pakai ini dan bekerja; supabase-kt menambah Ktor + serialization sebagai stack kedua tanpa manfaat |
| Realtime | Phoenix WebSocket digulung tangan | Teruji, nol dependensi tambahan |
| Offline | Room + SyncQueue idempoten per modul | Pola POS terbukti |
| DI | **Hilt** | Berbeda dari POS (manual). Dengan 4 feature module dalam satu APK, wiring manual jadi tidak terkelola. Ini satu-satunya penyimpangan yang saya sarankan dari pola POS. |
| Navigasi | Navigation Compose — `NavHost` root berisi `navigation()` per feature | — |
| minSdk / targetSdk | 26 / 34 | Samakan dengan POS |
| Distribusi | WhatsApp + auto-update in-app, key `global_settings` per `applicationId` | Keputusan user final |
| Keystore | **`suka-shawarma-release.jks` yang sudah ada** | Jangan generate baru |

## 3.4 Peta modul → role → perangkat

| Modul | Role pemakai | Perangkat | Kebutuhan khusus |
|---|---|---|---|
| Home | semua | HP | — |
| Absensi | crew, leader, spv, staff_pusat, admin_hr, area_manager, RM | HP + kamera kiosk outlet | CameraX, ML Kit, model embedding, GPS presisi, anti-mock |
| Stok | crew, leader, spv, kitchen, admin, admin_finance, area_manager, purchasing | HP + TV monitoring | Room offline, kamera bukti waste, mode layar lebar |
| Distribusi | kitchen, crew, leader, spv, admin, RM | HP + printer Bluetooth | ZXing QR, tanda tangan canvas, ESC/POS |
| POS *(APK lain)* | kiosk, crew, leader | tablet kasir | — |

---

# BAGIAN 4 — INVENTARIS FITUR LENGKAP

Ini daftar yang harus ada di versi native. Setiap baris = satu layar atau satu kemampuan, dengan sumber datanya dan siapa yang boleh mengaksesnya. **Total: 63 layar + 47 aksi mutasi.**

Kolom **Prioritas**: `P0` = tanpa ini modul tidak berguna · `P1` = dipakai harian · `P2` = dipakai berkala · `P3` = nice-to-have, boleh menyusul.

---

## 4.1 MODUL HOME (menggantikan `apps/portal`)

| # | Layar / Fitur | Isi | Sumber data | Role | Prio |
|---|---|---|---|---|---|
| H-01 | **Splash + auto-login** | Cek refresh token tersimpan → langsung Home | `AuthPrefs` + GoTrue refresh | semua | P0 |
| H-02 | **Login** | Username/email + password. Username tanpa `@` → `<username>@outlet.local`. Pesan galat: kredensial salah, staff non-aktif, profil tak ditemukan | GoTrue `token?grant_type=password` | semua | P0 |
| H-03 | **Gate status staff** | Tolak login bila `status != 'active'`; tampilkan pesan spesifik untuk `inactive` / `on_leave` | `outlet_staff.status` | semua | P0 |
| H-04 | **Home / Launcher** | Avatar + salam menurut jam WIB (pagi<11, siang<15, sore<18, malam) + nama + role + nama outlet + tanggal panjang `id-ID` | `outlet_staff` + `outlets` | semua | P0 |
| H-05 | **Strip status absensi hari ini** | Chip hijau "Absen Masuk HH:mm WIB", chip kuning "Absen Pulang HH:mm", atau chip merah "Belum Absen Masuk • Ketuk untuk absen" (langsung ke modul absensi) | `attendance` hari ini WIB | semua | P0 |
| H-06 | **Grid modul role-based** | Kartu per modul dengan ikon, label, deskripsi. Warna per modul (stok hijau, absensi oranye, distribusi indigo). **Hanya 3 modul dalam cakupan** — hasil `my_apps` difilter di client | view `my_apps` (difilter client) | semua | P0 |
| H-07 | **Kartu tautan-keluar ke web** | Untuk app di luar cakupan native (`admin-dashboard`, `owner-dashboard`, `finance`, `manager`): kartu bergaya beda + ikon "buka di web", membuka Custom Tab. **Hanya tautan — jangan implementasi ulang apa pun dari app tersebut** | `my_apps` + `ROLE_APP_ACCESS` | sesuai role | P1 |
| H-08 | **Kartu POS deep-link** | Bila `com.sukashawarma.pos` terpasang → `Intent` launch. Bila belum → kartu "Pasang" + tautan APK | PackageManager | kiosk/crew/leader | P1 |
| H-09 | **Banner "perbarui data wajah"** | Muncul bila `face_descriptor IS NULL` (konsekuensi migrasi W4) | `outlet_staff` | semua | P1 |
| H-10 | **Switcher outlet** | Untuk role multi-outlet (leader/spv/RM/area_manager). Pilihan outlet aktif dipakai seluruh modul | `accessible_outlet_ids()` | multi-outlet | P1 |
| H-11 | **Profil & ganti password** | Nama, email login (read-only), role, outlet. Ubah password (min 6 karakter + konfirmasi) | GoTrue `updateUser` | semua | P1 |
| H-12 | **Logout** | Bersihkan token, Room, kembali ke Login | — | semua | P0 |
| H-13 | **Indikator offline** | Banner global saat koneksi putus + jumlah antrean tertunda | `NetworkMonitor` + `SyncQueue` | semua | P1 |
| H-14 | **Auto-update in-app** | Pill status draggable, download background, install senyap Android 12+ | `global_settings` + realtime | semua | P1 |
| H-15 | **Buku panduan** | Daftar panduan sistem, navigasi Sebelumnya/Selanjutnya | `system_guides` | semua | P2 |
| H-16 | **Changelog** | Modal "apa yang baru" setelah update | `global_settings` | semua | P3 |
| H-17 | **Home kosong (role di luar cakupan)** | Untuk `owner`, `mitra`, `developer`: pesan "Modul operasional tidak tersedia untuk role Anda" + kartu tautan-keluar ke dashboard web mereka. **Login tidak diblokir** — memblokir akan terbaca sebagai aplikasi rusak | `my_apps` | owner, mitra, developer | P1 |

---

## 4.2 MODUL ABSENSI

### 4.2.1 Absen (inti)

| # | Layar / Fitur | Isi | Role | Prio |
|---|---|---|---|---|
| AB-01 | **Panel absen pribadi** (mode 1:1) | Kamera depan terkunci ke akun yang login. Wajah orang lain ditolak walau ter-enroll | semua | P0 |
| AB-02 | **Kiosk outlet** (mode 1:N) | Kamera bersama, kenali siapa pun yang ter-enroll di outlet + role global | kiosk device | P1 |
| AB-03 | **Permintaan izin** | Handshake kamera + lokasi lewat tombol, dengan pesan galat spesifik per izin yang ditolak | semua | P0 |
| AB-04 | **Validasi lokasi** | Ambil `outlets.lat/lng` fresh → dua probe GPS (cepat + presisi) → tampilkan jarak & akurasi live | semua | P0 |
| AB-05 | **Emergency Lock** | Bila `outlets.is_active=false` → layar terkunci "Kamera absensi dinonaktifkan Pusat" | semua | P0 |
| AB-06 | **Bypass geofence HQ** | Bila `lat/lng` NULL (Kantor Pusat) → lewati validasi jarak | staff_pusat | P1 |
| AB-07 | **Anti-fake-GPS** | `Location.isFromMockProvider()` (menggantikan heuristik akurasi 1.0/0.0) + tolak `accuracy > 150 m` | semua | P0 |
| AB-08 | **Geofence** | `max(0, haversine − accuracy) <= 100 m`. Pesan galat memuat jarak, batas, dan akurasi | semua | P0 |
| AB-09 | **Deteksi + identifikasi wajah** | ML Kit detect → crop → MobileFaceNet embedding → cocokkan. Throttle ~4 FPS | semua | P0 |
| AB-10 | **Sapaan "Halo, <Nama>"** | Jeda 900 ms sebelum masuk fase liveness | semua | P1 |
| AB-11 | **Liveness challenge** | Acak menoleh kiri/kanan, 2 fase (gerak → kembali frontal). Batal bila wajah hilang, >1 wajah, atau identitas berubah >3 dtk | semua | P0 |
| AB-12 | **Penentuan aksi** | Baca `attendance` hari ini WIB → `in` / `out` / `done` ("sudah absen masuk & keluar") | semua | P0 |
| AB-13 | **Gate pulang: checklist tutup** | Semua item wajib fase `tutup` harus tercentang hari ini | semua | P0 |
| AB-14 | **Gate pulang: shift kasir** | Tidak boleh ada `shifts.status='open'` di outlet | semua | P0 |
| AB-15 | **Gate pulang: order berjalan** | Tidak boleh ada `orders` berstatus `pending`/`preparing`/`ready` (server-side) | semua | P0 |
| AB-16 | **Tombol absen manual** | Tanpa wajah, hanya untuk staff `allow_manual_button=true`. Ditandai `is_manual_button` | terbatas | P1 |
| AB-17 | **Capture + upload selfie** | Frame → JPEG → `selfies/{outlet_id}/{uuid}.jpg` | semua | P0 |
| AB-18 | **Submit + antrean offline** | UUID idempoten. Offline → antre **durable di Room** (yang lama in-memory dan hilang saat app dibunuh), flush saat online | semua | P0 |
| AB-19 | **Pesan hasil** | "Selamat bekerja!" / "Hati-hati di jalan!" + varian offline. Peta 11 alasan gagal (`not_enrolled`, `forbidden_role`, `cross_outlet`, `terlambat_alpha`, `too_early_in`, `too_early_out`, `gps_accuracy_low`, `shift_not_closed`, `unfinished_orders`, `fake_gps_detected`, `teleportation_detected`) | semua | P0 |
| AB-20 | **Haptic feedback** | Sukses/gagal | semua | P2 |

### 4.2.2 Enrollment wajah

| # | Layar / Fitur | Isi | Role | Prio |
|---|---|---|---|---|
| AB-21 | **Pilih crew belum terdaftar** | Daftar staff outlet, tandai "Sudah Terdaftar" / "Belum" | spv, leader, RM, AM, admin, admin_hr, owner, kitchen | P0 |
| AB-22 | **Consent UU PDP** | Checkbox persetujuan wajib, tercatat `consent_at` + `consent_by` | idem | P0 |
| AB-23 | **Capture 3 sudut** | Tengah → kiri → kanan, otomatis. Rata-rata descriptor | idem | P0 |
| AB-24 | **Re-enrollment** | Alasan opsional, tercatat `re_enrolled_at`/`_by`/`_reason` | idem | P1 |
| AB-25 | **Upload foto referensi** | `face-refs/{outlet_id}/{staff_id}.jpg` | idem | P1 |
| AB-26 | **Face Debug / kalibrasi** | Capture A/B → tampilkan cosine similarity. Alat kalibrasi threshold | admin/dev | P0 (alat) |

### 4.2.3 Papan, rekap, checklist

| # | Layar / Fitur | Isi | Role | Prio |
|---|---|---|---|---|
| AB-27 | **Papan Kehadiran** | Kehadiran tim hari ini real-time, persentase kehadiran, daftar staf, filter status, preview selfie, pilih outlet | SPV-tier | P1 |
| AB-28 | **Rekap & Riwayat** | Periode (harian/mingguan/bulanan/kustom), filter status, ringkasan per karyawan, detail per hari (In/Out/telat), preview selfie, **ekspor CSV** | SPV-tier | P1 |
| AB-29 | **Beranda Kru** | Panel absen pribadi + riwayat absensi sendiri | crew | P0 |
| AB-30 | **Checklist Harian (isi)** | Tab "Sebelum Buka" / "Sebelum Pulang", centang item, status terkunci bila belum absen masuk, indikator progres | semua | P0 |
| AB-31 | **Monitor Checklist** | Status "Siap Buka" / "Siap Tutup" per outlet, jumlah tugas selesai vs wajib, jam real-time WIB | SPV-tier | P1 |
| AB-32 | **Manajemen Checklist** | CRUD kategori (nama + fase buka/tutup) dan CRUD tugas (nama + wajib/tidak) | SPV-tier | P2 |

### 4.2.4 Cuti & kasbon

| # | Layar / Fitur | Isi | Role | Prio |
|---|---|---|---|---|
| AB-33 | **Saldo cuti** | Kuota tahunan (`leave_quota`, default 12) − terpakai (approved tahun ini) | semua | P2 |
| AB-34 | **Ajukan cuti** | Jenis (`annual`/`sick`/`unpaid`/`maternity`/`other`), tanggal mulai–selesai, jumlah hari, alasan, **lampiran** → `hr-attachments` | semua | P2 |
| AB-35 | **Riwayat cuti** | Daftar + status ganda (`status_spv` dan `status` HR) | semua | P2 |
| AB-36 | **Notifikasi cuti** | Badge jumlah belum dibaca di menu | SPV-tier | P2 |
| AB-37 | **Ajukan kasbon** | Nominal, jumlah bulan cicilan, alasan | semua | P2 |
| AB-38 | **Riwayat kasbon** | Daftar + `status_spv` / `status_hr` (approval) + `status` (`active`/`paid_off`, siklus pencairan) + cicilan | semua | P2 |

### 4.2.5 Pengaturan & manajemen

| # | Layar / Fitur | Isi | Role | Prio |
|---|---|---|---|---|
| AB-39 | **Jam shift global** | Jam masuk, jam pulang, toleransi menit | admin, admin_hr, owner, RM, AM | P2 |
| AB-40 | **Outlet khusus** | Tambah/edit/hapus outlet dengan jam sendiri, cari outlet, reset semua ke pusat | idem | P2 |
| AB-41 | **Mode kamera kiosk** | Otomatis (ikut jam shift) vs Manual (dinyalakan SPV) — `absen_window_mode` | idem | P2 |
| AB-42 | **Emergency Lock** | Matikan paksa kamera absensi, per outlet atau semua outlet | idem | P1 |
| AB-43 | **Manajemen Kru** | Buat akun crew (nama, username, password sementara, role) via Edge Function `create-staff` | spv, leader, admin_hr | P2 |
| AB-44 | **Kehadiran lokasi (presence)** | Siapa sedang membuka app di outlet mana | SPV-tier | P3 |

**Absensi: 44 fitur.**

---

## 4.3 MODUL STOK

### 4.3.1 Monitoring

| # | Layar / Fitur | Isi | Role | Prio |
|---|---|---|---|---|
| ST-01 | **Dashboard Crew** | Status stok outlet sendiri, peringatan kritis, opname jatuh tempo/overdue, pintasan ke Opname Baru & Riwayat Waste | crew | P0 |
| ST-02 | **Dashboard SPV — tab Overview** | Ringkasan semua outlet accessible: Aman / Menipis / Kritis, pilih outlet → detail bahan baku | spv, kitchen, admin, admin_hr, RM, admin_finance | P0 |
| ST-03 | **Dashboard SPV — tab Alerts** | Daftar item kritis lintas outlet, dengan hitungan badge | idem | P1 |
| ST-04 | **Dashboard SPV — tab Approval Permintaan** | Antrean permintaan bahan menunggu, badge jumlah | kitchen, admin, owner | P0 |
| ST-05 | **Dashboard SPV — tab Approval Waste** | Antrean waste menunggu, badge jumlah | approver | P1 |
| ST-06 | **Dashboard SPV — tab Penerimaan PO Supplier** | PO inbound menunggu verifikasi | kitchen, purchasing | P1 |
| ST-07 | **Dashboard multi-outlet scoped** | Leader / area_manager melihat SPVDashboard tapi **dibatasi `staff_outlets`** | leader, AM | P0 |
| ST-08 | **Detail outlet monitoring** | Breakdown per item + riwayat ledger, dari kartu outlet | SPV-tier | P1 |
| ST-09 | **Modal detail item** | Saldo, threshold, laju pakai, prediksi habis | SPV-tier | P1 |
| ST-10 | **Monitoring Live (mode TV)** | Papan 1920px: header stat kritis/menipis + jam, panel Kitchen, Top-3 kritis, grid 18 outlet 3 kolom, auto-refresh realtime | SPV-tier | P2 |
| ST-11 | **Estimasi produksi** | Widget perkiraan produksi dari resep aktif | SPV-tier | P2 |
| ST-12 | **Saran transfer antar-outlet** | Outlet A surplus + outlet B kritis pada item sama → saran transfer | SPV-tier | P3 |
| ST-13 | **Modal transfer stok** | Pilih outlet asal, jumlah, outlet tujuan | SPV-tier | P2 |
| ST-14 | **Verifikasi fisik barang dapur** | Modal verifikasi kitchen dengan catatan selisih | kitchen | P1 |

### 4.3.2 Ledger (kartu stok)

| # | Layar / Fitur | Isi | Role | Prio |
|---|---|---|---|---|
| ST-15 | **Daftar transaksi ledger** | Agregasi per transaksi (`ledger_transaksi_ringkas`), cari nama bahan / nomor order / opname, filter tipe | semua | P0 |
| ST-16 | **Detail transaksi** | Rincian per bahan: tipe, qty, saldo sebelum/sesudah, catatan, **sadar `saldo_is_gram`** | semua | P0 |
| ST-17 | **8 tipe transaksi** | `terima_kiriman`, `pemakaian`, `waste`, `adjustment`, `opname_selisih`, `transfer_keluar`, `transfer_masuk`, `waste_pending` | — | P0 |
| ST-18 | **Entri manual / penyesuaian** | Pilih bahan, arah penyesuaian (+/−), kuantitas, stok existing system, tipe transaksi, alasan, **foto bukti** | leader+ | P1 |
| ST-19 | **Glosarium satuan** | Modal penjelasan satuan besar/tengah/kecil per bahan | semua | P2 |
| ST-20 | **Format satuan komposit** | Tampilkan saldo dalam tiga tingkat satuan sesuai `faktor_*`, adaptif terhadap `saldo_is_gram` | — | P0 |

### 4.3.3 Opname

| # | Layar / Fitur | Isi | Role | Prio |
|---|---|---|---|---|
| ST-21 | **Form opname** | Daftar bahan baku, input tiga satuan (besar/tengah/kecil), cari bahan, tampilkan qty sistem vs fisik + selisih | crew, leader | P0 |
| ST-22 | **Simpan draft** | Simpan progres tanpa finalize; banner "📝 Draft — terakhir update <jam>" | idem | P0 |
| ST-23 | **Resume draft hari ini** | Otomatis melanjutkan draft yang ada, memulihkan nilai mentah dari `opname_item.catatan` JSON field `raw` | idem | P0 |
| ST-24 | **Finalize opname** | `finalize_opname` → tulis `opname_selisih` ke ledger | idem | P0 |
| ST-25 | **Cap 1×/hari + pengecualian** | Batas satu opname per hari, dengan pengecualian per-tanggal/per-outlet | — | P1 |
| ST-26 | **Daftar opname** | Riwayat + status (`draft`/`pending_approval`/`finalized`/`rejected`) | semua | P1 |
| ST-27 | **Detail opname** | Item, qty fisik vs sistem, selisih, flagged, catatan, pembuat, penyetuju | semua | P1 |
| ST-28 | **Ajukan approval** | `set_opname_pending` | crew, leader | P1 |
| ST-29 | **Approval opname** | Setujui / tolak + alasan, dengan hitungan pending | approver | P1 |
| ST-30 | **Compliance opname** | Outlet mana yang telat/belum opname | SPV-tier | P2 |

### 4.3.4 Permintaan bahan

| # | Layar / Fitur | Isi | Role | Prio |
|---|---|---|---|---|
| ST-31 | **Form permintaan — tab Target Menu** | Pilih menu target + jumlah → hitung kebutuhan bahan dari BOM, tampilkan estimasi omzet | crew, leader | P0 |
| ST-32 | **Form permintaan — tab Item Kritis & Manual** | Saran item kritis otomatis + tambah item manual | idem | P0 |
| ST-33 | **Keranjang permintaan** | Nama bahan, kebutuhan (BOM), stok sisa, pembulatan, qty final | idem | P0 |
| ST-34 | **Kirim permintaan** | `buat_permintaan_svc`; permintaan lama yang stale otomatis `dibatalkan` | idem | P0 |
| ST-35 | **Riwayat permintaan outlet** | Daftar + status + nama pembuat | idem | P1 |
| ST-36 | **Daftar approval pending** | Lintas outlet accessible | kitchen, spv, leader, admin, owner | P0 |
| ST-37 | **Modal approval** | Per item: qty diminta vs disetujui, **set qty 0 untuk menolak item tertentu**, crosscheck stok outlet & Gudang Pusat (sadar skala), alasan penolakan | kitchen, admin, owner | P0 |
| ST-38 | **Tolak permintaan** | `tolak_permintaan_svc` + alasan | idem | P0 |
| ST-39 | **Approval menerbitkan surat jalan** | `approve_permintaan_svc` memanggil `create_surat_jalan()` | — | P0 |
| ST-40 | **Nudge batch** | Permintaan `menunggu` >12 jam dibebaskan dari hide | — | P2 |

### 4.3.5 Mutasi antar-outlet

| # | Layar / Fitur | Isi | Role | Prio |
|---|---|---|---|---|
| ST-41 | **Form mutasi** | Pilih item + outlet tujuan + catatan, cari bahan | leader+ | P1 |
| ST-42 | **Daftar mutasi** | Riwayat + status | leader+ | P1 |
| ST-43 | **Detail mutasi** | Item, qty, asal, tujuan, jejak status | leader+ | P1 |
| ST-44 | **Alur 4 tahap** | `ajukan_mutasi` → `approve_mutasi` → `kirim_mutasi` → `terima_mutasi` | leader+ | P1 |

### 4.3.6 Waste

| # | Layar / Fitur | Isi | Role | Prio |
|---|---|---|---|---|
| ST-45 | **Lapor waste** | Bahan, qty, alasan (Basi/Expired · Gosong/Rusak Masak · Jatuh/Tumpah · Kualitas Buruk dari supplier · Lainnya), **foto bukti wajib** | crew+ | P0 |
| ST-46 | **Approval waste** | Setujui / tolak + alasan, badge jumlah | approver | P1 |
| ST-47 | **Riwayat waste** | Daftar sendiri + semua (sesuai scope) | semua | P1 |
| ST-48 | **Detail waste** | Foto bukti, alasan, pelapor, penyetuju, status | semua | P1 |
| ST-49 | **Status ganda** | `waste_pending` di ledger sampai disetujui, baru jadi `waste` | — | P0 |

### 4.3.7 PO & threshold

| # | Layar / Fitur | Isi | Role | Prio |
|---|---|---|---|---|
| ST-50 | **Penerimaan PO supplier** | Daftar PO inbound, `verifikasi_terima_po` | kitchen, purchasing | P1 |
| ST-51 | **Pengaturan threshold** | Pilih outlet → edit reorder point per bahan (`outlet_reorder_point`) | leader+ | P2 |
| ST-52 | **Tabel threshold** | Daftar bahan + nilai saat ini + edit inline | leader+ | P2 |

**Stok: 52 fitur.**

---

## 4.4 MODUL DISTRIBUSI

| # | Layar / Fitur | Isi | Role | Prio |
|---|---|---|---|---|
| DS-01 | **Dashboard pusat** | Aktivitas distribusi terkini, pintasan: Buat Surat Jalan, Cek Pengajuan Permintaan, Pantau Status Pengiriman, Serah Terima & Cetak QR | kitchen, admin, admin_hr, spv, RM, owner | P0 |
| DS-02 | **Dashboard outlet** | Pintasan: Scan QR Kedatangan, Verifikasi Kuantitas & Kondisi, Riwayat | crew, leader | P0 |
| DS-03 | **Buat surat jalan** | Pilih outlet tujuan + barang + qty, `create_surat_jalan` | pusat | P0 |
| DS-04 | **Daftar surat jalan** | Filter status, cari, realtime | semua | P0 |
| DS-05 | **Detail surat jalan** | No. surat jalan, tanggal, dikirim dari (Gudang Pusat HQ), outlet tujuan, item, tanda tangan, tindakan verifikasi | semua | P0 |
| DS-06 | **Kirim surat jalan** | `send_surat_jalan` — memicu pengurangan stok kitchen | pusat | P0 |
| DS-07 | **Alur tanda tangan pengirim** | Pilih Admin Kitchen + pilih Supir Internal atau ketik nama Lalamove/eksternal, canvas tanda tangan, `sign_surat_jalan` | pusat | P0 |
| DS-08 | **Kirim dengan tanda tangan** | `send_surat_jalan_signed` | pusat | P0 |
| DS-09 | **Cetak QR surat jalan** | Render QR + cetak thermal, layout dari `global_settings.print_layout.qr_surat_jalan` (lebar 58/80 mm, logo, judul, footer, ukuran QR) | pusat | P1 |
| DS-10 | **Scan QR kedatangan** | Kamera ZXing + **input kode manual** sebagai cadangan (mis. `A3F9D2`) | outlet | P0 |
| DS-11 | **Daftar terima** | Surat jalan menuju outlet ini yang menunggu diterima | outlet | P0 |
| DS-12 | **Form verifikasi penerimaan** | Per item: Qty Kirim vs Qty Terima, kondisi, catatan selisih. Ringkasan verifikasi. `verify_surat_jalan_item` | outlet | P0 |
| DS-13 | **Tanda tangan penerima** | Canvas, `sign_receipt_surat_jalan` | outlet | P0 |
| DS-14 | **Finalize + ledger** | `finalize_surat_jalan_and_ledger` — menulis `terima_kiriman` ke ledger | outlet | P0 |
| DS-15 | **Gate akses verifikasi** | Layar "Akses Verifikasi Terkunci" bila bukan outlet tujuan | — | P0 |
| DS-16 | **Terima bahan dari supplier** | Daftar PO, per item: dipesan vs diterima, Selisih Kurang, kondisi barang, keterangan, **foto bukti wajib** (faktur/surat jalan supplier), `verifikasi_terima_po` | kitchen | P1 |
| DS-17 | **Daftar pengiriman** | Status pengiriman berjalan | pusat | P1 |
| DS-18 | **Riwayat distribusi** | Daftar selesai + filter | semua | P1 |
| DS-19 | **Detail riwayat** | Rincian + tanda tangan tersimpan | semua | P1 |
| DS-20 | **Status printer** | Indikator koneksi printer Bluetooth + pilih perangkat | — | P1 |
| DS-21 | **Cetak surat jalan** | ESC/POS thermal (menggantikan `generatePDF.ts`) | — | P1 |
| DS-22 | **Realtime surat jalan** | Pusat: semua. Outlet: filter `outlet_id` | — | P1 |
| DS-23 | **Cache offline** | Daftar surat jalan + antrean verifikasi tertunda | — | P2 |

**Distribusi: 23 fitur.**

---

## 4.5 Ringkasan inventaris

| Modul | Jumlah fitur | P0 | P1 | P2 | P3 |
|---|---|---|---|---|---|
| Home | 17 | 7 | 7 | 2 | 1 |
| Absensi | 44 | 19 | 12 | 11 | 2 |
| Stok | 52 | 20 | 20 | 10 | 2 |
| Distribusi | 23 | 12 | 9 | 2 | 0 |
| **Total** | **136** | **58** | **48** | **25** | **5** |

**Untuk rilis pertama yang berguna, 58 fitur P0 wajib ada.** P1 menyusul di rilis berikutnya, P2/P3 boleh menunggu.

---

# BAGIAN 5 — PEKERJAAN BACKEND PRASYARAT

## 5.1 Prinsip

> **Setiap gerbang otorisasi yang hari ini hidup di TypeScript harus pindah ke database (PL/pgSQL) atau Edge Function.** Kalau tidak, versi native memanggil RPC `SECURITY DEFINER` tanpa penjaga dan seluruh sistem approval terbuka.

Ini bukan biaya tambahan untuk native saja — ia sekaligus menutup S2 yang **sudah menganga hari ini** (`'use server'` = endpoint POST publik bagi siapa pun yang punya sesi).

> ⚠️ **Batasan dari keputusan #3 (web dipertahankan):** semua perubahan **wajib aditif**. Server Action web di-*repoint* ke RPC baru yang sama, bukan dibiarkan jalan sendiri — kalau tidak, dua implementasi menyimpang dalam hitungan bulan.

## 5.2 Peta migrasi endpoint

### Kelompok 1 — Sudah aman, native tinggal panggil (distribusi)
`create_surat_jalan`, `send_surat_jalan`, `send_surat_jalan_signed`, `sign_surat_jalan`, `sign_receipt_surat_jalan`, `verify_surat_jalan_item`, `finalize_surat_jalan`, `finalize_surat_jalan_and_ledger`, `verifikasi_terima_po`, `get_purchase_orders`, `accessible_outlet_ids`.
→ **Audit guard `auth.uid()` di dalam masing-masing, lalu pakai apa adanya.**

### Kelompok 2 — RPC ada, gerbang di TypeScript — **pindahkan ke SQL**

| RPC | Gerbang TS hari ini | Tambahkan di SQL |
|---|---|---|
| `buat_permintaan_svc` | `assertOutletAccessible` | `p_outlet_id IN (SELECT accessible_outlet_ids())` + `p_dibuat_oleh = auth.uid()` |
| `approve_permintaan_svc` | `requirePermintaanApprover` | cek role pemanggil di dalam fungsi |
| `tolak_permintaan_svc` | idem | idem |
| `set_opname_pending` | **nol guard** | `outlet_staff.outlet_id = opname.outlet_id` (menutup S4) |
| `approve_opname` / `reject_opname` | `actions/opname.ts` | cek role approver + `accessible_outlet_ids()` |
| `finalize_opname` | ada guard, tapi **di-skip** saat dipanggil service-role (`auth.uid()` NULL) | jangan pernah panggil lewat service-role dari jalur approval |
| `ajukan/approve/kirim/terima_mutasi` | `actions/mutasi.ts` | pindahkan ke fungsi |
| `calculate_bahan_baku_request` | read-only | cukup scope outlet |

### Kelompok 3 — Hanya Server Action — **buat RPC baru**
`upsertOpnameItems`, `updateThresholdAction`, `submitWasteReport`, `approveWasteReport`, `rejectWasteReport`. Semua `fetch*`/`count*` **jadi view ber-RLS**, bukan RPC — lebih murah dan otomatis ikut scope.

### Kelompok 4 — API route absensi — **pindah ke Edge Function**

| Route hari ini | Jadi | Perubahan wajib |
|---|---|---|
| `/api/submit-attendance` | Edge Function `submit-attendance` (direktori sudah ada) | **Verifikasi JWT pemanggil** — menutup A1 |
| `/api/face-match` | Edge Function `face-match` | Verifikasi JWT + **jangan kembalikan descriptor mentah** — menutup A2 |
| `/api/enroll` | Edge Function `enroll-face` | Sudah verifikasi JWT, tinggal pindah |
| `/api/checklist/*`, `/api/attendance/{rekap,papan}`, `/api/outlet-config`, `/api/staff-outlets`, `/api/outlet-presence` | **view ber-RLS** | Semua read-only; 8 route hilang sekaligus |

## 5.3 Matriks role→app butuh sumber tunggal lintas-bahasa

Masalah P1/X1: `ROLE_APP_ACCESS` hanya di TypeScript, dan salinan Kotlin sudah pernah menyimpang.

**Solusi:** tabel `role_app_access(role, app)` + view `my_apps` yang mengembalikan app untuk `auth.uid()`.
- Web: `packages/auth` baca dari DB, salinan TS jadi fallback offline.
- Native: `GET /rest/v1/my_apps` — nol duplikasi.
- Bonus: tambah role/app baru tidak lagi butuh rilis APK.

## 5.4 Model wajah — kenapa "database sama" tidak menyelesaikannya

`outlet_staff.face_descriptor` hanyalah array angka. Database tidak tahu angka itu dari model mana. **Embedding wajah hanya bermakna relatif terhadap model pembuatnya** — dua model berbeda menempatkan wajah yang sama di dua ruang vektor yang tidak berhubungan.

Analoginya: kolom `harga` diisi Rupiah oleh app A dan Dollar oleh app B. Kolomnya satu, databasenya satu, tapi membandingkan `50000` dengan `3.2` menghasilkan omong kosong. Bedanya di sini **tidak ada kurs konversi** — tidak ada fungsi matematis yang mengubah vektor Human 1024-d jadi vektor FaceNet 192-d. Informasinya memang berbeda, bukan sekadar berbeda skala.

Syarat satu kolom bisa dipakai bersama cuma satu: **kedua sisi menjalankan model identik — bobot sama, preprocessing sama.**

### Kenapa W4 (web ikut native), bukan W1 (native ikut Human)

| | W1: native ikut Human | **W4: web ikut native** |
|---|---|---|
| Model | `faceres`, spesifik Human | MobileFaceNet / EdgeFace — standar industri |
| Arah konversi | TFJS → SavedModel → TFLite | TFLite → ONNX → `onnxruntime-web` |
| Preprocessing | Langkah `enhance`/equalization Human harus direplikasi persis di Kotlin | Standar & terdokumentasi: crop 112×112, normalisasi `[-1,1]` |
| Referensi pembanding | Tidak ada | Banyak implementasi rujukan di kedua platform |
| Verifikasi | Sulit, tidak ada ground truth | Mudah: gambar sama → cosine web vs native harus ≈1.0 |

Web tetap memakai Human untuk **deteksi wajah, mesh, dan gesture liveness** — bagian itu bekerja bagus. Yang diganti hanya langkah terakhir: ekstraksi embedding.

### Enroll ulang sekali, tanpa downtime

66 vektor Human yang ada **tidak bisa dikonversi**. Jalur mana pun menuntut enroll ulang satu kali. Caranya supaya absensi tidak berhenti sedetik pun:

1. Migration: rename kolom lama → `face_descriptor_human`. Web lama tetap membacanya. **Nol gangguan.**
2. Kolom `face_descriptor` dikosongkan, jadi milik model baru.
3. Web baru **dan** APK sama-sama menulis ke `face_descriptor`. Saat mencocokkan: **coba kolom baru dulu; kalau NULL, jatuh ke `face_descriptor_human` dengan model lama.**
4. Crew enroll ulang bertahap — banner H-09, dikerjakan saat shift berikutnya bersama leader. ±30 detik per orang.
5. Setelah `COUNT(*) WHERE face_descriptor IS NULL` = 0, buang kolom lama, jalur fallback, dan dependensi `faceres`.

### Prasyarat mutlak

Threshold `0.65` adalah kalibrasi lapangan **untuk Human** (orang sama ≈0.86, orang beda ≈0.53). Tidak berlaku untuk MobileFaceNet, dan `0.80` di `native-superapp` adalah tebakan yang tidak pernah diverifikasi. Kalibrasi ulang dengan **≥30 pasang same-person dan ≥30 pasang different-person** wajib selesai sebelum enrollment lapangan. Threshold salah = crew tidak bisa absen, atau orang lain bisa absen mewakilinya.

---

# BAGIAN 6 — RENCANA EKSEKUSI

## Fase 0 — Bersih-bersih, keputusan, spike (2–3 hari)

| # | Task | DoD |
|---|---|---|
| 0.1 | Tulis ADR untuk 4 keputusan yang sudah diambil | ADR-0016…0019 ada di repo |
| 0.2 | **Backup `suka-shawarma-release.jks` + password** ke luar repo | Terbukti bisa dipulihkan. Dokumen rilis masih menulis "belum dilakukan" |
| 0.3 | **Spike paritas model**: MobileFaceNet → ONNX, jalankan di browser & TFLite Android atas gambar sama | Cosine ≥ 0,99. **Gagal ⇒ keputusan W4 ditinjau ulang sebelum ada kode lain ditulis** |
| 0.4 | Arsipkan `mobile/superapp`, `mobile/native-superapp`, `mobile/native-pos` ke tag git lalu hapus dari `workspaces` | `yarn install` root tidak menyentuh ketiganya |
| 0.5 | Panen dari `native-superapp` sebelum dihapus: `FaceRecognizer.kt`, `LocationHelper.kt`, `CameraPreview.kt`, `libs.versions.toml` | Tersimpan di repo baru |
| 0.6 | Clone `rendydev404/SUPER-APPS-SS-MOBILE`, buat skeleton (`core/` + `feature/` + `app/`) + Hilt + **`.gitignore` benar sejak commit pertama** (`*.jks`, `*.keystore`, `local.properties`, `google-services.json`) | `./gradlew assembleDebug` hijau; `git status` bersih dari file rahasia |
| 0.7 | Namespace key auto-update: `app_update` → `app_update:<applicationId>`, POS baca key baru dengan fallback key lama satu rilis | Menutup X4 sebelum APK kedua ada |

## Fase 1 — Ekstraksi `core/` dari POS (4–6 hari)

Pindahkan, jangan tulis ulang.

| # | Task | Sumber di POS |
|---|---|---|
| 1.1 | `core/network` | `SupabaseClient.kt`, `AuthApi.kt`, `SessionTokenHolder.kt`, `NetworkMonitor.kt` |
| 1.2 | `core/auth` — **plus** `EncryptedSharedPreferences` dan **buang `setLastCredentials`** (menutup X3). ⚠️ Baru boleh dibuang setelah task 2.16 (`mint-web-session`) siap — lihat §3.6 | `AuthSessionManager.kt`, `AuthPrefs.kt`, `presentation/login/*` |
| 1.2b | `core/auth` — `WebHandoffLauncher(appUrl, path)`, generalisasi `StokOutletLauncher` | SSO-2 |
| 1.3 | `core/realtime` — generalisasi jadi `RealtimeChannel(schema, table, filter, onChange)`; pertahankan `UpdateRealtimeProtocol` yang testable | `data/remote/realtime/*` |
| 1.4 | `core/database` — `SyncQueueEntity`/`Dao` generik + `SyncEngine<T>` abstrak (409/duplicate = sukses) | `SyncQueueDao.kt`, `OrderSyncEngine.kt` |
| 1.5 | `core/update` — key per `applicationId` | `data/update/*` |
| 1.6 | `core/printer` | `data/bluetooth/*`, `domain/printer/*` |
| 1.7 | `core/camera` — CameraX + ML Kit face detection | `native-superapp/CameraPreview.kt` |
| 1.8 | `core/location` — FusedLocation, haversine, `isFromMockProvider()` | `native-superapp/LocationHelper.kt` |
| 1.9 | `core/storage` — upload/download Supabase Storage + kompresi | baru |
| 1.10 | `core/ui` — tema, warna brand (`#f29744`/`#701604`/`#400a07`/`#fff7ed`/`#0a7d2c`), Lilita One + Plus Jakarta Sans, `AdaptiveScaffold`, `OfflineIndicator`, `EmptyState` | `presentation/theme/*`, `presentation/components/*` |
| 1.11 | `core/roles` — client `my_apps` + cache | baru |
| 1.12 | Unit test ikut naik | `./gradlew test` hijau |

**DoD:** `core/*` mengompilasi berdiri sendiri; POS lama tetap bisa di-build tanpa perubahan perilaku (verifikasi `assembleRelease` + smoke test 1 tablet).

## Fase 2 — Backend hardening (paralel dengan Fase 1, 6–9 hari)

Di repo monorepo web.

| # | Task | Menutup |
|---|---|---|
| 2.1 | Tabel `role_app_access` + view `my_apps` + repoint `packages/auth` | P1, X1 |
| 2.2 | Rename `face_descriptor` → `face_descriptor_human`; kolom baru + jalur dual-read | — |
| 2.3 | Guard `auth.uid()`+role ke 12 RPC Kelompok 2. **Wajib `grep -rn "<nama_fungsi>" supabase/migrations/` dulu** | S2, S4, S6 |
| 2.4 | RPC baru Kelompok 3 (opname item, threshold, waste ×3) + repoint Server Action web ke RPC yang sama | S1 |
| 2.5 | View ber-RLS pengganti 8 route read-only absensi | — |
| 2.6 | Edge Function `submit-attendance` — verifikasi JWT | A1 |
| 2.7 | Edge Function `face-match` — verifikasi JWT + berhenti mengembalikan descriptor + dual-read | A2 |
| 2.8 | Edge Function `enroll-face` | — |
| 2.9 | Web absensi: ekstraksi embedding → `onnxruntime-web` + MobileFaceNet, model di `public/models/` | W4, A7 |
| 2.10 | Test paritas embedding web vs native (cosine ≥ 0,99) | **gerbang Fase 5** |
| 2.11 | Matikan `matchMode` client legacy + `setMatchMode` | A3 |
| 2.12 | Perbaiki `sj_on_dikirim_kurangi_kitchen` agar scale-aware | D1 |
| 2.13 | Filter `type != 'marketplace'` di query outlet stok/absensi/distribusi | S5 |
| 2.14 | Buang fallback hardcode anon-key di `makeServiceClient()` — gagal keras | S3 |
| 2.15 | Tambah `korlap` ke union `Role` + `MonitoringPage` | S7 |
| 2.16 | **Edge Function `mint-web-session`** — verifikasi JWT pemanggil → `admin.generateLink` + `/auth/v1/verify` → kembalikan sesi baru yang independen | Prasyarat X3; tanpa ini SSO handoff tetap butuh password plaintext (§3.6) |
| 2.17 | Salin rute `/auth/sso` ke `admin-dashboard`, `owner-dashboard`, `finance`, `manager`; kecualikan dari matcher middleware | SSO-1 — prasyarat H-07 & H-17 |

**DoD:** setiap RPC pengubah data **menolak pemanggil tanpa hak ketika dipanggil langsung lewat PostgREST dengan JWT crew** — uji ini eksplisit, bukan lewat UI.

## Fase 3 — Shell + Home + Login (3–4 hari)

Sekarang bisa lebih awal daripada rencana sebelumnya, karena tidak ada SSO antar-proses yang harus menunggu modul kedua.

| # | Task | Fitur |
|---|---|---|
| 3.1 | `MainActivity` + NavHost root + Hilt wiring | — |
| 3.2 | Splash + auto-login | H-01 |
| 3.3 | Login + gate status staff | H-02, H-03 |
| 3.4 | Home: profil, salam WIB, tanggal, strip absensi | H-04, H-05 |
| 3.5 | Grid modul dari `my_apps` **difilter ke 3 modul dalam cakupan** + kartu tautan-keluar (Custom Tab) + Home kosong untuk owner/mitra/developer | H-06, H-07, H-17 |
| 3.6 | Deep-link POS + kartu "Pasang" | H-08 |
| 3.7 | Switcher outlet multi-outlet | H-10 |
| 3.8 | Profil & ganti password, logout | H-11, H-12 |
| 3.9 | Indikator offline global | H-13 |
| 3.10 | Auto-update in-app | H-14 |

**DoD:** login sekali, Home tampil dengan modul yang tepat sesuai role, ganti password jalan, logout bersih.

## Fase 4 — Modul Distribusi (5–7 hari)

Modul pertama karena nol Server Action — ia menguji seluruh `core/` tanpa terhalang Fase 2.

| # | Task | Fitur |
|---|---|---|
| 4.1 | Model + Retrofit API: 10 tabel + 10 RPC | — |
| 4.2 | Dashboard pusat & outlet | DS-01, DS-02 |
| 4.3 | Buat / daftar / detail surat jalan | DS-03…DS-05 |
| 4.4 | Kirim + alur tanda tangan pengirim (canvas Compose `pointerInput` → PNG → Storage) | DS-06…DS-08 |
| 4.5 | Scan QR ZXing + input kode manual | DS-10 |
| 4.6 | Daftar terima + form verifikasi per item + tanda tangan penerima + finalize | DS-11…DS-15 |
| 4.7 | Terima bahan supplier + foto bukti | DS-16 |
| 4.8 | Pengiriman, riwayat, detail riwayat | DS-17…DS-19 |
| 4.9 | Printer status + cetak ESC/POS (QR + surat jalan), baca `global_settings.print_layout` | DS-09, DS-20, DS-21 |
| 4.10 | Realtime + cache offline | DS-22, DS-23 |

**DoD:** satu surat jalan dibuat di web, dikirim, diterima+diverifikasi+ditandatangani+dicetak dari APK, dan `ledger_stok` kedua outlet benar — **termasuk untuk baris gram-scale** (inilah yang menguji 2.12).

## Fase 5 — Modul Absensi (9–13 hari, paling berisiko)

**Gerbang masuk:** task 2.10 (paritas embedding) hijau.

| # | Task | Fitur |
|---|---|---|
| 5.1 | CameraX preview + analisis frame + ML Kit detection | AB-09 |
| 5.2 | Embedding MobileFaceNet TFLite (**model identik dengan web setelah 2.9**) | AB-09 |
| 5.3 | **Kalibrasi threshold di HP nyata** — layar Face Debug, ≥30 pasang same + ≥30 different. **Gerbang wajib** | AB-26 |
| 5.4 | Verifikasi paritas ulang di perangkat nyata (cosine ≥ 0,99) | — |
| 5.5 | Liveness native — port detektor 2 fase, sumber gesture = `headEulerAngleY` + `eyeOpenProbability` ML Kit | AB-11 |
| 5.6 | GPS: dua probe, `isFromMockProvider()`, geofence, gate akurasi | AB-04, AB-07, AB-08 |
| 5.7 | Emergency Lock + bypass HQ | AB-05, AB-06 |
| 5.8 | Permintaan izin + panel absen pribadi + kiosk 1:N + sapaan | AB-01…AB-03, AB-10 |
| 5.9 | Penentuan aksi + 3 gate absen pulang (dari view, bukan 3 query terpisah) | AB-12…AB-15 |
| 5.10 | Tombol absen manual | AB-16 |
| 5.11 | Capture + upload selfie | AB-17 |
| 5.12 | Submit + **antrean offline durable di Room** + peta 11 pesan galat + haptic | AB-18…AB-20 |
| 5.13 | Enrollment: pilih crew, consent PDP, 3 sudut, re-enroll, foto referensi | AB-21…AB-25 |
| 5.14 | Papan Kehadiran + Rekap (periode, filter, preview selfie, ekspor CSV) | AB-27, AB-28 |
| 5.15 | Beranda Kru | AB-29 |
| 5.16 | Checklist: isi harian, monitor, manajemen kategori & tugas | AB-30…AB-32 |
| 5.17 | Cuti: saldo, ajukan + lampiran, riwayat, notifikasi | AB-33…AB-36 |
| 5.18 | Kasbon: ajukan, riwayat | AB-37, AB-38 |
| 5.19 | Pengaturan: jam shift, outlet khusus, mode kiosk, Emergency Lock | AB-39…AB-42 |
| 5.20 | Manajemen kru + presence | AB-43, AB-44 |
| 5.21 | Mode kiosk: screen pinning + reset otomatis | AB-02 |

**DoD:** crew enroll di HP, absen masuk & pulang, baris `attendance` tercatat dengan status/telat/GPS yang benar, dan kolom wajah lama tidak tersentuh.

## Fase 6 — Modul Stok (11–15 hari, paling luas)

| # | Task | Fitur |
|---|---|---|
| 6.1 | **Port `compositeUnit.ts` (436 LOC) ke Kotlin beserta test-nya** — matematika yang salah sedikit merusak saldo produksi | ST-20 |
| 6.2 | Dashboard Crew + Dashboard SPV 5 tab + scoped multi-outlet | ST-01…ST-07 |
| 6.3 | Detail outlet + modal detail item | ST-08, ST-09 |
| 6.4 | Ledger: daftar transaksi, detail, 8 tipe, sadar `saldo_is_gram` | ST-15…ST-17 |
| 6.5 | Entri manual + foto bukti + glosarium satuan | ST-18, ST-19 |
| 6.6 | Opname: form 3 satuan, simpan draft, resume, finalize, cap 1×/hari | ST-21…ST-25 |
| 6.7 | Opname: daftar, detail, ajukan approval, approval, compliance | ST-26…ST-30 |
| 6.8 | Permintaan: form target menu + item kritis, keranjang, kirim | ST-31…ST-35 |
| 6.9 | Permintaan: daftar approval, modal approval + crosscheck, tolak | ST-36…ST-40 |
| 6.10 | Mutasi: form, daftar, detail, alur 4 tahap | ST-41…ST-44 |
| 6.11 | Waste: lapor + foto, approval, riwayat, detail, status ganda | ST-45…ST-49 |
| 6.12 | Penerimaan PO + threshold | ST-50…ST-52 |
| 6.13 | Monitoring Live mode layar lebar + estimasi produksi + transfer | ST-10…ST-14 |
| 6.14 | Offline: cache `bahan_baku` + `stok_balance`; antrean opname & waste | — |

**DoD:** opname lengkap satu outlet dari HP, finalized, `ledger_stok` `opname_selisih` benar untuk baris gram-scale **maupun** besar-scale.

## Fase 7 — Konsolidasi (3–5 hari)

| # | Task |
|---|---|
| 7.1 | Tarik repo POS masuk sebagai `app/pos`, repoint ke `core/*` — **`applicationId` dan keystore tidak berubah** |
| 7.2 | Bandingkan APK sebelum/sesudah; rilis POS berikutnya harus tetap terpasang otomatis di 19 tablet |
| 7.3 | Web tetap hidup sebagai jalur desktop; pastikan setiap fitur native berbagi RPC/Edge Function **yang sama** dengan web. Tulis daftar kontrak bersama sebagai kontrak beku |
| 7.4 | Buang `face_descriptor_human` + jalur fallback + dependensi `faceres` setelah semua staff enroll ulang |
| 7.5 | Pipeline rilis: build + sign + upload ke bucket `app-releases` + upsert `global_settings` per `applicationId` |
| 7.6 | Perbarui `CLAUDE.md` + ADR |

## 6.1 Ringkasan waktu

| Fase | Isi | Perkiraan |
|---|---|---|
| 0 | Bersih-bersih, keputusan, spike model | 2–3 hari |
| 1 | Ekstraksi `core/` | 4–6 hari |
| 2 | Backend hardening *(paralel dgn 1)* | 6–9 hari |
| 3 | Shell + Home + Login | 3–4 hari |
| 4 | Modul Distribusi | 5–7 hari |
| 5 | Modul Absensi | 9–13 hari |
| 6 | Modul Stok | 11–15 hari |
| 7 | Konsolidasi | 3–5 hari |
| | **Total** | **±37–53 hari kerja** (Fase 2 paralel) |

Menyempit dari revisi 4-APK karena SSO antar-proses hilang (−3 hari), tapi melebar karena inventaris fitur sekarang lengkap dan realistis: 136 fitur, bukan perkiraan kasar.

## 6.2 Urutan ini dipilih karena

1. **Shell + Home dulu** — tanpa launcher, tidak ada tempat memasang modul. Dan ia menguji `core/auth` di jalur yang paling sederhana.
2. **Distribusi kedua** — nol Server Action, jadi memvalidasi seluruh `core/` tanpa menunggu Fase 2 tuntas. Kalau `core/` salah, ketahuan di modul termurah.
3. **Absensi ketiga** — paling berisiko (model wajah, kalibrasi, biometrik), butuh fondasi yang sudah terbukti, bukan fondasi yang masih berubah.
4. **Stok terakhir** — paling luas (52 fitur) dan paling bergantung pada Fase 2 selesai tuntas.

---

# BAGIAN 7 — RISIKO & KEPUTUSAN TERBUKA

## 7.1 Risiko teratas

| Risiko | Dampak | Mitigasi |
|---|---|---|
| Threshold wajah salah kalibrasi | Crew tidak bisa absen, **atau** orang lain bisa absen mewakilinya | Task 5.3 adalah gerbang wajib; ≥30 pasang sampel tiap kategori |
| Spike paritas W4 gagal | Keputusan model harus ditinjau ulang | Task 0.3 gerbang; kalau gagal, jatuh ke dua kolom dengan konsekuensi yang sudah dipetakan |
| Guard RPC bocor saat dipindah ke SQL | Crew bisa approve permintaannya sendiri | Uji tiap RPC dengan JWT crew lewat PostgREST langsung, bukan lewat UI |
| Bug satuan gram/besar terbawa ke native | Saldo stok korup, seperti 141 baris yang sudah terjadi | Port `compositeUnit` beserta test-nya; selesaikan 2.12 sebelum Fase 4 |
| Keystore hilang | 19 tablet POS harus uninstall manual; superapp tidak bisa di-update | **Task 0.2, hari pertama** |
| Auto-update saling silang antar-APK | Tablet kasir menginstal APK superapp | **Task 0.7, sebelum APK kedua dirilis** |
| Ranjau migration 2030 menimpa fix Fase 2 | Perbaikan hilang senyap tanpa error | `grep` wajib sebelum menyentuh fungsi DB; migration baru bernomor > `20300108…` |
| Web dan native menyimpang | Dua sumber kebenaran | Aturan: **logika bisnis di DB, bukan di app.** Fitur yang pindah ke native harus lebih dulu memindahkan logikanya ke RPC/Edge Function yang **juga** dipakai web |
| Scope 136 fitur membengkak | Rilis tak pernah terjadi | Rilis per modul, dan **hanya P0 di rilis pertama tiap modul** (58 fitur total) |

## 7.2 Keputusan terbuka

| # | Pertanyaan | Rekomendasi |
|---|---|---|
| 1 | **Repo** — `suka-superapp` baru, atau perluas repo POS jadi multi-modul? | Perluas repo POS — keystore, `local.properties`, dan pipeline rilis sudah jalan di sana. Repo baru berarti ketiganya dibangun ulang. |
| 2 | **Mode TV monitoring stok** (ST-10) — cukup mode layar lebar di superapp, atau perangkatnya Android TV betulan? | Perlu konfirmasi: TV di outlet itu Android TV box, atau layar biasa yang dicolok tablet/stick? Kalau Android TV, itu butuh navigasi D-pad + Leanback launcher = pekerjaan tersendiri. |
| 3 | **Kiosk absensi outlet** (AB-02) — perangkatnya apa? | Kalau tablet khusus, superapp bisa masuk mode kiosk dengan screen pinning. Kalau masih browser di perangkat lama, modul absensi web harus tetap hidup lebih lama. |
| 4 | **Cakupan rilis pertama** | Saran: Distribusi P0 saja (12 fitur) sebagai rilis pertama ke 2–3 kurir. Umpan balik lapangan sebelum menulis 100+ fitur berikutnya. |
| 5 | ~~Role tanpa modul~~ | ✅ **Diputuskan:** Home kosong + tombol "Buka di Web" yang membawa sesi, tanpa login ulang. Login tidak diblokir. Mekanisme di §3.6. |
| 6 | ~~Repo~~ | ✅ **Diputuskan:** repo baru `rendydev404/SUPER-APPS-SS-MOBILE`. |
| 7 | **Repo public** — repo baru ini publik. Konsekuensi: `.gitignore` harus benar sejak commit pertama (`*.jks`, `*.keystore`, `local.properties`, `google-services.json`). Anon key boleh (memang publik), service key & keystore **tidak pernah**. | Kalau tidak ada alasan kuat untuk publik, ubah jadi privat — repo ini tidak punya nilai open-source dan satu kelalaian commit sudah cukup. **Perlu konfirmasi.** |

---

# LAMPIRAN A — Temuan review, terurut prioritas

| ID | Tingkat | Ringkasan | Lokasi |
|---|---|---|---|
| A1 | Kritis | `/api/submit-attendance` tidak memverifikasi pemanggil sama sekali | `apps/absensi/src/app/api/submit-attendance/route.ts` |
| A2 | Kritis | `/api/face-match` tanpa auth **dan** mengembalikan descriptor biometrik mentah | `apps/absensi/src/app/api/face-match/route.ts` |
| S1 | Kritis | Semua mutasi stok lewat Server Action — tidak bisa dipanggil native | `apps/stok/src/app/actions/*.ts` |
| S2 | Kritis | RPC `*_svc` SECURITY DEFINER tanpa cek role; gerbang hanya di TypeScript | migration + `actions/permintaan.ts` |
| X1 | Kritis | Tiga percobaan mobile mangkrak berdampingan; POS di repo lain | `mobile/*` |
| X2 | Kritis | Embedding wajah web vs native tidak kompatibel | `lib/face/*` vs `native-superapp/FaceRecognizer.kt` |
| X3 | Tinggi | Username + password plaintext di SharedPreferences | `data/local/AuthPrefs.kt` (POS) |
| X4 | Tinggi | Key auto-update tunggal untuk semua APK | `global_settings.app_update` |
| A3 | Tinggi | Mode face-match client legacy masih bisa diaktifkan | `useClockKiosk.ts` |
| A4 | Tinggi | Deteksi mock GPS berbasis heuristik akurasi | `lib/gps.ts` |
| S3 | Tinggi | `makeServiceClient()` fallback diam-diam ke anon key | `actions/permintaan.ts` |
| S4 | Tinggi | `kitchen` bisa opname outlet lain | RLS `opname_*` |
| D1 | Tinggi | `sj_on_dikirim_kurangi_kitchen` tidak scale-aware | trigger surat jalan |
| A5 | Sedang | Dependensi `useCallback` `checkLocation` memuat state yang di-set sendiri | `useClockKiosk.ts` |
| A6 | Sedang | `scheduleReset` `setTimeout` tanpa cleanup | `useClockKiosk.ts` |
| A7 | Sedang | Model Human ~20 MB dari CDN jsdelivr per perangkat | `lib/face/recognizer.ts` |
| S5 | Sedang | Outlet virtual marketplace bocor ke dropdown stok/absensi/distribusi | `useOutlets` per app |
| S6 | Sedang | Ranjau 8 migration bertimestamp 2030 | `supabase/migrations/2030*` |
| S7 | Sedang | `korlap` tidak ada di union `Role` → jatuh ke CrewDashboard | `packages/auth/types.ts`, `MonitoringPage.tsx` |
| D2 | Sedang | `generatePDF.ts` 706 LOC berbasis DOM | `distribusi/src/utils/generatePDF.ts` |
| D3 | Sedang | Realtime surat jalan butuh filter berbeda per peran | `useSuratJalanList.ts` |
| X6 | Sedang | 200+ script ad-hoc menembak DB produksi | root monorepo |
| X7 | Sedang | Cookie sesi non-httpOnly, umur 1 tahun, domain wildcard | `packages/auth/supabase-client.ts` |
| P1 | Sedang | Matriks role→app hanya di TypeScript | `packages/auth/access.ts` |
| A8 | Rendah | Komentar `decideAction` tidak sesuai kodenya | `useClockKiosk.ts` |
| P2 | Rendah | Pengecualian hardcode `username === 'adminkitchen'` | `packages/auth/access.ts` |
| P3 | Rendah | Versi footer portal hardcode | `portal/launcher/page.tsx` |
| X5 | Rendah | Anon key hardcode di `build.gradle.kts` | POS |

---

# LAMPIRAN B — Kode POS yang dipakai ulang

| Berkas POS | LOC | Tujuan |
|---|---|---|
| `data/remote/SupabaseClient.kt` | ~95 | `core/network` |
| `data/remote/AuthApi.kt` | ~50 | `core/network` |
| `data/remote/AuthSessionManager.kt` | ~70 | `core/auth` |
| `data/local/AuthPrefs.kt` | ~40 | `core/auth` *(terenkripsi, buang password)* |
| `data/remote/NetworkMonitor.kt` | — | `core/network` |
| `data/remote/realtime/OrderRealtimeManager.kt` | 222 | `core/realtime` *(digeneralisasi)* |
| `data/update/UpdateRealtimeManager.kt` | 161 | `core/realtime` |
| `data/update/UpdateRealtimeProtocol.kt` | 99 | `core/realtime` *(sudah testable)* |
| `data/update/AppUpdateManager.kt` | 605 | `core/update` *(di-namespace)* |
| `data/update/ApkDeltaApplier.kt` | 81 | `core/update` |
| `data/local/dao/SyncQueueDao.kt` | 50 | `core/database` |
| `data/sync/OrderSyncEngine.kt` | 136 | `core/database` *(digeneralisasi)* |
| `data/bluetooth/*` + `domain/printer/*` | ~400 | `core/printer` |
| `presentation/theme/*` | ~150 | `core/ui` |
| `POSAdaptiveScaffold`, `SideNavRail`, `OfflineIndicator`, `EmptyState`, `DraggableUpdateOverlay`, `AppUpdateIndicator` | ~600 | `core/ui` |
| `data/notification/*` | ~250 | `core/push` |
| **Total** | **≈3.000 LOC** | dari 23.105 LOC POS |

---

# LAMPIRAN C — Peta tabel, RPC, Edge Function

## Tabel per modul

**Absensi (14):** `attendance`, `outlet_staff`, `outlets`, `staff_outlets`, `outlet_attendance_config`, `global_settings`, `checklist_categories`, `checklist_items`, `daily_checklist_records`, `daily_checklist_ticks`, `leave_requests`, `cash_advances`, `shifts`, `orders` *(read-only untuk gate)*, `system_guides`
**Storage:** `selfies`, `face-refs`, `hr-attachments`

**Stok (26):** `bahan_baku`, `stok_balance`, `ledger_stok`, `ledger_transaksi_ringkas` *(view)*, `ledger_feed_spv` *(view)*, `opname`, `opname_item`, `opname_compliance_view`, `permintaan_bahan`, `permintaan_bahan_item`, `mutasi_antar_outlet`, `stok_waste_reports`, `waste_evidence`, `outlet_reorder_point`, `resep`, `resep_item`, `menu_items`, `monitoring_view_spv`, `monitoring_view_crew`, `stockout_forecast_spv`, `purchase_order`, `purchase_order_item`, `surat_jalan`, `outlets`, `outlet_staff`, `staff_outlets`, `orders`, `order_items`, `bahan_baku_substitusi`

**Distribusi (10):** `surat_jalan`, `surat_jalan_item`, `distribusi`, `bahan_baku`, `purchase_order`, `purchase_order_item`, `outlets`, `outlet_staff`, `staff_outlets`, `global_settings`

## RPC

**Sudah aman (distribusi, 10):** `create_surat_jalan`, `send_surat_jalan`, `send_surat_jalan_signed`, `sign_surat_jalan`, `sign_receipt_surat_jalan`, `verify_surat_jalan_item`, `finalize_surat_jalan`, `finalize_surat_jalan_and_ledger`, `verifikasi_terima_po`, `get_purchase_orders`

**Butuh guard (stok, 12):** `buat_permintaan_svc`, `approve_permintaan_svc`, `tolak_permintaan_svc`, `set_opname_pending`, `approve_opname`, `reject_opname`, `finalize_opname`, `ajukan_mutasi`, `approve_mutasi`, `kirim_mutasi`, `terima_mutasi`, `calculate_bahan_baku_request`

**Baru (5):** `upsert_opname_items`, `update_threshold`, `submit_waste_report`, `approve_waste_report`, `reject_waste_report`

**Bersama:** `accessible_outlet_ids`

## Edge Function

**Ada (13):** `_shared`, `admin-create-staff`, `admin-reset-password`, `admin-set-status`, `admin-update-staff`, `auto-toggle-menu`, `create-staff`, `delete-staff`, `ledger-create-from-shipment`, `send-push`, `submit-attendance` *(direktori ada, perlu diselaraskan)*, `sync-outlets`, `system-health-collector`, `webhook-sync-order`
**POS:** `send-fcm`
**Baru:** `face-match`, `enroll-face`
