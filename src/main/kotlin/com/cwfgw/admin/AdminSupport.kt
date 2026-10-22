package com.cwfgw.admin

import com.cwfgw.db.Transactor
import com.cwfgw.espn.EspnSupport
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.teams.TeamRepository
import com.cwfgw.tournaments.TournamentRepository

/** @see com.cwfgw.AppServices.from */
internal class AdminSupport private constructor(
    val adminService: AdminService,
) {
    companion object {
        fun from(
            seasonRepository: SeasonRepository,
            tournamentRepository: TournamentRepository,
            espn: EspnSupport,
            golferRepository: GolferRepository,
            teamRepository: TeamRepository,
            transactor: Transactor,
        ): AdminSupport =
            AdminSupport(
                adminService =
                    AdminService(
                        tx = transactor,
                        seasonRepository = seasonRepository,
                        tournamentRepository = tournamentRepository,
                        espnService = espn.espnService,
                        golferRepository = golferRepository,
                        teamRepository = teamRepository,
                    ),
            )
    }
}
