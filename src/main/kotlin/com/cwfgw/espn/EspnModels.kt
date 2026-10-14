package com.cwfgw.espn

import com.cwfgw.golfers.GolferId
import com.cwfgw.teams.TeamId
import java.math.BigDecimal

/**
 * A tournament parsed from ESPN's scoreboard API. Internal to the import
 * flow — we map these into our domain's Tournament / TournamentResult rows
 * rather than exposing them as wire types over our own API.
 */
data class EspnTournament(
    val espnId: String,
    val name: String,
    val completed: Boolean,
    val competitors: List<EspnCompetitor>,
    val isTeamEvent: Boolean,
)

/**
 * One leaderboard row. Team events (e.g. Zurich Classic) come in as a single
 * team entry from ESPN and are expanded here into one row per partner that
 * shares the team's position/score; both partners carry `isTeamPartner = true`
 * and the same [pairKey] so downstream logic can regroup them. The synthetic
 * `espnId` on team-partner rows is scoped to the team and must never be
 * linked back to a Golfer record — those rows are matched by name only.
 */
data class EspnCompetitor(
    val espnId: String,
    val name: String,
    val order: Int,
    val scoreStr: String?,
    val scoreToPar: Int?,
    val totalStrokes: Int?,
    val roundScores: List<Int>,
    val position: Int,
    val status: EspnStatus,
    val isTeamPartner: Boolean,
    val pairKey: String?,
) {
    val madeCut: Boolean get() = status.madeCut
}

/**
 * ESPN's per-competitor status code. Kept as a typed tag rather than a raw
 * string so `when` branches stay exhaustive and callers can never pattern-match
 * against a typo. Any code ESPN adds that we don't recognize decodes to
 * [Unknown], which conservatively reports `madeCut = false` — treating an
 * unknown status as in-play could silently award payout to someone ESPN has
 * actually flagged out.
 */
enum class EspnStatus(val code: String) {
    Active("1"),
    Cut("2"),
    Withdrawn("3"),
    Disqualified("4"),
    Unknown(""),
    ;

    val madeCut: Boolean get() = this == Active

    companion object {
        fun fromCode(code: String): EspnStatus = entries.firstOrNull { it.code == code } ?: Unknown
    }
}

/**
 * `startDate` stays ESPN's raw string, time component included: the admin schedule import parses only
 * its date prefix, and skips an entry it can't parse rather than failing the preview.
 */
data class EspnCalendarEntry(
    val id: String,
    val label: String,
    val startDate: String,
)

/**
 * One ESPN player as identified across recent tournament scoreboards. The
 * pool is built by unioning competitors from a handful of recent events
 * and deduplicating by [espnId] — see [EspnService.fetchActivePlayers].
 * `name` is ESPN's `displayName` (with original diacritics); callers do
 * their own normalization for matching.
 */
data class EspnAthlete(
    val espnId: String,
    val name: String,
)

/**
 * Dry-run scoring of one ESPN tournament against the league's current
 * rosters — what each team *would* earn if the tournament were finalized
 * with ESPN's current leaderboard. Consumed by the live overlay to merge
 * in-progress data onto reports + rankings; no route returns it.
 *
 * `payoutMultiplier` and `isTeamEvent` come from the previewed DB
 * tournament (see [buildLivePreviewForEvent]) so the per-team
 * dollar figures honor any overrides operators set up-front, however
 * the ESPN event was matched; ESPN can also raise `isTeamEvent`.
 */
data class EspnLivePreview(
    val espnName: String,
    val espnId: String,
    val completed: Boolean,
    val payoutMultiplier: BigDecimal,
    val totalCompetitors: Int,
    val teams: List<PreviewTeamScore>,
    val leaderboard: List<PreviewLeaderboardEntry>,
    val isTeamEvent: Boolean = false,
)

/** Per-team rollup of live golfer scores — sums golferScores into topTenEarnings + weekly +/- across the league. */
data class PreviewTeamScore(
    val teamId: TeamId,
    val teamName: String,
    val ownerName: String,
    val topTenEarnings: BigDecimal,
    val golferScores: List<PreviewGolferScore>,
    val weeklyTotal: BigDecimal = BigDecimal.ZERO,
)

/** One rostered golfer's projected score for the in-progress tournament. */
data class PreviewGolferScore(
    val golferName: String,
    val golferId: GolferId,
    val position: Int,
    val numTied: Int,
    val scoreToPar: Int?,
    val basePayout: BigDecimal,
    val ownershipPct: BigDecimal,
    val payout: BigDecimal,
)

/**
 * One row per competitor in ESPN's field, rostered or not. [teamNames] lists every team rostering the golfer,
 * since one can be split across teams; [rostered] is derived from it so the two cannot disagree.
 */
data class PreviewLeaderboardEntry(
    val name: String,
    val position: Int,
    val scoreToPar: Int?,
    val thru: String?,
    val teamNames: List<String>,
    val pairKey: String? = null,
    val roundScores: List<Int> = emptyList(),
    val totalStrokes: Int? = null,
) {
    val rostered: Boolean get() = teamNames.isNotEmpty()
}
