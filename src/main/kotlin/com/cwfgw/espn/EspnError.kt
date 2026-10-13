package com.cwfgw.espn

import com.cwfgw.tournaments.TournamentId

/**
 * Typed failure modes the ESPN import service surfaces alongside success.
 * Variants stay free of HTTP concerns — routes map them to
 * [com.cwfgw.http.DomainError] at the boundary.
 */
sealed interface EspnError {
    /** @see EspnError */
    data class UpstreamUnavailable(val failure: EspnUpstreamFailure) : EspnError

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

/** [summary] goes out verbatim as the `error` of the 502 an ESPN-backed route answers with. */
sealed interface EspnUpstreamFailure {
    val summary: String

    /** @see EspnUpstreamFailure */
    data class BadStatus(val status: Int) : EspnUpstreamFailure {
        override val summary: String get() = "ESPN returned $status"
    }

    /**
     * A timeout, a refused or dropped connection, a host that did not resolve. No status exists for any
     * of them, which is why this is not a [BadStatus] holding an invented one.
     */
    data object NoResponse : EspnUpstreamFailure {
        override val summary: String = "ESPN did not respond"
    }
}
