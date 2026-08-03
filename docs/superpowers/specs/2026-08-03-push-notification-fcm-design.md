# Push Notification (FCM) via Supabase Edge Function Design

## 1. Goal
Menambahkan sistem *Push Notification* (Firebase Cloud Messaging - FCM) yang berjalan di latar belakang (background) dan realtime untuk aplikasi POS Native. Notifikasi ini akan dikirimkan saat ada pesanan baru yang masuk ke sistem (dari Web atau Native) dan ketika ada pesan dari owner untuk kasir.

## 2. Architecture Overview
Karena database yang digunakan sama (Supabase) untuk Web dan Native, kita menggunakan arsitektur berbasis Event-Driven di sisi database:
- **Event Source:** Operasi `INSERT` pada tabel `orders` dan `owner_messages`.
- **Event Trigger:** Database Webhook (PostgreSQL Trigger) yang memanggil Supabase Edge Function secara asinkron.
- **Worker (Edge Function):** Fungsi bernama `send-fcm` ditulis dalam Deno/TypeScript.
- **Delivery:** Firebase Admin SDK (atau HTTP v1 API Firebase) memanggil server Google untuk mengirim push notifikasi ke device Android.

## 3. Komponen Sistem

### 3.1. Supabase Edge Function (`send-fcm`)
- **Tugas Utama:** Menerima *payload* dari trigger, mencari token FCM yang relevan di tabel `fcm_tokens`, dan mengirimkan request ke API Firebase.
- **Dependencies:** Membutuhkan Service Account Key dari Firebase (disimpan dengan aman di Supabase Secrets/Environment Variables).
- **Logika Filtering:**
  - Jika event berasal dari `orders`: Ambil token dari tabel `fcm_tokens` di mana `outlet_id` sama dengan `outlet_id` pesanan tersebut.
  - Jika event berasal dari `owner_messages`: Tergantung target pesannya (bisa spesifik ke satu `outlet_id` atau *broadcast* ke seluruh outlet).

### 3.2. Database Trigger: Pesanan Baru
- **Tabel:** `orders`
- **Operasi:** `AFTER INSERT`
- **Aksi:** Memanggil Edge Function `send-fcm`.
- **Payload yang diteruskan:** Berisi detail order seperti `id`, `outlet_id`, dan `total_amount` agar fungsi bisa merangkai teks notifikasi yang jelas (misal: "Pesanan Baru Rp 50.000").

### 3.3. Database Trigger: Pesan Owner
- **Tabel:** `owner_messages` (atau tabel yang ekuivalen di database saat ini)
- **Operasi:** `AFTER INSERT`
- **Aksi:** Memanggil Edge Function `send-fcm`.
- **Payload yang diteruskan:** Berisi `title`, `body`, dan `outlet_id` target.

### 3.4. Sisi Klien (Android)
- Sudah diimplementasikan: `POSFirebaseMessagingService` menerima pesan data dari FCM, membunyikan alert khusus (beda antara pesan owner dan order), lalu memunculkan notifikasi sistem Android.

## 4. Keamanan (Security)
- Endpoint Edge Function bersifat privat. Hanya request yang memiliki *Authorization Header* valid (Service Role Key atau JWT khusus) yang diizinkan untuk dieksekusi.
- Database Trigger secara otomatis akan menggunakan otentikasi internal Supabase saat memanggil webhook.

## 5. Rencana Pengujian (Testing)
1. *Unit Testing:* Mengirim payload HTTP POST manual (misalnya menggunakan Postman atau curl) ke Edge Function untuk memastikan Firebase Admin SDK berhasil mengirim pesan.
2. *Integration Testing:* Melakukan insert manual pada tabel `orders` via SQL Editor di Supabase untuk memverifikasi apakah trigger bekerja dan notifikasi muncul di HP.
