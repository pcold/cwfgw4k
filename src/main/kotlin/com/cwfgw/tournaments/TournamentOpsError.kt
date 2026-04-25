package com.cwfgw.tournaments

/**
 * Typed failure modes for tournament-scope finalize/reset orchestration.
 * Variants stay free of HTTP concerns — routes map them to
 * [com.cwfgw.http.DomainError] at the boundary. The chronological-order
 * checks (`OutOfOrder`) are 409 conflicts; the not-found variant is 404;
 * upstream/import failures are 502 (carried through from EspnError).
 *
 * Season-scope ops have their own [com.cwfgw.seasons.SeasonOpsError]
 * since the lifecycle state machine and the error variants are
 * conceptually distinct.
 */
sealed interface TournamentOpsError {
    data class TournamentNotFound(val id: TournamentId) : TournamentOpsError

    /**
     * Returned when finalize/reset would violate chronological order:
     * earlier tournaments must be finalized before later ones (and vice
     * versa for reset). [blocking] is sorted by start date so the route
     * layer can render a deterministic error message.
     */
    data class OutOfOrder(val action: Action, val blocking: List<Tournament>) : TournamentOpsError

    /** ESPN was unreachable (or returned non-2xx) during the import step of finalize. */
    data class UpstreamUnavailable(val status: Int) : TournamentOpsError

    /** ESPN's scoreboard for the tournament's date didn't include the linked event. */
    data class EventNotInScoreboard(val espnEventId: String) : TournamentOpsError

    /** The action being attempted — used to render an action-specific message in [OutOfOrder]. */
    enum class Action { Finalize, Reset }
}
