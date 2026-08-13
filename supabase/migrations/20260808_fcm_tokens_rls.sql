-- fcm_tokens: policy SELECT untuk baris milik sendiri.
--
-- Akar masalah (terbukti lewat reproduksi di SQL, bukan dugaan):
--   INSERT polos sebagai role `authenticated` dengan staff_id = auth.uid() SUKSES,
--   tapi app mendaftarkan token lewat upsert PostgREST
--   (?on_conflict=staff_id,token + Prefer: resolution=merge-duplicates), yang
--   diterjemahkan jadi INSERT ... ON CONFLICT DO UPDATE. Perintah itu harus bisa
--   MEMBACA baris yang bentrok, sedangkan tabel ini sengaja dibuat tanpa policy
--   SELECT untuk `authenticated`. Hasilnya device kasir selalu ditolak dengan
--   42501 "new row violates row-level security policy", tabel fcm_tokens tetap
--   kosong, dan send-fcm selalu balas "No tokens found".
--
-- Policy INSERT dan UPDATE-nya sendiri sudah benar dan sudah ada di produksi
-- (dicek lewat pg_policies), jadi migrasi ini hanya menambah yang hilang.
-- Device hanya boleh melihat token miliknya sendiri, bukan token device lain.
--
-- Aman dijalankan berulang.

ALTER TABLE public.fcm_tokens ENABLE ROW LEVEL SECURITY;

GRANT SELECT, INSERT, UPDATE ON public.fcm_tokens TO authenticated;

DROP POLICY IF EXISTS "fcm_tokens_select_self" ON public.fcm_tokens;
CREATE POLICY "fcm_tokens_select_self" ON public.fcm_tokens
  FOR SELECT TO authenticated
  USING (staff_id = auth.uid());
