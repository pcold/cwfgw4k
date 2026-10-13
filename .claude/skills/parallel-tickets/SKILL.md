---
name: parallel-tickets
description: Run several Linear tickets in parallel through isolated git worktrees, one subagent each. Invoke when the user asks to work through a set of tickets, a review project, or a backlog "in parallel" / "with subagents" / "as much as you can at once". Covers wave planning from file footprints, the agent brief contract, the serial verification gate, PR opening, and the watch-triage-merge cycle that carries each ticket to merge and then pulls the next unblocked one without being asked. The coordinator half of cwf-review, which each agent runs on its own diff.
---

# parallel-tickets

Coordinating several tickets at once is mostly a scheduling problem, not a coding
one. The failure mode is not a bad diff — it is two agents editing the same file,
or an agent that reports success without evidence. This skill is benrollio's
contract for it, adapted to this repo; the incidents it cites happened there,
and are kept where the rule they justify applies here too.

You are the **coordinator**. Subagents implement and self-review; you plan the
waves, run the verification gate, and open the PRs.

---

## Phase 1 — Map footprints before launching anything

Read every candidate ticket and write down the files each one touches. Then
parallelize only genuinely disjoint sets.

- **MUST NOT** launch two agents whose file sets intersect in an **authored**
  file. Almost every sequencing problem comes from tickets that *read*
  independent but share one file.
- A **generated** file is not one of those. The jOOQ classes under
  `build/generated/jooq` are written by `generateJooq` and never committed. No
  tracked file here is generated, so every tracked file two tickets share is a
  genuine collision.
- **MUST** treat an open PR's files as taken. A branch that is merged is free; a
  branch that is in review is not.
- **MUST** read the board for tickets already **In Progress** — the counterpart
  to the Phase 2 rule that you move your own there. `git worktree list` shows
  only this clone's worktrees and `gh pr list` shows only pushed branches, so a
  sibling clone mid-ticket is invisible to both: its branch is local and its PR
  does not exist yet. The board is the only signal, which is why Phase 2 owes it
  a write.
- **SHOULD** also `ls` the shared worktree parent directory rather than trusting
  `git worktree list`. Another clone's worktrees sit beside yours and are not
  reachable from your repo at all. The accidental way this surfaces is a running
  `gradle` process naming a worktree root you do not own — which happens after
  the fence is already written and the agents are already running.
- **SHOULD** confirm a footprint with `get_impact_radius_tool` /
  `query_graph_tool` (`imports_of`, `callers_of`) on each ticket's named files
  rather than trusting the ticket, falling back to `git grep` only when the
  graph doesn't cover the pattern. Line numbers rot, and a ticket written weeks
  ago may name code that three merged refactors have since moved — the graph
  resolves the current file/symbol set for a fraction of the tokens a repo-wide
  grep costs, and it does it once per candidate rather than once per grep
  pattern.
- **SHOULD** schedule first the candidate that unblocks the most others. The
  footprint map already answers it: count, per candidate, how many other
  candidates share a file with it. One 21-file ticket gated four siblings and
  ran about seventy minutes without a commit, and all four waited it out; its
  own ticket reads like any other.
- Tickets that sweep broadly — a palette change across many files, a compiler
  flag, a branded-type propagation — get the tree to themselves. Schedule them
  alone, last.

The collisions a footprint map misses are in [`fence-checklist.md`](fence-checklist.md) → Phase 1.

Three or four concurrent agents is usually the honest maximum on a mature
codebase. If the fifth candidate needs a fence so tight that the ticket cannot be
finished inside it, that is the signal to sequence it instead.

---

## Phase 2 — Set up isolation

One git worktree per ticket, branched from **latest** `origin/main`:

```sh
git fetch origin
git worktree add -b "<user>/cwf-<num>-<slug>" "<worktrees>/cwf-<num>" origin/main
(cd "<worktrees>/cwf-<num>/ui" && npm ci)
```

The branch takes Linear's `gitBranchName`, so a ticket number is required —
file one first if the work does not have it. Renaming a PR's head branch
through GitHub closes the PR.
Keep `<worktrees>` outside the main clone, beside it: anything that walks the
clone's tree reads a nested worktree as part of it.

Then rebuild the main clone's graph, once per wave, with `main` checked out and
pulled:

```
build_or_update_graph_tool(repo_root="<main clone>")
```

That build is the premise the whole wave explores from — but only for the agents
whose calls name it. `repo_root` auto-detects from the caller's directory, so an
agent that omits it reads its own worktree instead, which is why the brief binds
the clone's path and has every graph instruction pass it. One wave spent its
first seventeen minutes exploring against a build made on an unrelated branch;
the delta happened to be small.

