/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */

import { env } from "cloudflare:test";
import worker from "../../src/index";
import { tokenCache } from "../../src/session/TokenCacheDO";
import { fakeMtls, jsonResponse, type RecordedCall } from "./fakeMtls";
import { halfHash, makeHsbcJwks, signTestIdToken, type TestJwks } from "./hsbcJwks";
import { b64urlEncode, generateTestKeyPairPem, verifyPs256 } from "./keys";

// A fake HSBC behind env.HSBC_MTLS for route tests: token endpoint per grant, AIS consents, AIS reads.
// Known token values let custody assertions grep every Worker response for them.

export const BANK = "secure.sandbox.ob.hsbc.co.uk";
export const CONSENT_ID = "aac-123";
export const TOKENS = {
  cc: "fixture-cc-token-31f0",
  access: "fixture-psu-access-7d2e",
  refresh: "fixture-psu-refresh-4b9c",
  idToken: "fixture-psu-idtoken-8e1a",
  newAccess: "fixture-psu-new-access-2c6f",
};
export const ALL_TOKENS = Object.values(TOKENS);
const nowSec = () => Math.floor(Date.now() / 1000);

export interface BankOptions {
  consentStatus?: string; // returned by GET account-access-consents/{id} and every PIS / VRP consent read
  accountsBody?: (url: URL) => unknown;
  statementFile?: { type: string; bytes: Uint8Array };
  rejectFirstAccess?: boolean; // AIS reads answer 401 to TOKENS.access, 200 to TOKENS.newAccess
  readError?: number; // AIS reads answer this status
  noRefreshToken?: boolean; // the authorization_code grant returns no refresh_token
}

/** The ConsentId the fake bank issues for a PIS / VRP consent endpoint, e.g. "pc-domestic-payment". */
export const payConsentId = (consentsSegment: string) => `pc-${consentsSegment.replace(/-consents$/, "")}`;
export const VRP_CONSENT_ID = payConsentId("domestic-vrp-consents");
export const PAYMENT_ID = "pmt-1";

export function fakeBank(o: BankOptions = {}) {
  const grants: string[] = [];
  const responder = async (call: RecordedCall): Promise<Response> => {
    const url = new URL(call.url);
    const path = url.pathname.replace("/obie/open-banking", "");
    if (path === "/v1.1/oauth2/token") {
      const grant = new URLSearchParams(call.body).get("grant_type") ?? "";
      grants.push(grant);
      if (grant === "client_credentials") return jsonResponse({ access_token: TOKENS.cc, token_type: "Bearer", expires_in: 3600 });
      if (grant === "refresh_token") return jsonResponse({ access_token: TOKENS.newAccess, refresh_token: TOKENS.refresh, expires_in: 300 });
      const refresh = o.noRefreshToken ? {} : { refresh_token: TOKENS.refresh };
      return jsonResponse({ access_token: TOKENS.access, ...refresh, id_token: TOKENS.idToken, token_type: "Bearer", expires_in: 300 });
    }
    if (path === "/v4.0/aisp/account-access-consents" && call.method === "POST") {
      return jsonResponse({ Data: { ConsentId: CONSENT_ID, Status: "AwaitingAuthorisation" }, Risk: {}, Links: { Self: `https://${BANK}/obie/open-banking${path}` } }, 201);
    }
    const consent = /^\/v4\.0\/aisp\/account-access-consents\/([\w-]+)$/.exec(path);
    if (consent) {
      if (call.method === "DELETE") return new Response(null, { status: 204 });
      return jsonResponse({ Data: { ConsentId: consent[1], Status: o.consentStatus ?? "Authorised", ExpirationDateTime: "2027-01-20T00:00:00Z" } });
    }
    const bearer = call.headers.get("authorization");
    const psuOk = o.rejectFirstAccess ? bearer === `Bearer ${TOKENS.newAccess}` : bearer === `Bearer ${TOKENS.access}` || bearer === `Bearer ${TOKENS.newAccess}`;
    const unauthorised = () => jsonResponse({ Code: "401", Errors: [{ ErrorCode: "UK.OBIE.Unauthorised" }] }, 401);
    const pisConsent = /^\/v4\.0\/pisp\/([a-z-]+-consents)(?:\/([\w-]+))?(\/funds-confirmation)?$/.exec(path);
    if (pisConsent) {
      const [, segment, id, funds] = pisConsent as unknown as [string, string, string | undefined, string | undefined];
      if (funds) return psuOk ? jsonResponse({ Data: { FundsAvailableResult: { FundsAvailable: true } } }) : unauthorised();
      if (bearer !== `Bearer ${TOKENS.cc}`) return unauthorised();
      if (!id) return jsonResponse({ Data: { ConsentId: payConsentId(segment), Status: "AwaitingAuthorisation" }, Risk: {} }, 201);
      if (call.method === "DELETE") return new Response(null, { status: 204 });
      const vrp = segment === "domestic-vrp-consents" ? { ControlParameters: { ValidToDateTime: "2027-03-01T00:00:00Z" } } : {};
      return jsonResponse({ Data: { ConsentId: id, Status: o.consentStatus ?? "Authorised", ...vrp } });
    }
    const pisPayment = /^\/v4\.0\/pisp\/([a-z-]+)(?:\/([\w-]+))?$/.exec(path);
    if (pisPayment) {
      // Data.<Product>Id, e.g. DomesticPaymentId, DomesticVRPId — read back by the Worker to record ownership.
      const idField = pisPayment[1]!.replace(/s$/, "").replace("vrp", "VRP").replace(/(^|-)(\w)/g, (_, __, c: string) => c.toUpperCase()) + "Id";
      if (call.method === "GET") return bearer === `Bearer ${TOKENS.cc}` ? jsonResponse({ Data: { [idField]: pisPayment[2], Status: "AcceptedSettlementCompleted" } }) : unauthorised();
      if (!psuOk) return unauthorised();
      const consentId = (JSON.parse(call.body) as { Data: { ConsentId: string } }).Data.ConsentId;
      return jsonResponse({ Data: { ConsentId: consentId, [idField]: PAYMENT_ID, Status: "AcceptedSettlementInProcess" } }, 201);
    }
    if (path.startsWith("/v4.0/aisp/accounts")) {
      if (!psuOk) return unauthorised();
      if (o.readError) return jsonResponse({ Code: String(o.readError), Errors: [{ ErrorCode: "UK.OBIE.Resource.NotFound" }] }, o.readError);
      if (path.endsWith("/file") && o.statementFile) return new Response(o.statementFile.bytes, { headers: { "content-type": o.statementFile.type } });
      return jsonResponse(o.accountsBody ? o.accountsBody(url) : { Data: { Account: [] }, Links: { Self: url.toString() } });
    }
    return new Response("unexpected bank call", { status: 599 });
  };
  const mtls = fakeMtls(responder);
  return { fetcher: mtls.fetcher, calls: mtls.calls, grants };
}

