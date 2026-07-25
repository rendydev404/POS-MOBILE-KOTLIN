# Push Notification (FCM Server Sender + Tap Deep-Link) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the existing FCM client plumbing actually receive pushes — add the missing server-side sender (Supabase Edge Function + DB trigger), switch to a data-only payload so the alarm fires in background, and make tapping a notification open the Dashboard with the order highlighted.

**Architecture:** A Postgres trigger on `orders` fires `net.http_post` (async, non-blocking) to a Supabase Edge Function `notify-order` on INSERT (source kiosk/online) and on UPDATE to status `cancelled`. The function loads that outlet's `fcm_tokens`, exchanges the Firebase service-account JSON for a short-lived OAuth2 token, and POSTs a **data-only** FCM v1 message to each device (tokens FCM reports as dead get deleted). The Android client's `POSFirebaseMessagingService` plays the alert tone/vibration and shows a system notification with a `PendingIntent` carrying `order_id`; `MainActivity` (now `launchMode="singleTop"`) reads that extra, forces the Dashboard tab, and `DashboardViewModel` briefly highlights + auto-scrolls to that order card.

**Tech Stack:** Supabase Postgres (`pg_net` extension, PL/pgSQL trigger), Supabase Edge Functions (Deno/TypeScript, `Deno.serve`), Firebase Cloud Messaging HTTP v1 API, Kotlin/Jetpack Compose (existing Android app).

## Global Constraints

- FCM messages MUST be data-only (no top-level `notification` key) — a `notification` block makes Android show the system tray notification itself without invoking `onMessageReceived()` while backgrounded, which would silently kill the alarm tone/vibration requirement from the spec (section 2.B).
- Supabase project ref is `khpkoreaaucvyqfhynfq` (from `SupabaseClient.BASE_URL`) — Edge Function URL is `https://khpkoreaaucvyqfhynfq.supabase.co/functions/v1/notify-order`.
- Firebase project ID is `pos-native-de856` (from `app/google-services.json`).
- This codebase has zero existing automated test infrastructure for the Kotlin app (no `app/src/test`, no `app/src/androidTest`). Do not introduce a test framework mid-feature. Kotlin tasks are verified by building the app and manual on-device checks, matching current project practice. The Deno Edge Function code is new and has no such constraint — its pure helper functions get real `deno test` coverage.
- The Firebase service-account private key must never be written into any file inside this repo. It is only ever read at runtime from the Supabase secret `FCM_SERVICE_ACCOUNT`.
- Existing PostgREST filter convention: query params are passed as literal filter strings like `"eq.$value"` (see `SupabaseApi.kt` `updateOrderStatus`), not raw values — follow this in new endpoints.

---

### Task 1: FCM OAuth2 + send helper (`fcm.ts`)

**Files:**
- Create: `supabase/functions/notify-order/fcm.ts`
- Test: `supabase/functions/notify-order/fcm_test.ts`

**Interfaces:**
- Consumes: Deno runtime env vars `FCM_SERVICE_ACCOUNT` (JSON string), `crypto.subtle` (Web Crypto, built into Deno).
- Produces: `getAccessToken(): Promise<string>`, `sendToToken(params: { accessToken: string; projectId: string; token: string; data: Record<string, string> }): Promise<FcmSendResult>`, `buildUnsignedJwt(sa: ServiceAccount, nowSeconds: number): string` (exported for testing), and the type `FcmSendResult = { ok: boolean; shouldRemoveToken: boolean; errorCode?: string }`.

- [ ] **Step 1: Write the failing test for the pure JWT-claims builder**

```typescript
// supabase/functions/notify-order/fcm_test.ts
import { assertEquals } from "https://deno.land/std@0.224.0/testing/asserts.ts";
import { buildUnsignedJwt } from "./fcm.ts";

const FAKE_SA = {
  client_email: "test@pos-native-de856.iam.gserviceaccount.com",
  private_key: "unused-in-this-test",
  token_uri: "https://oauth2.googleapis.com/token",
};

function decodeSegment(segment: string): Record<string, unknown> {
  const padded = segment.replace(/-/g, "+").replace(/_/g, "/");
  const normalized = padded + "=".repeat((4 - (padded.length % 4)) % 4);
  return JSON.parse(atob(normalized));
}

Deno.test("buildUnsignedJwt encodes header and claims for the messaging scope", () => {
  const nowSeconds = 1_700_000_000;
  const jwt = buildUnsignedJwt(FAKE_SA, nowSeconds);
  const parts = jwt.split(".");
  assertEquals(parts.length, 2, "unsigned JWT is header.claims (signature appended later)");

  const header = decodeSegment(parts[0]);
  assertEquals(header.alg, "RS256");
  assertEquals(header.typ, "JWT");

  const claims = decodeSegment(parts[1]);
  assertEquals(claims.iss, FAKE_SA.client_email);
  assertEquals(claims.scope, "https://www.googleapis.com/auth/firebase.messaging");
  assertEquals(claims.aud, FAKE_SA.token_uri);
  assertEquals(claims.iat, nowSeconds);
  assertEquals(claims.exp, nowSeconds + 3600);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `deno test supabase/functions/notify-order/fcm_test.ts`
Expected: FAIL — `fcm.ts` does not exist yet (module not found).

- [ ] **Step 3: Write `fcm.ts`**

```typescript
// supabase/functions/notify-order/fcm.ts
// OAuth2 service-account exchange + FCM HTTP v1 send. Kept separate from
// index.ts so the pure claims-building logic can be unit-tested without a
// live network call or a real private key.

