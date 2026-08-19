-- Add petty cash tables and shifts to realtime publication
DO $$
DECLARE t text;
BEGIN
  FOR t IN SELECT unnest(ARRAY[
    'petty_cash_topups', 'petty_cash_expenses', 'shifts'
  ])
  LOOP
    IF to_regclass('public.' || t) IS NOT NULL
       AND NOT EXISTS (
         SELECT 1 FROM pg_publication_tables
         WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = t
       )
    THEN
      EXECUTE format('ALTER PUBLICATION supabase_realtime ADD TABLE public.%I', t);
    END IF;
  END LOOP;
END $$;

-- Set REPLICA IDENTITY FULL for petty_cash_topups and petty_cash_expenses
-- so that UPDATE events contain the full row, particularly outlet_id.
ALTER TABLE public.petty_cash_topups REPLICA IDENTITY FULL;
ALTER TABLE public.petty_cash_expenses REPLICA IDENTITY FULL;
ALTER TABLE public.shifts REPLICA IDENTITY FULL;

-- Ensure pg_net is available
CREATE EXTENSION IF NOT EXISTS pg_net;

-- Create the FCM notification trigger for petty_cash
CREATE OR REPLACE FUNCTION public.handle_new_petty_cash_notification()
RETURNS TRIGGER AS $$
BEGIN
  -- We use pg_net.http_post to call the edge function asynchronously
  -- URL and Secret are retrieved from Supabase Vault
  PERFORM net.http_post(
      url:=(SELECT decrypted_secret FROM vault.decrypted_secrets WHERE name = 'fcm_webhook_url' LIMIT 1),
      headers:=json_build_object(
        'Content-Type', 'application/json', 
        'Authorization', 'Bearer ' || (SELECT decrypted_secret FROM vault.decrypted_secrets WHERE name = 'fcm_webhook_secret' LIMIT 1)
      )::jsonb,
      body:=json_build_object(
          'type', 'petty_cash',
          'record', row_to_json(NEW)
      )::jsonb
  );
  
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Trigger for petty_cash_topups on INSERT
DROP TRIGGER IF EXISTS trigger_new_petty_cash_topup_notification ON public.petty_cash_topups;
CREATE TRIGGER trigger_new_petty_cash_topup_notification
AFTER INSERT ON public.petty_cash_topups
FOR EACH ROW
EXECUTE FUNCTION public.handle_new_petty_cash_notification();

-- Trigger for petty_cash_topups on UPDATE (only when status changes)
DROP TRIGGER IF EXISTS trigger_update_petty_cash_topup_notification ON public.petty_cash_topups;
CREATE TRIGGER trigger_update_petty_cash_topup_notification
AFTER UPDATE ON public.petty_cash_topups
FOR EACH ROW
WHEN (OLD.status IS DISTINCT FROM NEW.status)
EXECUTE FUNCTION public.handle_new_petty_cash_notification();
