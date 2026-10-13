# Code Style Guide

Code hygiene is of the utmost importance. You should generally follow the
principles of clean code. This document uses **MUST**, **MUST NOT**, **SHOULD**,
**SHOULD NOT**, and **MAY** in the RFC 2119 sense. Deviations from **MUST** rules
are defects. Deviations from **SHOULD** rules require justification.

This file covers cross-cutting rules that apply to the whole repo. Stack-
specific guidance lives next to the code:

- **Backend (Kotlin/Ktor/jOOQ):** see [`src/CLAUDE.md`](../src/CLAUDE.md) —
  language rules, concurrency, Ktor/jOOQ/kotlinx.serialization/Kotest, Gradle
  tooling.
- **Frontend (React/TypeScript):** see [`ui/CLAUDE.md`](../ui/CLAUDE.md) —
  TypeScript rules, React rules, TanStack Query, Tailwind, Vitest.

Where existing code predates a rule and conflicts with it, treat the code as
wrong, not the rule — the code gets reworked.

---

## Guiding Principles

1. **Correctness through the type system.** Model domains so invalid states are
   unrepresentable. Reach for `sealed interface` hierarchies, non-nullable
   types, and precise data classes before runtime checks. What this requires of
   an error value in particular is in [`src/CLAUDE.md`](../src/CLAUDE.md) →
   Error Handling.

2. **Explicit over implicit.** Clear function signatures, named arguments,
   obvious control flow. No annotation-driven magic. No framework-injected
   behavior that can't be read top-to-bottom.

3. **Immutability by default.** `val` over `var`. Read-only collections over
   mutable ones. Copy via `copy()` instead of mutating.

4. **Idiomatic by language.** Use each language's native tools where they
   fit — Kotlin's nullable types, scope functions, and `when`; TypeScript's
   discriminated unions and type guards. Don't transliterate patterns from
   another language or bend the language into knots to fake a feature it
   genuinely lacks.

5. **No framework heroics.** Lightweight libraries composed explicitly, not
   batteries-included frameworks with annotation-driven wiring.

6. **Readable before clever.** Code is read more than written. A few more lines
   that are obvious beats one line that requires a comment.

7. **Always try to explain yourself in code.** Comments are the fallback, not
   the primary mechanism. Good names, good types, and good structure are the
   primary mechanism.

8. **Parallel things stay the same.** Sibling types, routes, specs and
   comments share one structure and idiom; where they differ, the difference
   carries its reason, or it reads as drift and gets converged.

---

## Hygiene Rules

- **SHOULD** enforce a reasonable line length. Most lines under 120 characters,
  stretching to 140 is acceptable.
- **SHOULD** break long functions into smaller intermediate functions. More
  than 50 lines is really long — keep them short.
- **MUST** use meaningful names.
- **MUST NOT** embed types in names. `val employeeList: List<Employee>` is
  wrong; it should be something more descriptive like `val activeRoster:
  List<Employee>`. Same for nullable types: `val managerOpt: Employee?` is
  wrong; `val manager: Employee?` is right.
- **MUST NOT** use Hungarian notation or any other type-encoding prefix/suffix.
- **MUST NOT** commit commented-out code. Remove it. Git remembers.
- **MUST** write tests for new code. Stack-specific test guidance lives in
  the per-stack files (`src/CLAUDE.md`, `ui/CLAUDE.md`).
- **SHOULD** use comments to:
  - Explain intent
  - Clarify non-obvious code
  - Warn of consequences
- **MUST NOT** use comments to:
  - Repeat what the code already says
  - Narrate obvious operations
  - Disable code "just in case"