interface ServiceAccount {
  client_email: string;
  private_key: string;
  token_uri: string;
}

interface CachedToken {
  accessToken: string;
  expiresAt: number; // epoch millis
}

let cached: CachedToken | null = null;

function getServiceAccount(): ServiceAccount {
  const raw = Deno.env.get("FCM_SERVICE_ACCOUNT");
  if (!raw) throw new Error("FCM_SERVICE_ACCOUNT secret is not set");
  return JSON.parse(raw);
}

function base64url(input: Uint8Array | string): string {
  const bytes = typeof input === "string" ? new TextEncoder().encode(input) : input;
  let str = "";
  for (const byte of bytes) str += String.fromCharCode(byte);
  return btoa(str).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

/** Exported for testing — pure, no network/crypto side effects. */
export function buildUnsignedJwt(sa: ServiceAccount, nowSeconds: number): string {
  const header = { alg: "RS256", typ: "JWT" };
  const claims = {
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: sa.token_uri,
    iat: nowSeconds,
    exp: nowSeconds + 3600,
  };
  return `${base64url(JSON.stringify(header))}.${base64url(JSON.stringify(claims))}`;
}

async function signJwt(unsigned: string, privateKeyPem: string): Promise<string> {
  const pemBody = privateKeyPem
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace("-----END PRIVATE KEY-----", "")
    .replace(/\s/g, "");
  const binaryDer = Uint8Array.from(atob(pemBody), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey(
    "pkcs8",
    binaryDer,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(unsigned),
  );
  return `${unsigned}.${base64url(new Uint8Array(signature))}`;
}

/** Returns a cached access token when it still has >60s of life, else refreshes it. */
export async function getAccessToken(): Promise<string> {
  const now = Date.now();
  if (cached && cached.expiresAt > now + 60_000) {
    return cached.accessToken;
  }
  const sa = getServiceAccount();
  const nowSeconds = Math.floor(now / 1000);
  const unsigned = buildUnsignedJwt(sa, nowSeconds);
  const jwt = await signJwt(unsigned, sa.private_key);

  const res = await fetch(sa.token_uri, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: jwt,
    }),
  });
  if (!res.ok) {
    throw new Error(`OAuth token exchange failed: ${res.status} ${await res.text()}`);
  }
  const json = await res.json();
  cached = {
    accessToken: json.access_token,
    expiresAt: now + (json.expires_in ?? 3300) * 1000,
  };
  return cached.accessToken;
}

export interface FcmSendResult {
  ok: boolean;
  shouldRemoveToken: boolean;
  errorCode?: string;
}

/** Sends one data-only FCM v1 message. Never throws on FCM-level errors — caller reads `ok`. */
export async function sendToToken(params: {
  accessToken: string;
  projectId: string;
  token: string;
  data: Record<string, string>;
}): Promise<FcmSendResult> {
  const res = await fetch(
    `https://fcm.googleapis.com/v1/projects/${params.projectId}/messages:send`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${params.accessToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        message: {
          token: params.token,
          data: params.data,
          android: { priority: "high" },
        },
      }),
    },
  );

  if (res.ok) {
    return { ok: true, shouldRemoveToken: false };
  }

  const errorJson = await res.json().catch(() => null);
  const errorCode: string | undefined = errorJson?.error?.details?.find(
    (d: { errorCode?: string }) => d.errorCode,
  )?.errorCode;
  const shouldRemoveToken = errorCode === "UNREGISTERED" || errorCode === "INVALID_ARGUMENT";
  return { ok: false, shouldRemoveToken, errorCode };
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `deno test supabase/functions/notify-order/fcm_test.ts`
Expected: PASS (1 test).

- [ ] **Step 5: Commit**

```bash
git add supabase/functions/notify-order/fcm.ts supabase/functions/notify-order/fcm_test.ts
git commit -m "feat: add FCM OAuth2 + send helper for notify-order edge function"
```

---

### Task 2: `notify-order` Edge Function handler (`index.ts`)

**Files:**
- Create: `supabase/functions/notify-order/index.ts`

**Interfaces:**
- Consumes: `getAccessToken()`, `sendToToken()`, `FcmSendResult` from Task 1's `fcm.ts`; env vars `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY` (both auto-injected into every Supabase Edge Function), `FCM_PROJECT_ID` (optional, defaults to `pos-native-de856`).
- Produces: An HTTP endpoint that accepts `POST` with JSON body `{ type, order_id, order_number, outlet_id, customer_name, total_amount }` — this exact shape is what Task 3's trigger must send.

- [ ] **Step 1: Write `index.ts`**

