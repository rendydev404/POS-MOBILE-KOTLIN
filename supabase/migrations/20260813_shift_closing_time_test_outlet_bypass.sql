-- Trigger check_shift_closing_time (lihat migration-shift-closing-time.sql di apps/pos-kasir)
-- memblokir penutupan shift di luar jam 22:00-06:00 untuk SEMUA outlet, tanpa
-- pengecualian. Ini menyulitkan pengujian di outlet tes karena hack tanggal di
-- client (ShiftViewModel.checkTimeRestriction) tidak berpengaruh sama sekali --
-- database tetap menolak. Tambahkan pengecualian permanen untuk outlet tes.
CREATE OR REPLACE FUNCTION check_shift_closing_time()
RETURNS trigger AS $$
DECLARE
  current_hour INT;
  test_outlet_id CONSTANT uuid := 'eb174b2b-ff69-47eb-97af-b6c824d3ce4a';
BEGIN
  IF OLD.status = 'open' AND NEW.status = 'closed' AND NEW.outlet_id <> test_outlet_id THEN
    current_hour := EXTRACT(HOUR FROM (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Jakarta'));
    IF current_hour >= 6 AND current_hour < 22 THEN
      RAISE EXCEPTION 'Penutupan petty cash (shift) hanya bisa dilakukan antara jam 22:00 malam hingga 06:00 pagi.';
    END IF;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
