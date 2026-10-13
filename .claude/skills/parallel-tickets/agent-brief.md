# Agent brief template

Fill every `<…>`. Delete a section only when it genuinely does not apply — the
sections exist because omitting them produced a worse diff at least once.

So run the repo's own cover-the-comment test one level up: **cover the clause
and leave the agent the repo.** If it could have reached that fact through
`.claude/CLAUDE.md`, the graph, the ticket, or a `Read`, the clause is a second
copy.

| Say it — the agent cannot reach it | Point at it — the agent resolves it |
| -- | -- |
| The fence, and which sibling owns what | File contents, structure, call sites |
| A decision already taken, and what it rejected | Line numbers, which rot |
| Which guard test protects which prior fix, and why the fix is subtle | What a test asserts today |

Apply it per clause, never as a length budget: under a cap the fence and the
guard-test list read as boilerplate and go first, while narrative padding reads
as substance. The `<…>` notes below say what a section is for, so you can tell
which of its clauses is the second copy.

---

You are implementing Linear issue `<CWF-NNN>` in an isolated git worktree, end to
end: implement, review, then STOP before pushing.

WORKTREE (your only working directory): `<abs path>`
Branch (already checked out, based on latest origin/main `<sha>`): `<branch>`
GRAPH ROOT (pass as `repo_root` on every graph call): `<abs path to the main clone>`
The brief never re-spells the worktree path: one re-spelling in a shipped brief
dropped the ticket segment, and `cwfgw4k-wt/ui/…` reads plausibly enough that
only a clause beside it kept the fence unambiguous. A path written
`<worktree>/…` below resolves against the WORKTREE line.
Your shell and every subagent you spawn start in the session's directory, not
here, and reading the wrong tree raises no error — give every command and every
forked pass an absolute path under the worktree.
SCRATCH (yours alone; `mkdir -p` it first): `<session scratchpad>/cwf-<num>/`
Every log and note goes there, written with `>|`. The scratchpad above it is
shared: two agents independently chose `gate.log` in it, `zsh`'s `noclobber`
refused the second redirect with a non-zero exit, and the `tail` after it
returned the other agent's failing build. `>|` only stops your second run being
refused over your first. The same shell mangles `"$B:ui/…"` without a word:
inside double quotes `:u` is its uppercase modifier, as `:h`, `:t`, `:r` and
`:e` are modifiers too, so `git show` receives no revision you wrote and its
empty output reads as a file with no changes. Brace the name: `"${B}:path"`.
UI deps installed. You are fully authorized to create/edit/delete files in this
worktree and run git add/commit without asking. `<Sibling agents work
concurrently in other worktrees — never touch anything outside yours. | No
sibling agents are running — you have the tree to yourself.>`

=== ISSUE `<CWF-NNN>` (`<priority>`, `<label>`) ===

`<Paste the ticket title and body, correct what has gone stale, and mark each
correction as yours: an agent that cannot tell your edit from the original goes
back to Linear and reads the premise you disproved. If prose already in the repo
asserts the ticket's answer — a KDoc, a comment, a doc paragraph — name the file
and quote the sentence: that turns the task from discovering a mechanism into
testing a claim, and an assertion with nothing behind it is itself a finding.
Then:>`

NOTE: line numbers predate `<the merged work since the ticket was written>` —
re-locate everything before editing.

=== CRITICAL: `<PRIOR FIX>` LIVES IN THIS CODE — DO NOT REGRESS IT ===

`<Name each recently-fixed bug in the code being changed, what mechanism carries
the fix, and why it is subtle. Then:>`

Guard tests that MUST still pass, unmodified: `<name them exactly>`. If you must
touch one, say which and why.

=== SCOPE ===

Files you may modify:
- `<worktree>/<path>` (+ its test)

You may READ anything. Do NOT modify: `<paths, each with who owns it — "a sibling
agent is mid-refactor there", "an open PR restructures those", "a queued ticket
owns it">`.

A file on neither list is outside the fence. If the work would reach code
outside it, STOP and report rather than reaching in — but a comment your own
diff falsified is in fence wherever it lives, and you fix it there.

The do-NOT-modify list is the exception to that carve-out, so there you report
the rot and leave it. Nothing else loosens for prose: outside the fence you may
change a comment and not the code under it.

