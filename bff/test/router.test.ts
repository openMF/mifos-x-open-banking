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
import { describe, expect, it } from "vitest";
import { rewriteLinks } from "../src/hsbc/pagination";
import worker, { HANDLERS } from "../src/index";
import { ROUTES, type Route } from "../src/routes";
import { matchRoute, resolveRoute } from "../src/router";
import { fakeMtls } from "./fixtures/fakeMtls";

const BASE = "https://bff.test";
const METHODS = ["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"];
const UNLISTED_PATHS = [
  "/",
  "/v1",
  "/v1/",
  "/v1/health/",
  "/v1/health/extra",
  "/V1/HEALTH",
  "//v1/health",
  "/v1/__spike/payment-consent",
  "/obie/open-banking/v1.1/oauth2/token",
  "/obie/open-banking/v4.0/aisp/accounts",
  "/v1/accounts",
  "/v1/accounts/abc/balances",
  "/v1/%2e%2e/health",
  "/.well-known/openid-configuration",
  `/v1/${crypto.randomUUID()}`,
  `/${crypto.randomUUID()}/${crypto.randomUUID()}`,
];

async function callWorker(req: Request) {
  const mtls = fakeMtls(() => new Response("must never be reached", { status: 599 }));
  const res = await worker.fetch(req, { ...env, HSBC_MTLS: mtls.fetcher });
  return { res, mtls };
}

