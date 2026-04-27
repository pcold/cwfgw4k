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

Status code mappings:

| `DomainError` variant       | Status |
|-----------------------------|--------|
| `NotFound`                  | 404    |
| `Validation`                | 400    |
| `Conflict`                  | 409    |
| `BadGateway`                | 502    |
| `Unauthorized`              | 401    |
| Anything else (uncaught)    | 500    |

Service-layer typed errors (e.g. `TournamentOpsError`, `DraftError`) are
mapped to `DomainError` at the route boundary; the wire body always uses
the same `{ "error": "..." }` shape.

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

Every domain ID — `LeagueId`, `SeasonId`, `TeamId`, `TournamentId`,
`GolferId`, `RosterEntryId`, `FantasyScoreId`, `SeasonStandingId`,
`TournamentResultId`, `DraftId`, `DraftPickId`, `UserId` — is a value
class wrapping a `UUID`. On the wire each is just the raw UUID string.
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
| GET    | `/api/v1/leagues/{id}` | No   | Get one league    |
| POST   | `/api/v1/leagues`      | Yes  | Create a league   |

### Seasons

| Method | Path                                  | Auth | Description                                                                        |
|--------|---------------------------------------|------|------------------------------------------------------------------------------------|
| GET    | `/api/v1/seasons?league_id=&year=`    | No   | List seasons; filters are optional UUIDs / ints                                    |
| GET    | `/api/v1/seasons/{id}`                | No   | Get one season                                                                     |
| GET    | `/api/v1/seasons/{id}/rules`          | No   | Get the season's `SeasonRules` (payouts, tie floor, side bet rounds + amount)      |
| POST   | `/api/v1/seasons`                     | Yes  | Create a season. Body accepts an optional `rules: SeasonRules` — when supplied, the side tables for payouts + side-bet rounds are populated atomically with the seasons row, and the rules' `tie_floor`/`side_bet_amount` override the top-level fields |
| PUT    | `/api/v1/seasons/{id}`                | Yes  | Partial update (`status`, `name`, `max_teams`, `tie_floor`, `side_bet_amount`)     |
| POST   | `/api/v1/seasons/{sid}/finalize`      | Yes  | Lock the season as completed (every tournament must already be `completed`)        |
| POST   | `/api/v1/seasons/{sid}/clean-results` | Yes  | Destructive: wipe all scores/results/standings + revert every tournament to upcoming. Returns deletion counts. |

### Golfers

| Method | Path                                | Auth | Description                                                                |
|--------|-------------------------------------|------|----------------------------------------------------------------------------|
| GET    | `/api/v1/golfers?active=&search=`   | No   | List golfers (default `active=true`); `search` matches first or last name |
| GET    | `/api/v1/golfers/{id}`              | No   | Get one golfer                                                             |
| POST   | `/api/v1/golfers`                   | Yes  | Create a golfer                                                            |
| PUT    | `/api/v1/golfers/{id}`              | Yes  | Partial update                                                             |

### Teams + Rosters

Routes nested under `/api/v1/seasons/{seasonId}/`.

| Method | Path                                                          | Auth | Description                                                |
|--------|---------------------------------------------------------------|------|------------------------------------------------------------|
| GET    | `/api/v1/seasons/{sid}/teams`                                 | No   | List teams in the season                                   |
| GET    | `/api/v1/seasons/{sid}/teams/{teamId}`                        | No   | Get one team                                               |
| GET    | `/api/v1/seasons/{sid}/teams/{teamId}/roster`                 | No   | Get the team's roster (active + dropped)                   |
| GET    | `/api/v1/seasons/{sid}/rosters`                               | No   | Display-shaped view: every team in the season + their picks |
| POST   | `/api/v1/seasons/{sid}/teams`                                 | Yes  | Create a team in the season                                |
| PUT    | `/api/v1/seasons/{sid}/teams/{teamId}`                        | Yes  | Partial update                                             |
| POST   | `/api/v1/seasons/{sid}/teams/{teamId}/roster`                 | Yes  | Add a golfer to the roster                                 |
| DELETE | `/api/v1/seasons/{sid}/teams/{teamId}/roster/{golferId}`      | Yes  | Drop a golfer from the roster                              |

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
| GET    | `/api/v1/tournaments/{id}`                        | No   | Get one tournament                                                                             |
| GET    | `/api/v1/tournaments/{id}/results`                | No   | Get the leaderboard rows                                                                       |
| POST   | `/api/v1/tournaments`                             | Yes  | Create a tournament                                                                            |
| PUT    | `/api/v1/tournaments/{id}`                        | Yes  | Partial update (incl. `status`, `is_team_event`)                                               |
| POST   | `/api/v1/tournaments/{id}/results`                | Yes  | Batch-upsert leaderboard rows                                                                  |
| POST   | `/api/v1/tournaments/{id}/finalize`               | Yes  | Compose ESPN import → score calculation → standings refresh. Rejects out-of-order with 409.   |
| POST   | `/api/v1/tournaments/{id}/reset`                  | Yes  | Wipe scores + results, revert status to `upcoming`. Rejects when later tournaments are completed (409). |

