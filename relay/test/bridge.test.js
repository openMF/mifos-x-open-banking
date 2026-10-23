/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */

/*
 * Run with:  node --test "relay/test/*.test.js"
 *
 * No dependencies and no framework — node:test is built in. This page is the single point of failure
 * for the whole consent journey on every platform, and it shipped untested; the deployed version has
 * been relaying zero parameters since June because it read location.search in a fragment flow.
 */

const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const SITE = path.join(__dirname, '..', 'site');
const { readParams, appUrl, loopbackUrl, webReturnUrl, WEB_RETURN_ORIGINS } = require(path.join(SITE, 'callback', 'bridge.js'));

const HYBRID_FRAGMENT = '#code=abc123&id_token=ey.J.sig&state=st-1';

test('reads the fragment, which is where the hybrid flow actually replies', () => {
  assert.equal(readParams(HYBRID_FRAGMENT, ''), 'code=abc123&id_token=ey.J.sig&state=st-1');
});

test('REGRESSION: a fragment-only response is not silently dropped', () => {
  // The deployed page did `window.location.search || ""`, so this returned '' and it relayed a URL
  // with no parameters at all. Every consent died here.
  assert.notEqual(readParams(HYBRID_FRAGMENT, ''), '');
});

test('falls back to the query if a response ever arrives that way', () => {
  assert.equal(readParams('', '?code=abc&state=st-1'), 'code=abc&state=st-1');
});

test('prefers the fragment when both are present', () => {
  assert.equal(readParams('#code=from-fragment', '?code=from-query'), 'code=from-fragment');
});

test('returns empty when the bank sent nothing', () => {
  assert.equal(readParams('', ''), '');
  assert.equal(readParams('#', '?'), '');
});

test('mobile hand-off targets the scheme and host the app registers', () => {
  assert.equal(
    appUrl('code=abc&state=st-1'),
    'org.mifosx.openbanking://callback?code=abc&state=st-1',
  );
});

test('desktop hand-off rewrites the fragment to a query, because servers cannot see fragments', () => {
  assert.equal(
    loopbackUrl('code=abc&state=st-1'),
    'http://127.0.0.1:8765/callback?code=abc&state=st-1',
  );
});

test('an error response relays through untouched', () => {
  const raw = readParams('#error=access_denied&state=st-1', '');
  assert.equal(appUrl(raw), 'org.mifosx.openbanking://callback?error=access_denied&state=st-1');
});

test('the id_token survives the hand-off intact', () => {
  // The callback validates the nonce inside this JWT; losing or mangling it fails a real consent.
  const raw = readParams(HYBRID_FRAGMENT, '');
  assert.ok(appUrl(raw).includes('id_token=ey.J.sig'));
  assert.ok(loopbackUrl(raw).includes('id_token=ey.J.sig'));
});

test('the page loads the tested logic and keeps a manual fallback', () => {
  const html = fs.readFileSync(path.join(SITE, 'callback', 'index.html'), 'utf8');

  assert.ok(html.includes('src="bridge.js"'), 'page must load the tested logic');
  assert.ok(html.includes('src="relay.js"'), 'page must load the relay flow');
  assert.ok(html.includes('id="retry"'), 'manual fallback missing: browsers block non-gesture scheme hand-off');
});

test('the page has no inline script, so a strict CSP (script-src self) can run it', () => {
  const html = fs.readFileSync(path.join(SITE, 'callback', 'index.html'), 'utf8');
  const scripts = html.match(/<script\b[^>]*>/g) || [];
  assert.ok(scripts.length >= 2);
  for (const tag of scripts) assert.match(tag, /\bsrc="/, `inline script found: ${tag}`);
  assert.doesNotMatch(html, /<style\b/, 'inline <style> would need style-src unsafe-inline');
});

test('the constants match the app, which is what makes the hand-off land', () => {
  const { SCHEME, LOOPBACK_PORT } = require(path.join(SITE, 'callback', 'bridge.js'));
  assert.equal(SCHEME, 'org.mifosx.openbanking'); // cmp-android ConsentRedirectIntent.kt
  assert.equal(LOOPBACK_PORT, 8765); // cmp-desktop ConsentRedirectListener.PORT
});

/* ── web return (state prefix `w<index>.` set by the Worker) ── */

test('WEB_RETURN_ORIGINS matches the Worker, entry for entry and in order', () => {
  const toml = fs.readFileSync(path.join(__dirname, '..', '..', 'bank-gateway', 'wrangler.toml'), 'utf8');
  const m = toml.match(/^WEB_RETURN_ORIGINS\s*=\s*"([^"]*)"/m);
  assert.ok(m, 'bank-gateway/wrangler.toml must declare WEB_RETURN_ORIGINS');
  assert.deepEqual(WEB_RETURN_ORIGINS, m[1].split(',').map((o) => o.trim()).filter(Boolean));
});

test('a w<i>. state returns to that allowlisted origin, fragment-only', () => {
  assert.equal(
    webReturnUrl('code=abc&id_token=ey.J.sig&state=w0.st-1'),
    'http://localhost:8080/#mifos-callback?code=abc&id_token=ey.J.sig&state=w0.st-1',
  );
});

