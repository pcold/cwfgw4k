#!/usr/bin/env bash
#
# Spin up an ephemeral cwfgw4k staging environment cloned from prod, run
# the load test against it, then tear everything down. Useful for trying
# config changes (provisioning, cache TTL, etc.) under realistic data
# without touching the live service.
#
# What it creates (all named with a unique timestamped suffix):
#   - Cloud SQL instance:   staging-YYYYMMDD-HHMMSS    (cloned from cwfgw4k-prod)
#   - Cloud Run service:    cwfgw4k-staging-YYYYMMDD-HHMMSS
#
# Both are deleted on script exit (trap), even on failure or Ctrl-C.
#
# Cost expectation: roughly $1–2 per run depending on duration. The clone
# is the dominant line item; teardown happens promptly so it's only alive
# for the load test plus ~5–10 min of clone bring-up.
#
# Requires: gcloud, hey, jq, curl. The caller's gcloud credentials need
# Cloud SQL Admin + Cloud Run Admin + Secret Manager Secret Accessor on
# the cwfgw4k project.
#
# Usage:
#   ops/load-test/spin-staging.sh                    # defaults: -c 10 -d 30s
#   ops/load-test/spin-staging.sh -c 25 -d 60s       # heavier burst
#   ops/load-test/spin-staging.sh -m 1               # match prod min-instances=0;
#                                                     # default 1 here for clean
#                                                     # latency measurement.
#   ops/load-test/spin-staging.sh --keep             # skip teardown (dev only)
#   ops/load-test/spin-staging.sh --list             # show currently-alive staging envs
#   ops/load-test/spin-staging.sh --teardown SUFFIX  # delete a kept env by its
#                                                     # YYYYMMDD-HHMMSS suffix

set -euo pipefail

PROJECT=cwfgw4k
REGION=us-west1
PROD_SQL=cwfgw4k-prod
IMAGE_REPO=${REGION}-docker.pkg.dev/${PROJECT}/cwfgw4k/cwfgw4k:latest

CONCURRENCY=10
DURATION=30s
TIMEOUT=30
MIN_INSTANCES=1
KEEP=0
LIST=0
TEARDOWN_SUFFIX=""
SEASON_ID=""
TOURNAMENT_ID=""
ACTIVE_TID=""

while [[ $# -gt 0 ]]; do
  case $1 in
    -c) CONCURRENCY=$2; shift 2 ;;
    -d) DURATION=$2; shift 2 ;;
    -t) TIMEOUT=$2; shift 2 ;;
    -m) MIN_INSTANCES=$2; shift 2 ;;
    -s) SEASON_ID=$2; shift 2 ;;
    -T) TOURNAMENT_ID=$2; shift 2 ;;
    --keep) KEEP=1; shift ;;
    --list) LIST=1; shift ;;
    --teardown) TEARDOWN_SUFFIX=${2:-}; shift 2 ;;
    *) echo "Unknown flag: $1" >&2; exit 2 ;;
  esac
done

if ! command -v gcloud >/dev/null 2>&1; then
  echo "Missing required command: gcloud" >&2
  exit 1
fi

if [[ $LIST -eq 1 ]]; then
  echo "==> Cloud SQL staging instances (project=$PROJECT):"
  gcloud sql instances list \
    --project="$PROJECT" \
    --filter='name~^staging-' \
    --format='table(name,settings.tier,state,createTime)'
  echo
  echo "==> Cloud Run staging services (region=$REGION):"
  gcloud run services list \
    --project="$PROJECT" \
    --region="$REGION" \
    --filter='metadata.name~^cwfgw4k-staging-' \
    --format='table(metadata.name,status.url,metadata.creationTimestamp)'
  echo
  echo "Tear one down with: $0 --teardown YYYYMMDD-HHMMSS"
  exit 0
fi

