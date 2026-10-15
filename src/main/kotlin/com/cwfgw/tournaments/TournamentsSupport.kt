package com.cwfgw.tournaments

import com.cwfgw.db.Transactor
import com.cwfgw.espn.EspnSupport
import com.cwfgw.scoring.ScoringRepository
import com.cwfgw.scoring.ScoringSupport

/** @see com.cwfgw.AppServices.from */
internal class TournamentsSupport private constructor(
    val tournamentService: TournamentService,
    val tournamentOpsService: TournamentOpsService,
) {
    companion object {
        fun from(
            tournamentRepository: TournamentRepository,
            scoringRepository: ScoringRepository,
            scoring: ScoringSupport,
            espn: EspnSupport,
            transactor: Transactor,
        ): TournamentsSupport =
            TournamentsSupport(
                tournamentService = TournamentService(tournamentRepository, transactor),
                tournamentOpsService =
                    TournamentOpsService(
                        tournamentRepository = tournamentRepository,
                        scoringService = scoring.scoringService,
                        scoringRepository = scoringRepository,
                        espnService = espn.espnService,
                        tx = transactor,
                    ),
            )
    }
}
