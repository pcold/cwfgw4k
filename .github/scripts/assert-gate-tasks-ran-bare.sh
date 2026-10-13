#!/usr/bin/env bash
#
# `grep -qxF` and not a substring match: the whole-line match is the whole
# assertion. Gradle appends UP-TO-DATE / FROM-CACHE / SKIPPED / NO-SOURCE to a
# task line that did not really run, so those fail to match.
#
# The module list is read out of settings.gradle.kts rather than kept here,
# because that is the one file a module cannot be added without editing, so a
# new subproject is asserted the day it is added. The price is a regex over a
# Kotlin DSL, so an `include` this cannot read is a hard failure rather than a
# skipped line.
#
# Both tasks are required of every module, not just the ones that happen to have
# them today. A module that should not be gated fails here, which is where the
# case for exempting it gets made and read.

set -uo pipefail

settings_file="${1:-}"
check_log="${2:-}"

if [ -z "${settings_file}" ] || [ -z "${check_log}" ]; then
    echo "usage: $(basename "$0") <settings.gradle.kts> <gradle-check.log>" >&2
    exit 2
fi

for required in "${settings_file}" "${check_log}"; do
    if [ ! -f "${required}" ]; then
        echo "::error::${required} does not exist, so nothing was asserted"
        exit 1
    fi
done

include_shape='^include[[:space:]]*\([[:space:]]*"[^"]+"([[:space:]]*,[[:space:]]*"[^"]+")*[[:space:]]*\)$'

asserted_modules=""
expected_tasks=(":test" ":detekt")

# The `|| [ -n ... ]` takes the last line of a file that ends without a newline,
# which `read` alone reports as end of input and drops.
while IFS= read -r raw_line || [ -n "${raw_line}" ]; do
    line="${raw_line%%//*}"
    line="${line#"${line%%[![:space:]]*}"}"
    line="${line%"${line##*[![:space:]]}"}"
    # Unanchored, so `listOf(...).forEach { include(it) }` is caught by the shape
    # test below instead of passing as a line with no include on it. includeBuild
    # has no `include(` in it, and its tasks belong to another build.
    [[ "${line}" =~ include[[:space:]]*\( ]] || continue
    if ! [[ "${line}" =~ ${include_shape} ]]; then
        echo "::error::${settings_file} has an include this cannot read, so its module would go unasserted: ${raw_line}"
        exit 1
    fi
    while IFS= read -r quoted_name; do
        name="${quoted_name//\"/}"
        asserted_modules="${asserted_modules} :${name#:}"
        expected_tasks+=(":${name#:}:test" ":${name#:}:detekt")
    done < <(printf '%s\n' "${line}" | grep -o '"[^"]*"')
done < "${settings_file}"

status=0

for task in "${expected_tasks[@]}"; do
    if ! grep -qxF "> Task ${task}" "${check_log}"; then
        found="$(grep -m1 -E "^> Task ${task}( |$)" "${check_log}" || echo '(no line at all — dropped from the task graph)')"
        echo "::error::'${task}' did not run bare — ${found}"
        status=1
    fi
done

# What reading the log back reaches: an `include` in a shape with no literal
# `include(` in it, and an included build whose tasks run under this one's
# check. Both want a decision here rather than a pass, which is why an
# unrecognised module is an error and not a quiet widening of the list. It
# reaches neither when the module's own gate task is missing from the log as
# well — nothing here can see a module that was never asserted and never ran.
observed_modules="$(sed -nE 's/^> Task (:.*):(test|detekt)( .*)?$/\1/p' "${check_log}" | sort -u)"
for module_path in ${observed_modules}; do
    if [[ " ${asserted_modules} " != *" ${module_path} "* ]]; then
        echo "::error::'${module_path}' ran a gate task that nothing asserts — ${settings_file} did not yield it"
        status=1
    fi
done

if [ "${status}" -eq 0 ]; then
    echo "ran bare: ${expected_tasks[*]}"
fi

exit "${status}"
