# Kotlin Code Style Guide

Code hygiene is of the utmost importance. You should generally follow the
principles of clean code. This document uses **MUST**, **MUST NOT**, **SHOULD**,
**SHOULD NOT**, and **MAY** in the RFC 2119 sense. Deviations from **MUST** rules
are defects. Deviations from **SHOULD** rules require justification.

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
   language into knots to fake Scala features it genuinely lacks.

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
- **SHOULD** use comments to:
  - Explain intent
  - Clarify non-obvious code
  - Warn of consequences
- **MUST NOT** use comments to:
  - Repeat what the code already says
  - Narrate obvious operations
  - Disable code "just in case"
- **MUST** provide KDoc on public interfaces that cross module boundaries.

---

## Language Rules

### Nullability

- **MUST NOT** use `!!` in production code. The only acceptable use is at
  genuine platform-type boundaries where non-nullness is externally guaranteed
  and documented in a comment.
- **MUST NOT** use `.let { it!! }` or similar workarounds that smuggle `!!`
  through a scope function.
- **MUST** use Kotlin's nullable types (`T?`) as the Option equivalent. Do not
  introduce custom `Option<T>` types or import Arrow's `Option<T>`.
- **SHOULD** prefer non-nullable types. Nullability is opt-in; if a function
  always returns a meaningful result, the return type must not be nullable.
- **SHOULD** use `?.let { }` for transforming nullable values, `?:` for
  defaults, and early `if (x == null) return` for guard patterns.
- **SHOULD NOT** chain more than three `?.` operators in one expression.
  Extract intermediate values with meaningful names.

### Mutability

- **MUST** prefer `val` over `var`. A `var` in production code requires a
  visible justification (tight performance loop, deliberate local mutation).
- **MUST NOT** use `var` properties on data classes. Data classes are value
  types; mutate via `copy()`.
- **SHOULD** use read-only collection types (`List`, `Set`, `Map`) in public
  APIs, parameters, and return types.
- **MAY** use mutable collections (`MutableList`, etc.) as a local
  implementation detail when performance or clarity benefits.
- **MUST NOT** expose mutable collections across module boundaries.

### Control Flow

- **MUST NOT** use `return` from within a lambda (non-local return) unless the
  function is `inline` and the non-local return is the deliberate point. Use
  `return@label` syntax explicitly if early exit from a lambda is needed.
- **SHOULD** use early returns for guards and invariant checks. Don't nest
  deeply to avoid a return.
- **MUST** use `when` as an expression for multi-branch logic. Don't chain
  `if`/`else if`/`else` for more than two branches.
- **MUST** have exhaustive `when` on sealed types. If you need an `else`
  branch on a sealed hierarchy, you're probably missing a variant.

### Error Handling

Policy, in order of preference:

1. **Nullable return types** when a function has a single meaningful failure
   mode — usually "not found" / "absent." `fun findById(id: EmployeeId):
   Employee?`. This is the default for repository reads and simple service
   lookups.
2. **`com.cwfgw.result.Result<T, E>` with a domain-specific sealed error
   hierarchy** when a function has multiple distinct failure modes that need
   to drive different caller behavior (e.g., state-machine transitions where
   "not found," "wrong status," "already completed," and "not your turn" must
   each produce a different HTTP status). The error type lives in the domain
   package and stays free of HTTP imports; routes map it to `DomainError`
   via a tiny `.orThrow()` extension. `DraftService` / `DraftRoutes` are the
   reference shape.
3. **Exceptions** for truly exceptional conditions: programmer errors
   (`IllegalArgumentException`, `IllegalStateException`), infrastructure
   failures, and at HTTP/framework boundaries — specifically `DomainError`
   thrown from routes and consumed by `installErrorHandling()`.

Rules:

- **MUST NOT** use exceptions for expected domain outcomes (validation
  failures, business-rule violations, not-found cases). Exceptions are for
  truly exceptional conditions and for bridging to `DomainError` at the
  route boundary.
- **MUST NOT** mix error-handling conventions for the same conceptual
  operation. A single function signature uses one pattern. Across a module,
  different operations can use different patterns (e.g., `get()` returning
  `T?` alongside `makePick()` returning `Result<T, E>` is fine — they have
  different failure-mode counts).
