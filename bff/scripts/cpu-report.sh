#!/usr/bin/env bash
# Copyright 2026 Mifos Initiative — MPL-2.0. See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
#
# cpu-report.sh — check the BFF stays inside the Workers Free CPU budget (p99 < 10 ms, NFR-012).
#
#   --since 1h|24h|7d          Worker-wide p99 cpuTime over a window, from the GraphQL analytics API
#                              (workersInvocationsAdaptive has no per-path dimension).
#   --per-route -- <command>   Per-route p99 while <command> generates traffic: `wrangler tail` events
#                              carry cpuTime + the request URL, grouped by "METHOD /path-template".
#   --routes a,b               (per-route) only report paths containing one of these substrings.
#   --limit-ms N               threshold, default 10.
# Exit 5 when any reported p99 ≥ limit — record the Workers Paid decision in docs/RUNBOOK.md.
set -uo pipefail

SINCE=""; PER_ROUTE=0; ROUTES=""; LIMIT=10; CMD=()
while [ $# -gt 0 ]; do case "$1" in
  --since) SINCE="$2"; shift 2 ;;
  --per-route) PER_ROUTE=1; shift ;;
  --routes) ROUTES="$2"; shift 2 ;;
  --limit-ms) LIMIT="$2"; shift 2 ;;
  --) shift; CMD=("$@"); break ;;
  *) echo "unknown flag: $1" >&2; exit 2 ;;
esac; done
[ -n "$SINCE" ] || [ "$PER_ROUTE" = 1 ] || SINCE=1h

BFF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; cd "$BFF_DIR"
FW="$BFF_DIR"
while [ "$FW" != "/" ] && [ ! -f "$FW/core/scripts/secrets-get.sh" ]; do FW=$(dirname "$FW"); done
[ -f "$FW/core/scripts/secrets-get.sh" ] || { echo "❌ framework root not found" >&2; exit 2; }
umask 077; WORK=$(mktemp -d); trap 'rm -rf "$WORK"' EXIT
for a in mifos-x-open-banking-cloudflare-bff-token mifos-x-cloudflare-account-id; do
  t=$( cd "$FW" && bash core/scripts/secrets-get.sh "$a" --secure-temp 2>/dev/null | tail -n 1 ) && mv "$t" "$WORK/$a" || { echo "❌ vault: $a" >&2; exit 3; }
done
export CLOUDFLARE_API_TOKEN; CLOUDFLARE_API_TOKEN="$(tr -d '\r\n' < "$WORK/mifos-x-open-banking-cloudflare-bff-token")"
export CLOUDFLARE_ACCOUNT_ID; CLOUDFLARE_ACCOUNT_ID="$(tr -d '\r\n' < "$WORK/mifos-x-cloudflare-account-id")"
printf 'Authorization: Bearer %s\n' "$CLOUDFLARE_API_TOKEN" > "$WORK/auth.h"
FAIL=0

