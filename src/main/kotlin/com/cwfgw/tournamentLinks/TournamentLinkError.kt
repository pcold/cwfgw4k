package com.cwfgw.tournamentLinks

import com.cwfgw.golfers.GolferId
import com.cwfgw.tournaments.TournamentId

/**
 * Failure modes the link service surfaces. Routes map these to
 * [com.cwfgw.http.DomainError] at the boundary; the domain stays HTTP-free.
 */
sealed interface TournamentLinkError {
    data class TournamentNotFound(val id: TournamentId) : TournamentLinkError

    /**
     * Tournament has reached the `Completed` state, so its link map is part of
     * settled history. Mutations are rejected because retroactively changing
     * the player→golfer mapping would silently invalidate persisted scores.
     */
    data class TournamentFinalized(val id: TournamentId) : TournamentLinkError

    /** The requested golfer doesn't exist (referential integrity check at the service layer). */
    data class GolferNotFound(val id: GolferId) : TournamentLinkError

    /** Still readable by id for the history it keeps, but a new link would credit the duplicate it was retired as. */
    data class GolferTombstoned(val id: GolferId) : TournamentLinkError

    /** No first name: the admin form never allowed a surname-only golfer, and the server split keeps that rule. */
    data class GolferNameIncomplete(val name: String) : TournamentLinkError
}
