# Global Shift Gate untuk POS Native

## Ringkasan

Buat modal fullscreen non-dismissible yang mengunci seluruh POS ketika server mengonfirmasi tidak ada shift berstatus `open`. Modal mengikuti flow web: input saldo awal petty cash, tombol utama **Buka Shift**, dan tombol **Logout**.

## Perubahan implementasi

- Tambahkan state gate shift di `ShiftViewModel`: status pengecekan awal, shift aktif/tidak, nominal petty cash terisi otomatis, status submit, dan pesan error.
- Pisahkan pengecekan ringan shift aktif dari pemuatan detail petty cash, lalu jalankan saat login, saat aplikasi kembali foreground, setiap 30 detik, dan setelah membuka shift.
- Pertahankan kalkulasi saldo awal yang sudah ada: gunakan saldo closing terakhir ditambah top-up/pengeluaran interim; kunci input bila nominal merupakan carry-over.
- Buat composable `ShiftBlockerOverlay` dengan visual setara web: backdrop blur, kartu terpusat, ikon wallet, penjelasan, field nominal, indikator loading/error, tombol **Buka Shift Sekarang**, dan tombol **Keluar Akun (Logout)**.
- Mount overlay di lapisan teratas `MainActivity`, di atas scaffold, navigasi, dan layar mana pun, sehingga Order, Menu, Histori, Laporan, dan halaman lainnya tidak dapat diakses sebelum shift dibuka.
- Tombol buka shift memanggil RPC `open_shift` yang sudah tersedia, menonaktifkan aksi ganda selama submit, lalu refresh state; overlay hilang hanya setelah shift `open` terkonfirmasi.
- Tombol logout memakai alur logout native yang sama, termasuk membersihkan session, token, outlet pada semua view model, dan state shift agar pengguna berikutnya tidak menerima state lama.
- Pertahankan layar Shift & Petty Cash sebagai halaman operasional setelah shift terbuka; rapikan form buka shift lama agar memakai state/validasi yang sama dan tidak menghasilkan flow ganda.

## Perilaku penting

- Modal tidak bisa ditutup dengan tap di luar, tombol Back, atau navigasi sidebar.
- Membuka shift wajib online; tampilkan pesan yang jelas bila perangkat offline atau RPC gagal.
- Sesuai versi web dan pilihan yang dikonfirmasi: bila pengecekan shift gagal karena jaringan/server, POS tidak dikunci (*fail-open*), tetapi user tetap tidak dapat membuka shift tanpa koneksi.
- Jika shift ditutup dari perangkat lain, polling/refresh berikutnya akan kembali menampilkan modal gate.

## Test plan

- Unit test state gate: shift aktif membuka akses; shift tidak ada memblokir; kegagalan cek bersifat fail-open.
- Unit test nominal awal: carry-over dari closing terkunci, outlet baru memakai `0`, dan nominal negatif/invalid ditolak.
- Unit test aksi buka shift: offline ditolak, RPC sukses membuka gate, RPC gagal mempertahankan modal dan menampilkan error.
- Uji manual tablet: login tanpa shift, buka shift, logout dari modal, tutup shift dari device lain, serta kembali dari background.

## Asumsi

- Scope gate berlaku untuk seluruh POS native, sama seperti `ShiftBlockerMount` pada layout kasir web.
- Tidak ada perubahan database atau RPC Supabase; memakai endpoint dan aturan petty cash yang sudah tersedia.
