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
import { base64url, utf8 } from "../crypto/base64url";
import type { Env } from "../env";

/*
 * One Durable Object per app session (D8). The app holds only the opaque 256-bit session id; the
 * object is addressed by — and stores only — SHA-256(id), so its storage never contains the id itself.
 * It custodies the PSU tokens and consent id per leg, the pending consent flows, the payment ids this
 * session created, and the pagination cursors handed to the app in place of HSBC Links URLs.
 */

const PENDING_FLOW_TTL_SECONDS = 600;
const CURSOR_TTL_SECONDS = 3600;
// payment:{product}:{consentId} — 5-minute PSU token, no refresh (HSBC issues none for payments);
// vrp:{consentId} — access + refresh until the consent ends, refreshed under the same lock as ais.
const LEG = /^(ais|payment:[a-z-]{1,40}:[\w-]{1,128}|vrp:[\w-]{1,128})$/;

export interface TokenSet {
  access: string;
  refresh?: string;
  expiresAt: number; // epoch seconds
}

export interface PendingFlow {
  flowId: string;
  state: string;
  nonce: string;
  leg: string;
  consentId: string;
  createdAt: number;
  expiresAt: number;
}

export async function hashSessionId(rawId: string): Promise<string> {
  return base64url(await crypto.subtle.digest("SHA-256", utf8(rawId)));
}

/** Mints a session and returns its raw id — the only time the raw id exists server-side. */
export async function newSession(ns: DurableObjectNamespace<SessionDO>, nowSec: number): Promise<string> {
  const rawId = base64url(crypto.getRandomValues(new Uint8Array(32)));
  const idHash = await hashSessionId(rawId);
  await ns.get(ns.idFromName(idHash)).create(idHash, nowSec);
  return rawId;
}

/** The object for a presented session id; an unknown id lands on an empty object (exists() → false). */
export async function sessionStub(ns: DurableObjectNamespace<SessionDO>, rawId: string): Promise<DurableObjectStub<SessionDO>> {
  return ns.get(ns.idFromName(await hashSessionId(rawId)));
}

export class SessionDO extends DurableObject<Env> {
  private refreshes = new Map<string, Promise<string>>();

  constructor(ctx: DurableObjectState, env: Env) {
    super(ctx, env);
    ctx.storage.sql.exec(`
      CREATE TABLE IF NOT EXISTS session (id_hash TEXT PRIMARY KEY, created_at INTEGER NOT NULL);
      CREATE TABLE IF NOT EXISTS tokens (leg TEXT PRIMARY KEY, access TEXT NOT NULL, refresh TEXT, expires_at INTEGER NOT NULL);
      CREATE TABLE IF NOT EXISTS flows (flow_id TEXT PRIMARY KEY, state TEXT NOT NULL, nonce TEXT NOT NULL, leg TEXT NOT NULL,
        consent_id TEXT NOT NULL, created_at INTEGER NOT NULL, expires_at INTEGER NOT NULL);
      CREATE TABLE IF NOT EXISTS consents (leg TEXT PRIMARY KEY, consent_id TEXT NOT NULL);
      CREATE TABLE IF NOT EXISTS cursors (cursor TEXT PRIMARY KEY, url TEXT NOT NULL, expires_at INTEGER NOT NULL);
      CREATE TABLE IF NOT EXISTS payments (product TEXT NOT NULL, payment_id TEXT NOT NULL, PRIMARY KEY (product, payment_id));
    `);
  }

  async create(idHash: string, nowSec: number): Promise<void> {
    this.ctx.storage.sql.exec("INSERT INTO session (id_hash, created_at) VALUES (?, ?)", idHash, nowSec);
  }

  async exists(): Promise<boolean> {
    return this.ctx.storage.sql.exec("SELECT 1 FROM session").toArray().length > 0;
  }

  async putTokens(leg: string, t: TokenSet): Promise<void> {
    if (!LEG.test(leg)) throw new Error(`unknown token leg ${leg.slice(0, 40)}`);
    this.ctx.storage.sql.exec(
      "INSERT OR REPLACE INTO tokens (leg, access, refresh, expires_at) VALUES (?, ?, ?, ?)",
      leg,
      t.access,
      t.refresh ?? null,
      t.expiresAt,
    );
  }

  async getTokens(leg: string): Promise<TokenSet | null> {
    return this.readTokens(leg);
  }

  private readTokens(leg: string): TokenSet | null {
    const row = this.ctx.storage.sql
      .exec<{ access: string; refresh: string | null; expires_at: number }>("SELECT access, refresh, expires_at FROM tokens WHERE leg = ?", leg)
      .toArray()[0];
    if (!row) return null;
    return row.refresh === null ? { access: row.access, expiresAt: row.expires_at } : { access: row.access, refresh: row.refresh, expiresAt: row.expires_at };
  }

  async putConsent(leg: string, consentId: string): Promise<void> {
    if (!LEG.test(leg)) throw new Error(`unknown token leg ${leg.slice(0, 40)}`);
    this.ctx.storage.sql.exec("INSERT OR REPLACE INTO consents (leg, consent_id) VALUES (?, ?)", leg, consentId);
  }

