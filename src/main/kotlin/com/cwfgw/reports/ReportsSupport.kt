package com.cwfgw.reports

import com.cwfgw.db.Transactor
import com.cwfgw.espn.EspnSupport
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.scoring.ScoringRepository
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.teams.TeamRepository
import com.cwfgw.tournaments.TournamentRepository

/** @see com.cwfgw.AppServices.from */
class ReportsSupport private constructor(
    val liveOverlayService: LiveOverlayService,
    val weeklyReportService: WeeklyReportService,
) {
    companion object {
        fun from(
            seasonRepository: SeasonRepository,
            tournamentRepository: TournamentRepository,
            teamRepository: TeamRepository,
            golferRepository: GolferRepository,
            scoringRepository: ScoringRepository,
            espn: EspnSupport,
            transactor: Transactor,
        ): ReportsSupport {
            val liveOverlayService = LiveOverlayService(espn.espnService)
            return ReportsSupport(
                liveOverlayService = liveOverlayService,
                weeklyReportService =
                    WeeklyReportService(
                        seasonRepository = seasonRepository,
                        tournamentRepository = tournamentRepository,
                        teamRepository = teamRepository,
                        golferRepository = golferRepository,
                        scoringRepository = scoringRepository,
                        liveOverlayService = liveOverlayService,
                        tx = transactor,
                    ),
            )
        }
    }
}