"Once per wave" is discipline here, not a property of the db. Another session
working in the main clone maintains that same graph through the same
`PostToolUse` hook — its `--repo "$(git rev-parse --show-toplevel)"` resolves to
the clone for a session rooted there — so the build the wave explores from can
move without anything here asking it to. It cannot narrow: those updates come
from the clone's own tree, so what a `repo_root`-bearing call reads stays a
whole-clone graph.

Pass no `base`. It defaults to the commit the graph was last built at, which is
the only base that covers the gap however far behind the graph has fallen.

The agents cannot do this for themselves. `.claude/settings.json`'s
`PostToolUse` hook passes `git rev-parse --show-toplevel`, which inside a
worktree resolves to the worktree, so each agent's edits maintain a private
`graph.db` under its own root covering only the files that agent touched. It is
not inert: it is exactly what an auto-detected call returns, and a graph of a
handful of files answering for the whole clone is the failure a rebuild here
cannot reach.

Move each ticket to **In Progress** as you create its worktree, and do it
yourself — never delegate it to the agent, whose brief should not carry tracker
write access as a side effect. Phase 1 already treats a file another branch owns
as taken; this is that same claim where a human can see it.

The window is real rather than theoretical: the branch is created locally here
and not pushed until Phase 5, so the Linear/GitHub integration — which links a
branch by its `cwf-<num>` name — sees nothing at all until then. Stop at In
Progress. **In Review** belongs to that integration on PR open and **Done** to
whoever merges; a manual step at either fights automation for no gain. If a wave
is abandoned, move the ticket back — one stuck In Progress behind a deleted
worktree is worse than one never moved.

Do this at Phase 2 rather than Phase 1: mapping footprints is exploratory, and a
candidate that turns out to need too tight a fence never gets a worktree at all.

Prune merged worktrees between waves; a stale worktree on a deleted branch is a
trap for the next agent.

---

## Phase 3 — Write the agent brief

Every brief carries these sections. The template lives in
[`agent-brief.md`](agent-brief.md) — start from it rather than from memory.

Brief wording that has misfired is in [`fence-checklist.md`](fence-checklist.md) → Phase 3.

The parts that do the work:

### The scope fence

Name the files the agent **may** modify, and a do-not-touch list naming what
sibling agents and open PRs own. The template's `=== SCOPE ===` carries the
rest.

Agents honor this. The ones that crossed a fence anyway said so up front, which
is exactly what made those diffs reviewable instead of surprising.

Draw the fence against the ticket's own **Done when**, not only against what
siblings own. One ticket had to make a failing test's exception message reach
the console log, and was fenced off the build file of the only module that
inherits nothing from the root project: honouring the fence would have shipped
the ticket's own defect in the one place it mattered. From inside a fence
tighter than its ticket an agent can only stop and report, which is the right
move and leaves the ticket unfinished.

### The graph it explores with

The brief **MUST** bind the main clone's path and require it as `repo_root` on
every graph call. Auto-detection resolves to the agent's own worktree, whose
hook-built db covers only the files that agent touched — so an unparameterised
call is answered, by a graph of the agent's own diff.

The brief **MUST** then tell the agent to check that graph before trusting it —
`list_graph_stats_tool`, confirming `built_at_sha` against its branch base and
`head_matches_build` — and, on a mismatch, to **report it and fall back to
Grep/Read**. In that order, because the check cannot stand in for the binding:
run against a worktree it reports a mismatch, correctly and for the wrong
reason, and the fallback that follows is indistinguishable from a graph
legitimately behind.

Name the check and forbid the fix in one breath, and forbid both routes to it.
An agent told only to make sure the graph is current reaches for
`code-review-graph update`, and its shell starts in the session's directory
rather than its worktree — so the rebuild lands on the db every concurrent
sibling reads, from one branch's tree. `build_or_update_graph_tool` reaches that
same db deliberately rather than by accident, because the brief has already
bound the clone as the `repo_root` every graph call passes.

Forbid the CLI invocation rather than the string. `core.hooksPath` is repo-level
config, so `.githooks/pre-commit` runs `code-review-graph update` on every
commit an agent makes. It resolves to the agent's worktree and writes the
private db above, which no call carrying the clone's path reads. Say so in the
brief. Six agents in a row reported the hook as a prohibition they could not
honour.

### What it may read

The fence bounds the agent's writes. Bound its reads too — in the brief, not by
trimming the brief, which is the tempting move and the wrong one. One extra turn
of an agent that wandered costs $0.07 and one wrong edit several, so brief text
that stops the wandering pays for itself many times over.

Fill that section's last line — the file this ticket cannot be done without
reading whole. A read bound that leaves the agent guessing is the one way this
loses money.

### The model it runs on

Pass no `model` on a ticket agent — it writes the diff, and a cheaper tier is
not cheaper on a diff that comes back through review. The same holds for every
`cwf-review` pass an agent forks: buying the gate's verdicts at a discount is
buying a different gate.

