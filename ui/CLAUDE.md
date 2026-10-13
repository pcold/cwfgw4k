# Frontend Code Style Guide (UI)

This guide covers the React + TypeScript frontend under `ui/`. The
language-agnostic rules (Guiding Principles, Hygiene Rules, Naming, Change
Scope, Working with AI Assistants) live in the root [`.claude/CLAUDE.md`](../.claude/CLAUDE.md)
and apply unchanged here.

Stack: Vite + React 19 + TypeScript with strict mode. TanStack Query for
server state. Tailwind v4 for styling. Vitest + Testing Library for tests.
The same guiding principles apply — type-system-first, explicit over implicit,
immutable by default, readable before clever.

This document uses **MUST**, **MUST NOT**, **SHOULD**, **SHOULD NOT**, and
**MAY** in the RFC 2119 sense. Deviations from **MUST** rules are defects.
Deviations from **SHOULD** rules require justification.

---

## Where Logic Lives

The UI is a display layer. The server decides every business rule: what a
finish pays, how a tie splits it, who wins a side bet, where a team stands,
what a roster upload proposes. The UI formats what the API returns, lays it
out, and moves between it.

This isn't a matter of taste. It's about which tests can reach the rule. A
rule computed in the browser is checked only by Vitest, against fixtures
written to match it. And a rule the server also enforces is two copies that
drift. The drift shows up as a button that returns a 409, or a total that
disagrees with the page beside it.

- **MUST** take a derived business fact from the API rather than compute it
  from the rows the API returned. The weekly report's `scope` is the shape to
  copy: `WeeklyReportService.kt` decides each report's scope on the server, and
  `isSeasonReport` in `weeklyReportModel.ts` only reads it.
- **MUST NOT** copy a rule the server already enforces, such as a state
  machine's legal moves or the guard a write checks. The copy is a defect even
  while it agrees with the server.
- **MUST** send typed facts over the wire and write the sentence in the UI.
  Send `scope: 'season'`, not `"Season totals"`. The wording is display. The
  fact under it isn't.
- **MUST** plan a fix that lands in a UI derivation as a move of that
  derivation to the server first. A patch to the browser's copy leaves the fix
  where only Vitest can hold it.
- **MUST NOT** answer a test gap in client-side logic with a browser driver.
  The real gap is that the logic sits in the browser.

What stays in the UI:

- **Presentation.** Labels, tones, headings, and the sentence a typed fact is
  written into. Order for convenience, like alphabetical, is the UI's. Order
  that claims something, like the standings, is the server's.
- **View state and navigation.** The selected league and season, the live
  overlay toggle, which golfer's history is open.
- **Reading input.** Parsing a typed field, like the side-bet rounds an admin
  enters, or checking a field's shape before submit so the user doesn't wait
  on a round trip. That check is a courtesy: the server still refuses whatever
  it must.
- **Sequencing calls the user drives**, such as a roster upload's preview and
  confirm, provided each step's decision comes from the server. A write whose
  meaning depends on two requests both landing belongs in one server write.

---

## TypeScript Rules

- **MUST NOT** use `any`. Use `unknown` and narrow with a type guard, or
  model the value precisely. `any` defeats the entire reason TypeScript
  exists and silently spreads through every expression that touches it.
- **MUST NOT** use `as` casts. Two narrow always-OK forms: `as const`
  for literal narrowing, and `value satisfies T` for type-checking
  without widening. Casting hides the bugs that strict mode would
  otherwise catch — when you reach for `as`, restructure or add a
  type guard instead.

  Two boundary exceptions where `as` is the least-bad option and
  should stay confined to those boundaries:
  - **JSON deserialization** (`shared/api/client.ts` post-parse):
    `unknown` from `JSON.parse` flows into `as T` once at the public
    function boundary. The API contract IS the type. Adopt runtime
    validation (e.g. Zod) before this surface gets larger.
  - **Testing-only DOM narrowing**: `getByLabelText(...) as
    HTMLInputElement` and `container.querySelector(...) as
    HTMLElement` are the conventional Testing Library shape; there
    isn't a cleaner form. Test files only.
