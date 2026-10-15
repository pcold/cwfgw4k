package com.cwfgw.seasons

import com.cwfgw.db.Transactor
import com.cwfgw.scoring.ScoringRepository
import com.cwfgw.tournaments.TournamentRepository

/** @see com.cwfgw.AppServices.from */
internal class SeasonsSupport private constructor(
    val seasonService: SeasonService,
    val seasonOpsService: SeasonOpsService,
) {
    companion object {
        fun from(
            seasonRepository: SeasonRepository,
            tournamentRepository: TournamentRepository,
            scoringRepository: ScoringRepository,
            transactor: Transactor,
        ): SeasonsSupport =
            SeasonsSupport(
                seasonService = SeasonService(seasonRepository, transactor),
                seasonOpsService =
                    SeasonOpsService(
                        seasonRepository = seasonRepository,
                        tournamentRepository = tournamentRepository,
                        scoringRepository = scoringRepository,
                        tx = transactor,
                    ),
            )
    }
}
