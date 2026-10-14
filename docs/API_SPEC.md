# cwfgw4k HTTP API

Canonical reference for the Kotlin/Ktor backend. Every endpoint registered
under `Application.module()` is documented here. The wire shapes match what
the running service emits, not what an upstream port might once have done.

---

## Conventions

### URL prefix

Every API endpoint lives under `/api/v1/`. Static UI assets (when bundled)
live outside that prefix; the SPA fallback never matches `/api/`, so an
unknown API path always returns a JSON 404 instead of the React shell.

### Authentication

Auth is session-based. `POST /api/v1/auth/login` validates a username +
password and sets a `cwfgw_session` cookie (HttpOnly, signed with HMAC).
Auth-gated endpoints require that cookie. Endpoints marked **Auth: Yes**
in the tables below return `401 Unauthorized` without it.

The session secret is mandatory at boot — the process refuses to start if
`AUTH_SESSION_SECRET` is unset or blank. There is no shipped default.

### Error responses

Errors are emitted by `installErrorHandling()` in a uniform shape:

```json
{ "error": "human-readable message" }
```

A business-rule refusal (`Refused`) adds a stable machine-readable code
beside the same `error` key, so a client reading only `error` still shows
a sentence:

```json
{ "refusal": "tournament_out_of_order", "error": "human-readable message" }
```

Status code mappings:

| `DomainError` variant       | Status |
|-----------------------------|--------|
| `NotFound`                  | 404    |
| `Validation`                | 400    |
| `Unprocessable`             | 422    |
| `Conflict`                  | 409    |
| `Refused`                   | 409, with `refusal` |
| `BadGateway`                | 502    |
| `Unauthorized`              | 401    |
| `Forbidden`                 | 403    |
| `TooManyRequests`           | 429, with `Retry-After` in whole seconds |
| Malformed or empty body     | 400    |
| Wrong or no `Content-Type`  | 415    |
| A write's lock wait timed out (SQLSTATE 55P03) | 409; the write rolled back, so retry |
| Anything else (uncaught)    | 500    |

Refusal codes on the wire today:

| `refusal`                           | Raised by |
|-------------------------------------|-----------|
| `tournament_not_linked`             | `GET /api/v1/admin/tournaments/{id}/competitors` |
| `tournament_finalized`              | `POST /api/v1/admin/tournaments/{id}/player-overrides`, `DELETE /api/v1/admin/tournaments/{id}/player-overrides/{espnCompetitorId}` |
| `tournament_out_of_order`           | `POST /api/v1/tournaments/{id}/finalize`, `POST /api/v1/tournaments/{id}/reset` |

Service-layer typed errors (e.g. `TournamentOpsError`, `DraftError`) are
mapped to `DomainError` at the route boundary.

### JSON casing

Request and response bodies use **snake_case** field names on the wire.
The Ktor server installs `kotlinx.serialization` with
`JsonNamingStrategy.SnakeCase`, so a Kotlin field named `payoutMultiplier`
serializes as `payout_multiplier`. Unknown fields are ignored on input,
defaults are encoded on output.

### Sealed-type discriminators

