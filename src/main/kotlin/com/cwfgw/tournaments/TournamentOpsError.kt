package com.cwfgw.tournaments

import com.cwfgw.seasons.SeasonId

/**
 * Typed failure modes for tournament + season finalize/reset orchestration.
 * Variants stay free of HTTP concerns — routes map them to
 * [com.cwfgw.http.DomainError] at the boundary. The state-machine
 * ordering checks (`OutOfOrder`, `IncompleteTournaments`) are 409
 * conflicts; the not-found variants are 404; upstream/import failures
 * are 502 (carried through from EspnError).
 */
sealed interface TournamentOpsError {
    data class TournamentNotFound(val id: TournamentId) : TournamentOpsError

    data class SeasonNotFound(val id: SeasonId) : TournamentOpsError

    /**
     * Returned when finalize/reset would violate chronological order:
     * earlier tournaments must be finalized before later ones (and vice
     * versa for reset). [blocking] is sorted by start date so the route
     * layer can render a deterministic error message.
     */
    data class OutOfOrder(val action: Action, val blocking: List<Tournament>) : TournamentOpsError

    /** Returned by `finalizeSeason` when one or more tournaments aren't yet completed. */
    data class IncompleteTournaments(val incomplete: List<Tournament>) : TournamentOpsError

    /** Returned by `finalizeSeason` when the season has no tournaments at all to finalize. */
    data object SeasonHasNoTournaments : TournamentOpsError

    /** ESPN was unreachable (or returned non-2xx) during the import step of finalize. */
    data class UpstreamUnavailable(val status: Int) : TournamentOpsError

    /** ESPN's scoreboard for the tournament's date didn't include the linked event. */
    data class EventNotInScoreboard(val espnEventId: String) : TournamentOpsError

    /** The action being attempted — used to render an action-specific message in [OutOfOrder]. */
    enum class Action { Finalize, Reset }
}
