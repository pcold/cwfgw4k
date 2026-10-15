package com.cwfgw.admin

import com.cwfgw.db.Transactor
import com.cwfgw.espn.EspnSupport
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.golfers.GolfersSupport
import com.cwfgw.seasons.SeasonsSupport
import com.cwfgw.teams.TeamRepository
import com.cwfgw.tournaments.TournamentsSupport

/** @see com.cwfgw.AppServices.from */
class AdminSupport private constructor(
    val adminService: AdminService,
) {
    companion object {
        fun from(
            seasons: SeasonsSupport,
            tournaments: TournamentsSupport,
            espn: EspnSupport,
            golfers: GolfersSupport,
            golferRepository: GolferRepository,
            teamRepository: TeamRepository,
            transactor: Transactor,
        ): AdminSupport =
            AdminSupport(
                adminService =
                    AdminService(
                        tx = transactor,
                        seasonService = seasons.seasonService,
                        tournamentService = tournaments.tournamentService,
                        espnService = espn.espnService,
                        golferService = golfers.golferService,
                        golferRepository = golferRepository,
                        teamRepository = teamRepository,
                    ),
            )
    }
}
