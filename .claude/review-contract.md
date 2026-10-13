# Review Contract

The single, authoritative contract for reviewing a cwfgw4k change. **Both**
reviewers consume this file:

- **`cwf-review`** (local, pre-push gate) — runs it as its style audit, and
  re-runs it inside `resolve-reviews` before pushing review fixes.
- **`claude-review.yml`** (CI, on every PR push) — applies it to the PR diff.

Running the *same* contract in both places is the point: CI must not be able to
flag a checklist item the local run didn't, and the local run must not flag one
CI wouldn't. What remains is the deliberate model difference — a thorough
Opus pass locally, a Sonnet second opinion on CI.

This file is the operational checklist. The authoritative *rules* it enforces
live in the CLAUDE.md files; where this contract and a CLAUDE.md disagree, the
CLAUDE.md wins and this file is the thing that gets fixed.

Above both sits the **toolchain**. ktlint, detekt and tsc are what
`./gradlew check` actually enforces, so where a prose rule and the toolchain
conflict, the toolchain wins and the prose is what gets fixed — a review can't
demand a change the gate rejects. Which invocation of it counts as having run
is fixed by root [`.claude/CLAUDE.md`](CLAUDE.md) → Verification.

## Source rules

- **root `.claude/CLAUDE.md`** — hygiene, naming, change scope, "Working with
  AI Assistants".
- **`src/CLAUDE.md`** — Kotlin / Ktor / jOOQ / Kotest. Apply to any change
  under `src/`.
- **`ui/CLAUDE.md`** — React / TypeScript / TanStack / Tailwind / Vitest. Apply
  to any change under `ui/`.

## The tree under review

A forked pass resolves paths against the session's working directory, and a
brief naming a worktree does not move it. The resolution is not reachable from
inside the repo, so the guard has to be procedural.

It fails green: an empty diff reads in the output exactly like a clean one. A
review whose diff does not intersect the branch under test has not run, whatever
verdict it printed.

The only tell is a finding naming a file the branch never touches, and it goes
silent exactly where the risk concentrates: a wave of sibling tickets works one
family of repositories, migrations and services, so a review handed the wrong
sibling's branch reads as plausible line by line. A guard that asks the reader
to notice an absence is not a guard.

So every local pass — `cwf-review`, and every review an agent or a phase spawns
— **MUST** open its report with the tree it resolved, ahead of any recap or
finding:

```
REVIEWED base <base sha> head <head sha>
<path>
<path>
```

and the agent that invoked it **MUST** check both SHAs and every path against
`cd <absolute worktree path> && git rev-parse HEAD` and
`cd <absolute worktree path> && git diff --name-only <base sha>...HEAD` before
reading a single finding. CI sits outside this: it reviews a checkout it made
itself, off a PR number that cannot quietly become another's.

A mismatch is not a finding to weigh — the review did not happen. Spawn it once
more with the absolute worktree path and the base SHA written into the prompt;
if the second preamble still disagrees, stop, report both preambles against the
diff, and leave the ticket blocked on the gate rather than carrying a verdict
forward. One retry, because the failure is a resolution accident that stating
the path usually corrects; not two, because an unbounded retry ends when some
output finally looks right.

## What to review

1. **Correctness.** Does the change do what it claims? Edge cases, error paths,
   concurrency, off-by-one, null/empty handling. A comment or doc claim the
   code contradicts, or one the diff made stale, is a correctness finding.
   Redundant or verbose comments are not reported, although root CLAUDE.md →
   Hygiene Rules still governs how comments are written.
2. **MUST / MUST NOT compliance** with the source rules above.
3. **Test coverage.** Flag a non-trivial production change with no
   corresponding test as a gap worth filling, naming the layer to add it at
   (repo / service / route / component). Use the layered testing strategy:
   repo specs use the shared `postgresHarness()`; service and route specs use
   fake repos and `FakeTransactor`; the production impl is the private `JooqX`
   class inside `XRepository.kt`. UI tests render with `renderWithProviders`,
   interact via `userEvent`, and mock at the `@/shared/api/client` boundary.
4. **Change scope.** One logical change per PR. Flag bundled unrelated changes;
   a CLAUDE.md update mixed with the work that motivated it (must be a separate
   commit); a placeholder commit message still standing in the PR's commit list
   (e.g. `TEMP review fixes`); and new top-level dependencies (Gradle,
   version-catalog entries, npm packages) introduced silently. A commit that is
   red in isolation is **not** a defect — the gate lands on the branch tip.

