-- Migration file for FCM Push Notifications triggers

-- IMPORTANT: Database Administrator Configuration
-- Because setting custom variables via ALTER DATABASE is restricted in Supabase,
-- we use Supabase Vault to store the webhook URL and Secret Key.
-- 
-- Run this in your SQL Editor BEFORE applying these triggers:
-- 
-- CREATE EXTENSION IF NOT EXISTS supabase_vault;
-- SELECT vault.create_secret('https://khpkoreaaucvyqfhynfq.supabase.co/functions/v1/send-fcm', 'fcm_webhook_url');
-- SELECT vault.create_secret('YOUR_SERVICE_ROLE_KEY', 'fcm_webhook_secret');

-- Ensure the pg_net extension is enabled
CREATE EXTENSION IF NOT EXISTS pg_net;

-- Create the trigger function for orders
CREATE OR REPLACE FUNCTION public.handle_new_order_notification()
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
  -- URL and Secret are retrieved from Supabase Vault
  PERFORM net.http_post(
      url:=(SELECT decrypted_secret FROM vault.decrypted_secrets WHERE name = 'fcm_webhook_url' LIMIT 1),
      headers:=json_build_object(
        'Content-Type', 'application/json', 
        'Authorization', 'Bearer ' || (SELECT decrypted_secret FROM vault.decrypted_secrets WHERE name = 'fcm_webhook_secret' LIMIT 1)
      )::jsonb,
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