```typescript
// supabase/functions/notify-order/index.ts
import { getAccessToken, sendToToken, type FcmSendResult } from "./fcm.ts";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const FCM_PROJECT_ID = Deno.env.get("FCM_PROJECT_ID") ?? "pos-native-de856";

interface OrderPushPayload {
  type: "new_order" | "order_cancelled";
  order_id: string;
  order_number: number;
  outlet_id: string;
  customer_name: string | null;
  total_amount: number;
}

interface FcmTokenRow {
  id: string;
  token: string;
}

function buildTitleAndBody(payload: OrderPushPayload): { title: string; body: string } {
  const numberLabel = `No. ${payload.order_number}`;
  const nameLabel = payload.customer_name ?? "Pelanggan";
  if (payload.type === "order_cancelled") {
    return {
      title: "Pesanan Dibatalkan",
      body: `${numberLabel} — ${nameLabel} dibatalkan.`,
    };
  }
  return {
    title: "Pesanan Baru Masuk",
    body: `${numberLabel} — ${nameLabel} · Rp ${payload.total_amount.toLocaleString("id-ID")}`,
  };
}

async function fetchTokensForOutlet(outletId: string): Promise<FcmTokenRow[]> {
  const res = await fetch(
    `${SUPABASE_URL}/rest/v1/fcm_tokens?outlet_id=eq.${outletId}&select=id,token`,
    {
      headers: {
        apikey: SERVICE_ROLE_KEY,
        Authorization: `Bearer ${SERVICE_ROLE_KEY}`,
      },
    },
  );
  if (!res.ok) {
    throw new Error(`Failed to fetch fcm_tokens: ${res.status} ${await res.text()}`);
  }
  return await res.json();
}

async function deleteToken(tokenRowId: string): Promise<void> {
  await fetch(`${SUPABASE_URL}/rest/v1/fcm_tokens?id=eq.${tokenRowId}`, {
    method: "DELETE",
    headers: {
      apikey: SERVICE_ROLE_KEY,
      Authorization: `Bearer ${SERVICE_ROLE_KEY}`,
    },
  });
}

Deno.serve(async (req) => {
  if (req.method !== "POST") {
    return new Response("Method Not Allowed", { status: 405 });
  }

  let payload: OrderPushPayload;
  try {
    payload = await req.json();
  } catch {
    return new Response("Invalid JSON body", { status: 400 });
  }

  if (!payload.outlet_id || !payload.order_id || !payload.type) {
    return new Response("Missing required fields", { status: 400 });
  }

  const tokens = await fetchTokensForOutlet(payload.outlet_id);
  if (tokens.length === 0) {
    return new Response(JSON.stringify({ sent: 0, message: "no tokens for outlet" }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  }

  const accessToken = await getAccessToken();
  const { title, body } = buildTitleAndBody(payload);

  let sent = 0;
  let removed = 0;
  for (const row of tokens) {
    const result: FcmSendResult = await sendToToken({
      accessToken,
      projectId: FCM_PROJECT_ID,
      token: row.token,
      data: {
        type: payload.type,
        order_id: payload.order_id,
        order_number: String(payload.order_number),
        title,
        body,
      },
    });
    if (result.ok) {
      sent++;
    } else if (result.shouldRemoveToken) {
      await deleteToken(row.id);
      removed++;
    }
  }

  return new Response(JSON.stringify({ sent, removed, total: tokens.length }), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  });
});
```

- [ ] **Step 2: Type-check locally**

Run: `deno check supabase/functions/notify-order/index.ts`
Expected: No errors.

- [ ] **Step 3: Commit**

```bash
git add supabase/functions/notify-order/index.ts
git commit -m "feat: add notify-order edge function handler"
```

---

### Task 3: DB trigger to call the edge function on order INSERT/cancel

**Files:**
- Create: `supabase_migration_order_push_trigger.sql`

**Interfaces:**
- Consumes: `public.orders` columns `id, outlet_id, order_number, customer_name, total_amount, status, source` (all already exist per spec section 10).
- Produces: An `AFTER INSERT OR UPDATE` trigger that POSTs the exact JSON shape Task 2's `index.ts` expects.

- [ ] **Step 1: Write the migration file**

```sql
-- Fires the notify-order Edge Function when:
--   (a) a new order is INSERTed with source in ('kiosk', 'online'), or
--   (b) an existing order's status changes to 'cancelled'.
-- Uses pg_net so the trigger does not block the write while the HTTP call
-- is in flight (net.http_post queues the request and returns immediately).

CREATE EXTENSION IF NOT EXISTS pg_net;

CREATE OR REPLACE FUNCTION public.notify_order_push()
RETURNS TRIGGER AS $$
DECLARE
  event_type TEXT;
  payload JSONB;
BEGIN
  IF TG_OP = 'INSERT' AND NEW.source IN ('kiosk', 'online') THEN
    event_type := 'new_order';
  ELSIF TG_OP = 'UPDATE' AND NEW.status = 'cancelled' AND OLD.status IS DISTINCT FROM 'cancelled' THEN
    event_type := 'order_cancelled';
  ELSE
    RETURN NEW;
  END IF;

  payload := jsonb_build_object(
    'type', event_type,
    'order_id', NEW.id,
    'order_number', NEW.order_number,
    'outlet_id', NEW.outlet_id,
    'customer_name', NEW.customer_name,
    'total_amount', NEW.total_amount
  );

  PERFORM net.http_post(
    url := 'https://khpkoreaaucvyqfhynfq.supabase.co/functions/v1/notify-order',
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'Authorization', 'Bearer ' || current_setting('app.settings.service_role_key', true)
    ),
    body := payload
  );

  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

DROP TRIGGER IF EXISTS trg_notify_order_push ON public.orders;
CREATE TRIGGER trg_notify_order_push
  AFTER INSERT OR UPDATE ON public.orders
  FOR EACH ROW EXECUTE FUNCTION public.notify_order_push();

-- One-time setup this migration cannot do for you (needs the actual secret
-- value, which must not live in a file that gets committed): run this once
-- in the SQL Editor, substituting your real service_role key from
-- Project Settings > API > service_role secret:
--
--   ALTER DATABASE postgres SET "app.settings.service_role_key" TO '<service_role_key>';
--
-- Then reconnect (or wait for the next session) for current_setting() to see it.
```

