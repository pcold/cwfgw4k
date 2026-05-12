#!/usr/bin/env bash
#
# Reproduce the May 10 prod wedge in a staging clone: spin a staging
# environment at the same CPU/memory profile as prod, force a freshly
# started instance for each iteration, send a tiny burst of parallel
# requests against the heavy live-overlay endpoints, and record per-
# request HTTP code + latency.
#
# What the wedge looks like: at 1Gi / 1CPU, a freshly-started instance
# that gets hit immediately with parallel `/report/<tid>?live=true` and
# `/rankings?live=true` (both cache-miss, both fan out internally into
# per-tournament ESPN scoring) pegs its single CPU. The PoolMetrics
# scheduled coroutine stops emitting, the Netty event loop keeps
# accepting on :8080 so Cloud Run's startup-probe stays green, and every
# in-flight DB-touching request times out at the Cloud Run 90s ceiling.
# Pool stays `waiting=0` throughout — this is NOT pool exhaustion.
#
# Why this and not stress.sh / soak.sh:
#   - stress.sh drives sustained concurrency against a warm instance.
#     Won't ever reproduce a cold-start wedge.
#   - soak.sh reproduces idle→first-hit (May 4 broken-pipe shape) with
#     a single-request canary. Won't catch parallel-fan-out CPU starvation.
#
# Forcing a cold start: each iteration bumps a COLD_BURST_NONCE env var
# on the staging service, which creates a new revision and makes Cloud
# Run start a fresh instance for it. That's faster (and more reliable)
# than waiting for scale-to-zero, and matches the prod shape — the
# wedge happened to an autoscale-spun new instance, not to a long-lived
# one.
#
# Default profile matches prod's pre-May-10 config (1Gi / 1CPU,
# min-instances=0, concurrency=20). Use -m / -C to verify a fix at a
# different profile (e.g. -m 2Gi -C 2 to confirm the resource bump
# actually clears the wedge).
#
# Cost: roughly $1-2 base (Cloud SQL clone), plus a few minutes of
# Cloud Run time. Far cheaper than running stress for an hour.
#
# Usage:
#   ops/load-test/cold-burst.sh                          # defaults: 3 iter, parallel=4
#   ops/load-test/cold-burst.sh -n 5 -p 6                # 5 iterations, 6 parallel each
#   ops/load-test/cold-burst.sh -m 2Gi -C 2              # verify fix at the bumped profile
#   ops/load-test/cold-burst.sh --keep                   # leave staging alive
#
# Requires: gcloud, jq, curl.

set -euo pipefail

PROJECT=cwfgw4k
REGION=us-west1
PROD_SQL=cwfgw4k-prod
IMAGE_REPO=${REGION}-docker.pkg.dev/${PROJECT}/cwfgw4k/cwfgw4k:latest

ITERATIONS=3
PARALLEL=4
MEMORY=1Gi
CPU=1
CONCURRENCY=20
KEEP=0
SEASON_ID=""
TOURNAMENT_ID=""

while [[ $# -gt 0 ]]; do
  case $1 in
    -n) ITERATIONS=$2; shift 2 ;;
    -p) PARALLEL=$2; shift 2 ;;
    -m) MEMORY=$2; shift 2 ;;
    -C) CPU=$2; shift 2 ;;
    -c) CONCURRENCY=$2; shift 2 ;;
    -s) SEASON_ID=$2; shift 2 ;;
    -T) TOURNAMENT_ID=$2; shift 2 ;;
    --keep) KEEP=1; shift ;;
    *) echo "Unknown flag: $1" >&2; exit 2 ;;
  esac
done

for cmd in gcloud jq curl; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Missing required command: $cmd" >&2
    exit 1
  fi
done

SUFFIX=$(date +%Y%m%d-%H%M%S)
STAGING_SQL="staging-${SUFFIX}"
STAGING_SERVICE="cwfgw4k-staging-${SUFFIX}"

cleanup() {
  if [[ $KEEP -eq 1 ]]; then
    echo
    echo "==> --keep specified; leaving staging up. Tear down later with:"
    echo "    ops/load-test/spin-staging.sh --teardown $SUFFIX"
    return
  fi
  echo
  echo "==> Cleaning up staging resources..."
  gcloud run services delete "$STAGING_SERVICE" --region="$REGION" --project="$PROJECT" --quiet 2>/dev/null || true
  gcloud sql instances delete "$STAGING_SQL" --project="$PROJECT" --quiet 2>/dev/null || true
  echo "Done."
}
trap cleanup EXIT

