package com.cwfgw.tournamentLinks

import com.cwfgw.golfers.Golfer
import com.cwfgw.golfers.GolferId
import com.cwfgw.tournaments.TournamentId
import kotlinx.serialization.Serializable

/**
 * Manual ESPN→golfer link override for one specific tournament. Lets an admin pin a
 * particular ESPN competitor row (`espnCompetitorId`) to a specific [GolferId] when
 * automatic matching would otherwise return null — typically last-name-only partner
 * rows in team events with multiple matching surnames on the roster.
 *
 * Per-tournament so a wrong manual link doesn't silently misroute future tournaments.
 */
@Serializable
data class TournamentPlayerOverride(
    val tournamentId: TournamentId,
    val espnCompetitorId: String,
    val golferId: GolferId,
)

@Serializable
data class UpsertTournamentPlayerOverrideRequest(
    val espnCompetitorId: String,
    val golferId: GolferId,
)

/**
 * Admin-UI view of one ESPN competitor for a tournament. Shows what golfer
 * the override-aware matcher would currently link this competitor to, and
 * whether that link came from an explicit override.
 */
@Serializable
data class TournamentCompetitorView(
    val espnCompetitorId: String,
    val name: String,
    val position: Int,
    val isTeamPartner: Boolean,
    val linkedGolfer: Golfer?,
    val hasOverride: Boolean,
)

/**
 * Top-level admin view: every ESPN competitor for the tournament plus an
 * `isFinalized` flag the UI uses to decide whether to disable link controls.
 */
@Serializable
data class TournamentCompetitorListing(
    val tournamentId: TournamentId,
    val isFinalized: Boolean,
    val competitors: List<TournamentCompetitorView>,
)
