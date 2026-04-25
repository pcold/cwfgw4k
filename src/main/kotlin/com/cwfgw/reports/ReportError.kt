package com.cwfgw.reports

import com.cwfgw.golfers.GolferId
import com.cwfgw.seasons.SeasonId
import com.cwfgw.tournaments.TournamentId

/**
 * Typed failure modes the report service surfaces. Variants stay free of
 * HTTP concerns — routes map them to [com.cwfgw.http.DomainError] at the
 * boundary.
 */
sealed interface ReportError {
    data class SeasonNotFound(val seasonId: SeasonId) : ReportError

    data class TournamentNotFound(val tournamentId: TournamentId) : ReportError

    data class GolferNotFound(val golferId: GolferId) : ReportError
}