## Scale the gate to the change

A review that costs the same on a one-line doc fix as on a schema migration taxes
exactly the changes that never needed it. So pick the tier from what the diff
*touches*, not from how it feels.

**Tier 0 — checklist.** Prose-only changes to a document about something else —
a README, anything under `docs/` — pure mechanical renames
with no logic change, and test-only additions. Run Phases 1–3 and the checklist.

**Tier 1 — standard (the default).** Phases 1–5 including the rot grep. A
prose-only diff lands here when the prose is itself instructions — the CLAUDE.md
files, this contract, everything under `.claude/skills/` — rather than a
description of the system those instructions govern.

**Tier 2 — adversarial, before the push.** Tier 1 plus a full `/code-review` pass
*prior to the first push*. Selected when the diff touches any of:

- a migration under `src/main/resources/db/migration/`
- a new architectural pattern with no in-repo precedent to check against
- a security surface: authentication, session handling, or the admin gate
- a repository's public contract
- payout or scoring arithmetic (`scoring/`, `reports/ReportHelpers.kt`) — the
  results must stay zero-sum, and a rounding change that compiles can break it
- `db/Transactor.kt`, or anything that changes how a transaction opens — the
  dispatcher and block contract there are the fix for a production outage
- ESPN golfer matching — a mismatch writes duplicate golfers, which has happened
  in production with the suite green

Tier 2 is not extra work: the same pass run after a push costs the same time
**plus** the fix commits, the CI re-run, and the reviewer's second visit.

## Severity

- **DEFECT** — a MUST / MUST NOT violation. Blocks.
- **NEEDS JUSTIFICATION** — a SHOULD / SHOULD NOT violation. Fix it, or write
  the justification.
- **NIT** — style preference, MAY, or readability. The author's call.

NIT is for matters the rules leave open. A MUST / MUST NOT violation does not
become a NIT because it is small or cosmetic. If a finding maps to a MUST, file
it as a DEFECT even when the fix is deleting two lines.

Guards on every finding:

- **Do not nitpick** generated code or pure mechanical renames. This guard is
  scoped to those two things. It is not licence to drop a MUST violation
  because it looks cosmetic.
- If a finding needs more context than the diff shows, **read the surrounding
  file before classifying** — don't fire a defect on a guess.
- **A fix that fails `./gradlew check` is not a finding.** The toolchain
  (ktlint, detekt, tsc) is the merge gate, so a suggestion it rejects
  is unmergeable no matter what a prose rule says. Where a rule below and the
  toolchain genuinely disagree, the toolchain wins — report the conflict as
  its own note naming both sides, so a human can fix the rule. Do not file it
  as a DEFECT against the code, and do not suggest a fix you have reason to
  think the gate would reject.
- **When citing an in-repo precedent** ("X already does this"), check *why* X
  does it before drawing the parallel. A sibling that looks inconsistent may
  differ for a load-bearing reason, and a consistency argument built on a
  misread precedent produces a defect that can't be fixed.
- The checklist below is representative, not exhaustive. If something looks off
  and isn't on it, still flag it and cite the closest rule.

## Patterns to check

These are the most-often-missed patterns. Grep / scan for each.

**Backend (Kotlin):**

- `!!` anywhere in production code, and `.let { it!! }` workarounds.
- `var` in production code (not test fixtures, not tight loops with
  justification).
- `var` properties on data classes (always wrong).
- A private-constructor `data class` without `@ConsistentCopyVisibility`
  (src/CLAUDE.md → Data Classes and Sealed Hierarchies).
- `return` from inside a non-inline lambda (non-local return).
- Missing KDoc on new public types/functions that cross module boundaries.
- Embedded type names in identifiers (e.g. `employeeList: List<Employee>`,
  `managerOpt: Employee?`).
- `runCatching { }`, `runBlocking { }` outside main/tests, catching `Throwable`
  or bare `Exception` outside `installErrorHandling()`.
- New Gradle dependencies / version catalog entries introduced silently.
- `as` unsafe casts (use `as?` with null handling, or restructure).
- `.first()` / `.single()` / `list[0]` on collections that may be empty.
- `lateinit` outside framework-imposed initialization.
- `@Throws`, `arrow.*` imports, `kotlin.Result<T>` as a return type, `String`
  or `Throwable` as the error type in `Result<T, E>`.
- Failures told apart by message text instead of error type, or a business
  rule refused as `Validation(message)` (src/CLAUDE.md → Error Handling).
