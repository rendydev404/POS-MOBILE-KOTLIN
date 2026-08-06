-- Realtime (postgres_changes) hanya mengirim event untuk tabel yang ada di
-- publication `supabase_realtime`. Tanpa ini, WebSocket app tersambung tapi
-- tidak pernah menerima apa-apa.

DO $$
DECLARE t text;
BEGIN
  FOR t IN SELECT unnest(ARRAY[
    'orders', 'order_items', 'owner_messages',
    'cancellation_requests', 'bypass_requests', 'daily_sales_targets'
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

-- REPLICA IDENTITY default hanya mengirim primary key pada UPDATE/DELETE, jadi
-- payload pembatalan kehilangan outlet_id dan difilter habis di sisi app.
ALTER TABLE public.orders REPLICA IDENTITY FULL;
ALTER TABLE public.cancellation_requests REPLICA IDENTITY FULL;

-- Owner bisa mengedit/menarik pesan; app juga harus dapat push-nya.
-- Butuh handle_new_owner_message_notification() dari migrasi 20260803.
DO $$
BEGIN
  IF to_regprocedure('public.handle_new_owner_message_notification()') IS NOT NULL THEN
    DROP TRIGGER IF EXISTS trigger_owner_message_update_notification ON public.owner_messages;
    CREATE TRIGGER trigger_owner_message_update_notification
    AFTER UPDATE ON public.owner_messages
    FOR EACH ROW
    WHEN (OLD.title IS DISTINCT FROM NEW.title OR OLD.body IS DISTINCT FROM NEW.body)
    EXECUTE FUNCTION public.handle_new_owner_message_notification();
  END IF;
END $$;