- **MUST** apply the **cover-the-comment test** to every comment written:
  cover the comment and read the code alone. If you can still tell what it
  does, the comment says nothing and gets deleted. A comment earns its place
  only by carrying what the code *cannot* — a constraint, a rejected
  alternative, a consequence, an edge case that looks like a bug.

  ```kotlin
  // WRONG — the line beneath it already says this
  // The employer / group name (CWF-28) — one lookup off the employee's companyId.
  val company = companies.findById(employee.companyId)

  // RIGHT — the reason for a choice the reader would otherwise undo
  // A real plan year, not a dangling random id: gather reads the row to resolve
  // the effective date, so a fabricated id would silently resolve to null.
  val planYear = planYears.insert(...)
  ```

  Appending a because-clause to a restatement does not convert it into
  intent. "Pre-resolved into the context from the employee's company — the
  employer name is a company property" restates a `findById` call twice.

  The test is **per clause, never to the block as a whole**. A restatement is
  not laundered by an earning sentence beside it any more than by a
  because-clause attached to it — the block passes on its good half, which is
  not the half under test. Doc comments are where this lands: KDoc convention
  asks for a summary first, and a summary of a well-named declaration is a
  restatement by construction. Delete your opening sentence and read what is
  left. If it still stands, the opener was carrying nothing; if nothing is
  left, the declaration had no constraint to name and `@see` its family head
  is the whole comment.

  ```kotlin
  // WRONG — sentence one is the signature in prose
  /**
   * How many beneficiaries this form has printed rows for, or null when it
   * asks for none. Null rather than zero, because the two mean opposite
   * things: nothing may refuse a form over a roster it never asked about.
   */

  // RIGHT — what survives the delete
  /**
   * Null rather than zero, because the two mean opposite things: nothing may
   * refuse a form over a roster it never asked about.
   */
  ```
- **MUST** keep each comment within a **length budget**: the constraint a
  future editor would otherwise break, in 1–3 lines. An essay passes the
  cover-the-comment test clause by clause, since each rationale is something
  the code cannot say; the budget is what catches it.
  - Argumentation is deleted: name the rejected alternative an editor would
    reach for and the constraint that rules it out, not the case against it.
  - History is pointed to — a `docs/*.md` section, or for a decision record a
    ticket cited per the rule below — never retold.
  - The summary opener the test above calls a restatement by construction is
    deleted, never trimmed to fit.

  ```kotlin
  // WRONG — the constraint, then the case for it, then a second fact at length
  /**
   * [dbValue] is `signature_assertion.capture_path` verbatim, and the CHECK there lists exactly these
   * strings. There is no translation table between the two on purpose: a variant added here without a
   * migration widening that constraint fails at the database, on the insert, rather than at the
   * compiler.
   *
   * Which path a signature took is declared by the surface that captured it and is never recovered
   * from the image — a typed name painted onto the canvas and a drawn one are the same pixels.
   */

  // RIGHT — every constraint kept
  /**
   * [dbValue] must match the CHECK on `signature_assertion.capture_path`; a new variant needs a
   * migration widening it. Declared by the capturing surface — a typed name and a drawn one are the
   * same pixels, so the image can't tell.
   */
  ```

  The budget caps the *length* of a catalog entry's doc and of cross-module
  KDoc; it waives neither the catalog rule nor the KDoc rule below.
- **MUST NOT** annotate code with a ticket id (`CWF-NNN`) as a matter of
  course. Lineage already lives in `git blame`, the commit, and the PR;
  a ticket id in a comment is a second copy that rots and that reads as
  justification for a comment which would otherwise be deleted. Cite a
  ticket only when a reader needs the *decision record* to understand a
  non-obvious constraint — a rejected alternative, an ESPN quirk, a
  deliberate deferral — and then cite it once, at the type or function that
  owns the constraint, never on each line of a change.
- **MUST** size doc comments to what the declaration cannot say itself. A
  new entry in an existing catalog, enum, or field set matches the length of
  its siblings, within the budget above; if it needs materially more prose
  than the entry above it, that is a signal the *type* is unclear, not that
  the entry needs an essay.
- **MUST** give an entry in a closed catalog either a doc naming a constraint
  the declaration cannot — what gates it, what an absent value does, why this
  source and not the obvious other — or `@see` its family head, which is how
  an entry says its meaning lives there. Never a gloss restating the
  declaration, and never bare: a gap reads as an oversight, and no later
  reader can tell it from one.
- **MUST** provide KDoc (Kotlin) / TSDoc (TypeScript) on public interfaces
  that cross module boundaries.

---

## Naming