echo "==> Cold-burst configuration:"
echo "    Memory / CPU:       ${MEMORY} / ${CPU} vCPU"
echo "    Concurrency:        ${CONCURRENCY}"
echo "    Iterations:         ${ITERATIONS}"
echo "    Parallel per burst: ${PARALLEL}"
echo

echo "==> Cloning $PROD_SQL into $STAGING_SQL — typically 5–10 min..."
gcloud sql instances clone "$PROD_SQL" "$STAGING_SQL" --project="$PROJECT"

echo
echo "==> Deploying $STAGING_SERVICE (min-instances=0, ${MEMORY}/${CPU}vCPU)..."
DB_JDBC_URL="jdbc:postgresql:///cwfgw4k?cloudSqlInstance=${PROJECT}:${REGION}:${STAGING_SQL}&socketFactory=com.google.cloud.sql.postgres.SocketFactory"

gcloud run deploy "$STAGING_SERVICE" \
  --image="$IMAGE_REPO" \
  --region="$REGION" \
  --project="$PROJECT" \
  --allow-unauthenticated \
  --add-cloudsql-instances="${PROJECT}:${REGION}:${STAGING_SQL}" \
  --set-secrets="DB_PASSWORD=cwfgw4k-db-password:latest,AUTH_SESSION_SECRET=cwfgw4k-session-secret:latest,AUTH_ADMIN_PASSWORD=cwfgw4k-admin-password:latest" \
  --set-env-vars="DB_JDBC_URL=${DB_JDBC_URL},DB_USER=cwfgw4k,DB_SCHEMA=cwfgw4k,DB_POOL=15,AUTH_ADMIN_USERNAME=admin,COLD_BURST_NONCE=0,DEBUG_ENDPOINTS_ENABLED=true" \
  --memory="$MEMORY" \
  --cpu="$CPU" \
  --concurrency="$CONCURRENCY" \
  --min-instances=0 \
  --max-instances=10 \
  --timeout=90 \
  --quiet

URL=$(gcloud run services describe "$STAGING_SERVICE" --region="$REGION" --project="$PROJECT" --format='value(status.url)')
echo
echo "==> Staging URL: $URL"

if [[ -z "$SEASON_ID" ]]; then
  echo "==> Discovering season + tournament from the cloned data..."
  curl -fsS -o /dev/null "$URL/api/v1/leagues" || true
  SEASON_ID=$(curl -fsS "$URL/api/v1/seasons" | jq -r '.[0].id // empty')
fi
if [[ -z "$SEASON_ID" ]]; then
  echo "ERROR: no seasons in cloned database — cold-burst cannot run." >&2
  exit 1
fi
if [[ -z "$TOURNAMENT_ID" ]]; then
  TOURNAMENT_ID=$(
    curl -fsS "$URL/api/v1/tournaments?season_id=$SEASON_ID" |
      jq -r '[.[] | select(.status != "completed")] | .[0].id // empty'
  )
fi
if [[ -z "$TOURNAMENT_ID" ]]; then
  echo "ERROR: no non-completed tournament in cloned season — live-overlay path can't trigger." >&2
  exit 1
fi
echo "==> season=$SEASON_ID tournament=$TOURNAMENT_ID (non-completed → ?live=true exercises the overlay)"

# Three heavy live-overlay paths that fan out into per-tournament ESPN
# previews. These are the ones the access log showed cache-missing
# during the May 10 wedge window.
TARGETS=(
  "$URL/api/v1/seasons/$SEASON_ID/report/$TOURNAMENT_ID?live=true"
  "$URL/api/v1/seasons/$SEASON_ID/rankings?live=true"
  "$URL/api/v1/seasons/$SEASON_ID/player-rankings?live=true"
)

echo
echo "==> Targets (each iteration fires $PARALLEL parallel hits across these):"
for t in "${TARGETS[@]}"; do
  echo "    $t"
done
echo

WEDGES=0
ANY_FAIL=0