Downgrade only a child whose output is a lookup rather than a judgment — locate
these symbols, collect this log, list what matches. `model: 'sonnet'` on that
child is ~60% less per turn at the same context, `'haiku'` ~80%.

### The load-bearing prior work

If the ticket touches code where a bug was recently fixed, **name the fix, name
its guard tests, and require explicit confirmation they still pass unmodified.**

This is the single highest-value line in a refactor brief. A large extraction
preserved two recently-fixed data-loss bugs because the brief named them; the
agent proved it with `git diff` on the file that carried the mechanism and a
list of the tests that still passed.

Not every spec beside the code is a guard in that sense. One that sweeps a
catalog — an `X.ALL` filtered and pinned as a whole list, every table in
`pg_class` — is edited by any ticket that adds an entry, so naming it "must pass
unmodified" hands the agent a fence that contradicts a red build. Name it as a
spec the change is expected to update, and say which assertion.

### Evidence, not assertions

- **MUST** require the *observed failure output* for a failing-test-first
  ticket, not a claim that a test was written. This is what catches a test that
  passes vacuously — one returned before the query rendered, another asserted
  how JavaScript multiplies rather than what the code does.
- **MUST** require a mutation check for any test that passes both before and
  after the change: break the behavior, confirm red, revert, report it.
- **SHOULD** ask what the agent deliberately left undone. The answer is often
  the most useful line in the report.

### Design decisions

When a ticket names two approaches, say: pick one, implement it, and state the
rejected alternative with reasoning. **MUST NOT** ask for both.

---

## Phase 4 — The agent's own gate

Each agent runs [`cwf-review`](../cwf-review/SKILL.md) on its branch and fixes
what it finds before reporting.

---

## Phase 5 — Verify serially, then push

**The agent does not run `./gradlew check`, does not push, and does not open the
PR.** The coordinator does all three, one branch at a time.

This is not ceremony. Seven concurrent Gradle builds crashed test-worker JVMs
with `EOFException` and produced a false red — no individual test failed, and the
retry was green with no code change. The diagnosis cost more than the
parallelism saved.

The `-g` isolation below does not lift that. It separates Gradle state, and the
crash was resource exhaustion instead: `maxParallelForks = 2` with a
Testcontainers Postgres per spec put several containers per build into one
Docker VM. The harness has since moved to one container per test JVM, and
concurrent builds have not been re-measured on it: until they are, concurrency
here needs more machine, not more isolation.

The rule leaks, and it leaks in the expensive direction: an agent that runs
`check` anyway spends a forced execution and a full-context turn on a result
nothing downstream reads. The PR body's Verification section carries the
agent's targeted run and the coordinator's gate and has no third field.

An agent between turns and an agent that has stopped are the same silence from
here. That silence used to be dangerous: both ran out of `~/.gradle`, so a live
agent's next `--stop` killed the coordinator's Gradle run mid-build, which came
back red with no verdict line and zero test failures. The `-g` below ends that,
and with it the *crash* reason for proving an agent is finished rather than
merely between edits. What replaces it:

- **MUST** confirm nothing is running rooted at its worktree —
  `pgrep -fl "<worktrees>/cwf-<num>"`. A gate over a tree still being edited
  verifies a commit that will not exist. The same check comes before the rebase
  and before any edit of yours in that worktree.
- **SHOULD** confirm its transcript has not been written to in the last few
  minutes — `ls -lt ~/.claude/projects/<worktree slug>/*.jsonl`;
  `~/bin/claude-orphans` answers the same question across every session at
  once. An agent idle between turns holds no process of its own, so `pgrep`
  alone reads a paused agent as a finished one.

A third tell costs nothing, because you are about to look anyway: a missing
`PR_BODY.md` at the worktree root means the agent's step 5 never ran, so its
step 4 may not have either. Reconstructing the body by hand is the coordinator
finishing the agent's work without having decided to — ask the agent first.

For each finished branch, in order:

```sh
git fetch origin && git rebase origin/main     # local branch: rebase
cd ui && npm ci                                # main may have moved package-lock
./gradlew check -g ~/.gradle-gate --max-workers=4 --rerun-tasks >| "<session scratchpad>/cwf-<num>-gate.log" 2>&1
```

- **MUST** pass `-g ~/.gradle-gate`, and **MUST NOT** substitute a
  `GRADLE_USER_HOME=` prefix — a prefixed command does not match a
  `Bash(./gradlew *)` permission rule, so it prompts on every gate.
  Without the flag this run shares `~/.gradle` with every agent in the wave:
  one daemon registry, one `build-cache-1`, one set of locks.