Where a sealed interface is exposed (e.g. `PickMatch`, `GolferAssignment`),
the JSON carries a `type` discriminator (kotlinx.serialization's default).
Variant names are SerialName-tagged; e.g. `PickMatch.Matched` serializes as
`{"type": "matched", ...}`.

### Typed IDs

Every domain ID on the wire — `LeagueId`, `SeasonId`, `TeamId`,
`TournamentId`, `GolferId`, `DraftId`, `DraftPickId`, `UserId` — is a
value class wrapping a `UUID`, sent as the raw UUID string.
Path-parameter parse failures return 400 with `"invalid <name> id"`.

### Query parameter parsing

Optional query parameters use `optionalQueryParam(name, parser)`. If the
parameter is absent, the filter is omitted; if it's present but malformed
(e.g. `?status=garbage` against `TournamentStatus`), the response is 400.
Silent fallback to "no filter" on bad input is deliberately avoided.

---

## Endpoints

### Auth

| Method | Path                  | Auth | Description                             |
|--------|-----------------------|------|-----------------------------------------|
| POST   | `/api/v1/auth/login`  | No   | Validate credentials, set session cookie |
| POST   | `/api/v1/auth/logout` | No   | Clear the session cookie                 |
| GET    | `/api/v1/auth/me`     | Yes  | Return the authenticated user           |

**POST /api/v1/auth/login**

Request: `{ "username": string, "password": string }`. On success the
response sets the `cwfgw_session` cookie and returns the user; on failure
returns 401 with the standard error body.

**GET /api/v1/auth/me**

Returns the `User` shape (`{ id, username, role, created_at }`). The
session cookie's user is resolved via the `Authentication` plugin's
`session<UserSession>` validator; if the underlying user no longer exists
the principal resolves to null and 401 is returned.

### Health

| Method | Path                | Auth | Description                                      |
|--------|---------------------|------|--------------------------------------------------|
| GET    | `/api/v1/health`    | No   | Returns 200 with status `ok` if the DB is reachable, otherwise 500 with `degraded` |

Body shape: `{ "status": "ok" | "degraded", "service": "cwfgw", "database": "connected" | "unreachable" }`.

### Leagues

| Method | Path                   | Auth | Description       |
|--------|------------------------|------|-------------------|
| GET    | `/api/v1/leagues`      | No   | List all leagues  |
| POST   | `/api/v1/leagues`      | Yes  | Create a league   |

### Seasons

| Method | Path                                  | Auth | Description                                                                        |
|--------|---------------------------------------|------|------------------------------------------------------------------------------------|
| GET    | `/api/v1/seasons?league_id=&year=`    | No   | List seasons; filters are optional UUIDs / ints                                    |
| GET    | `/api/v1/seasons/{id}/rules`          | No   | Get the season's `SeasonRules` (payouts, tie floor, side bet rounds + amount)      |
| POST   | `/api/v1/seasons`                     | Yes  | Create a season. Body accepts an optional `rules: SeasonRules` — when supplied, the side tables for payouts + side-bet rounds are populated atomically with the seasons row, and the rules' `tie_floor`/`side_bet_amount` override the top-level fields |
| DELETE | `/api/v1/seasons/{id}`                | Yes  | Delete the season; its teams, rosters, tournaments and draft cascade with it. 204, or 404 for an unknown id |
| POST   | `/api/v1/seasons/{sid}/clean-results` | Yes  | Destructive: wipe all scores/results/standings + revert every tournament to upcoming. Returns deletion counts. |

### Golfers

| Method | Path                                | Auth | Description                                                                |
|--------|-------------------------------------|------|----------------------------------------------------------------------------|
| GET    | `/api/v1/golfers?search=`           | No   | List golfers; `search` matches first or last name                          |
| POST   | `/api/v1/golfers`                   | Yes  | Create a golfer                                                            |

### Rosters

| Method | Path                             | Auth | Description                                                 |
|--------|----------------------------------|------|-------------------------------------------------------------|
| GET    | `/api/v1/seasons/{sid}/rosters`  | No   | Display-shaped view: every team in the season + their picks |

### Drafts

| Method | Path                                                | Auth | Description                                                                          |
|--------|-----------------------------------------------------|------|--------------------------------------------------------------------------------------|
| GET    | `/api/v1/seasons/{sid}/draft`                       | No   | Get the season's draft                                                               |
| GET    | `/api/v1/seasons/{sid}/draft/picks`                 | No   | List all picks made so far                                                           |
| GET    | `/api/v1/seasons/{sid}/draft/available`             | No   | List golfers eligible for the next pick                                              |
| POST   | `/api/v1/seasons/{sid}/draft`                       | Yes  | Create a draft for the season                                                        |
| POST   | `/api/v1/seasons/{sid}/draft/start`                 | Yes  | Transition draft state from `pending` → `in_progress`                                |
| POST   | `/api/v1/seasons/{sid}/draft/initialize?rounds=`    | Yes  | Generate snake-order picks (default 6 rounds)                                        |
| POST   | `/api/v1/seasons/{sid}/draft/pick`                  | Yes  | Make a pick. Body: `{ "team_id": UUID, "golfer_id": UUID }`. Returns `Result<DraftPick, DraftError>` shape — typed errors map to 404/409 at the boundary |

### Tournaments

| Method | Path                                              | Auth | Description                                                                                    |
|--------|---------------------------------------------------|------|------------------------------------------------------------------------------------------------|
| GET    | `/api/v1/tournaments?season_id=&status=`          | No   | List tournaments. `status` is one of `upcoming`/`in_progress`/`completed`. Unknown values → 400 |
| PUT    | `/api/v1/tournaments/{id}`                        | Yes  | Partial update (incl. `status`, `is_team_event`)                                               |
| POST   | `/api/v1/tournaments/{id}/finalize`               | Yes  | Compose ESPN import → score calculation → standings refresh. Refuses out-of-order with 409 `tournament_out_of_order`. |
| POST   | `/api/v1/tournaments/{id}/reset`                  | Yes  | Wipe scores + results, revert status to `upcoming`. Refuses when later tournaments are completed (409 `tournament_out_of_order`). |

### Reports

| Method | Path                                                    | Auth | Description                                                               |
|--------|---------------------------------------------------------|------|---------------------------------------------------------------------------|
| GET    | `/api/v1/seasons/{id}/report?live=`                     | No   | Season-aggregate `WeeklyReport` (`scope: "season"`). `?live=true` overlays in-progress ESPN data (see below) |
| GET    | `/api/v1/seasons/{id}/report/{tournamentId}?live=`      | No   | Single-tournament `WeeklyReport` (`scope: "tournament"`). `?live=true` overlays in-progress ESPN data (see below) |
| GET    | `/api/v1/seasons/{id}/rankings?live=&through=`          | No   | Cumulative team rankings + per-tournament series. `?live=true` overlays in-progress ESPN data; `?through=` is an optional cutoff tournament id (404 if missing) |
| GET    | `/api/v1/seasons/{id}/golfer/{golferId}/history`        | No   | Top-10 finishes for one golfer over the season                            |

`?live=true` triggers `LiveOverlayService` to merge ESPN scoreboard data
on top of the persisted base report. For a single-tournament report it
projects the current leaderboard onto the team grid and refreshes
side-bet payouts; for the season-aggregate report and rankings it folds
each in-progress tournament's projected zero-sum into the running
totals. Selected tournaments whose status is already `completed` skip
the ESPN call entirely — the persisted numbers are immutable so a fresh
pull can't change them. An ESPN failure — a non-200, a timeout, a
connection that fails — is absorbed (logged at WARN) and the response
falls back to the base report.

A report GET is served from the Postgres response cache, keyed on the
deployed revision and the full request URI, for 300 seconds
(`CACHE_DEFAULT_TTL_SECONDS`). That includes a `live=true` request while no
tournament in the season is a live candidate (not `completed`, and starting
within three days or already started): nothing would be overlaid, so it is
cached under its own URI like any other. While one is, a `live=true` request
never reads or writes that cache: the report is rebuilt on every request, and its ESPN
data comes from an in-process cache of ESPN's scoreboard that holds each
date for 60 seconds (`ESPN_SCOREBOARD_CACHE_TTL_SECONDS`), which is the
most a live report can trail ESPN. A failed ESPN fetch is not cached, so
the next `live=true` request asks ESPN again.

Both report endpoints return the same `WeeklyReport` shape; its `scope`
field says which one a body is. Clients must branch on `scope`, never on
the `tournament` block: the season-aggregate report sends
`tournament.status` as `null`, and `status` only ever carries
`upcoming` / `in_progress` / `completed`. The live overlay leaves `scope`
unchanged.

Side bets are settled by the server and described on the report, so a
client never infers them. `side_bet_rules` carries the season's
`amount_per_team` and the draft `rounds` the bet runs on; it is `null`,
and `side_bet_detail` is empty, for a season that stakes nothing, which
is one whose `side_bet_amount` is zero. Each `side_bet_detail` entry
carries its `team_name` and an `outcome` of `"won"` or `"lost"`, or
`null` where the round has settled nothing for that team, as when nobody
has earned in it yet. Every team at the round's highest cumulative has
won, so a tie has several winners and a round every team ties has all of
them at a zero `payout`: read `outcome`, never the sign of `payout`. The
live overlay re-settles `payout` and `outcome` on the projected
cumulative and leaves `side_bet_rules` unchanged.

A report's totals are the server's too. `cash_totals` runs over every
team's `total_cash` and `side_bet_totals` over every team's `side_bets`;
each carries `won` (the sum of the positive amounts), `lost` (the sum of
the negative ones, so zero or negative) and `net` (`won` plus `lost`). A
client displays them and never sums `total_cash` or `side_bets` itself. A
live overlay's totals are those of its overlaid teams.

### Admin

| Method | Path                                              | Auth | Description                                                                                    |
|--------|---------------------------------------------------|------|------------------------------------------------------------------------------------------------|
| POST   | `/api/v1/admin/seasons/{id}/upload/preview`       | Yes  | Pull ESPN's calendar for the date range in the body and return candidate tournaments — no DB writes. Body: `{ "start_date": "YYYY-MM-DD", "end_date": "YYYY-MM-DD" }`. Returns `SeasonSchedulePreviewResult` |
| POST   | `/api/v1/admin/seasons/{id}/upload/confirm`       | Yes  | Create one tournament per operator-kept entry from a prior preview call. Body: `ConfirmSeasonScheduleRequest`. Returns `SeasonImportResult` |
| POST   | `/api/v1/admin/roster/preview`                    | Yes  | Preview a roster TSV/CSV. Body is `text/plain` (the raw roster). Returns `RosterPreviewResult` describing per-pick match status. If any pick can't be matched to an existing golfer the service pulls ESPN's recent active-player pool and persists any newly-discovered athletes (with their `pga_player_id`) before re-matching — so this endpoint is a *write* on the golfers table, not strictly read-only |
| POST   | `/api/v1/admin/roster/confirm`                    | Yes  | Persist an operator-confirmed roster. Body: `ConfirmRosterRequest`. Returns `RosterUploadResult` with deletion counts and persisted teams |

---

## Wire types

Reference for the more complex bodies. Field names below are the Kotlin
property names; on the wire they're snake-cased.

### Common

```kotlin
data class ErrorBody(val error: String)
data class RefusalBody(val refusal: String, val error: String)
```

### User

Returned by `POST /api/v1/auth/login` (200) and `GET /api/v1/auth/me`
(200). Both endpoints respond with 401 + `ErrorBody` when there is no
authenticated session — clients should treat 401 from `/auth/me` as the
"not logged in" signal rather than an error.

```kotlin
enum class UserRole { Admin, User }   // serialized as "admin" / "user"

data class User(
    val id: UserId,
    val username: String,
    val role: UserRole,
    val createdAt: Instant,
)
```

### Tournament

```kotlin
data class Tournament(
    val id: TournamentId,
    val pgaTournamentId: String?,
    val name: String,
    val seasonId: SeasonId,
    val startDate: LocalDate,    // ISO-8601 yyyy-MM-dd
    val endDate: LocalDate,
    val courseName: String?,
    val status: TournamentStatus, // "upcoming" | "in_progress" | "completed"
    val purseAmount: Long?,
    val payoutMultiplier: BigDecimal,
    val week: String?,            // free-form label, e.g. "8A" / "8B"
    val isTeamEvent: Boolean,     // true for Zurich Classic; halves per-partner payout
    val createdAt: Instant,
)
```

### Season + Rules

```kotlin
data class Season(
    val id: SeasonId,
    val leagueId: LeagueId,
    val name: String,
    val seasonYear: Int,
    val seasonNumber: Int,
    val tieFloor: BigDecimal,
    val sideBetAmount: BigDecimal,
    val maxTeams: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class SeasonRules(
    val payouts: List<BigDecimal>, // [18, 12, 10, 8, 7, 6, 5, 4, 3, 2] by default
    val tieFloor: BigDecimal,      // per-player floor when ties span the payout-zone tail
    val sideBetRounds: List<Int>,  // [5, 6, 7, 8] by default
    val sideBetAmount: BigDecimal,
)

// POST /api/v1/seasons body. When `rules` is supplied the repo
// transactionally inserts the seasons row + per-position payouts +
// per-round side-bet entries; rules' tie_floor / side_bet_amount win
// over the top-level flat fields.
data class CreateSeasonRequest(
    val leagueId: LeagueId,
    val name: String,
    val seasonYear: Int,
    val seasonNumber: Int? = null,
    val maxTeams: Int? = null,
    val tieFloor: BigDecimal? = null,
    val sideBetAmount: BigDecimal? = null,
    val rules: SeasonRules? = null,
)
```

### Reports

```kotlin
enum class ReportScope { Season, Tournament }        // wire: "season" | "tournament"

data class WeeklyReport(
    val scope: ReportScope,                          // which endpoint built it; always present
    val tournament: ReportTournamentInfo,            // season scope: id/dates/status/week are null
    val teams: List<ReportTeamColumn>,
    val undraftedTopTens: List<UndraftedGolfer>,
    val sideBetRules: ReportSideBetRules?,           // null: the season stakes nothing on side bets
    val sideBetDetail: List<ReportSideBetRound>,     // one per round in sideBetRules.rounds, same order
    val standingsOrder: List<StandingsEntry>,
    val live: Boolean? = null,                       // populated only by live overlay
    val liveLeaderboard: List<LiveLeaderboardEntry> = emptyList(), // sorted by position; ESPN's field when live and ESPN lists one, else persisted rows
) {
    val cashTotals: WonLostTotals                    // over teams[].totalCash; derived, so a copy recomputes it
    val sideBetTotals: WonLostTotals                 // over teams[].sideBets, likewise
    val weeklyTotals: WonLostTotals                  // over teams[].weeklyTotal, likewise
}

data class LiveLeaderboardEntry(                     // one competitor slot, not one golfer
    val position: Int,
    val scoreToPar: Int?,                            // strokes relative to par; 0 is even
    val players: List<LeaderboardPlayer>,            // one, or a team event's two partners
    val roundScores: List<Int> = emptyList(),        // R1..R4 strokes for the rounds played
    val totalStrokes: Int? = null,
) {
    val rostered: Boolean                            // some player has a team; derived from players
}

data class LeaderboardPlayer(
    val name: String,
    val teamNames: List<String>,                     // every team rostering the player; empty is undrafted
)

data class WonLostTotals(                            // the server builds it only by WonLostTotals.over(amounts)
    val won: BigDecimal,                             // sum of the positive amounts
    val lost: BigDecimal,                            // sum of the negative amounts: zero or negative
    val net: BigDecimal,                             // won + lost
)

data class ReportSideBetRules(
    val amountPerTeam: BigDecimal,
    val rounds: List<Int>,
)

data class ReportSideBetRound(
    val round: Int,
    val teams: List<ReportSideBetTeamEntry>,
)

enum class SideBetOutcome { Won, Lost }              // wire: "won" | "lost"

data class ReportSideBetTeamEntry(
    val teamId: TeamId,
    val teamName: String,
    val golferName: String,                          // last name UPPER-cased; "—" when the team has no pick
    val cumulativeEarnings: BigDecimal,
    val payout: BigDecimal,
    val outcome: SideBetOutcome?,                    // null: nothing settled for this team in the round
)

data class ReportTeamColumn(
    val teamId: TeamId,
    val teamName: String,
    val ownerName: String,
    val cells: List<ReportCell>,    // 8 cells, one per draft round
    val topTenEarnings: BigDecimal,
    val weeklyTotal: BigDecimal,    // weekly +/-, sums to zero across all teams in the report
    val previous: BigDecimal,       // running total before this tournament
    val subtotal: BigDecimal,       // previous + weeklyTotal
    val topTenCount: Int,
    val topTenMoney: BigDecimal,
    val sideBets: BigDecimal,
    val totalCash: BigDecimal,      // subtotal + sideBets; standings rank by this desc
)

data class ReportCell(
    val round: Int,                 // 1..8
    val golferName: String?,        // last name UPPER-cased; null when the slot is empty
    val golferId: GolferId?,
    val positionStr: String?,       // the grid's label: "1", "T3", or a top-ten count "10x" (season-aggregate)
    val finish: ReportCellFinish?,  // { position: Int, tied: Boolean }; null where positionStr is not a finish
    val scoreToPar: Int?,           // strokes relative to par; 0 is even
    val earnings: BigDecimal,
    val topTens: Int,
    val ownershipPct: BigDecimal,
    val seasonEarnings: BigDecimal,
    val seasonTopTens: Int,
    val pairKey: String? = null,
)

data class Rankings(
    val teams: List<TeamRanking>,
    val weeks: List<String>,            // week labels in chronological order
    val tournamentNames: List<String>,
    val live: Boolean? = null,
)

data class TeamRanking(
    val teamId: TeamId,
    val teamName: String,
    val subtotal: BigDecimal,
    val sideBets: BigDecimal,
    val totalCash: BigDecimal,
    val series: List<BigDecimal>,       // cumulative, one entry per included tournament
    val liveWeekly: BigDecimal? = null,
)

data class GolferHistory(
    val golferName: String,
    val golferId: GolferId,
    val totalEarnings: BigDecimal,
    val topTens: Int,
    val results: List<GolferHistoryEntry>,
)
```

### Admin

```kotlin
data class SeasonSchedulePreviewResult(
    val entries: List<PreviewedTournament>,
    val skipped: List<SkippedEntry>,
)

data class PreviewedTournament(
    val espnEventId: String,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val week: String,
)

data class ConfirmSeasonScheduleRequest(
    val entries: List<ConfirmedTournamentEntry>,
)

data class ConfirmedTournamentEntry(
    val espnEventId: String,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
)

data class SeasonImportResult(
    val created: List<Tournament>,
    val skipped: List<SkippedEntry>,
)

data class RosterPreviewResult(
    val teams: List<PreviewTeam>,
    val totalPicks: Int,
    val matchedCount: Int,
    val ambiguousCount: Int,
    val unmatchedCount: Int,
)

// PickMatch is sealed — the wire carries `"type": "matched"|"ambiguous"|"no_match"`.

data class ConfirmRosterRequest(
    val seasonId: SeasonId,
    val teams: List<ConfirmedTeam>,
)

data class ConfirmedPick(
    val round: Int,
    val ownershipPct: Int,
    val assignment: GolferAssignment,   // sealed: "existing" | "new"
)

data class RosterUploadResult(
    val teamsCreated: Int,
    val golfersCreated: Int,
    val teams: List<Team>,              // the persisted Teams with their assigned ids
)
```

### Tournament + season ops

```kotlin
data class CleanSeasonResult(
    val scoresDeleted: Int,
    val resultsDeleted: Int,
    val standingsDeleted: Int,
    val tournamentsReset: Int,
)
```

`finalizeTournament` and `resetTournament` return the affected `Tournament`.
An `OutOfOrder` violation maps to a 409 refusal whose code is in the table
under Error responses, and whose `error` names the blocking tournaments.
`cleanSeasonResults` answers 404 for an unknown season.

---

## Operational notes

### Logging

Request logs are emitted by `installRequestLogging()` in the format:

```
cwfgw4k.request method=GET route=/api/v1/seasons/:id/report status=200 duration_ms=12
```

Path placeholders are normalized: UUIDs → `:id`, integer path segments →
`:n`, `/assets/*` collapsed to `/assets/:asset`. The format is load-bearing
for Cloud Logging metric extraction; changing it requires coordinating
with the shared cwfgw ops config.

A 403 logs one `WARN` line, `cwfgw4k.forbidden method=GET route=/api/v1/... reason="..."`,
with the route normalized the same way. No other 4xx adds a line beyond
the request log: scan traffic draws 401s and 404s in volume.

Caught exceptions in service code are logged at `WARN` (root logger is
`INFO` so `DEBUG` would go nowhere), with the throwable attached.
