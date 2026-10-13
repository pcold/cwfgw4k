---
name: resolve-reviews
description: Resolve code-review feedback on an open cwfgw4k PR. Invoke when the user says "resolve reviews", "address the comments on PR N", "fix up PR N", "/resolve-reviews [N]", or otherwise points at a PR's review threads. Reads every feedback channel (inline review threads, top-level + bot comments, formal reviews), triages, checks out the PR branch (directly in a dedicated review checkout, or via a throwaway worktree when there's in-progress work to protect), fixes the actionable items, resolves merge conflicts when the base has advanced (merge, never rebase), verifies with ./gradlew check --rerun-tasks, pushes new commits to the PR branch — never amend, never force-push. The post-review counterpart to cwf-review (which is the pre-PR gate).
---

# resolve-reviews

Iterate on an **already-open** PR by resolving its review feedback. This is the
counterpart to [`cwf-review`](../cwf-review/SKILL.md): `cwf-review` is the
*pre-PR* gate (audit your branch before opening); `resolve-reviews` is the
*post-review* gate (address what reviewers asked for and push the fixes).

## When to invoke

- The user types `/resolve-reviews [N]` or says "resolve the reviews", "address
  the comments on PR N", "fix up the review feedback on N".
- A PR has review threads, a `request-changes`, or bot-review findings that
  need to be turned into commits.
- A PR has gone **unmergeable** — its base advanced and the branch now
  conflicts. Bringing it back to mergeable is part of resolving it.

It is **not** for opening PRs or starting new feature work — only for iterating
on feedback against a PR that already exists.

## Operating rules (these bind — from the repo root `.claude/CLAUDE.md`)

- **Iterate with NEW commits.** Never `git commit --amend`, never
  `git push --force` / `--force-with-lease` on a PR branch. Reviewers diff
  between visits; squash-merge consolidates at the end.
- **`./gradlew check --rerun-tasks` is the gate, and a round runs it once.**
  Every *push* must pass it, not just `./gradlew test`. It gates the branch tip
  you are pushing, not each commit behind it — so a round that merges its base,
  fixes the feedback and re-audits itself gates the tree those three produced
  together, once, rather than once per step. Read the verdict out of the log,
  and confirm `test` and `detekt` appear **bare for every module** — `:test` and `:detekt`, plus the
  `:<module>:` pair of every project `settings.gradle.kts` includes, with
  nothing after any of those names, not `UP-TO-DATE`, `FROM-CACHE`, `SKIPPED`
  or `NO-SOURCE`. Why each of those is load-bearing is
  root [`.claude/CLAUDE.md`](../../CLAUDE.md) → Verification. Two things about
  it are specific to a resolve cycle:
  - The loop's own targeted runs are what fill the cache. `:detekt` is never
    one of them, so it does not go `UP-TO-DATE` on its own; `:test` rejoins
    `FROM-CACHE` the moment one full `check` has run — which, on a second
    round, it has.
  - Forcing costs about half a minute against a skipped run's under a second,
    and it is what makes the gate a *second, independent* execution rather than
    a readback of the targeted runs it follows.
- **One logical change per commit.** Resolving the feedback is the logical
  change; don't bundle drive-by refactors. A mechanical rename a comment asks
  for can ride along; anything larger is a separate commit.
- **Commit message convention:** `CWF-<num>: address review — <what changed>`,
  ending with the `Co-Authored-By: <active model> <noreply@anthropic.com>`
  trailer your harness specifies — don't hardcode a model qualifier, it drifts.
- **You're iterating, not opening** — the PR already exists. If a requested fix
  grows into something that warrants its own PR, say so and stop rather than
  scope-creep this one.

## Workflow

### 1. Identify the PR

The user names it (`/resolve-reviews 119`) or you infer it from the branch. If
ambiguous, ask. Pull its state:

```sh
gh pr view <N> --json number,title,headRefName,baseRefName,state,mergeable,reviewDecision,statusCheckRollup
```

