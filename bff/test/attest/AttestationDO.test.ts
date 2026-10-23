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
import { attestationStub } from "../../src/attest/AttestationDO";

describe("AttestationDO", () => {
  it("a challenge is consumed exactly once, even under concurrent takes", async () => {
    const stub = attestationStub(env, `challenge:${crypto.randomUUID()}`);
    await stub.issueChallenge(Date.now() + 300_000);
    const takes = await Promise.all([1, 2, 3, 4].map(() => stub.takeChallenge(Date.now())));
    expect(takes.filter(Boolean)).toHaveLength(1);
  });

  it("an expired challenge cannot be taken", async () => {
    const stub = attestationStub(env, `challenge:${crypto.randomUUID()}`);
    await stub.issueChallenge(Date.now() - 1);
    expect(await stub.takeChallenge(Date.now())).toBe(false);
  });

  it("a key registers once", async () => {
    const stub = attestationStub(env, `key:${crypto.randomUUID()}`);
    expect(await stub.registerKey(new Uint8Array([1, 2, 3]))).toBe(true);
    expect(await stub.registerKey(new Uint8Array([4]))).toBe(false);
  });

  it("verdict cache answers until it expires", async () => {
    const stub = attestationStub(env, `verdict:${crypto.randomUUID()}`);
    expect(await stub.hasVerdict(Date.now())).toBe(false);
    await stub.cacheVerdict(Date.now() + 600_000);
    expect(await stub.hasVerdict(Date.now())).toBe(true);
    expect(await stub.hasVerdict(Date.now() + 600_001)).toBe(false);
  });
});
