#!/usr/bin/env bash
#
# Reproduce the May 4 prod wedge in a staging clone: spin a fresh staging
# environment, send a warmup burst to confirm health, idle for a
# configurable window so connections can age out / Cloud SQL has a chance
# to drop them, then send a canary every CANARY_INTERVAL seconds for
# CANARY_WINDOW seconds and record per-canary latency.
#
# A wedge looks like a single canary at ~30s+ surrounded by sub-second
# rows; a healthy system shows every canary < 1s. The first canary after
# the idle window is the load-bearing one — that's what real users hit.
#
# Why this and not stress.sh? stress.sh drives high concurrency
# continuously and so never lets connections age. The wedge we keep
# hitting in prod is the *opposite* shape: instance alive but quiet,
# pool slowly fills with stale sockets, then the first new request
# hangs.
#
# Cost: roughly $1-2 base (Cloud SQL clone), plus the staging instance
# alive for the duration of -i + the canary window. A 30-min idle is
# ~$0.50 of compute, well worth it for the diagnostic.
#
# Usage:
#   ops/load-test/soak.sh                    # defaults: -i 30 -w 300 -c 30
#   ops/load-test/soak.sh -i 60              # 60-min idle
#   ops/load-test/soak.sh -i 30 -w 600 -c 60 # 30-min idle, 10-min canary window every 60s
#
#   ops/load-test/soak.sh --keep             # leave staging up after; tear down later with
#                                            # ops/load-test/spin-staging.sh --teardown <suffix>

set -euo pipefail

PROJECT=cwfgw4k
REGION=us-west1
PROD_SQL=cwfgw4k-prod
IMAGE_REPO=${REGION}-docker.pkg.dev/${PROJECT}/cwfgw4k/cwfgw4k:latest

IDLE_MINUTES=30
CANARY_WINDOW_SECONDS=300
CANARY_INTERVAL_SECONDS=30
MIN_INSTANCES=1
KEEP=0

while [[ $# -gt 0 ]]; do
  case $1 in
    -i) IDLE_MINUTES=$2; shift 2 ;;
    -w) CANARY_WINDOW_SECONDS=$2; shift 2 ;;
    -c) CANARY_INTERVAL_SECONDS=$2; shift 2 ;;
    -m) MIN_INSTANCES=$2; shift 2 ;;
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

echo "==> Soak configuration:"
echo "    Idle window:        ${IDLE_MINUTES} min"
echo "    Canary window:      ${CANARY_WINDOW_SECONDS}s ($((CANARY_WINDOW_SECONDS / 60)) min)"
echo "    Canary interval:    ${CANARY_INTERVAL_SECONDS}s"
echo "    Total runtime:      ~$((IDLE_MINUTES + (CANARY_WINDOW_SECONDS / 60) + 10)) min"
echo

echo "==> Cloning $PROD_SQL into $STAGING_SQL — typically 5–10 min..."
gcloud sql instances clone "$PROD_SQL" "$STAGING_SQL" --project="$PROJECT"

echo
echo "==> Deploying $STAGING_SERVICE against the cloned database..."
DB_JDBC_URL="jdbc:postgresql:///cwfgw4k?cloudSqlInstance=${PROJECT}:${REGION}:${STAGING_SQL}&socketFactory=com.google.cloud.sql.postgres.SocketFactory"

gcloud run deploy "$STAGING_SERVICE" \
  --image="$IMAGE_REPO" \
  --region="$REGION" \
  --project="$PROJECT" \
  --allow-unauthenticated \
  --add-cloudsql-instances="${PROJECT}:${REGION}:${STAGING_SQL}" \
  --set-secrets="DB_PASSWORD=cwfgw4k-db-password:latest,AUTH_SESSION_SECRET=cwfgw4k-session-secret:latest,AUTH_ADMIN_PASSWORD=cwfgw4k-admin-password:latest" \
  --set-env-vars="DB_JDBC_URL=${DB_JDBC_URL},DB_USER=cwfgw4k,DB_SCHEMA=cwfgw4k,DB_POOL=15,AUTH_ADMIN_USERNAME=admin" \
  --memory=1Gi \
  --concurrency=20 \
  --min-instances="$MIN_INSTANCES" \
  --max-instances=10 \
  --timeout=90 \
  --quiet

URL=$(gcloud run services describe "$STAGING_SERVICE" --region="$REGION" --project="$PROJECT" --format='value(status.url)')
echo
echo "==> Staging URL: $URL"

echo
echo "==> Warmup burst — confirms the deploy is healthy before we idle..."
for _ in $(seq 1 10); do
  curl -fsS -o /dev/null -w "  /api/v1/leagues  http=%{http_code}  total=%{time_total}s\n" "$URL/api/v1/leagues"
done

echo
echo "==> Idling for ${IDLE_MINUTES} min so connections can age out..."
sleep $((IDLE_MINUTES * 60))

echo
echo "==> Canary phase — one /api/v1/leagues every ${CANARY_INTERVAL_SECONDS}s for ${CANARY_WINDOW_SECONDS}s."
echo "    Latency >5s in this phase is the signal we're hunting (the wedge)."
echo
printf "    %-20s %-8s %-10s\n" "timestamp" "http" "latency_s"
elapsed=0
while [[ $elapsed -lt $CANARY_WINDOW_SECONDS ]]; do
  ts=$(date -u +%H:%M:%SZ)
  out=$(curl -fsS -o /dev/null -w "%{http_code} %{time_total}" "$URL/api/v1/leagues" 2>/dev/null || echo "ERR -")
  read -r code latency <<<"$out"
  printf "    %-20s %-8s %-10s\n" "$ts" "$code" "$latency"
  elapsed=$((elapsed + CANARY_INTERVAL_SECONDS))
  [[ $elapsed -lt $CANARY_WINDOW_SECONDS ]] && sleep "$CANARY_INTERVAL_SECONDS"
done

echo
echo "==> Soak complete. Cross-reference Cloud Logging for the run window:"
echo "    resource.type=\"cloud_run_revision\" AND \\"
echo "    resource.labels.service_name=\"$STAGING_SERVICE\" AND \\"
echo "    severity>=WARNING"
echo "    → look for cwfgw4k.db.slow_tx, cwfgw4k.db.slow_acquire, Hikari Broken Pipe stacks"
echo
echo "    Pool counters across the run:"
echo "    resource.type=\"cloud_run_revision\" AND \\"
echo "    resource.labels.service_name=\"$STAGING_SERVICE\" AND \\"
echo "    textPayload:\"cwfgw4k.db.pool\""