- **MUST NOT** use `!` (the non-null assertion operator). Use a guard, an
  early return, or restructure to remove the nullable. Same rationale as
  Kotlin's `!!` rule.
- **MUST** use `import type { ... }` for type-only imports.
  `verbatimModuleSyntax` is on; mixed value/type imports break tree-shaking
  and silently leak runtime references that should have been erased.
- **MUST** model variants as discriminated unions, not enums or string
  unions decorated with optional fields:

  ```ts
  // RIGHT — narrowing follows the discriminator automatically
  type PickMatch =
    | { kind: 'matched'; golferId: GolferId }
    | { kind: 'ambiguous'; candidates: readonly GolferId[] }
    | { kind: 'noMatch' };

  // WRONG — every consumer has to remember which fields go with which kind
  interface PickMatch {
    kind: 'matched' | 'ambiguous' | 'noMatch';
    golferId?: GolferId;
    candidates?: GolferId[];
  }
  ```

- **MUST** exhaustively switch on discriminated unions. Use an
  `assertNever(x: never): never` helper in the default case so adding a
  new variant becomes a compile error instead of a runtime hole.
- **SHOULD** use branded types for domain IDs to prevent crossing them up
  at the type level — same idea as the Kotlin `@JvmInline value class`
  rule:

  ```ts
  type GolferId = string & { readonly __brand: 'GolferId' };
  ```

- **SHOULD** prefer `interface` for object shapes (props, API payloads).
  Use `type` for unions, intersections, and computed types.
- **MUST** declare return types on exported functions. Inference is fine
  inside a function body; it is not fine for a module's public surface.
- **MUST** use `===` / `!==`. Never `==` / `!=`.
- **MUST** mark inputs as `readonly` where they aren't mutated. Arrays
  and tuples crossing function boundaries should be `readonly T[]` so
  callers can't mutate behind your back.
- **MUST NOT** disable lint rules locally with `// eslint-disable` or TS
  errors with `// @ts-ignore` / `// @ts-expect-error` without a one-line
  comment explaining why and a link or ticket if it's a known limitation.
- **SHOULD** prefer `unknown` over `any` at API boundaries (e.g.,
  `JSON.parse` results) and narrow before use.

---

## React Rules

- **MUST** use function components. No class components, ever.
- **MUST NOT** use `React.FC` / `React.FunctionComponent`. Type the props
  inline: `function MyThing(props: MyThingProps) { ... }`. `React.FC`
  forces an implicit `children` prop and has historical baggage.
- **MUST** follow the rules of hooks: only call at the top level, only
  from components or other hooks. Keep the
  `react-hooks/rules-of-hooks` and `react-hooks/exhaustive-deps` lints
  on; never silence them locally without an explanatory comment.
- **MUST NOT** use `useEffect` to derive state. If a value can be
  computed from props or other state, compute it inline (or with
  `useMemo` if profiling shows it matters). The most common React bug
  is a `useEffect` that copies one piece of state into another and then
  forgets to keep them in sync.

  ```ts
  // RIGHT — derive
  const fullName = `${firstName} ${lastName}`;
  const activeItems = items.filter((i) => i.active);

  // WRONG — useEffect copying derivable state
  const [fullName, setFullName] = useState('');
  useEffect(() => {
    setFullName(`${firstName} ${lastName}`);
  }, [firstName, lastName]);
  ```

- **MUST NOT** use `useEffect` to fetch data. Use TanStack Query
  (`useQuery` / `useMutation`). Fetch-in-effect leaks, races, and
  double-fires under StrictMode.
- **MUST NOT** use `useEffect` to reset state when a prop changes. Pass
  a `key` prop instead — React unmounts and remounts the subtree, and
  state resets cleanly.
- **DO** use `useEffect` for genuine side effects: subscribing to a
  non-React event source, opening a websocket, an interval timer. Each
  use must clean up in the returned function.
- **MUST NOT** copy props into state via `useState(prop)`. That state
  silently drifts when the prop changes. Either derive, `key`-reset the
  component, or keep the prop as the source of truth.
- **MUST** use stable keys on list items — the item's domain ID, not
  its array index, unless the list is permanently static. Index keys
  cause state mismatches when items are reordered or removed.
