#!/usr/bin/env bash
# One-time GCP infrastructure setup for cwfgw4k.
#
# Prerequisites (do these once before running this script):
#   1. gcloud auth login
#   2. gcloud projects create PROJECT_ID --name=cwfgw4k     (or use an existing project)
#   3. Link a billing account in the Console — Cloud SQL bills regardless of usage
#
# Usage:  ./deploy/setup.sh PROJECT_ID
#
# Idempotent: re-runs are safe. APIs already enabled stay enabled, existing
# Artifact Registry repos / SQL instances / databases / users / secrets are
# left alone (passwords are NOT rotated on re-run — delete the secret first if
# you want to rotate). Prints the generated admin password at the end so you
# can log into the UI on first boot.
set -euo pipefail

PROJECT_ID="${1:?Usage: ./deploy/setup.sh PROJECT_ID}"
REGION="us-west1"
SQL_INSTANCE="cwfgw4k-prod"
DB_NAME="cwfgw4k"
DB_USER="cwfgw4k"
REPO="cwfgw4k"
REPORTS_BUCKET="${PROJECT_ID}-cwfgw4k-test-reports"

echo "==> Setting project to ${PROJECT_ID}"
gcloud config set project "${PROJECT_ID}"

echo "==> Enabling required APIs"
gcloud services enable \
  run.googleapis.com \
  sqladmin.googleapis.com \
  cloudbuild.googleapis.com \
  artifactregistry.googleapis.com \
  secretmanager.googleapis.com \
  storage.googleapis.com

echo "==> Creating Artifact Registry repo"
if ! gcloud artifacts repositories describe "${REPO}" --location="${REGION}" &>/dev/null; then
  gcloud artifacts repositories create "${REPO}" \
    --repository-format=docker \
    --location="${REGION}" \
    --description="cwfgw4k Docker images"
else
  echo "    (already exists)"
fi

echo "==> Checking Cloud SQL instance"
if ! gcloud sql instances describe "${SQL_INSTANCE}" &>/dev/null; then
  echo "    Creating instance (this takes a few minutes)..."
  gcloud sql instances create "${SQL_INSTANCE}" \
    --database-version=POSTGRES_18 \
    --edition=ENTERPRISE \
    --tier=db-f1-micro \
    --region="${REGION}" \
    --storage-auto-increase
else
  echo "    Instance ${SQL_INSTANCE} already exists"
fi

echo "==> Creating database"
if ! gcloud sql databases describe "${DB_NAME}" --instance="${SQL_INSTANCE}" &>/dev/null; then
  gcloud sql databases create "${DB_NAME}" --instance="${SQL_INSTANCE}"
else
  echo "    (already exists)"
fi

echo "==> Setting database user password (rotates on every run if user exists)"
DB_PASSWORD="$(openssl rand -base64 18)Aa1!"
if ! gcloud sql users describe "${DB_USER}" --instance="${SQL_INSTANCE}" &>/dev/null; then
  echo "    Creating user ${DB_USER}"
  gcloud sql users create "${DB_USER}" \
    --instance="${SQL_INSTANCE}" \
    --password="${DB_PASSWORD}"
else
  gcloud sql users set-password "${DB_USER}" \
    --instance="${SQL_INSTANCE}" \
    --password="${DB_PASSWORD}"
fi

echo "==> Storing DB password in Secret Manager"
printf "%s" "${DB_PASSWORD}" | gcloud secrets create cwfgw4k-db-password \
  --data-file=- \
  2>/dev/null || \
printf "%s" "${DB_PASSWORD}" | gcloud secrets versions add cwfgw4k-db-password \
  --data-file=-

echo "==> Storing AUTH_SESSION_SECRET in Secret Manager"
# 32 bytes hex = 64 chars; matches dev.properties.example guidance.
SESSION_SECRET="$(openssl rand -hex 32)"
printf "%s" "${SESSION_SECRET}" | gcloud secrets create cwfgw4k-session-secret \
  --data-file=- \
  2>/dev/null || echo "    (cwfgw4k-session-secret already exists; not rotating)"

echo "==> Storing initial admin password in Secret Manager"
ADMIN_PASSWORD="$(openssl rand -base64 18)"
if ! gcloud secrets describe cwfgw4k-admin-password &>/dev/null; then
  printf "%s" "${ADMIN_PASSWORD}" | gcloud secrets create cwfgw4k-admin-password \
    --data-file=-
  ADMIN_PASSWORD_PRINTED="${ADMIN_PASSWORD}"
else
  echo "    (cwfgw4k-admin-password already exists; not rotating)"
  ADMIN_PASSWORD_PRINTED=""
fi

echo "==> Creating public reports bucket ${REPORTS_BUCKET}"
if ! gcloud storage buckets describe "gs://${REPORTS_BUCKET}" &>/dev/null; then
  gcloud storage buckets create "gs://${REPORTS_BUCKET}" \
    --location="${REGION}" \
    --uniform-bucket-level-access
  gcloud storage buckets add-iam-policy-binding "gs://${REPORTS_BUCKET}" \
    --member=allUsers \
    --role=roles/storage.objectViewer
else
  echo "    (already exists)"
fi

echo "==> Granting Cloud Run access to the secrets"
PROJECT_NUMBER="$(gcloud projects describe "${PROJECT_ID}" --format='value(projectNumber)')"
RUNTIME_SA="${PROJECT_NUMBER}-compute@developer.gserviceaccount.com"
for secret in cwfgw4k-db-password cwfgw4k-session-secret cwfgw4k-admin-password; do
  gcloud secrets add-iam-policy-binding "${secret}" \
    --member="serviceAccount:${RUNTIME_SA}" \
    --role="roles/secretmanager.secretAccessor" \
    --quiet
done

echo "==> Granting Cloud Build permission to deploy to Cloud Run"
gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
  --member="serviceAccount:${PROJECT_NUMBER}@cloudbuild.gserviceaccount.com" \
  --role="roles/run.admin" \
  --quiet
gcloud iam service-accounts add-iam-policy-binding \
  "${RUNTIME_SA}" \
  --member="serviceAccount:${PROJECT_NUMBER}@cloudbuild.gserviceaccount.com" \
  --role="roles/iam.serviceAccountUser" \
  --quiet

echo ""
echo "=== Setup complete ==="
echo ""
if [[ -n "${ADMIN_PASSWORD_PRINTED}" ]]; then
  echo "Initial admin login (save this somewhere safe — it will not be shown again):"
  echo "  username: admin"
  echo "  password: ${ADMIN_PASSWORD_PRINTED}"
  echo ""
fi
echo "Update cloudbuild.yaml's _REPORTS_BUCKET to: ${REPORTS_BUCKET}"
echo ""
echo "Next steps:"
echo "  1. (optional) Connect a Cloud Build trigger to the GitHub repo so pushes deploy."
echo "  2. Run a one-shot deploy:"
echo "     gcloud builds submit --config=cloudbuild.yaml --substitutions=_REPORTS_BUCKET=${REPORTS_BUCKET}"
echo "  3. Find the running service URL:"
echo "     gcloud run services describe cwfgw4k --region=${REGION} --format='value(status.url)'"