  async getConsent(leg: string): Promise<string | null> {
    return this.ctx.storage.sql.exec<{ consent_id: string }>("SELECT consent_id FROM consents WHERE leg = ?", leg).toArray()[0]?.consent_id ?? null;
  }

  /** Records a payment this session submitted, so only this session may read it back. */
  async putPayment(product: string, paymentId: string): Promise<void> {
    this.ctx.storage.sql.exec("INSERT OR IGNORE INTO payments (product, payment_id) VALUES (?, ?)", product, paymentId);
  }

  async hasPayment(product: string, paymentId: string): Promise<boolean> {
    return this.ctx.storage.sql.exec("SELECT 1 FROM payments WHERE product = ? AND payment_id = ?", product, paymentId).toArray().length > 0;
  }

  /** Stores each URL under a fresh opaque cursor (same order); expired cursors are dropped first. */
  async putCursors(urls: string[], nowSec: number): Promise<string[]> {
    const sql = this.ctx.storage.sql;
    sql.exec("DELETE FROM cursors WHERE expires_at <= ?", nowSec);
    return urls.map((url) => {
      const cursor = base64url(crypto.getRandomValues(new Uint8Array(16)));
      sql.exec("INSERT INTO cursors (cursor, url, expires_at) VALUES (?, ?, ?)", cursor, url, nowSec + CURSOR_TTL_SECONDS);
      return cursor;
    });
  }

  async getCursor(cursor: string, nowSec: number): Promise<string | null> {
    return this.ctx.storage.sql.exec<{ url: string }>("SELECT url FROM cursors WHERE cursor = ? AND expires_at > ?", cursor, nowSec).toArray()[0]?.url ?? null;
  }

  /** Logout: forget the session, its tokens, consents, flows and cursors (exists() → false afterwards). */
  async wipe(): Promise<void> {
    for (const t of ["session", "tokens", "consents", "flows", "cursors", "payments"]) this.ctx.storage.sql.exec(`DELETE FROM ${t}`);
    await this.ctx.storage.deleteAlarm();
  }

  async createPendingFlow(f: { flowId: string; state: string; nonce: string; leg: string; consentId: string; nowSec: number }): Promise<void> {
    if (!LEG.test(f.leg)) throw new Error(`unknown token leg ${f.leg.slice(0, 40)}`);
    const expiresAt = f.nowSec + PENDING_FLOW_TTL_SECONDS;
    this.ctx.storage.sql.exec("INSERT INTO flows VALUES (?, ?, ?, ?, ?, ?, ?)", f.flowId, f.state, f.nonce, f.leg, f.consentId, f.nowSec, expiresAt);
    await this.armAlarm();
  }

  /** Returns the flow once and deletes it; an expired or unknown flow is null. */
  async takePendingFlow(flowId: string, nowSec: number): Promise<PendingFlow | null> {
    const row = this.ctx.storage.sql
      .exec<{ state: string; nonce: string; leg: string; consent_id: string; created_at: number; expires_at: number }>("SELECT * FROM flows WHERE flow_id = ?", flowId)
      .toArray()[0];
    if (!row) return null;
    this.ctx.storage.sql.exec("DELETE FROM flows WHERE flow_id = ?", flowId);
    if (row.expires_at <= nowSec) return null;
    return { flowId, state: row.state, nonce: row.nonce, leg: row.leg, consentId: row.consent_id, createdAt: row.created_at, expiresAt: row.expires_at };
  }

  /**
   * Single-writer refresh per leg: concurrent callers share one in-flight refresh. `staleAccess` is the
   * token the caller saw rejected — if it was already replaced, the current token is returned without
   * refreshing again. Returns the access token to retry with.
   */
  async refresh(leg: string, staleAccess: string, refresher: (refreshToken: string) => Promise<TokenSet>): Promise<string> {
    const inflight = this.refreshes.get(leg);
    if (inflight) return inflight;
    // No await between the lock check above and the lock set below — that is what makes it single-writer.
    const current = this.readTokens(leg);
    if (!current) throw new Error("no tokens for this leg");
    if (current.access !== staleAccess) return current.access;
    if (!current.refresh) throw new Error("no refresh token for this leg");

    const refreshToken = current.refresh;
    // .finally always runs asynchronously, so the lock is released only after it was set below.
    const run = (async () => {
      const next = await refresher(refreshToken);
      await this.putTokens(leg, { ...next, refresh: next.refresh ?? refreshToken });
      return next.access;
    })().finally(() => this.refreshes.delete(leg));
    this.refreshes.set(leg, run);
    return run;
  }

  async alarm(): Promise<void> {
    this.ctx.storage.sql.exec("DELETE FROM flows WHERE expires_at <= ?", Math.floor(Date.now() / 1000));
    await this.armAlarm();
  }

  /** Alarm at the earliest pending-flow expiry, or none. */
  private async armAlarm(): Promise<void> {
    const next = this.ctx.storage.sql.exec<{ t: number | null }>("SELECT MIN(expires_at) AS t FROM flows").toArray()[0]?.t;
    if (next == null) await this.ctx.storage.deleteAlarm();
    else await this.ctx.storage.setAlarm(next * 1000);
  }
}
