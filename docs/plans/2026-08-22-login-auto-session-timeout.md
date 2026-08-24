# Login auto-session: batas 1 detik

## Tujuan

Tablet kasir tidak boleh tertahan di layar loading penuh ketika pemulihan sesi
Supabase lambat. Form login harus tersedia setelah paling lama satu detik.

## Keputusan

- Auto-login diberi deadline total **1.000 ms** untuk refresh token, lookup
  `outlet_staff`, dan lookup `outlets` secara keseluruhan.
- Bila deadline lewat, coroutine dan request Retrofit di dalamnya dibatalkan,
  `isCheckingSession` dilepas, dan form login ditampilkan dengan pesan singkat.
- Login manual tidak diubah. Hasil auto-login yang terlambat tidak dapat
  menimpa proses manual karena requestnya sudah dibatalkan.
- Error jaringan selain timeout mempertahankan fallback sesi offline yang sudah
  ada, agar perilaku offline yang tidak terkait bug ini tidak berubah.

## Optimasi API/database

- Query identitas sekarang meminta hanya kolom DTO yang dipakai, bukan
  `select=*`, sehingga payload staff dan outlet lebih kecil.
- Lookup menggunakan filter `id=eq.<uuid>`. `id` adalah identitas kanonik,
  sehingga seharusnya sudah mendapat indeks primary-key. Tidak dibuat indeks
  duplikat tanpa hasil `EXPLAIN ANALYZE`/metrik Supabase, karena tidak
  mempercepat lookup dan hanya menambah biaya tulis.
- Tindak lanjut produksi: ukur durasi tiga request secara terpisah di log/metric
  Supabase sebelum mempertimbangkan perubahan indeks atau RPC gabungan.

## Verifikasi

- Tes unit menjamin operasi cepat tetap mengembalikan hasil dan operasi yang
  melewati satu detik dibatalkan.
- Build debug dan seluruh unit test debug dijalankan.

## Decision log

| Keputusan | Alternatif | Alasan |
|---|---|---|
| Batalkan auto-login setelah 1 detik | Tetap jalan di background | Menghindari race dengan login manual dan spinner tanpa akhir. |
| Pertahankan fallback offline untuk error non-timeout | Paksa form login untuk semua error | Menjaga perilaku offline yang sudah ada di luar scope perubahan. |
| Tidak tambah indeks DB | Tambah indeks `id` | Lookup primary-key sudah terindeks; indeks tambahan redundan tanpa bukti metrik. |