- [ ] **Step 2: Commit**

```bash
git add supabase_migration_order_push_trigger.sql
git commit -m "feat: add DB trigger that calls notify-order on new/cancelled orders"
```

---

### Task 4: `fcm_tokens` DELETE policy

**Files:**
- Create: `supabase_migration_fcm_tokens_delete_policy.sql`

**Interfaces:**
- Consumes: existing `public.fcm_tokens` table from `supabase_migration_fcm_tokens.sql` (columns `id, staff_id, outlet_id, token`).
- Produces: RLS policy allowing an authenticated staff member to delete their own token rows — required by Task 5's client-side logout cleanup.

- [ ] **Step 1: Write the migration file**

```sql
-- Adds a DELETE policy for fcm_tokens so a staff member's device can
-- deregister its own token on logout. service_role (used by the
-- notify-order edge function to prune UNREGISTERED/INVALID_ARGUMENT
-- tokens) already bypasses RLS, so no separate policy is needed for that.

DROP POLICY IF EXISTS "fcm_tokens_delete_self" ON public.fcm_tokens;
CREATE POLICY "fcm_tokens_delete_self" ON public.fcm_tokens
  FOR DELETE TO authenticated
  USING (staff_id = auth.uid());

GRANT DELETE ON public.fcm_tokens TO authenticated;
```

- [ ] **Step 2: Commit**

```bash
git add supabase_migration_fcm_tokens_delete_policy.sql
git commit -m "feat: allow staff to delete their own fcm_tokens row"
```

---

### Task 5: Client — deregister FCM token on logout

**Files:**
- Modify: `app/src/main/java/com/sukashawarma/pos/data/remote/SupabaseApi.kt:124-131`
- Modify: `app/src/main/java/com/sukashawarma/pos/data/notification/FcmTokenRegistrar.kt`
- Modify: `app/src/main/java/com/sukashawarma/pos/presentation/login/LoginViewModel.kt:107-112`

**Interfaces:**
- Consumes: `SupabaseClient.api` (existing), `SessionTokenHolder` (existing) — the delete call MUST happen while the staff's access token is still set, since the RLS policy from Task 4 checks `staff_id = auth.uid()`.
- Produces: `FcmTokenRegistrar.unregisterCurrentToken(): suspend () -> Unit`, `SupabaseApi.deleteFcmToken(tokenFilter: String): Response<Void>`.

- [ ] **Step 1: Add the DELETE endpoint to `SupabaseApi.kt`**

In `app/src/main/java/com/sukashawarma/pos/data/remote/SupabaseApi.kt`, right after the existing `upsertFcmToken` (currently the last method in the interface, lines 124-131), add:

```kotlin
    // Deregisters this device's FCM token (called on logout so the outlet
    // doesn't keep receiving pushes for a device no longer staffed there).
    @DELETE("rest/v1/fcm_tokens")
    suspend fun deleteFcmToken(@Query("token") tokenFilter: String): Response<Void>
}
```

(Remove the old closing `}` from the previous last method and keep this one — `retrofit2.http.*` already covers `@DELETE`.)

- [ ] **Step 2: Add `unregisterCurrentToken()` to `FcmTokenRegistrar.kt`**

Replace the full file content with:

```kotlin
package com.sukashawarma.pos.data.notification

import com.google.firebase.messaging.FirebaseMessaging
import com.sukashawarma.pos.data.remote.SupabaseClient
import com.sukashawarma.pos.data.remote.dto.UpsertFcmTokenPayload

/** Shared by POSFirebaseMessagingService.onNewToken, post-login registration, and logout. */
object FcmTokenRegistrar {
    suspend fun registerCurrentToken(staffId: String, outletId: String?) {
        val token = try {
            fetchToken() ?: return
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }
        try {
            SupabaseClient.api.upsertFcmToken(
                payload = UpsertFcmTokenPayload(staffId = staffId, outletId = outletId, token = token)
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Call BEFORE clearing the session token — deletion is RLS-scoped to auth.uid(). */
    suspend fun unregisterCurrentToken() {
        val token = try {
            fetchToken() ?: return
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }
        try {
            SupabaseClient.api.deleteFcmToken(tokenFilter = "eq.$token")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun fetchToken(): String? = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                cont.resume(task.result, onCancellation = null)
            } else {
                cont.resume(null, onCancellation = null)
            }
        }
    }
}
```

- [ ] **Step 3: Wire it into `LoginViewModel.logout()`**