- **MUST** use `UpperCamelCase` for types; `lowerCamelCase` for functions,
  properties, parameters, and local `val`s.
- **MUST** use `SCREAMING_SNAKE_CASE` for `const val` and for top-level,
  `object`, or `companion object` `val`s that hold **deeply-immutable constant
  data** — a fixed scalar, string, `Duration`, immutable value object, or
  read-only collection of those, built from constants. This is the Kotlin
  coding-conventions rule (and the ktlint/detekt default): `const` is not
  required, the property just has to be a constant.

  ```kotlin
  private const val SLOW_TX_THRESHOLD_MS: Long = 3_000
  private val DEFAULT_IDLE_TIMEOUT: Duration = Duration.ofMinutes(30)  // companion val
  val SYSTEM_USER_ID: UserId = UserId(UUID(0L, 0L))                    // top-level val
  private val CASTLEWOOD_ID = LeagueId(...)                            // file-private test fixture
  ```

- **MUST NOT** use `SCREAMING_SNAKE_CASE` for a `val` that holds a stateful or
  instance-like value rather than a constant. Loggers, dispatchers, encoders,
  configured serializers, `SecureRandom`, and the like stay `lowerCamelCase`
  even at top level — they are objects with identity/behavior, not constants
  (e.g. `private val logger = KotlinLogging.logger(...)`,
  `val appJson: Json = Json { ... }`). The test is what calling it does: one
  that touches a database, file, socket, clock or global state is not a
  constant however immutable its fields, while a `Regex` or jOOQ `Condition` is.
- **SHOULD** prefer full words over abbreviations (`employee` not `emp`).
  Exceptions: widely understood domain abbreviations (SSN, URL, HTTP, ID) and
  conventional loop variables.
- **MUST** name functions for what they do, not how. `findActivePlayers()`,
  not `queryPlayersWhereActive()`.
- **MUST NOT** embed type names in variable names (see Hygiene Rules).

---

## Change Scope

How changes are packaged matters as much as how code is written. Small
cohesive changes get reviewed carefully; large sprawling ones get
rubber-stamped or stalled. Optimize each commit and pull request for
the reviewer who will read it cold.

- **MUST** keep each commit and pull request focused on a single
  logical change. A logical change is one thing a reviewer needs to
  understand end-to-end: a feature, a fix, a refactor, or a migration
  — not three of those at once.
- **MUST** map pull requests to Linear issues one to one. Separable work
  becomes sub-issues when it is planned, never a second PR on one issue; a
  change heading past a few hundred non-test lines proposes its split first.
- **MUST NOT** bundle unrelated refactors, formatting passes, or
  drive-by cleanups with feature work. If you spot something worth
  cleaning up while implementing a feature, do it in a separate
  commit before or after — never the same diff.
- **MUST** present a clean pull request. What a reviewer is shown has
  to be coherent; the path taken to get there does not. Concretely:
  the branch tip passes the **Verification** gate below, the diff is
  scoped to one logical change, and `PR_BODY.md` describes what
  actually landed. Intermediate commits are working state — a commit
  that is red in isolation is not a defect, and a "fix compilation"
  or "fix tests" follow-up does not need folding away.

  The reason this stops at the branch tip: `main` carries exactly one
  commit per PR, because `allow_squash_merge` is the only mode
  enabled. Two-thirds of the commits written here are unreachable
  from `main`, and `git bisect` can never land on one. A per-commit
  gate therefore spends real time — a forced `check` takes over three
  minutes on the hosted runner — buying a property nothing downstream
  can observe. **If `allow_rebase_merge`
  is ever enabled, the bisect argument returns and so does the
  per-commit rule.**
- **MUST NOT** leave a placeholder commit message. This is the one
  commit-level rule that survives the bullet above, because the
  commit list *is* part of what the PR presents. `TEMP review fixes
  (to be folded)` is a defect; the fix is to write a real subject,
  never to rewrite history so the commit disappears.
- **SHOULD** split large work into a stack of small dependent commits
  or PRs. A vertical slice of a single domain (migration + models +
  repository + service + routes + UI + tests) is one logical change;
  a cross-cutting refactor that touches many domains is not — split
  by domain or by concern.
