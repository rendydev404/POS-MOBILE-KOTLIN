-- daily_sales_targets sebelumnya cuma punya policy admin_all (is_owner_or_admin()).
-- Tanpa policy SELECT untuk kasir, Supabase Realtime diam-diam menolak
-- mengirim event perubahan target ke socket kasir (RLS gagal di sisi server),
-- jadi progress bar target di app cuma ke-update lewat fallback poll 60 detik
-- atau kebetulan ada event 'orders' lain, bukan realtime beneran.
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'daily_sales_targets' AND column_name = 'outlet_id'
  ) THEN
    DROP POLICY IF EXISTS daily_sales_targets_select_scoped ON public.daily_sales_targets;
    CREATE POLICY daily_sales_targets_select_scoped
      ON public.daily_sales_targets
      FOR SELECT
      USING (outlet_id IN (SELECT accessible_outlet_ids() AS accessible_outlet_id));
  END IF;
END $$;
