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
import worker from "../src/index";
import { fakeMtls } from "./fixtures/fakeMtls";

// The web app (cmp-web) calls the Worker cross-origin with custom headers, so the browser preflights.
// CORS is an allowlist (CORS_ORIGINS) over the same closed route table: an unlisted route or origin
// gets no CORS headers, and a preflight never reaches a handler or HSBC.

const BASE = "https://gateway.test";
const WEB = "http://localhost:8080";

async function call(req: Request) {
  const mtls = fakeMtls(() => new Response("must never be reached", { status: 599 }));
  const res = await worker.fetch(req, { ...env, CORS_ORIGINS: `${WEB}, https://app.example`, HSBC_MTLS: mtls.fetcher });
  return { res, mtls };
}

const preflight = (path: string, origin: string, method = "POST") =>
  new Request(BASE + path, {
    method: "OPTIONS",
    headers: { origin, "access-control-request-method": method, "access-control-request-headers": "content-type,x-client-platform,x-attestation" },
  });

describe("CORS for the web client", () => {
  it("answers a preflight for a listed route from an allowed origin, without touching HSBC", async () => {
    const { res, mtls } = await call(preflight("/v1/consents/ais", WEB));
    expect(res.status).toBe(204);
    expect(res.headers.get("access-control-allow-origin")).toBe(WEB);
    expect(res.headers.get("access-control-allow-methods")).toContain("POST");
    const allowed = res.headers.get("access-control-allow-headers") ?? "";
    for (const h of ["authorization", "content-type", "x-client-platform", "x-client-instance", "x-attestation", "x-idempotency-key"]) expect(allowed).toContain(h);
    expect(res.headers.get("vary")).toContain("Origin");
    expect(mtls.calls).toHaveLength(0);
  });

  it("refuses a preflight from an origin that is not allowlisted", async () => {
    const { res } = await call(preflight("/v1/consents/ais", "https://evil.example"));
    expect(res.status).toBe(404);
    expect(res.headers.get("access-control-allow-origin")).toBeNull();
  });

  it("refuses a preflight for a method the route table does not list", async () => {
    const { res } = await call(preflight("/v1/consents/ais", WEB, "DELETE"));
    expect(res.status).toBe(404);
    expect(res.headers.get("access-control-allow-origin")).toBeNull();
  });

  it("refuses a preflight for an unlisted path", async () => {
    const { res } = await call(preflight("/obie/open-banking/v4.0/aisp/accounts", WEB, "GET"));
    expect(res.status).toBe(404);
  });

  it("an allowed origin's real request carries ACAO and exposes the request id", async () => {
    const { res } = await call(new Request(BASE + "/v1/health", { headers: { origin: WEB } }));
    expect(res.status).toBe(200);
    expect(res.headers.get("access-control-allow-origin")).toBe(WEB);
    expect(res.headers.get("access-control-expose-headers")).toContain("x-request-id");
  });

  it("an error response to an allowed origin is readable too", async () => {
    const { res } = await call(new Request(BASE + "/v1/ais/accounts", { headers: { origin: WEB } }));
    expect(res.status).toBe(401);
    expect(res.headers.get("access-control-allow-origin")).toBe(WEB);
  });

  it("no Origin, or another origin, gets no CORS headers", async () => {
    for (const headers of [{}, { origin: "https://evil.example" }] as Record<string, string>[]) {
      const { res } = await call(new Request(BASE + "/v1/health", { headers }));
      expect(res.headers.get("access-control-allow-origin")).toBeNull();
    }
  });
});
