-- Control-plane only: this table contains session metadata, never image/video data.
CREATE TABLE IF NOT EXISTS public.camera_sessions (
  outlet_id uuid PRIMARY KEY REFERENCES public.outlets(id) ON DELETE CASCADE,
  staff_id uuid NOT NULL REFERENCES public.outlet_staff(id) ON DELETE RESTRICT,
  room_name text NOT NULL UNIQUE,
  status text NOT NULL DEFAULT 'live' CHECK (status IN ('live', 'error', 'stopped')),
  started_at timestamptz NOT NULL DEFAULT now(),
  last_heartbeat_at timestamptz NOT NULL DEFAULT now(),
  error_message text,
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE OR REPLACE FUNCTION public.set_camera_session_updated_at()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS set_camera_session_updated_at ON public.camera_sessions;
CREATE TRIGGER set_camera_session_updated_at
BEFORE UPDATE ON public.camera_sessions
FOR EACH ROW EXECUTE FUNCTION public.set_camera_session_updated_at();

ALTER TABLE public.camera_sessions ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "camera_sessions_read_scoped" ON public.camera_sessions;
CREATE POLICY "camera_sessions_read_scoped"
ON public.camera_sessions FOR SELECT TO authenticated
USING (outlet_id IN (SELECT public.accessible_outlet_ids()));

-- The edge function uses service_role for writes after it verifies the caller.
-- No browser or POS client receives a direct INSERT/UPDATE policy.

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_publication_tables
    WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = 'camera_sessions'
  ) THEN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.camera_sessions;
  END IF;
END $$;