- **MUST** use sealed error hierarchies when returning `Result<T, E>`.
  `String` or `Throwable` as the error type is forbidden — the error must
  be an exhaustive sealed hierarchy so mapping at the boundary stays
  `when`-exhaustive.
- **MUST NOT** use `kotlin.Result<T>` as a return type. It's `Throwable`
  bound, requires opt-in, and doesn't carry typed error variants — wrong
  tool for domain errors. Use `com.cwfgw.result.Result<T, E>`.
- **MUST NOT** introduce Arrow's `Either`. `Result<T, E>` covers the use
  case and `arrow.*` is excluded by the "what we do not use" list.
- **MUST NOT** catch `Throwable` or bare `Exception` outside of
  `installErrorHandling()`. Catch specific types.
- **MUST NOT** swallow exceptions silently. If catching, log at appropriate
  level with the caught throwable attached.
- **MUST NOT** use `runCatching { }.getOrNull()` as a general pattern. It
  catches too much.
- **MUST NOT** use `@Throws`. Kotlin doesn't enforce it; it's noise.

### Data Classes and Sealed Hierarchies

- **MUST** use `data class` for product types. All fields `val`.
- **SHOULD** use `sealed interface` (preferred over `sealed class` when no
  shared state) for sum types.
- **MUST NOT** use plain `enum class` for anything that might later need to
  carry state. Start with `sealed interface` + `object` variants.
- **MUST NOT** define nested data classes inside other classes unless there's
  a compelling scoping reason. Most "nested" types belong at file level.
- **MUST NOT** expose public constructors when construction has invariants.
  Use a private primary constructor + factory on the companion object.
- **SHOULD** keep all variants of a sealed hierarchy in the same file as the
  parent type.

### Collections

- **MUST NOT** use `.first()` on collections that may be empty; use
  `.firstOrNull()` or a guarded access.
- **MUST NOT** use `.single()` unless you have proven the collection has
  exactly one element; use `.singleOrNull()`.
- **MUST NOT** index into lists without bounds checking (`list[0]` on
  possibly-empty lists). Use `.getOrNull(index)`.
- **SHOULD** use sequences (`.asSequence()`) for long pipelines, large data,
  or operations that benefit from short-circuiting. Eager is fine for small
  collections with two or three operations.
- **MAY** destructure pairs, map entries, and small data classes (≤3 fields).
  Don't destructure larger things — name the fields.

### Functions and Lambdas

- **SHOULD** keep functions under 50 lines. Extract helpers aggressively.
- **MUST** use named arguments at call sites when a function takes more than
  two parameters of the same type, or when boolean flags are passed.
- **MUST NOT** use positional booleans at call sites:

  ```kotlin
  // WRONG
  createEmployee("Jim", "jim@example.com", true, false)

  // RIGHT
  createEmployee(
    name = "Jim",
    email = "jim@example.com",
    isActive = true,
    isSalaried = false,
  )
  ```

- **SHOULD** prefer trailing lambda syntax only when the lambda is the
  conceptual "body" of the call:

  ```kotlin
  // RIGHT — lambda is the body of the operation
  employees.map { it.name }
  transaction { dsl -> repository.insert(dsl, employee) }
  ```

- **SHOULD** use method references for extremely short lambdas:

  ```kotlin
  // RIGHT
  employees.map(Employee::name)
  employees.filter { it.isActive }

  // WRONG — method reference expressed as lambda
  employees.map { it.name }  // prefer Employee::name when simple
  ```

- **SHOULD** name the lambda parameter when the lambda spans multiple lines.
  `it` is fine for single-expression lambdas only.

  ```kotlin
  // RIGHT
  employees.map { employee ->
    val department = employee.department
    department.name
  }

  // WRONG
  employees.map {
    val department = it.department
    department.name
  }
  ```

### Scope Functions

Use deliberately. Each scope function has a specific purpose:

