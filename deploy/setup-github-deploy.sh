#!/usr/bin/env bash
# Lets GitHub Actions deploy to Cloud Run, without a key anywhere.
#
#   ./deploy/setup-github-deploy.sh PROJECT_ID [REPO_SLUG]
#
# Run once, after deploy/setup.sh has created the project's infrastructure and
# the service has been deployed at least once. Idempotent.
#
# The shape: GitHub mints an OIDC token for a workflow run; Workload Identity
# Federation exchanges it for a short-lived credential on a service account that
# can deploy this service and push its image, and nothing else. No key file
# exists to leak. .github/workflows/deploy.yml is the only consumer.
set -euo pipefail

PROJECT_ID="${1:?Usage: ./deploy/setup-github-deploy.sh PROJECT_ID [REPO_SLUG]}"
REPO_SLUG="${2:-pcold/cwfgw4k}"
REGION="us-west1"
IMAGE_REPO="cwfgw4k"

POOL="github"
PROVIDER="cwfgw4k"
SA_NAME="github-deployer"
SA_EMAIL="${SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"
PROJECT_NUMBER="$(gcloud projects describe "${PROJECT_ID}" --format='value(projectNumber)')"
RUNTIME_SA="${PROJECT_NUMBER}-compute@developer.gserviceaccount.com"

# A freshly created service account is not immediately visible to the IAM API,
# which rejects a grant that names it with "does not exist" or a permission
# error rather than with anything that reads as a race. So every grant below
# that names the new account goes through this.
grant_once_visible() {
  local grant="$1"; shift
  local output
  for _ in $(seq 1 12); do
    if output="$("$@" 2>&1)"; then
      return 0
    fi
    sleep 5
  done
  echo "ERROR: could not grant ${grant} after ~60s. The last attempt said:" >&2
  echo "${output}" >&2
  exit 1
}

echo "==> Enabling the federation APIs"
gcloud services enable iamcredentials.googleapis.com sts.googleapis.com --project "${PROJECT_ID}"

echo "==> Creating the ${SA_NAME} service account"
if ! gcloud iam service-accounts describe "${SA_EMAIL}" --project "${PROJECT_ID}" &>/dev/null; then
  gcloud iam service-accounts create "${SA_NAME}" --project "${PROJECT_ID}" \
    --display-name="Deploys cwfgw4k to Cloud Run from GitHub Actions"
else
  echo "    (already exists)"
fi

echo "==> Granting it what a deploy needs, and nothing else"
# run.developer and not run.admin: it can create and update revisions but cannot
# change who may invoke the service. The service's public binding is set once,
# by hand, and outlives revisions.
#
# No Secret Manager or Cloud SQL role either. A deploy only names the secrets
# and the instance; reading the one and connecting to the other is done by the
# revision, as the runtime service account, which deploy/setup.sh grants.
grant_once_visible "${SA_EMAIL} roles/run.developer" \
  gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
    --member="serviceAccount:${SA_EMAIL}" \
    --role="roles/run.developer" --condition=None --quiet

# On the image repository, never on the project: it can push this service's
# image and cannot touch any other repository.
grant_once_visible "${SA_EMAIL} roles/artifactregistry.writer on ${IMAGE_REPO}" \
  gcloud artifacts repositories add-iam-policy-binding "${IMAGE_REPO}" \
    --project "${PROJECT_ID}" --location="${REGION}" \
    --member="serviceAccount:${SA_EMAIL}" \
    --role="roles/artifactregistry.writer" --quiet

# A revision runs as the runtime service account, and deploying one means
# acting as it.
grant_once_visible "${SA_EMAIL} roles/iam.serviceAccountUser on ${RUNTIME_SA}" \
  gcloud iam service-accounts add-iam-policy-binding "${RUNTIME_SA}" \
    --project "${PROJECT_ID}" \
    --member="serviceAccount:${SA_EMAIL}" \
    --role="roles/iam.serviceAccountUser" --quiet

echo "==> Creating the workload identity pool and GitHub provider"
if ! gcloud iam workload-identity-pools describe "${POOL}" \
    --project "${PROJECT_ID}" --location=global &>/dev/null; then
  gcloud iam workload-identity-pools create "${POOL}" \
    --project "${PROJECT_ID}" --location=global \
    --display-name="GitHub Actions"
else
  echo "    (pool already exists)"
fi

if ! gcloud iam workload-identity-pools providers describe "${PROVIDER}" \
    --project "${PROJECT_ID}" --location=global --workload-identity-pool="${POOL}" &>/dev/null; then
  # The attribute condition is the security boundary, not a filter. Without it
  # the provider trusts every token GitHub issues to anyone, and any repository
  # on github.com could impersonate this service account. The ref half keeps a
  # workflow edited on a branch from deploying: only a run on main is trusted.
  gcloud iam workload-identity-pools providers create-oidc "${PROVIDER}" \
    --project "${PROJECT_ID}" --location=global --workload-identity-pool="${POOL}" \
    --display-name="${REPO_SLUG}" \
    --issuer-uri="https://token.actions.githubusercontent.com" \
    --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository" \
    --attribute-condition="assertion.repository == '${REPO_SLUG}' && assertion.ref == 'refs/heads/main'"
else
  echo "    (provider already exists)"
fi

POOL_RESOURCE="projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/${POOL}"
PROVIDER_RESOURCE="${POOL_RESOURCE}/providers/${PROVIDER}"

echo "==> Letting ${REPO_SLUG}'s workflows impersonate ${SA_NAME}"
grant_once_visible "${REPO_SLUG} roles/iam.workloadIdentityUser on ${SA_EMAIL}" \
  gcloud iam service-accounts add-iam-policy-binding "${SA_EMAIL}" \
    --project "${PROJECT_ID}" \
    --role="roles/iam.workloadIdentityUser" \
    --member="principalSet://iam.googleapis.com/${POOL_RESOURCE}/attribute.repository/${REPO_SLUG}" \
    --quiet

cat <<SUMMARY

=== Federation ready ===

Set these as repository variables so the Deploy workflow can find them:

  gh variable set GCP_PROJECT_ID --repo ${REPO_SLUG} --body '${PROJECT_ID}'
  gh variable set GCP_WIF_PROVIDER --repo ${REPO_SLUG} --body '${PROVIDER_RESOURCE}'
  gh variable set GCP_DEPLOYER_SA --repo ${REPO_SLUG} --body '${SA_EMAIL}'

To hold each deploy for your approval, give the production environment a
required reviewer (drop the reviewer later to let deploys run straight through):

  gh api -X PUT repos/${REPO_SLUG}/environments/production \\
    -F "reviewers[][type]=User" -F "reviewers[][id]=\$(gh api user --jq .id)"

The service must already allow public access, which the Deploy workflow no
longer sets on each run. To check, and to set it if it is missing:

  gcloud run services get-iam-policy cwfgw4k --project ${PROJECT_ID} --region ${REGION}
  gcloud run services add-iam-policy-binding cwfgw4k --project ${PROJECT_ID} --region ${REGION} \\
    --member=allUsers --role=roles/run.invoker
SUMMARY
