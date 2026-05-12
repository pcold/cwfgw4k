---
name: cwf-review
description: Repo-specific code review gate for cwfgw4k (Kotlin/Ktor backend + React/TypeScript UI). Performs the pre-commit human-gate audit defined in .claude/CLAUDE.md "Working with AI Assistants" — diff recap, test coverage review, and style audit against MUST/MUST NOT/SHOULD rules in root .claude/CLAUDE.md, src/CLAUDE.md, and ui/CLAUDE.md. Output is triaged into defects (MUST), needs-justification (SHOULD), and nits. Optionally offers to fix MUST defects after review.
---

# cwf-review

A repo-specific code review for cwfgw4k that mirrors the pre-commit gate
described in the "Working with AI Assistants" section of the root
.claude/CLAUDE.md. The default scope is **uncommitted changes plus all
commits ahead of main** — the full state a reviewer would see immediately
before a commit or PR.

## When to invoke

- The user types `/cwf-review` (or asks "review this branch", "do the style
  audit", "is this ready to commit").
- Before any AI-proposed commit on this repo, regardless of whether the
  user typed the slash command — the root CLAUDE.md requires the pause and
  this skill is the canonical way to perform it.

## Inputs

- `args` (optional): scope override. Accepts `staged`, `working`, `branch`,
  `pr <number>`. Default is `branch` (working tree + commits ahead of main).

## Procedure

Run these phases in order. Do **not** skip phases on the assumption that a
diff is small — the audit catches the patterns most often missed, which
are typically subtle and live in small diffs.

### Phase 1 — Gather the diff

Run these in parallel:

- `git status` (no `-uall`)
- `git diff --stat <base>` and `git diff <base>` where `<base>` is:
  - `HEAD` for `staged`/`working` scope
  - `origin/main...HEAD` (or `main...HEAD` if no origin) for `branch` scope
  - The PR base SHA for `pr <number>` scope (use `gh pr view <n> --json baseRefOid`)
- `git log <base>..HEAD --oneline` for branch/PR scope to see commit
  decomposition.

If working tree is dirty and scope is `branch`, gather **both** the
committed diff and the working-tree diff so the audit covers everything a
reviewer would land.

### Phase 2 — Diff recap

Produce a 3–8 line recap of what changed: domains touched, the shape of
the change (feature / fix / refactor / migration / test-only), and the
commit decomposition. This is the entry point for the rest of the review
— skipping it makes the triage harder to read.

Flag immediately if:

- Multiple unrelated logical changes are bundled (root CLAUDE.md Change
  Scope rules).
- A CLAUDE.md update is mixed with the work that motivated it (must be
  a separate commit).
- A commit on the branch is not independently buildable (e.g. a "fix
  tests" follow-up).
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

Use the layered testing strategy from memory: repo specs use the shared
`postgresHarness()`; service and route specs use fake repos; the
production impl is the private `JooqX` class inside `XRepository.kt`. UI
tests render with `renderWithProviders`, interact via `userEvent`, and
mock at the `@/api/client` boundary.

### Phase 4 — Style audit (the gate)

Walk the diff and produce findings. Every finding has:

1. **Severity** — `DEFECT` (MUST violation), `NEEDS JUSTIFICATION` (SHOULD
   violation), or `NIT` (style preference, MAY, or readability).
2. **Location** — file path and line number, written plain (no
   backticks — the user's terminal renders inline code in a hard-to-read
   light blue).
3. **Rule** — the CLAUDE.md section the finding maps to (e.g. "src/CLAUDE.md
   → Nullability → no !! in production code").
4. **What's wrong** — one sentence.
5. **Fix** — concrete, applicable suggestion.

#### Patterns the audit MUST explicitly check

These are the most-often-missed patterns. Grep / scan for each:

**Backend (Kotlin):**

- `!!` anywhere in production code, and `.let { it!! }` workarounds.
- `var` in production code (not test fixtures, not tight loops with
  justification).
- `var` properties on data classes (always wrong).
- `return` from inside a non-inline lambda (non-local return).
- Missing KDoc on new public types/functions that cross module
  boundaries.
- Embedded type names in identifiers (e.g. `employeeList: List<Employee>`,
  `managerOpt: Employee?`).
- `runCatching { }`, `runBlocking { }` outside main/tests, catching
  `Throwable` or bare `Exception` outside `installErrorHandling()`.
- New Gradle dependencies / version catalog entries introduced silently.
- `as` unsafe casts (use `as?` with null handling, or restructure).
- `.first()` / `.single()` / `list[0]` on collections that may be empty.
- `lateinit` outside framework-imposed initialization.
- `@Throws`, `arrow.*` imports, `kotlin.Result<T>` as a return type,
  `String` or `Throwable` as the error type in `Result<T, E>`.
- `JsonObject` / `JsonElement` in production code paths.
- Positional booleans at call sites.
- Space before colon in declarations.
- Missing explicit return type on a public function.
- Trailing-lambda used where it isn't the conceptual body of the call.
- Inline FQN references instead of imports (`org.jooq.DSLContext` in a
  signature when there's no name collision).
- Repository methods that don't take `context(ctx: TransactionContext)`,
  or services that capture a `DSLContext` instead of a `Transactor`.
- Services calling other services inside a `tx.read` / `tx.update` for
  flows that need read-then-write atomicity (see "Cross-repo flows" in
  src/CLAUDE.md; reference shapes: AdminService.confirmRoster,
  ScoringService.calculateScores).
- Domain ID types passed as raw UUID across service/repo/route
  boundaries (every domain ID is a `@JvmInline value class` per memory).
- Routes hand-writing `call.respond(HttpStatusCode.X)` for error cases
  instead of throwing `DomainError.NotFound` / `Validation` / `Conflict`.
- Top-level `Route.xxxRoutes()` with inlined handler bodies instead of
  delegating to private `RoutingContext.xxx(service)` helpers.
- Query parameters parsed via `queryParameters[name]?.toXxx()` instead
  of `optionalQueryParam(name, parser)`.
- Caught exceptions logged below WARN, or not logged with the throwable
  attached (memory: root logger is INFO; DEBUG goes nowhere).
- Subpackages within a domain (routes/, services/, repos/) — the
  package layout is flat by domain per memory.

**Frontend (TypeScript / React):**

- `any` type, `as` casts outside the two documented boundary exceptions
  (JSON deserialization in shared/api/client.ts and Testing Library DOM
  narrowing in test files), `!` non-null assertions.
- `React.FC` / `React.FunctionComponent`.
- `useEffect` used to derive state, fetch data, or reset state on prop
  change (use a derived const, TanStack Query, or a `key` prop).
- `useState(prop)` copying a prop into state.
- Index keys on list items in non-static lists.
- Direct state mutation (e.g. `array.push`, `obj.field = x` on state).
- `data-testid` queries where an accessible role / label / text exists.
- `fireEvent` used instead of `userEvent` for interaction.
- `useQuery` called conditionally instead of via `enabled`.
- `useState` / Context holding API response data instead of TanStack
  Query.
- Mixed value/type imports without `import type` (verbatimModuleSyntax
  is on).
- Missing return type on exported functions.
- `==` / `!=` instead of `===` / `!==`.
- `// eslint-disable` or `// @ts-ignore` / `// @ts-expect-error`
  without an explanatory comment.
- Generic `pages/`, `components/`, or `hooks/` top-level buckets;
  catch-all `utils.ts` / `helpers.ts`.
- Direct `fetch` in components instead of routing through `@/api/client`.

**Cross-cutting (root CLAUDE.md):**

- Bundled unrelated changes in one commit.
- A commit on the branch that isn't independently buildable.
- A CLAUDE.md update mixed with the motivating work.
- Comments that narrate what the code already says.
- Commented-out code.

### Phase 5 — Triage summary

End with a compact summary block in this exact shape:

```
Defects (MUST): <count>
Needs justification (SHOULD): <count>
Nits: <count>

Verdict: <BLOCK | PROCEED WITH JUSTIFICATIONS | CLEAN>
```

`BLOCK` if any defects exist. `PROCEED WITH JUSTIFICATIONS` if no
defects but SHOULD violations exist that need a written reason.
`CLEAN` if neither.

### Phase 6 — Offer fixes for defects only

If defects exist, list them numbered and ask:

> Would you like me to fix any of these MUST defects? (e.g. "fix 1, 3,
> 5"). I will not touch SHOULD violations or nits — those are your call.

If the user picks fixes, apply them, then re-run Phase 4 on the touched
lines to confirm the defect is resolved and nothing new was introduced.

Do **not** fix SHOULD violations or nits automatically — that's the
human's judgment call per the user's selection when this skill was
designed.

## Output format

Use plain text with section headers. Do not wrap file paths or symbol
names in backticks (memory: terminal renders them in hard-to-read light
blue). Use forward references like `src/main/kotlin/.../TeamService.kt:42`
inline as plain text.

Example finding:

```
DEFECT  src/main/kotlin/com/cwfgw/teams/TeamService.kt:88
Rule    src/CLAUDE.md → Nullability → no !! in production code
Issue   team!!.captain accesses through a !! assertion
Fix     Replace with a null guard: val captain = team?.captain ?: return Result.Err(NotFound)
```

## Notes for the reviewer

- The audit is a **pause for human review**, not a green light. Even a
  CLEAN verdict means the human still reads the diff before the commit
  lands.
- The MUST/MUST NOT lists in src/CLAUDE.md and ui/CLAUDE.md are
  authoritative. The patterns above are representative, not exhaustive —
  if something looks off and isn't on the list, still flag it and cite
  the closest rule.
- When a rule and existing code conflict, the rule wins: existing code
  is wrong and gets reworked, per the root CLAUDE.md preamble.
- If a finding requires reading more context than the diff shows, read
  the surrounding file before classifying — don't fire a defect on a
  guess.
