-- Push pembatalan lewat pipeline send-fcm yang sudah aktif. Trigger INSERT
-- pesanan baru tetap memakai trigger_new_order_notification; fungsi terpisah
-- ini hanya menangani transisi pertama menuju status cancelled agar tidak
-- mengirim push ganda pada UPDATE kolom lain.

CREATE OR REPLACE FUNCTION public.handle_order_cancelled_notification()
RETURNS TRIGGER AS $$
BEGIN
  PERFORM net.http_post(
      url := (SELECT decrypted_secret FROM vault.decrypted_secrets WHERE name = 'fcm_webhook_url' LIMIT 1),
      headers := json_build_object(
        'Content-Type', 'application/json',
        'Authorization', 'Bearer ' || (SELECT decrypted_secret FROM vault.decrypted_secrets WHERE name = 'fcm_webhook_secret' LIMIT 1)
      )::jsonb,
      body := json_build_object(
        'type', 'order_cancelled',
        'record', row_to_json(NEW)
      )::jsonb
  );

  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

DROP TRIGGER IF EXISTS trigger_order_cancelled_notification ON public.orders;
CREATE TRIGGER trigger_order_cancelled_notification
AFTER UPDATE OF status ON public.orders
FOR EACH ROW
WHEN (NEW.status = 'cancelled' AND OLD.status IS DISTINCT FROM 'cancelled')
EXECUTE FUNCTION public.handle_order_cancelled_notification();