- **MUST NOT** mutate state directly. Always produce new objects /
  arrays via spread, `map`, `filter`, etc. Mutation in place won't
  trigger a re-render and is one of the more confusing bug shapes.
- **SHOULD NOT** reach for `useMemo` / `useCallback` by default. They
  cost more than they save unless profiling shows a real render
  bottleneck or you're feeding a memoized child. Premature memoization
  is its own bug category — stale closures over the memoized callback
  are a frequent cause.
- **SHOULD** prefer composition (children, slots) over a sprawling prop
  surface. Conditional rendering inside a parent and explicit children
  beats a `variant: 'a' | 'b' | 'c'` prop with branching.
- **SHOULD** keep components small and single-purpose. If render logic
  exceeds ~80 lines, extract subcomponents.

---

## State Management

- **MUST** classify state by lifetime, and pick the matching tool:
  - **Server state** (anything that came from the API): TanStack Query.
    Never store API responses in `useState` or Context — caching,
    revalidation, retries, and dedup are why we have a server-state
    library.
  - **Local UI state** (open/closed, hovered, active tab, current
    input): `useState`.
  - **Cross-component shared state that is not server data** (auth
    status, selected league/season): `useContext` with a typed provider.
- **MUST NOT** introduce Redux, Zustand, Jotai, or any other
  client-state library without explicit discussion. Context + Query
  covers everything this app needs.
- **MUST** type the context value precisely and throw from the hook if
  used outside the provider — silent `undefined` is worse than a clear
  error:

  ```ts
  const ctx = useContext(LeagueSeasonContext);
  if (!ctx) {
    throw new Error('useLeagueSeason must be used inside LeagueSeasonProvider');
  }
  return ctx;
  ```

- **SHOULD** keep Context providers small and focused. One provider
  per concern (auth, selected season) — don't pile unrelated state into
  a god-context.

---

## Data Fetching (TanStack Query)

- **MUST** route every API call through the typed `api` client in
  `src/api/client.ts`. No direct `fetch` calls in components.
- **MUST** use a stable, hierarchical query key structured by entity:

  ```ts
  useQuery({
    queryKey: ['weeklyReport', seasonId, tournamentId, { live }],
    queryFn: () => api.weeklyReport(seasonId, tournamentId, { live }),
  });
  ```

  Hierarchy lets a write invalidate everything below it:
  `queryClient.invalidateQueries({ queryKey: ['weeklyReport', seasonId] })`.
- **MUST** invalidate (or `setQueryData`) the affected query keys after
  a mutation. Don't rely on refetch-on-window-focus to mask staleness —
  that fixes one user, not the next page navigation.
- **MUST** centralize loading / error rendering in a shared component
  (today: `<QueryState>`). Don't reimplement the three-state ternary in
  every page.
- **MUST NOT** call `useQuery` conditionally. Use the `enabled` option
  to gate the fetch instead.
- **SHOULD** colocate query keys and fetcher functions per entity so
  invalidation and prefetching can reuse the same constants.

---

## Forms and Inputs

- **MUST** use controlled components — read the value from state, write
  it back through `onChange`. `ref`s only when interacting with the DOM
  imperatively (focus, scroll, file inputs).
- **MUST NOT** introduce a form library (Formik, React Hook Form) for
  the size of forms in this app. Hand-rolled controlled inputs plus a
  validation function are sufficient.
- **MUST NOT** stash event objects in state. Copy the field you need.
- **SHOULD** validate on submit and on blur, not on every keystroke,
  unless the field has a real-time correctness signal (password
  strength, search-as-you-type).

---

## Styling (Tailwind)

- **MUST** use Tailwind utility classes inline. No CSS modules, no
  styled-components, no Emotion.
- **SHOULD** extract a small reusable component the third time the same
  class string appears. Don't pre-extract on the second occurrence — a
  `<Button>` shouldn't exist until there's a real third caller.
- **MUST** keep palette and spacing consistent. Muted text is `gray-400`,
  errors are `red-400`, primary text is `white` on dark backgrounds. New
  colors get added to a shared place, not sprinkled per page.
- **SHOULD** prefer Tailwind's responsive prefixes (`md:`, `lg:`) over
  custom media queries.

---

## Testing (Vitest + Testing Library)