Note `mergeable` and `baseRefName`. If `mergeable` is `CONFLICTING` (or CI
flags a merge conflict), the branch's base advanced past it and resolving that
conflict against `baseRefName` is part of this job — see step 4.

### 2. Gather ALL feedback — don't miss a channel

There are three distinct channels; read every one before changing anything.
`gh` resolves `{owner}/{repo}` from the current clone's remote, so these work
from any checkout:

```sh
# Inline review-thread comments (anchored to lines):
gh api repos/{owner}/{repo}/pulls/<N>/comments \
  --jq '.[] | "[\(.user.login)] \(.path):\(.line // .original_line): \(.body)"'

# Top-level PR comments + any bot-review summary:
gh pr view <N> --json comments --jq '.comments[] | "[\(.author.login)]: \(.body)"'

# Formal reviews (approve / request-changes bodies):
gh pr view <N> --json reviews --jq '.reviews[] | "[\(.author.login)/\(.state)]: \(.body)"'
```

Triage each item into **must-fix** (defects, MUST-rule violations — a false or
stale comment claim among them — and requested changes), **should-address** (needs-justification — fix it or write the
justification), and **nit** (judgment call; fix if cheap, else note why
skipped). Surface the triage to the user before editing so they can veto.

### 3. Get onto the PR branch

**Default — a clean / dedicated review checkout:** when the current clone has
no in-progress work to protect (a clean tree, e.g. a checkout kept solely for
review work), bring main current and check out the PR branch directly:

```sh
git fetch origin
git checkout main && git pull --ff-only
git fetch origin <headRefName> && git checkout <headRefName>
```

**Fallback — a clone with uncommitted or in-progress work you must not
disturb:** branch the PR into a sibling worktree instead (path derived, not
hardcoded) and do the rest of the work there:

```sh
root="$(git rev-parse --show-toplevel)"
git fetch origin <headRefName>
git worktree add "${root}-pr<N>" origin/<headRefName>   # --track set automatically
cd "${root}-pr<N>"
```

When a review asks you to test a hard-to-reach branch (a not-found path, an
error variant), prefer constructing the bad state through the existing fixtures
and fake repositories in `src/test/kotlin/.../testing/` over adding new
affordances — e.g. seed an entity pointing at an id that was never inserted to
force a not-found.

### 4. Bring the branch current — resolve any merge conflicts

If step 1 showed `mergeable: CONFLICTING` (or CI reports a merge conflict), the
base advanced past the branch and you must reconcile them. **Merge the base in;
never rebase** — rebasing rewrites history and forces a `--force` push, which is
banned on PR branches here. [`parallel-tickets`](../parallel-tickets/SKILL.md)
Phase 5 rebases at the same step, because there the branch has not been pushed
and no reviewer is reading the history a rebase would rewrite. A merge commit on
the branch is fine; the squash-merge consolidates it away at the end. Keep this
its own commit, separate from the review fixes — one logical change each.

```sh
git fetch origin
git merge origin/<baseRefName>   # the lower PR's branch for a stack, else main
# ...resolve conflicts in the marked files...
git add <resolved-files> && git commit   # keep git's merge message; note what you reconciled
```

**No gate and no push here** — step 5 does both, for this commit and the fixes
together. You merge the base *in order to* fix on a current one, so the fixes
always follow, and the tree this step produces exists for minutes. Nothing
downstream ever reads it either: `allow_squash_merge` is the only merge mode
enabled, so `main` takes one commit per PR and this merge commit is consolidated
away. Gating and pushing here bought a second full run and a second CI cycle for
that.

Reverse this the first time a resolution has to stand on its own — a conflict
you reconcile and hand back without fixing anything after it. Then this commit
*is* the tip being pushed, and it gates like any other tip.

Resolve by understanding **both** sides, not by reflexively taking one. If a
conflict is semantically non-obvious — both sides changed the same logic, a
migration renumbering, a schema collision — **stop and flag it** rather than
guess; a wrong resolution is worse than an unresolved conflict and is the
author's call. For a stacked PR whose base is itself a moving open PR, see
Stacking awareness below.

