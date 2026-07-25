# Push Notification (FCM) — Server Sender + Tap Deep-Link

**Status:** Approved
**Tanggal:** 2026-07-25

## Latar Belakang

Client-side FCM sudah lengkap: `POSFirebaseMessagingService`, `FcmTokenRegistrar`,
`NotificationChannels`, `OrderAlertPlayer`, permission runtime, dan tabel
`fcm_tokens` di Supabase. Yang hilang: **tidak ada apa pun yang mengirim push**.
Token tersimpan tapi tidak pernah dipakai. Fitur belum berfungsi end-to-end.

## Tujuan

1. Kirim push FCM otomatis saat: order baru masuk dengan `source` = `kiosk`
   atau `online`, dan saat order berubah status ke `cancelled`.
2. Push harus membunyikan alarm (`OrderAlertPlayer`) walau app di-background —
   ini memaksa payload FCM **data-only** (bukan `notification` block), karena
   payload `notification` membuat Android menampilkan notifikasi sendiri tanpa
   memanggil `onMessageReceived()` saat background, sehingga alarm tidak bunyi.
3. Tap notifikasi membuka Dashboard dan menyorot (highlight) kartu order terkait.
4. Token FCM dihapus dari server saat staff logout, supaya device lama tidak
   terus menerima push untuk outlet yang sudah ditinggalkan.
5. Token yang ditolak FCM (`UNREGISTERED` / `INVALID_ARGUMENT`) dibersihkan
   otomatis dari tabel `fcm_tokens`.

## Arsitektur

```
orders  ── INSERT (source IN kiosk, online)
        └─ UPDATE (status → cancelled)
   │
   ▼ trigger SQL + pg_net (async POST)
Edge Function `notify-order` (Deno/TypeScript)
   │ 1. Baca payload trigger (event type, record baru)
   │ 2. SELECT token FROM fcm_tokens WHERE outlet_id = record.outlet_id
   │ 3. Tukar service-account JSON (secret FCM_SERVICE_ACCOUNT) → OAuth2
   │    access token via JWT bearer grant, cache in-memory ±55 menit
   │ 4. POST FCM HTTP v1 /v1/projects/pos-native-de856/messages:send
   │    per token, payload data-only
   │ 5. Kalau FCM balas UNREGISTERED/INVALID_ARGUMENT → DELETE token itu
   ▼
FCM ──► device (POSFirebaseMessagingService.onMessageReceived)
   ├─ OrderAlertPlayer.playNewOrderAlert()   (tone + getar)
   └─ NotificationCompat + PendingIntent(order_id, type) → MainActivity
   ▼ (tap)
MainActivity baca extras → set tab DASHBOARD →
DashboardViewModel.highlightedOrderId = order_id (auto-clear 5 detik)
DashboardScreen scroll ke kartu + border highlight sementara
```

## Komponen Server (baru)

| File | Isi |
|---|---|
| `supabase/functions/notify-order/index.ts` | HTTP handler: parse trigger payload, ambil token, kirim, cleanup token invalid |
| `supabase/functions/notify-order/fcm.ts` | OAuth2 service-account exchange + `sendToToken()`, terpisah supaya diuji sendiri |
| `supabase_migration_order_push_trigger.sql` | Fungsi trigger + `AFTER INSERT OR UPDATE ON orders FOR EACH ROW` yang memanggil Edge Function lewat `net.http_post` (ekstensi `pg_net`) |
| `supabase_migration_fcm_tokens_delete_policy.sql` | Tambah policy `DELETE` untuk `fcm_tokens` (staff hapus token miliknya sendiri saat logout; service_role bebas untuk cleanup token invalid) |

Payload data FCM (semua string, sesuai batasan format FCM data message):
```json
{
  "type": "new_order" | "order_cancelled",
  "order_id": "<uuid>",
  "order_number": "15",
  "title": "Pesanan Baru Masuk" | "Pesanan Dibatalkan",
  "body": "No. 15 — Ahmad · Rp 80.000"
}
```

## Komponen Client (diubah)

| File | Perubahan |
|---|---|
| `POSFirebaseMessagingService.kt` | Baca `data["type"]`/`order_id` untuk judul & isi; bangun `PendingIntent` ke `MainActivity` membawa extras `order_id`, `notif_type`; notification ID = `order_id.hashCode()` supaya push berulang untuk order sama tidak menumpuk |
| `MainActivity.kt` | Baca extras di `onCreate` (cold start) dan `onNewIntent` (app sudah hidup); set `currentTab = DASHBOARD`; teruskan `order_id` ke `dashboardViewModel.highlightOrder(id)` |
| `DashboardViewModel.kt` | `highlightedOrderId: StateFlow<String?>` + `highlightOrder(id)` yang set lalu `delay(5000)` clear otomatis |
| `DashboardScreen.kt` | `OrderCard` untuk order yang `id == highlightedOrderId` diberi border warna aksen; `LazyListState` + `scrollToItem` saat highlight berubah |
| `LoginViewModel.kt` | `logout()` memanggil `FcmTokenRegistrar.unregisterCurrentToken()` sebelum clear session |
| `FcmTokenRegistrar.kt` | Tambah `unregisterCurrentToken()`: ambil token FCM saat ini, `DELETE fcm_tokens?token=eq....` |
| `SupabaseApi.kt` | Tambah `@DELETE("rest/v1/fcm_tokens")` dengan query filter `token` |
| `NotificationChannels.kt` | Set `lockscreenVisibility = VISIBILITY_PUBLIC` (isi order tidak sensitif secara publik — nomor antrean & nama depan pelanggan) |

## Yang tidak dikerjakan (YAGNI)

- Retry queue kalau pengiriman FCM gagal — WebSocket realtime (`OrderRealtimeManager`)
  dan sync 30 detik yang sudah ada tetap membawa data order-nya; push cuma alarm,
  bukan satu-satunya jalur data.
- Push untuk status `ready` — di luar scope yang disepakati saat ini.
- Batch send (`sendEach`) — outlet realistis <10 device terdaftar, loop sekuensial cukup.

## Verifikasi

- Kirim order test dengan `source=kiosk` dari SQL Editor → device penerima
  harus bunyi alarm + notifikasi muncul walau app di-background/killed.
- Tap notifikasi → app buka ke Dashboard, kartu order tersebut ter-highlight.
- Logout lalu cek tabel `fcm_tokens` → baris device itu sudah terhapus.
- Kirim ke token basi (uninstall app tanpa logout) → baris token itu hilang
  otomatis dari `fcm_tokens` setelah Edge Function jalan.