- **`?.let { }`** — transform a nullable value. Most common legitimate use.
- **`.also { }`** — perform a side effect while returning the receiver.
- **`.run { }`** — scope a block needing multiple operations on a receiver,
  returning a different value.
- **`.apply { }`** — configure a mutable builder. Rare in this codebase;
  `copy()` is usually better.
- **`.with(x) { }`** — avoid. `x.run { }` reads better.

Rules:

- **MUST NOT** chain more than three scope functions. Extract intermediate
  `val`s.
- **MUST NOT** wrap code in `.let { }` solely to "look functional." If you
  aren't using the nullability feature, `.let` is just an extra indent.
- **MUST NOT** use `.apply { }` to configure a data class. Use `copy()` with
  named arguments.

### Classes and Inheritance

- **MUST NOT** define useless interfaces that exist only to "have an
  interface." Extract an interface only when there's a second implementation
  (real or test-double) or a genuine abstraction boundary.
- **SHOULD** keep classes `final` by default (Kotlin's default). `open` only
  when inheritance is an intended extension point.
- **MUST NOT** use `lateinit` outside of framework-imposed initialization (DI,
  test fixtures).
- **SHOULD** use constructor injection with `val` properties. No field
  injection, no setter injection.

### Casting

- **SHOULD** avoid casting. Use sealed hierarchies and exhaustive `when`
  instead of `is`-check ladders that feel like casts.
- **MUST NOT** use `as` (unsafe cast) in production code. Use `as?` (safe
  cast) with explicit null handling, or restructure to avoid the cast.

### Miscellaneous

- **MUST NOT** use `runBlocking` outside of `main` entrypoints and tests. It
  breaks structured concurrency.
- **MUST NOT** use reflection in business logic (`::class.members`, etc.). If
  you're reaching for reflection, there's usually a better design.
- **MUST NOT** use `GlobalScope`. It's almost always a bug.
- **SHOULD** prefer `object` over `class` + companion object + private
  constructor when the type is a true singleton with no construction
  parameters.

---

## Concurrency

- **MUST** use coroutines (`suspend` functions) for async work. No
  `CompletableFuture`, `Thread`, or callback patterns without specific interop
  justification.
- **MUST** respect structured concurrency. Child coroutines launched in a
  known `CoroutineScope`.
- **MUST** use the correct dispatcher:
  - `Dispatchers.IO` for blocking I/O (JDBC, file I/O, blocking SDK calls)
  - `Dispatchers.Default` for CPU-bound work
  - No `Dispatchers.Main` in this codebase
- **MUST** wrap blocking Java APIs in `withContext(Dispatchers.IO)` when
  calling from coroutines.
- **MUST NOT** swallow `CancellationException`. Let it propagate.
- **SHOULD** check `isActive` or `yield()` in long-running loops.

---

## Stack-Specific Guidance

### Ktor

- **MUST** split routing by domain into extension functions on `Route`:
  `fun Route.employeeRoutes() { ... }`, composed in `Application.module()`.
  No giant single `routing { }` blocks.
- **MUST** keep route handlers thin: deserialize → call service → serialize.
  Business logic lives in the service layer.
- **MUST** use the `ContentNegotiation` plugin with `kotlinx.serialization`.
  Respond with domain types directly.
- **MUST NOT** install a DI framework. Wire dependencies via constructor
  parameters and module-level setup.
- **SHOULD** install only Ktor plugins actually used. Each `install()` must be
  justifiable.

#### HTTP boundary: errors and parameter parsing

- **MUST** throw `DomainError.NotFound` / `Validation` / `Conflict`
  (`com.cwfgw.http.DomainError`) from routes — never hand-write
  `call.respond(HttpStatusCode.X)` for error cases. `installErrorHandling()`
  is the canonical sink that maps each variant to its status plus a JSON
  `ErrorBody`. The `exception<Throwable>` handler in `ErrorHandling.kt` is
  the single documented place in the codebase where `Throwable` is caught —
  do not replicate in business logic.
- **MUST NOT** push HTTP error semantics into the domain or service layer.
  Services return `T?` for "not found"; routes bridge with
  `?: throw DomainError.NotFound("<label> ${id.value} not found")`.
- **MUST NOT** make `String.toXId()` extensions throw `DomainError.Validation`.
  They're pure domain primitives and must not depend on `com.cwfgw.http`;
  the HTTP-aware throw belongs at the route boundary, not on the type.
- **MUST** use `com.cwfgw.http.optionalQueryParam(name, parser)` for every
  optional query parameter that can be malformed. Writing
  `queryParameters[name]?.toXxx()` directly silently swallows garbage as
  "no filter" — that's the bug the helper exists to prevent.
- **SHOULD** give each route file a private `RoutingContext.xxxId(): XId`
  helper that parses a path parameter or throws `DomainError.Validation`:
  ```kotlin
  private fun RoutingContext.teamId(): TeamId =
      call.parameters["teamId"]?.toTeamId() ?: throw DomainError.Validation("invalid team id")
  ```
  Error messages: `"invalid <thing> id"` for Validation; `"<thing> ${id.value}
  not found"` for NotFound — include the id for debuggability.
- **MUST** keep the top-level `Route.xxxRoutes()` function as a thin
  dispatcher — one line per verb that delegates to a private
  `RoutingContext.xxx(service)` function. Inlining handler bodies in the
  dispatcher is not allowed, even when the body would fit in two lines.
  Reasons: the dispatcher reads as a table of contents for the domain's
  HTTP surface; each handler has its own name and signature so a failing
  test points at the right function; and detekt's `ThrowsCount` rule (max
  2 per function) counts throws in nested lambdas as part of the outer
  function, so aggregating parse-or-throw handlers in one fun hits the
  limit fast. `TeamRoutes.kt` and `TournamentRoutes.kt` are the reference
  shape.
