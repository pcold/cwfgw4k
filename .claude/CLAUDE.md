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
   types, and precise data classes before runtime checks.

2. **Explicit over implicit.** Clear function signatures, named arguments,
   obvious control flow. No annotation-driven magic. No framework-injected
   behavior that can't be read top-to-bottom.

3. **Immutability by default.** `val` over `var`. Read-only collections over
   mutable ones. Copy via `copy()` instead of mutating.

4. **Idiomatic Kotlin, not transliterated Scala.** Use Kotlin's native tools
   (nullable types, scope functions, `when`) where they fit. Don't bend the
   language into knots to fake Scala features it genuinely lacks. The same
   principle applies to TypeScript — use discriminated unions and type
   guards rather than transliterating from another language.

5. **No framework heroics.** Lightweight libraries composed explicitly, not
   batteries-included frameworks with annotation-driven wiring.

6. **Readable before clever.** Code is read more than written. A few more lines
   that are obvious beats one line that requires a comment.

7. **Always try to explain yourself in code.** Comments are the fallback, not
   the primary mechanism. Good names, good types, and good structure are the
   primary mechanism.

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
- **MUST** provide KDoc (Kotlin) / TSDoc (TypeScript) on public interfaces
  that cross module boundaries.

---

## Naming

- **MUST** use `UpperCamelCase` for types; `lowerCamelCase` for functions,
  properties, and parameters; `SCREAMING_SNAKE_CASE` only for `const val`.
- **MAY** use `SCREAMING_SNAKE_CASE` for file-private `val` fixtures in test
  code that represent fixed test data referenced across multiple tests in the
  file (e.g., `private val CASTLEWOOD_ID = LeagueId(...)`). They read as
  constants even though the `val` is not `const`.
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
- **MUST NOT** bundle unrelated refactors, formatting passes, or
  drive-by cleanups with feature work. If you spot something worth
  cleaning up while implementing a feature, do it in a separate
  commit before or after — never the same diff.
- **MUST** keep each commit independently buildable. Every commit on
  the branch should pass `./gradlew check`. No "fix compilation" or
  "fix tests" follow-up commits within the same PR.
- **SHOULD** split large work into a stack of small dependent commits
  or PRs. A vertical slice of a single domain port (migration +
  models + repository + service + routes + tests) is one logical
  change; a cross-cutting refactor that touches many domains is not
  — split by domain or by concern.
- **SHOULD** keep PRs small enough to review carefully in one
  sitting. If a single PR is reaching hundreds of lines of
  non-mechanical diff, look for a natural split.
- **SHOULD** colocate tests with the code they cover in the same
  commit. Production code in one commit followed by a tests-only
  commit reads worse than one combined commit reviewers can verify
  end-to-end.
- **SHOULD** keep convention-codifying CLAUDE.md updates in a
  separate commit from the work that motivated them. The motivating
  work is reviewed on its merits; the policy update is reviewed for
  long-term implications. Mixing them obscures both.
- **MAY** include obvious mechanical refactors (renaming a function
  whose new name is only sensible given a feature change) inline
  with feature work when separating them would create churn. Use
  judgment; err toward separation.

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
  plugins, Ktor plugins, npm packages) silently. Flag additions explicitly.
- AI **SHOULD** match the existing code style of the file being edited when
  it diverges slightly from this document. Consistency within a file is more
  important than global uniformity.
- AI **MUST** pause for review before `git commit`. The pause includes
  (1) a recap of the diff and (2) a test coverage review listing what
  is well covered, gaps worth filling (with rationale), and gaps
  deliberately skipped. User agreement to a plan is not a commit
  signal.