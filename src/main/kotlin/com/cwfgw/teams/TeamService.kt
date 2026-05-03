package com.cwfgw.teams

import com.cwfgw.db.Transactor
import com.cwfgw.golfers.GolferId
import com.cwfgw.seasons.SeasonId

class TeamService(
    private val repository: TeamRepository,
    private val tx: Transactor,
) {
    suspend fun listBySeason(seasonId: SeasonId): List<Team> = tx.get { repository.findBySeason(seasonId) }

    suspend fun get(id: TeamId): Team? = tx.get { repository.findById(id) }

    suspend fun create(
        seasonId: SeasonId,
        request: CreateTeamRequest,
    ): Team = tx.update { repository.create(seasonId, request) }

    suspend fun update(
        id: TeamId,
        request: UpdateTeamRequest,
    ): Team? = tx.update { repository.update(id, request) }

    suspend fun getRoster(teamId: TeamId): List<RosterEntry> = tx.get { repository.getRoster(teamId) }

    suspend fun addToRoster(
        teamId: TeamId,
        request: AddToRosterRequest,
    ): RosterEntry = tx.update { repository.addToRoster(teamId, request) }

    suspend fun dropFromRoster(
        teamId: TeamId,
        golferId: GolferId,
    ): Boolean = tx.update { repository.dropFromRoster(teamId, golferId) }

    suspend fun getRosterView(seasonId: SeasonId): List<RosterViewTeam> = tx.get { repository.getRosterView(seasonId) }
}
