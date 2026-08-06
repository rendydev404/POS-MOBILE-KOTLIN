# Push Notification (FCM) Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Menambahkan sistem Push Notification (FCM) via Supabase Edge Function dan Database Trigger untuk notifikasi pesanan masuk dan pesan owner secara realtime di latar belakang.

**Architecture:** Kita akan membuat Edge Function `send-fcm` dengan Deno yang mengirim push notifikasi via API Firebase HTTP v1. Kemudian kita akan membuat file SQL berisi Database Trigger untuk memanggil Edge Function ini saat ada baris baru di tabel `orders` dan `owner_messages`.

**Tech Stack:** Supabase Edge Functions (Deno, TypeScript), PostgreSQL Triggers, `pg_net` (opsional untuk trigger webhook), Firebase Admin SDK.

---

### Task 1: Setup File Supabase Edge Function `send-fcm`

**Files:**
- Create: `supabase/functions/send-fcm/index.ts`
- Create: `supabase/functions/send-fcm/deno.json`

**Step 1: Write the Edge Function boilerplate**

Kita akan menyiapkan struktur dasar Deno HTTP handler.

```typescript
// supabase/functions/send-fcm/index.ts
import { serve } from "https://deno.land/std@0.177.0/http/server.ts";

serve(async (req) => {
  try {
    const payload = await req.json();
    return new Response(JSON.stringify({ success: true, payload }), {
      headers: { "Content-Type": "application/json" },
    });
  } catch (err) {
    return new Response(JSON.stringify({ error: err.message }), { status: 400 });
  }
});
```

**Step 2: Commit**

```bash
git add supabase/functions/send-fcm/index.ts
git commit -m "feat: scaffold send-fcm edge function"
```

### Task 2: Implementasi Logika Firebase & Ambil Token `fcm_tokens`

**Files:**
- Modify: `supabase/functions/send-fcm/index.ts`

**Step 1: Implementasi API Supabase & Firebase**
Update `index.ts` untuk menggunakan `@supabase/supabase-js` mengambil `fcm_tokens` berdasarkan `outlet_id`, dan menggunakan Google JWT untuk memanggil endpoint FCM.

```typescript
// (Deno script lengkap akan ditulis di fase eksekusi)
```

**Step 2: Commit**

```bash
git add supabase/functions/send-fcm/index.ts
git commit -m "feat: implement logic to fetch tokens and send FCM push"
```

### Task 3: Buat SQL Script untuk Database Triggers

**Files:**
- Create: `supabase/migrations/20260803_fcm_triggers.sql` (atau di-apply manual di DB)

**Step 1: Write SQL Triggers**

Kita akan menulis file SQL untuk membuat trigger `AFTER INSERT` di tabel `orders` dan `owner_messages` yang menembakkan HTTP POST webhook (lewat `net.http_post` atau fitur Webhook bawaan Supabase) ke fungsi `send-fcm`.

```sql
-- Trigger untuk tabel orders
CREATE OR REPLACE FUNCTION notify_new_order_fcm() RETURNS trigger AS $$
BEGIN
  -- HTTP POST ke edge function URL (disesuaikan di production)
  PERFORM net.http_post(
    url := 'https://[PROJECT_REF].supabase.co/functions/v1/send-fcm',
    headers := '{"Content-Type": "application/json", "Authorization": "Bearer [SERVICE_ROLE_KEY]"}'::jsonb,
    body := json_build_object('type', 'new_order', 'record', row_to_json(NEW))::jsonb
  );
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE TRIGGER on_new_order_fcm
  AFTER INSERT ON public.orders
  FOR EACH ROW EXECUTE FUNCTION notify_new_order_fcm();
```

**Step 2: Commit**

```bash
git add supabase/migrations/20260803_fcm_triggers.sql
git commit -m "feat: add sql triggers for push notifications"
```
