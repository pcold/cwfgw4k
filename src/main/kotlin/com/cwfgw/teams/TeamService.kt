package com.cwfgw.teams

import com.cwfgw.db.Transactor
import com.cwfgw.seasons.SeasonId

class TeamService(
    private val repository: TeamRepository,
    private val tx: Transactor,
) {
    suspend fun getRosterView(seasonId: SeasonId): List<RosterViewTeam> = tx.get { repository.getRosterView(seasonId) }
}
