/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */

/**
 * Web sign-in through the bank gateway Cloudflare Worker (GOAL AC14), driven by Playwright.
 *
 *   ./gradlew :cmp-web:jsBrowserDistribution
 *   bash .claude-runtime/scripts/web-debug-bootstrap.sh run <this file>     # from the framework root
 *
 * Serves the built distribution on http://localhost:8080 (the Worker's CORS_ORIGINS), opens the app,
 * walks onboarding → login → "Connect", and asserts:
 *   1. the app loads and talks only to the Worker (never to an HSBC host directly),
 *   2. POST /v1/consents/ais succeeds (CORS preflight + X-Client-Platform: web, no X-Attestation),
 *   3. the tab lands on the Worker-signed authorize URL on sandbox.ob.hsbc.co.uk.
 *   4. the authorize `state` carries the web return prefix `w0.` (WEB_RETURN_ORIGINS index of localhost:8080),
 *      in the query and in the signed request object (only the prefix is checked; the JWT is never printed).
 * The bank login itself needs a human (sandbox PSU credentials + SCA). Unattended, the run then simulates
 * the return leg: it opens the live relay with a fake code / id_token and the REAL state (the app rejects
 * any other state locally, before calling the Worker) and asserts
 *   5. the relay hands the tab to http://localhost:8080/#mifos-callback?…,
 *   6. the app takes it off the hash and POSTs /v1/auth/complete, which the Worker refuses (400/401 —
 *      the fake id_token fails verification). That proves the wiring end to end, short of a bank login.
 * With HSBC_PSU_DONE=1 and a human driving a headed browser (HEADLESS=0), it instead waits for the
 * account list.
 *
 * Env: WEB_DIST (default: cmp-web/build/dist/js/productionExecutable under the cwd, the app repo or the
 *      framework root),
 *      GATEWAY_BASE_URL (default the reference Worker), HEADLESS (default 1), OUT (screenshots dir).
 */

import { chromium, type Request } from "playwright"
import { createServer } from "node:http"
import { existsSync, mkdirSync, readFileSync, statSync } from "node:fs"
import { extname, join, normalize } from "node:path"
import { strict as assert } from "node:assert"

const DIST_REL = join("cmp-web", "build", "dist", "js", "productionExecutable")
const DIST = process.env.WEB_DIST ??
  [process.cwd(), join(process.cwd(), "workspaces/mifos-x/mifos-x-open-banking/source/mifos-x-open-banking")]
    .map((root) => join(root, DIST_REL))
    .find((d) => existsSync(join(d, "index.html"))) ?? DIST_REL
const GATEWAY = process.env.GATEWAY_BASE_URL ?? "https://mifos-x-open-banking-gateway.mifos-x-open-banking.workers.dev"
const PORT = 8080
const BASE = `http://localhost:${PORT}`
const OUT = process.env.OUT ?? `/tmp/web-debug/gateway-web-signin-${Date.now()}`
const HEADLESS = process.env.HEADLESS !== "0"
const RELAY = process.env.RELAY_URL ?? "https://mifos-x-open-banking-relay.pages.dev/callback/"

const TYPES: Record<string, string> = {
  ".html": "text/html", ".js": "text/javascript", ".mjs": "text/javascript", ".wasm": "application/wasm",
  ".css": "text/css", ".json": "application/json", ".png": "image/png", ".svg": "image/svg+xml",
  ".ico": "image/x-icon", ".ttf": "font/ttf", ".otf": "font/otf", ".xml": "text/xml",
}

