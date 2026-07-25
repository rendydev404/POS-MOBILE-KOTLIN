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