- **SHOULD** keep PRs small enough to review carefully in one
  sitting. If a single PR is reaching hundreds of lines of
  non-mechanical diff, look for a natural split.
- **SHOULD** colocate tests with the code they cover in the same
  pull request. Production code in one PR followed by a tests-only PR
  splits a single logical change across two reviews, neither of which
  can be verified end-to-end.
- **SHOULD** keep convention-codifying CLAUDE.md updates in a
  separate commit from the work that motivated them. The motivating
  work is reviewed on its merits; the policy update is reviewed for
  long-term implications. Mixing them obscures both.
- **MAY** include obvious mechanical refactors (renaming a function
  whose new name is only sensible given a feature change) inline
  with feature work when separating them would create churn. Use
  judgment; err toward separation.

---

## Git & PR Workflow

These rules apply to every change that lands via PR. A branch takes the name
Linear gives its issue (`gitBranchName`, shaped `<user>/cwf-<num>-<slug>`).
`main` is protected against direct push, force-push, and deletion, and
requires the `gradle check` status; `scripts/setup-branch-protection.sh` is
the record of that configuration.

### Iteration

- **MUST** iterate on a PR branch with new commits on top — never `git
  commit --amend`, never `git push --force` / `--force-with-lease` on a PR
  branch. Reviewers see exactly what changed between visits; squash-merge
  at the end consolidates the iteration into one clean main-history entry.
- The repo is configured to make this structural:
  `allow_squash_merge: true`, `allow_merge_commit: false`,
  `allow_rebase_merge: false`, `delete_branch_on_merge: true`, squash
  commit takes `PR_TITLE` + `PR_BODY`. Squash is the only merge mode
  visible in the GitHub UI; the source branch auto-deletes on merge.

### Pushing

- **MUST** push a new branch for the first time with an explicit refspec:
  `git push -u origin <local>:<local>`. Why: `git checkout -b X origin/main`
  sets the local branch's upstream to `origin/main`, and a subsequent
  `git push -u origin X` honors that upstream and lands the commit on
  `main` (fast-forward, no warning). Branch protection now rejects the
  catastrophic case, but the rule keeps the bad pattern out of habit.
- **MUST NOT** force-push to `main` under any circumstance without
  explicit, in-the-moment user authorization. `git push
  --force-with-lease=<refname>:<expected-sha>` is the right tool when
  destructive recovery is genuinely needed (e.g. rolling back an
  accidental direct push to main); it is never a normal development
  action.
- **MUST NOT** merge a PR as an agent — no `gh pr merge`, with or without
  `--admin`. Stop at ready; a human merges.

---

## Verification

`./gradlew check --rerun-tasks` is the canonical pre-PR verification gate for
the whole repo. It aggregates the backend tests, ktlint, detekt, and coverage,
plus — through the node-gradle integration — the UI build and tests.
The branch tip must pass it before the PR is opened, and again after any
rebase or merge from `main` — see **Change Scope** for why the gate lands
there rather than on every commit. `./gradlew test` alone is **not**
sufficient (it skips coverage and the UI tests). Stack-specific toolchain
rules (Gradle / version catalog / ktlint / detekt for the backend; npm
typecheck / vitest for the UI) live in the per-stack files.

Three things make that command a gate. Without any one of them, a run that
executed nothing, or failed, still reads as success.

- **MUST** pass `--rerun-tasks`. Gradle has two ways to not run a task: an
  unchanged input snapshot marks it `UP-TO-DATE`, and a result the build cache
  already holds is fetched and marked `FROM-CACHE`. **`--no-build-cache` does
  not substitute** — it disables the cache and leaves the up-to-date checks in
  place.
