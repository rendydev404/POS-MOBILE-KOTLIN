-- fcm_tokens: bikin APK LAMA ikut berperilaku benar selama masa rollout.
--
-- Setelah migrasi 20260808_fcm_tokens_one_row_per_device.sql, device yang masih
-- memakai APK lama tidak bisa lagi berpindah akun:
--   1. App lama mengirim `?on_conflict=staff_id,token`. Indeks unik itu sudah
--      dihapus, jadi PostgREST menolak dengan 42P10 "no unique or exclusion
--      constraint matching the ON CONFLICT specification".
--   2. Andai target itu ada pun, barisnya bentrok di indeks unik `token` milik
--      staff SEBELUMNYA, dan app lama tidak punya cara mengambil alih.
-- Akibatnya HP itu tetap terikat ke akun lama — bukan menyasar dua outlet lagi,
-- tapi juga tidak pindah ke akun yang sedang login.
--
-- Dua langkah di bawah menutup itu di sisi server, jadi perilaku "satu device =
-- akun yang sedang login" berlaku untuk SEMUA versi app, bukan hanya yang baru.
--
-- Aman dijalankan berulang.

-- 1. Kembalikan target ON CONFLICT milik app lama.
--    Sekarang ini cuma formalitas: `token` sudah unik global, jadi
--    (staff_id, token) otomatis unik juga. Indeks ini tidak melonggarkan apa pun,
--    hanya memberi PostgREST target yang bisa dirujuk.
CREATE UNIQUE INDEX IF NOT EXISTS fcm_tokens_staff_id_token_key
  ON public.fcm_tokens (staff_id, token);

-- 2. Satu device hanya boleh dimiliki satu staff — dipaksakan di database, bukan
--    di app, supaya versi app apa pun tidak bisa melanggarnya.
--
--    SECURITY DEFINER: penghapusan ini menyentuh baris milik staff LAIN, yang
--    memang tidak boleh dilakukan langsung oleh `authenticated` lewat RLS.
--    Aman karena kondisinya sempit — hanya baris dengan token yang sama persis,
--    yaitu device fisik yang sama yang sedang dipakai staff baru.
CREATE OR REPLACE FUNCTION public.fcm_tokens_claim_device()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  DELETE FROM public.fcm_tokens
  WHERE token = NEW.token
    AND staff_id IS DISTINCT FROM NEW.staff_id;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS fcm_tokens_claim_device ON public.fcm_tokens;
CREATE TRIGGER fcm_tokens_claim_device
  BEFORE INSERT ON public.fcm_tokens
  FOR EACH ROW
  EXECUTE FUNCTION public.fcm_tokens_claim_device();
