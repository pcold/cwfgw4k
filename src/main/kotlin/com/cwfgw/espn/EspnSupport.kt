package com.cwfgw.espn

import com.cwfgw.db.Transactor
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.teams.TeamRepository
import com.cwfgw.tournamentLinks.TournamentLinkRepository
import com.cwfgw.tournaments.TournamentRepository

/** @see com.cwfgw.AppServices.from */
internal class EspnSupport private constructor(
    val espnService: EspnService,
) {
    companion object {
        fun from(
            espnClient: EspnClient,
            seasonRepository: SeasonRepository,
            golferRepository: GolferRepository,
            teamRepository: TeamRepository,
            tournamentRepository: TournamentRepository,
            tournamentLinkRepository: TournamentLinkRepository,
            transactor: Transactor,
        ): EspnSupport =
            EspnSupport(
                espnService =
                    EspnService(
                        client = espnClient,
                        seasonRepository = seasonRepository,
                        golferRepository = golferRepository,
                        teamRepository = teamRepository,
                        tournamentRepository = tournamentRepository,
                        linkRepository = tournamentLinkRepository,
                        tx = transactor,
                    ),
            )
    }
}
