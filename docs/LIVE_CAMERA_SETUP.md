# Live Camera POS — setup deployment

Fitur ini adalah **live view saja**. Tidak ada endpoint, bucket, atau proses yang
menyimpan rekaman video. Supabase menyimpan metadata sesi dan LiveKit membawa
frame WebRTC langsung dari tablet ke dashboard.

## 1. Endpoint LiveKit

Deployment produksi saat ini menggunakan LiveKit self-hosted di VPS:

- `LIVEKIT_URL`: `wss://livekit.sukashawarma.tech`
- HTTPS signalling/API dilewatkan melalui Traefik pada port 443.
- WebRTC memakai TCP 7881 dan UDP 50000-50099.

Jika membuat deployment lain, catat tiga nilai berikut tanpa memasukkannya ke
APK atau repository:

- `LIVEKIT_URL` — URL WebSocket, misalnya `wss://nama-project.livekit.cloud`
- `LIVEKIT_API_KEY`
- `LIVEKIT_API_SECRET`

## 2. Konfigurasi Supabase Edge Function

Set ketiga nilai LiveKit sebagai secret pada project Supabase, lalu deploy
fungsi `livekit-token`. Function ini memverifikasi Supabase session, role, dan
scope outlet sebelum menandatangani token LiveKit berdurasi 10 menit.

```powershell
supabase secrets set LIVEKIT_URL=wss://YOUR_LIVEKIT_HOST
supabase secrets set LIVEKIT_API_KEY=YOUR_LIVEKIT_API_KEY
supabase secrets set LIVEKIT_API_SECRET=YOUR_LIVEKIT_API_SECRET
supabase functions deploy livekit-token
```

Jalankan migrasi `20260827170000_live_camera_sessions.sql` melalui alur migrasi
Supabase yang biasa dipakai project ini. Migration tersebut hanya membuat tabel
status `camera_sessions` dan menambahkannya ke Supabase Realtime.

## 3. Build dan deploy aplikasi

- POS Native membutuhkan dependency `io.livekit:livekit-android:2.28.1`.
- Admin dashboard membutuhkan `livekit-client`. Jalankan install dependency dari
  workspace root sebelum build dashboard.
- Setelah POS login dan izin kamera Android diberikan, `LiveCameraService`
  mempublikasikan kamera depan 720p (H720) tanpa audio. Logout menghentikan service.

Konfigurasi VPS sengaja memakai satu node tanpa Redis agar jejak memori kecil.
LiveKit tidak melakukan transcoding; bandwidth dan kualitas video ditentukan oleh
track 720p dari perangkat POS dan adaptive/dynacast pada dashboard.

## Perilaku akses

- POS publisher hanya dapat membuka room `pos-camera-<outlet_id>` untuk outlet
  yang ada di `accessible_outlet_ids()` miliknya.
- Hanya role `admin` dan `owner` yang dapat meminta token viewer.
- Token LiveKit API secret tetap berada di Supabase Environment, bukan browser
  atau perangkat Android.
- Dashboard menganggap sesi offline bila heartbeat lebih tua dari 75 detik.
