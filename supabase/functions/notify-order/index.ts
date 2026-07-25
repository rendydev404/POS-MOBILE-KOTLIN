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
