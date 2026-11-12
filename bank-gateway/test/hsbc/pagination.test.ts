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
import { rewriteLinks } from "../../src/hsbc/pagination";

const BANK = "secure.sandbox.ob.hsbc.co.uk";
const WORKER = "https://gateway.test";
// Maps each bank URL to a Worker URL in one batch (the AIS handlers store cursors here).
const toWorker = async (urls: string[]) => urls.map((u) => `${WORKER}/mapped?u=${encodeURIComponent(u)}`);
const mapped = (u: string) => `${WORKER}/mapped?u=${encodeURIComponent(u)}`;

describe("rewriteLinks", () => {
  it("maps every absolute HSBC link through the mapper, keeping the rest of the body", async () => {
    const body = {
      Data: { Transaction: [] },
      Links: {
        Self: `https://${BANK}/obie/open-banking/v4.0/aisp/accounts/a1/transactions?page=1`,
        Next: `https://${BANK}/obie/open-banking/v4.0/aisp/accounts/a1/transactions?page=2`,
        Last: `https://${BANK}/obie/open-banking/v4.0/aisp/accounts/a1/transactions?page=9`,
      },
      Meta: { TotalPages: 9 },
    };
    expect(await rewriteLinks(body, BANK, toWorker)).toEqual({
      ...body,
      Links: { Self: mapped(body.Links.Self), Next: mapped(body.Links.Next), Last: mapped(body.Links.Last) },
    });
  });

  it("passes a body without Links through untouched", async () => {
    const body = { Data: {} };
    expect(await rewriteLinks(body, BANK, toWorker)).toBe(body);
  });

  it("throws on any other host, a look-alike host, plain http, or a non-URL", async () => {
    for (const bad of [
      "https://evil.example/obie/open-banking/x",
      `https://${BANK}.evil.example/x`,
      `https://evil.example@${BANK}.attacker.example/x`,
      `http://${BANK}/x`,
      "/relative/path",
    ]) {
      await expect(rewriteLinks({ Links: { Next: bad } }, BANK, toWorker), bad).rejects.toThrow(/pagination/);
    }
  });
});
