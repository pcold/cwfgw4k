package com.cwfgw.testing

import com.cwfgw.db.WriteTransactionContext
import com.cwfgw.golfers.CreateGolferRequest
import com.cwfgw.golfers.Golfer
import com.cwfgw.golfers.GolferId
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.leagues.CreateLeagueRequest
import com.cwfgw.leagues.LeagueId
import com.cwfgw.leagues.LeagueRepository
import com.cwfgw.seasons.CreateSeasonRequest
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.teams.NewRosterEntry
import com.cwfgw.teams.NewTeam
import com.cwfgw.teams.TeamId
import com.cwfgw.teams.TeamRepository
import java.util.UUID

/** @see seedSeasonRules */
context(ctx: WriteTransactionContext)
fun seedLeague(name: String): LeagueId = LeagueRepository().create(CreateLeagueRequest(name = name)).id

/** @see seedSeasonRules */
context(ctx: WriteTransactionContext)
fun seedLeagueSeason(
    leagueId: LeagueId,
    seasonYear: Int,
    seasonNumber: Int,
): SeasonId =
    SeasonRepository().create(
        CreateSeasonRequest(
            leagueId = leagueId,
            name = "$seasonYear Season $seasonNumber",
            seasonYear = seasonYear,
            seasonNumber = seasonNumber,
        ),
    ).id

/** @see seedSeasonRules */
context(ctx: WriteTransactionContext)
fun seedGolfer(
    firstName: String,
    lastName: String,
    pgaPlayerId: String? = null,
): Golfer =
    GolferRepository().create(
        CreateGolferRequest(pgaPlayerId = pgaPlayerId, firstName = firstName, lastName = lastName),
    )

/** The team name is random because it is unique per season, and one season may take several of these. */
context(ctx: WriteTransactionContext)
fun seedRosteredTeam(
    seasonId: SeasonId,
    golferIds: List<GolferId>,
): TeamId {
    val teams = TeamRepository()
    val team = teams.create(seasonId, NewTeam(ownerName = "Owner", teamName = "Team ${UUID.randomUUID()}"))
    golferIds.forEach { golferId -> teams.addToRoster(team.id, NewRosterEntry(golferId = golferId)) }
    return team.id
}