- **MUST** use `installRequestLogging()` (not bare `install(CallLogging)`).
  Its format — `cwfgw.request method=X route=Y status=Z duration_ms=N` with
  path normalization (UUIDs → `:id`, numerics → `:n`, `/assets/*` bucketed)
  — is load-bearing for Cloud Logging metric extraction. Changing the format
  requires coordinating with the shared cwfgw ops config.

### jOOQ

- **MUST** use the jOOQ DSL with generated types for all queries. No raw SQL
  strings except for DDL migrations and vendor-specific features jOOQ doesn't
  expose.
- **MUST** keep jOOQ code in repository classes. Service-layer code takes and
  returns domain types, not jOOQ records.
- **MUST NOT** expose jOOQ-generated types outside the repository package.
  Map to domain types explicitly at the repository boundary.
- **MUST** wrap blocking JDBC calls in `withContext(Dispatchers.IO)` or use
  jOOQ's coroutine-friendly execution.
- **MUST** use explicit transactions (`dsl.transaction { ... }`) for
  multi-statement atomic operations. Pass the transactional `DSLContext`
  through as a parameter; do not capture an outer one.

### kotlinx.serialization

- **MUST** annotate domain data classes with `@Serializable` at the point of
  declaration.
- **MUST** use `@SerialName` when the Kotlin property name and JSON field
  name diverge. Never rely on accidental matching.
- **MUST** configure `JsonClassDiscriminator` deliberately for sealed
  hierarchies exposed over the wire. Document the discriminator in a comment.
- **SHOULD** put custom `KSerializer` implementations in a dedicated package
  (e.g., `com.app.serialization`).
- **MUST NOT** use `JsonObject` / `JsonElement` in production code paths.
  Define a proper schema.

### Kotest

- **MUST** default to `FunSpec` or `DescribeSpec`. Pick one per module and use
  it consistently.
- **MUST** use Kotest matchers (`shouldBe`, `shouldContain`, `shouldThrow`)
  consistently. No mixing with JUnit-style assertions.
- **SHOULD** use property-based tests (`checkAll`, `Arb`) for logic with
  clear algebraic properties: scoring functions, parsers, data transformations.
- **MUST** write tests for new code.
- **MUST NOT** depend on test ordering or shared mutable state. Each test
  sets up its own world.
- **SHOULD** name tests as full sentences describing scenario and expected
  outcome: `"returns empty list when no players are active"`. Backticks are
  acceptable.

---

## What We Do Not Use