test('an out-of-range, malformed or absent web index never returns to the web', () => {
  for (const raw of ['code=a&state=w9.st', 'code=a&state=w-1.st', 'code=a&state=wx.st', 'code=a&state=w0', 'code=a&state=st-1', 'code=a', 'code=a&state=w01.st'])
    assert.equal(webReturnUrl(raw), null, raw);
});

/* ── relay flow (relay.js) — driven with a fake window so every branch is exercised ── */

const { run } = require(path.join(SITE, 'callback', 'relay.js'));

function fakeEnv({ hash = '', search = '', loopback = 'refused' } = {}) {
  const el = () => ({ textContent: '', style: {}, onclick: null });
  const nodes = { heading: el(), detail: el(), manual: el(), retry: el() };
  const env = {
    location: { hash, search, pathname: '/callback/', href: '', replace: (url) => env.navigated.push(url) },
    navigated: [],
    replaced: [],
    fetched: [],
    timers: [],
    history: { replaceState: (_s, _t, url) => env.replaced.push(url) },
    document: { getElementById: (id) => nodes[id] },
    fetch: (url, opts) => {
      env.fetched.push(url);
      env.fetchOpts = opts;
      return loopback === 'answers' ? Promise.resolve({}) : Promise.reject(new TypeError('Failed to fetch'));
    },
    setTimeout: (fn) => env.timers.push(fn),
    nodes,
  };
  return env;
}
const settle = () => new Promise((r) => setImmediate(r));

test('scrubs the authorisation code from browser history immediately', async () => {
  const env = fakeEnv({ hash: HYBRID_FRAGMENT });
  run(env);
  await settle();
  assert.deepEqual(env.replaced, ['/callback/']);
});

test('desktop listener answers → delivered, and NEVER also hands off to the mobile scheme', async () => {
  const env = fakeEnv({ hash: HYBRID_FRAGMENT, loopback: 'answers' });
  run(env);
  await settle();
  assert.equal(env.fetched[0], 'http://127.0.0.1:8765/callback?code=abc123&id_token=ey.J.sig&state=st-1');
  assert.equal(env.location.href, '', 'custom scheme must not be opened when desktop took it');
  assert.equal(env.fetchOpts.targetAddressSpace, 'loopback', 'Local Network Access needs the loopback declaration');
  assert.equal(env.nodes.heading.textContent, 'Consent received');
});

test('no desktop listener → mobile hand-off, then a tap-to-reopen fallback', async () => {
  const env = fakeEnv({ hash: HYBRID_FRAGMENT, loopback: 'refused' });
  run(env);
  await settle();
  assert.equal(env.location.href, 'org.mifosx.openbanking://callback?code=abc123&id_token=ey.J.sig&state=st-1');
  env.timers.forEach((fn) => fn());
  assert.equal(env.nodes.heading.textContent, 'Open the app to continue');
  assert.equal(env.nodes.manual.style.display, 'block');
});

test('nothing to relay → says so, probes nothing, scrubs nothing', async () => {
  const env = fakeEnv();
  run(env);
  await settle();
  assert.equal(env.nodes.heading.textContent, 'Nothing to return');
  assert.equal(env.fetched.length, 0);
  assert.equal(env.replaced.length, 0);
});

test('web state → scrubs, then replaces to the web origin; no loopback probe, no custom scheme', async () => {
  const env = fakeEnv({ hash: '#code=abc&id_token=ey.J.sig&state=w0.st-1', loopback: 'answers' });
  run(env);
  await settle();
  assert.deepEqual(env.replaced, ['/callback/']);
  assert.deepEqual(env.navigated, ['http://localhost:8080/#mifos-callback?code=abc&id_token=ey.J.sig&state=w0.st-1']);
  assert.equal(env.fetched.length, 0);
  assert.equal(env.location.href, '');
});

test('out-of-range web index → ignored, existing desktop/mobile behaviour', async () => {
  const env = fakeEnv({ hash: '#code=abc&state=w7.st-1', loopback: 'refused' });
  run(env);
  await settle();
  assert.deepEqual(env.navigated, []);
  assert.equal(env.fetched.length, 1);
  assert.equal(env.location.href, 'org.mifosx.openbanking://callback?code=abc&state=w7.st-1');
});

test('desktop and mobile flows never navigate to a web origin', async () => {
  for (const loopback of ['answers', 'refused']) {
    const env = fakeEnv({ hash: HYBRID_FRAGMENT, loopback });
    run(env);
    await settle();
    assert.deepEqual(env.navigated, []);
  }
});

/* ── public root page (what a customer or reviewer sees at the bare domain) ── */

