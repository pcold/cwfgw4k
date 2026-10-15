package com.cwfgw.leagues

import com.cwfgw.db.Transactor

/** @see com.cwfgw.AppServices.from */
class LeaguesSupport private constructor(
    val leagueService: LeagueService,
) {
    companion object {
        fun from(
            leagueRepository: LeagueRepository,
            transactor: Transactor,
        ): LeaguesSupport = LeaguesSupport(leagueService = LeagueService(leagueRepository, transactor))
    }
}
