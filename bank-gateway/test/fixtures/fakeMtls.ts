/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */

// Stands in for env.HSBC_MTLS (mTLS cannot run in Miniflare): records every call and answers from a
// queue, falling back to a default responder.

export interface RecordedCall {
  url: string;
  method: string;
  headers: Headers;
  body: string;
}

export function fakeMtls(responder: (call: RecordedCall, index: number) => Response | Promise<Response>) {
  const calls: RecordedCall[] = [];
  const fetcher = {
    async fetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
      const req = new Request(input, init);
      const call = { url: req.url, method: req.method, headers: req.headers, body: new TextDecoder().decode(await req.arrayBuffer()) };
      calls.push(call);
      return responder(call, calls.length - 1);
    },
    connect(): never {
      throw new Error("fakeMtls: connect() not supported");
    },
  } as unknown as Fetcher;
  return { fetcher, calls };
}

export const jsonResponse = (data: unknown, status = 200, headers: Record<string, string> = {}) =>
  new Response(JSON.stringify(data), { status, headers: { "content-type": "application/json", ...headers } });