- **MUST** confirm the same run shows `test` and `detekt` **bare for every
  module** — `:test` and `:detekt`, plus the `:<module>:` pair of every project
  `settings.gradle.kts` includes — no `UP-TO-DATE`, `FROM-CACHE`, `SKIPPED` or
  `NO-SOURCE` after the name. Absence of `UP-TO-DATE` is not the test: it
  passes a `FROM-CACHE` gate, and a forced run still marks actionless lifecycle
  tasks `UP-TO-DATE`. It is also what catches a task dropped from the graph,
  which no flag can force because it prints no line at all.
  `.github/scripts/assert-gate-tasks-ran-bare.sh settings.gradle.kts <log>`
  is the check CI runs; run it on a local log the same way.
- **MUST** read the verdict from inside the run's own output, captured where
  you can grep it for `BUILD SUCCESSFUL` / `BUILD FAILED` and for every one of
  those task lines. An exit code relayed by a wrapper is the status of the *last*
  command in a compound, so `./gradlew check --rerun-tasks > log; echo done`
  always looks like success.

Reds the formatter would have prevented are their own class:

- **MUST** run `./gradlew ktlintFormat` before the first gate of a change, and
  commit what it rewrites. `test` depends on `ktlintCheck`, so an unsorted
  import fails the *test* run and not merely the lint one.
- **MUST NOT** run `ktlintFormat` between a red gate and a green one. The green
  attests to a tree no commit holds.
- It corrects nothing detekt owns. `MaxLineLength`, `LongMethod`,
  `LongParameterList` remain hand-fixes.

A red run's evidence is perishable:

- **SHOULD** copy `build/test-results/test/TEST-*.xml` aside before retrying a
  red build. It carries the exception message; a green retry overwrites it.
- Console output from earlier runs survives in
  `~/.gradle/daemon/<version>/daemon-<pid>.out.log`.

---

## Working with AI Assistants

When an AI assistant generates code against this guide:

- Generated code **MUST** match the idioms in this document and the per-stack
  guides. Code using patterns explicitly excluded here (Arrow types, Spring
  annotations, mutable collections in public APIs, scattered `!!`,
  `runBlocking` outside entry points; Redux, `React.FC`, `any` casts on the
  UI side) is wrong and must be rewritten regardless of whether it compiles.
- When two valid approaches exist, the AI **SHOULD** surface both and let the
  human choose rather than silently picking.
- AI-generated domain models **MUST** be reviewed especially carefully. The
  shape of the domain is load-bearing; mistakes compound.
- AI **MUST NOT** introduce new top-level dependencies (libraries, Gradle
  plugins, Ktor plugins, npm packages) silently. Flag additions
  explicitly.
- AI **SHOULD** match the existing code style of the file being edited when
  it diverges slightly from this document. Consistency within a file is more
  important than global uniformity.
- AI **MUST** write a decision into its ticket as soon as the user makes it. A
  ticket holding options and a recommendation but no record reads as
  undecided, and gets re-raised.
- AI **MUST** keep anything not committed to git in Linear, never in Drive
  or a local folder: a copy outside both is one nothing checks.
- AI **SHOULD** read a closed-catalog entry's KDoc, or the header of the
  migration that added it, before escalating a product question about it.
  Product rules are written there.
- AI **MUST** run [`cwf-review`](skills/cwf-review/SKILL.md) before the
  **first push**, not merely before opening the PR. Review findings that arrive
  after a push are fix commits by necessity; the same findings before it fold
  into the commit that caused them. The skill produces:
  1. A recap of the diff (working tree + commits ahead of the PR base).
  2. A test coverage review — what's well covered, gaps worth filling
     (with rationale), and gaps deliberately skipped.
  3. A style audit against this guide and the per-stack files.
     Explicitly check the patterns most often missed (representative,
     not exhaustive — the per-stack files are authoritative):
     `var` in production code; `!!` anywhere including tests;
     non-local returns from lambdas; missing KDoc on new public
     surface; embedded type names; `runCatching` / `runBlocking` /
     `Throwable` catching outside framework boundaries; new dependencies
     introduced silently. Comments are audited only for claims the code
     contradicts; the comment Hygiene Rules govern writing, not review.

  Each potential violation must be fixed or explicitly justified before the
  first push; the human reads the diff on the PR. User agreement to a plan
  does not stand in for the gate.
- When the user asks to review a change locally first, AI **MUST** stage or
  commit it and stop before pushing. The push waits for their word.
