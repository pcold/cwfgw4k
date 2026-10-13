#!/usr/bin/env bash
#
# The gate job records each tree it passes as an artifact named for it, and this
# looks that name up. Whenever it cannot show a pass it exits non-zero and the
# caller runs the gate: no artifact, an expired one, a run that did not pass, an
# API call that failed or answered in a shape this does not know. Skipping is
# the claim that needs proof, so an unknown is a no.
#
# The tree and not a commit, because a squash merge is a new commit whose bytes
# can be exactly the ones a PR's gate tested, and only the tree says so.
#
# A lookup prints one line either way, for the step summary.

set -uo pipefail

tree="${1:-}"

if ! [[ "${tree}" =~ ^[0-9a-f]{40}([0-9a-f]{24})?$ ]]; then
    echo "usage: $(basename "$0") <tree-sha>" >&2
    exit 2
fi

run_the_gate() {
    echo "Running the gate: $*."
    exit 1
}

listing=$(gh api "repos/${GITHUB_REPOSITORY}/actions/artifacts?name=gate-passed-${tree}&per_page=100") \
    || run_the_gate "could not list the proofs of tree ${tree}"

# `expired == false` rather than `!= true`, so a field that went missing reads as
# expired.
run_ids=$(jq -r '.artifacts[] | select(.expired == false) | .workflow_run.id | numbers' <<<"${listing}") \
    || run_the_gate "the proof listing for tree ${tree} was not in a shape this knows"

for run_id in ${run_ids}; do
    # A run that cannot be read proves nothing, but another candidate still might.
    run=$(gh api "repos/${GITHUB_REPOSITORY}/actions/runs/${run_id}") || continue
    # The path, because any workflow can upload an artifact under this name.
    if jq -e '.path == ".github/workflows/check.yml" and .conclusion == "success"' <<<"${run}" >/dev/null; then
        echo "Skipping the gate: tree ${tree} already passed in $(jq -r '.html_url' <<<"${run}")."
        exit 0
    fi
done

run_the_gate "no passing run of check.yml has recorded tree ${tree}"
