/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */

import { DurableObject } from "cloudflare:workers";
import type { Env } from "../env";

/*
 * One object per attestation fact, named by attestationStub():
 *   challenge:<challenge>  a single-use challenge (5 min) — takeChallenge() deletes it atomically
 *   key:<keyId>            an App Attest public key (SPKI) + its sign counter; assert() is the single writer
 *   verdict:<sessionHash>  a passed Play Integrity verdict for one session (10 min)
 * Every check-and-set is one synchronous SQL statement, so concurrent requests cannot both win.
 */
export class AttestationDO extends DurableObject<Env> {
  private pub?: Promise<CryptoKey>;

  constructor(ctx: DurableObjectState, env: Env) {
    super(ctx, env);
    ctx.storage.sql.exec("CREATE TABLE IF NOT EXISTS fact (k TEXT PRIMARY KEY, v BLOB, n INTEGER NOT NULL)");
  }

  private sql(query: string, ...args: (string | number | ArrayBuffer)[]) {
    return this.ctx.storage.sql.exec(query, ...args);
  }

  async issueChallenge(expiresAt: number): Promise<void> {
    this.sql("INSERT OR REPLACE INTO fact (k, n) VALUES ('challenge', ?)", expiresAt);
    await this.ctx.storage.setAlarm(expiresAt);
  }

  /** True once for a live challenge; it is gone afterwards. */
  async takeChallenge(now: number): Promise<boolean> {
    return this.sql("DELETE FROM fact WHERE k = 'challenge' AND n > ?", now).rowsWritten > 0;
  }

  /** Stores the key with counter 0; false if this keyId is already registered. */
  async registerKey(spki: Uint8Array): Promise<boolean> {
    return this.sql("INSERT OR IGNORE INTO fact (k, v, n) VALUES ('key', ?, 0)", spki.slice().buffer).rowsWritten > 0;
  }

  /** ECDSA P-256 over `nonce` (signature in r‖s form), then counter must strictly exceed the stored one. */
  async assert(nonce: Uint8Array, signature: Uint8Array, counter: number): Promise<"ok" | "unknown_key" | "signature" | "counter"> {
    const row = this.sql("SELECT v, n FROM fact WHERE k = 'key'").toArray()[0] as { v: ArrayBuffer; n: number } | undefined;
    if (!row) return "unknown_key";
    this.pub ??= crypto.subtle.importKey("spki", row.v, { name: "ECDSA", namedCurve: "P-256" }, false, ["verify"]);
    if (!(await crypto.subtle.verify({ name: "ECDSA", hash: "SHA-256" }, await this.pub, signature, nonce))) return "signature";
    return this.sql("UPDATE fact SET n = ? WHERE k = 'key' AND n < ?", counter, counter).rowsWritten > 0 ? "ok" : "counter";
  }

  async cacheVerdict(expiresAt: number): Promise<void> {
    this.sql("INSERT OR REPLACE INTO fact (k, n) VALUES ('verdict', ?)", expiresAt);
    await this.ctx.storage.setAlarm(expiresAt);
  }

  async hasVerdict(now: number): Promise<boolean> {
    return this.sql("SELECT 1 FROM fact WHERE k = 'verdict' AND n > ?", now).toArray().length === 1;
  }

  /** Only challenge and verdict objects set alarms; both are disposable once expired. */
  async alarm(): Promise<void> {
    await this.ctx.storage.deleteAll();
  }
}

export const attestationStub = (env: Pick<Env, "ATTESTATION">, name: string) => env.ATTESTATION.get(env.ATTESTATION.idFromName(name));