async function main() {
assert.ok(existsSync(join(DIST, "index.html")), `no web build at ${DIST} — run ./gradlew :cmp-web:jsBrowserDistribution`)
mkdirSync(OUT, { recursive: true })

const server = createServer((req, res) => {
  const path = normalize(decodeURIComponent((req.url ?? "/").split("?")[0]!)).replace(/^(\.\.[/\\])+/, "")
  let file = join(DIST, path)
  if (!existsSync(file) || statSync(file).isDirectory()) file = join(DIST, "index.html")
  res.writeHead(200, { "content-type": TYPES[extname(file)] ?? "application/octet-stream" })
  res.end(readFileSync(file))
}).listen(PORT)

const browser = await chromium.launch({ headless: HEADLESS })
const page = await browser.newPage({ viewport: { width: 420, height: 900 } })
const gatewayCalls: { method: string; path: string; status?: number; headers: Record<string, string> }[] = []
const bankCalls: string[] = []
const consoleErrors: string[] = []
let authorizeNav: string | undefined
let statePrefix: string | undefined
let returnLanding: string | undefined
page.on("console", (m) => { if (m.type() === "error") consoleErrors.push(m.text()) })
page.on("request", (r: Request) => {
  const u = new URL(r.url())
  if (r.url().startsWith(GATEWAY)) gatewayCalls.push({ method: r.method(), path: u.pathname, headers: r.headers() })
  // Only what the APP sends: once the tab is on HSBC's own pages their XHRs are HSBC's business.
  else if (/hsbc\.(co\.uk|com)$/.test(u.hostname) && r.resourceType() !== "document" && r.frame().url().startsWith(BASE)) bankCalls.push(r.url())
  if (r.isNavigationRequest() && u.hostname === "sandbox.ob.hsbc.co.uk" && !authorizeNav) authorizeNav = r.url()
})
page.on("response", (r) => {
  const hit = gatewayCalls.findLast((c) => r.url().startsWith(GATEWAY) && c.path === new URL(r.url()).pathname && c.status === undefined)
  if (hit) hit.status = r.status()
})

let step = 0
const shot = async (name: string) => page.screenshot({ path: join(OUT, `${String(++step).padStart(2, "0")}-${name}.png`) })
/** Compose web paints into a canvas: tap by position, as a user would. */
const tap = async (xFraction: number, yFraction: number) => {
  const v = page.viewportSize()!
  await page.mouse.click(v.width * xFraction, v.height * yFraction)
}
const report = (stoppedAt: string) =>
  console.log(JSON.stringify({ stoppedAt, url: page.url().replace(/#.*/, "#…"), statePrefix, returnLanding, authorizeNavHost: authorizeNav && new URL(authorizeNav).host, gatewayCalls, bankCalls, consoleErrors: consoleErrors.slice(0, 10), out: OUT }, null, 2))

try {
  await page.goto(BASE)
  await page.waitForSelector("canvas", { state: "attached", timeout: 60_000 })
  await page.waitForTimeout(4_000)
  await shot("app-loaded")

  // Onboarding → login → Continue → Connect: the primary button sits at the bottom of each screen.
  const authorize = page.waitForURL(/sandbox\.ob\.hsbc\.co\.uk/, { timeout: 90_000 })
  for (let i = 0; i < 6 && !/hsbc\.co\.uk/.test(page.url()); i++) {
    const v = page.viewportSize()!
    await page.mouse.move(v.width / 2, v.height / 2)
    for (let s = 0; s < 6; s++) await page.mouse.wheel(0, 2_000) // the connect button ends a long list
    await page.waitForTimeout(800)
    // Onboarding "Continue" is the last row; on the login screen "Continue to HSBC" sits above "Cancel".
    await tap(0.5, i === 0 ? 0.955 : 0.89)
    await page.waitForTimeout(2_500)
    if (!/hsbc\.co\.uk/.test(page.url())) await shot(`tap-${i}`)
  }
  await authorize

  const consent = gatewayCalls.find((c) => c.method === "POST" && c.path === "/v1/consents/ais")
  assert.ok(consent, "expected POST /v1/consents/ais to the Worker")
  assert.equal(consent.status, 200, "consent creation through the Worker")
  assert.equal(consent.headers["x-client-platform"], "web")
  assert.equal(consent.headers["x-attestation"], undefined, "web is not attested (GOAL D3)")
  assert.deepEqual(bankCalls, [], "the app must never call HSBC directly")
  assert.match(authorizeNav ?? "", /^https:\/\/sandbox\.ob\.hsbc\.co\.uk\/obie\/open-banking\/v1\.1\/oauth2\/authorize\?/)
  const authorizeParams = new URL(authorizeNav!).searchParams
  const state = authorizeParams.get("state") ?? ""
  const requestState = String(JSON.parse(Buffer.from(authorizeParams.get("request")!.split(".")[1]!, "base64url").toString()).state ?? "")
  assert.match(state, /^w0\./, "web sign-in state must carry the w0. return prefix")
  assert.equal(requestState, state, "the signed request object carries the same state")
  statePrefix = state.slice(0, 3)
  await page.waitForLoadState("domcontentloaded").catch(() => {})
  await shot("hsbc-authorize")

  if (process.env.HSBC_PSU_DONE !== "1") {
    // Return leg, simulated: what the relay receives after a bank login, with a fake code / id_token.
    const landed = page.waitForURL((u) => {
      const hit = u.origin === BASE && u.hash.startsWith("#mifos-callback?")
      if (hit) returnLanding = `${u.origin}${u.pathname}#mifos-callback?…` // the app clears the hash right after
      return hit
    }, { timeout: 30_000 })
    const complete = page.waitForResponse((r) => r.url() === `${GATEWAY}/v1/auth/complete` && r.request().method() === "POST", { timeout: 60_000 })
    await page.goto(`${RELAY}#code=fake-code&id_token=fake.id.token&state=${encodeURIComponent(state)}`)
    await landed
    const res = await complete
    assert.ok([400, 401].includes(res.status()), `fake code must be refused by the Worker, got ${res.status()}`)
    await page.waitForTimeout(3_000)
    assert.equal(new URL(page.url()).hash, "", "the app clears the callback from the address bar")
    await shot("after-simulated-return")
    report("simulated return → POST /v1/auth/complete refused (bank login needs a human: HEADLESS=0 HSBC_PSU_DONE=1)")
  } else {
    // A human signs in at HSBC; the relay then hands the result back and the app lists accounts.
    await page.waitForURL((u) => u.origin === BASE, { timeout: 600_000 })
    await page.waitForTimeout(8_000)
    await shot("after-return")
    assert.ok(gatewayCalls.some((c) => c.path === "/v1/auth/complete" && c.status === 200), "expected /v1/auth/complete")
    assert.ok(gatewayCalls.some((c) => c.path === "/v1/ais/accounts" && c.status === 200), "expected /v1/ais/accounts")
    report("accounts")
  }
} catch (e) {
  await shot("failure").catch(() => {})
  report(`error: ${(e as Error).message}`)
  process.exitCode = 1
} finally {
  await browser.close()
  server.close()
}
}

main()
