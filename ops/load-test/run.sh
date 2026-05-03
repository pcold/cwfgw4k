#!/usr/bin/env bash
#
# Load test harness for cwfgw4k. Drives a configurable mix of cached and
# uncached GET endpoints with `hey`, then prints per-endpoint summaries
# (RPS, P50 / P95 / P99 latency, error count) plus aggregate.
#
# Install hey:
#   brew install hey                # macOS
#   go install github.com/rakyll/hey@latest
#
# Usage:
#   ops/load-test/run.sh https://cwfgw4k-...run.app SEASON_ID [TOURNAMENT_ID]
#
#   ops/load-test/run.sh https://cwfgw4k-...run.app \
#     1234abcd-... 5678efgh-... \
#     -c 20 -d 60s
#
# Required:
#   $1  — base URL (no trailing slash)
#   $2  — season UUID for the cached season-scoped endpoints
#   $3  — tournament UUID (optional; only needed for the per-tournament
#         report and scoring routes — script skips them if absent)
#
# Optional flags (passed through to every hey invocation):
#   -c N    concurrency per endpoint (default 10)
#   -d D    duration per endpoint (default 30s)
#   -t S    per-request timeout in seconds (default 30)
#
# What it tests:
#   /api/v1/seasons/<sid>/standings              cached
#   /api/v1/seasons/<sid>/scoring/side-bets      cached
#   /api/v1/seasons/<sid>/rankings               cached
#   /api/v1/seasons/<sid>/rankings?live=true     cached, separate key
#   /api/v1/seasons/<sid>/report                 cached, multi-service
#   /api/v1/seasons/<sid>/scoring/<tid>          cached, per-tournament (if tid)
#   /api/v1/seasons/<sid>/report/<tid>           cached (if tid)
#   /api/v1/tournaments/<tid>/results            cached (if tid)
#   /api/v1/tournaments                          uncached, baseline
#
# Run order: walks each endpoint sequentially so per-endpoint p95 isn't
# polluted by other endpoints' contention. For cache-warming behaviour
# (first request is the only miss), expect the first second of each
# endpoint to be slower.

set -euo pipefail

BASE_URL=${1:?usage: run.sh BASE_URL SEASON_ID [TOURNAMENT_ID] [hey flags...]}
SEASON_ID=${2:?usage: run.sh BASE_URL SEASON_ID [TOURNAMENT_ID] [hey flags...]}
TOURNAMENT_ID=${3:-}
shift 2
[[ -n $TOURNAMENT_ID ]] && shift 1 || true

CONCURRENCY=10
DURATION=30s
TIMEOUT=30

while [[ $# -gt 0 ]]; do
  case $1 in
    -c) CONCURRENCY=$2; shift 2 ;;
    -d) DURATION=$2; shift 2 ;;
    -t) TIMEOUT=$2; shift 2 ;;
    *)  echo "Unknown flag: $1" >&2; exit 2 ;;
  esac
done

if ! command -v hey >/dev/null 2>&1; then
  echo "hey is not installed. Install with: brew install hey" >&2
  exit 1
fi

run_endpoint() {
  local label=$1
  local path=$2
  local url="${BASE_URL}${path}"
  echo
  echo "==== ${label} ===="
  echo "URL:         ${url}"
  echo "Concurrency: ${CONCURRENCY}"
  echo "Duration:    ${DURATION}"
  hey \
    -z "${DURATION}" \
    -c "${CONCURRENCY}" \
    -t "${TIMEOUT}" \
    -disable-keepalive=false \
    "${url}" \
    | sed -n '/^Summary:/,$p' \
    | head -40
}

# Warm the JVM + Cloud Run revision so cold-start doesn't dominate the first run.
echo "Warm-up burst (10 reqs, 5 concurrency)..."
hey -n 10 -c 5 -t "${TIMEOUT}" "${BASE_URL}/api/v1/health" > /dev/null

run_endpoint "Standings (cached)" \
  "/api/v1/seasons/${SEASON_ID}/standings"

run_endpoint "Side-bet standings (cached)" \
  "/api/v1/seasons/${SEASON_ID}/scoring/side-bets"

run_endpoint "Rankings (cached)" \
  "/api/v1/seasons/${SEASON_ID}/rankings"

run_endpoint "Rankings live=true (cached, separate key, hits ESPN on miss)" \
  "/api/v1/seasons/${SEASON_ID}/rankings?live=true"

run_endpoint "Season report (cached, multi-service aggregate)" \
  "/api/v1/seasons/${SEASON_ID}/report"

if [[ -n $TOURNAMENT_ID ]]; then
  run_endpoint "Tournament scoring (cached, per-tournament)" \
    "/api/v1/seasons/${SEASON_ID}/scoring/${TOURNAMENT_ID}"
  run_endpoint "Tournament report (cached)" \
    "/api/v1/seasons/${SEASON_ID}/report/${TOURNAMENT_ID}"
  run_endpoint "Tournament results (cached)" \
    "/api/v1/tournaments/${TOURNAMENT_ID}/results"
fi

run_endpoint "Tournaments list (uncached, baseline)" \
  "/api/v1/tournaments"

echo
echo "Done. Cross-reference with Cloud Logging during the run window:"
echo "  resource.type=\"cloud_run_revision\" AND \\"
echo "  resource.labels.service_name=\"cwfgw4k\" AND \\"
echo "  textPayload:\"cwfgw4k.cache\""
echo "to see hit / miss / put rates. Aim for hit rate > 0.7 on warm endpoints."