In `app/src/main/java/com/sukashawarma/pos/presentation/login/LoginViewModel.kt:107-112`, replace:

```kotlin
    fun logout() {
        activeSession.value = null
        SessionTokenHolder.clear()
        AuthPrefs.clear()
        SessionPrefs.clear()
    }
```

with:

```kotlin
    fun logout() {
        viewModelScope.launch {
            // Must run before clearing the token — the RLS delete policy checks auth.uid().
            com.sukashawarma.pos.data.notification.FcmTokenRegistrar.unregisterCurrentToken()
            activeSession.value = null
            SessionTokenHolder.clear()
            AuthPrefs.clear()
            SessionPrefs.clear()
        }
    }
```

- [ ] **Step 4: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Manual verification**

Log in on a device/emulator, then log out. In the Supabase SQL Editor run:
`select * from fcm_tokens where staff_id = '<that staff's id>';`
Expected: zero rows (or fewer rows than before logout, if the device had multiple tokens).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/sukashawarma/pos/data/remote/SupabaseApi.kt app/src/main/java/com/sukashawarma/pos/data/notification/FcmTokenRegistrar.kt app/src/main/java/com/sukashawarma/pos/presentation/login/LoginViewModel.kt
git commit -m "feat: deregister FCM token on logout"
```

---

### Task 6: Client — data-only payload handling + tap-to-open PendingIntent

**Files:**
- Modify: `app/src/main/java/com/sukashawarma/pos/data/notification/POSFirebaseMessagingService.kt`
- Modify: `app/src/main/java/com/sukashawarma/pos/data/notification/NotificationChannels.kt`

**Interfaces:**
- Consumes: `MainActivity` (Task 7 adds `EXTRA_ORDER_ID` constant there — this task references it by name now; Task 7 must define it with that exact name for the reference to compile).
- Produces: Notifications with a `PendingIntent` that launches `MainActivity` carrying string extra `MainActivity.EXTRA_ORDER_ID`.

- [ ] **Step 1: Replace `POSFirebaseMessagingService.kt`**

```kotlin
package com.sukashawarma.pos.data.notification

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.sukashawarma.pos.R
import com.sukashawarma.pos.data.local.SessionPrefs
import com.sukashawarma.pos.data.remote.AuthSessionManager
import com.sukashawarma.pos.presentation.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles FCM pushes for "pesanan masuk"/"pesanan dibatalkan". The payload
 * sent by the notify-order edge function is data-only (no top-level
 * `notification` key) on purpose: a `notification` payload makes Android
 * show its own tray notification without calling onMessageReceived() while
 * the app is backgrounded, which would skip the alarm tone/vibration below.
 */
class POSFirebaseMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        scope.launch {
            val staffId = SessionPrefs.getStaffId() ?: return@launch
            if (!AuthSessionManager.ensureAuthenticated()) return@launch
            FcmTokenRegistrar.registerCurrentToken(staffId, SessionPrefs.getOutletId())
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        OrderAlertPlayer(applicationContext).playNewOrderAlert()

        val type = message.data["type"] ?: "new_order"
        val orderId = message.data["order_id"]
        val title = message.data["title"]
            ?: if (type == "order_cancelled") "Pesanan Dibatalkan" else "Pesanan Baru Masuk"
        val body = message.data["body"] ?: "Ada pesanan baru menunggu diproses."
        showSystemNotification(title, body, orderId)
    }

    private fun showSystemNotification(title: String, body: String, orderId: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            orderId?.let { putExtra(MainActivity.EXTRA_ORDER_ID, it) }
        }
        val requestCode = orderId?.hashCode() ?: System.currentTimeMillis().toInt()
        val pendingIntent = PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NotificationChannels.NEW_ORDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(requestCode, notification)
    }
}
```

- [ ] **Step 2: Make the channel visible on the lock screen**

In `app/src/main/java/com/sukashawarma/pos/data/notification/NotificationChannels.kt`, inside the `.apply { ... }` block on the `channel` (currently only `description` and `enableVibration(true)`), add:

```kotlin
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
```

and add the import `import androidx.core.app.NotificationCompat` at the top of the file.

- [ ] **Step 3: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: This task alone will NOT compile yet — `MainActivity.EXTRA_ORDER_ID` doesn't exist until Task 7. This is expected; proceed to Task 7 before attempting a full build. (Note this in the commit message so a reviewer isn't surprised by a red build mid-task-sequence.)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/sukashawarma/pos/data/notification/POSFirebaseMessagingService.kt app/src/main/java/com/sukashawarma/pos/data/notification/NotificationChannels.kt
git commit -m "feat: data-only FCM payload + tap-to-open PendingIntent (depends on Task 7 for EXTRA_ORDER_ID)"
```

---

### Task 7: Client — MainActivity reads the tapped order and forces the Dashboard tab

**Files:**
- Modify: `app/src/main/java/com/sukashawarma/pos/presentation/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml:36-47`

**Interfaces:**
- Consumes: nothing new.
- Produces: `MainActivity.EXTRA_ORDER_ID: String` (companion object constant, referenced by Task 6), and calls `dashboardViewModel.highlightOrder(orderId: String)` (Task 8 must define this exact method name/signature).

