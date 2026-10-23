/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */

import { env, runDurableObjectAlarm, runInDurableObject } from "cloudflare:test";
import { describe, expect, it } from "vitest";
import { hashSessionId, newSession, sessionStub, type SessionDO } from "../../src/session/SessionDO";

const nowSec = () => Math.floor(Date.now() / 1000);

function dumpAll(state: DurableObjectState): string {
  const sql = state.storage.sql;
  const tables = sql.exec<{ name: string }>("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE '\_%' ESCAPE '\\'").toArray();
  return JSON.stringify(tables.map((t) => sql.exec(`SELECT * FROM "${t.name}"`).toArray()));
}

describe("SessionDO", () => {
  it("mints a 256-bit session id and stores only its SHA-256", async () => {
    const rawId = await newSession(env.SESSIONS, nowSec());
    expect(rawId).toMatch(/^[A-Za-z0-9_-]{43}$/); // 32 bytes, base64url
    const stub = await sessionStub(env.SESSIONS, rawId);
    await runInDurableObject(stub, async (instance: SessionDO, state) => {
      const rows = state.storage.sql.exec<{ id_hash: string }>("SELECT id_hash FROM session").toArray();
      expect(rows).toEqual([{ id_hash: await hashSessionId(rawId) }]);
      expect(rows[0]!.id_hash).not.toBe(rawId);
      expect(dumpAll(state)).not.toContain(rawId);
      expect(await instance.exists()).toBe(true);
    });
  });

  it("an unknown session id resolves to an empty object", async () => {
    expect(await (await sessionStub(env.SESSIONS, "not-a-real-session")).exists()).toBe(false);
  });

  it("stores PSU tokens per leg and rejects unknown legs", async () => {
    const stub = await sessionStub(env.SESSIONS, await newSession(env.SESSIONS, nowSec()));
    await stub.putTokens("ais", { access: "a1", refresh: "r1", expiresAt: 100 });
    await stub.putTokens("payment:domestic:c-1", { access: "p1", refresh: "pr1", expiresAt: 200 });
    expect(await stub.getTokens("ais")).toEqual({ access: "a1", refresh: "r1", expiresAt: 100 });
    expect(await stub.getTokens("payment:domestic:c-1")).toEqual({ access: "p1", refresh: "pr1", expiresAt: 200 });
    expect(await stub.getTokens("vrp:c-2")).toBeNull();
    await expect(runInDurableObject(stub, (i: SessionDO) => i.putTokens("admin", { access: "x", expiresAt: 1 }))).rejects.toThrow(/leg/);
  });

  it("pending flows expire at most 600 s after creation and are consumed once", async () => {
    const stub = await sessionStub(env.SESSIONS, await newSession(env.SESSIONS, nowSec()));
    const t = nowSec();
    await stub.createPendingFlow({ flowId: "f1", state: "s", nonce: "n", leg: "ais", consentId: "c1", nowSec: t });
    await runInDurableObject(stub, async (_i: SessionDO, state) => {
      const [row] = state.storage.sql.exec<{ created_at: number; expires_at: number }>("SELECT created_at, expires_at FROM flows").toArray();
      expect(row!.expires_at - row!.created_at).toBeLessThanOrEqual(600);
      expect(await state.storage.getAlarm()).toBe(row!.expires_at * 1000);
    });
    expect(await stub.takePendingFlow("f1", t + 10)).toEqual({ flowId: "f1", state: "s", nonce: "n", leg: "ais", consentId: "c1", createdAt: t, expiresAt: t + 600 });
    expect(await stub.takePendingFlow("f1", t + 10)).toBeNull();

    await stub.createPendingFlow({ flowId: "f2", state: "s", nonce: "n", leg: "ais", consentId: "c1", nowSec: t });
    expect(await stub.takePendingFlow("f2", t + 601)).toBeNull();
  });

  it("the alarm prunes expired flows and re-arms for the next one", async () => {
    const stub = await sessionStub(env.SESSIONS, await newSession(env.SESSIONS, nowSec()));
    const t = nowSec();
    await stub.createPendingFlow({ flowId: "old", state: "s", nonce: "n", leg: "ais", consentId: "c", nowSec: t - 1000 });
    await stub.createPendingFlow({ flowId: "new", state: "s", nonce: "n", leg: "ais", consentId: "c", nowSec: t });
    expect(await runDurableObjectAlarm(stub)).toBe(true);
    await runInDurableObject(stub, async (_i: SessionDO, state) => {
      expect(state.storage.sql.exec("SELECT flow_id FROM flows").toArray()).toEqual([{ flow_id: "new" }]);
      expect(await state.storage.getAlarm()).toBe((t + 600) * 1000);
    });
  });

  it("concurrent refresh(leg) calls run the refresher once, one at a time", async () => {
    const stub = await sessionStub(env.SESSIONS, await newSession(env.SESSIONS, nowSec()));
    await stub.putTokens("ais", { access: "old-a", refresh: "old-r", expiresAt: 1 });
    await runInDurableObject(stub, async (instance: SessionDO) => {
      let calls = 0;
      let active = 0;
      let maxActive = 0;
      const refresher = async (refreshToken: string) => {
        calls++;
        active++;
        maxActive = Math.max(maxActive, active);
        expect(refreshToken).toBe("old-r");
        await new Promise((r) => setTimeout(r, 20));
        active--;
        return { access: "new-a", refresh: "new-r", expiresAt: 2 };
      };
      const results = await Promise.all([instance.refresh("ais", "old-a", refresher), instance.refresh("ais", "old-a", refresher)]);
      expect(results).toEqual(["new-a", "new-a"]);
      expect(calls).toBe(1);
      expect(maxActive).toBe(1);
      expect(await instance.getTokens("ais")).toEqual({ access: "new-a", refresh: "new-r", expiresAt: 2 });

      // A caller that saw the already-replaced token gets the current one without a second refresh.
      expect(await instance.refresh("ais", "old-a", refresher)).toBe("new-a");
      expect(calls).toBe(1);
    });
  });

  it("keeps the old refresh token when HSBC does not rotate it", async () => {
    const stub = await sessionStub(env.SESSIONS, await newSession(env.SESSIONS, nowSec()));
    await stub.putTokens("vrp:c-9", { access: "a", refresh: "r", expiresAt: 1 });
    await runInDurableObject(stub, async (instance: SessionDO) => {
      await instance.refresh("vrp:c-9", "a", async () => ({ access: "a2", expiresAt: 5 }));
      expect(await instance.getTokens("vrp:c-9")).toEqual({ access: "a2", refresh: "r", expiresAt: 5 });
    });
  });
});
