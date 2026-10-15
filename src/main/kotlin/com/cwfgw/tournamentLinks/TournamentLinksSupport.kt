package com.cwfgw.tournamentLinks

import com.cwfgw.db.Transactor
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.tournaments.TournamentRepository

/** @see com.cwfgw.AppServices.from */
class TournamentLinksSupport private constructor(
    val tournamentLinkService: TournamentLinkService,
) {
    companion object {
        fun from(
            tournamentLinkRepository: TournamentLinkRepository,
            tournamentRepository: TournamentRepository,
            golferRepository: GolferRepository,
            transactor: Transactor,
        ): TournamentLinksSupport =
            TournamentLinksSupport(
                tournamentLinkService =
                    TournamentLinkService(
                        repository = tournamentLinkRepository,
                        tournamentRepository = tournamentRepository,
                        golferRepository = golferRepository,
                        tx = transactor,
                    ),
            )
    }
}
