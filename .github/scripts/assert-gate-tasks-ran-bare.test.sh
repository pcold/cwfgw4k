#!/usr/bin/env bash
#
# The gate's own assertion is the only thing between a FROM-CACHE run and a
# green required check, and a weakened one still reports success — which is the
# shape of the bug it was written for. So it gets driven here rather than only
# in Actions, against a settings file and a log pinned in fixtures/.
#
# Pinned rather than the repo's own settings.gradle.kts: the log would have to
# be regenerated for every module ever added, and the day someone forgot, this
# would go red over a fixture instead of over the thing under test.
#
# Each failing case is derived from the passing log by one edit, so a case that
# fails names the property that broke rather than a whole rewritten file.

set -uo pipefail

script_dir="$(cd "$(dirname "$0")" && pwd)"
assert="${script_dir}/assert-gate-tasks-ran-bare.sh"
fixtures="${script_dir}/fixtures"
settings="${fixtures}/settings.gradle.kts"
# .txt and not .log: .gitignore excludes *.log, so the fixture would stay
# untracked, keep passing on the laptop that wrote it, and fail in Actions.
ran_bare="${fixtures}/gate-ran-bare.txt"

work="$(mktemp -d)"
trap 'rm -rf "${work}"' EXIT

failures=0
cases=0

expect() {
    local wanted="$1" name="$2" settings_arg="$3" log_arg="$4"
    local actual
    cases=$((cases + 1))
    "${assert}" "${settings_arg}" "${log_arg}" > "${work}/output" 2>&1
    actual=$?
    if [ "${actual}" -eq "${wanted}" ]; then
        echo "ok    ${name}"
    else
        echo "::error::${name}: wanted exit ${wanted}, got ${actual}"
        sed 's/^/          /' "${work}/output"
        failures=$((failures + 1))
    fi
}

sed 's|^> Task :beta:detekt$|> Task :beta:detekt FROM-CACHE|' "${ran_bare}" > "${work}/subproject-cached.log"
sed 's|^> Task :test$|> Task :test UP-TO-DATE|' "${ran_bare}" > "${work}/root-stale.log"
grep -vxF '> Task :beta:test' "${ran_bare}" > "${work}/subproject-dropped.log"
# No subproject lines at all, so the reconciliation at the bottom of the
# assertion has nothing to catch and cannot stand in for the case under test.
grep -v '^> Task :alpha:\|^> Task :beta:' "${ran_bare}" > "${work}/root-only.log"

printf 'rootProject.name = "f"\ninclude("alpha", "beta")\n' > "${work}/one-include.gradle.kts"
# No trailing newline, which `read` alone reports as end of input.
printf 'rootProject.name = "f"\ninclude("alpha")\ninclude("beta")' > "${work}/unterminated.gradle.kts"
printf 'rootProject.name = "f"\ninclude("alpha")\n' > "${work}/beta-missing.gradle.kts"
printf 'rootProject.name = "f"\nlistOf("alpha", "beta").forEach { include(it) }\n' > "${work}/unreadable.gradle.kts"

expect 0 "every gate task bare"                "${settings}"                      "${ran_bare}"
expect 0 "two modules on one include"          "${work}/one-include.gradle.kts"   "${ran_bare}"
expect 0 "a settings file with no final newline" "${work}/unterminated.gradle.kts" "${ran_bare}"
expect 1 "a subproject task served FROM-CACHE" "${settings}"                      "${work}/subproject-cached.log"
expect 1 "a subproject task with no line at all" "${settings}"                    "${work}/subproject-dropped.log"
expect 1 "the root task UP-TO-DATE"            "${settings}"                      "${work}/root-stale.log"
expect 1 "a module nothing in settings yields" "${work}/beta-missing.gradle.kts"  "${ran_bare}"
expect 1 "an include the parser cannot read"   "${work}/unreadable.gradle.kts"    "${work}/root-only.log"
expect 1 "a log that was never written"        "${settings}"                      "${work}/absent.log"

if [ "${failures}" -ne 0 ]; then
    echo "::error::${failures} case(s) of the bare-task assertion's own behaviour failed"
    exit 1
fi

echo "assert-gate-tasks-ran-bare.sh holds on all ${cases} cases"
