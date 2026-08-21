-- Device menghapus token FCM-nya sendiri saat logout. Edge Function memakai
-- service_role dan tetap dapat membersihkan token mati tanpa policy tambahan.

DROP POLICY IF EXISTS "fcm_tokens_delete_self" ON public.fcm_tokens;
CREATE POLICY "fcm_tokens_delete_self" ON public.fcm_tokens
  FOR DELETE TO authenticated
  USING (staff_id = auth.uid());

GRANT DELETE ON public.fcm_tokens TO authenticated;
