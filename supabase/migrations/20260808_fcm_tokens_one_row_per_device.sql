-- fcm_tokens: satu baris per device, bukan satu baris per (staff, device).
--
-- Akar masalah (terbukti lewat query ke produksi, bukan dugaan):
--   Kunci unik tabel ini `UNIQUE (staff_id, token)`, dan tidak ada satu pun
--   jalur yang menghapus baris saat kasir logout. Jadi setiap akun yang pernah
--   login di satu HP meninggalkan barisnya sendiri, dengan `outlet_id` outlet
--   lamanya. Edge function send-fcm memilih target hanya lewat
--   `.eq('outlet_id', ...)`, sehingga HP itu ikut dikirimi notifikasi outlet
--   lama selamanya — persis keluhan "push nyasar dari akun yang sudah logout".
--
--   Hitungan di produksi saat migrasi ini ditulis: 1 device unik, 2 baris,
--   2 staff berbeda, 2 outlet berbeda. Satu HP terdaftar di dua outlet.
--
-- Perbaikannya menjadikan device (token FCM) sebagai identitas tunggal:
-- login berikutnya MENGAMBIL ALIH baris token itu, bukan menambah baris baru.
-- Baris tetap ada setelah logout, jadi akun terakhir masih menerima notifikasi
-- sampai ada yang login lagi di HP tersebut — sesuai yang diminta.
--
-- Aman dijalankan berulang.

-- 1. Rapikan baris lama: sisakan pendaftaran TERBARU per device.
--    Yang terbaru = akun yang terakhir dipakai di HP itu.
DELETE FROM public.fcm_tokens a
USING public.fcm_tokens b
WHERE a.token = b.token
  AND (
    a.updated_at < b.updated_at
    OR (a.updated_at = b.updated_at AND a.id < b.id)
  );

-- 2. Tukar kunci unik: (staff_id, token) -> (token).
ALTER TABLE public.fcm_tokens DROP CONSTRAINT IF EXISTS fcm_tokens_staff_id_token_key;
CREATE UNIQUE INDEX IF NOT EXISTS fcm_tokens_token_key ON public.fcm_tokens (token);

-- 3. Pendaftaran token lewat RPC, bukan upsert langsung ke tabel.
--
--    Alasannya RLS: baris yang bentrok bisa saja milik staff SEBELUMNYA, dan
--    policy UPDATE tabel ini (sengaja) hanya mengizinkan `staff_id = auth.uid()`.
--    Upsert biasa akan ditolak. Melonggarkan policy jadi "siapa pun boleh
--    meng-update baris token apa pun" membuka penyalahgunaan: siapa saja yang
--    tahu token sebuah HP bisa membelokkan notifikasi HP itu ke dirinya.
--    SECURITY DEFINER menutup celah itu — pemanggil tidak pernah bisa memilih
--    staff_id, karena diambil paksa dari auth.uid().
CREATE OR REPLACE FUNCTION public.register_fcm_token(
  p_token text,
  p_outlet_id uuid DEFAULT NULL
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_staff uuid := auth.uid();
BEGIN
  IF v_staff IS NULL THEN
    RAISE EXCEPTION 'register_fcm_token: tidak ada sesi aktif';
  END IF;

  IF p_token IS NULL OR btrim(p_token) = '' THEN
    RAISE EXCEPTION 'register_fcm_token: token kosong';
  END IF;

  INSERT INTO public.fcm_tokens (staff_id, outlet_id, token, platform, updated_at)
  VALUES (v_staff, p_outlet_id, btrim(p_token), 'android', now())
  ON CONFLICT (token) DO UPDATE
    SET staff_id   = EXCLUDED.staff_id,
        outlet_id  = EXCLUDED.outlet_id,
        updated_at = now();
END;
$$;

REVOKE ALL ON FUNCTION public.register_fcm_token(text, uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.register_fcm_token(text, uuid) TO authenticated;

-- Grant INSERT/UPDATE ke `authenticated` sengaja TIDAK dicabut. Indeks unik di
-- langkah 2 sudah menutup celahnya: app versi lama tidak mungkin lagi membuat
-- baris kedua untuk device yang sama (upsert-nya gagal 409), sementara device
-- yang belum sempat diperbarui tetap bisa mendaftar pertama kali seperti biasa.
-- Mencabut grant justru akan mematikan notifikasi di device yang APK-nya belum
-- diganti.