- One persistent gate home shared across waves, not one per worktree. A home
  per worktree would re-resolve `caches/modules-2` for every ticket of every
  wave; this one is cold exactly once.
- **MUST** keep `--rerun-tasks`, and **MUST** confirm the same log shows
  `test` and `detekt` **bare for every module** — `:test` and `:detekt`, plus
  the `:<module>:` pair of every project `settings.gradle.kts` includes, with
  nothing after any of those names, not `UP-TO-DATE`, `FROM-CACHE`, `SKIPPED`
  or `NO-SOURCE`. Root
  [`.claude/CLAUDE.md`](../../CLAUDE.md) → Verification has why each of those
  is load-bearing. What makes this gate in particular the one that needs them:
  the gate home outlives the wave, so the *previous* branch's gate filled the
  cache, and a `main` that moved only under `ui/` or `.claude/` leaves every
  Kotlin input identical, so an unforced gate reports `BUILD SUCCESSFUL` over
  `> Task :test FROM-CACHE` and `> Task :detekt UP-TO-DATE`.
- **MUST** rebase here and merge later. The branch has not been pushed yet, so
  its history is private and a rebase costs no force-push — which is the whole
  reason the repo forbids one. The moment the push below lands, that flips:
  a pushed branch syncs with `git merge origin/main`, because reviewers are
  now reading a history that a rebase would rewrite underneath them.
- Forced execution costs about half a minute here against a skipped run's under a second. For
  most branches this is the *only* `check` the tree ever gets, and the agent's
  own pass ran against a tree without `main` in it.
- **MUST** read the build result from inside the log. The coordinator sees this
  run through a background-task completion notification, which reports the exit
  status of the *last* command in a compound — so `./gradlew check > log; echo
  done` always looks like success. Grep for `BUILD SUCCESSFUL` / `BUILD
  FAILED`.
- **MUST** give the log a per-branch name and redirect with `>|`. The retry
  below re-runs this command unchanged, and under `zsh`'s `noclobber` a second
  redirect onto an existing file is refused with a non-zero exit — leaving the
  first run's verdict in place to be read back as the retry's. `>|` gives up
  the refusal that would have surfaced a collision, so the per-branch name is
  what keeps the next branch in the queue from overwriting this one's.
- A red build with no individual test failure and a dead test worker is
  environmental. Retry once before believing it.
- Mass `NoClassDefFoundError` on untouched classes, "multiple Kotlin daemon sessions", or an
  incremental-cache error is a daemon collision: `rm -rf build/kotlin` and re-gate, not `--stop`.
  "Not enough memory" or GC overhead takes `-Pkotlin.daemon.jvmargs=-Xmx3g`.

Green, then:

```sh
git push -u origin "<branch>:<branch>"         # explicit refspec, always
gh pr create --base main --head "<branch>" --title "…" --body-file PR_BODY.md
```

The agent writes `PR_BODY.md` at its worktree root and leaves it **untracked**,
so the coordinator can open the PR without re-deriving the rationale.

### Arm one monitor over every open PR

**MUST** have a monitor covering the open PRs, armed before the first one is
opened. **MUST NOT** arm one watcher per PR. The coordinator only acts when
something wakes it, so an uncovered PR means a review comment or a merge lands
with nobody home.

Per-PR watchers were the previous design and they do not hold. This was already a
**MUST** here, with the consequence spelled out, and a session still armed one for
six PRs, dropped it for the next three, and sat idle through four merges. Two
properties make that shape droppable rather than merely forgettable:

- **It is re-executed N times**, always at the moment a PR opens — which is
  exactly when agents are returning, a report is due, and the next ticket is
  being scoped. It is the step most easily lost under load.
- **Its absence is silent.** From the inside, a missing watcher and a quiet
  queue are the same thing.

One monitor over `gh pr list` fixes both: arming is a single action, a PR opened
later joins with no arming step at all, and *nothing at all happening* becomes a
signal you can notice.

**MUST** arm it with the `Monitor` tool — never `nohup … &`.
A detached background command reports to the coordinator when it *exits*, and
this loop never exits, so everything it finds goes to a log that nothing carries
into context. `Monitor` turns every line the loop prints into a notification in
the chat. The delivery has to push: an instruction to go and read the log is only
ever read by a coordinator who is not, at that moment, forgetting to.

`Monitor` has no `persistent` option: `timeout_ms` caps at 1800000 (30 minutes) and silently clamps
anything larger. Arm at the cap and re-arm on every expiry notice; the loop's state file below is what
lets a re-arm resume rather than re-announce every open PR.

