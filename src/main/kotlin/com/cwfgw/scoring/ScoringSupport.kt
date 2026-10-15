package com.cwfgw.scoring

import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.teams.TeamRepository
import com.cwfgw.tournaments.TournamentRepository

/** @see com.cwfgw.AppServices.from */
class ScoringSupport private constructor(
    val scoringService: ScoringService,
) {
    companion object {
        fun from(
            scoringRepository: ScoringRepository,
            seasonRepository: SeasonRepository,
            tournamentRepository: TournamentRepository,
            teamRepository: TeamRepository,
        ): ScoringSupport =
            ScoringSupport(
                scoringService =
                    ScoringService(
                        repository = scoringRepository,
                        seasonRepository = seasonRepository,
                        tournamentRepository = tournamentRepository,
                        teamRepository = teamRepository,
                    ),
            )
    }
}