- [ ] **Step 1: Set `launchMode="singleTop"` in the manifest**

In `app/src/main/AndroidManifest.xml`, change the `<activity>` block (lines 36-47) from:

```xml
        <activity
            android:name=".presentation.MainActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:screenOrientation="landscape"
            android:configChanges="orientation|keyboardHidden|screenSize|smallestScreenSize"
            android:theme="@style/Theme.SukaShawarmaPOS">
```

to:

```xml
        <activity
            android:name=".presentation.MainActivity"
            android:exported="true"
            android:launchMode="singleTop"
            android:label="@string/app_name"
            android:screenOrientation="landscape"
            android:configChanges="orientation|keyboardHidden|screenSize|smallestScreenSize"
            android:theme="@style/Theme.SukaShawarmaPOS">
```

Without this, tapping a notification while the app is already running would create a second `MainActivity` instance instead of delivering to `onNewIntent()`, losing the existing session/ViewModel state.

- [ ] **Step 2: Add intent handling to `MainActivity.kt`**

In `app/src/main/java/com/sukashawarma/pos/presentation/MainActivity.kt`, add these imports near the top:

```kotlin
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
```

(`androidx.compose.runtime.*` is already imported via the existing wildcard import on line 14 — check for it first; if `mutableStateOf` is already covered by that wildcard, skip re-adding it.)

Add a companion object and a class-level state property, then handle both cold-start and warm-start (already-running) delivery:

```kotlin
class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_ORDER_ID = "order_id"
    }

    private val loginViewModel: LoginViewModel by viewModels()
    private val dashboardViewModel: DashboardViewModel by viewModels()
    private val posManualOrderViewModel: POSManualOrderViewModel by viewModels()
    private val menuManagementViewModel: MenuManagementViewModel by viewModels()
    private val orderHistoryViewModel: OrderHistoryViewModel by viewModels()
    private val reportsViewModel: ReportsViewModel by viewModels()
    private val shiftViewModel: ShiftViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    // Bumped every time a notification tap should force the UI back to
    // Dashboard — a plain nullable order id wouldn't retrigger the
    // LaunchedEffect below if the same order is tapped twice in a row.
    private val pendingNotificationOrderId = mutableStateOf<String?>(null)

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op — alarm suara/realtime tetap jalan walau ditolak, hanya notifikasi sistem yang hilang */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        handleNotificationIntent(intent)
        setContent {
```

Then, right after the existing `var currentTab by remember { mutableStateOf(POSTab.DASHBOARD) }` line (inside the `else` branch, active-session block), add a `LaunchedEffect` that reacts to a notification tap:

```kotlin
                    var currentTab by remember { mutableStateOf(POSTab.DASHBOARD) }
                    val notifiedOrderId by pendingNotificationOrderId

                    LaunchedEffect(notifiedOrderId) {
                        val orderId = notifiedOrderId ?: return@LaunchedEffect
                        currentTab = POSTab.DASHBOARD
                        dashboardViewModel.highlightOrder(orderId)
                        pendingNotificationOrderId.value = null
                    }
```

Finally, override `onNewIntent` (Activity method, goes after `onCreate`'s closing brace, before the class's closing brace):

```kotlin
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val orderId = intent?.getStringExtra(EXTRA_ORDER_ID) ?: return
        pendingNotificationOrderId.value = orderId
    }
```

- [ ] **Step 3: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL once Task 8 (`highlightOrder`) also lands — if run standalone before Task 8, this will fail with "unresolved reference: highlightOrder", which is expected mid-sequence.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/sukashawarma/pos/presentation/MainActivity.kt app/src/main/AndroidManifest.xml
git commit -m "feat: MainActivity forces Dashboard tab and reads order_id from notification taps"
```

---

### Task 8: Client — `DashboardViewModel` highlight state

**Files:**
- Modify: `app/src/main/java/com/sukashawarma/pos/presentation/dashboard/DashboardViewModel.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces: `val highlightedOrderId: StateFlow<String?>`, `fun highlightOrder(orderId: String)` — this exact name/signature is what Task 7 calls and Task 9 collects.

- [ ] **Step 1: Add the import**

In `app/src/main/java/com/sukashawarma/pos/presentation/dashboard/DashboardViewModel.kt`, add near the other `kotlinx.coroutines` imports (after `import kotlinx.coroutines.isActive` on line 21):

```kotlin
import kotlinx.coroutines.Job
```

- [ ] **Step 2: Add the state and the highlight function**

Right after the existing `val isRealtimeConnected = MutableStateFlow(false)` declaration (line 43), add:

```kotlin
    val isRealtimeConnected = MutableStateFlow(false)
    val highlightedOrderId = MutableStateFlow<String?>(null)
    private var highlightClearJob: Job? = null
```

Then add the function anywhere inside the class body, e.g. right after `setSession(...)` (after its closing brace, currently ending at line 94):

```kotlin
    /** Called when a push notification is tapped — briefly highlights the order card. */
    fun highlightOrder(orderId: String) {
        highlightedOrderId.value = orderId
        highlightClearJob?.cancel()
        highlightClearJob = viewModelScope.launch {
            delay(5_000)
            highlightedOrderId.value = null
        }
    }
```

