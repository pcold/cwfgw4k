---
name: cwf-review
description: Repo-specific code review gate for cwfgw4k (Kotlin/Ktor backend + React/TypeScript UI). Performs the pre-push gate defined in .claude/CLAUDE.md "Working with AI Assistants" — diff recap, test coverage review, and style audit against MUST/MUST NOT/SHOULD rules in root .claude/CLAUDE.md, src/CLAUDE.md, and ui/CLAUDE.md. Output is triaged into defects (MUST), needs-justification (SHOULD), and nits. Fixes MUST defects after review.
---

# cwf-review

A repo-specific code review for cwfgw4k that mirrors the pre-PR gate
described in the "Working with AI Assistants" section of the root
.claude/CLAUDE.md. The default scope is **uncommitted changes plus all
commits on the current branch that aren't yet on the PR base** — the
full state a reviewer would see when the PR is opened.

## When to invoke

- The user types `/cwf-review` (or asks "review this branch", "do the
  style audit", "is this ready to push", "is this ready to PR").
- Before any AI-proposed commit on this repo, regardless of whether the
  user typed the slash command — the root CLAUDE.md requires the gate
  and this skill is the canonical way to run it.

## Run it before the first push, not after

A finding that arrives after the push is a round-trip: a fix commit, a CI
re-run, and the reviewer's second visit. The same finding before it is just part
of the work.

So the order is: implement → review → fix → **then** commit and open. Not
implement → commit → open → review → fix.

The early run does not change how the fix lands, which is a new commit in every
case: the root .claude/CLAUDE.md Iteration rule forbids `--amend` on a branch
headed for a PR. What it buys is a first reviewer who reads a branch with the
finding already fixed.

## Inputs

- `args` (optional): scope override. Accepts `staged`, `working`,
  `branch`, `pr <number>`. Default is `branch` (working tree + commits
  on this branch that diverged from its base).

## Procedure

**First pick the tier** — see **Scale the gate to the change** in
[`.claude/review-contract.md`](../../review-contract.md). It decides from what the
diff touches whether this is a checklist pass (Tier 0), the standard one
(Tier 1), or the adversarial one that runs `/code-review` before the push
(Tier 2). Pick it before Phase 1 so the recap says which tier and why.

Then run the phases in order. Within a tier, do **not** skip phases on the
assumption that a diff is small — the audit catches the patterns most often
missed, which are typically subtle and live in small diffs. Tier 0 drops a pass
on the contract's stated grounds only, never because a diff looks harmless.

The style audit (Phase 4) applies the shared
[`.claude/review-contract.md`](../../review-contract.md) — the same contract the
CI `claude-review.yml` action runs, so a finding either reviewer can raise, the
other can too.

The Skill tool resolves against a registry built once from the session's working
directory and prints the directory it loaded from, so an agent in a worktree is
handed the main clone's copy of this file. A difference between that copy and the
branch's is a version difference rather than a sign the review found the wrong
tree: running the branch's copy instead would have it audit itself against
whatever procedure it happened to freeze.

The exception is a branch that edits `.claude/skills/cwf-review` or
`.claude/review-contract.md`, where the gate cannot exercise the change. Read the
branch's copies by path and run every phase from those. Name the copy you ran in
the Phase 2 recap either way.

### Phase 1 — Gather the diff

Open with `cd <absolute path to the tree under review>` and run every command
here from there. A bare run resolves against the session's working directory,
which for an agent working in a worktree is the main clone, not its branch.
`EnterWorktree` only moves a session that is already inside a worktree, so
nothing this skill can run recovers the intended tree once the invocation omits
it.

Run these in parallel:

- `git status` (no `-uall`)
- `git diff --stat <base>` and `git diff <base>` where `<base>` is:
  - `HEAD` for `staged`/`working` scope
  - `origin/main...HEAD` (or `main...HEAD` if no origin) for `branch`
    scope — i.e., the diff the open PR (or the PR about to be opened)
    would carry
  - The PR base SHA for `pr <number>` scope (use `gh pr view <n> --json baseRefOid`)
- `git log <base>..HEAD --oneline` for branch/PR scope to see commit
  decomposition.
- `git rev-parse HEAD`, the preamble's path list, and the base SHA itself
  — `git merge-base origin/main HEAD` for branch scope, `git rev-parse HEAD` for
  `staged`/`working`, the `baseRefOid` above for `pr <number>`. A range is not a
  SHA, so the branch-scope `<base>` cannot fill the preamble on its own.
  Gather the paths with the command the spawner clears them against:
  `git diff --name-only <base sha>...HEAD` for `branch` and `pr` scope, and
  `git diff --name-only HEAD` for `staged`/`working`, whose subject is the
  uncommitted work itself. Not the one-argument `git diff --name-only <base>`:
  it compares the base against the *working tree*, so on a dirty branch the
  preamble carries files the spawner's committed-only check cannot see, and the
  gate rejects a review that did happen.

If the working tree is dirty and scope is `branch`, gather **both** the
committed diff and the working-tree diff so the audit covers everything
the PR would include after the next push. The preamble's paths stay the
committed ones, so uncommitted files belong in the recap below it — listing them
in the preamble is what would fail the spawner's check.

**An empty `branch` or `pr` diff aborts the review.** If the range resolves to no
changed files and the working tree adds none, report the toplevel that
`git rev-parse --show-toplevel` actually resolved and stop — no Phase 2, no
verdict. The contract's **The tree under review** catches a mis-resolution at
the spawner, which is nobody when a
human invokes this skill directly; an empty diff is the one signature this skill
can catch unaided, and it is the signature of a bare run landing in the main clone
at `origin/main`. Aborting rather than reporting keeps the two readings apart: a
branch with genuinely nothing on it and a review that never found the branch
otherwise print the same `CLEAN`.

The `mcp__code-review-graph__*` tools are deferred by default — if a call to
one fails because its schema isn't loaded, run `ToolSearch` once, up front,
rather than discovering this mid-phase:

```
select:mcp__code-review-graph__get_minimal_context_tool,mcp__code-review-graph__detect_changes_tool,mcp__code-review-graph__get_review_context_tool,mcp__code-review-graph__query_graph_tool
```

Once `<base>` and the changed-file list are resolved, call
`get_minimal_context_tool(task="review <scope>", base=<base sha>)` for a
~100-token risk snapshot, then `detect_changes_tool(base=<base sha>,
changed_files=<paths from above>, detail_level="minimal")` for the risk-scored,
priority-ordered list Phases 3 and 4 walk. Pass `base`/`changed_files`
explicitly — both tools default `base` to `HEAD~1`, which is wrong for every
scope here except a single-commit `staged` diff. If either call errors (the
graph is uninitialized — a fresh clone before the first build, or genuinely
mid-rebuild), fall back to the diff gathered above and note the fallback in the
recap.

### Phase 2 — Resolved target and diff recap

Open the report with the resolved-target preamble from
[`.claude/review-contract.md`](../../review-contract.md) → **The tree under
review**, filled from Phase 1. It comes before the recap because the agent that
spawned this pass has to clear it before reading anything below.

Produce a 3–8 line recap of what changed: domains touched, the shape of
the change (feature / fix / refactor / migration / test-only), the commit
decomposition, and which copy of this skill ran. This is the entry point
for the rest of the review — skipping it makes the triage harder to read.

Flag immediately if:

- Multiple unrelated logical changes are bundled (root CLAUDE.md Change
  Scope rules).
- A CLAUDE.md update is mixed with the work that motivated it (must be
  a separate commit).
- A placeholder commit message is left standing (e.g. `TEMP review
  fixes (to be folded)`). A commit that is red in isolation is not a
  defect — the gate lands on the branch tip, not on each commit.
- New top-level dependencies (Gradle, version catalog entries, npm
  packages) appear without being called out.

### Phase 3 — Test coverage review

For each non-trivial production change in the diff, state explicitly:

- **Covered.** Tests exist that exercise the new behavior.
- **Gap worth filling.** Production change has no corresponding test;
  recommend the specific test to add and the layer (repo / service /
  route / component).
- **Gap deliberately skipped.** State why it's not worth testing
  (mechanical rename, pure type change, generated code).

For each production symbol `detect_changes_tool` flagged in Phase 1, confirm
coverage with `query_graph_tool(pattern="tests_for", target=<symbol>,
detail_level="minimal")` instead of manually searching for the test file — read
what it actually returns rather than assuming coverage exists because a
similarly-named spec file is nearby.

Neither a `tests_for` zero nor an empty grep proves a gap. `git grep` the symbol
under `src/test`, and for a pinned string grep the file being changed instead:
specs assemble what they pin from constants, so the literal never appears.

Use the layered testing strategy described under "What to review" in
[`.claude/review-contract.md`](../../review-contract.md) (repo / service /
route specs and the UI `renderWithProviders` boundary).

### Phase 4 — Style audit (the gate)

Pull the touched-symbol source with `get_review_context_tool(base=<base sha>,
changed_files=<paths>, detail_level="minimal")` rather than `Read`-ing whole
files up front — it's the same snippets scoped to what changed, at a fraction
of the tokens. Fall back to `Read` on the surrounding file only when a finding
needs context the snippet doesn't carry (see Notes below).

Walk the diff and produce findings. Every finding has:

1. **Severity** — `DEFECT`, `NEEDS JUSTIFICATION`, or `NIT`, per the severity
   definitions in [`.claude/review-contract.md`](../../review-contract.md).
2. **Location** — file path and line number, written plain (no
   backticks — the user's terminal renders inline code in a hard-to-read
   light blue).
3. **Rule** — the CLAUDE.md section the finding maps to (e.g. "src/CLAUDE.md
   → Nullability → no !! in production code").
4. **What's wrong** — one sentence.
5. **Fix** — concrete, applicable suggestion.

#### The checklist

Apply the full **Patterns to check** list in
[`.claude/review-contract.md`](../../review-contract.md), walking every item
against the diff. It is the shared scan that this skill and the CI
`claude-review.yml` action both run — backend (Kotlin), frontend
(TypeScript/React), and cross-cutting rules — and it also carries the
"what to review" scope (correctness, coverage, change scope) and the
no-nitpick / read-before-firing guards.

#### Comment and doc claims

The one question review asks of a comment or doc claim is whether it is true:
one the code contradicts, or one the diff made stale, is a correctness `DEFECT`.
Read every comment and doc block the diff adds against the code it describes,
then look for the claims it falsified elsewhere. Those are the ones a diff read
misses, because none of them are added lines: a KDoc still describing the shape
a type had before the change, a doc asserting something no longer true, a
comment above a function whose path the same commit rewrote.

**Outward first.** Build the list of names the diff *changes the truth about*:

- symbols renamed, removed, or given a different contract (`git diff <base>...HEAD`
  for removed `fun`/`val`/`class`/`data class` declarations)
- database tables and columns a migration creates, drops, or renames
- files deleted or moved

Then grep the **whole repo** — not the diff — for comments naming each one:

```sh
git grep -nE '(//|\*|--|#).*\b<name>\b' -- '*.kt' '*.kts' '*.ts' '*.tsx' '*.sql' '*.md'
```

The grep reaches past the diff because `docs/API_SPEC.md` documents every
endpoint and its wire shape and `README.md` states the league's rules, and a
change to a route, a DTO or a payout rule is exactly what falsifies them.

Every hit is a `DEFECT` unless the comment is still true. A migration whose header
describes *its own era* is not rot — an early migration may say it created a
table a later one dropped, because a migration is a historical record and editing
one to match today's schema makes it lie about when things were true.

**Then inward.** Re-read whole, at branch state, every comment or doc block the
diff *edited* — with the removed text gone rather than remembered. A block the
diff only deleted from adds no `+` line, so a diff read never shows it, and the
rot a deletion leaves names nothing to grep for: it leans on its neighbours.
The enumeration that loses a member, the opener summarising points that no
longer all follow, and the `@param` describing behaviour the same edit removed
all fail this way.

### Phase 5 — Triage summary

End with a compact summary block in this exact shape:

```
Defects (MUST): <count>
Needs justification (SHOULD): <count>
Nits: <count>
False or stale comment claims (Phase 4): <count>
Tier: <0 | 1 | 2> (<what selected it>)

Verdict: <BLOCK | PROCEED WITH JUSTIFICATIONS | CLEAN>
```

Which verdict to write is the contract's:
[`.claude/review-contract.md`](../../review-contract.md) → **Verdict**. Read it
when writing the verdict rather than from memory.

### Phase 5b — Tier 2 only: the adversarial pass, before the push

For a Tier 2 diff, run `/code-review` on the branch **now** — after the phases
above and before the first push. Spawn it with the absolute worktree path and
the base SHA written into the prompt, and name the branch as its target: a
worktree shares the main clone's object store and refs, so a pass that resolved
the wrong toplevel still reads the right commits from a branch target.

`/code-review` ships with the CLI rather than with this repo, so it does not
return the contract's preamble, and a pass that mis-resolved is silent in exactly
the way a compliant one would be. Clear it against its findings instead: every
file it names must appear in `cd <worktree> && git diff --name-only <base sha>...HEAD`,
and one that does not means the pass read another tree and none of its output
counts.

Treat its findings exactly as this skill's own: fix every DEFECT, and justify or
fix every NEEDS JUSTIFICATION.

It reads the whole repository rather than the diff, which is why it is the pass
that catches a defect the checklist reads past — a guard that compiles and is
wrong, an ordering that is unrecoverable, a constraint a sibling table has and
this one does not.

### Phase 6 — Fix defects only

Fix every MUST defect, then re-run Phase 4 on the touched lines to confirm
the defect is resolved and nothing new was introduced. When a human typed
`/cwf-review`, list the defects numbered and fix only the ones they pick.

Do **not** fix SHOULD violations or nits — justify each, or list it in
`PR_BODY.md` for the human to judge on the PR.

## Output format

Use plain text with section headers. Do not wrap file paths or symbol
names in backticks (terminal renders them in hard-to-read light blue).
Use forward references like `src/main/kotlin/.../TeamService.kt:42`
inline as plain text.

Example finding:

```
DEFECT  src/main/kotlin/com/cwfgw/teams/TeamService.kt:88
Rule    src/CLAUDE.md → Nullability → no !! in production code
Issue   team!!.seasonId accesses through a !! assertion
Fix     Replace with a null guard: val seasonId = team?.seasonId ?: return Result.Err(NotFound)
```

## Notes for the reviewer

- The audit is a **gate**, not a green light. Even a CLEAN verdict means
  the human still reads the diff, on the PR.
- The MUST/MUST NOT lists in src/CLAUDE.md and ui/CLAUDE.md are
  authoritative. The contract's checklist is representative, not
  exhaustive — if something looks off and isn't on it, still flag it and
  cite the closest rule.
- When a rule and existing code conflict, the rule wins: existing code
  is wrong and gets reworked, per the root CLAUDE.md preamble.
- If a finding requires reading more context than the diff shows, read
  the surrounding file before classifying — don't fire a defect on a
  guess.
- Check an "everything passes through X" claim by constructions, not callers:
  `comm` the files that build X's output type (`git grep -l 'Type('`) against
  X's callers; the files only in the first list bypass it.