test('root page explains the relay without running any script', () => {
  const html = fs.readFileSync(path.join(SITE, 'index.html'), 'utf8');
  assert.doesNotMatch(html, /<script\b/, 'the public page needs no script at all');
  assert.doesNotMatch(html, /<style\b|style="/, 'no inline styles — CSP style-src self');
  assert.doesNotMatch(html, /<form\b|<input\b/, 'never collect anything on a page that sits in a bank journey');
  assert.ok(html.includes('href="site.css"'), 'styles come from site.css');
});

test('root page links the source, licence and security policy, and disclaims affiliation', () => {
  const html = fs.readFileSync(path.join(SITE, 'index.html'), 'utf8');
  for (const needle of [
    'https://github.com/openMF/mifos-x-open-banking/tree/dev/relay',
    'https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE',
    'https://github.com/openMF/mifos-x-open-banking/blob/dev/SECURITY.md',
    'security-disclosure@mifos.org',
    'not affiliated with HSBC',
    '/callback/',
  ]) assert.ok(html.includes(needle), `root page missing: ${needle}`);
});

/* ── security headers (Cloudflare Pages _headers) ── */

test('_headers ships the strict policy for every page', () => {
  const h = fs.readFileSync(path.join(SITE, '_headers'), 'utf8');
  for (const needle of [
    "default-src 'none'",
    "script-src 'self'",
    'connect-src http://127.0.0.1:8765',
    "frame-ancestors 'none'",
    'Referrer-Policy: no-referrer',
    'Cache-Control: no-store',
    'Strict-Transport-Security: max-age=',
    'X-Content-Type-Options: nosniff',
  ]) assert.ok(h.includes(needle), `_headers missing: ${needle}`);
});

/* ── legal pages + branding ── */

for (const page of ['privacy', 'terms']) {
  test(`${page} page is static, styled from the shared sheet and cross-linked`, () => {
    const html = fs.readFileSync(path.join(SITE, page, 'index.html'), 'utf8');
    assert.doesNotMatch(html, /<script\b/, `${page} needs no script`);
    assert.doesNotMatch(html, /<style\b|style="/, 'no inline styles — CSP style-src self');
    assert.doesNotMatch(html, /<form\b|<input\b/, 'legal pages never collect anything');
    assert.ok(html.includes('href="../site.css"'), 'styles come from the shared sheet');
    assert.match(html, /Last updated \d{1,2} \w+ \d{4}/, 'legal pages carry a dated version');
    for (const needle of ['href="../"', 'not affiliated with HSBC', 'security-disclosure@mifos.org'])
      assert.ok(html.includes(needle), `${page} missing: ${needle}`);
  });
}

test('every public page links both legal pages', () => {
  const pages = {
    'index.html': ['href="privacy/"', 'href="terms/"'],
    'callback/index.html': ['href="../privacy/"', 'href="../terms/"'],
    'privacy/index.html': ['href="../terms/"'],
    'terms/index.html': ['href="../privacy/"'],
  };
  for (const [file, needles] of Object.entries(pages)) {
    const html = fs.readFileSync(path.join(SITE, file), 'utf8');
    for (const n of needles) assert.ok(html.includes(n), `${file} missing ${n}`);
  }
});

test('privacy policy states the facts the relay is built on', () => {
  const html = fs.readFileSync(path.join(SITE, 'privacy', 'index.html'), 'utf8');
  for (const needle of ['127.0.0.1', 'org.mifosx.openbanking://callback', 'no cookies', 'https://www.cloudflare.com/privacypolicy/', 'info@mifos.org', 'https://mifos.org/privacy-policy/'])
    assert.ok(html.includes(needle), `privacy policy missing: ${needle}`);
});

test('brand assets referenced by the pages exist and nothing loads cross-origin', () => {
  for (const f of ['mifos-logo.png', 'mifos-logo-dark.png', 'mifos-mark.svg'])
    assert.ok(fs.statSync(path.join(SITE, 'assets', f)).size > 0, `missing asset ${f}`);
  for (const file of ['index.html', 'privacy/index.html', 'terms/index.html', 'callback/index.html']) {
    const html = fs.readFileSync(path.join(SITE, file), 'utf8');
    const loads = html.match(/<(?:img|script|link)\b[^>]*(?:src|href)="https?:\/\/[^"]*"/g) || [];
    assert.deepEqual(loads, [], `${file} loads a cross-origin resource`);
  }
});

/* ── Android App Links (FR-052): the bank's redirect opens the app directly ── */

test('assetlinks.json verifies the app for the callback App Link', () => {
  const links = JSON.parse(fs.readFileSync(path.join(SITE, '.well-known', 'assetlinks.json'), 'utf8'));
  const manifest = fs.readFileSync(path.join(SITE, '..', '..', 'cmp-android', 'src', 'main', 'AndroidManifest.xml'), 'utf8');
  assert.match(manifest, /android:autoVerify="true"/, 'manifest must declare the verified App Link filter');
  assert.match(manifest, /android:host="mifos-x-open-banking-relay\.pages\.dev" android:pathPrefix="\/callback"/);
  const packages = links.map((l) => l.target.package_name);
  assert.ok(packages.includes('org.mifosx.openbanking'), 'production package must be listed');
  for (const l of links) {
    assert.deepEqual(l.relation, ['delegate_permission/common.handle_all_urls']);
    assert.equal(l.target.namespace, 'android_app');
    for (const fp of l.target.sha256_cert_fingerprints) assert.match(fp, /^([0-9A-F]{2}:){31}[0-9A-F]{2}$/, 'colon-separated upper-case SHA-256');
  }
});
