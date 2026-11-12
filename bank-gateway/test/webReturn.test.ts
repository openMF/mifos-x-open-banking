/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */

import { describe, expect, it } from "vitest";
import { call, completeAuth, consentBody, fakeBank, idTokenFor, testEnv, type Flow, type TestEnv } from "./fixtures/bank";

// The relay page cannot tell a web sign-in apart from a mobile one, so the Worker encodes the web return
// origin into `state` as `w<index>.` — an index into WEB_RETURN_ORIGINS, never the origin itself. The relay
// holds the same ordered list and only ever navigates to an origin on it.

const WEB = "http://localhost:8080";
const ORIGINS = `${WEB}, https://app.example`;

async function start(e: TestEnv, path: string, headers: Record<string, string>, body: unknown) {
  const res = await call(e, "POST", path, { body, headers });
  const out = res.status === 200 ? ((await res.json()) as { flowId: string; consentId: string; authorizeUrl: string }) : null;
  return { res, out, state: out ? new URL(out.authorizeUrl).searchParams.get("state")! : null };
}
const ais = (e: TestEnv, headers: Record<string, string>) => start(e, "/v1/consents/ais", headers, { permissions: ["ReadAccountsDetail"] });

describe("web return origin in state", () => {
  it("web + allowed Origin → state carries w<index>. for that origin", async () => {
    const e = await testEnv(fakeBank(), { WEB_RETURN_ORIGINS: ORIGINS });
    const a = await ais(e, { "X-Client-Platform": "web", Origin: WEB });
    expect(a.res.status).toBe(200);
    expect(a.state).toMatch(/^w0\.[0-9a-f-]{36}$/);
    const b = await ais(e, { "X-Client-Platform": "web", Origin: "https://app.example" });
    expect(b.state).toMatch(/^w1\./);
  });

  it("web + Origin not on the list (or missing) → 400 web_origin_not_allowed, before any bank call", async () => {
    const bank = fakeBank();
    const e = await testEnv(bank, { WEB_RETURN_ORIGINS: ORIGINS });
    const cases: Record<string, string>[] = [{ "X-Client-Platform": "web", Origin: "https://evil.example" }, { "X-Client-Platform": "web" }];
    for (const headers of cases) {
      const r = await ais(e, headers);
      expect(r.res.status).toBe(400);
      expect(await r.res.json()).toEqual({ error: "web_origin_not_allowed" });
    }
    expect(bank.calls.filter((c) => c.url.includes("consents"))).toEqual([]);
  });

  it("web with WEB_RETURN_ORIGINS unset → 400", async () => {
    const e = await testEnv(fakeBank(), { WEB_RETURN_ORIGINS: undefined });
    expect((await ais(e, { "X-Client-Platform": "web", Origin: WEB })).res.status).toBe(400);
  });

  it("non-web flows are unchanged: plain UUID state, Origin ignored", async () => {
    const e = await testEnv(fakeBank(), { WEB_RETURN_ORIGINS: ORIGINS });
    const cases: Record<string, string>[] = [{}, { "X-Client-Platform": "desktop", Origin: WEB }, { "X-Client-Platform": "android" }];
    for (const headers of cases) {
      const r = await ais(e, headers);
      expect(r.res.status).toBe(200);
      expect(r.state).toMatch(/^[0-9a-f-]{36}$/);
    }
  });

  it("payment consents get the same prefix", async () => {
    const e = await testEnv(fakeBank(), { WEB_RETURN_ORIGINS: ORIGINS });
    const r = await start(e, "/v1/consents/payment/domestic", { "X-Client-Platform": "web", Origin: WEB, "X-Idempotency-Key": "web-pay-1" }, consentBody());
    expect(r.res.status).toBe(200);
    expect(r.state).toMatch(/^w0\./);
  });

  it("/v1/auth/complete accepts the prefixed state (it is just part of the state string)", async () => {
    const e = await testEnv(fakeBank(), { WEB_RETURN_ORIGINS: ORIGINS });
    const a = await ais(e, { "X-Client-Platform": "web", Origin: WEB });
    const q = new URL(a.out!.authorizeUrl).searchParams;
    const flow: Flow = { ...a.out!, state: a.state!, nonce: q.get("nonce")! };
    const res = await completeAuth(e, flow, { idToken: await idTokenFor(flow) }, { "X-Client-Platform": "web", Origin: WEB });
    expect(res.status).toBe(200);
  });
});

// Several builds of the app can share a phone (store, debug, demo). Each registers its own package as
// its callback scheme, and the Worker tells the relay which one started the flow: `a<index>.` into
// ANDROID_RETURN_APPS (index 0, the store build, keeps the plain state).
const APPS = "org.mifosx.openbanking, org.mifosx.openbanking.debug, org.mifosx.openbanking.demo";

describe("android return app in state", () => {
  it("android + a listed non-store app → state carries a<index>.", async () => {
    const e = await testEnv(fakeBank(), { ANDROID_RETURN_APPS: APPS });
    const dbg = await ais(e, { "X-Client-Platform": "android", "X-Client-App": "org.mifosx.openbanking.debug" });
    expect(dbg.res.status).toBe(200);
    expect(dbg.state).toMatch(/^a1\.[0-9a-f-]{36}$/);
    const demo = await start(e, "/v1/consents/payment/domestic", { "X-Client-Platform": "android", "X-Client-App": "org.mifosx.openbanking.demo", "X-Idempotency-Key": "and-pay-1" }, consentBody());
    expect(demo.state).toMatch(/^a2\./);
  });

  it("the store app, an unlisted app or no header → plain state (the store scheme)", async () => {
    const e = await testEnv(fakeBank(), { ANDROID_RETURN_APPS: APPS });
    for (const app of ["org.mifosx.openbanking", "com.evil.clone", undefined]) {
      const headers: Record<string, string> = { "X-Client-Platform": "android", ...(app ? { "X-Client-App": app } : {}) };
      const r = await ais(e, headers);
      expect(r.res.status).toBe(200);
      expect(r.state).toMatch(/^[0-9a-f-]{36}$/);
    }
  });

  it("X-Client-App is ignored off Android", async () => {
    const e = await testEnv(fakeBank(), { ANDROID_RETURN_APPS: APPS });
    const r = await ais(e, { "X-Client-Platform": "desktop", "X-Client-App": "org.mifosx.openbanking.debug" });
    expect(r.state).toMatch(/^[0-9a-f-]{36}$/);
  });
});
