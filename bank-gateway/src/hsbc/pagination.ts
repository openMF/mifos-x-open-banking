/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */

type Body = { Links?: Record<string, string> } & Record<string, unknown>;

/**
 * Points HSBC's absolute Links (Self/Next/Prev/First/Last) at the Worker so the app never talks to the
 * bank: every link is checked, then `toWorker` maps the whole batch (the AIS handlers store each URL
 * behind an opaque /v1/ais/page cursor). Any link on another host is refused — following it would make
 * the Worker an open proxy.
 */
export async function rewriteLinks<T extends Body>(body: T, bankHost: string, toWorker: (bankUrls: string[]) => Promise<string[]>): Promise<T> {
  if (!body.Links) return body;
  const entries = Object.entries(body.Links).map(([name, href]) => [name, checkedBankUrl(name, href, bankHost)] as const);
  const mapped = await toWorker(entries.map(([, u]) => u));
  return { ...body, Links: Object.fromEntries(entries.map(([name], i) => [name, mapped[i]!])) };
}

function checkedBankUrl(name: string, href: string, bankHost: string): string {
  let u: URL;
  try {
    u = new URL(href);
  } catch {
    throw new Error(`pagination: refusing non-absolute link ${name}`);
  }
  if (u.protocol !== "https:" || u.host !== bankHost || u.username || u.password) {
    throw new Error(`pagination: refusing non-HSBC host ${u.host}`);
  }
  return u.toString();
}
