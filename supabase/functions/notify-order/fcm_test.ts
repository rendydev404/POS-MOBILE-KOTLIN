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