Code outside the fence loosens in one case, and it is not one you choose. Adding
a variant to a sealed hierarchy means editing every exhaustive `when` over it or
nothing builds, and a repo MUST — a seed fixture — binds the same way. Stopping there leaves the ticket unfinished over
a mechanical edit, so make it, and report the crossing under its own heading in
your report and the PR body, naming what forced it and every file you reached. A
path on the do-NOT-modify list is never this case: a sibling agent or an open PR
holds it, and ownership is not a compiler.

`<Name the files this change will drag in — a shared id registry, a barrel
export, a seed fixture — or the agent meets a repo MUST it cannot honor inside
the fence, and has to choose between them alone.>`

`<For a move or a rename, "import lines only" is not a fence — config files
address source by string, and no compiler checks those strings. `ui/vite.config.ts`
(the coverage `exclude` list) and `build.gradle.kts` (Kover's excluded packages)
are where this lands; a rename that repoints every import and leaves those
behind compiles and tests green while the exclusion points at nothing. Admit
them, or the fence is drawn against a check that does not exist.>`

`<A repository interface drags in its fake: one agent could only report a defect
it had already diagnosed. Name it, in or out. `detekt`'s `LongMethod` counts
test sources — 50 lines, per method, excluding nothing — so one more case in a
long `test("...") { }` block turns added coverage into a red gate the agent
cannot read as a fence question. Say the block is near it, or admit a sibling
spec file to split into.>`

`<Comments the diff will falsify, named ahead: write "at least these". A list
that reads as a census answers the question the carve-out above exists to make
the agent ask, and the one it finds for itself gets reported as out of fence.>`

=== `<DESIGN DECISION | TDD REQUIREMENT>` ===

`<Delete this section whole on a ticket with no test surface and no fork in the
road — carried onto a prose change it teaches the agent that this brief is
boilerplate, and the agent reads the rest of it that way.>`

`<For a bug:>` Write the failing test FIRST — `<the specific assertion>`. Confirm
it fails, then fix. Report the failure output you observed.

`<For a design choice:>` The ticket names `<two approaches>`. They are not
equivalent: `<the tradeoff>`. Pick one, implement it, and state the rejected
alternative with reasoning in your report and the PR body. Do not do both.

`<For anything whose test could pass vacuously:>` Commit the work first, then
mutation-check it — break the behavior, confirm the test goes red, revert, and
report the mutation and the failure. Reverting out of a dirty tree discards the
uncommitted feature edit along with the mutation, and the green run after it is
green against `main`. Name the call site the test exercises as what to break,
never a helper the test's own fixture also runs through: the fixture then goes
red while seeding, which it would do with the behaviour under test deleted.
The mutation must compile and lint: a `:detekt` red stops the build before
`:test` and leaves the last green run's XML in place. Quote the failing test's
name from the console, since the XML misattributes it; take only counts from the XML.

`<For a break the evidence needs but the diff must not carry:>` Put it in a NEW
untracked file — a spec under `<worktree>/src/test` is fine. Never edit a
tracked one: a file that did not exist before you wrote it cannot collide.

=== REPO CONVENTIONS (binding — read these first) ===

Read `<worktree>/.claude/CLAUDE.md` and `<ui/CLAUDE.md | src/CLAUDE.md>` and
FOLLOW THEM. In full, and once — the section below bounds what you read, and
these are its exception. You cannot tell which rule binds before you have read
it; and re-opening one later to re-check a rule buys a second copy of what you
already carry.

`<A highlights list here is a second copy of a file the line above sends the
agent to read in full.>`

Explore this ticket's code with the `mcp__code-review-graph__*` tools
(`semantic_search_nodes_tool`, `query_graph_tool` with `callers_of`/
`imports_of`/`tests_for`, `get_impact_radius_tool`) before falling back to
Grep/Read — see root `<worktree>/CLAUDE.md` → MCP Tools: code-review-graph.
These tools are deferred by default; if a call fails because its schema
isn't loaded, `ToolSearch` for it once (e.g.
`select:mcp__code-review-graph__semantic_search_nodes_tool`) rather than
falling back to Grep. This matters more here than in a single-agent session:
every concurrent sibling re-explores from zero context, so a Grep-first habit
multiplies across the wave instead of costing once.