### Scoring

| Method | Path                                                          | Auth | Description                                          |
|--------|---------------------------------------------------------------|------|------------------------------------------------------|
| GET    | `/api/v1/seasons/{sid}/standings`                             | No   | Cumulative season standings                          |
| GET    | `/api/v1/seasons/{sid}/scoring/{tid}`                         | No   | Per-team score rows for one tournament               |
| GET    | `/api/v1/seasons/{sid}/scoring/side-bets`                     | No   | Side-bet standings + per-round breakdown             |
| POST   | `/api/v1/seasons/{sid}/scoring/calculate/{tid}`               | Yes  | Recompute and upsert scores for one tournament       |
| POST   | `/api/v1/seasons/{sid}/scoring/refresh-standings`             | Yes  | Recompute and upsert season standings                |

### Reports

| Method | Path                                                    | Auth | Description                                                               |
|--------|---------------------------------------------------------|------|---------------------------------------------------------------------------|
| GET    | `/api/v1/seasons/{id}/report?live=`                     | No   | Season-aggregate `WeeklyReport`. `?live=` parsed but currently a no-op    |
| GET    | `/api/v1/seasons/{id}/report/{tournamentId}?live=`      | No   | Single-tournament `WeeklyReport`. `?live=` parsed but currently a no-op   |
| GET    | `/api/v1/seasons/{id}/rankings?live=&through=`          | No   | Cumulative team rankings + per-tournament series. `?through=` is an optional cutoff tournament id (404 if missing) |
| GET    | `/api/v1/seasons/{id}/golfer/{golferId}/history`        | No   | Top-10 finishes for one golfer over the season                            |

Live overlay (`?live=true`) is accepted at the route level but ignored by
the service today — the response is identical to the non-live shape. The
in-progress ESPN scoreboard merge is tracked separately.

### ESPN

| Method | Path                                                     | Auth | Description                                                                            |
|--------|----------------------------------------------------------|------|----------------------------------------------------------------------------------------|
| GET    | `/api/v1/espn/calendar`                                  | No   | Read ESPN's PGA season schedule (`EspnCalendarEntry[]`). Used by admin uploadSeason    |
| POST   | `/api/v1/espn/import?date=YYYY-MM-DD`                    | Yes  | Import every completed event for the given date; results auto-link to existing tournaments by `pga_tournament_id` |
| POST   | `/api/v1/espn/import/tournament/{tournamentId}`          | Yes  | Import results for one specific (already-linked) tournament                             |

### Admin

| Method | Path                                              | Auth | Description                                                                                    |
|--------|---------------------------------------------------|------|------------------------------------------------------------------------------------------------|
| POST   | `/api/v1/admin/seasons/{id}/upload`               | Yes  | Pull ESPN's calendar for the date range in the body and create one tournament per entry. Body: `{ "start_date": "YYYY-MM-DD", "end_date": "YYYY-MM-DD" }`. Returns `SeasonImportResult` |
| POST   | `/api/v1/admin/roster/preview`                    | Yes  | Read-only preview of a roster TSV. Body is `text/plain` (the raw TSV). Returns `RosterPreviewResult` describing per-pick match status |
| POST   | `/api/v1/admin/roster/confirm`                    | Yes  | Persist an operator-confirmed roster. Body: `ConfirmRosterRequest`. Returns `RosterUploadResult` with deletion counts and persisted teams |

---

## Wire types

Reference for the more complex bodies. Field names below are the Kotlin
property names; on the wire they're snake-cased.

### Common

```kotlin
data class ErrorBody(val error: String)
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

data class TournamentResult(
    val id: TournamentResultId,
    val tournamentId: TournamentId,
    val golferId: GolferId,
    val position: Int?,
    val scoreToPar: Int?,
    val totalStrokes: Int?,
    val earnings: Long?,
    val round1: Int?, val round2: Int?, val round3: Int?, val round4: Int?,
    val madeCut: Boolean,
    val pairKey: String?,         // synthetic team-event partner key, e.g. "team:t1"
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
    val status: String,           // still stringly-typed; cleanup pending
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
data class WeeklyReport(
    val tournament: ReportTournamentInfo,
    val teams: List<ReportTeamColumn>,
    val undraftedTopTens: List<UndraftedGolfer>,
    val sideBetDetail: List<ReportSideBetRound>,
    val standingsOrder: List<StandingsEntry>,
    val live: Boolean? = null,                       // populated only by live overlay
    val liveLeaderboard: List<LiveLeaderboardEntry> = emptyList(),
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
    val positionStr: String?,       // "1", "T3", "10x" (season-aggregate)
    val scoreToPar: String?,        // "E", "+5", "-3"
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

`finalizeTournament` and `resetTournament` return the affected `Tournament`;
`finalizeSeason` returns the affected `Season`. State-machine violations
(`OutOfOrder`, `IncompleteTournaments`, `SeasonHasNoTournaments`) map to 409
with a human-readable error body that names the blocking tournaments.

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

Caught exceptions in service code are logged at `WARN` (root logger is
`INFO` so `DEBUG` would go nowhere), with the throwable attached.