if [[ -n "$TEARDOWN_SUFFIX" ]]; then
  # Tolerate users pasting the full instance name back in.
  TEARDOWN_SUFFIX=${TEARDOWN_SUFFIX#cwfgw4k-staging-}
  TEARDOWN_SUFFIX=${TEARDOWN_SUFFIX#staging-}
  TARGET_SQL="staging-${TEARDOWN_SUFFIX}"
  TARGET_SERVICE="cwfgw4k-staging-${TEARDOWN_SUFFIX}"
  echo "==> Tearing down $TARGET_SERVICE and $TARGET_SQL..."
  gcloud run services delete "$TARGET_SERVICE" --region="$REGION" --project="$PROJECT" --quiet 2>/dev/null || true
  gcloud sql instances delete "$TARGET_SQL" --project="$PROJECT" --quiet 2>/dev/null || true
  echo "Done."
  exit 0
fi

for cmd in hey jq curl; do
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
    echo "    $0 --teardown $SUFFIX"
    echo "    (or: $0 --list to see all kept envs)"
    return
  fi
  echo
  echo "==> Cleaning up staging resources..."
  gcloud run services delete "$STAGING_SERVICE" --region="$REGION" --project="$PROJECT" --quiet 2>/dev/null || true
  gcloud sql instances delete "$STAGING_SQL" --project="$PROJECT" --quiet 2>/dev/null || true
  echo "Done."
}
trap cleanup EXIT

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
  --quiet

URL=$(gcloud run services describe "$STAGING_SERVICE" --region="$REGION" --project="$PROJECT" --format='value(status.url)')
echo
echo "==> Staging URL: $URL"

if [[ -z "$SEASON_ID" || -z "$TOURNAMENT_ID" ]]; then
  echo "==> Discovering season + tournament IDs from the cloned data..."
  # Hit the staging API itself — guarantees the connection string + secrets
  # are wired correctly before we start the load test.
  #
  # Seasons: sorted year-desc by the API, so [0] is the most recent.
  # Tournaments: start_date-asc. We track two:
  #   - ACTIVE_TID:    earliest non-completed (the one users hit live, what
  #                    the UI defaults its through-selector to). Logged for
  #                    visibility; not passed to run.sh today since none of
  #                    its per-tournament endpoints accept "the active one"
  #                    as input.
  #   - TOURNAMENT_ID: most recently completed (latest start_date among
  #                    completed). This is what run.sh's /scoring/<tid>,
  #                    /report/<tid>, and /tournaments/<tid>/results
  #                    endpoints get — exercising the cache+scoring path
  #                    against real data instead of an empty upcoming row.
  # Falls back to ACTIVE_TID if the season has no completed tournaments
  # (fresh league); accepts -T <uuid> to override.
  if [[ -z "$SEASON_ID" ]]; then
    SEASON_ID=$(curl -fsS "$URL/api/v1/seasons" | jq -r '.[0].id // empty')
  fi
  if [[ -z "$SEASON_ID" ]]; then
    echo "ERROR: no seasons found in cloned database — load test cannot run." >&2
    exit 1
  fi
  TOURNAMENTS_JSON=$(curl -fsS "$URL/api/v1/tournaments?season_id=$SEASON_ID")
  ACTIVE_TID=$(jq -r '[.[] | select(.status != "completed")] | .[0].id // empty' <<<"$TOURNAMENTS_JSON")
  if [[ -z "$TOURNAMENT_ID" ]]; then
    TOURNAMENT_ID=$(jq -r '[.[] | select(.status == "completed")] | .[-1].id // empty' <<<"$TOURNAMENTS_JSON")
    if [[ -z "$TOURNAMENT_ID" && -n "$ACTIVE_TID" ]]; then
      TOURNAMENT_ID=$ACTIVE_TID
      echo "    (no completed tournaments; falling back to active — per-tournament endpoints will return empty bodies)"
    fi
  fi
  if [[ -z "$TOURNAMENT_ID" ]]; then
    echo "WARN: no tournaments in cloned database; per-tournament endpoints will be skipped." >&2
  fi
fi

echo "==> season=$SEASON_ID active=${ACTIVE_TID:-<none>} per_tournament=${TOURNAMENT_ID:-<none>}"
echo

# Brief settle so the new revision serves a few warmup requests at its own
# pace before we launch the burst — matches the warmup the inner script does
# but on the brand-new staging revision specifically.
sleep 5

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
ARGS=("$URL" "$SEASON_ID")
[[ -n "$TOURNAMENT_ID" ]] && ARGS+=("$TOURNAMENT_ID")
ARGS+=(-c "$CONCURRENCY" -d "$DURATION" -t "$TIMEOUT")
"$SCRIPT_DIR/run.sh" "${ARGS[@]}"

echo
echo "==> Load test complete. Logs to cross-reference (10-minute window):"
echo "    resource.type=\"cloud_run_revision\" AND \\"
echo "    resource.labels.service_name=\"$STAGING_SERVICE\""
echo
echo "    Cache events:"
echo "    resource.type=\"cloud_run_revision\" AND \\"
echo "    resource.labels.service_name=\"$STAGING_SERVICE\" AND \\"
echo "    textPayload:\"cwfgw4k.cache\""
echo
echo "    ESPN calls:"
echo "    resource.type=\"cloud_run_revision\" AND \\"
echo "    resource.labels.service_name=\"$STAGING_SERVICE\" AND \\"
echo "    textPayload:\"cwfgw4k.espn\""