for iter in $(seq 1 "$ITERATIONS"); do
  echo "==> Iteration $iter / $ITERATIONS"
  echo "    Forcing fresh revision (bumping COLD_BURST_NONCE=$iter)..."
  gcloud run services update "$STAGING_SERVICE" \
    --region="$REGION" --project="$PROJECT" \
    --update-env-vars="COLD_BURST_NONCE=$iter" \
    --quiet >/dev/null

  # Small settle so the new revision is ready to take traffic. Cloud Run
  # only routes once the startup probe passes, but the response from the
  # update command returns before that. A few seconds is enough in
  # practice given the TCP probe.
  sleep 3

  # One warmup request to ensure the new revision has an instance up
  # for the burst to land on. Without this, the very first parallel
  # request pays the boot cost and the others queue at Cloud Run's
  # request router — masking the wedge shape we're trying to reproduce.
  curl -fsS -o /dev/null "$URL/" || true

  tmpdir=$(mktemp -d)
  dump_dir="${DUMP_DIR:-/tmp}/cold-burst-${SUFFIX}/iter-${iter}"
  mkdir -p "$dump_dir"
  echo "    Firing ${PARALLEL} parallel hits across ${#TARGETS[@]} endpoints simultaneously..."
  i=0
  for slot in $(seq 1 "$PARALLEL"); do
    target=${TARGETS[$((i % ${#TARGETS[@]}))]}
    i=$((i + 1))
    (
      out=$(curl -sS -o /dev/null \
        --max-time 95 \
        -w '%{http_code} %{time_total}' \
        "$target" 2>/dev/null || echo "ERR -")
      echo "$slot $target $out" >"$tmpdir/$slot"
    ) &
  done

  # Capture thread + coroutine dumps mid-wedge. Three snapshots span the
  # bulk of the 90s Cloud Run window so a parked coroutine's stack shows
  # up even if it's blocked in a different state at one of the moments.
  # `curl --max-time 20` so a wedged /debug response doesn't itself hang
  # the harness — the dump endpoint runs on the same instance as the
  # wedged handlers, but its IO-dispatcher work is small and should still
  # land. If it doesn't, the empty file is itself a signal.
  for offset in 10 30 60; do
    (
      sleep "$offset"
      curl -sS --max-time 20 -o "$dump_dir/+${offset}s.txt" "$URL/debug/threads" 2>/dev/null || true
    ) &
  done
  wait

  echo
  printf "    %-4s %-10s %-10s  %s\n" "slot" "http" "latency_s" "endpoint"
  iter_wedge=0
  for slot in $(seq 1 "$PARALLEL"); do
    read -r _ target code latency <"$tmpdir/$slot"
    short=${target#"$URL"}
    short=${short%%\?*}
    printf "    %-4s %-10s %-10s  %s\n" "$slot" "$code" "$latency" "$short"
    case $code in
      2*|3*) : ;;
      *)
        ANY_FAIL=1
        iter_wedge=1
        ;;
    esac
    case $latency in
      [3-9][0-9].*|[1-9][0-9][0-9]*) iter_wedge=1 ;;
    esac
  done
  rm -rf "$tmpdir"
  if [[ $iter_wedge -eq 1 ]]; then
    WEDGES=$((WEDGES + 1))
    echo "    >>> WEDGE SIGNAL on iteration $iter (504 or latency ≥30s)"
    echo "    Thread/coroutine dumps captured in: $dump_dir"
  fi
  echo
done

echo "==> Summary: ${WEDGES}/${ITERATIONS} iterations showed a wedge signal."
if [[ $WEDGES -gt 0 ]]; then
  echo "    The cold-start fan-out wedge is reproducible at memory=${MEMORY} cpu=${CPU}."
else
  echo "    No wedges observed at memory=${MEMORY} cpu=${CPU}."
fi
echo
echo "==> Cross-reference Cloud Logging for this run (revisions named ${STAGING_SERVICE}-*):"
echo "    resource.type=\"cloud_run_revision\" AND \\"
echo "    resource.labels.service_name=\"$STAGING_SERVICE\""
echo
echo "    Pool state (should stay waiting=0 — that's the diagnostic point):"
echo "    resource.type=\"cloud_run_revision\" AND \\"
echo "    resource.labels.service_name=\"$STAGING_SERVICE\" AND \\"
echo "    textPayload:\"cwfgw4k.db.pool\""

if [[ $ANY_FAIL -eq 1 ]]; then
  exit 1
fi