describe("closed route table (D7 / AC6)", () => {
  it("the table is exactly this list — a new row must be added here on purpose", () => {
    expect(ROUTES.map((r) => `${r.method} ${r.path} -> ${r.hsbcOp.method} ${r.hsbcOp.path} [${r.token}]`)).toEqual([
      "GET /v1/health -> GET /__none__ [none]",
      "POST /v1/attest/challenge -> POST /__none__ [none]",
      "POST /v1/attest/ios/register -> POST /__none__ [none]",
      "POST /v1/consents/ais -> POST /v4.0/aisp/account-access-consents [cc:accounts]",
      "GET /v1/consents/ais/:consentId -> GET /v4.0/aisp/account-access-consents/:consentId [cc:accounts]",
      "DELETE /v1/consents/ais/:consentId -> DELETE /v4.0/aisp/account-access-consents/:consentId [cc:accounts]",
      "POST /v1/auth/complete -> POST /v1.1/oauth2/token [none]",
      "POST /v1/session/logout -> DELETE /v4.0/aisp/account-access-consents/:consentId [cc:accounts]",
      "GET /v1/ais/accounts -> GET /v4.0/aisp/accounts [psu:ais]",
      "GET /v1/ais/accounts/:accountId -> GET /v4.0/aisp/accounts/:accountId [psu:ais]",
      "GET /v1/ais/accounts/:accountId/balances -> GET /v4.0/aisp/accounts/:accountId/balances [psu:ais]",
      "GET /v1/ais/accounts/:accountId/beneficiaries -> GET /v4.0/aisp/accounts/:accountId/beneficiaries [psu:ais]",
      "GET /v1/ais/accounts/:accountId/direct-debits -> GET /v4.0/aisp/accounts/:accountId/direct-debits [psu:ais]",
      "GET /v1/ais/accounts/:accountId/product -> GET /v4.0/aisp/accounts/:accountId/product [psu:ais]",
      "GET /v1/ais/accounts/:accountId/party -> GET /v4.0/aisp/accounts/:accountId/party [psu:ais]",
      "GET /v1/ais/accounts/:accountId/parties -> GET /v4.0/aisp/accounts/:accountId/parties [psu:ais]",
      "GET /v1/ais/accounts/:accountId/transactions -> GET /v4.0/aisp/accounts/:accountId/transactions [psu:ais]",
      "GET /v1/ais/accounts/:accountId/standing-orders -> GET /v4.0/aisp/accounts/:accountId/standing-orders [psu:ais]",
      "GET /v1/ais/accounts/:accountId/scheduled-payments -> GET /v4.0/aisp/accounts/:accountId/scheduled-payments [psu:ais]",
      "GET /v1/ais/accounts/:accountId/statements -> GET /v4.0/aisp/accounts/:accountId/statements [psu:ais]",
      "GET /v1/ais/accounts/:accountId/statements/:statementId -> GET /v4.0/aisp/accounts/:accountId/statements/:statementId [psu:ais]",
      "GET /v1/ais/accounts/:accountId/statements/:statementId/transactions -> GET /v4.0/aisp/accounts/:accountId/statements/:statementId/transactions [psu:ais]",
      "GET /v1/ais/accounts/:accountId/statements/:statementId/file -> GET /v4.0/aisp/accounts/:accountId/statements/:statementId/file [psu:ais]",
      "GET /v1/ais/page -> GET /v4.0/aisp/* [psu:ais]",
      "POST /v1/consents/payment/domestic -> POST /v4.0/pisp/domestic-payment-consents [cc:payments]",
      "GET /v1/consents/payment/domestic/:consentId -> GET /v4.0/pisp/domestic-payment-consents/:consentId [cc:payments]",
      "GET /v1/consents/payment/domestic/:consentId/funds-confirmation -> GET /v4.0/pisp/domestic-payment-consents/:consentId/funds-confirmation [psu:payment]",
      "POST /v1/payments/domestic -> POST /v4.0/pisp/domestic-payments [psu:payment]",
      "GET /v1/payments/domestic/:paymentId -> GET /v4.0/pisp/domestic-payments/:paymentId [cc:payments]",
      "POST /v1/consents/payment/international -> POST /v4.0/pisp/international-payment-consents [cc:payments]",
      "GET /v1/consents/payment/international/:consentId -> GET /v4.0/pisp/international-payment-consents/:consentId [cc:payments]",
      "GET /v1/consents/payment/international/:consentId/funds-confirmation -> GET /v4.0/pisp/international-payment-consents/:consentId/funds-confirmation [psu:payment]",
      "POST /v1/payments/international -> POST /v4.0/pisp/international-payments [psu:payment]",
      "GET /v1/payments/international/:paymentId -> GET /v4.0/pisp/international-payments/:paymentId [cc:payments]",
      "POST /v1/consents/payment/domestic-scheduled -> POST /v4.0/pisp/domestic-scheduled-payment-consents [cc:payments]",
      "GET /v1/consents/payment/domestic-scheduled/:consentId -> GET /v4.0/pisp/domestic-scheduled-payment-consents/:consentId [cc:payments]",
      "POST /v1/payments/domestic-scheduled -> POST /v4.0/pisp/domestic-scheduled-payments [psu:payment]",
      "GET /v1/payments/domestic-scheduled/:paymentId -> GET /v4.0/pisp/domestic-scheduled-payments/:paymentId [cc:payments]",
      "POST /v1/consents/payment/international-scheduled -> POST /v4.0/pisp/international-scheduled-payment-consents [cc:payments]",
      "GET /v1/consents/payment/international-scheduled/:consentId -> GET /v4.0/pisp/international-scheduled-payment-consents/:consentId [cc:payments]",
      "POST /v1/payments/international-scheduled -> POST /v4.0/pisp/international-scheduled-payments [psu:payment]",
      "GET /v1/payments/international-scheduled/:paymentId -> GET /v4.0/pisp/international-scheduled-payments/:paymentId [cc:payments]",
      "POST /v1/consents/payment/domestic-standing-order -> POST /v4.0/pisp/domestic-standing-order-consents [cc:payments]",
      "GET /v1/consents/payment/domestic-standing-order/:consentId -> GET /v4.0/pisp/domestic-standing-order-consents/:consentId [cc:payments]",
      "POST /v1/payments/domestic-standing-order -> POST /v4.0/pisp/domestic-standing-orders [psu:payment]",
      "GET /v1/payments/domestic-standing-order/:paymentId -> GET /v4.0/pisp/domestic-standing-orders/:paymentId [cc:payments]",
      "POST /v1/consents/payment/international-standing-order -> POST /v4.0/pisp/international-standing-order-consents [cc:payments]",
      "GET /v1/consents/payment/international-standing-order/:consentId -> GET /v4.0/pisp/international-standing-order-consents/:consentId [cc:payments]",
      "POST /v1/payments/international-standing-order -> POST /v4.0/pisp/international-standing-orders [psu:payment]",
      "GET /v1/payments/international-standing-order/:paymentId -> GET /v4.0/pisp/international-standing-orders/:paymentId [cc:payments]",
      "POST /v1/consents/vrp -> POST /v4.0/pisp/domestic-vrp-consents [cc:payments]",
      "GET /v1/consents/vrp/:consentId -> GET /v4.0/pisp/domestic-vrp-consents/:consentId [cc:payments]",
      "DELETE /v1/consents/vrp/:consentId -> DELETE /v4.0/pisp/domestic-vrp-consents/:consentId [cc:payments]",
      "POST /v1/consents/vrp/:consentId/funds-confirmation -> POST /v4.0/pisp/domestic-vrp-consents/:consentId/funds-confirmation [psu:vrp]",
      "POST /v1/vrp/payments -> POST /v4.0/pisp/domestic-vrps [psu:vrp]",
      "GET /v1/vrp/payments/:paymentId -> GET /v4.0/pisp/domestic-vrps/:paymentId [cc:payments]",
    ]);
    expect(new Set(ROUTES.map((r) => r.id)).size).toBe(ROUTES.length);
  });

  it("every listed route matches exactly its own row; other methods match only another listed row", () => {
    const listed = new Set(ROUTES.map((r) => `${r.method} ${r.path}`));
    for (const r of ROUTES) {
      const concrete = r.path.replace(/:[A-Za-z]+/g, "x");
      expect(matchRoute(r.method, concrete)?.route.id).toBe(r.id);
      for (const m of METHODS.filter((m) => m !== r.method)) {
        const hit = matchRoute(m, concrete);
        if (hit) expect(listed.has(`${m} ${r.path}`), `${m} ${r.path}`).toBe(true);
      }
    }
  });

  it("every handler key named by the table exists", () => {
    for (const r of ROUTES) expect(HANDLERS[r.handler ?? r.id], r.id).toBeTypeOf("function");
  });

  it("every unlisted method/path returns 404 route_not_found with ZERO calls to HSBC_MTLS", async () => {
    const listed = new Set(ROUTES.map((r) => `${r.method} ${r.path}`));
    const probes: Array<[string, string]> = [];
    for (const r of ROUTES) for (const m of METHODS) if (!listed.has(`${m} ${r.path}`)) probes.push([m, r.path]);
    for (const p of UNLISTED_PATHS) for (const m of METHODS) probes.push([m, p]);

    for (const [method, path] of probes) {
      expect(listed.has(`${method} ${path}`)).toBe(false);
      const { res, mtls } = await callWorker(new Request(BASE + path, { method }));
      expect(res.status, `${method} ${path}`).toBe(404);
      const body = (await res.json()) as { error: string; requestId: string };
      expect(body.error).toBe("route_not_found");
      expect(body.requestId).toBe(res.headers.get("x-request-id"));
      expect(mtls.calls, `${method} ${path}`).toHaveLength(0);
    }
  });

  it("GET /v1/health answers 200 without touching HSBC", async () => {
    const { res, mtls } = await callWorker(new Request(`${BASE}/v1/health`));
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({ status: "ok" });
    expect(res.headers.get("x-request-id")).toMatch(/^[0-9a-f-]{36}$/);
    expect(mtls.calls).toHaveLength(0);
  });

  it("captures :params segment by segment", () => {
    const routes: Route[] = [{ ...ROUTES[0]!, id: "bal", path: "/v1/accounts/:accountId/balances" }];
    expect(matchRoute("GET", "/v1/accounts/acc-1/balances", routes)?.params).toEqual({ accountId: "acc-1" });
    expect(matchRoute("GET", "/v1/accounts//balances", routes)).toBeNull();
    expect(matchRoute("GET", "/v1/accounts/acc-1", routes)).toBeNull();
  });
});