let shared: Promise<{ pem: string; publicKey: CryptoKey; jwks: TestJwks }> | undefined;
/** One signing key + one fake HSBC JWKS per test file, seeded into TokenCacheDO so no discovery call runs. */
export function keys() {
  shared ??= (async () => {
    const kp = await generateTestKeyPairPem();
    return { pem: kp.privatePkcs8Pem, publicKey: kp.publicKey, jwks: await makeHsbcJwks() };
  })();
  return shared;
}

export async function testEnv(bank: ReturnType<typeof fakeBank>, extra: Partial<typeof env> = {}) {
  const k = await keys();
  await tokenCache(env).put("hsbc:oidc", JSON.stringify({ issuer: env.HSBC_ISSUER, jwks: k.jwks.jwks }), nowSec() + 3600);
  return { ...env, HSBC_MTLS: bank.fetcher, HSBC_SIGNING_KEY_PEM: k.pem, HSBC_CLIENT_ID: "client-1", HSBC_KID: "kid-1", HSBC_SIGNING_ISSUER: SIGNING_ISSUER, ...extra };
}

export const SIGNING_ISSUER = "CN=fixture-signer,OU=TPP,O=Mifos Fixture,C=GB";

export type TestEnv = Awaited<ReturnType<typeof testEnv>>;

export function call(e: TestEnv, method: string, path: string, init: { body?: unknown; headers?: Record<string, string> } = {}) {
  const headers: Record<string, string> = { "X-Client-Instance": crypto.randomUUID(), ...init.headers };
  let body: string | undefined;
  if (init.body !== undefined) {
    body = typeof init.body === "string" ? init.body : JSON.stringify(init.body);
    headers["content-type"] ??= "application/json";
  }
  return worker.fetch(new Request(`https://bff.test${path}`, { method, headers, body }), e);
}

export interface Flow {
  flowId: string;
  consentId: string;
  authorizeUrl: string;
  state: string;
  nonce: string;
}

export async function startFlow(e: TestEnv): Promise<Flow> {
  const res = await call(e, "POST", "/v1/consents/ais", { body: { permissions: ["ReadAccountsDetail"] } });
  if (res.status !== 200) throw new Error(`consent create returned ${res.status}`);
  const b = (await res.json()) as { flowId: string; consentId: string; authorizeUrl: string };
  const q = new URL(b.authorizeUrl).searchParams;
  return { ...b, state: q.get("state")!, nonce: q.get("nonce")! };
}