Pass GRAPH ROOT as `repo_root` on every one of those calls. Omitted, it
auto-detects from your directory and resolves to your worktree, where the hooks
have built a graph of only the files you have touched. That graph answers
rather than erroring; it just answers about your own diff.

Check it before you trust it: `list_graph_stats_tool` with that same
`repo_root`, `built_at_sha` against this branch's base and `head_matches_build`.
On a mismatch, report it and fall back to Grep/Read — with `repo_root` explicit,
a mismatch finally means what it says, that the clone's graph is behind. Do NOT
rebuild it — by either route. `code-review-graph update` starts in the main
clone, so it rebuilds the db every sibling in this wave is reading, from your
tree; `build_or_update_graph_tool` reaches that same db on purpose, because
GRAPH ROOT is the `repo_root` you have been told to pass on every call. Report
the mismatch and let the coordinator rebuild. The one exception is the
`.githooks/pre-commit` hook — see step 2.

`detect_changes_tool` runs its `git diff` in `repo_root`, so with GRAPH ROOT it
reports the clone's history, not your branch, and raises no error. Take your own
diff from `git diff <base sha>..HEAD`; keep the graph for callers, imports and tests.

`<Where the graph does not reach — a diff confined to `.claude/`, docs, or
migrations — say so in place of those paragraphs: the `list_graph_stats_tool`
currency check stands.>`

`<Add a convention that postdates the files above: landed after this branch's
base, or decided and not yet written down anywhere.>`

=== WHAT YOU READ, YOU CARRY ===

A tool result is not paid for once. It stays in your context and is re-read on
every turn that follows, so what you pull in early is charged for the rest of
the run — and enough of it ends the run early: a full context compacts, which
costs a stall and hands you a summary in place of the detail you were working
from. Outside the convention files above, read narrowly and on purpose.

- Search for locations, not contents. `grep -l` or `grep -c` first, `-n` only
  once you know which file, and `| head -30` even then. A repo-wide grep that
  returns matching lines pays for every file it touched, including the ones you
  were never going to open.
- Size a file before reading it whole. Over ~600 lines take the range
  (`sed -n '<a>,<b>p'`) or `get_review_context_tool`, which is what that tool is
  for. Dense Kotlin runs about twenty tokens a line, so opening a long file to
  reach one function spends most of a compaction's headroom on code you did not
  need.
- `--stat` before the diff itself. Which files moved is usually the whole
  question, and a bare `git diff` answers it with the contents of everything
  else that moved alongside. The diffs the steps below name are already scoped
  this way — none of them is a bare `git diff`.

None of this licenses guessing. Where you would otherwise guess, read it — one
wrong edit costs more turns than any read. `<Name the file this ticket cannot be
done without reading whole.>`

Issue together every call whose arguments you already hold — the test is whether
the next call's input depends on this one's output. A turn bills for everything
you carry, so splitting independent calls buys nothing and pays a second full
context re-read. This does not license reading more: an unneeded read costs the
same turn as a needed one and then rides in your context for the rest of the run.

=== STEPS ===

