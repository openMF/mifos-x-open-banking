/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */

export const utf8 = (s: string): Uint8Array => new TextEncoder().encode(s);

export function base64url(bytes: Uint8Array | ArrayBuffer): string {
  let bin = "";
  for (const b of new Uint8Array(bytes)) bin += String.fromCharCode(b);
  return btoa(bin).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/, "");
}

/** Throws on input that is not base64url. */
export function fromBase64url(s: string): Uint8Array {
  if (!/^[A-Za-z0-9_-]*$/.test(s)) throw new Error("not base64url");
  return Uint8Array.from(atob(s.replaceAll("-", "+").replaceAll("_", "/")), (c) => c.charCodeAt(0));
}

export const base64urlJson = (value: unknown): string => base64url(utf8(JSON.stringify(value)));

export const parseBase64urlJson = (s: string): any => JSON.parse(new TextDecoder().decode(fromBase64url(s)));
