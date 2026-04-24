package com.cwfgw.espn

import com.cwfgw.tournaments.TournamentId

/**
 * Typed failure modes the ESPN import service surfaces alongside success.
 * Variants stay free of HTTP concerns — routes map them to
 * [com.cwfgw.http.DomainError] at the boundary.
 */
sealed interface EspnError {
    /** ESPN returned a non-2xx response. Carries the raw upstream status so callers can log it. */
    data class UpstreamUnavailable(val status: Int) : EspnError

    /** The caller asked to import a tournament that does not exist in our DB. */
    data class TournamentNotFound(val tournamentId: TournamentId) : EspnError

    /** The tournament exists but has no `pga_tournament_id` set, so we can't match it to ESPN. */
    data class TournamentNotLinked(val tournamentId: TournamentId) : EspnError

    /**
     * ESPN returned the expected date's scoreboard but no event matched the
     * tournament's `pga_tournament_id`. Either ESPN dropped the event or the
     * linked id is wrong.
     */
    data class EventNotInScoreboard(val espnEventId: String) : EspnError
}
