-- Bucket publik untuk APK auto-update POS native.
-- Migrasi ini idempoten dan dijalankan manual melalui Supabase SQL Editor.

insert into storage.buckets (id, name, public)
values ('app-releases', 'app-releases', true)
on conflict (id) do update
set name = excluded.name,
    public = excluded.public;

drop policy if exists "app_releases_public_read" on storage.objects;

create policy "app_releases_public_read"
on storage.objects
for select
to public
using (bucket_id = 'app-releases');
