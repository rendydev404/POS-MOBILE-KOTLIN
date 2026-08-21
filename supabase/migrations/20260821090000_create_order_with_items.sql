-- Order dan baris pesanannya HARUS mendarat di database sebagai satu kesatuan.
--
-- Sebelumnya klien mengirim dua request terpisah: POST /orders lalu menyusul
-- POST /order_items di coroutine latar belakang. Di antara keduanya ada jendela
-- waktu di mana order sudah ada di server TANPA satu pun barisnya. Siapa pun
-- yang membaca di jendela itu — device lain, dashboard web, atau device itu
-- sendiri yang me-resync karena event realtime tabel `orders` — melihat pesanan
-- dengan total_amount terisi tapi daftar menunya KOSONG.
--
-- Snapshot kosong itu lalu menetap permanen di Room: app hanya berlangganan
-- realtime tabel `orders`, bukan `order_items`, jadi tidak ada event susulan
-- yang memicu perbaikan. Gejala yang dilaporkan kasir: kartu order menampilkan
-- harga tapi menunya hilang, dan tidak pulih dengan sendirinya.
--
-- Fungsi ini menutup jendela tersebut. Kedua insert berada di dalam satu
-- transaksi (fungsi plpgsql selalu berjalan dalam satu transaksi), sehingga
-- pesanan tidak pernah terlihat "setengah jadi" oleh pembaca mana pun: entah
-- order beserta seluruh barisnya yang terlihat, atau tidak ada sama sekali.
--
-- SECURITY INVOKER, BUKAN DEFINER: RLS `orders_insert_scoped` (outlet_id harus
-- ada di accessible_outlet_ids()) dan `order_items_insert_public` tetap berlaku
-- persis seperti saat klien menulis langsung lewat PostgREST. Fungsi ini hanya
-- menggabungkan dua penulisan yang sudah boleh dilakukan pemanggil — tidak ada
-- hak baru yang diberikan, jadi tidak perlu pengecekan outlet tambahan di sini.
--
-- IDEMPOTEN. `id` order dibuat klien (UUID), dan jalur pengiriman ulang memang
-- ada: submitOrder menyerah setelah 5 detik lalu menyerahkan pesanan ke
-- OrderSyncEngine, yang mengirim payload yang sama sekali lagi. Percobaan kedua
-- atas order yang sebenarnya sudah mendarat tidak boleh gagal karena tabrakan
-- primary key — ia mengembalikan baris yang sudah ada, dan mengisikan barisnya
-- hanya kalau memang belum ada.

create or replace function public.create_order_with_items(
  p_order jsonb,
  p_items jsonb default '[]'::jsonb
)
returns jsonb
language plpgsql
volatile
security invoker
set search_path = public
as $$
declare
  v_order_id uuid := nullif(p_order->>'id', '')::uuid;
  v_order    public.orders;
begin
  if v_order_id is null then
    raise exception 'create_order_with_items: p_order.id wajib diisi';
  end if;

  -- Kolom yang TIDAK disebut di sini sengaja dibiarkan memakai default tabel
  -- (cancellation_status 'none', sales_source, is_endorse, dst), persis seperti
  -- perilaku insert PostgREST yang digantikan fungsi ini.
  --
  -- order_number ikut dikirim hanya demi kesetaraan dengan payload lama; nilai
  -- itu tetap ditimpa trigger BEFORE INSERT `trg_assign_order_number`, dan
  -- nomor hasil trigger itulah yang dikembalikan ke klien untuk dicetak.
  insert into public.orders (
    id, order_number, outlet_id, customer_name, status, source, payment_method,
    discount_amount, promo_subsidy, total_amount, amount_received, change_amount,
    created_at, channel, pickup_time, release_time, cashier_name,
    pos_client, is_offline_sync
  )
  select
    v_order_id,
    coalesce((p_order->>'order_number')::int, 0),
    (p_order->>'outlet_id')::uuid,
    p_order->>'customer_name',
    coalesce(p_order->>'status', 'pending'),
    coalesce(p_order->>'source', 'pos'),
    p_order->>'payment_method',
    coalesce((p_order->>'discount_amount')::numeric, 0),
    coalesce((p_order->>'promo_subsidy')::int, 0),
    (p_order->>'total_amount')::numeric,
    (p_order->>'amount_received')::numeric,
    (p_order->>'change_amount')::numeric,
    coalesce((p_order->>'created_at')::timestamptz, now()),
    p_order->>'channel',
    (p_order->>'pickup_time')::timestamptz,
    (p_order->>'release_time')::timestamptz,
    p_order->>'cashier_name',
    coalesce(p_order->>'pos_client', 'native'),
    coalesce((p_order->>'is_offline_sync')::boolean, false)
  on conflict (id) do nothing
  returning * into v_order;

  -- Kosong berarti baris dengan id ini sudah ada: percobaan sebelumnya
  -- sebenarnya sukses di server, hanya responsnya yang tidak sampai ke klien.
  if v_order.id is null then
    select * into v_order from public.orders where id = v_order_id;

    if v_order.id is null then
      -- Tidak ter-insert DAN tidak ditemukan: baris itu memang tidak boleh
      -- ditulis pemanggil (RLS). Jangan lanjut menulis order_items yatim.
      raise exception 'create_order_with_items: order % tidak tersimpan (kemungkinan ditolak RLS)', v_order_id;
    end if;
  end if;

  -- Dijaga supaya kiriman ulang tidak menggandakan baris pesanan yang sudah
  -- lengkap. Pengecekan "belum ada sama sekali", bukan per-baris: satu order
  -- selalu dikirim beserta SELURUH barisnya sekaligus, tidak pernah sebagian.
  if not exists (select 1 from public.order_items where order_id = v_order_id) then
    insert into public.order_items (
      order_id, menu_item_id, menu_item_name, quantity, unit_price, subtotal
    )
    select
      v_order_id,
      nullif(it->>'menu_item_id', '')::uuid,
      it->>'menu_item_name',
      (it->>'quantity')::int,
      (it->>'unit_price')::numeric,
      (it->>'subtotal')::numeric
    from jsonb_array_elements(coalesce(p_items, '[]'::jsonb)) as it;
  end if;

  return to_jsonb(v_order);
end $$;

-- Hanya sesi yang sudah login. EXECUTE bawaan PostgreSQL diberikan ke PUBLIC,
-- jadi harus dicabut eksplisit — pola yang sama dipakai pos_revenue_summary.
revoke all on function public.create_order_with_items(jsonb, jsonb) from public, anon;
grant execute on function public.create_order_with_items(jsonb, jsonb) to authenticated;
