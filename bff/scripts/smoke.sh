#!/usr/bin/env bash
# Copyright 2026 Mifos Initiative — MPL-2.0. See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
#
# smoke.sh — post-deploy check against the deployed BFF Worker: GET /v1/health → 200.
# Env: BFF_WORKER_URL (the https://mifos-x-open-banking-bff.<subdomain>.workers.dev URL printed by deploy.sh)
set -uo pipefail
URL="${BFF_WORKER_URL:?set BFF_WORKER_URL to the deployed workers.dev URL}"
HEALTH=$(curl -sS -o /dev/null -w '%{http_code}' "$URL/v1/health" || echo 000)
echo "health: $HEALTH"
[ "$HEALTH" = 200 ] || { echo "❌ /v1/health returned $HEALTH" >&2; exit 1; }