1. Implement in reviewable commits. The gate lands on the branch tip, so a
   commit that is red on its own is fine and needs no history rewrite — but
   every commit message must be a real subject, never a placeholder. Verify:
   - UI: `cd <worktree>/ui && npm run typecheck && npm test` — all clean,
     whole suite.
   - Backend, in this order:

     ```sh
     cd <worktree> && ./gradlew ktlintFormat -q
     ./gradlew test \
       --tests 'com.cwfgw.<package>.<SpecA>' \
       --tests 'com.cwfgw.<package>.<SpecB>' --rerun-tasks
     ```

     - One invocation, every spec the change touched: repeat `--tests` rather
       than running the task once per spec. Gradle applies them as a union.
       Keep it a separate invocation from `ktlintFormat` — with both on one
       command line, `test`'s dependency on `ktlintCheck` can run the check
       before the format and fail on what the format was about to fix.
     - A spec that sweeps a catalog or the schema fails on a diff that never
       opened its package, so name it from the *shape* of the change rather than
       from the files touched. None is known here yet; a spec that filters an
       `X.entries` or reads the schema out of `pg_class` belongs on this list
       the day one is written.
     - A *wrong* fully-qualified name does not fail at all — it silently runs
       fewer specs. `--tests` that matches nothing is only fatal when nothing
       else matched either, so one bad package beside three good ones reports
       `BUILD SUCCESSFUL` over a narrower run than you asked for, with no
       signal anywhere in the output. Read the test count out of
       `build/test-results/test/TEST-*.xml` and check it against the specs you
       meant to run.
     - `:test` and `:detekt` cannot share one invocation once `--tests` is
       passed: Gradle dies with `Unknown command-line option '--tests'` while
       configuring `:detekt`, before either task runs. Two commands.
     - To run *one test* rather than one spec, filter through Kotest instead:
       `-Dkotest.filter.tests='*<any part of the test name>*'`, narrowed with
       `-Dkotest.filter.specs='*<SpecName>*'`. Neither substitutes for `--tests`
       on a whole spec — `--tests` cannot reach inside one at all, because
       Kotest exposes no JUnit method descriptors.
     - One pattern per property, per run. A comma-separated list of patterns
       matches nothing, runs no test body, and still reports `BUILD SUCCESSFUL`.
       Several whole specs go through the repeated `--tests` above, which fails
       outright when none of its names match.
     - The filter's tell is the per-spec `TEST-<FQN>.xml` under
       `build/test-results/test/`, and the `<testsuite … tests="N" skipped="M">`
       line inside it: bodies ran only for the difference. A test filter that
       matched nothing still writes the file, with `skipped` equal to `tests`,
       and a spec filter that matched nothing writes no file at all. Both report
       `BUILD SUCCESSFUL`. The `TEST-Gradle-Test-Run-*.xml` beside it answers
       neither question — it lists the specs the filter *excluded*, so its large
       `tests=` count and its `skipped="0"` read the same whether the filter
       matched one spec or none.
     - Without `--rerun-tasks` an unchanged filter answers `> Task :test
       UP-TO-DATE`, and the `BUILD SUCCESSFUL` is the previous run's result. A
       document a spec guards is not a `:test` input, so a docs-only edit is the
       case where this bites.
     - Read the verdict out of the full output. A pipe (`| tail`) exits with
       the status of `tail`, so a `BUILD FAILED` arrives as exit `0`.
     - Add the lint that reads the files you changed and nothing wider:
       `ktlintKotlinScriptCheck` for a `.kts` diff, `ktlintCheck` and `detekt`
       for a `.kt` one. Without it a diff that is only `build.gradle.kts` has no
       authorized command that reads it at all.
2. Commit subjects `<CWF-NNN>: <concise imperative summary>`, short bodies, each
   ending with the trailer:
   `Co-authored-by: <model> <noreply@anthropic.com>`

   `.githooks/pre-commit` runs `code-review-graph update` on every one of these,
   unasked. That run resolves to your worktree and writes the private db no call
   carrying GRAPH ROOT reads — it does not touch the clone's graph your siblings
   are reading, which is the thing worth being sure of and has been checked.
   Let it fire, or pass `--no-verify`; either is fine. Do not report it, and do
   not switch to `--no-verify` on a theory about what it rebuilt.
