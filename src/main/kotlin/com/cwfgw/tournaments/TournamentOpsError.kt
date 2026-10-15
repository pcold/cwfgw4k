package com.cwfgw.tournaments

import com.cwfgw.espn.EspnUpstreamFailure
import com.cwfgw.seasons.SeasonId
import java.time.LocalDate

/**
 * Typed failure modes for tournament-scope finalize/reset orchestration.
 * Variants stay free of HTTP concerns — routes map them to
 * [com.cwfgw.http.DomainError] at the boundary. The chronological-order
 * checks (`OutOfOrder`) are 409 refusals; the not-found variants are 404;
 * upstream/import failures are 502 (carried through from EspnError).
 */
sealed interface TournamentOpsError {
    data class TournamentNotFound(val id: TournamentId) : TournamentOpsError

    /** Finalize only: the season was gone by the time scoring read it, after finalize had read the tournament. */
    data class SeasonNotFound(val id: SeasonId) : TournamentOpsError

    /**
     * Returned when finalize/reset would violate chronological order:
     * earlier tournaments must be finalized before later ones (and vice
     * versa for reset). [blocking] is sorted by start date so the route
     * layer can render a deterministic error message.
     */
    data class OutOfOrder(val action: Action, val blocking: List<Tournament>) : TournamentOpsError

    /**
     * Finalize only: the start date moved during the ESPN fetch, so the events fetched for [fetchedFor] are another
     * day's. Refused rather than re-fetched, as CWF-203 decided.
     */
    data class StartDateChanged(val fetchedFor: LocalDate, val startDate: LocalDate) : TournamentOpsError

    /**
     * Finalize only: results upsert on `(tournament_id, golfer_id)`, so a second import leaves a row whose credit
     * has since moved in place and paid. Reset is the way back.
     */
    data class TournamentFinalized(val id: TournamentId) : TournamentOpsError

    /** ESPN was unreachable (or returned non-2xx) during the import step of finalize. */
    data class UpstreamUnavailable(val failure: EspnUpstreamFailure) : TournamentOpsError

    /** The action being attempted — used to render an action-specific message in [OutOfOrder]. */
    enum class Action { Finalize, Reset }
}