```sh
#!/bin/bash
# bash, not the session `zsh`. Three of that shell's settings each kill this
# loop on its own: `noclobber` refuses every `>` below, `mv` and `cp` are
# aliased to their `-i` forms and answer their own prompt with "not
# overwritten", and `grep` is a function wrapping `ugrep`, whose `--ignore-files`
# skips the very temp files the state lives in. Write this to a file and arm
# `Monitor` on `bash <path>`; pasting it inline reintroduces all three.
set +o noclobber

cd <repo-root>

# The CI review posts a placeholder comment on start and then EDITS IT IN PLACE
# to deliver the verdict, so the signal is the latest timestamp across all three
# endpoints and never a count: a count moves once for the placeholder and never
# again, leaving the BLOCK that replaces it unread. A review still in progress
# stamps `submitted_at` null, which would outsort every real date. All three page
# at 30 oldest-first, and `--jq` runs per page, so the read must paginate and take
# the max across pages. The size is explicit because `--paginate` documents none.
stamp() {
  local inline reviews toplevel
  toplevel=$(gh api --paginate "repos/{owner}/{repo}/issues/$1/comments?per_page=100" \
               --jq '.[].updated_at' 2>/dev/null) || return 1
  inline=$(gh api --paginate "repos/{owner}/{repo}/pulls/$1/comments?per_page=100" \
             --jq '.[].updated_at' 2>/dev/null) || return 1
  reviews=$(gh api --paginate "repos/{owner}/{repo}/pulls/$1/reviews?per_page=100" \
              --jq '.[].submitted_at // empty' 2>/dev/null) || return 1
  printf '%s\n%s\n%s\n' "$toplevel" "$inline" "$reviews" | awk 'NF' | sort | tail -1   # ISO-8601 sorts as text
}

# Two verdict shapes ship. The review contract writes `Verdict: CLEAN` inline;
# the CI reviewer writes a `### Verdict` heading and bolds the token on a line
# below it. A regex anchored to the inline shape reads the second as no verdict
# at all — which is the shape every CI review on this repo uses — so scan a few
# lines past the last heading instead of matching one line.
VERDICT_AWK='
  { line[NR] = $0; if ($0 ~ /Verdict/) last = NR }
  END {
    if (!last) exit
    for (i = last; i <= last + 5 && i <= NR; i++) {
      if (match(line[i], /PROCEED WITH JUSTIFICATIONS/)) { print "PROCEED WITH JUSTIFICATIONS"; exit }
      if (match(line[i], /BLOCK/))                       { print "BLOCK"; exit }
      if (match(line[i], /CLEAN/))                       { print "CLEAN"; exit }
    }
  }'

# State is a file of "<number> <stamp> <woke> <verdict…>" rows. Not an associative
# array: macOS ships bash 3.2, where `declare -A` fails *without stopping the
# script* — every PR then looks new forever and no merge ever fires. The watcher
# may not depend on the shell it runs under. Not a shell variable either: the rows
# are newline-joined and `awk -v` rejects a newline in an assignment. The verdict
# is multi-word (`PROCEED WITH JUSTIFICATIONS`), so it sits last.
# A fixed path, not mktemp, so a re-arm after the 30-minute expiry resumes from it.
seen="<session scratchpad>/pr-monitor.state"; touch "$seen"
open=$(mktemp)
failures=0
while true; do
  # Re-read the set every round, so a PR opened later needs no arming step. Own
  # branches only: a long-lived dependabot PR would otherwise wake this on every
  # bump, and a monitor that cries wolf gets ignored. The limit sits far past any
  # real wave because the default is the thirty newest and the branch filter runs
  # after that cut, and the terminal check below reads a dropped PR as
  # merged-or-closed.
  if ! gh pr list --state open --limit 200 --json number,headRefName \
         --jq '.[]|select(.headRefName|startswith("<user>/"))|.number' > "$open" 2>/dev/null; then
    # Skipping the round is right — a failed poll must not read as "all merged".
    # Skipping it quietly is not: three in a row is an expired token or a limit,
    # not a blip, and by then the reader has spent minutes taking this loop's
    # silence for a calm queue. The count sits here rather than in `stamp()`
    # because every round passes through this one read.
    failures=$((failures + 1))
    [ "$failures" -ge 3 ] && { echo "cannot read the PR list — the queue is NOT confirmed quiet"; failures=0; }
    sleep 60; continue
  fi
  failures=0

  # Leaving the open set is the terminal signal — one check covering merged and
  # closed, so neither can be the silent case.
  for n in $(cut -d' ' -f1 "$seen"); do
    grep -qx "$n" "$open" && continue
    state=$(gh pr view "$n" --json state --jq .state 2>/dev/null || echo UNKNOWN)
    echo "PR #$n $state — if MERGED: prune, pull main, re-map footprints, start next unblocked ticket"
  done

  : > "$seen.next"
  for n in $(cat "$open"); do
    row=$(grep "^$n " "$seen")
    # First sight baselines at "-", never at what the PR already carries: a review
    # that starts and finishes inside a single round would otherwise be recorded
    # as already seen and never triaged.
    was_stamp=$(printf '%s' "$row" | cut -d' ' -f2); : "${was_stamp:=-}"
    woke=$(printf '%s' "$row" | cut -d' ' -f3); : "${woke:=-}"
    was_verdict=$(printf '%s' "$row" | cut -d' ' -f4-); : "${was_verdict:=-}"

    if ! now=$(stamp "$n"); then
      # Do not invent a delta.
      printf '%s %s %s %s\n' "$n" "$was_stamp" "$woke" "$was_verdict" >> "$seen.next"
      continue
    fi
    : "${now:=-}"                        # no feedback yet, and it keeps the row four fields wide

    # Read the verdict while the PR is already in hand, so the wake carries it.
    # "Verdict: BLOCK" is acted on where "feedback changed" is filed for later.
    # Split from the awk, and checked, for the reason `stamp()` is: a pipeline
    # reports only its last command, so a failed `gh` arrives as a PR carrying no
    # verdict. That is the "feedback moved with no verdict on it" wake fired at a
    # PR that has one, and a row that forgets the verdict it already triaged and
    # re-fires it next round. Carry the old row forward and fire nothing; the
    # next round re-reads both fields anyway.
    if ! body=$(gh pr view "$n" --json comments \
                  --jq '[.comments[]|select(.author.login=="claude")|.body]|last // ""' 2>/dev/null); then
      printf '%s %s %s %s\n' "$n" "$was_stamp" "$woke" "$was_verdict" >> "$seen.next"
      continue
    fi
    verdict=$(printf '%s' "$body" | awk "$VERDICT_AWK")
    : "${verdict:=-}"

    if [ "$verdict" != "$was_verdict" ] && [ "$verdict" != "-" ]; then
      echo "PR #$n — Verdict: $verdict — read the review body and triage"
      woke=verdict
    elif [ "$now" != "$was_stamp" ] && [ "$woke" != "stamp" ]; then
      echo "PR #$n — feedback moved with no verdict on it — open the thread"
      woke=stamp
    fi
    printf '%s %s %s %s\n' "$n" "$now" "$woke" "$verdict" >> "$seen.next"
  done
  cat "$seen.next" > "$seen"   # not mv, which the coordinator's shell aliases to mv -i
  sleep 60