describe("body limits", () => {
  const post: Route = {
    id: "test-post",
    method: "POST",
    path: "/v1/test",
    hsbcOp: { method: "POST", path: "/__none__" },
    token: "none",
    signJws: false,
    maxBodyBytes: 16,
    attest: false,
    session: false,
  };
  const req = (body: BodyInit, headers: Record<string, string> = { "content-type": "application/json" }) =>
    new Request(`${BASE}/v1/test`, { method: "POST", body, headers });

  it("413 when Content-Length exceeds maxBodyBytes", async () => {
    expect(await resolveRoute(req('{"a":"0123456789abcdef"}'), [post])).toEqual({ ok: false, status: 413, error: "payload_too_large" });
  });

  it("413 when a streamed body (no Content-Length) exceeds maxBodyBytes", async () => {
    const stream = new ReadableStream({
      start(c) {
        c.enqueue(new TextEncoder().encode('{"a":"'));
        c.enqueue(new TextEncoder().encode("0123456789abcdef"));
        c.enqueue(new TextEncoder().encode('"}'));
        c.close();
      },
    });
    const r = new Request(`${BASE}/v1/test`, { method: "POST", body: stream, headers: { "content-type": "application/json" } });
    expect(r.headers.get("content-length")).toBeNull();
    expect(await resolveRoute(r, [post])).toEqual({ ok: false, status: 413, error: "payload_too_large" });
  });

  it("413 when a body is sent to a route that takes none", async () => {
    const del: Route = { ...post, id: "test-del", method: "DELETE", maxBodyBytes: 0 };
    const r = new Request(`${BASE}/v1/test`, { method: "DELETE", body: "{}", headers: { "content-type": "application/json" } });
    expect(await resolveRoute(r, [del])).toEqual({ ok: false, status: 413, error: "payload_too_large" });
  });

  it("an empty POST (Content-Length: 0, no content-type) is a body-less request, not a 415", async () => {
    const r = new Request(`${BASE}/v1/test`, { method: "POST", body: "", headers: { "content-length": "0" } });
    expect(await resolveRoute(r, [post])).toMatchObject({ ok: true, body: null });
  });

  it("415 for a non-JSON content-type on a route with a body", async () => {
    expect(await resolveRoute(req("a=b", { "content-type": "application/x-www-form-urlencoded" }), [post])).toEqual({
      ok: false,
      status: 415,
      error: "unsupported_media_type",
    });
  });

  it("passes a JSON body within the limit through to the handler", async () => {
    const r = await resolveRoute(req('{"a":1}', { "content-type": "application/json; charset=utf-8" }), [post]);
    expect(r.ok && new TextDecoder().decode(r.body!)).toBe('{"a":1}');
  });
});

describe("pagination stays on the bank host (AC6)", () => {
  it("refuses a Links URL on any host but the bank host", async () => {
    await expect(
      rewriteLinks({ Links: { Next: "https://evil.example/obie/open-banking/v4.0/aisp/accounts?page=2" } }, "secure.sandbox.ob.hsbc.co.uk", async (u) => u),
    ).rejects.toThrow(/refusing non-HSBC host/);
  });
});
