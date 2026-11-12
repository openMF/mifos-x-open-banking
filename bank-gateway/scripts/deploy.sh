#!/usr/bin/env bash
# Copyright 2026 Mifos Initiative — MPL-2.0. See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
#
# deploy.sh — deploy the bank gateway Worker (mifos-x-open-banking-gateway) with credentials from the framework vault.
#
#   1. resolve every credential from the vault into a 0700 temp dir (shredded on exit)
#   2. split transport.p12 into cert + key PEM and upload them as a Cloudflare mTLS certificate
#      (reused by name if already uploaded) → write its public ID into wrangler.toml
#   3. wrangler deploy
#   4. set the Worker secrets in one `wrangler secret bulk` call — including the attestation credentials
#      (PLAY_INTEGRITY_SA_JSON, APPLE_TEAM_ID) when they resolve; when they don't, they are skipped and
#      named, and the Worker runs attestation in its declared unconfigured mode (ATTESTATION_MODE)
#   5. run scripts/smoke.sh against the deployed URL
#
# Nothing secret is ever printed, passed on a command line, or written outside the temp dir.
# secrets-get.sh may print vault re-auth status lines before the temp-file path, so only its last line is used.
# Needs: framework checkout above this repo (core/scripts/secrets-get.sh), node, openssl, npx.
#
# Usage: bash scripts/deploy.sh [--dry-run] [--no-smoke]
set -euo pipefail

DRY=0; SMOKE=1
while [ $# -gt 0 ]; do case "$1" in
  --dry-run) DRY=1; shift ;;
  --no-smoke) SMOKE=0; shift ;;
  --env) shift 2 ;;   # accepted for compatibility; the Worker has a single environment
  *) echo "unknown flag: $1" >&2; exit 2 ;;
esac; done

GATEWAY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; cd "$GATEWAY_DIR"
FW="$GATEWAY_DIR"
while [ "$FW" != "/" ] && [ ! -f "$FW/core/scripts/secrets-get.sh" ]; do FW=$(dirname "$FW"); done
[ -f "$FW/core/scripts/secrets-get.sh" ] || { echo "❌ framework root (core/scripts/secrets-get.sh) not found above $GATEWAY_DIR" >&2; exit 2; }

umask 077
WORK=$(mktemp -d); chmod 700 "$WORK"
cleanup() { find "$WORK" -type f -exec sh -c 'command -v shred >/dev/null && shred -u "$1" || rm -P "$1" 2>/dev/null || rm -f "$1"' _ {} \; ; rm -rf "$WORK"; }
trap cleanup EXIT

ALIASES=(
  mifos-x-open-banking-cloudflare-gateway-token mifos-x-cloudflare-account-id
  mifos-x-open-banking-hsbc-private-key mifos-x-open-banking-hsbc-transport-p12
  mifos-x-open-banking-hsbc-transport-p12-password mifos-x-open-banking-hsbc-client-id
  mifos-x-open-banking-hsbc-kid mifos-x-open-banking-hsbc-software-statement
  mifos-x-open-banking-hsbc-signing-issuer
)
for a in "${ALIASES[@]}"; do
  t=$( cd "$FW" && bash core/scripts/secrets-get.sh "$a" --secure-temp 2>/dev/null | tail -n 1 ) || { echo "❌ vault: cannot resolve $a" >&2; exit 3; }
  [ -s "$t" ] || { echo "❌ vault: $a is empty" >&2; exit 3; }
  mv "$t" "$WORK/$a"
done
echo "✓ resolved ${#ALIASES[@]} credentials from the vault"

# Attestation credentials (phase 06) are optional until handed off. A missing one is skipped and named —
# Both are ORG-level (mifos-x): the Firebase SA of the mifos-mobile-apps Cloud project the app is registered in,
# and the Mifos Apple team id.
# never silent: the Worker then fails closed (503 attestation_unconfigured) unless wrangler.toml sets
# ATTESTATION_MODE = "report-only".
ATTEST_SKIPPED=""
for pair in mifos-x-firebase-service-account:PLAY_INTEGRITY_SA_JSON mifos-x-apple-team-id:APPLE_TEAM_ID; do
  a="${pair%%:*}"
  if t=$( cd "$FW" && bash core/scripts/secrets-get.sh "$a" --secure-temp 2>/dev/null | tail -n 1 ) && [ -s "$t" ]; then
    mv "$t" "$WORK/$a"
  else
    [ -n "${t:-}" ] && rm -f "$t"
    ATTEST_SKIPPED="$ATTEST_SKIPPED ${pair#*:}"
  fi
done
if [ -n "$ATTEST_SKIPPED" ]; then
  echo "⚠ attestation credentials not in the vault, skipped:$ATTEST_SKIPPED — Worker attestation runs as ATTESTATION_MODE in wrangler.toml"
else
  echo "✓ resolved attestation credentials (PLAY_INTEGRITY_SA_JSON, APPLE_TEAM_ID)"
fi

export CLOUDFLARE_API_TOKEN; CLOUDFLARE_API_TOKEN="$(tr -d '\r\n' < "$WORK/mifos-x-open-banking-cloudflare-gateway-token")"
export CLOUDFLARE_ACCOUNT_ID; CLOUDFLARE_ACCOUNT_ID="$(tr -d '\r\n' < "$WORK/mifos-x-cloudflare-account-id")"
printf 'Authorization: Bearer %s\n' "$CLOUDFLARE_API_TOKEN" > "$WORK/auth.h"

