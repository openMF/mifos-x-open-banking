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

/**
 * Worker-wide cache (one instance, see tokenCache()) for client-credentials access tokens per scope
 * ("cc:accounts", "cc:payments") and the HSBC OIDC discovery + JWKS ("hsbc:oidc"). Values are JSON.
 */
export class TokenCacheDO extends DurableObject<Env> {
  constructor(ctx: DurableObjectState, env: Env) {
    super(ctx, env);
    ctx.storage.sql.exec("CREATE TABLE IF NOT EXISTS cache (key TEXT PRIMARY KEY, value TEXT NOT NULL, expires_at INTEGER NOT NULL)");
  }

  async get(key: string, nowSec: number): Promise<string | null> {
    const row = this.ctx.storage.sql.exec<{ value: string; expires_at: number }>("SELECT value, expires_at FROM cache WHERE key = ?", key).toArray()[0];
    return row && row.expires_at > nowSec ? row.value : null;
  }

  async put(key: string, value: string, expiresAt: number): Promise<void> {
    this.ctx.storage.sql.exec("INSERT OR REPLACE INTO cache (key, value, expires_at) VALUES (?, ?, ?)", key, value, expiresAt);
  }
}

export const tokenCache = (env: Pick<Env, "TOKEN_CACHE">) => env.TOKEN_CACHE.get(env.TOKEN_CACHE.idFromName("global"));
