/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */

// Library spike (phase 06 T3): cbor-x and @peculiar/x509 load and run inside workerd (nodejs_compat).
import "reflect-metadata";
import { X509Certificate, X509CertificateGenerator } from "@peculiar/x509";
import { decode, encode } from "cbor-x";
import { describe, expect, it } from "vitest";
import { APPLE_APP_ATTEST_ROOT_CA_PEM } from "../../src/attest/appleRootCA";

describe("attestation library spike", () => {
  it("cbor-x round-trips a byte-string map", () => {
    const value = { fmt: "apple-appattest", authData: new Uint8Array([1, 2, 3]) };
    const back = decode(encode(value)) as { fmt: string; authData: Uint8Array };
    expect(back.fmt).toBe("apple-appattest");
    expect([...back.authData]).toEqual([1, 2, 3]);
  });

  it("@peculiar/x509 issues and verifies a P-256 certificate with WebCrypto", async () => {
    const keys = (await crypto.subtle.generateKey({ name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"])) as CryptoKeyPair;
    const cert = await X509CertificateGenerator.createSelfSigned({ name: "CN=spike", keys, signingAlgorithm: { name: "ECDSA", hash: "SHA-256" } });
    expect(await new X509Certificate(cert.toString("pem")).verify({ signatureOnly: true })).toBe(true);
  });

  it("parses the embedded Apple App Attestation Root CA (P-384)", () => {
    const root = new X509Certificate(APPLE_APP_ATTEST_ROOT_CA_PEM);
    expect(root.subject).toContain("CN=Apple App Attestation Root CA");
    expect((root.publicKey.algorithm as { namedCurve?: string }).namedCurve).toBe("P-384");
  });
});
