# Sinkronisasi Fitur Android POS dengan Versi Web

## Deskripsi

Menyamakan semua fitur dan logic dari halaman **Shift (Petty Cash)**, **Histori & Bonus**, **Kontrol Device**, dan **Laporan** pada versi Android agar 100% identik dengan versi web. Halaman **Manajemen Menu** dikecualikan.

Berdasarkan diskusi:
1. **Kiosk Control**: Fitur ini di-skip sementara (menjadi fitur *incoming/coming soon*).
2. **Receipt Image Upload**: Diterapkan (flow sama seperti web).
3. **Export Laporan**: Menggunakan export PDF.
4. **Bluetooth Printer**: Diimplementasikan untuk cetak struk (via ESC/POS).
5. **Admin Dashboard Rules**: Diimplementasikan banner "Target Harian" dan "Pesan Owner".

---

## Proposed Changes

### Komponen 1: Supabase API Layer (Data Layer)

Menambah endpoint RPC dan query yang belum ada di Android.

#### [MODIFY] `SupabaseApi.kt`
Tambah endpoint baru:
- `POST rpc/void_petty_cash_expense` — void petty cash dengan reason
- `POST rpc/crew_receive_funds` — terima dana topup petty cash
- `POST rpc/calculate_monthly_crew_bonus` — kalkulasi bonus bulanan crew
- `POST rpc/get_daily_bonus_breakdown` — breakdown bonus harian
- `POST rpc/get_my_target_progress` — untuk widget Target Harian
- `POST rpc/get_my_active_messages` — untuk widget Pesan Owner
- `POST rest/v1/petty_cash_topups` — request topup dana
- `GET rest/v1/petty_cash_topups` — fetch daftar topup
- `GET rest/v1/orders` dengan date range filter — untuk laporan berdasarkan tanggal
- `GET rest/v1/shifts` dengan status filter `eq.closed` — untuk laporan shift history
- `PUT storage/v1/object/petty-cash-receipts/...` — upload foto struk petty cash

#### [NEW] `SupabaseDtos.kt` (additions)
Tambah DTO baru:
- `VoidPettyCashPayload`, `ReceiveFundsPayload`, `PettyCashTopupDto`
- `MonthlyBonusPayload`, `MonthlyBonusResultDto`, `DailyBonusBreakdownDto`
- `ReportOrderDto`
- `TargetProgressDto` (untuk Target Harian)
- `OwnerMessageDto` (untuk Pesan Owner)

---

### Komponen 2: Global UI & Admin Rules (Presentation Layer)

Menambahkan elemen global untuk target harian dan pesan owner yang muncul di bagian atas aplikasi.

#### [NEW] `BriefingBanner.kt` (Global Component)
- **Target Harian**: Menampilkan progress bar pencapaian omzet vs target hari ini. Memanggil `get_my_target_progress` dan otomatis polling / real-time update. Jika target tercapai (≥ 100%), akan ada indikator visual "Target Tercapai".
- **Pesan Owner**: Menampilkan pesan-pesan dari admin/owner (motivasi, peringatan, info) yang didapat dari `get_my_active_messages`.

---

### Komponen 3: Bluetooth Printer & Cetak Struk (Utility & Setting Layer)

Mengimplementasikan sistem koneksi Bluetooth dan encoding struk menggunakan ESC/POS (setara dengan `EscPosEncoder` di versi Web).

#### [NEW] `BluetoothPrinterService.kt`
- Mengelola koneksi ke thermal printer via Android Bluetooth API.
- Fitur auto-reconnect dan manual search (disertai opsi filter nama prefix printer, misalnya `POS`, `RPP`, dll).

#### [NEW] `EscPosBuilder.kt`
- Utility class untuk menyusun command ESC/POS (align, bold, text size, cut, raster image logo).
- Fungsi generate byte array dari data receipt/struk.

#### [NEW] `ReceiptPrinter.kt`
- Menggabungkan data struk pesanan (order details, total, diskon, metode pembayaran, nama kasir) dan mengirimkannya ke `BluetoothPrinterService`.

