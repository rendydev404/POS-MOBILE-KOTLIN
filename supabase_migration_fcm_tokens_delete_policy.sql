-- Adds a DELETE policy for fcm_tokens so a staff member's device can
-- deregister its own token on logout. service_role (used by the
-- notify-order edge function to prune UNREGISTERED/INVALID_ARGUMENT
-- tokens) already bypasses RLS, so no separate policy is needed for that.

DROP POLICY IF EXISTS "fcm_tokens_delete_self" ON public.fcm_tokens;
CREATE POLICY "fcm_tokens_delete_self" ON public.fcm_tokens
  FOR DELETE TO authenticated
  USING (staff_id = auth.uid());

GRANT DELETE ON public.fcm_tokens TO authenticated;
