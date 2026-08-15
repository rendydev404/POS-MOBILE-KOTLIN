-- Auto-update native membutuhkan `value` sebagai objek pada payload REST/realtime.
-- Tabel lama memakai TEXT. Setting selain app_update dipertahankan sebagai JSON
-- string agar perilaku web tetap sama.

begin;

alter table public.global_settings
alter column value type jsonb
using (
    case
        when key = 'app_update' and value is not null then value::jsonb
        else to_jsonb(value)
    end
);

-- Trigger BOM sebelumnya membaca kolom TEXT langsung ke variabel TEXT. Setelah
-- kolom menjadi JSONB, ekstrak scalar string tanpa tanda kutip JSON.
do $migration$
declare
    function_definition text;
    updated_definition text;
begin
    select pg_get_functiondef(p.oid)
    into function_definition
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and p.proname = 'trg_process_bom_stok'
      and p.prokind = 'f'
    limit 1;

    if function_definition is null then
        raise exception 'Function public.trg_process_bom_stok was not found';
    end if;

    updated_definition := replace(
        function_definition,
        'SELECT value INTO v_allowed_outlets FROM public.global_settings',
        'SELECT value #>> ''{}'' INTO v_allowed_outlets FROM public.global_settings'
    );

    if updated_definition = function_definition then
        raise exception 'Expected global_settings lookup was not found in trg_process_bom_stok';
    end if;

    execute updated_definition;
end
$migration$;

commit;
