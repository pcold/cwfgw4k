#!/usr/bin/env bash
#
# .githooks/pre-push and branch-name-check.yml each carry their own copy of the
# pattern, and a copy that drifts still passes on its own. So the two lines are
# asserted identical here, and both checks are driven over one table.

set -uo pipefail

script_dir="$(cd "$(dirname "$0")" && pwd)"
repo="$(cd "${script_dir}/../.." && pwd)"
hook="${repo}/.githooks/pre-push"
workflow="${repo}/.github/workflows/branch-name-check.yml"

work="$(mktemp -d)"
trap 'rm -rf "${work}"' EXIT

failures=0
cases=0
tip="5f2c2a6c0ad4ea273e68d54e579803dfc90911cd"
absent="0000000000000000000000000000000000000000"
linear_name="pcold/cwf-65-check-branch-names-in-ci-and-in-a-pre-push-hook"

# Lifted out of the YAML rather than copied here, so the cases run the script
# Actions runs: the lines indented under the first `run: |`, up to the dedent.
awk '
    /^ *run: \|$/ && !inside { match($0, /^ */); key = RLENGTH; inside = 1; next }
    inside && /^ *$/ { print ""; next }
    inside {
        match($0, /^ */)
        if (RLENGTH <= key) exit
        if (!depth) depth = RLENGTH
        print substr($0, depth + 1)
    }
' "${workflow}" > "${work}/step.sh"

# Both run from ${work}, an empty directory: the workflow's job checks nothing
# out, and the hook must not need a file either.

# pre_push <ref line>... — the lines git writes to the hook's stdin for one push.
pre_push() {
    (cd "${work}" && printf '%s\n' "$@" | "${hook}" origin git@github.com:pcold/cwfgw4k.git)
}

# Git ends every line it writes; a check of the hook piped in by hand often does not.
unterminated_push() {
    (cd "${work}" && printf '%s' "$1" | "${hook}" origin git@github.com:pcold/cwfgw4k.git)
}

nothing_to_push() {
    (cd "${work}" && "${hook}" origin git@github.com:pcold/cwfgw4k.git < /dev/null)
}

# The form Actions gives a Linux step that names no shell.
workflow_step() {
    (cd "${work}" && GITHUB_HEAD_REF="$1" bash -e "${work}/step.sh")
}

pattern_line() {
    grep -F "pattern='" "$1" | sed 's/^ *//'
}

same_pattern() {
    local in_hook in_workflow
    in_hook="$(pattern_line "${hook}")"
    in_workflow="$(pattern_line "${workflow}")"
    printf 'hook:     %s\nworkflow: %s\n' "${in_hook}" "${in_workflow}"
    [ -n "${in_hook}" ] && [ "${in_hook}" = "${in_workflow}" ]
}

# expect <wanted exit> <name> <command>...
expect() {
    local wanted="$1" name="$2"
    local actual
    shift 2
    cases=$((cases + 1))
    "$@" > "${work}/output" 2>&1
    actual=$?
    if [ "${actual}" -eq "${wanted}" ]; then
        echo "ok    ${name}"
    else
        echo "::error::${name}: wanted exit ${wanted}, got ${actual}"
        sed 's/^/          /' "${work}/output"
        failures=$((failures + 1))
    fi
}

# either <wanted exit> <branch> — a new branch pushed under its own name, and a pull request from it.
either() {
    expect "$1" "hook:     $2" pre_push "refs/heads/$2 ${tip} refs/heads/$2 ${absent}"
    expect "$1" "workflow: $2" workflow_step "$2"
}

expect 0 "the hook and the workflow carry the same pattern line" same_pattern
expect 0 "the workflow's script was found in the YAML" test -s "${work}/step.sh"

either 0 "${linear_name}"
either 0 "a-b/cwf-1-x"
either 1 "scratch"
either 1 "pcold/CWF-65-x"
either 1 "Pcold/cwf-65-x"
either 1 "cwf-65-x"
either 1 "pcold/abc-65-x"
either 1 "pcold/cwf-65"
either 1 "pcold/cwf-65-"
either 1 "pcold/cwf-x-foo"
either 1 "pcold/cwf-65-a_b"
either 1 "a/pcold/cwf-65-x"
either 1 "dependabot/npm_and_yarn/ui/foo-1.2.3"

expect 0 "hook:     a push to main" \
    pre_push "refs/heads/main ${tip} refs/heads/main ${tip}"
expect 0 "hook:     a deletion of a misnamed branch" \
    pre_push "(delete) ${absent} refs/heads/scratch ${tip}"
expect 0 "hook:     a deletion in a SHA-256 repository" \
    pre_push "(delete) ${absent}${absent:0:24} refs/heads/scratch ${tip}"
expect 0 "hook:     a tag" \
    pre_push "refs/tags/v1 ${tip} refs/tags/v1 ${absent}"
expect 0 "hook:     nothing to push" \
    nothing_to_push
expect 0 "hook:     a misnamed local branch pushed to a well-named remote one" \
    pre_push "refs/heads/scratch ${tip} refs/heads/${linear_name} ${absent}"
expect 1 "hook:     a well-named local branch pushed to a misnamed remote one" \
    pre_push "refs/heads/${linear_name} ${tip} refs/heads/scratch ${absent}"
expect 1 "hook:     a misnamed branch after a well-named one" \
    pre_push "refs/heads/${linear_name} ${tip} refs/heads/${linear_name} ${absent}" \
             "refs/heads/scratch ${tip} refs/heads/scratch ${absent}"
expect 1 "hook:     a misnamed branch on a line with no newline" \
    unterminated_push "refs/heads/scratch ${tip} refs/heads/scratch ${absent}"
expect 0 "hook:     a tag, a deletion and main in one push" \
    pre_push "refs/tags/v1 ${tip} refs/tags/v1 ${absent}" \
             "(delete) ${absent} refs/heads/scratch ${tip}" \
             "refs/heads/main ${tip} refs/heads/main ${tip}"

# The hook's exemption for main is for a push to it. The workflow sees head
# branches and has none.
expect 1 "workflow: a fork's main" \
    workflow_step "main"
expect 1 "workflow: no head branch, as on a push event" \
    workflow_step ""
expect 1 "workflow: a name carrying a command substitution" \
    workflow_step 'pcold/cwf-65-$(id)'

if [ "${failures}" -ne 0 ]; then
    echo "::error::${failures} case(s) of the branch-name checks failed"
    exit 1
fi

echo "the pre-push hook and the branch-name workflow hold on all ${cases} cases"
