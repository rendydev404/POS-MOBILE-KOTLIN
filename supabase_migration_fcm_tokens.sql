-- fcm_tokens: registers native Android device push tokens (Firebase Cloud Messaging)
-- Purely additive — does not touch any existing table, so the web app is unaffected.

CREATE TABLE IF NOT EXISTS public.fcm_tokens (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  staff_id UUID NOT NULL REFERENCES public.outlet_staff(id) ON DELETE CASCADE,
  outlet_id UUID REFERENCES public.outlets(id) ON DELETE SET NULL,
  token TEXT NOT NULL,
  platform TEXT NOT NULL DEFAULT 'android',
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (staff_id, token)
);

CREATE INDEX IF NOT EXISTS idx_fcm_tokens_outlet ON public.fcm_tokens(outlet_id);

ALTER TABLE public.fcm_tokens ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "fcm_tokens_upsert_self" ON public.fcm_tokens;
CREATE POLICY "fcm_tokens_upsert_self" ON public.fcm_tokens
  FOR INSERT TO authenticated
  WITH CHECK (staff_id = auth.uid());

DROP POLICY IF EXISTS "fcm_tokens_update_self" ON public.fcm_tokens;
CREATE POLICY "fcm_tokens_update_self" ON public.fcm_tokens
  FOR UPDATE TO authenticated
  USING (staff_id = auth.uid())
  WITH CHECK (staff_id = auth.uid());

-- Server-side sender (Edge Function using the service role) needs to read all tokens
-- for an outlet when a new order comes in — service_role bypasses RLS by default,
-- so no explicit SELECT policy for it is required here. No policy is granted to
-- 'authenticated' for SELECT since staff devices never need to read other tokens.

GRANT SELECT, INSERT, UPDATE ON public.fcm_tokens TO authenticated;

-- Auto-bump updated_at on token refresh (upsert via ON CONFLICT DO UPDATE from the app).
CREATE OR REPLACE FUNCTION public.touch_fcm_token_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_touch_fcm_token ON public.fcm_tokens;
CREATE TRIGGER trg_touch_fcm_token
  BEFORE UPDATE ON public.fcm_tokens
  FOR EACH ROW EXECUTE FUNCTION public.touch_fcm_token_updated_at();