### 5. Fix, re-audit, verify once, commit, push

```sh
# ...edits...
# ...re-audit and fix what it surfaces (below) — before the gate, not after...
./gradlew check --rerun-tasks    # the round's one gate, on the tip being pushed
git add <changed-files> && git commit   # CWF-<num>: address review — ...  + Co-Authored-By trailer
git push                         # to origin/<headRefName>; never --force
```

For a fast inner loop, run `./gradlew ktlintFormat -q` and then every spec the
fixes touched in one invocation — `./gradlew test
--tests 'com.cwfgw.<package>.<SpecA>' --tests
'com.cwfgw.<package>.<SpecB>' --rerun-tasks`. Keep the format its own
invocation — on one command line, `test`'s dependency on `ktlintCheck` can run
before the format. Without `--rerun-tasks` an unchanged filter answers
`> Task :test UP-TO-DATE` and the previous run's `BUILD SUCCESSFUL`. A pipe
(`| tail`) exits with the status of `tail`, so a `BUILD FAILED` arrives as
exit `0`.

**Re-audit your own fixes before the gate — don't skip this.** Resolving one
finding routinely introduces another (a `!!`, a missing test, a non-local
return). An un-audited fix is exactly what the CI `claude-review.yml` action
catches on the next push, restarting the review cycle. So run the `cwf-review`
style audit — Phase 4 against the shared
[`.claude/review-contract.md`](../../review-contract.md), the same contract CI
runs — over the diff your fixes introduced. Treat its verdict as the pre-PR
gate would: never push on an unresolved DEFECT. This is what keeps the local
and CI reviews from ping-ponging.

Run it **before** the gate rather than after. That is the order the pre-PR path
already uses, and it is what leaves the round with one gate instead of two: the
gate then runs on the tree the audit's own fixes produced, which is the tree
being pushed.

When an audit fix does land after the gate has run, what you re-run depends on
what it touched. A fix that changes code re-runs the full gate. A
comments-and-prose fix re-runs

```sh
./gradlew ktlintCheck detekt --rerun-tasks
```

instead, because that is the whole set of things a comment can break: detekt's
`MaxLineLength` and ktlint's own line rules. Root
[`.claude/CLAUDE.md`](../../CLAUDE.md) → Verification is explicit that
`ktlintFormat` corrects nothing detekt owns, `MaxLineLength` included, so an
over-long comment line survives the formatter and fails the gate. Nothing else
can: no test observes a comment, so re-running the Testcontainers suite to prove
one did not change behaviour spends its whole runtime on a question the compiler
has already answered.

Reverse that narrow scope the first time a prose-only fix breaks something
neither `ktlintCheck` nor `detekt` catches — a KDoc reference a build step
resolves would do it — and run the full gate there instead.

### 6. Clean up and report

If you used the worktree fallback, remove it (skip this on the direct-checkout
path — there's nothing to tear down):

```sh
cd "$root"
git worktree remove "${root}-pr<N>"
```

Report: which comments you resolved and how, which you deliberately left and
why, the new commit SHA, and that CI will re-run on the push. If a thread asked
a **question** rather than a change, draft a reply for the user to post (or post
it with `gh pr comment` / the review-reply API) — don't silently resolve it.

## Stacking awareness

PRs here may be **stacked** (each builds on the last). Operate on **one PR
branch at a time** and push only to that branch. If a fix on a lower PR changes
something an upstack PR depends on, **flag it** — rebasing the upstack is a
separate step the author drives, not something this skill does. Don't rebase or
retarget other branches from here.

When resolving a merge conflict (step 4) on a stacked PR, merge its **immediate
base** — `baseRefName`, the lower PR's branch — not `main`; merging `main`
into a mid-stack branch drags in changes that belong to the PRs beneath it. If
that base is itself an open PR still moving, note that the conflict may reopen
once the base lands, and leave the rebase/re-merge of the stack to the author.
