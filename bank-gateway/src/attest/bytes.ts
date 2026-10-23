/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */

export type Verdict = { ok: true } | { ok: false; reason: string };
export const OK: Verdict = { ok: true };
export const fail = (reason: string): Verdict => ({ ok: false, reason });

export const sha256 = async (bytes: Uint8Array): Promise<Uint8Array> => new Uint8Array(await crypto.subtle.digest("SHA-256", bytes));

export function concat(...parts: Uint8Array[]): Uint8Array {
  const out = new Uint8Array(parts.reduce((n, p) => n + p.length, 0));
  let o = 0;
  for (const p of parts) {
    out.set(p, o);
    o += p.length;
  }
  return out;
}

export const equal = (a: Uint8Array, b: Uint8Array) => a.length === b.length && a.every((x, i) => x === b[i]);

/** Standard or URL-safe base64, padded or not; null when it is neither. */
export function fromBase64(s: string): Uint8Array | null {
  if (!/^[A-Za-z0-9+/_-]*={0,2}$/.test(s)) return null;
  try {
    return Uint8Array.from(atob(s.replaceAll("-", "+").replaceAll("_", "/")), (c) => c.charCodeAt(0));
  } catch {
    return null;
  }
}

/** A single-use challenge as issued by POST /v1/attest/challenge: base64url of 32 random bytes. */
export const CHALLENGE = /^[A-Za-z0-9_-]{43}$/;
