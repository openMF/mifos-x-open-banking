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
import { sessionStub } from "../../src/session/SessionDO";
import { BANK, call, fakeBank, sessionHeader, signIn, testEnv } from "../fixtures/bank";

const TX = `https://${BANK}/obie/open-banking/v4.0/aisp/accounts/acc-1/transactions`;
const paged = (url: URL) => ({
  Data: { Transaction: [{ TransactionId: url.searchParams.get("page") ?? "1" }] },
  Links: { Self: url.toString(), Next: `${TX}?page=2&fromBookingDateTime=2026-01-01T00:00:00Z` },
  Meta: { TotalPages: 2 },
});

describe("GET /v1/ais/page?cursor=…", () => {
  it("Links point at /v1/ais/page with an opaque cursor that resolves to the stored HSBC URL", async () => {
    const bank = fakeBank({ accountsBody: paged });
    const e = await testEnv(bank);
    const s = await signIn(e);
    const first = (await (await call(e, "GET", "/v1/ais/accounts/acc-1/transactions", { headers: sessionHeader(s) })).json()) as { Links: Record<string, string> };
    for (const href of Object.values(first.Links)) {
      const u = new URL(href);
      expect(`${u.origin}${u.pathname}`).toBe("https://gateway.test/v1/ais/page");
      expect([...u.searchParams.keys()]).toEqual(["cursor"]);
      expect(href).not.toContain(BANK);
    }

    const next = new URL(first.Links.Next!);
    const res = await call(e, "GET", `${next.pathname}${next.search}`, { headers: sessionHeader(s) });
    expect(res.status).toBe(200);
    expect(((await res.json()) as { Data: { Transaction: { TransactionId: string }[] } }).Data.Transaction[0]!.TransactionId).toBe("2");
    expect(bank.calls.at(-1)!.url).toBe(`${TX}?page=2&fromBookingDateTime=2026-01-01T00:00:00Z`);
  });

  it("400 unknown_cursor for a cursor that was never issued, or issued to another session", async () => {
    const e = await testEnv(fakeBank({ accountsBody: paged }));
    const a = await signIn(e);
    const b = await signIn(e);
    const page = (await (await call(e, "GET", "/v1/ais/accounts/acc-1/transactions", { headers: sessionHeader(a) })).json()) as { Links: Record<string, string> };
    const next = new URL(page.Links.Next!);
    for (const [s, path] of [
      [a, "/v1/ais/page?cursor=never-issued"],
      [a, "/v1/ais/page"],
      [b, `${next.pathname}${next.search}`],
    ] as const) {
      const res = await call(e, "GET", path, { headers: sessionHeader(s) });
      expect(res.status, path).toBe(400);
      expect(await res.json()).toEqual({ error: "unknown_cursor" });
    }
  });

  it("400 host_refused for a stored cursor on any host but the bank host, without calling out", async () => {
    const bank = fakeBank();
    const e = await testEnv(bank);
    const s = await signIn(e);
    const stub = await sessionStub(env.SESSIONS, s);
    const [evil, lookalike, wrongPrefix] = await stub.putCursors(
      ["https://evil.example/obie/open-banking/v4.0/aisp/accounts", `https://${BANK}.evil.example/x`, `https://${BANK}/obie/open-banking/v4.0/pisp/domestic-payments`],
      Math.floor(Date.now() / 1000),
    );
    const before = bank.calls.length;
    for (const c of [evil, lookalike, wrongPrefix]) {
      const res = await call(e, "GET", `/v1/ais/page?cursor=${c}`, { headers: sessionHeader(s) });
      expect(res.status).toBe(400);
      expect(await res.json()).toEqual({ error: "host_refused" });
    }
    expect(bank.calls.length).toBe(before);
  });

  it("a bank body with a Links URL on a foreign host is refused, not proxied", async () => {
    const e = await testEnv(fakeBank({ accountsBody: () => ({ Data: {}, Links: { Next: "https://evil.example/next" } }) }));
    const s = await signIn(e);
    const res = await call(e, "GET", "/v1/ais/accounts/acc-1/transactions", { headers: sessionHeader(s) });
    expect(res.status).toBe(502);
    expect(await res.text()).not.toContain("evil.example");
  });
});