- [ ] **Step 3: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (Tasks 6, 7, 8 together — `MainActivity.EXTRA_ORDER_ID` and `highlightOrder` both now exist).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/sukashawarma/pos/presentation/dashboard/DashboardViewModel.kt
git commit -m "feat: DashboardViewModel tracks a briefly-highlighted order id"
```

---

### Task 9: Client — highlight border + auto-scroll in the Dashboard UI

**Files:**
- Modify: `app/src/main/java/com/sukashawarma/pos/presentation/components/OrderCard.kt`
- Modify: `app/src/main/java/com/sukashawarma/pos/presentation/dashboard/DashboardScreen.kt`

**Interfaces:**
- Consumes: `DashboardViewModel.highlightedOrderId` (Task 8).
- Produces: `OrderCard(..., isHighlighted: Boolean = false, ...)` — new optional parameter, backward compatible with any other call site.

- [ ] **Step 1: Add the highlight border to `OrderCard`**

In `app/src/main/java/com/sukashawarma/pos/presentation/components/OrderCard.kt`, add the import:

```kotlin
import androidx.compose.foundation.border
```

Change the function signature (lines 26-31) from:

```kotlin
fun OrderCard(
    order: Order,
    onStatusChange: (Order, OrderStatus) -> Unit,
    onPrintReceipt: (Order) -> Unit,
    modifier: Modifier = Modifier
) {
```

to:

```kotlin
fun OrderCard(
    order: Order,
    onStatusChange: (Order, OrderStatus) -> Unit,
    onPrintReceipt: (Order) -> Unit,
    isHighlighted: Boolean = false,
    modifier: Modifier = Modifier
) {
```

Then change the `Card(...)` call (lines 43-48) from:

```kotlin
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
```

to:

```kotlin
    val highlightModifier = if (isHighlighted) {
        Modifier.border(3.dp, ShawarmaOrange, RoundedCornerShape(12.dp))
    } else {
        Modifier
    }

    Card(
        modifier = modifier.fillMaxWidth().then(highlightModifier),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
```

(`ShawarmaOrange` is already available via the existing `import com.sukashawarma.pos.presentation.theme.*` wildcard on line 20.)

- [ ] **Step 2: Collect highlight state and scroll to it in `DashboardScreen`**

In `app/src/main/java/com/sukashawarma/pos/presentation/dashboard/DashboardScreen.kt`, add the import:

```kotlin
import androidx.compose.foundation.lazy.rememberLazyListState
```

Right after the existing state collection block (lines 33-43, ending with `var completedSearchQuery by remember { mutableStateOf("") }`), add:

```kotlin
    val highlightedOrderId by viewModel.highlightedOrderId.collectAsState()
    val pendingListState = rememberLazyListState()
    val preparingListState = rememberLazyListState()
    val completedListState = rememberLazyListState()

    LaunchedEffect(highlightedOrderId, pendingOrders, preparingOrders, completedOrders) {
        val id = highlightedOrderId ?: return@LaunchedEffect
        val pendingIndex = pendingOrders.indexOfFirst { it.id == id }
        val preparingIndex = preparingOrders.indexOfFirst { it.id == id }
        val completedIndex = completedOrders.indexOfFirst { it.id == id }
        when {
            pendingIndex >= 0 -> pendingListState.animateScrollToItem(pendingIndex)
            preparingIndex >= 0 -> preparingListState.animateScrollToItem(preparingIndex)
            completedIndex >= 0 -> completedListState.animateScrollToItem(completedIndex)
        }
    }
```

Then update the three `LazyColumn`/`OrderCard` blocks to pass the state and the highlight flag:

Column 1 (pending, lines 254-262) — change:

```kotlin
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(pendingOrders, key = { it.id }) { order ->
                            OrderCard(
                                order = order,
                                onStatusChange = { o, newStatus -> viewModel.updateOrderStatus(o, newStatus) },
                                onPrintReceipt = { o -> viewModel.printReceipt(o, isKitchen = false) }
                            )
                        }
                    }
```

to:

```kotlin
                    LazyColumn(state = pendingListState, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(pendingOrders, key = { it.id }) { order ->
                            OrderCard(
                                order = order,
                                onStatusChange = { o, newStatus -> viewModel.updateOrderStatus(o, newStatus) },
                                onPrintReceipt = { o -> viewModel.printReceipt(o, isKitchen = false) },
                                isHighlighted = order.id == highlightedOrderId
                            )
                        }
                    }
```

Column 2 (preparing, lines 276-284) — same pattern, using `preparingListState`:

```kotlin
                    LazyColumn(state = preparingListState, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(preparingOrders, key = { it.id }) { order ->
                            OrderCard(
                                order = order,
                                onStatusChange = { o, newStatus -> viewModel.updateOrderStatus(o, newStatus) },
                                onPrintReceipt = { o -> viewModel.printReceipt(o, isKitchen = true) },
                                isHighlighted = order.id == highlightedOrderId
                            )
                        }
                    }
```

Column 3 (completed, lines 298-306) — same pattern, using `completedListState`:

```kotlin
                    LazyColumn(state = completedListState, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(completedOrders.filter { completedSearchQuery.isEmpty() || it.orderNumber.toString().contains(completedSearchQuery) }, key = { it.id }) { order ->
                            OrderCard(
                                order = order,
                                onStatusChange = { o, newStatus -> viewModel.updateOrderStatus(o, newStatus) },
                                onPrintReceipt = { o -> viewModel.printReceipt(o, isKitchen = false) },
                                isHighlighted = order.id == highlightedOrderId
                            )
                        }
                    }
```

- [ ] **Step 3: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Manual verification**

Run the app on a device/emulator (`./gradlew :app:installDebug` or the `run` skill). In the Supabase SQL Editor, manually set `highlightedOrderId` behavior by simulating a tap: since a real end-to-end push requires Task 10's deployment, for now just confirm the build runs and the Dashboard renders unchanged with `isHighlighted` defaulting to `false` everywhere (no visual regression).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/sukashawarma/pos/presentation/components/OrderCard.kt app/src/main/java/com/sukashawarma/pos/presentation/dashboard/DashboardScreen.kt
git commit -m "feat: highlight + auto-scroll to the order a push notification pointed at"
```

---

### Task 10: Deploy secrets, migrations, and end-to-end verification

**Files:** none (operational task — no code changes).

**Interfaces:**
- Consumes: everything from Tasks 1-9.
- Produces: a working push notification end-to-end.

- [ ] **Step 1: Set the Firebase service-account secret**

```bash
supabase secrets set FCM_SERVICE_ACCOUNT="$(cat '/path/to/your/firebase-service-account.json')"
```

Use the actual service-account JSON file path — never paste its contents directly into a shell command or into any tracked file.

- [ ] **Step 2: Deploy the edge function**

```bash
supabase functions deploy notify-order
```

Expected output includes a deployed URL matching `https://khpkoreaaucvyqfhynfq.supabase.co/functions/v1/notify-order`.

- [ ] **Step 3: Run the two SQL migrations**

In the Supabase SQL Editor (or `supabase db push` if migrations are tracked that way in this project), run in order:
1. `supabase_migration_fcm_tokens_delete_policy.sql`
2. `supabase_migration_order_push_trigger.sql`

Then run the one-time GUC setup mentioned in that file's trailing comment, substituting the real `service_role` key from Project Settings > API:

```sql
ALTER DATABASE postgres SET "app.settings.service_role_key" TO '<service_role_key>';
```

- [ ] **Step 4: End-to-end test — new order push**

Install the app on a physical device or emulator with Google Play Services, log in, then background the app (press Home — don't force-stop). From the SQL Editor, insert a test row:

```sql
insert into orders (outlet_id, order_number, customer_name, status, source, total_amount)
values ('<a real outlet_id from your outlets table>', 999, 'Test Notif', 'pending', 'kiosk', 50000);
```

Expected: within a few seconds, the device vibrates + plays the alert tone and a system notification titled "Pesanan Baru Masuk" appears, even though the app is backgrounded.

- [ ] **Step 5: End-to-end test — tap-to-open + highlight**

Tap the notification from Step 4.

Expected: the app opens directly to the Dashboard tab (not whatever tab it was on before backgrounding), scrolls the "Menunggu Pembayaran" column to order #999, and that card shows an orange border for about 5 seconds before it fades back to normal.

- [ ] **Step 6: End-to-end test — cancelled order push**

```sql
update orders set status = 'cancelled' where order_number = 999 and outlet_id = '<same outlet_id>';
```

Expected: a second push arrives titled "Pesanan Dibatalkan".

- [ ] **Step 7: End-to-end test — dead token cleanup**

Uninstall the app from the test device (without logging out first, so its `fcm_tokens` row is not deleted via Task 5's cleanup). Repeat Step 4's insert. Then in the SQL Editor:

```sql
select * from fcm_tokens where token = '<the uninstalled device's token>';
```

Expected: zero rows — the edge function received `UNREGISTERED` from FCM and deleted it.

- [ ] **Step 8: Clean up test data**

```sql
delete from orders where order_number = 999 and customer_name = 'Test Notif';
```

---

## Self-Review Notes

- **Spec coverage:** all 5 goals from the design doc map to tasks — server sender (1-4), data-only alarm fix (6), tap deep-link (6-7), highlight (8-9), logout cleanup (5), dead-token cleanup (2, verified in 10).
- **Type consistency checked:** `highlightOrder(orderId: String)` (Task 8) matches the call in Task 7; `MainActivity.EXTRA_ORDER_ID` (Task 7) matches the reference in Task 6; `OrderCard(..., isHighlighted: Boolean = false, ...)` (Task 9) matches all three call sites updated in the same task; `FcmSendResult` fields (`ok`, `shouldRemoveToken`, `errorCode`) match between `fcm.ts` (Task 1) and `index.ts` (Task 2); JSON payload shape from the SQL trigger (Task 3: `type, order_id, order_number, outlet_id, customer_name, total_amount`) matches the `OrderPushPayload` interface in `index.ts` (Task 2) field-for-field.
- **Cross-task compile gap:** Tasks 6 and 7 are mutually dependent (`EXTRA_ORDER_ID` / `highlightOrder`) and will not compile in isolation until Task 8 also lands — this is called out explicitly in each task's build-verification step so a reviewer doesn't mistake it for a mistake.
