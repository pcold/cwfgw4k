#!/usr/bin/env bash
#
# A lookup that finds a proof it should not skips the gate on main, and nothing
# downstream would notice. So its refusals get driven here against canned API
# answers rather than discovered in main's history.
#
# A stub `gh` on PATH serves each case's answers from a directory the case
# writes, and fails any endpoint the case did not write, as an HTTP error would.
# Every case is the passing one with one thing changed, so a case that fails
# names the property that broke.

set -uo pipefail

script_dir="$(cd "$(dirname "$0")" && pwd)"
lookup="${script_dir}/tree-already-proven.sh"
tree="4b825dc642cb6eb9a060e54bf8d69288fbee4904"
listing_endpoint="repos/o/r/actions/artifacts?name=gate-passed-${tree}&per_page=100"

work="$(mktemp -d)"
trap 'rm -rf "${work}"' EXIT

mkdir "${work}/bin"
cat > "${work}/bin/gh" <<'STUB'
#!/usr/bin/env bash
answer="${STUB_ANSWERS}/$(printf '%s' "$2" | tr -c 'A-Za-z0-9' '_')"
if [ "$1" = "api" ] && [ -f "${answer}" ]; then
    cat "${answer}"
else
    echo "gh: Not Found (HTTP 404)" >&2
    exit 1
fi
STUB
chmod +x "${work}/bin/gh"

failures=0
cases=0

# answers <case> <endpoint> <body>
answers() {
    mkdir -p "${work}/$1"
    printf '%s' "$3" > "${work}/$1/$(printf '%s' "$2" | tr -c 'A-Za-z0-9' '_')"
}

artifact() {
    printf '{"name":"gate-passed-%s","expired":%s,"workflow_run":{"id":%s}}' "${tree}" "$2" "$1"
}

run() {
    printf '{"id":%s,"path":"%s","status":"completed","conclusion":%s,"html_url":"https://github.com/o/r/actions/runs/%s"}' \
        "$1" "$2" "$3" "$1"
}

# expect <wanted-exit> <name> <case> [tree]
expect() {
    local wanted="$1" name="$2" case_dir="${work}/$3" tree_arg="${4-${tree}}"
    local actual
    cases=$((cases + 1))
    mkdir -p "${case_dir}"
    STUB_ANSWERS="${case_dir}" PATH="${work}/bin:${PATH}" GITHUB_REPOSITORY="o/r" \
        "${lookup}" "${tree_arg}" > "${work}/output" 2>&1
    actual=$?
    if [ "${actual}" -eq "${wanted}" ]; then
        echo "ok    ${name}"
    else
        echo "::error::${name}: wanted exit ${wanted}, got ${actual}"
        sed 's/^/          /' "${work}/output"
        failures=$((failures + 1))
    fi
}

check_yml=".github/workflows/check.yml"

answers passed "${listing_endpoint}" "{\"artifacts\":[$(artifact 101 false)]}"
answers passed "repos/o/r/actions/runs/101" "$(run 101 "${check_yml}" '"success"')"

answers unrecorded "${listing_endpoint}" '{"total_count":0,"artifacts":[]}'

answers expired "${listing_endpoint}" "{\"artifacts\":[$(artifact 101 true)]}"
answers expired "repos/o/r/actions/runs/101" "$(run 101 "${check_yml}" '"success"')"

answers failed "${listing_endpoint}" "{\"artifacts\":[$(artifact 101 false)]}"
answers failed "repos/o/r/actions/runs/101" "$(run 101 "${check_yml}" '"failure"')"

# A re-run still going reports no conclusion at all.
answers unfinished "${listing_endpoint}" "{\"artifacts\":[$(artifact 101 false)]}"
answers unfinished "repos/o/r/actions/runs/101" "$(run 101 "${check_yml}" null)"

answers elsewhere "${listing_endpoint}" "{\"artifacts\":[$(artifact 101 false)]}"
answers elsewhere "repos/o/r/actions/runs/101" "$(run 101 ".github/workflows/other.yml" '"success"')"

answers second-candidate "${listing_endpoint}" "{\"artifacts\":[$(artifact 101 false),$(artifact 102 false)]}"
answers second-candidate "repos/o/r/actions/runs/101" "$(run 101 "${check_yml}" '"failure"')"
answers second-candidate "repos/o/r/actions/runs/102" "$(run 102 "${check_yml}" '"success"')"

answers run-refused "${listing_endpoint}" "{\"artifacts\":[$(artifact 101 false)]}"

answers unknown-shape "${listing_endpoint}" '{"message":"Bad credentials"}'

answers not-json "${listing_endpoint}" '<html>502 Bad Gateway</html>'

expect 0 "a tree a passing check.yml run recorded"   passed
expect 0 "a failed run's proof beside a passing one" second-candidate
expect 1 "a tree no run recorded"                    unrecorded
expect 1 "a proof past its retention"                expired
expect 1 "a proof from a run that failed"            failed
expect 1 "a proof from a run not yet finished"       unfinished
expect 1 "a proof from another workflow"             elsewhere
expect 1 "a listing the API refused"                 listing-refused
expect 1 "a run the API refused"                     run-refused
expect 1 "a listing in a shape this does not know"   unknown-shape
expect 1 "a listing that is not JSON"                not-json
expect 2 "a tree that is not a sha"                  passed "HEAD"
expect 2 "no tree at all"                            passed ""

# The summary line is how main's history says which run vouched for a skip.
expect 0 "the summary names the run that passed"     passed
if ! grep -qF "https://github.com/o/r/actions/runs/101" "${work}/output"; then
    echo "::error::the summary names the run that passed: it did not"
    sed 's/^/          /' "${work}/output"
    failures=$((failures + 1))
fi

if [ "${failures}" -ne 0 ]; then
    echo "::error::${failures} case(s) of the proof lookup's own behaviour failed"
    exit 1
fi

echo "tree-already-proven.sh holds on all ${cases} cases"
