-- Fires the notify-order Edge Function when:
--   (a) a new order is INSERTed with source in ('kiosk', 'online'), or
--   (b) an existing order's status changes to 'cancelled'.
-- Uses pg_net so the trigger does not block the write while the HTTP call
-- is in flight (net.http_post queues the request and returns immediately).

CREATE EXTENSION IF NOT EXISTS pg_net;

CREATE OR REPLACE FUNCTION public.notify_order_push()
RETURNS TRIGGER AS $$
DECLARE
  event_type TEXT;
  payload JSONB;
BEGIN
  IF TG_OP = 'INSERT' AND NEW.source IN ('kiosk', 'online') THEN
    event_type := 'new_order';
  ELSIF TG_OP = 'UPDATE' AND NEW.status = 'cancelled' AND OLD.status IS DISTINCT FROM 'cancelled' THEN
    event_type := 'order_cancelled';
  ELSE
    RETURN NEW;
  END IF;

  payload := jsonb_build_object(
    'type', event_type,
    'order_id', NEW.id,
    'order_number', NEW.order_number,
    'outlet_id', NEW.outlet_id,
    'customer_name', NEW.customer_name,
    'total_amount', NEW.total_amount
  );

  PERFORM net.http_post(
    url := 'https://khpkoreaaucvyqfhynfq.supabase.co/functions/v1/notify-order',
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'Authorization', 'Bearer ' || current_setting('app.settings.service_role_key', true)
    ),
    body := payload
  );

  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

DROP TRIGGER IF EXISTS trg_notify_order_push ON public.orders;
CREATE TRIGGER trg_notify_order_push
  AFTER INSERT OR UPDATE ON public.orders
  FOR EACH ROW EXECUTE FUNCTION public.notify_order_push();

-- One-time setup this migration cannot do for you (needs the actual secret
-- value, which must not live in a file that gets committed): run this once
-- in the SQL Editor, substituting your real service_role key from
-- Project Settings > API > service_role secret:
--
--   ALTER DATABASE postgres SET "app.settings.service_role_key" TO '<service_role_key>';
--
-- Then reconnect (or wait for the next session) for current_setting() to see it.
