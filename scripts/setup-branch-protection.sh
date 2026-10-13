#!/usr/bin/env bash
# Apply branch protection to `main`, mirroring the benrollio baseline.
#
# GitHub stores the live protection state server-side, not in the repo — this
# script is the in-repo record of the intended config and the one-shot command
# to (re)apply it. Run it once per repo (needs admin on the repo):
#
#   ./scripts/setup-branch-protection.sh
#
# It requires the `gradle check` status context to exist, which it will once the
# Check workflow (.github/workflows/check.yml) has run at least once on a PR.

set -euo pipefail

REPO="${REPO:-pcold/cwfgw4k}"
BRANCH="${BRANCH:-main}"

echo "Applying branch protection to $REPO@$BRANCH ..."

# - required check "gradle check": the canonical gate, as check.yml runs it.
# - strict=false: don't force branches to be up-to-date with main before merge
#   (squash workflow keeps history linear enough; avoids serialized re-runs).
#   The push-to-main run of the gate covers the merged tree instead.
# - enforce_admins=true: the gate applies to everyone, including admins.
# - no required reviews (solo workflow); the bot review advises, doesn't block.
# - block force-push and deletion of main.
gh api -X PUT "repos/$REPO/branches/$BRANCH/protection" \
  --input - <<'JSON'
{
  "required_status_checks": {
    "strict": false,
    "contexts": ["gradle check"]
  },
  "enforce_admins": true,
  "required_pull_request_reviews": null,
  "restrictions": null,
  "allow_force_pushes": false,
  "allow_deletions": false
}
JSON

echo "Done. Current required checks:"
gh api "repos/$REPO/branches/$BRANCH/protection/required_status_checks" \
  --jq '.contexts'