done
```

A **CLOSED** PR is not a merge: do not start the next ticket on it, ask. The loop
reports the state rather than assuming, because a filter that only matches the
happy path is silent and indistinguishable from still-waiting. The loop's own
reads answer to the same rule: silence means *nothing happened* only while the
polls are landing, so a run of failed ones says so rather than passing for quiet.

Fire on **any** change to a stamp, not only a later one — a deleted comment moves
it backwards, and a spurious wake is the cheap failure. Expect a benign wake or
two: a linkback bot and the review's own placeholder both move it, and so does a
PR that already carried feedback when the monitor first saw it. Waking on nothing
costs a triage glance; missing a verdict costs the round trip this phase exists to
prevent.

A *run* of them is a different thing from one. The review edits its placeholder
in place while it works, so the stamp moves repeatedly carrying nothing. A
stamp-only change therefore wakes you once and then goes quiet until something
else moves. A verdict change is never collapsed.

Every bug in this watcher has been the same mistake at a different level — **its
coverage is set by how the watched thing actually behaves, not by whichever
signal looks obvious.** In order: polled an endpoint the verdict is never posted
to; counted comments a verdict arrives by editing; armed per-PR, making the
signal as reliable as a step a busy coordinator had to remember; read one page of
an endpoint that pages at thirty, going blind on the PR with the most review
traffic; took whatever a PR already carried at first sight as its baseline,
discarding every verdict that landed before the first poll; read `submitted_at`
as the name promises rather than as an unfinished review leaves it, and that
`null` text-sorts above every real date; took the newest thirty open PRs for the
open set, so the longest-open branches fell out of the read — and out of the read
is how this loop spells merged; left the page size to a `gh` default, turning
each round into a burst into a rate limit that fails the read and freezes the
stamp; watched only what a successful read returns, in a loop whose reader has
been told that hearing nothing is what nothing-happened sounds like; got every
read right and was detached with `nohup … &`, six correct events into a log and
none of them acted on; handed newline-joined state to `awk -v`, erroring to
stderr every round while reporting armed; emitted on each placeholder edit the
review makes while it works, and four dead wakes in a row teach a reader to stop
opening them; matched one of the two verdict shapes in the wild, so every CI
review — which posts the other — read as no verdict at all; and ran in a shell
whose `noclobber` refuses the redirects the state file is built from, spending
its whole life reporting that it could not read the PR list.

Before trusting a watcher, go and look at what the real event does, at how much
of it one read returns and what the whole of it costs, at what a first look is
allowed to discard, at what a failed read is allowed to leave unsaid, at where
what it prints ends up and who is there to read it — and at what has to happen
for the watch to exist at all.

### Confirm it is armed, not merely started

`Monitor` reports a loop as running whether or not the loop can do anything, and
this one is built to survive a failed round quietly. So read
`<session tasks>/<task id>.output` once, a minute after arming. `file exists:` is
`noclobber` refusing a redirect and `not overwritten` is an `-i` alias answering
its own prompt; either means the snippet is being run by the wrong shell and the
monitor is dead. `cannot read the PR list` three rounds running says the same
thing from the other end, and looks exactly like an expired token.

A second read, against a PR that already carries a review, is what proves the
verdict half: `gh pr view <n> --json comments` piped through the extractor must
print one of the three tokens. A monitor that polls correctly and extracts
nothing wakes you for stamps forever and never once for a `BLOCK`.

The monitor dies with the session. If a session ends with PRs open, say so in the
handoff — the queue does not resume itself.

What the watch loop and the rest of the coordinator's work have misread is in [`pitfalls.md`](pitfalls.md).

---

## Phase 6 — After the PR opens

Opening the PR is not the end of the coordinator's job. Feedback arrives
asynchronously and the branch stays yours until it merges. The monitor from
Phase 5 is what wakes you for either event; until it fires, the ticket is still
in flight.

A ticket's cycle ends at **merge**, not at PR-open and not at "I posted my
reasoning". Handing a PR over with "say the word and I'll start the next one" is
the failure this phase exists to prevent — it converts an autonomous queue into
one that stalls on every round trip.

### Read the verdict, not the check status

A check's colour and its content are different facts, and both directions have
bitten:

- **A failed check may be infrastructure.** During a GitHub outage the review
  action took a `503` posting its opening comment and failed having never read
  the diff. Re-running is the fix; diagnosing it as a code problem costs an
  iteration and finds nothing.
- **A passing check may still carry findings** — a review can pass while
  leaving a COMMENTED verdict with real content in it.

Open the review body before deciding anything. If no body was posted, read the
job log and find out why before assuming.

### Triage, then keep moving

Adjudicate each finding against the code and the guides rather than against the
reviewer's confidence — check the claim, don't weigh the tone.

- **Accept** → fix as new commits, re-verify, push. Never amend. The push
  re-triggers review, so expect another round and keep watching for it.
- **Decline** → post the reasoning *and the evidence* as a PR comment, then
  carry on. A finding contradicted by three occurrences on `main` is not a
  defect, and leaving it unanswered makes a deliberate call look like an
  oversight.

**A declined finding is settled by the merge, not by the comment.** Posting the
reasoning does not close the thread — the user may read it and disagree, and
their reply is the authoritative answer. So keep the monitor running after a
decline and treat a reply on that thread as feedback to act on, exactly like a
reviewer's. Do not move a ticket forward on the strength of your own no-fix
rationale alone.

**A BLOCK you judge wrong is not a stop condition.** Do not ask whether to
override it and do not park the wave waiting for an answer — that stalls every
other ticket over one disagreement. The PR comment *is* the escalation: the
user merges if they agree and replies on the PR if they do not. Post, then go
back to the queue.

Escalate synchronously only for what the code cannot settle — a product call,
or a rule genuinely ambiguous enough that both readings have support in the
guide. Even then, prefer filing it and continuing over blocking.

What of this loop reaches the user is **What to relay** at the end of this file.

### On merge, pull the next unblocked ticket

The merge notification is the trigger for the next ticket, not a prompt to ask
for one. On merge, without waiting to be asked:

1. Prune the merged worktree and its local branch — the remote branch is already
   gone (`delete_branch_on_merge`), and a stale worktree on a deleted branch is a
   trap for the next agent.
2. `git checkout main && git pull --ff-only`.
3. Rebuild the graph as in Phase 2 — the next step reads it.
4. Re-run the Phase 1 footprint map against the **new** `main`. A merge frees
   whatever its files were blocking, and it may have moved code a queued ticket
   named.
5. Start every ticket that is now **unblocked**, in parallel where footprints are
   disjoint — Phase 1's rules are unchanged, and one-at-a-time is a consequence
   of the footprints, never the default.

A ticket is **blocked** while any ticket sharing its file footprint is unmerged.
That is what makes a queue serial: a set of tickets that all edit one mapping
file or one catalog enum can only ever go one at a time, and the honest move is
to say so rather than launch agents into a guaranteed conflict. A ticket blocked
only by *review latency* is not blocked at all — if its footprint is free, start
it while the other PR waits.

If nothing is unblocked and PRs are still open, say what is waiting on what
rather than going quiet.

### A merge can land under you

A PR that merges while you are verifying an addition to it deletes its branch,
and a later `git push` silently **recreates** that branch with no PR attached —
`[new branch]` on a branch pushed an hour ago is the tell. The commit is then
orphaned and `main` is missing the change. Check `gh pr view` before pushing to
a branch you did not just create, and if it merged, re-land the work under its
own ticket rather than reopening the old one.

---

## A resume costs a full run

Resuming a subagent replays its accumulated context on every turn of the
follow-up. Pick by what the follow-up actually needs.

- **It names the file, the line and the replacement** → do it yourself. Your own
  context is already warm. A review BLOCK that pinpoints five comment lines is
  this case.
- **It needs the agent's live model of code it just wrote** → resume, and keep
  the follow-up short. Applying a bound change across five test doubles, hours
  after establishing what that bound means, is this case.
- **It is fresh work in the same area** → spawn a new agent over the worktree and
  the outgoing agent's `PR_BODY.md`. That file already carries the decisions and
  the rejected alternatives, so a successor reads a few thousand tokens of prose
  rather than replaying a few hundred thousand of transcript.
- **Two agents disagree** → never resume them into a conversation with each
  other. Every message is a resume and the exchange pays for both transcripts on
  every turn. Have each write its findings to a file and give both files plus the
  diff to a fresh adjudicator — one run, and it has no prior claim of its own to
  rescue.

Batch what you can: gather every follow-up before waking an agent once.

An agent killed mid-gate with good commits already on its branch is what the
mechanism is for, and re-briefing that from scratch costs more and risks the
work.

---

## When you hit a rate limit

A rate limit is a pause, not a failure. Everything the wave needs is already
durable — worktrees on disk, branches carrying commits, tickets In Progress,
PRs open. It costs time, not state, so nothing needs tearing down and nothing
needs redoing.

It can land in any phase: inside a subagent, in the serial `./gradlew check`
queue, or mid-triage on an open PR. The response is the same each time.

- **Tell it apart from an outage before reacting.** A `503` means the service is
  unhealthy and a retry in a few minutes is reasonable — that is the Phase 6
  case. A `429` or a usage-limit message means wait for the stated reset;
  retrying it on a short interval spends quota to rediscover a time the error
  already gave you.
- **Stop starting work.** Do not launch the next wave, and do not relaunch a
  subagent that died on a limit — that is the one action guaranteed to deepen
  the hole, and a dead agent is exactly what invites it.
- **A rate-limited agent has not produced a bad diff.** Do not review, revert,
  or re-brief on that basis. Check its worktree first: often the commits are
  all there and only the final report was lost, in which case the branch is
  ready for Phase 5 as it stands.
- **Resume where the wave stopped** once the limit resets — finish the serial
  verification queue, then the open-PR triage. Re-launch only the agents whose
  work is genuinely incomplete.
- **Say it once.** One message naming the reset time and what is parked beats a
  line per retry.

---

## Judgment the briefs must supply

Agents get these right when told and would plausibly get them wrong by default.
They are worth restating in any brief that refactors shared code.

- **An abstraction that only fits by taking flags is worse than the honest
  copies.** Extract what is genuinely common; leave what is not, and say which.
- **A recorded justification is not to be flattened for uniformity.** A form
  deliberately validating on submit only, with the reason written down, should be
  honored and pinned with a test — not converted because its siblings differ.
- **Deleting a duplicate is not always right.** Two forms that look the same
  may differ in a field one audience must never see; separation makes that
  absence structural rather than a prop a caller could pass wrong.
- **Prefer the smallest thing that removes the duplication.**
- **Report an unwelcome number plainly.** A de-duplication ticket that comes out
  a net line *add* should say so.

---

## What to relay

Subagent reports are not shown to the user. Relay the decision, the evidence, and
anything needing a human call — not the transcript. The lines worth surfacing are
the ones where an agent contradicted its ticket, crossed a fence, found a second
bug, deleted its own test, or corrected an instruction you gave it.

Between those reports the coordinator's own output is the larger volume, and the
same bar governs it. Phase 5's monitor fires on every feedback change across
every open PR — dozens in a six-PR wave.

- **A monitor event is a trigger to act, not an event to report.** What the
  triage produces can be worth surfacing — a verdict, a blocker, a decision
  needing a human, one of the surprises above. The wake itself never is.
- Do not re-render state that has not materially changed, and do not restate a
  plan already agreed.

The pull the other way is structural rather than careless: the monitor makes
hearing nothing the signal that nothing happened, so watching it invites
confirming that each wake was benign — and a benign wake is the one case that
warrants silence.

**The bar is materiality, not brevity.** An agent that disproved its ticket's
premise, or found a production defect behind a test flake, is worth its
paragraph; a report that drops one to stay short fails this rule exactly as a
narrated wake does.