- `JsonObject` / `JsonElement` in production code paths.
- Positional booleans at call sites.
- Space before colon in declarations.
- Missing explicit return type on a public function.
- Trailing-lambda used where it isn't the conceptual body of the call.
- Inline FQN references instead of imports (`org.jooq.DSLContext` in a
  signature when there's no name collision).
- Repository methods that don't take `context(ctx: TransactionContext)`, or
  services that capture a `DSLContext` instead of a `Transactor`.
- Services calling other services inside a `tx.read` / `tx.update` for flows
  that need read-then-write atomicity or one read snapshot (see "Cross-repo
  flows" in src/CLAUDE.md).
- `withContext(Dispatchers.IO)`, or any other dispatcher, around a JDBC call
  in repository or service code (src/CLAUDE.md → jOOQ).
- A `.filter { }` in a service or route over a list a repository returned,
  instead of a `WHERE` in the query (src/CLAUDE.md → jOOQ).
- A list read followed by a per-row point read (N+1) instead of one join
  (src/CLAUDE.md → Combining data across repositories (reads)).
- A business decision — a rule classifying or annotating rows — encoded in SQL
  (src/CLAUDE.md → Combining data across repositories (reads)).
- A data-integrity invariant held by an app guard alone, with no FK or CHECK
  under it (src/CLAUDE.md → jOOQ).
- An edit to a landed migration, comments included: any `db/migration/` file
  the diff modifies rather than adds (src/CLAUDE.md → jOOQ).
- Domain ID types passed as raw UUID across service/repo/route boundaries
  (every domain ID is a `@JvmInline value class`).
- Routes hand-writing `call.respond(HttpStatusCode.X)` for error cases instead
  of throwing `DomainError.NotFound` / `Validation` / `Conflict` / `BadGateway` /
  `Unauthorized` / `Forbidden`.
- Top-level `Route.xxxRoutes()` with inlined handler bodies instead of
  delegating to private `RoutingContext.xxx(service)` helpers.
- Query parameters parsed via `queryParameters[name]?.toXxx()` instead of
  `optionalQueryParam(name, parser)`.
- Caught exceptions logged below WARN, or not logged with the throwable
  attached (root logger is INFO; DEBUG goes nowhere).
- Subpackages within a domain (routes/, services/, repos/) — the package layout
  is flat by domain.
- A per-spec seed helper over `ApiFixture`, or a fixture helper written as a
  member instead of a top-level extension (src/CLAUDE.md → Kotest).

**Frontend (TypeScript / React):**

- `any` type, `as` casts outside the two documented boundary exceptions (JSON
  deserialization in shared/api/client.ts and Testing Library DOM narrowing in
  test files), `!` non-null assertions.
- `React.FC` / `React.FunctionComponent`.
- `useEffect` used to derive state, fetch data, or reset state on prop change
  (use a derived const, TanStack Query, or a `key` prop).
- `useState(prop)` copying a prop into state.
- Index keys on list items in non-static lists.
- Direct state mutation (e.g. `array.push`, `obj.field = x` on state).
- `data-testid` queries where an accessible role / label / text exists.
- `fireEvent` used instead of `userEvent` for interaction.
- `useQuery` called conditionally instead of via `enabled`.
- `useState` / Context holding API response data instead of TanStack Query.
- Mixed value/type imports without `import type` (verbatimModuleSyntax is on).
- Missing return type on exported functions.
- `==` / `!=` instead of `===` / `!==`.
- `// @ts-ignore` / `// @ts-expect-error`, or any other suppression comment,
  without an explanatory comment.
- Generic `pages/`, `components/`, or `hooks/` top-level buckets; catch-all
  `utils.ts` / `helpers.ts`.
- Direct `fetch` in components instead of routing through
  `@/shared/api/client`.
- A business fact derived in the UI from API rows, or a copy of a rule the
  server enforces (ui/CLAUDE.md → Where Logic Lives).

**Cross-cutting (root CLAUDE.md):**

- Bundled unrelated changes in one commit.
- A placeholder commit message left standing on the branch.
- A CLAUDE.md update mixed with the motivating work.
- A surface removed, replaced or narrowed without naming what admits rows to the
  replacement and showing the old one's population sits inside it. "Y replaces X"
  is the claim, not the showing.
- Commented-out code.

## Verdict

- **BLOCK** — any defect exists.
- **PROCEED WITH JUSTIFICATIONS** — no defect, but SHOULD violations exist.
- **CLEAN** — neither.