# ── signing key must be PKCS#8 for WebCrypto (checked in memory, prints a verdict only) ──
node -e 'const s=require("fs").readFileSync(process.argv[1],"utf8");process.exit(s.includes("BEGIN PRIVATE KEY")?0:1)' \
  "$WORK/mifos-x-open-banking-hsbc-private-key" || { echo "❌ signing key is not PKCS#8 — convert with: openssl pkcs8 -topk8 -nocrypt" >&2; exit 4; }

# ── split transport.p12 (password read from a file, never argv) ──
P12="$WORK/mifos-x-open-banking-hsbc-transport-p12"; PW="file:$WORK/mifos-x-open-banking-hsbc-transport-p12-password"
# Re-encode through x509/pkey: pkcs12 output carries "Bag Attributes" text that Cloudflare accepts on upload
# but cannot use when presenting the certificate (the binding then fails with an empty 500).
split_p12() { openssl pkcs12 -in "$P12" -passin "$PW" "$@" -clcerts -nokeys 2>/dev/null | openssl x509 -out "$WORK/transport.cert.pem" 2>/dev/null \
           && openssl pkcs12 -in "$P12" -passin "$PW" "$@" -nocerts -nodes 2>/dev/null | openssl pkey -out "$WORK/transport.key.pem" 2>/dev/null; }
split_p12 || split_p12 -legacy || { echo "❌ cannot open transport.p12 with the vaulted password" >&2; exit 4; }
echo "✓ transport certificate split for upload"

CERT_NAME="mifos-x-open-banking-hsbc-transport"
cert_id_by_name() {
  curl -fsS -H @"$WORK/auth.h" "https://api.cloudflare.com/client/v4/accounts/$CLOUDFLARE_ACCOUNT_ID/mtls_certificates" \
    | node -e 'let d="";process.stdin.on("data",c=>d+=c).on("end",()=>{const r=JSON.parse(d).result||[];const c=r.find(x=>x.name===process.argv[1]);process.stdout.write(c?c.id:"")})' "$CERT_NAME"
}

if [ "$DRY" = 1 ]; then
  echo "▸ dry run — would upload/reuse mTLS certificate '$CERT_NAME', deploy, set 5 HSBC secrets + the resolved attestation secrets, smoke"; exit 0
fi

CERT_ID="$(cert_id_by_name)"
if [ -z "$CERT_ID" ]; then
  npx --yes wrangler@4 mtls-certificate upload --cert "$WORK/transport.cert.pem" --key "$WORK/transport.key.pem" --name "$CERT_NAME" >/dev/null
  CERT_ID="$(cert_id_by_name)"
  echo "✓ uploaded mTLS certificate $CERT_NAME"
else
  echo "✓ reusing mTLS certificate $CERT_NAME"
fi
[ -n "$CERT_ID" ] || { echo "❌ mTLS certificate id not found after upload" >&2; exit 4; }
node -e 'const f=require("fs"),p="wrangler.toml";f.writeFileSync(p,f.readFileSync(p,"utf8").replace(/certificate_id = "[^"]*"/,`certificate_id = "${process.argv[1]}"`))' "$CERT_ID"

npx --yes wrangler@4 deploy 2>&1 | tee "$WORK/deploy.log" | grep -vE 'Bearer|token' || true
WORKER_URL="$(grep -oE 'https://mifos-x-open-banking-gateway\.[a-z0-9-]+\.workers\.dev' "$WORK/deploy.log" | head -1)"
[ -n "$WORKER_URL" ] || { echo "❌ deploy did not report a workers.dev URL" >&2; exit 5; }

node -e '
  const f=require("fs"),d=process.argv[1],r=a=>f.readFileSync(`${d}/${a}`,"utf8");
  const t=a=>r(a).trim();
  const opt=(a,read)=>f.existsSync(`${d}/${a}`)?read(a):undefined; // undefined keys are dropped by JSON.stringify
  f.writeFileSync(`${d}/secrets.json`, JSON.stringify({
    HSBC_SIGNING_KEY_PEM: r("mifos-x-open-banking-hsbc-private-key"),
    HSBC_CLIENT_ID: t("mifos-x-open-banking-hsbc-client-id"),
    HSBC_KID: t("mifos-x-open-banking-hsbc-kid"),
    HSBC_SOFTWARE_STATEMENT: t("mifos-x-open-banking-hsbc-software-statement"),
    HSBC_SIGNING_ISSUER: t("mifos-x-open-banking-hsbc-signing-issuer"),
    PLAY_INTEGRITY_SA_JSON: opt("mifos-x-firebase-service-account", r),
    APPLE_TEAM_ID: opt("mifos-x-apple-team-id", t),
  }), {mode: 0o600});' "$WORK"
npx --yes wrangler@4 secret bulk "$WORK/secrets.json" 2>&1 | grep -vE 'Bearer|token' || true
echo "✓ Worker secrets set"
echo "WORKER_URL=$WORKER_URL"

if [ "$SMOKE" = 1 ]; then
  GATEWAY_WORKER_URL="$WORKER_URL" bash "$GATEWAY_DIR/scripts/smoke.sh"
fi