Conscious exclusions. Do not introduce without discussion:

- **Spring / Spring Boot** — annotation-driven framework that pulls toward
  Java idioms. We use Ktor.
- **JPA / Hibernate** — ORM paradigm fights both Kotlin idioms and explicit
  SQL. We use jOOQ.
- **Koin, Hilt, Dagger, or other DI frameworks** — constructor injection is
  sufficient at this scale.
- **Jackson** — we use kotlinx.serialization.
- **Arrow (core, fx, optics, etc.)** by default — may be reconsidered for
  specific cases (Optics for deeply nested domain updates is the most likely
  candidate). No `Raise<E>` or Arrow types in new code without explicit
  discussion.
- **`@Throws`** — checked exceptions are noise in Kotlin.
- **`runBlocking`** outside of `main` and tests.
- **Reflection in business logic.**

---

## Code Organization

- **MUST** organize by feature/domain, not by technical layer.
  `com.app.players`, `com.app.scoring`, `com.app.drafts` — each containing
  its own types, routes, services, and repositories.
- **SHOULD** keep one top-level declaration per file for non-trivial types.
  Small related types (sealed hierarchy + variants) may share a file.
- **SHOULD** use `internal` visibility liberally within a module.
- **MUST NOT** create `Utils.kt`, `Helpers.kt`, or other catch-all files. Name
  utility files for their purpose (`StringFormatting.kt`, `TimeParsing.kt`).

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

## Formatting

- **MUST NOT** put a space before a colon in declarations:

  ```kotlin
  // RIGHT
  val name: String = "Jim"
  fun greet(name: String): String = "Hello, $name"

  // WRONG
  val name : String = "Jim"
  fun greet(name : String) : String = "Hello, $name"
  ```

- **MUST** use trailing commas in multi-line parameter lists, argument lists,
  and collection literals. They produce cleaner diffs.
- **SHOULD** use expression-body functions for single-expression functions:

  ```kotlin
  // RIGHT
  fun fullName(e: Employee): String = "${e.firstName} ${e.lastName}"

  // WRONG
  fun fullName(e: Employee): String {
    return "${e.firstName} ${e.lastName}"
  }
  ```

- **MUST** explicitly declare return types on all public functions. Type
  inference is fine for local `val`s; it is not fine for public API.

---

## Build and Tooling

- **MUST** use Gradle with Kotlin DSL (`build.gradle.kts`). Not Groovy.
- **MUST** use version catalogs (`libs.versions.toml`) for dependency
  management. No hardcoded versions in build files.
- **MUST** configure ktlint and detekt to fail the build on violations, not
  just warn in the IDE.
- **MUST** use `./gradlew check` (not `./gradlew test`) as the verification
  gate before committing. `check` aggregates tests, ktlint, detekt, and
  coverage; `test` alone skips lint. The `test` task is wired to depend on
  `ktlintCheck` and `detekt` so running tests also runs lint, but `check`
  remains the canonical full verification command.
- **SHOULD** enable explicit API mode for library modules.

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

- Generated code **MUST** match the idioms in this document. Code using
  patterns explicitly excluded here (Arrow types, Spring annotations, mutable
  collections in public APIs, scattered `!!`, `runBlocking` outside entry
  points) is wrong and must be rewritten regardless of whether it compiles.
- When two valid approaches exist, the AI **SHOULD** surface both and let the
  human choose rather than silently picking.
- AI-generated domain models **MUST** be reviewed especially carefully. The
  shape of the domain is load-bearing; mistakes compound.
- AI **MUST NOT** introduce new top-level dependencies (libraries, Gradle
  plugins, Ktor plugins) silently. Flag additions explicitly.
- AI **SHOULD** match the existing code style of the file being edited when
  it diverges slightly from this document. Consistency within a file is more
  important than global uniformity.
- AI **MUST** write tests for new code.
- AI **MUST** pause for review before `git commit`. The pause includes
  (1) a recap of the diff and (2) a test coverage review listing what
  is well covered, gaps worth filling (with rationale), and gaps
  deliberately skipped. User agreement to a plan is not a commit
  signal.

