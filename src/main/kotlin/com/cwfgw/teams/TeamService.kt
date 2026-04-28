package com.cwfgw.teams

import com.cwfgw.golfers.GolferId
import com.cwfgw.seasons.SeasonId
import org.jooq.DSLContext

class TeamService(private val repository: TeamRepository) {
    suspend fun listBySeason(seasonId: SeasonId): List<Team> = repository.findBySeason(seasonId)

    suspend fun get(id: TeamId): Team? = repository.findById(id)

    suspend fun create(
        seasonId: SeasonId,
        request: CreateTeamRequest,
        dsl: DSLContext? = null,
    ): Team = repository.create(seasonId, request, dsl)

    suspend fun update(
        id: TeamId,
        request: UpdateTeamRequest,
    ): Team? = repository.update(id, request)

    suspend fun getRoster(teamId: TeamId): List<RosterEntry> = repository.getRoster(teamId)

    suspend fun addToRoster(
        teamId: TeamId,
        request: AddToRosterRequest,
        dsl: DSLContext? = null,
    ): RosterEntry = repository.addToRoster(teamId, request, dsl)

    suspend fun dropFromRoster(
        teamId: TeamId,
        golferId: GolferId,
    ): Boolean = repository.dropFromRoster(teamId, golferId)

    suspend fun getRosterView(seasonId: SeasonId): List<RosterViewTeam> = repository.getRosterView(seasonId)
}