#### [MODIFY] `SettingsScreen.kt` & `SettingsViewModel.kt`
- Tambah UI untuk scan dan connect perangkat Bluetooth Printer.
- Simpan ID/MAC Address printer terpilih ke SharedPreferences.
- (Catatan: Kiosk Control di-skip, jadi halaman settings fokus ke pengaturan Printer).

---

### Komponen 4: Shift & Petty Cash (Presentation Layer)

#### [MODIFY] `ShiftViewModel.kt`
1. **Petty Cash Topups**: Request topup, fetch list, dan "Terima Dana".
2. **Void Expense**: Memanggil RPC `void_petty_cash_expense`.
3. **Activity Ledger**: Gabungan sales (cash), expenses, dan topups menjadi satu list chronological.
4. **Close Shift Logic**: Validasi waktu (hanya boleh close jam 22:00-06:00), perbandingan kas aktual vs sistem.
5. **Starting Petty Cash Lock**: Modal awal dilock berdasarkan saldo akhir shift sebelumnya.
6. **Receipt Image Upload**: Menggunakan Camera API Android untuk ambil foto struk petty cash dan upload ke bucket `petty-cash-receipts`.

#### [MODIFY] `ShiftScreen.kt`
- Activity Ledger Timeline.
- Form tambah Petty Cash dengan capture foto struk.
- "Void" button dengan dialog konfirmasi & alasan.
- Topup Section (request form & list pending).
- Split input tutup shift: Cash Drawer Aktual & Petty Cash Aktual.

---

### Komponen 5: Histori & Bonus (Presentation Layer)

#### [MODIFY] `OrderHistoryViewModel.kt` & `OrderHistoryScreen.kt`
1. **Date Range & Filter**: Filter tanggal (Hari ini, Kemarin, 7 Hari, 30 Hari, Custom), Status (Pending, Selesai, Batal), dan Channel order.
2. **Order List**: Expand item, tampilkan detail order dan bukti pembayaran QRIS jika ada.
3. **Bonus Tab (NEW)**:
   - Pilih Bulan & Tahun.
   - Panggil `calculate_monthly_crew_bonus` dan tampilkan Total Terkumpul, Bonus Per Orang, Target Tercapai (X hari).
   - Tampilkan tabel detail target harian per tanggal (`get_daily_bonus_breakdown`).
   - Card Simulasi Bonus (slider untuk interaktif preview perhitungan bonus).

---

### Komponen 6: Laporan (Presentation Layer)

#### [MODIFY] `ReportsViewModel.kt` & `ReportsScreen.kt`
1. **Date Range Filter**: Filter tanggal kustom.
2. **KPI Cards**: Omzet Kotor, Total Potongan, Pendapatan Bersih, Pesanan Sukses, Rata-rata/Order, Jam Tersibuk.
3. **Distribusi Pembayaran & Tren 24 Jam**: Progress bar cash vs QRIS vs Card, dan bar chart distribusi per jam.
4. **Top 10 Best Sellers**: Daftar menu terlaris.
5. **Laporan Laci Cash**: Tabel rekap shift (Modal Awal, Penjualan, Petty Cash, Selisih).
6. **Export PDF (NEW)**: Tombol untuk men-generate layout laporan ke dalam bentuk file PDF dan membagikan / menyimpannya ke perangkat Android.

---

## Verification Plan

### Automated Tests
```bash
.\gradlew.bat testDebugUnitTest
```

### Manual Verification
1. **Admin Rules**: Verifikasi banner "Target Harian" dan "Pesan Owner" tampil di atas UI aplikasi dan update otomatis.
2. **Bluetooth Printer**: Pairing dengan thermal printer, pastikan struk tercetak (lengkap dengan header, details, dan logo jika disetting).
3. **Shift**: Tes open shift (modal dilock), tambah petty cash dengan foto struk, request topup, dan close shift (dengan validasi jam).
4. **History & Bonus**: Pastikan filtering tanggal akurat, klik order bisa expand, dan Tab Bonus menampilkan hitungan yang persis sama dengan web.
5. **Reports**: Cocokkan seluruh KPI dengan dashboard web, dan tes fungsi **Export to PDF**.
