#!/usr/bin/env bash
#
# Fan-out load test that reproduces the May 3 prod wedge access pattern.
# Unlike run.sh (which walks endpoints sequentially against a single
# tournament_id), this fires every non-completed tournament's
# `report/<tid>?live=true` URL in parallel — distinct Postgres cache
# keys, distinct Caffeine ESPN cache keys, distinct fetches all in flight
# at once. Cheap uncached endpoints (`/api/v1/tournaments`,
# `/api/v1/leagues`) run in parallel too so we can tell whether they
# starve while the heavy paths are inflight, which was the wedge symptom.
#
# Usage:
#   ops/load-test/stress.sh BASE_URL SEASON_ID [-c N] [-d D] [-t S]
#
# Required:
#   $1  base URL (no trailing slash)
#   $2  season UUID
#
# Optional:
#   -c N   concurrency PER URL (default 10). Total in-flight = N × <num urls>.
#   -d D   duration (default 120s). Bumping past 5 min ages the JVM /
#          Caffeine cache more representatively if you can spare the cost.
#   -t S   per-request timeout in seconds (default 90 — matches prod's
#          --timeout=90).
#
# The URL set is:
#   /api/v1/leagues                                    cheap, uncached
#   /api/v1/tournaments?season_id=<sid>                cheap, uncached
#   /api/v1/seasons/<sid>/rankings?live=true           cached, ESPN
#   /api/v1/seasons/<sid>/report?live=true             cached, ESPN per non-completed
#   /api/v1/seasons/<sid>/report/<tid>?live=true × N   one per non-completed tournament
#
# What to watch for in Cloud Logging during the run:
#   - `cwfgw4k.db.pool waiting=N` with N > 0 sustained → pool exhaustion
#   - 504s on `/api/v1/leagues` or `/api/v1/tournaments` → wedge reproduced
#   - `cwfgw4k.cache event=coalesced` rate vs `event=miss` rate → single-flight effectiveness

set -euo pipefail

BASE_URL=${1:?usage: stress.sh BASE_URL SEASON_ID [-c N] [-d D] [-t S]}
SEASON_ID=${2:?usage: stress.sh BASE_URL SEASON_ID [-c N] [-d D] [-t S]}
shift 2

CONCURRENCY=10
DURATION=120s
TIMEOUT=90

while [[ $# -gt 0 ]]; do
  case $1 in
    -c) CONCURRENCY=$2; shift 2 ;;
    -d) DURATION=$2; shift 2 ;;
    -t) TIMEOUT=$2; shift 2 ;;
    *)  echo "Unknown flag: $1" >&2; exit 2 ;;
  esac
done

for cmd in hey jq curl; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Missing required command: $cmd" >&2
    exit 1
  fi
done

NON_COMPLETED_IDS=$(
  curl -fsS "$BASE_URL/api/v1/tournaments?season_id=$SEASON_ID" \
    | jq -r '[.[] | select(.status != "completed")] | .[].id'
)

if [[ -z "$NON_COMPLETED_IDS" ]]; then
  echo "ERROR: season has no non-completed tournaments — nothing to fan out against." >&2
  exit 1
fi

URLS=(
  "$BASE_URL/api/v1/leagues"
  "$BASE_URL/api/v1/tournaments?season_id=$SEASON_ID"
  "$BASE_URL/api/v1/seasons/$SEASON_ID/rankings?live=true"
  "$BASE_URL/api/v1/seasons/$SEASON_ID/report?live=true"
)
while IFS= read -r tid; do
  [[ -n "$tid" ]] && URLS+=("$BASE_URL/api/v1/seasons/$SEASON_ID/report/$tid?live=true")
done <<<"$NON_COMPLETED_IDS"

echo "==> Stress configuration:"
echo "    URLs:                ${#URLS[@]}"
echo "    Concurrency per URL: $CONCURRENCY"
echo "    Total in-flight:     $((CONCURRENCY * ${#URLS[@]}))"
echo "    Duration:            $DURATION"
echo "    Per-request timeout: ${TIMEOUT}s"
echo

LOG_DIR=$(mktemp -d)
trap 'rm -rf "$LOG_DIR"' EXIT

echo "==> Warm-up burst (catches obvious config errors before the real run)..."
hey -n 5 -c 2 -t "$TIMEOUT" "${URLS[0]}" > /dev/null

echo "==> Firing ${#URLS[@]} parallel hey processes..."
pids=()
for i in "${!URLS[@]}"; do
  url=${URLS[$i]}
  out="$LOG_DIR/$i.log"
  printf '    [%2d] %s\n' "$i" "$url"
  hey -z "$DURATION" -c "$CONCURRENCY" -t "$TIMEOUT" -disable-keepalive=false "$url" > "$out" 2>&1 &
  pids+=($!)
done

echo
echo "==> Waiting for $DURATION..."
for pid in "${pids[@]}"; do wait "$pid"; done

echo
echo "==== Per-URL summaries ===="
for i in "${!URLS[@]}"; do
  echo
  echo "[$i] ${URLS[$i]}"
  # hey's "Summary" + latency block is the meat; trim the histogram noise.
  sed -n '/^Summary:/,/^Latency distribution:/p' "$LOG_DIR/$i.log"
  echo "Status code distribution:"
  sed -n '/^Status code distribution:/,/^$/p' "$LOG_DIR/$i.log" | tail -n +2
done

echo
echo "==== Aggregate status codes ===="
awk '
  /^Status code distribution:/ { in_block = 1; next }
  in_block && /^\[/ {
    code = substr($1, 2, length($1) - 2)
    counts[code] += $2
  }
  in_block && NF == 0 { in_block = 0 }
  END {
    for (c in counts) printf "  [%s] %d\n", c, counts[c]
  }
' "$LOG_DIR"/*.log | sort

echo
echo "Cross-reference Cloud Logging for the run window:"
echo "  resource.type=\"cloud_run_revision\" AND \\"
echo "  textPayload:\"cwfgw4k.db.pool\""
echo "  → look for waiting > 0 sustained (pool exhaustion)"
echo
echo "  resource.type=\"cloud_run_revision\" AND \\"
echo "  httpRequest.status>=500"
echo "  → 504s on /leagues or /tournaments mean wedge reproduced"
