-- Agregasi omzet di sisi database.
--
-- Sebelumnya app menarik seluruh baris `orders` lalu menjumlahkannya di klien.
-- Itu salah untuk rentang panjang: PostgREST memotong di 1000 baris (dan halaman
-- Riwayat bahkan membatasi 100-200), jadi omzet "30 Hari" dan "Semua Waktu"
-- selalu lebih kecil dari yang sebenarnya tanpa peringatan apa pun.
--
-- Definisi omzet yang dipakai di sini adalah satu-satunya yang berlaku di app:
--   * hanya pesanan berstatus 'completed'
--   * omzet KOTOR = SUM(order_items.subtotal), yaitu sebelum diskon/subsidi promo
--   * seluruh pengelompokan waktu memakai zona Asia/Jakarta
--
-- `net` dan `deductions` ikut dikembalikan untuk keperluan rekonsiliasi kas,
-- tapi app hanya menampilkan `gross`.

CREATE OR REPLACE FUNCTION public.pos_revenue_summary(
  p_outlet_id      uuid,
  p_start          timestamptz DEFAULT NULL,
  p_end            timestamptz DEFAULT NULL,
  p_payment_method text        DEFAULT NULL,
  p_channels       text[]      DEFAULT NULL,
  p_include_null_channel boolean DEFAULT false
)
RETURNS jsonb
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
WITH scoped AS (
  SELECT
    o.id,
    o.status,
    o.payment_method,
    o.total_amount,
    o.created_at,
    o.cancellation_status,
    COALESCE(o.discount_amount, 0) AS discount_amount,
    COALESCE(o.promo_subsidy, 0)   AS promo_subsidy
  FROM public.orders o
  WHERE o.outlet_id = p_outlet_id
    AND (p_start IS NULL OR o.created_at >= p_start)
    AND (p_end   IS NULL OR o.created_at <= p_end)
    AND (p_payment_method IS NULL OR o.payment_method = p_payment_method)
    AND (
      p_channels IS NULL
      OR o.channel = ANY (p_channels)
      OR (p_include_null_channel AND o.channel IS NULL)
    )
),
-- Omzet kotor per pesanan. LEFT JOIN supaya pesanan tanpa baris item tetap
-- terhitung: nilainya direkonstruksi dari total_amount + potongan yang tercatat,
-- persis seperti fallback RevenueCalculator.grossOf() di app.
completed AS (
  SELECT
    s.id,
    s.payment_method,
    s.total_amount,
    s.created_at,
    COALESCE(i.gross, s.total_amount + s.discount_amount + s.promo_subsidy) AS gross,
    COALESCE(i.qty, 0) AS qty
  FROM scoped s
  LEFT JOIN (
    SELECT order_id, SUM(subtotal) AS gross, SUM(quantity) AS qty
    FROM public.order_items
    GROUP BY order_id
  ) i ON i.order_id = s.id
  -- Pembatalan yang sudah disetujui owner tidak selalu ikut mengubah kolom
  -- `status`, jadi `status = 'completed'` saja masih meloloskan pesanan batal.
  WHERE s.status = 'completed'
    AND COALESCE(s.cancellation_status, '') <> 'approved'
),
totals AS (
  SELECT
    COALESCE(SUM(gross), 0)        AS gross,
    COALESCE(SUM(total_amount), 0) AS net,
    COUNT(*)                       AS order_count,
    COALESCE(SUM(qty), 0)          AS items_sold
  FROM completed
),
by_payment AS (
  SELECT jsonb_agg(jsonb_build_object(
           'payment_method', COALESCE(payment_method, 'unknown'),
           'gross', gross, 'count', cnt
         ) ORDER BY gross DESC) AS data
  FROM (
    SELECT payment_method, SUM(gross) AS gross, COUNT(*) AS cnt
    FROM completed GROUP BY payment_method
  ) p
),
-- Jam ditentukan setelah dikonversi ke Asia/Jakarta. Sebelumnya app membaca jam
-- UTC mentah dari created_at, sehingga "Jam Tersibuk" selalu meleset 7 jam.
by_hour AS (
  SELECT jsonb_agg(jsonb_build_object('hour', h, 'count', cnt) ORDER BY h) AS data
  FROM (
    SELECT EXTRACT(HOUR FROM created_at AT TIME ZONE 'Asia/Jakarta')::int AS h,
           COUNT(*) AS cnt
    FROM completed GROUP BY 1
  ) x
),
by_day AS (
  SELECT jsonb_agg(jsonb_build_object('day', d, 'gross', gross) ORDER BY d) AS data
  FROM (
    SELECT (created_at AT TIME ZONE 'Asia/Jakarta')::date AS d,
           SUM(gross) AS gross
    FROM completed GROUP BY 1
  ) x
),
top_items AS (
  SELECT jsonb_agg(jsonb_build_object(
           'name', name, 'qty', qty, 'gross', gross
         ) ORDER BY qty DESC) AS data
  FROM (
    SELECT oi.menu_item_name AS name,
           SUM(oi.quantity)  AS qty,
           SUM(oi.subtotal)  AS gross
    FROM public.order_items oi
    JOIN completed c ON c.id = oi.order_id
    GROUP BY oi.menu_item_name
    ORDER BY qty DESC
    LIMIT 10
  ) x
),
-- Dihitung dari `scoped`, bukan `completed`. "Pending" mencakup seluruh pesanan
-- yang masih berjalan: tidak ada baris berstatus 'pending' di tabel ini, yang
-- ada 'preparing', sehingga menghitung 'pending' saja selalu menghasilkan nol.
statuses AS (
  SELECT
    COUNT(*) FILTER (
      WHERE status IN ('pending', 'preparing', 'ready')
        AND COALESCE(cancellation_status, '') <> 'approved'
    ) AS pending_count,
    COUNT(*) FILTER (
      WHERE status = 'cancelled' OR COALESCE(cancellation_status, '') = 'approved'
    ) AS cancelled_count
  FROM scoped
)
SELECT jsonb_build_object(
  'gross',           t.gross,
  'net',             t.net,
  'deductions',      GREATEST(t.gross - t.net, 0),
  'order_count',     t.order_count,
  'items_sold',      t.items_sold,
  'avg_order_gross', CASE WHEN t.order_count > 0 THEN t.gross / t.order_count ELSE 0 END,
  'pending_count',   s.pending_count,
  'cancelled_count', s.cancelled_count,
  'by_payment',      COALESCE(bp.data, '[]'::jsonb),
  'by_hour',         COALESCE(bh.data, '[]'::jsonb),
  'by_day',          COALESCE(bd.data, '[]'::jsonb),
  'top_items',       COALESCE(ti.data, '[]'::jsonb)
)
FROM totals t, statuses s, by_payment bp, by_hour bh, by_day bd, top_items ti;
$$;

