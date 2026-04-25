package com.cwfgw.seasons

import com.cwfgw.tournaments.Tournament

/**
 * Typed failure modes for season-scope finalize / clean-results
 * orchestration. Variants stay free of HTTP concerns — routes map them
 * to [com.cwfgw.http.DomainError] at the boundary. The "not yet
 * complete" variants are 409 conflicts; the not-found variant is 404.
 *
 * Tournament-scope ops have their own
 * [com.cwfgw.tournaments.TournamentOpsError]; the two state machines
 * are conceptually distinct, so the error hierarchies stay split.
 */
sealed interface SeasonOpsError {
    data class SeasonNotFound(val id: SeasonId) : SeasonOpsError

    /** Returned by `finalizeSeason` when the season has no tournaments at all to finalize. */
    data object SeasonHasNoTournaments : SeasonOpsError

    /**
     * Returned by `finalizeSeason` when one or more tournaments aren't
     * yet completed. [incomplete] is sorted by start date so the route
     * layer can render a deterministic message of what's still left.
     */
    data class IncompleteTournaments(val incomplete: List<Tournament>) : SeasonOpsError
}
