package com.cwfgw.espn

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
