package com.cwfgw.teams

import com.cwfgw.db.Transactor

/** @see com.cwfgw.AppServices.from */
class TeamsSupport private constructor(
    val teamService: TeamService,
) {
    companion object {
        fun from(
            teamRepository: TeamRepository,
            transactor: Transactor,
        ): TeamsSupport = TeamsSupport(teamService = TeamService(teamRepository, transactor))
    }
}