- **MUST** test behavior, not implementation. Render, interact via
  `userEvent`, query by accessible role / label / text, assert on what
  the user sees.
- **MUST** use `userEvent` over `fireEvent` for any user interaction.
  `fireEvent` skips the events a real browser would also fire (focus,
  keyup, etc.) and lets bugs through.
- **MUST NOT** query by `data-testid` unless there is genuinely no
  accessible selector available. Test IDs couple tests to DOM structure
  instead of the user-visible contract.
- **MUST** mock at the API client boundary (`vi.mock('@/api/client', …)`).
  Don't mock `fetch` — that couples tests to the transport.
- **MUST** wrap component tests in the shared `renderWithProviders`
  helper so Query, Router, and contexts are set up consistently. The
  auth context is opt-in (`{ withAuthProvider: true }`) because it mounts
  the real `AuthProvider`, whose `auth/me` bootstrap the test then has to
  mock; a test that stubs `useAuth` outright leaves it off.
- **MUST NOT** test internal state (`useState` values) or call hooks in
  isolation with `renderHook` unless the hook is genuinely a public
  API. Test what renders.
- **SHOULD** name tests as full sentences describing scenario and
  outcome: `it('disables submit while the request is in flight', …)`.

---

## File Organization

- **MUST** organize `src/` by feature/domain, not by technical layer —
  the same rule the Kotlin code follows. Each feature owns its page(s),
  sub-components, hooks, model logic, and types in one folder:

  ```
  ui/src/
    features/
      scoreboard/   ScoreboardPage + View + scoreboardModel + tests
      rankings/     PlayerRankingsPage + View + RankingsChart + rankingsModel
      admin/        AdminPage + all admin sections + tests
      auth/         AuthContext, LoginModal, useAuth
      ...
    shared/
      api/          client, types — cross-feature HTTP
      components/   genuinely cross-feature primitives (QueryState)
      util/         pure cross-feature helpers (money, tournament)
      test/         renderWithProviders + test-only fixtures
    App.tsx
    main.tsx
  ```

  Touching one feature should mean opening one folder. Cross-feature
  primitives only land in `shared/` once a second feature actually
  needs them — moving a component from `features/x/` to `shared/` on
  the second user is the right time, not the first.

- **MUST NOT** create generic `pages/`, `components/`, or `hooks/`
  buckets at the top level. They become catch-alls and accelerate the
  drift `features/` exists to prevent.
- **MUST** export one component per file. Default export for the
  primary type when the file is named after it (`AdminPage.tsx`
  default-exports `AdminPage`); named exports for everything else
  (helpers, types, secondary components in the same file).
- **MUST** use the `@/` path alias for cross-folder imports. Relative
  paths only for siblings in the same directory.
- **SHOULD** group imports as: external packages → `@/` aliases →
  relative.
- **MUST NOT** create `utils.ts` or `helpers.ts` catch-all files. Name
  the file for its purpose (`tournament.ts`, `money.ts`).
- **MUST** colocate tests next to source as `Foo.test.tsx`.

---

## Build and Verification

- **MUST** keep `cd ui && npm run typecheck` clean before opening a PR
  for any UI change.
- **MUST** keep `cd ui && npm test` green. See root
  [`.claude/CLAUDE.md`](../.claude/CLAUDE.md) → Verification.
- **MUST NOT** lower the `vitest` coverage thresholds in
  `vite.config.ts` to make a build pass. Add tests instead.

---

## What We Do Not Use (UI)

Conscious exclusions. Do not introduce without discussion:

- **Redux / MobX / Zustand / Jotai / Recoil** — Context + TanStack Query
  cover everything this app needs.
- **CSS Modules / styled-components / Emotion** — Tailwind only.
- **Formik / React Hook Form** — too heavy for the forms in this app.
- **Lodash / Underscore / Ramda** — the JS standard library plus a small
  inline helper covers the use cases. If a single utility is genuinely
  needed, copy or write it; do not import the package.
- **Class components and HOCs** — function components and hooks only.
- **Moment.js** — use `Intl` and the date primitives, or
  `date-fns` if a real need shows up.
- **`React.FC`** — see React rules; type props inline instead.
