# CWFGW4K Architecture

A fantasy PGA Tour league tracker for one league, the Castlewood group. Each season the teams hold their draft
outside the app. An admin then uploads the rosters and the tournament schedule, and finalizes each tournament
once ESPN reports it finished. The app turns the results into payouts, the weekly zero-sum ledger, the late-row
side bets and the season standings. It overlays ESPN's live leaderboard while a tournament is in play.

This document is the architectural reference: the domain, the persistence rules, the tournament lifecycle,
the payout rules and the HTTP surface. It does not cover the code-style contract (see
[`.claude/CLAUDE.md`](../.claude/CLAUDE.md), [`src/CLAUDE.md`](../src/CLAUDE.md), [`ui/CLAUDE.md`](../ui/CLAUDE.md))
or local setup (see [`DEV.md`](DEV.md)). Per-field wire shapes live in the Kotlin DTOs, not here.

Every rule below names the code that implements it and, where one exists, the spec that pins it. When this
document and the code disagree, the code is right and this document has a bug.

---

## Table of Contents

1. [Stack](#1-stack)
2. [Domain Map](#2-domain-map)
3. [Conventions](#3-conventions)
4. [Persistence](#4-persistence)
5. [Transactional Invariants](#5-transactional-invariants)
6. [State Machines](#6-state-machines)
7. [Scoring and Payouts](#7-scoring-and-payouts)
8. [ESPN Integration](#8-espn-integration)
9. [HTTP API Surface](#9-http-api-surface)
10. [Package Layout](#10-package-layout)
11. [Deployment](#11-deployment)
12. [Open Questions](#12-open-questions)

---

## 1. Stack

| Layer | Choice |
|---|---|
| Language | Kotlin 2.4 (language version 2.2) on JVM 21 |
| HTTP | Ktor 3.6 server on Netty; Ktor CIO client for ESPN |
| JSON | kotlinx.serialization |
| Database | PostgreSQL; jOOQ 3.21 for queries, Flyway 13 for migrations, HikariCP for pooling |
| Caching | Postgres `UNLOGGED` table for responses; Caffeine in-process for ESPN |
| Auth | Signed session cookie, bcrypt password hashes |
| Logging | logback + kotlin-logging, one `key=value` line per event |
| Tests | Kotest (including property tests), Testcontainers Postgres |
| Lint and coverage | ktlint, detekt with the repo's own `detekt-rules` subproject, Kover |
| UI | React 19, React Router 8, TanStack Query 5, Tailwind 4, TypeScript 6, Vite 8, Vitest |
| Build | Gradle; the node-gradle plugin builds the UI into the fat jar |
| Runtime | One fat jar (`cwfgw4k-all.jar`) in a Temurin 21 JRE image on Cloud Run |

Versions are pinned in [`gradle/libs.versions.toml`](../gradle/libs.versions.toml) and `ui/package.json`.

`db/schema.sql` is a generated dump of the migrated schema (`./gradlew updateSchemaDump`). `check` fails when
it drifts from the migrations, so the file is a reliable map of the current tables.

## 2. Domain Map

```
League
 └─ Season (year, number, rules)
     ├─ Rules: payout per place, tie floor, side-bet rounds, side-bet amount
     ├─ Tournament (dates, payout multiplier, status, ESPN event id, team-event flag)
     │   ├─ TournamentResult (golfer, position, score to par, round scores, pair key)
     │   └─ PlayerOverride (ESPN competitor -> golfer)
     ├─ Team (owner, name)
     │   └─ RosterEntry (golfer, draft round, ownership %)
     └─ FantasyScore (team x tournament x golfer: position, ties, base payout, share)
Golfer (global; ESPN/PGA player id)
User (username, role admin | user)
```

- **League.** One row in practice. Seasons hang off it.
- **Season.** Identified by `season_year` and `season_number` (when the create request names none, the next free
  number, from `SeasonRepository.nextSeasonNumber`). Owns its rules: the payout table and the side-bet rounds live
  in the side tables `season_rule_payouts` and `season_rule_side_bet_rounds`; the tie floor and side-bet amount are
  columns. `max_teams` is stored and returned but nothing enforces it; the team count is whatever the roster upload
  creates.
- **Tournament.** A season's schedule entry. `pga_tournament_id` is the ESPN event id once linked.
  `payout_multiplier` (default 1) scales every payout for that event. `is_team_event` marks two-player events
  such as the Zurich Classic. `week` is the display label from the schedule upload.
- **Team and roster.** Teams belong to one season. A roster entry puts a golfer on a team with a `draft_round`
  and an `ownership_pct`; two teams can each own a share of one golfer.
- **Golfer.** Global across seasons, unique by `pga_player_id`. A golfer imported from an ESPN team entry has
  no PGA id.
- **Side bets.** Not stored. Derived in the report from the fantasy scores of each team's pick in the season's
  side-bet rounds.
- **Reports.** Not stored. The weekly report, standings, player rankings and golfer history are assembled
  from persisted scores on each request, then cached (see [Response cache](#response-cache)).

## 3. Conventions

### URL prefix and auth

Every API route sits under `/api/v1` (`App.kt`). Anything else serves the bundled UI: `/`, `/assets/{file}`,
`/static/{file}`, then a fallback that returns `index.html` for client routes. An unmatched path whose first
segment is `api` answers 404, never the UI shell (`http/StaticRoutes.kt`).

Auth is a session cookie, `cwfgw_session`: HttpOnly, SameSite=Lax, Secure unless
`AUTH_COOKIE_SECURE=false` (local http), HMAC-signed with
`AUTH_SESSION_SECRET`, holding only the user id. The user is re-read on every request, so a role change or
deletion takes effect at once (`users/SessionAuth.kt`). Every data write, and the admin-only reads,
call `requireAdmin` (`users/AdminGate.kt`): 401 with no session, 403 for a signed-in non-admin, plus a WARN
`cwfgw4k.forbidden`.

Failed logins are throttled per submitted username and per client address (`users/LoginThrottle.kt`). Once
`LOGIN_THROTTLE_MAX_FAILURES` (5) have failed inside `LOGIN_THROTTLE_WINDOW_SECONDS` (900), the next attempt
gets 429 before its password is checked. Counts live in memory, per instance. A bad value refuses boot.

An admin account is seeded at boot from `AUTH_ADMIN_USERNAME` / `AUTH_ADMIN_PASSWORD` only when the users
table is empty. Setting one without the other, or a blank `AUTH_SESSION_SECRET`, refuses boot
(`config/ConfigViolation.kt`).

### Error responses

Domain code throws a `DomainError`; `http/ErrorHandling.kt` maps it to a status and a `{"error": "..."}` body.

| Error | Status |
|---|---|
| `Validation`, malformed body or parameter | 400 |
| `Unauthorized` | 401 |
| `Forbidden` | 403 |
| `NotFound` | 404 |
| unsupported content type | 415 |
| `Conflict` | 409; also a create that lost a unique-key race |
| `Refused` | 409, body `{"refusal": "<code>", "error": "..."}` |
| Postgres lock timeout (`55P03`) | 409 "another change is being saved here; try again", plus WARN `cwfgw4k.db.lock_timeout` |
| `Unprocessable` | 422 |
| `TooManyRequests` | 429 with `Retry-After`; raised by the login throttle |
| `BadGateway` (ESPN unreachable) | 502 |
| anything else | 500 |

A `Refused` carries a stable code the UI branches on: `tournament_out_of_order`, `tournament_start_date_changed`,
`tournament_not_linked`, `tournament_finalized`.

### JSON, IDs and parameters

- `appJson` (`http/AppJson.kt`) is snake_case on the wire, ignores unknown keys, and encodes defaults.
- Sealed types serialize with a `type` discriminator.
- IDs are value classes (`SeasonId`, `TournamentId`, ...). A path id that is not a UUID answers 400
  "invalid season id" and the like.
- Typed optional query parameters go through `optionalQueryParam` (`http/QueryParams.kt`): absent means
  absent, and a value that fails to parse answers 400 rather than being ignored. Free text such as the golfer
  `search` is read directly, and a blank value counts as absent.
- League money (payouts, rules, scores) is `BigDecimal` end to end, stored as `NUMERIC`. ESPN's purse and
  earnings figures are whole dollars in `bigint`.
- Tournament status is `upcoming`, `in_progress` or `completed` on the wire and in the database.

### Logging and compression

Each request logs one line, `cwfgw4k.request method= route= status= duration_ms=`. The route is normalized so
it groups: UUID segments become `:id`, numeric segments `:n`, and anything under `/assets/` collapses to
`/assets/*` (`http/RequestLogging.kt`). Other structured lines share the `cwfgw4k.<area>` prefix
(`cwfgw4k.db.pool`, `cwfgw4k.cache`, `cwfgw4k.espn`).

Responses outside `/api` that are `text/*`, JavaScript or SVG are gzip/deflate-compressed above 1 KiB. API
responses are never compressed: they reflect request input next to the session cookie, which is the BREACH
precondition (`http/Compression.kt`).

## 4. Persistence

### Schema overview

Tables in schema `cwfgw4k`, as dumped in `db/schema.sql`:

| Table | Holds | Notable constraints |
|---|---|---|
| `leagues` | the league | |
| `seasons` | year, number, tie floor, side-bet amount | defaults: `tie_floor` 1, `side_bet_amount` 15, `max_teams` 10 |
| `season_rule_payouts` | payout per place | unique `(season_id, position)` |
| `season_rule_side_bet_rounds` | side-bet rounds | unique `(season_id, round)`; `round` within 1–8, the draft's rounds |
| `tournaments` | schedule, status, multiplier | `pga_tournament_id` unique; `status` default `upcoming`; `payout_multiplier` `NUMERIC(6,4)` default 1 |
| `tournament_results` | one row per golfer per tournament | unique `(tournament_id, golfer_id)` |
| `tournament_player_overrides` | ESPN competitor -> golfer | PK `(tournament_id, espn_competitor_id)` |
| `teams` | a season's teams | unique `(season_id, team_name)` |
| `team_rosters` | golfer on a team | unique `(team_id, golfer_id, acquired_at)`; `ownership_pct` `NUMERIC(5,2)` default 100 |
| `golfers` | golfers | `pga_player_id` unique |
| `fantasy_scores` | a team's payout for a golfer in a tournament | unique `(season_id, team_id, tournament_id, golfer_id)` |
| `users` | accounts | `role` default `user` |
| `cache`, `cache_generation` | response cache | `cache` is `UNLOGGED` |

Deleting a season cascades to its teams, rosters, tournaments, results and scores. Flyway migrates at boot.

### Transactor

`db/Transactor.kt` is the only way to a connection. It has three entry points, and the choice is a statement
about the block:

- **`read`** runs the block in one `REPEATABLE READ READ ONLY` transaction: every query sees one snapshot.
  Every report gather is one `read`, so a report never mixes two finalizes.
- **`update`** runs the block in one read-write transaction at the default isolation. It commits on return and
  rolls back on any throw. It sets `lock_timeout` to 5 s, longer than Postgres's 1 s `deadlock_timeout`, so a
  deadlock is detected before a waiter gives up; a timeout surfaces as the 409 above.
- **`get`** runs the block on one connection in auto-commit: no snapshot, and writes are not atomic. It exists
  for single-statement lookups.

Blocks are plain (non-suspending) functions. They run on `Dispatchers.IO.limitedParallelism(maxPoolSize)`,
so coroutines queue for a pool slot instead of parking threads inside Hikari. A block that takes 3 s or
more, queue wait included, logs WARN `cwfgw4k.db.slow_tx kind=<read|get|update>`. The Postgres driver bounds
connection setup at 10 s and a single read at 30 s, so a stuck socket fails fast instead of hanging until
Cloud Run's timeout (`db/Database.kt`). Pool occupancy logs every 30 s as `cwfgw4k.db.pool`.

### WriteTransactionContext

Only `update` provides a `WriteTransactionContext` (`db/TransactionContext.kt`). Every repository write, and
every read that takes a row lock, declares it as a context parameter. Calling a write from inside `read` or
`get` is therefore a compile error, not a runtime surprise.

### Response cache

The report routes cache their responses in the `cache` table (`cache/RequestCache.kt`):

- The key is the deploy revision (`K_REVISION`) plus the request URI, so a deploy starts cold.
- Entries live `CACHE_DEFAULT_TTL_SECONDS` (300); a sweeper deletes expired rows every
  `CACHE_SWEEP_INTERVAL_SECONDS` (300).
- Fills are single-flight per key: concurrent misses share one loader. A loader that fails stores nothing.
- `cache_generation` is a counter bumped on every clear. A fill that started before a clear and finishes after
  it stores nothing, so a stale report cannot outlive the write that invalidated it.

Pinned by `RequestCacheSpec`.

Invalidation is coarse on purpose. Any non-GET/HEAD/OPTIONS request under `/api/v1` by an admin clears the
whole cache, whether it succeeded or failed (`cache/ResponseCacheInvalidation.kt`). A failed clear logs WARN
`cwfgw4k.cache event=uncleared`. Pinned by `ResponseCacheInvalidationSpec`.

Report routes skip the cache only when the request asks for `live=true` and the season has a live candidate
(see [Live overlay](#live-overlay)); otherwise the live and non-live forms are both cached.

## 5. Transactional Invariants

Each of these runs in exactly one `tx.update`, so it lands whole or not at all:

- **Season create** writes the season row and, when the request carries rules, both rules tables
  (`SeasonRepository.kt`). A season created without rules reads back the defaults.
- **Finalize** checks ordering, imports ESPN results and computes every fantasy score. A scoring failure throws and
  rolls back the import with it (`TournamentOpsServiceFinalizeRollbackSpec`). The ESPN fetch happens *before* the
  transaction, so no connection is held across the network call.
- **Reset** checks no later tournament is completed, deletes the tournament's scores and results, and sets it
  back to `upcoming`.
- **Season clean-results** wipes every score and result in the season and returns every tournament to
  `upcoming`.
- **Roster confirm** creates every team, golfer and roster entry in the upload together (`AdminService.kt`).
- **Player-override write** re-checks the tournament's link and status inside the same transaction as the
  write, so it cannot race a finalize (`TournamentLinkService.kt`).

A create that loses a race on a unique key gets a typed outcome, not a 500. `Transactor.updateOrOutraced`
(`db/OutracedWrites.kt`) turns a 23505 on the one constraint it names into a typed error and rethrows anything
else: 409 from the golfer POST and roster confirm, a skipped entry in schedule confirm, a log line in the boot
admin seed. The finalize import creates golfers with `ON CONFLICT DO NOTHING` instead, since a 23505 would abort
the finalize.

Not atomic, deliberately or by default:

- **Schedule confirm** creates each tournament in its own transaction; a failure part way leaves the earlier
  entries. An entry already linked to the same ESPN event is skipped, so re-running the confirm is safe.

## 6. State Machines

### Tournament status

```
            finalize (ESPN says completed)
 upcoming ───────────────────────────────▶ completed
    ▲                                          │
    └──────────── reset / clean-results ───────┘

 in_progress: settable only through PUT /tournaments/{id}
```

`tournaments/TournamentStatus.kt` defines the three values. No application code moves a tournament to
`in_progress`; only an admin's `PUT` sets it, and nothing requires it. "Is it live?" is decided by dates, not
by this status (see [Live overlay](#live-overlay)).

**Finalize** (`TournamentOpsService.finalizeTournament`):

1. Read the tournament. Fetch ESPN's scoreboard for its start date, outside any transaction. ESPN unreachable
   answers 502.
2. In one `update`, re-read the tournament under its season's lock and then its own, so a finalize or reset of
   any tournament in the season waits, and one deleted during the fetch answers 404. Refuse
   `tournament_start_date_changed` if its start date moved during the fetch, and `tournament_out_of_order` if an
   earlier tournament in the season is still `upcoming` or `in_progress`.
3. Import results. A linked tournament imports only its own ESPN event (a no-op if the scoreboard lacks it). An
   unlinked one locks, then imports into, each tournament that a completed event on that scoreboard matches by
   ESPN id.
4. The tournament becomes `completed` only if ESPN marks the event completed; the import also syncs
   `is_team_event` (`EspnService.syncTournamentFromEventIn`).
5. Compute and upsert fantasy scores ([§7](#7-scoring-and-payouts)).

Re-finalizing a completed tournament is not blocked; it re-imports and upserts the scores again.

**Reset** refuses when a later tournament in the season is completed, so results are always unwound newest
first. **Season clean-results** resets the whole season at once.

Pinned by `TournamentOpsServiceSpec`, `TournamentOpsServiceFinalizeRollbackSpec`,
`TournamentOpsServiceResetSpec`, `TournamentFinalizeRaceSpec` and `TournamentSiblingRaceSpec`.

## 7. Scoring and Payouts

Payouts are the app's most important behavior. The arithmetic is in `scoring/PayoutTable.kt`, a pure object
pinned by example and property tests in `PayoutTableSpec`. `scoring/ScoringService.kt` applies it per roster
entry inside finalize and persists `fantasy_scores`.

All amounts are dollars as `BigDecimal`. Payout divisions (tie averages, team-event halves, ownership shares)
round `HALF_UP` to four decimal places; a side-bet pool split rounds to cents.

### Rules and their defaults

A season's rules (`SeasonRules`, `seasons/SeasonModels.kt`) default to:

| Rule | Default |
|---|---|
| Payout by place, 1st–10th | $18, $12, $10, $8, $7, $6, $5, $4, $3, $2 |
| Tie floor | $1 |
| Side-bet rounds | 5, 6, 7, 8 |
| Side-bet amount | $15 |

The payout table's length is the number of paying places. An admin sets the rules when creating a season;
`GET /seasons/default-rules` returns these defaults to prefill the form.

### Per-golfer payout

1. **Place is the golfer's real finish across the whole field.** A rostered golfer finishing outside the paying
   places, or without a position, earns nothing (`ScoringService.scoreGolferForTeam`; `ScoringServiceSpec`
   "payout zone only"). An undrafted golfer still occupies his place, and his payout goes to no team.
2. **Ties average the places they span.** `numTied` golfers at position *p* share the mean of places
   *p* … *p*+`numTied`−1, counting places past the table as $0. T4 with three tied averages ($8+$7+$6)/3 = $7
   (`PayoutTable.tieSplitPayout`; spec: ties, tail tie).
3. **The tournament multiplier scales payouts and the floor.** Base payout and floor are both multiplied by the
   tournament's `payout_multiplier` (spec: multiplier; `ScoringServiceSpec` multiplier). By league convention
   an admin sets 2 on the four majors, The Players Championship and the Tour Championship; no code knows which
   events those are.
4. **Team events count pairs, not golfers.** ESPN lists both partners at the pair's position, so tied units are
   `numTied / 2` (at least 1), and each partner gets half the pair's payout (`PayoutTable.tiedUnits`; spec:
   team event cases).
5. **Shared ownership splits the payout.** A golfer owned by several teams pays each its `ownership_pct` of the
   base, rounded `HALF_UP` to four places, except the smallest stake, which takes what is left. The shares sum
   exactly to the base even when the percentages do not total 100, which nothing enforces. A golfer with a single
   owner pays that team the full base, whatever its percentage (`PayoutTable.splitOwnership`; spec:
   `splitOwnership` cases and property).
6. **The floor.** Any positive amount below `tie_floor × multiplier` is raised to it: a tie average, a team
   partner's half, or an ownership share. Zero stays zero. The floor is a guarantee, not a redistribution, so a
   floored split can pay out more than the base (spec: tail-tie floor, 4 × 25% of $2, floor on both owners).

### Weekly ledger

Each team's `topTens` is the sum of its golfers' payouts for the tournament. The league is zero-sum: every
team pays every other team's winnings, so

`weekly +/- = topTens × numTeams − totalPot`

where `totalPot` is all teams' payouts summed. Finalize computes this (`ScoringService.buildWeeklyResult`;
`ScoringServiceSpec` zero-sum example), but the ledger the report shows is recomputed from persisted scores
(`WeeklyReportService.buildReportTeamColumn`). There `topTens` sums the eight draft-round cells, one pick per
round, while `totalPot` sums every score, so the two agree only while each team has exactly one pick in each of
rounds 1–8. Roster confirm refuses any other roster (`AdminService.findRosterProblems`;
`AdminServiceConfirmRosterRoundsSpec`). Standings rank teams by their season total with sequential ranks; equal
totals still get distinct ranks (`ReportHelpersSpec`).

### Late-row side bets

For each side-bet round, every team's pick from that draft round races for the season
(`reports/ReportHelpers.kt`, `WeeklyReportService.buildSideBetPerRound`; `ReportHelpersSpec`,
`WeeklyReportServiceSpec`):

- A pick's score is the sum of its fantasy-score payouts over completed tournaments (through the selected
  tournament on a weekly report, up to `through` on rankings), plus the live tournaments in a live report. A
  team with two picks in one round races with only one of them.
- While every pick in the round is at $0 the bet has no outcome.
- The pick(s) at the maximum win; everyone else loses. Each loser pays the side-bet amount, so the pool is
  `amount × (numTeams − winners)`.
- Winners split the pool evenly; when it does not divide to the cent, the last winner by team id takes the
  remainder.
- A side-bet amount of 0, or an empty round list, turns the bets off for the season. A season created without
  rules gets rounds 5–8 written as rows; no rows reads back as no rounds.

### Report assembly

Reports (`reports/WeeklyReportService.kt`) read persisted `fantasy_scores` of completed tournaments only, and
gather them in one `tx.read`; a live request adds the route's live-candidate check and one more read for the
overlay's context. The weekly grid shows eight draft rounds per team, and every golfer who finished in a fixed top
10 (independent of the payout table's length), drafted or not. Golfer history recomputes each finish's payout from
`tournament_results` at full ownership, which is what the golfer earned rather than what any one team took.

## 8. ESPN Integration

### Client and cache

`espn/EspnClient.kt` calls ESPN's public scoreboard and calendar at
`site.web.api.espn.com/apis/site/v2/sports/golf/pga/scoreboard`; the other ESPN host answers this app with 403.
`CachingEspnClient` wraps it in a per-JVM Caffeine cache: entries live `ESPN_SCOREBOARD_CACHE_TTL_SECONDS` (60),
up to `ESPN_SCOREBOARD_CACHE_MAX_SIZE` (256). Concurrent misses share one request, and a failed request is
evicted rather than cached (`CachingEspnClientSpec`). Each load logs
`cwfgw4k.espn event=hit|miss|error endpoint=scoreboard|calendar`; a caller that joins an in-flight load logs
nothing.

### Parsing

- **Team entries.** A competitor with `type == "team"`, or a `/` in its name, becomes one row per partner,
  sharing a `pair_key` of `team:<id>`.
- **Positions.** Competitors are sorted by score to par, then ESPN's order. Equal scores share a position, and
  the next position skips past them, counting a pair as one. Once the event is completed a tie for the lead is
  broken by ESPN's order (the playoff result): every playoff entrant gets its own place, so a three-way playoff
  pays 1st, 2nd and 3rd, not a shared T2. Ties below the lead stay shared. A competitor with no score sorts
  last, each in its own position.

Pinned by `EspnClientSpec`.

### Golfer matching

Import, finalize, the link editor and the live preview resolve each competitor to a golfer in this order
(`EspnService.kt`): a manual player override for the tournament; the ESPN player id (not for team partners); a
unique normalized full-name match (not for team partners, whose name is a surname alone); a unique surname match,
or on a tie, for a partner only, the rostered golfer among the candidates. A competitor that matches nothing is
created as a new golfer. Names are compared normalized for case, accents and hyphens, on the "first last"
concatenation rather than the stored columns; the surname is its last word, kept whole if hyphenated and past a
"Jr"/"III" suffix (`golfers/GolferMatching.kt`, which roster upload shares). A new golfer's name is split at its
last space, and a single word is stored as the last name. Overrides are managed per tournament through the tournament-link routes and are refused once the
tournament is finalized.

### Live overlay

A tournament is a **live candidate** when it is not completed and its start date is no later than three days after
today in `America/New_York`, so an unfinalized tournament that started earlier stays a candidate
(`tournaments/TournamentPredicates.kt`; `TournamentPredicatesSpec`). The window shows the field and tee times from
the Monday before a Thursday start.

When a report is requested with `live=true`, `reports/LiveOverlayService.kt` fetches each candidate's
leaderboard, scores it with the same payout rules, and overlays the result on the persisted report without
writing anything. A candidate previews only the ESPN event whose id is its `pga_tournament_id`, the rule finalize
imports by (`findLinkedEvent` in `espn/EspnService.kt`), so an unlinked or stale-id tournament previews nothing
until it is linked. The preview pays a team event by ESPN's flag, which finalize's import writes over the row.
Rankings gain a column per live candidate; player rankings fold in the live payouts. A completed tournament
selected in the weekly report is never overlaid.

**Fallback.** If ESPN does not answer, answers non-200, or answers 200 with a body that does not parse, the
overlay logs a WARN and skips that candidate, so a full outage returns the persisted report unchanged
(`LiveReportEspnOutageSpec`, `CompletedTournamentLiveGuardSpec`). The client reports an unparseable 200 as its
own failure, `MalformedBody`, and never caches it (`EspnClientSpec`). A route that needs the scoreboard answers it
with a 502 as it does an outage (`EspnOutageRoutesSpec`). Finalize is one: it refuses before opening its write
transaction, so nothing is imported (`TournamentOpsServiceSpec`).

## 9. HTTP API Surface

Paths are relative to `/api/v1` unless they start with `/debug`. **Public** needs no session; **Session**
needs any signed-in user; **Admin** needs the admin role (401 without a session, 403 without the role).
Request and response shapes are the `@Serializable` types in each package's `*Routes.kt` and `*Models.kt`.

| Method | Path | Purpose | Auth |
|---|---|---|---|
| POST | `/auth/login` | Check credentials, set the session cookie, return the user; throttled (429) | Public |
| POST | `/auth/logout` | Clear the session cookie (204) | Public |
| GET | `/auth/me` | The signed-in user | Session |
| GET | `/health` | Database probe: 200 ok or 500 degraded | Public |
| GET | `/leagues` | List leagues | Public |
| POST | `/leagues` | Create a league | Admin |
| GET | `/golfers` | List golfers, optional `search` | Public |
| POST | `/golfers` | Create a golfer | Admin |
| GET | `/seasons` | List seasons, optional `league_id`, `year` | Public |
| GET | `/seasons/{id}/rules` | A season's rules | Public |
| GET | `/seasons/default-rules` | Default rules to prefill season create | Admin |
| POST | `/seasons` | Create a season and its rules | Admin |
| DELETE | `/seasons/{id}` | Delete a season and everything under it | Admin |
| POST | `/seasons/{id}/clean-results` | Wipe the season's results and scores, reset its tournaments | Admin |
| GET | `/seasons/{id}/rosters` | Teams with their rosters | Public |
| GET | `/tournaments` | List tournaments, optional `season_id`, `status` | Public |
| PUT | `/tournaments/{id}` | Edit a tournament (name, dates, multiplier, status, ...) | Admin |
| POST | `/tournaments/{id}/finalize` | Import ESPN results and compute scores | Admin |
| POST | `/tournaments/{id}/reset` | Delete results and scores, back to upcoming | Admin |
| POST | `/admin/seasons/{id}/upload/preview` | Match a schedule against ESPN's calendar; writes nothing | Admin |
| POST | `/admin/seasons/{id}/upload/confirm` | Create the previewed tournaments | Admin |
| POST | `/admin/roster/preview` | Parse a plain-text roster and match golfers; writes nothing | Admin |
| POST | `/admin/roster/confirm` | Create the season's teams and rosters | Admin |
| GET | `/admin/tournaments/{id}/competitors` | ESPN competitors for a linked tournament, to pick overrides from | Admin |
| POST | `/admin/tournaments/{id}/player-overrides` | Pin an ESPN competitor to a golfer | Admin |
| DELETE | `/admin/tournaments/{id}/player-overrides/{espnCompetitorId}` | Remove an override | Admin |
| GET | `/seasons/{id}/report` | Season weekly report, optional `live` | Public |
| GET | `/seasons/{id}/report/{tournamentId}` | Weekly report for one tournament, optional `live` | Public |
| GET | `/seasons/{id}/rankings` | Team standings by tournament, optional `live`, `through` | Public |
| GET | `/seasons/{id}/player-rankings` | Golfer earnings across the season, optional `live`, `through` | Public |
| GET | `/seasons/{id}/golfer/{golferId}/history` | One golfer's finishes and earnings in the season | Public |
| GET | `/debug/threads` | JVM thread dump; mounted only when `DEBUG_ENDPOINTS_ENABLED` | Public |

The table is generated from the `get`/`post`/`put`/`delete` calls in `src/main/**/*Routes.kt` (31 under
`/api/v1`, plus `/debug/threads`). A route change updates this table in the same PR.

## 10. Package Layout

```
src/main/kotlin/com/cwfgw/
├─ Main.kt, App.kt, AppServices.kt   boot, plugin install, route wiring, service construction
├─ config/          typed config from application.yaml; boot refuses on violations
├─ db/              Transactor, transaction contexts, Hikari/Flyway setup
├─ http/            JSON, error mapping, query params, request logging, compression, static UI
├─ result/          Result type for expected failures
├─ serialization/   shared serializers (BigDecimal, dates, UUID)
├─ cache/           response cache, invalidation, sweeper
├─ users/           auth routes, session, admin gate, admin seeding
├─ leagues/  seasons/  golfers/  teams/  tournaments/   domain CRUD and season/tournament ops
├─ tournamentLinks/ ESPN competitor list and player overrides
├─ espn/            ESPN client, cache, parsing, import
├─ scoring/         payout math and finalize-time scoring
├─ reports/         report assembly, live overlay, report routes
├─ admin/           schedule and roster upload flows
├─ health/  logging/  debug/   probes, logging setup, thread dump
└─ seed/            dev-only data seeder, its own entry point (`SeedMain`)
```

Each domain package follows one shape: `*Models.kt` (types and DTOs), `*Repository.kt` (jOOQ),
`*Service.kt` (rules and transactions), `*Routes.kt` (HTTP). Services open transactions; repositories take a
transaction context and never open one. Ops on a season or tournament live beside them in `*OpsService.kt`.

`src/main/resources/db/migration` holds the Flyway migrations. `detekt-rules/` is the repo's custom detekt
rule set. The UI lives in `ui/src`, with one folder per screen under `features/` (`admin`, `auth`,
`lateRowBets`, `leagues`, `playerRankings`, `rankings`, `rosters`, `rules`, `scoreboard`, `weeklyReport`) and
shared code in `shared/`.

## 11. Deployment

- **Gate.** `.github/workflows/check.yml` runs `./gradlew check --rerun-tasks` on pull requests and pushes to
  `main`, and `main` requires it to pass. It skips the Gradle run, still reporting success, for a pull request
  whose every changed file is a `.md`, and for a push to `main` whose exact tree an earlier run already passed.
- **Deploy.** `.github/workflows/deploy.yml` starts when Check succeeds on a push to `main`, or by manual
  dispatch with a sha. The job runs in the `production` environment, which holds it for a required reviewer's
  approval. It authenticates with Workload Identity Federation, builds the fat jar, pushes the image to
  Artifact Registry in `us-west1` tagged with the sha and `latest`, deploys Cloud Run service `cwfgw4k`, and
  curls `/api/v1/health`. A started deploy always finishes; a newer run replaces one still waiting in the queue.
- **Runtime.** Cloud Run, 1 GiB and 2 CPU, 20 concurrent requests per instance, 0 to 6 instances, 90 s
  request timeout. The JVM heap is capped at 512 MiB in the image.
- **Database.** Cloud SQL Postgres instance `cwfgw4k-prod`, reached through the Cloud SQL socket factory.
  Each instance runs a pool of 15, so 6 instances stay under the instance's 100 connections.
- **Secrets.** `DB_PASSWORD`, `AUTH_SESSION_SECRET` and `AUTH_ADMIN_PASSWORD` come from Secret Manager.
- **Setup.** `deploy/setup.sh` and `deploy/setup-github-deploy.sh` record the one-time GCP and GitHub setup.

## 12. Open Questions

- **A team with no pick in a side-bet round (CWF-83).** The persisted report and rankings leave such a team out of
  that round's race, yet the pool counts it among the losers, so the round pays out a stake nobody paid. The
  live overlay instead races it at $0 and charges it, so the two views disagree.
- **A shared ESPN-event resolver (CWF-179).** The live preview and the competitors route resolve a tournament's
  ESPN event through `findLinkedEvent` (`espn/EspnService.kt`); finalize applies the same exact-id rule inline
  and does not call it yet.
