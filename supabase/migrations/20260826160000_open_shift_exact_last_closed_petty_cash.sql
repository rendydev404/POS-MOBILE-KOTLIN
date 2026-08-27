-- Saldo awal shift baru harus sama persis dengan saldo fisik petty cash yang
-- disimpan saat shift closed terbaru. Jangan hitung ulang top-up/pengeluaran
-- setelah closing dan jangan gunakan snapshot penyesuaian sebagai penggantinya.

CREATE OR REPLACE FUNCTION public.open_shift(
  p_outlet_id UUID,
  p_starting_petty_cash DECIMAL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_shift_id UUID;
  v_last_closed_balance DECIMAL;
  v_starting DECIMAL;
BEGIN
  IF p_outlet_id NOT IN (SELECT public.accessible_outlet_ids()) THEN
    RAISE EXCEPTION 'Not authorized for this outlet';
  END IF;

  -- Serialisasikan open shift per outlet agar dua perangkat tidak bisa membuka
  -- shift bersamaan dengan saldo dasar yang berbeda.
  PERFORM pg_advisory_xact_lock(hashtextextended(p_outlet_id::TEXT, 0));

  IF EXISTS (
    SELECT 1
    FROM public.shifts
    WHERE outlet_id = p_outlet_id AND status = 'open'
  ) THEN
    RAISE EXCEPTION 'There is already an open shift for this outlet';
  END IF;

  SELECT COALESCE(
    actual_ending_petty_cash,
    expected_ending_petty_cash,
    starting_petty_cash
  )
  INTO v_last_closed_balance
  FROM public.shifts
  WHERE outlet_id = p_outlet_id
    AND status = 'closed'
  ORDER BY end_time DESC NULLS LAST, start_time DESC
  LIMIT 1;

  -- Input klien hanya dipakai untuk outlet yang belum pernah tutup shift.
  v_starting := COALESCE(v_last_closed_balance, p_starting_petty_cash, 0);

  IF v_starting < 0 THEN
    RAISE EXCEPTION 'Saldo awal Petty Cash tidak valid';
  END IF;

  INSERT INTO public.shifts (
    outlet_id,
    staff_id,
    starting_cash,
    starting_petty_cash,
    status
  )
  VALUES (p_outlet_id, auth.uid(), 0, v_starting, 'open')
  RETURNING id INTO v_shift_id;

  RETURN v_shift_id;
END;
$$;

GRANT EXECUTE ON FUNCTION public.open_shift(UUID, DECIMAL) TO authenticated;
