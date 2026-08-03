-- Migration file for FCM Push Notifications triggers

-- Ensure the pg_net extension is enabled
CREATE EXTENSION IF NOT EXISTS pg_net;

-- Create the trigger function for orders
CREATE OR REPLACE FUNCTION public.handle_new_order_notification()
RETURNS TRIGGER AS $$
BEGIN
  -- We use pg_net.http_post to call the edge function asynchronously
  -- Replace [PROJECT_REF] and [SERVICE_ROLE_KEY] with your actual project ref and service role key
  PERFORM net.http_post(
      url:='https://[PROJECT_REF].supabase.co/functions/v1/send-fcm',
      headers:='{"Content-Type": "application/json", "Authorization": "Bearer [SERVICE_ROLE_KEY]"}'::jsonb,
      body:=json_build_object(
          'type', 'new_order',
          'record', row_to_json(NEW)
      )::jsonb
  );
  
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Create the trigger on orders
DROP TRIGGER IF EXISTS trigger_new_order_notification ON public.orders;
CREATE TRIGGER trigger_new_order_notification
AFTER INSERT ON public.orders
FOR EACH ROW
EXECUTE FUNCTION public.handle_new_order_notification();

-- Create the trigger function for owner messages
-- (Adjust table name if it's different from owner_messages)
CREATE OR REPLACE FUNCTION public.handle_new_owner_message_notification()
RETURNS TRIGGER AS $$
BEGIN
  -- We use pg_net.http_post to call the edge function asynchronously
  -- Replace [PROJECT_REF] and [SERVICE_ROLE_KEY] with your actual project ref and service role key
  PERFORM net.http_post(
      url:='https://[PROJECT_REF].supabase.co/functions/v1/send-fcm',
      headers:='{"Content-Type": "application/json", "Authorization": "Bearer [SERVICE_ROLE_KEY]"}'::jsonb,
      body:=json_build_object(
          'type', 'owner_message',
          'record', row_to_json(NEW)
      )::jsonb
  );
  
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Create the trigger on owner_messages
-- Note: If your owner messages table is named differently, update the ON clause
DROP TRIGGER IF EXISTS trigger_new_owner_message_notification ON public.owner_messages;
CREATE TRIGGER trigger_new_owner_message_notification
AFTER INSERT ON public.owner_messages
FOR EACH ROW
EXECUTE FUNCTION public.handle_new_owner_message_notification();