3. RUN THE REVIEW GATE.

   `<Say nothing anywhere in this brief about which tier the diff deserves, or
   how small it is. `cwf-review` picks the tier from `.claude/review-contract.md`
   by what the diff touches, and a steer from you only ever reads as permission
   to go lower, which is the direction that ships the defect.>`

   The Skill tool resolves against a registry built once from the session's
   working directory, so a subagent in a worktree is handed the main clone's
   skills. Establish which copy you would be running before you run it:

   ```sh
   cd <worktree>
   clone=$(git rev-parse --path-format=absolute --git-common-dir)/..
   (cd "$clone" && git diff --stat <base sha> -- .claude/skills/cwf-review .claude/review-contract.md)
   git diff --stat <base sha> -- .claude/skills/cwf-review .claude/review-contract.md
   ```

   Silence from both means the copy the Skill tool would hand you is the one
   your branch carries: invoke it with skill `cwf-review`, args `branch`.
   Otherwise read `.claude/skills/cwf-review/SKILL.md` and
   `.claude/review-contract.md` by path and execute all phases from what you
   read — a copy that is not the right one is available, runs, and reports a
   clean gate on a procedure nobody is reviewing.

   - **The worktree line spoke**, alone or alongside the other: read them under
     `<worktree>`. Your branch edits the review procedure, so its copy is the
     one under review.
   - **Only the clone line spoke**: read them under `$clone`. Falling back to
     your branch's older copy would spend exactly the guard `main` just added.
     Do not invoke the skill instead — its registry was built once, at session
     start, so what it hands you predates the movement.

   Comparing the two trees to each other cannot separate those two cases, and
   reported three worktrees' untouched copies as discrepancies the day a skill
   merged mid-wave. Asking only the clone is the opposite error: it goes silent
   on the branch that edits the review procedure, which is the case with the
   most to lose.

   A pass resolves its own diff whether it runs in your context or in a fork,
   so give it the tree either way: `cd <worktree>` before every command you run
   yourself, and `<worktree>` plus the base SHA in the prompt of every pass you
   spawn. Before reading a finding, clear the resolved-target preamble it opens
   with against `cd <worktree> && git rev-parse HEAD` and
   `cd <worktree> && git diff --name-only <base sha>...HEAD`; on a mismatch the
   contract's **The tree under review** governs — one re-run with the path
   stated, then stop and report the gate as blocked.

   Start every Agent `description` you write, review passes included, with
   `<CWF-NNN> · `: the coordinator tells a wave's agents apart by it.

   Every pass you spawn returns through the harness's completion notification.
   Wait for it, and end your turn to do so. **MUST NOT** stand up a monitor,
   poll, or watchdog of your own to judge whether a pass is still alive: a
   stalled pass and a slow one are silent in the same way, so a signal built
   from outside reports the one as the other. Do not edit ahead of a verdict you
   expect to receive. A pass that genuinely never returns is where you stop and
   report the gate as blocked — running without that pass is not the fallback.
4. Fix every DEFECT your change caused; report the ones it merely exposed. A
   false clause in a sentence your diff rewrote is caused, however old the
   clause: the rewrite carried it forward. `cd <worktree> && git diff <base sha>
   -- <file>` settles which is which. Justify or fix every NEEDS JUSTIFICATION.
   Re-verify. Commit the fixes as `cwf-review` → **Run it before the first
   push** directs.
5. If you committed anything, write your intended PR body to
   `<worktree>/PR_BODY.md` ending with the Claude Code trailer line. Leave it
   UNTRACKED. Cite prior art by PR number (`#14`), not by ticket: Linear moves
   every `CWF-NNN` the body names, including issues already Done. A finding that refutes the ticket's premise still opens a PR when
   it landed tests. Having committed nothing, report the finding and write no
   body. Its Verification section names two runs and keeps them apart:

   ```md
   ## Verification

   Agent iteration: `<the exact command, run in the worktree>` — `<the result,
   and the task lines you read bare>`.

   Canonical gate: `./gradlew check --rerun-tasks` runs at the coordinator on
   this branch, after rebasing onto `main` and immediately before the push, its
   verdict and its bare `:test` / `:detekt` lines read from inside the log.
   Agents do not run `check`: concurrent Gradle builds across worktrees crash
   test-worker JVMs and produce a false red.
   ```

   Claim nothing about the gate's result — you will not have seen it. Omitting
   verification entirely is the other way to fail this.
6. DO NOT run `./gradlew check`. Not once, not "just to be sure" before
   reporting. DO NOT push. DO NOT open a PR.

=== REPORT BACK ===

`<List the specific evidence this ticket needs. Always include:>`
- the failing test output before the fix
- `<per-ticket: the decision made and the alternative rejected>`
- explicit confirmation the named guard tests pass unmodified
- anything you deliberately left undone, and why
- the step-3 provenance output, and which copy of `cwf-review` you executed
- the resolved-target preamble each pass returned, verbatim, and the
  `cd <worktree> && git diff --name-only <base sha>...HEAD` output you cleared it
  against
- cwf-review triage counts and what you fixed
- the step-1 verification result; commit SHAs; intended PR TITLE on a
  clearly-labeled line
- `PR_BODY.md` as a deliverable, not a claim: paste its first heading and the
  `ls -l <worktree>/PR_BODY.md` line that shows it exists and its size. It is
  gitignored, so `git status` is silent about it either way. A report
  that merely says it was written is the shape that has arrived with no file
  behind it, and the coordinator then reconstructs the body by hand. Having
  committed nothing, say so and write none.
- which parts of this brief you did not use, and what you needed that was not
  in it
