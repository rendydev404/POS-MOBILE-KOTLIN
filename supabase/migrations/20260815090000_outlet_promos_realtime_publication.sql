-- POS native app needs instant (WebSocket, no polling) promo updates: when admin
-- edits a promo on the web dashboard, the cashier screen must reflect it immediately
-- via Supabase Realtime postgres_changes on outlet_promos, filtered per outlet_id.
-- Idempotent: safe to re-run if the table is already in the publication.
do $$
begin
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and tablename = 'outlet_promos'
  ) then
    alter publication supabase_realtime add table outlet_promos;
  end if;
end $$;
