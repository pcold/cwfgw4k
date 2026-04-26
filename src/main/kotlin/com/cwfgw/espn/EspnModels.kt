package com.cwfgw.espn

import com.cwfgw.golfers.GolferId
import com.cwfgw.serialization.BigDecimalSerializer
import com.cwfgw.teams.TeamId
import kotlinx.serialization.Serializable
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
 * One entry in ESPN's season schedule — a tournament's stable id, a
 * human-readable label, and an ISO date for when it starts. Returned by
 * `GET /api/v1/espn/calendar` so operators can preview which events are
 * available before linking them to local tournaments.
 *
 * `startDate` stays a String because ESPN's format includes a time
 * component we don't parse here; the field is informational and we don't
 * make scheduling decisions off it.
 */
@Serializable
data class EspnCalendarEntry(
    val id: String,
    val label: String,
    val startDate: String,
)

/**
 * Dry-run scoring of one ESPN tournament against the league's current
 * rosters — what each team *would* earn if the tournament were finalized
 * with ESPN's current leaderboard. Returned by
 * `GET /api/v1/espn/preview/{seasonId}?date=` and consumed by the live
 * overlay to merge in-progress data onto reports + rankings.
 *
 * `payoutMultiplier` and `isTeamEvent` come from the matched DB
 * tournament (see [com.cwfgw.reports.matchDbTournament]) so the per-team
 * dollar figures honor any overrides operators set up-front; a missing
 * match defaults to multiplier 1.0 and not a team event.
 */
@Serializable
data class EspnLivePreview(
    val espnName: String,
    val espnId: String,
    val completed: Boolean,
    @Serializable(with = BigDecimalSerializer::class) val payoutMultiplier: BigDecimal,
    val totalCompetitors: Int,
    val teams: List<PreviewTeamScore>,
    val leaderboard: List<PreviewLeaderboardEntry>,
    val isTeamEvent: Boolean = false,
)

/** Per-team rollup of live golfer scores — sums golferScores into topTenEarnings + weekly +/- across the league. */
@Serializable
data class PreviewTeamScore(
    val teamId: TeamId,
    val teamName: String,
    val ownerName: String,
    @Serializable(with = BigDecimalSerializer::class) val topTenEarnings: BigDecimal,
    val golferScores: List<PreviewGolferScore>,
    @Serializable(with = BigDecimalSerializer::class) val weeklyTotal: BigDecimal = BigDecimal.ZERO,
)

/** One rostered golfer's projected score for the in-progress tournament. */
@Serializable
data class PreviewGolferScore(
    val golferName: String,
    val golferId: GolferId,
    val position: Int,
    val numTied: Int,
    val scoreToPar: Int?,
    @Serializable(with = BigDecimalSerializer::class) val basePayout: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class) val ownershipPct: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class) val payout: BigDecimal,
)

/**
 * One row of the projected leaderboard — top-N competitors regardless of
 * roster status. Lets the UI render a unified scoreboard with rostered
 * and undrafted players in correct position order.
 */
@Serializable
data class PreviewLeaderboardEntry(
    val name: String,
    val position: Int,
    val scoreToPar: Int?,
    val thru: String?,
    val rostered: Boolean,
    val teamName: String?,
    val pairKey: String? = null,
    val roundScores: List<Int> = emptyList(),
    val totalStrokes: Int? = null,
)