-- SECURITY DEFINER melewati RLS, jadi pembatasan outlet dilakukan di sini:
-- pemanggil hanya boleh meminta outlet tempat dia terdaftar sebagai staf.
CREATE OR REPLACE FUNCTION public.pos_revenue_summary_guarded(
  p_outlet_id      uuid,
  p_start          timestamptz DEFAULT NULL,
  p_end            timestamptz DEFAULT NULL,
  p_payment_method text        DEFAULT NULL,
  p_channels       text[]      DEFAULT NULL,
  p_include_null_channel boolean DEFAULT false
)
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY INVOKER
SET search_path = public
AS $$
BEGIN
  IF NOT EXISTS (
    -- outlet_staff.id adalah auth.uid() itu sendiri; lihat policy
    -- outlet_staff_read_self yang memakai `id = auth.uid()`.
    SELECT 1 FROM public.outlet_staff st
    WHERE st.outlet_id = p_outlet_id
      AND st.id = auth.uid()
  ) THEN
    RAISE EXCEPTION 'Tidak punya akses ke outlet ini';
  END IF;

  RETURN public.pos_revenue_summary(
    p_outlet_id, p_start, p_end, p_payment_method, p_channels, p_include_null_channel
  );
END $$;

-- Fungsi inti melewati RLS, jadi tidak boleh dipanggil langsung oleh siapa pun
-- selain wrapper-nya. Wrapper sendiri hanya untuk sesi yang sudah login —
-- EXECUTE bawaan PostgreSQL diberikan ke PUBLIC, jadi harus dicabut eksplisit.
REVOKE ALL ON FUNCTION public.pos_revenue_summary(uuid, timestamptz, timestamptz, text, text[], boolean) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.pos_revenue_summary_guarded(uuid, timestamptz, timestamptz, text, text[], boolean) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.pos_revenue_summary_guarded(uuid, timestamptz, timestamptz, text, text[], boolean) TO authenticated;

-- Rentang panjang memindai orders.created_at per outlet; tanpa indeks ini
-- "30 Hari" dan "Semua Waktu" berubah jadi sequential scan.
CREATE INDEX IF NOT EXISTS idx_orders_outlet_created_at
  ON public.orders (outlet_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_order_items_order_id
  ON public.order_items (order_id);