export const CODE = "auth-code-0f9e";

/** A valid HSBC id_token for the flow; `claims` / `header` / `signer` override parts to tamper with it. */
export async function idTokenFor(
  f: Flow,
  o: { claims?: Record<string, unknown>; signer?: { kid: string; privateKey: CryptoKey } } = {},
): Promise<string> {
  const k = await keys();
  const now = nowSec();
  return signTestIdToken(
    {
      iss: env.HSBC_ISSUER,
      aud: "client-1",
      sub: CONSENT_ID,
      iat: now,
      exp: now + 300,
      nonce: f.nonce,
      c_hash: await halfHash(CODE),
      s_hash: await halfHash(f.state),
      ...o.claims,
    },
    o.signer ?? k.jwks,
  );
}

export async function completeAuth(
  e: TestEnv,
  f: Flow,
  body: Partial<{ flowId: string; code: string; idToken: string; state: string }> = {},
  headers: Record<string, string> = {},
) {
  return call(e, "POST", "/v1/auth/complete", { body: { flowId: f.flowId, code: CODE, idToken: await idTokenFor(f), state: f.state, ...body }, headers });
}

/** Consent → auth/complete; returns the opaque session id. */
export async function signIn(e: TestEnv): Promise<string> {
  const res = await completeAuth(e, await startFlow(e));
  if (res.status !== 200) throw new Error(`auth/complete returned ${res.status}`);
  return ((await res.json()) as { session: string }).session;
}

export const sessionHeader = (s: string) => ({ Authorization: `Session ${s}` });

export async function exposed(res: Response): Promise<string> {
  return `${[...res.headers].map(([k, v]) => `${k}: ${v}`).join("\n")}\n${await res.clone().text()}`;
}

// ── PIS / VRP ──

let idem = 0;
/** A fresh X-Idempotency-Key header (≤ 40 chars). */
export const idemHeader = () => ({ "X-Idempotency-Key": `idem-${Date.now()}-${++idem}` });

/** POST a signed payment / VRP consent and return its flow (authorize URL decoded). */
export async function startPayFlow(e: TestEnv, path: string, body: string): Promise<Flow> {
  const res = await call(e, "POST", path, { body, headers: idemHeader() });
  if (res.status !== 200) throw new Error(`${path} returned ${res.status}: ${await res.text()}`);
  const b = (await res.json()) as { flowId: string; consentId: string; authorizeUrl: string };
  const q = new URL(b.authorizeUrl).searchParams;
  return { ...b, state: q.get("state")!, nonce: q.get("nonce")! };
}

/** Consent → auth/complete for a payment / VRP leg, optionally onto an existing session. */
export async function authorisePay(e: TestEnv, path: string, body: string, session?: string) {
  const flow = await startPayFlow(e, path, body);
  const res = await completeAuth(e, flow, {}, session ? sessionHeader(session) : {});
  if (res.status !== 200) throw new Error(`auth/complete returned ${res.status}: ${await res.text()}`);
  return { flow, out: (await res.json()) as { session: string; expiresAt: number; leg: string; consentId: string; consentStatus: string; consentExpiresAt: string | null } };
}

/** Verifies an x-jws-signature (header..signature) over exactly `body` with the fixture signing key. */
export async function verifyDetachedJws(sig: string, body: string): Promise<boolean> {
  const [h, mid, s] = sig.split(".");
  if (mid !== "" || !h || !s) return false;
  return verifyPs256((await keys()).publicKey, h, b64urlEncode(new TextEncoder().encode(body)), s);
}

export const INITIATION = {
  InstructionIdentification: "instr-0001",
  EndToEndIdentification: "e2e-0001",
  InstructedAmount: { Amount: "1.00", Currency: "GBP" },
  CreditorAccount: { SchemeName: "UK.OBIE.SortCodeAccountNumber", Identification: "40120965872310", Name: "Jameson Lettings" },
  RemittanceInformation: { Unstructured: ["Rent – £ October"], Structured: [{ CreditorReferenceInformation: { Reference: "RENT-OCT" } }] },
};

/**
 * A consent body exactly as the app sends it: compact JSON (JsonObject.toString()), non-ASCII left
 * literal. HSBC verifies x-jws-signature over the compact form, so the Worker refuses anything else.
 */
export const consentBody = (data: Record<string, unknown> = { Initiation: INITIATION }) =>
  JSON.stringify({ Data: data, Risk: { PaymentContextCode: "TransferToThirdParty" } });

export const paymentBody = (consentId: string, extra: Record<string, unknown> = { Initiation: INITIATION }) =>
  JSON.stringify({ Data: { ConsentId: consentId, ...extra }, Risk: {} });