if [ -n "$SINCE" ]; then
  case "$SINCE" in 1h) S=3600 ;; 24h) S=86400 ;; 7d) S=604800 ;; *) echo "--since must be 1h, 24h or 7d" >&2; exit 2 ;; esac
  node -e '
    const [acc, since] = process.argv.slice(1);
    const from = new Date(Date.now() - Number(since) * 1000).toISOString();
    process.stdout.write(JSON.stringify({
      query: `query($a:String!,$f:Time!){viewer{accounts(filter:{accountTag:$a}){workersInvocationsAdaptive(limit:10,filter:{scriptName:"mifos-x-open-banking-bff",datetime_geq:$f}){sum{requests errors}quantiles{cpuTimeP50 cpuTimeP99}}}}}`,
      variables: { a: acc, f: from } }));' "$CLOUDFLARE_ACCOUNT_ID" "$S" > "$WORK/q.json"
  curl -sS -H @"$WORK/auth.h" -H 'content-type: application/json' --data @"$WORK/q.json" \
    https://api.cloudflare.com/client/v4/graphql > "$WORK/r.json"
  node -e '
    const r = JSON.parse(require("fs").readFileSync(process.argv[1], "utf8")), lim = Number(process.argv[3]);
    if (r.errors && r.errors.length) { console.error("❌ analytics: " + r.errors.map(e => e.message).join("; ")); process.exit(6); }
    const rows = r.data.viewer.accounts[0]?.workersInvocationsAdaptive ?? [];
    let req = 0, err = 0, p50 = 0, p99 = 0;
    for (const x of rows) { req += x.sum.requests; err += x.sum.errors; p50 = Math.max(p50, x.quantiles.cpuTimeP50); p99 = Math.max(p99, x.quantiles.cpuTimeP99); }
    const ms = us => (us / 1000).toFixed(2);   // analytics reports microseconds
    console.log(`| window | requests | errors | p50 cpuTime (ms) | p99 cpuTime (ms) |\n|---|---:|---:|---:|---:|\n| ${process.argv[2]} | ${req} | ${err} | ${ms(p50)} | ${ms(p99)} |`);
    process.exit(p99 / 1000 >= lim ? 5 : 0);' "$WORK/r.json" "$SINCE" "$LIMIT" || FAIL=$?
fi

if [ "$PER_ROUTE" = 1 ]; then
  [ "${#CMD[@]}" -gt 0 ] || { echo "--per-route needs: -- <traffic command>" >&2; exit 2; }
  npx --yes wrangler@4 tail mifos-x-open-banking-bff --format json > "$WORK/tail.jsonl" 2>/dev/null &
  TAIL=$!; sleep 8                       # tail needs a few seconds to attach
  "${CMD[@]}" >/dev/null 2>&1 || echo "⚠ traffic command exited non-zero (report still produced)"
  sleep 6; kill "$TAIL" 2>/dev/null; wait "$TAIL" 2>/dev/null
  node -e '
    const fs = require("fs"), lim = Number(process.argv[2]), only = (process.argv[3] || "").split(",").filter(Boolean);
    const groups = {};
    // wrangler tail --format json prints pretty JSON objects back to back; split on top-level braces.
    let depth = 0, buf = "";
    for (const ch of fs.readFileSync(process.argv[1], "utf8")) {
      if (ch === "{") depth++;
      if (depth > 0) buf += ch;
      if (ch === "}" && --depth === 0) {
        try {
          const e = JSON.parse(buf), q = e.event?.request;
          if (q && typeof e.cpuTime === "number") {
            const path = new URL(q.url).pathname.replace(/\/[0-9a-f-]{8,}(?=\/|$)/gi, "/{id}");
            (groups[`${q.method} ${path}`] ??= []).push(e.cpuTime);
          }
        } catch {}
        buf = "";
      }
    }
    const keys = Object.keys(groups).filter(k => !only.length || only.some(o => k.includes(o)));
    if (!keys.length) { console.error("❌ no tail events with cpuTime were captured"); process.exit(6); }
    let fail = 0;
    console.log("| route | requests | p99 cpuTime (ms) |\n|---|---:|---:|");
    for (const k of keys.sort()) {
      const v = groups[k].sort((a, b) => a - b), p99 = v[Math.min(v.length - 1, Math.ceil(v.length * 0.99) - 1)];
      if (p99 >= lim) fail = 1;
      console.log(`| ${k} | ${v.length} | ${p99} |`);
    }
    process.exit(fail ? 5 : 0);' "$WORK/tail.jsonl" "$LIMIT" "$ROUTES" || FAIL=$?
fi

[ "$FAIL" = 0 ] || { [ "$FAIL" = 5 ] && echo "❌ a p99 is ≥ ${LIMIT} ms — record the Workers Paid decision in docs/RUNBOOK.md" >&2; exit "$FAIL"; }
